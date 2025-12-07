/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 * <p>
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends AppCompatActivity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /*
     * Creates a projection that returns the note ID and the note contents.
     */
    private static final String[] PROJECTION =
            new String[]{
                    NotePad.Notes._ID,
                    NotePad.Notes.COLUMN_NAME_TITLE,
                    NotePad.Notes.COLUMN_NAME_NOTE,
                    NotePad.Notes.COLUMN_NAME_COLOR
            };

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;
    private static final int REQUEST_EXPORT = 1001;
    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private int mColor;
    private String mOriginalContent;
    private boolean mPendingPaste = false;
    private int mOriginalColor = 0;
    private EditText mTitle;
    private String mOriginalTitle = "";
    private boolean mIsFromPaste = false;

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置编辑页布局（注意：应使用 note_editor，而非 title_editor）
        setContentView(R.layout.note_editor);

        // 顶部导航栏
        MaterialToolbar toolbar = findViewById(R.id.top_app_bar_editor);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> promptSaveOnExit());
        }

        // 根据 Intent 设置编辑/插入状态，并准备 mUri
        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action) || Intent.ACTION_PASTE.equals(action)) {
            mState = STATE_INSERT;
            // 插入一个新的空笔记
            ContentValues initialValues = new ContentValues();
            long now = System.currentTimeMillis();
            initialValues.put(NotePad.Notes.COLUMN_NAME_TITLE, "");
            initialValues.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
            initialValues.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
            initialValues.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
            // 默认颜色读取偏好（ListPreference 字符串值，需解析为整型）
            String colorStr = getSharedPreferences("settings", MODE_PRIVATE)
                    .getString("pref_default_color", "0");
            int defColor;
            try {
                defColor = Integer.parseInt(colorStr);
            } catch (NumberFormatException e) {
                defColor = 0;
            }
            initialValues.put(NotePad.Notes.COLUMN_NAME_COLOR, defColor);
            mUri = getContentResolver().insert(NotePad.Notes.CONTENT_URI, initialValues);
            mColor = defColor;
            applyEditorColor();
            // 如果是粘贴操作，标记待执行，等到窗口获得焦点后再粘贴
            if (Intent.ACTION_PASTE.equals(action)) {
                mPendingPaste = true;
                mIsFromPaste = true;
            }
        } else {
            // 默认按编辑处理
            mState = STATE_EDIT;
            mUri = intent.getData();
        }

        // 初始化查询光标，避免菜单刷新早于光标创建导致空指针
        if (mUri != null) {
            mCursor = managedQuery(
                    mUri,
                    PROJECTION,
                    null,
                    null,
                    null
            );
        }

        /*
         * Using the URI passed in with the triggering Intent, gets the note or notes in
         * the provider.
         * Note: This is being done on the UI thread. It will block the thread until the query
         * completes. In a sample app, going against a simple provider based on a local database,
         * the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        mCursor = managedQuery(
                mUri,         // The URI that gets multiple notes from the provider.
                PROJECTION,   // A projection that returns the note ID and note content for each note.
                null,         // No "where" clause selection criteria.
                null,         // No "where" clause selection values.
                null          // Use the default sort order (modification date, descending)
        );

        // 获取笔记正文编辑框
        mText = this.findViewById(R.id.note);
        // 获取标题输入框
        mTitle = this.findViewById(R.id.title);
        // 请求焦点并显示键盘，确保前台输入状态
        if (mState == STATE_INSERT && mTitle != null) {
            mTitle.requestFocus();
        } else if (mText != null) {
            mText.requestFocus();
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View target = (mState == STATE_INSERT && mTitle != null) ? mTitle : mText;
            imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
        }
        // 初始化完编辑器视图后再应用颜色，避免空指针
        applyEditorColor();
        // 文本变化时刷新菜单，使“撤销更改”即时可见
        if (mTitle != null) {
            mTitle.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    supportInvalidateOptionsMenu();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
        mText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                supportInvalidateOptionsMenu();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        /*
         * If this Activity had stopped previously, its state was written the ORIGINAL_CONTENT
         * location in the saved Instance state. This gets the state.
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    /**
     * This method is called when the Activity is about to come to the foreground. This happens
     * when the Activity comes to the top of the task stack, OR when it is first starting.
     * <p>
     * Moves to the first note in the list, sets an appropriate title for the action chosen by
     * the user, puts the note contents into the TextView, and saves the original text as a
     * backup.
     */
    @Override
    protected void onResume() {
        super.onResume();

        /*
         * mCursor is initialized, since onCreate() always precedes onResume for any running
         * process. This tests that it's not null, since it should always contain data.
         */
        if (mCursor != null) {
            // Requery in case something changed while paused (such as the title)
            mCursor.requery();

            /* Moves to the first record. Always call moveToFirst() before accessing data in
             * a Cursor for the first time. The semantics of using a Cursor are that when it is
             * created, its internal index is pointing to a "place" immediately before the first
             * record.
             */
            mCursor.moveToFirst();

            // Modifies the window title for the Activity according to the current Activity state.
            if (mState == STATE_EDIT) {
                // Set the title of the Activity to include the note title
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setTitle(text);
                // Sets the title to "create" for inserts
            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }

            /*
             * onResume() may have been called after the Activity lost focus (was paused).
             * The user was either editing or creating a note when the Activity paused.
             * The Activity should re-display the text that had been retrieved previously, but
             * it should not move the cursor. This helps the user to continue editing or entering.
             */

            // Gets the note text from the Cursor and puts it in the TextView, but doesn't change
            // the text cursor's position.
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            mText.setTextKeepState(note);
            int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
            String titleVal = colTitleIndex != -1 ? mCursor.getString(colTitleIndex) : "";
            if (mTitle != null) {
                mTitle.setText(titleVal != null ? titleVal : "");
            }
            int colColorIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_COLOR);
            if (colColorIndex != -1) {
                mColor = mCursor.getInt(colColorIndex);
                mOriginalColor = mColor;
                applyEditorColor();
            }

            // Stores the original note text, to allow the user to revert changes.
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }
            if (mOriginalTitle == null || mOriginalTitle.isEmpty()) {
                mOriginalTitle = titleVal != null ? titleVal : "";
            }

            /*
             * Something is wrong. The Cursor should always contain data. Report an error in the
             * note.
             */
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    /**
     * This method is called when an Activity loses focus during its normal operation, and is then
     * later on killed. The Activity has a chance to save its state so that the system can restore
     * it.
     * <p>
     * Notice that this method isn't a normal part of the Activity lifecycle. It won't be called
     * if the user simply navigates away from the Activity.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * This method is called when the Activity loses focus.
     * <p>
     * For Activity objects that edit information, onPause() may be the one place where changes are
     * saved. The Android application model is predicated on the idea that "save" and "exit" aren't
     * required actions. When users navigate away from an Activity, they shouldn't have to go back
     * to it to complete their work. The act of going away should save everything and leave the
     * Activity in a state where Android can destroy it if necessary.
     * <p>
     * If the user hasn't done anything, then this deletes or clears out the note, otherwise it
     * writes the user's work to the provider.
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor != null) {

            String text = mText.getText().toString();
            String titleNow = mTitle != null ? mTitle.getText().toString() : null;

            // 仅当标题与正文都为空且正在结束时，删除笔记
            if (isFinishing() && text.trim().isEmpty() && (titleNow == null || titleNow.trim().isEmpty())) {
                setResult(RESULT_CANCELED);
                deleteNote();
            } else {
                // 读取当前数据库中的内容与颜色用于差异判断
                mCursor.requery();
                mCursor.moveToFirst();
                int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                int colColorIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_COLOR);
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String savedNote = colNoteIndex != -1 ? mCursor.getString(colNoteIndex) : "";
                int savedColor = colColorIndex != -1 ? mCursor.getInt(colColorIndex) : 0;
                String savedTitle = colTitleIndex != -1 ? mCursor.getString(colTitleIndex) : "";

                boolean changed = !text.equals(savedNote)
                        || (mColor != savedColor)
                        || ((titleNow != null) && !titleNow.equals(savedTitle));

                if (mState == STATE_EDIT) {
                    if (changed) {
                        updateNote(text, titleNow);
                    }
                } else if (mState == STATE_INSERT) {
                    if (changed) {
                        // 插入状态仅在有内容或颜色变化时保存，并转为编辑状态
                        String insertTitle = (titleNow != null && !titleNow.isEmpty()) ? titleNow : text;
                        updateNote(text, insertTitle);
                        mState = STATE_EDIT;
                    }
                }
            }
        }
    }

    /**
     * This method is called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     * <p>
     * Builds the menus for editing and inserting, and adds in alternative actions that
     * registered themselves to handle the MIME types for this application.
     *
     * @param menu A Menu object to which items should be added.
     * @return True to display the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // 在新建与编辑状态下都追加隐藏菜单项（只要当前有有效的 URI）
        if (mUri != null) {
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 光标判空，避免删除/返回后崩溃
        if (mCursor == null) {
            MenuItem revert = menu.findItem(R.id.menu_revert);
            if (revert != null) revert.setVisible(false);
            return super.onPrepareOptionsMenu(menu);
        }
        // 重新查询最新内容，检查是否发生变化以控制撤销按钮可见性
        mCursor.requery();
        if (!mCursor.moveToFirst()) {
            MenuItem revert = menu.findItem(R.id.menu_revert);
            if (revert != null) revert.setVisible(false);
            return super.onPrepareOptionsMenu(menu);
        }
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = colNoteIndex != -1 ? mCursor.getString(colNoteIndex) : "";
        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
        String savedTitle = colTitleIndex != -1 ? mCursor.getString(colTitleIndex) : "";
        String currentNote = mText.getText().toString();
        String currentTitle = mTitle != null ? mTitle.getText().toString() : savedTitle;
        MenuItem revert = menu.findItem(R.id.menu_revert);
        if (revert != null) {
            revert.setVisible(!savedNote.equals(currentNote) || !savedTitle.equals(currentTitle));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This method is called when a menu item is selected. Android passes in the selected item.
     * The switch statement in this method calls the appropriate method to perform the action the
     * user chose.
     *
     * @param item The selected MenuItem
     * @return True to indicate that the item was processed, and no further work is necessary. False
     * to proceed to further processing as indicated in the MenuItem object.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the possible menu actions.
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            String text = mText.getText().toString();
            String title = mTitle != null ? mTitle.getText().toString() : null;
            // 无实际内容时不保存，提示用户
            if (text.trim().isEmpty() && (title == null || title.trim().isEmpty())) {
                Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show();
                return true;
            }
            updateNote(text, title);
            finish();
            return true;
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
            return true;
        } else if (id == R.id.menu_revert) {
            cancelNote();
            return true;
        } else if (id == R.id.menu_export) {
            // 使用系统文件创建界面导出为文本
            String title = mTitle != null ? mTitle.getText().toString().trim() : "";
            String base = (title.isEmpty() ? "note" : title);
            // 简化文件名：去掉可能的换行
            base = base.replace('\n', ' ').replace('\r', ' ');
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            create.addCategory(Intent.CATEGORY_OPENABLE);
            create.setType("text/plain");
            create.putExtra(Intent.EXTRA_TITLE, base + ".txt");
            startActivityForResult(create, REQUEST_EXPORT);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A helper method that replaces the note's data with the contents of the clipboard.
     */
    private void performPaste() {
        // Gets a handle to the Clipboard Manager
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // Gets a content resolver instance
        ContentResolver cr = getContentResolver();

        // Gets the clipboard data from the clipboard
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text = null;
            String title = null;

            // Gets the first item from the clipboard data
            ClipData.Item item = clip.getItemAt(0);

            // Tries to get the item's contents as a URI pointing to a note
            Uri uri = item.getUri();

            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                Cursor orig = cr.query(
                        uri,
                        new String[]{NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_TITLE},
                        null,
                        null,
                        null
                );

                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        if (colNoteIndex != -1) text = orig.getString(colNoteIndex);
                        if (colTitleIndex != -1) title = orig.getString(colTitleIndex);
                    }
                    orig.close();
                }
            }

            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // Updates the current note with the retrieved title and text.
            updateNote(text, title);
            // 立即刷新界面显示与状态，提升粘贴可见性
            mText.setTextKeepState(text);
            if (mTitle != null && title != null) {
                mTitle.setTextKeepState(title);
                mOriginalTitle = title;
            }
            mOriginalContent = text;
            mState = STATE_EDIT;

            // 确保光标存在，便于后续菜单刷新与撤销逻辑
            if (mUri != null && (mCursor == null || mCursor.isClosed())) {
                mCursor = managedQuery(mUri, PROJECTION, null, null, null);
            }
            supportInvalidateOptionsMenu();
        }
    }
