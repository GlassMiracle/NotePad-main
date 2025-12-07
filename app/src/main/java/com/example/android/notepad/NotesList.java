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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;


/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 * 显示一份笔记列表。如果有 {@link Uri} 的 incoming Intent 提供的笔记，
 * 将显示，否则默认显示 {@link NotePadProvider} 的内容。
 * <p>
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 * 注意：请注意，此 Activity 的操作都在 UI 线程中执行。这不是一个好的实践。
 * 这里这样做是为了使代码更易读。实际应用程序应该使用 {@link android.content.AsyncQueryHandler}
 * 或 {@link android.os.AsyncTask} 对象在单独的线程上异步执行操作。
 */
public class NotesList extends AppCompatActivity {

    // For logging and debugging 用于日志记录和调试
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     * 光标适配器所需的列
     */
    private static final String[] PROJECTION = new String[]{
            NotePad.Notes._ID, // 0 ID
            NotePad.Notes.COLUMN_NAME_TITLE, // 1 标题
            NotePad.Notes.COLUMN_NAME_NOTE, // 2 内容
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 3 修改日期
            NotePad.Notes.COLUMN_NAME_COLOR, // 4 颜色
    };

    /**
     * The index of the title column
     * 标题列的索引
     */
    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_NOTE = 2;

    /**
     * The index of the modification date column
     * 修改日期列的索引
     */
    private static final int COLUMN_INDEX_MODIFICATION_DATE = 3;
    private static final int COLUMN_INDEX_COLOR = 4;
    // 搜索模式：全部/标题/内容
    private static final int SEARCH_MODE_ALL = 0;
    private static final int SEARCH_MODE_TITLE = 1;
    private static final int SEARCH_MODE_CONTENT = 2;
    private static final long SEARCH_DEBOUNCE_MS = 250L;
    // 搜索防抖
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    // AppCompat 改造新增字段
    private SimpleCursorAdapter mAdapter;
    private ListView listView;
    private int searchMode = SEARCH_MODE_ALL;
    private String currentQuery = "";
    private SearchView searchView;
    private Spinner searchModeSpinner;
    private Runnable searchRunnable;