//BEGIN_INCLUDE(paste)

    /**
     * Replaces the current note contents with the text and title provided as arguments.
     *
     * @param text  The new note contents to use.
     * @param title The new note title to use
     */
    private void updateNote(String text, String title) {

        // Sets up a map to contain values to be updated in the provider.
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        // If the action is to insert a new note, this creates an initial title for it.
        if (mState == STATE_INSERT) {
            // If no title was provided as an argument, create one from the note text.
            if (title == null || title.isEmpty()) {
                // Get the note's length
                int length = text.length();

                // Sets the title by getting a substring of the text that is 31 characters long
                // or the number of characters in the note plus one, whichever is smaller.
                title = text.substring(0, Math.min(30, length));

                // If the resulting length is more than 30 characters, chops off any
                // trailing spaces
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            // In the values map, sets the value of the title
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // In the values map, sets the value of the title
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }
        // This puts the desired notes text into the map.
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
        values.put(NotePad.Notes.COLUMN_NAME_COLOR, mColor);

        /*
         * Updates the provider with the new values in the map. The ListView is updated
         * automatically. The provider sets this up by setting the notification URI for
         * query Cursor objects to the incoming URI. The content resolver is thus
         * automatically notified when the Cursor for the URI changes, and the UI is
         * updated.
         * Note: This is being done on the UI thread. It will block the thread until the
         * update completes. In a sample app, going against a simple provider based on a
         * local database, the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        getContentResolver().update(
                mUri,    // The URI for the record to update.
                values,  // The map of column names and new values to apply to them.
                null,    // No selection criteria are used, so no where columns are necessary.
                null     // No where columns are used, so no where arguments are necessary.
        );


    }
//END_INCLUDE(paste)

    private void cancelNote() {
        // 撤销更改：回滚到进入页面时的原始内容与颜色，不退出编辑页，不更新修改时间
        if (mUri != null) {
            ContentValues values = new ContentValues();
            if (mState == STATE_EDIT) {
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent != null ? mOriginalContent : "");
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, mOriginalTitle != null ? mOriginalTitle : "");
                values.put(NotePad.Notes.COLUMN_NAME_COLOR, mOriginalColor);
            } else if (mState == STATE_INSERT) {
                // 插入状态下，撤销将内容清空、颜色复位为默认、标题清空
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, "");
                values.put(NotePad.Notes.COLUMN_NAME_COLOR, 0);
                mOriginalContent = "";
                mOriginalTitle = "";
                mOriginalColor = 0;
            }
            getContentResolver().update(mUri, values, null, null);
            // 同步界面与内部状态
            mText.setTextKeepState(mOriginalContent != null ? mOriginalContent : "");
            if (mTitle != null) {
                mTitle.setTextKeepState(mOriginalTitle != null ? mOriginalTitle : "");
            }
            mColor = (mState == STATE_EDIT) ? mOriginalColor : 0;
            applyEditorColor();
            // 重新查询以便菜单逻辑正确识别“无变化”
            if (mCursor != null) {
                mCursor.requery();
                mCursor.moveToFirst();
            }
            supportInvalidateOptionsMenu();
        }
        setResult(RESULT_CANCELED);
    }

    /**
     * Take care of deleting a note.  Simply deletes the entry.
     */
    private void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 只有当窗口获得焦点时才执行粘贴，满足Android 12+剪贴板前台访问限制
        if (hasFocus && mPendingPaste) {
            performPaste();
            mState = STATE_EDIT;
            mPendingPaste = false;
        }
    }

    @Override
    public void onBackPressed() {
        promptSaveOnExit();
    }

    private void promptSaveOnExit() {
        // 粘贴进入：始终弹窗询问是否取消粘贴或保存
        if (mIsFromPaste) {
            String currentText = mText != null ? mText.getText().toString() : "";
            String currentTitle = mTitle != null ? mTitle.getText().toString() : "";
            new AlertDialog.Builder(this)
                    .setTitle("粘贴")
                    .setMessage("要取消粘贴并丢弃内容吗？")
                    .setPositiveButton("取消粘贴", (d, w) -> {
                        deleteNote();
                        finish();
                    })
                    .setNegativeButton(R.string.menu_save, (d, w) -> {
                        updateNote(currentText, currentTitle);
                        finish();
                    })
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        // 新建：仅在有实际内容时弹窗；无内容直接取消创建
        if (mState == STATE_INSERT) {
            String currentText = mText != null ? mText.getText().toString() : "";
            String currentTitle = mTitle != null ? mTitle.getText().toString() : "";
            boolean empty = currentText.trim().isEmpty() && currentTitle.trim().isEmpty();
            if (empty) {
                deleteNote();
                finish();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("新建")
                    .setMessage("要取消新建并丢弃内容吗？")
                    .setPositiveButton("取消创建", (d, w) -> {
                        deleteNote();
                        finish();
                    })
                    .setNegativeButton(R.string.menu_save, (d, w) -> {
                        updateNote(currentText, currentTitle);
                        finish();
                    })
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        String currentText = mText != null ? mText.getText().toString() : "";
        String currentTitle = mTitle != null ? mTitle.getText().toString() : "";
        String savedText = "";
        int savedColor = mColor;
        String savedTitle = "";
        if (mCursor != null) {
            mCursor.requery();
            if (mCursor.moveToFirst()) {
                int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                if (colNoteIndex != -1) savedText = mCursor.getString(colNoteIndex);
                int colColorIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_COLOR);
                if (colColorIndex != -1) savedColor = mCursor.getInt(colColorIndex);
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                if (colTitleIndex != -1) savedTitle = mCursor.getString(colTitleIndex);
            }
        }
        final boolean hasChanges = (!currentText.equals(savedText)) || (!currentTitle.equals(savedTitle))
                || (mColor != savedColor) || mPendingPaste;
        if (!hasChanges) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_save)
                .setMessage("是否保存更改？")
                .setPositiveButton(R.string.menu_save, (d, w) -> {
                    updateNote(currentText, currentTitle);
                    finish();
                })
                .setNegativeButton(R.string.menu_revert, (d, w) -> {
                    // 先撤销回滚到原始内容与颜色，再退出
                    cancelNote();
                    finish();
                })
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    // 颜色按钮点击
    public void onPickColor(View v) {
        int id = v.getId();
        if (id == R.id.btn_color_yellow) {
            mColor = 1;
        } else if (id == R.id.btn_color_green) {
            mColor = 2;
        } else if (id == R.id.btn_color_blue) {
            mColor = 3;
        } else if (id == R.id.btn_color_red) {
            mColor = 4;
        } else {
            mColor = 0;
        }
        applyEditorColor();
        // 颜色变化也刷新菜单（撤销按钮）
        supportInvalidateOptionsMenu();
    }

    private void applyEditorColor() {
        int resolved;
        switch (mColor) {
            case 1:
                resolved = getResources().getColor(R.color.noteColorYellow);
                break;
            case 2:
                resolved = getResources().getColor(R.color.noteColorGreen);
                break;
            case 3:
                resolved = getResources().getColor(R.color.noteColorBlue);
                break;
            case 4:
                resolved = getResources().getColor(R.color.noteColorRed);
                break;
            default:
                resolved = getResources().getColor(R.color.editorPaper);
        }
        if (mText != null) {
            mText.setBackgroundColor(resolved);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            String title = mTitle != null ? mTitle.getText().toString() : "";
            String text = mText != null ? mText.getText().toString() : "";
            try (OutputStream os = getContentResolver().openOutputStream(uri);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                pw.println(title);
                pw.println();
                pw.println(text);
                pw.flush();
                Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Defines a custom EditText View that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends EditText {
        private final Rect mRect; // 绘制线
        private final Paint mPaint; // 绘制线的画笔

        // This constructor is used by LayoutInflater
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // Creates a Rect and a Paint object, and sets the style and color of the Paint object.
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        /**
         * This is called to draw the LinedEditText object
         *
         * @param canvas The canvas on which the background is drawn.
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // Gets the number of lines of text in the View.
            int count = getLineCount();

            // Gets the global Rect and Paint objects
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * Draws one line in the rectangle for every line of text in the EditText
             */
            for (int i = 0; i < count; i++) {

                // Gets the baseline coordinates for the current line of text
                int baseline = getLineBounds(i, r);

                /*
                 * Draws a line in the background from the left of the rectangle to the right,
                 * at a vertical position one dip below the baseline, using the "paint" object
                 * for details.
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // Finishes up by calling the parent method
            super.onDraw(canvas);
        }
    }
}