    /**
     * onCreate is called when Android starts this Activity from scratch.
     * 当 Android 从无到有启动此 Activity 时调用。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 应用用户主题偏好
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String themeMode = prefs.getString("theme_mode", "system");
        int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if ("light".equals(themeMode)) mode = AppCompatDelegate.MODE_NIGHT_NO;
        else if ("dark".equals(themeMode)) mode = AppCompatDelegate.MODE_NIGHT_YES;
        AppCompatDelegate.setDefaultNightMode(mode);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        // 使用包含 Toolbar + SearchView 的自定义布局
        setContentView(R.layout.noteslist);
        // 设置 Toolbar 为 ActionBar
        MaterialToolbar toolbar = findViewById(R.id.top_app_bar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        // 绑定 ListView（替代 ListActivity 内置列表）
        listView = findViewById(android.R.id.list);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        listView.setOnCreateContextMenuListener(this);

        /* Performs a managed query. The Activity handles closing and requerying the cursor
         * when needed.
         *
         * Please see the introductory note about performing provider operations on the UI thread.
         */
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),            // Use the default content URI for the provider.
                PROJECTION,                       // Return the note ID and title for each note.
                null,                             // No where clause, return all records.
                null,                             // No where clause, therefore no where column values.
                NotePad.Notes.DEFAULT_SORT_ORDER  // Use the default sort order.
        );

        /*
         * The following two arrays create a "map" between columns in the cursor and view IDs
         * for items in the ListView. Each element in the dataColumns array represents
         * a column name; each element in the viewID array represents the ID of a View.
         * The SimpleCursorAdapter maps them in ascending order to determine where each column
         * value will appear in the ListView.
         * 以下两个数组在光标列和视图中为列表视图中的项创建“映射”。
         * dataColumns 数组中的每个元素代表一个列名;ViewID 数组中的每个元素代表视图的 ID。
         * SimpleCursorAdapter 按升序映射它们，以确定每个列值在列表视图中的位置。
         */

        // The names of the cursor columns to display in the view, initialized to the title column
        // 初始化数据列数组，将其设置为标题、内容、修改日期、颜色
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_COLOR
        };

        // The view IDs that will display the cursor columns, initialized to the TextView in
        // noteslist_item.xml
        // 初始化视图 ID 数组
        int[] viewIDs = {android.R.id.text1, R.id.note_preview, R.id.timestamp_text, R.id.card_root};

        // Creates the backing adapter for the ListView.
        // 创建 SimpleCursorAdapter 适配器，将光标数据绑定到列表视图
        SimpleCursorAdapter adapter
                = new SimpleCursorAdapter(
                this,                             // The Context for the ListView 列表视图的上下文
                R.layout.noteslist_item,          // Points to the XML for a list item 指向列表项的 XML
                cursor,                           // The cursor to get items from 要获取项的光标
                dataColumns,
                viewIDs
        );

        adapter.setViewBinder((view, cursor1, columnIndex) -> {
            SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
            boolean useRelative = sp.getBoolean("pref_relative_time", true);
            boolean showPreview = sp.getBoolean("pref_show_preview", true);
            if (columnIndex == COLUMN_INDEX_MODIFICATION_DATE) {
                long timestamp = cursor1.getLong(columnIndex);
                if (useRelative) {
                    CharSequence rel = DateUtils.getRelativeTimeSpanString(
                            timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                    ((TextView) view).setText(rel);
                } else {
                    java.text.DateFormat df = android.text.format.DateFormat.getDateFormat(this);
                    java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(this);
                    java.util.Date d = new java.util.Date(timestamp);
                    ((TextView) view).setText(df.format(d) + " " + tf.format(d));
                }
                return true;
            } else if (columnIndex == COLUMN_INDEX_NOTE) {
                if (!showPreview && view instanceof TextView) {
                    ((TextView) view).setText("");
                    return true;
                }
                return false;
            } else if (columnIndex == COLUMN_INDEX_COLOR) {
                int colorIdx = cursor1.getInt(columnIndex);
                int resolved;
                switch (colorIdx) {
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
                        resolved = getResources().getColor(R.color.colorSurface);
                }
                // 只改变卡片背景色，避免整行容器被染色
                if (view instanceof com.google.android.material.card.MaterialCardView) {
                    ((com.google.android.material.card.MaterialCardView) view).setCardBackgroundColor(resolved);
                }
                return true;
            }
            return false;
        });

        // Sets the ListView's adapter to be the cursor adapter that was just created.
        // 将适配器设置为列表视图的适配器
        mAdapter = adapter;
        listView.setAdapter(mAdapter);

        // 绑定 SearchView 与适配器的过滤逻辑（按标题或内容模糊匹配）
        searchView = findViewById(R.id.search_view);
        searchView.setQueryHint(getString(R.string.search_hint_all));

        // 搜索模式下拉框与搜索框关联
        searchModeSpinner = findViewById(R.id.search_mode_spinner);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        getString(R.string.menu_search_all),
                        getString(R.string.menu_search_title),
                        getString(R.string.menu_search_content)
                });
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        searchModeSpinner.setAdapter(modeAdapter);
        searchModeSpinner.setSelection(0);
        // 搜索模式下拉框选择监听
        searchModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    searchMode = SEARCH_MODE_ALL;
                    searchView.setQueryHint(getString(R.string.search_hint_all));
                } else if (position == 1) {
                    searchMode = SEARCH_MODE_TITLE;
                    searchView.setQueryHint(getString(R.string.search_hint_title));
                } else {
                    searchMode = SEARCH_MODE_CONTENT;
                    searchView.setQueryHint(getString(R.string.search_hint_content));
                }
                // 模式切换也走防抖，避免立刻多次查询
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    if (mAdapter != null) {
                        mAdapter.getFilter().filter(currentQuery);
                    }
                };
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }

            // 搜索模式下拉框无选择监听
            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });

        adapter.setFilterQueryProvider(constraint -> {
            if (constraint == null || constraint.length() == 0) {
                return getContentResolver().query(
                        getIntent().getData(),
                        PROJECTION,
                        null,
                        null,
                        NotePad.Notes.DEFAULT_SORT_ORDER
                );
            } else {
                String selection;
                String[] selectionArgs;
                if (searchMode == SEARCH_MODE_TITLE) {
                    selection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? COLLATE NOCASE";
                    selectionArgs = new String[]{"%" + constraint + "%"};
                } else if (searchMode == SEARCH_MODE_CONTENT) {
                    selection = NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ? COLLATE NOCASE";
                    selectionArgs = new String[]{"%" + constraint + "%"};
                } else { // SEARCH_MODE_ALL
                    selection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? COLLATE NOCASE OR " +
                            NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ? COLLATE NOCASE";
                    selectionArgs = new String[]{"%" + constraint + "%", "%" + constraint + "%"};
                }
                return getContentResolver().query(
                        getIntent().getData(),
                        PROJECTION,
                        selection,
                        selectionArgs,
                        NotePad.Notes.DEFAULT_SORT_ORDER
                );
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query;
                mAdapter.getFilter().filter(query); // 提交时立即过滤
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText;
                // 文本变化时防抖过滤
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    if (mAdapter != null) {
                        mAdapter.getFilter().filter(currentQuery);
                    }
                };
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
                return true;
            }
        });

        // 使用 ListView 的点击回调，直接使用稳定的 id 参数
        listView.setOnItemClickListener((l, v, position, id) -> {
            if (id == AdapterView.INVALID_ROW_ID) return;
            Uri uri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, id);
            String action = getIntent().getAction();
            if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                setResult(RESULT_OK, new Intent().setData(uri));
            } else {
                startActivity(new Intent(Intent.ACTION_EDIT, uri));
            }
        });

        // 悬浮新建按钮：点击后新建笔记
        FloatingActionButton fab = findViewById(R.id.fab_add);
        if (fab != null) {
            fab.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData())));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置变更后刷新列表所有可见项（相对时间/预览）
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     * <p>
     * Sets up a menu that provides the Insert option plus a list of alternative actions for
     * this Activity. Other applications that want to handle notes can "register" themselves in
     * Android by providing an intent filter that includes the category ALTERNATIVE and the
     * mimeTYpe NotePad.Notes.CONTENT_TYPE. If they do this, the code in onCreateOptionsMenu()
     * will add the Activity that contains the intent filter to its list of options. In effect,
     * the menu will offer the user other applications that can handle notes.
     *
     * @param menu A Menu object, to which menu items should be added.
     * @return True, always. The menu should be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 生成其它扩展动作
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        // 根据当前主题状态勾选对应项
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String themeMode = prefs.getString("theme_mode", "system");
        MenuItem follow = menu.findItem(R.id.menu_theme_follow_system);
        MenuItem light = menu.findItem(R.id.menu_theme_light);
        MenuItem dark = menu.findItem(R.id.menu_theme_dark);
        if (follow != null && light != null && dark != null) {
            switch (themeMode) {
                case "system":
                    follow.setChecked(true);
                    break;
                case "light":
                    light.setChecked(true);
                    break;
                case "dark":
                    dark.setChecked(true);
                    break;
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // The paste menu item is enabled if there is data on the clipboard.
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);


        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // If the clipboard contains an item, enables the Paste option on the menu.
        // If the clipboard is empty, disables the menu's Paste option.
        mPasteItem.setEnabled(clipboard.hasPrimaryClip());

        // Gets the number of notes currently being displayed.
        final boolean haveItems = mAdapter.getCount() > 0;

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems) {

            // This is the selected item.
            long selectedId = listView.getSelectedItemId();
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), selectedId);

            // Creates an array of Intents with one element. This will be used to send an Intent
            // based on the selected menu item.
            Intent[] specifics = new Intent[1];

            // Sets the Intent in the array to be an EDIT action on the URI of the selected note.
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // Creates an array of menu items with one element. This will contain the EDIT option.
            MenuItem[] items = new MenuItem[1];

            // Creates an Intent with no specific action, using the URI of the selected note.
            Intent intent = new Intent(null, uri);

            /* Adds the category ALTERNATIVE to the Intent, with the note ID URI as its
             * data. This prepares the Intent as a place to group alternative options in the
             * menu.
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * Add alternatives to the menu
             */
            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,  // Add the Intents as options in the alternatives group.
                    Menu.NONE,                  // A unique item ID is not required.
                    Menu.NONE,                  // The alternatives don't need to be in order.
                    null,                       // The caller's name is not excluded from the group.
                    specifics,                  // These specific options must appear first.
                    intent,                     // These Intent objects map to the options in specifics.
                    Menu.NONE,                  // No flags are required.
                    items                       // The menu items generated from the specifics-to-
                    // Intents mapping
            );
            // If the Edit menu item exists, adds shortcuts for it.
            if (items[0] != null) {

                // Sets the Edit menu item shortcut to numeric "1", letter "e"
                items[0].setShortcut('1', 'e');
            }
        } else {
            // If the list is empty, removes any existing alternative actions from the menu
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        // Displays the menu
        return true;
    }

    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected. If the option was INSERT, then a new Intent is sent out with action
     * ACTION_INSERT. The data from the incoming Intent is put into the new Intent. In effect,
     * this triggers the NoteEditor activity in the NotePad application.
     * <p>
     * If the item was not INSERT, then most likely it was an alternative option from another
     * application. The parent method is called to process the item.
     *
     * @param item The menu item that was selected by the user
     * @return True, if the INSERT menu item was selected; otherwise, the result of calling
     * the parent method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (item.getItemId() == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (item.getItemId() == R.id.menu_theme_follow_system
                || item.getItemId() == R.id.menu_theme_light
                || item.getItemId() == R.id.menu_theme_dark) {
            String modeStr = "system";
            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (item.getItemId() == R.id.menu_theme_light) {
                modeStr = "light";
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (item.getItemId() == R.id.menu_theme_dark) {
                modeStr = "dark";
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            }
            AppCompatDelegate.setDefaultNightMode(mode);
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit().putString("theme_mode", modeStr).apply();
            item.setChecked(true);
            recreate(); // 重新应用主题
            return true;
        } else if (item.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     * <p>
     * The only available options are COPY and DELETE.
     * <p>
     * Context-click is equivalent to long-press.
     *
     * @param menu     A ContexMenu object to which items should be added.
     * @param view     The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        Cursor cursor = (Cursor) mAdapter.getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);
        int id = item.getItemId();
        if (id == R.id.context_open) {
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(
                    getContentResolver(),
                    "Note",
                    noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(noteUri, null, null);
            return true;
        } else if (id == R.id.context_color) {
            // 弹出颜色选择对话框，避免菜单过长
            String[] labels = new String[]{
                    getString(R.string.menu_color_default),
                    getString(R.string.menu_color_yellow),
                    getString(R.string.menu_color_green),
                    getString(R.string.menu_color_blue),
                    getString(R.string.menu_color_red)
            };
            new AlertDialog.Builder(this)
                    .setTitle(R.string.menu_color_change)
                    .setItems(labels, (dialog, which) -> {
                        int color = 0;
                        if (which == 1) color = 1;
                        else if (which == 2) color = 2;
                        else if (which == 3) color = 3;
                        else if (which == 4) color = 4;
                        ContentValues values = new ContentValues();
                        values.put(NotePad.Notes.COLUMN_NAME_COLOR, color);
                        getContentResolver().update(noteUri, values, null, null);
                    })
                    .show();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        // 清理防抖回调，避免泄漏
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        super.onDestroy();
    }
}
