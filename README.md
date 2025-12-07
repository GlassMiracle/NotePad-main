# NotePad介绍文档

## 简介

一个基于 ContentProvider 的简洁记事本应用，已完成现代化 UI 美化、搜索与颜色管理、暗黑模式适配，以及更合理的编辑与返回交互。

- 运行环境：Android 5.0+（minSdk 21），targetSdk 34，AndroidX + Material3。

---

## 实验要求与完成情况

1. 基本要求（必须完成）
    - NoteList 界面中笔记条目增加时间戳显示（已完成：相对时间展示，支持暗黑模式）
    - 添加笔记查询功能（根据标题或内容查询）（已完成：标题/内容/全部 3 种模式，输入防抖（约250ms），大小写不敏感）

2. 附加功能
    - **UI美化**（主题设定，更改背景，优化编辑器、卡片化列表、悬浮新建按钮、暗黑模式适配）（已完成）
    - **记事本偏好设置**（主题模式、相对/绝对时间切换、内容预览开关、默认笔记颜色）（已完成）
    - **笔记颜色管理**（长按列表卡片直接更改颜色，卡片背景随颜色变化）（已完成）
    - **编辑交互优化**（返回弹窗区分新建/粘贴/编辑；撤销更改不退出编辑页；粘贴前台权限适配）（已完成）
    - **导出笔记为文本**（编辑页使用系统文件选择器保存为.txt）（已完成）

---

## 扩展后功能概览

- 主界面（NotesList）
    - 卡片化笔记项：显示标题、内容预览（单行）、相对时间（如“5分钟前”、“昨天”）
    - 搜索：支持“全部/仅标题/仅内容”三种模式，防抖处理（约250ms），不区分大小写
    - 颜色管理：长按笔记弹出“更改颜色”对话框（默认/黄/绿/蓝/红），卡片背景随颜色变化
    - 主题切换：支持“跟随系统/浅色/深色”，在溢出菜单中设置；深色模式下三点与返回图标、弹出菜单可读性优化
    - 悬浮按钮：快速新建笔记
- 编辑界面（NoteEditor）
    - 标题与正文同时编辑；标题输入框支持清除图标
    - 颜色选择按钮（黄/绿/蓝/红），即时改变编辑背景并保存到笔记
    - 返回弹窗：区分场景
        - 新建/粘贴：弹窗询问“取消创建/粘贴”或“保存”，无内容时直接取消创建
        - 已有笔记：询问“是否保存更改”，撤销更改不退出编辑页
    - 粘贴适配：Android 12+ 仅前台可读剪贴板；在窗口获得焦点后执行粘贴，避免权限拒绝
    - 仅在内容或颜色确有变化时更新“修改时间”，避免无修改也变更时间戳
    - 导出：支持将当前笔记导出为 UTF-8 文本（使用系统文件创建界面）

### 构建与运行

- 依赖
    - AndroidX AppCompat：`androidx.appcompat:appcompat:1.7.1`
    - Material3：`com.google.android.material:material:1.13.0`
    - AndroidX Preference：`androidx.preference:preference:1.2.1`
- Gradle 配置
    - `compileSdkVersion 34`，`targetSdkVersion 34`，`minSdkVersion 21`
    - [gradle.properties](gradle.properties) 必须启用：
        - `android.useAndroidX=true`
        - `android.enableJetifier=true`
- 运行
    - 使用 Android Studio 打开项目，Sync Gradle 后直接运行 `app`
    - 首次运行如报 `android:exported` 或主题相关资源错误，请确认 Manifest 与 styles 一致

---

## UI美化与暗黑模式

- Material3 风格主题（DayNight），自定义颜色方案（values / values-night）
- 主界面采用 AppBarLayout + Toolbar + CoordinatorLayout，内容区域卡片化
- 卡片与弹出菜单在暗黑模式下采用 colorOnPrimary / colorOnSurface 高对比色
- Toolbar 图标（返回、三点）着色保证在暗黑模式下清晰可见

---

## 基本要求实现

### （一）笔记条目增加时间戳显示（相对时间）

#### 1. 功能要求

- 新建笔记保存创建时间；修改笔记更新为最新修改时间
- 列表显示更直观的相对时间（例如“1分钟前”、“1小时前”、“昨天”等），超出7天显示完整日期
<!-- 可在设置中切换为“绝对时间”（日期+时间）显示 -->

#### 2. 实现思路和技术实现

(1) 在列表的投影中加入修改时间字段

```
// NotesList.java 
private static final String[] PROJECTION = new String[] { 
    NotePad.Notes._ID, NotePad.Notes.COLUMN_NAME_TITLE, // 用于标题显示
    NotePad.Notes.COLUMN_NAME_NOTE, // 用于内容预览
    NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 相对时间显示
    NotePad.Notes.COLUMN_NAME_COLOR, // 卡片颜色
};
```

(2) 使用 SimpleCursorAdapter + ViewBinder，将时间戳转换为相对时间；并绑定内容预览与卡片颜色

```
// NotesList.java（片段）
int[] viewIDs = {android.R.id.text1, R.id.note_preview, R.id.timestamp_text, R.id.card_root};

adapter.setViewBinder((view, cursor, columnIndex) -> {
    if (columnIndex == COLUMN_INDEX_MODIFICATION_DATE) { 
        long timestamp = cursor.getLong(columnIndex);
                CharSequence rel = DateUtils.getRelativeTimeSpanString(
                        timestamp,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS);
        ((TextView) view).setText(rel);
        return true;
    } else if (columnIndex == COLUMN_INDEX_COLOR) {
        int colorIdx = cursor.getInt(columnIndex);
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
        if (view instanceof com.google.android.material.card.MaterialCardView) { 
            ((com.google.android.material.card.MaterialCardView) view).setCardBackgroundColor(resolved); 
        }
        return true;
    }
    return false;
});
    
```

(3) 列表项布局展示标题、预览与时间（注意时间视图 id 为 timestamp_text）

```xml
<!-- res/layout/noteslist_item.xml（片段） -->
<TextView
    android:id="@android:id/text1"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="44dp"
    android:gravity="center_vertical"
    android:singleLine="true"
    android:ellipsize="end"
    android:textStyle="bold"
    android:textSize="17sp"
    android:fontFamily="sans-serif-medium"/>
    <!-- 内容预览（最多两行） -->
<TextView
    android:id="@+id/note_preview"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="1dp"
    android:maxLines="1"
    android:ellipsize="end"
    android:textSize="13sp"
    android:textColor="@color/colorOnSurface"/>
    <!-- 时间戳（右对齐） -->
<TextView
    android:id="@+id/timestamp_text"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="1dp"
    android:gravity="end"
    android:textColor="#9E9E9E"
    android:textSize="12sp" />
```

#### 3. 实现效果界面截图

(1) 创建时显示创建时间

<!-- 截图描述：新建笔记后，在笔记列表中可以看到该笔记的创建时间显示为"0分钟前"或具体的相对时间 -->

![基本功能1.1.png](screenshot/%E5%9F%BA%E6%9C%AC%E5%8A%9F%E8%83%BD1.1.png)

(2) 修改后显示为最新修改时间（相对显示）

<!-- 截图描述：编辑笔记后，笔记列表中该笔记的时间会更新为最新的修改时间，以相对时间形式显示 -->

![基本功能1.2.png](screenshot/%E5%9F%BA%E6%9C%AC%E5%8A%9F%E8%83%BD1.2.png)

---

### （二）添加笔记查询功能（支持标题/内容/全部）

#### 1. 功能要求

- 在主界面顶部提供搜索框与模式选择（全部/标题/内容）
- 输入即筛选，防抖降低频繁查询；不区分大小写；可清空恢复全部

#### 2. 实现思路和技术实现

(1) 在主界面布局加入 SearchView 和模式选择 Spinner（id 为 search_mode_spinner）

```xml
<!-- res/layout/noteslist.xml（片段） -->
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:padding="8dp">
<SearchView
    android:id="@+id/search_view"
    android:layout_width="0dp"
    android:layout_height="48dp"
    android:layout_weight="1"
    android:iconifiedByDefault="false"
    android:queryHint="@string/search_hint_all" />

<Spinner
    android:id="@+id/search_mode_spinner"
    android:layout_width="wrap_content"
    android:layout_height="48dp"
    android:layout_gravity="end" />
</LinearLayout>
```

(2) 通过 SimpleCursorAdapter.setFilterQueryProvider 实现动态筛选；
统一使用 ContentResolver.query；LIKE + COLLATE NOCASE 实现不区分大小写模糊查询

```
// NotesList.java（片段)
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

```

(3) 模式选择联动提示与过滤

```
// NotesList.java（片段）
searchModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0) { // 全部
            searchMode = SEARCH_MODE_ALL;
            searchView.setQueryHint(getString(R.string.search_hint_all));
        } else if (position == 1) { // 标题
            searchMode = SEARCH_MODE_TITLE;
            searchView.setQueryHint(getString(R.string.search_hint_title));
        } else {  // 内容
            searchMode = SEARCH_MODE_CONTENT;
            searchView.setQueryHint(getString(R.string.search_hint_content));
        }
        mAdapter.getFilter().filter(currentQuery);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
    });
```

(4) 输入防抖（约 250ms）

```
// NotesList.java（片段）
private final Handler searchHandler = new Handler(Looper.getMainLooper());
private Runnable searchRunnable;
private static final long SEARCH_DEBOUNCE_MS = 250L;

searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
    @Override
    public boolean onQueryTextSubmit(String query) {
        currentQuery = query;
        mAdapter.getFilter().filter(query); // 提交立即过滤
        return true;
    }
    @Override
    public boolean onQueryTextChange(String newText) {
        currentQuery = newText;
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

```

#### 3. 实现效果界面截图

(1) 进入搜索、模式选择（全部/标题/内容）

![基本功能2.1.1.png](screenshot/%E5%9F%BA%E6%9C%AC%E5%8A%9F%E8%83%BD2.1.1.png)

![基本功能2.1.2.png](screenshot/%E5%9F%BA%E6%9C%AC%E5%8A%9F%E8%83%BD2.1.2.png)

(2) 输入后动态显示匹配项

![基本功能2.2.png](screenshot/%E5%9F%BA%E6%9C%AC%E5%8A%9F%E8%83%BD2.2.png)

(3) 清空查询恢复全部

![基本功能2.3.png](screenshot/%E5%9F%BA%E6%9C%AC%E5%8A%9F%E8%83%BD2.3.png)

---

## 附加功能实现

### （一）UI美化（Material3 主题、Toolbar、卡片化）

#### 1. 功能点

- 采用 Material3 风格；禁用系统 ActionBar，使用 Toolbar
- 列表卡片化，优化间距与阴影；暗色模式下可读性良好

#### 2. 实现思路和技术实现

(1) 主题与样式

```xml
<!-- res/values/styles.xml（片段） -->
<style name="Theme.NotePad" parent="Theme.Material3.DayNight">
    <item name="colorPrimary">@color/colorPrimary</item>
    <item name="colorOnPrimary">@color/colorOnPrimary</item>
    <item name="colorSurface">@color/colorSurface</item>
    <item name="colorOnSurface">@color/colorOnSurface</item>
    <item name="android:colorBackground">@color/colorBackground</item>
    <item name="android:statusBarColor">@color/colorPrimary</item>
    <item name="windowActionBar">false</item>
    <item name="windowNoTitle">true</item>
</style>
    <!-- Toolbar 溢出菜单的主题覆盖 -->
<style name="ThemeOverlay.NotePad.PopupMenu" parent="ThemeOverlay.Material3">
    <item name="colorSurface">@color/colorSurface</item>
    <item name="colorOnSurface">@color/colorOnSurface</item>
    <item name="android:textColor">@color/colorOnSurface</item>
    <item name="android:textColorPrimary">@color/colorOnSurface</item>
</style>
    <!-- Toolbar 图标着色覆盖，保证三点与返回图标可见 -->
<style name="ThemeOverlay.NotePad.ToolbarIconsOnPrimary" parent="ThemeOverlay.Material3">
    <item name="colorControlNormal">@color/colorOnPrimary</item>
    <item name="actionMenuTextColor">@color/colorOnPrimary</item>
    <item name="android:textColor">@color/colorOnPrimary</item>
</style>
```

(2) Manifest 应用主题与导出属性

```xml
<!-- app/src/main/AndroidManifest.xml（片段） -->
<application android:icon="@drawable/app_notes" android:label="@string/app_name" android:theme="@style/Theme.NotePad">
    <provider
        android:name="NotePadProvider"
        android:authorities="com.google.provider.NotePad"
        android:exported="true" />

    <activity
        android:name="NotesList"
        android:label="@string/title_notes_list"
        android:theme="@style/Theme.NotePad"
        android:exported="true">
        <!-- LAUNCHER 与数据过滤略 -->
    </activity>

    <activity
        android:name="NoteEditor"
        android:theme="@style/Theme.NotePad"
        android:exported="true" />
</application>
```

(3) 依赖与编译配置

```gradle
// app/build.gradle（片段） 
android { 
    namespace 'com.example.android.notepad' 
    compileSdkVersion 34 
    
    defaultConfig { 
        applicationId "com.example.android.notepad" 
        minSdkVersion 21 
        targetSdkVersion 34 
    } 
} 
dependencies { 
    implementation 'androidx.appcompat:appcompat:1.7.1'
    implementation 'com.google.android.material:material:1.13.0'
    implementation 'androidx.preference:preference:1.2.1'
}
```

#### 3. 实现效果界面截图

- 主界面 Toolbar、卡片化列表

![拓展功能1.1.1.png](screenshot/%E6%8B%93%E5%B1%95%E5%8A%9F%E8%83%BD1.1.1.png)

![拓展功能1.1.2.png](screenshot/%E6%8B%93%E5%B1%95%E5%8A%9F%E8%83%BD1.1.2.png)

- 暗色模式下三点与返回图标、弹出菜单可读

![拓展功能1.2.1.png](screenshot/%E6%8B%93%E5%B1%95%E5%8A%9F%E8%83%BD1.2.1.png)

![拓展功能1.2.2.png](screenshot/%E6%8B%93%E5%B1%95%E5%8A%9F%E8%83%BD1.2.2.png)

---

### （二）偏好设置（Settings）

#### 1. 功能点
- 主题模式：跟随系统/浅色/深色
- 显示相对时间：开/关（关时显示绝对日期+时间）
- 显示内容预览：开/关
- 默认笔记颜色：新建笔记的初始颜色（默认/黄/绿/蓝/红）
- 设置页带导航栏返回按钮

#### 2. 实现思路和关键实现

(1) 添加 AndroidX Preference 依赖

```gradle
dependencies { implementation 'androidx.preference:preference:1.2.1' }
```

(2) 在主界面菜单中添加"设置"选项

```xml
<!-- res/menu/list_options_menu.xml 新增“设置”菜单 -->
<item 
    android:id="@+id/menu_settings" 
    android:title="@string/menu_settings" 
    app:showAsAction="never" />
```

(3) 创建 SettingsActivity 并在 Manifest 中注册

```xml
<!-- AndroidManifest.xml 注册 SettingsActivity -->
<activity
    android:name="com.example.android.notepad.SettingsActivity"
    android:label="@string/menu_settings"
    android:exported="true"
    android:theme="@style/Theme.NotePad"/>
```

(4) 在 NotesList 中读取偏好设置并刷新界面

```
// NotesList.java（片段：读取偏好并刷新）
SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
boolean useRelative = sp.getBoolean("pref_relative_time", true);
boolean showPreview = sp.getBoolean("pref_show_preview", true);
// onResume 中：
if (mAdapter != null) mAdapter.notifyDataSetChanged(); // 刷新
if (listView != null) listView.invalidateViews(); // 刷新

```

(5) 在 NoteEditor 中应用默认颜色设置

```
// NoteEditor.java（片段：新建时应用默认颜色）
String colorStr = getSharedPreferences("settings", MODE_PRIVATE)
        .getString("pref_default_color", "0");
int defColor = 0; 
try { 
    defColor = Integer.parseInt(colorStr); 
} catch (NumberFormatException ignored) {
    defColor = 0;
}
initialValues.put(NotePad.Notes.COLUMN_NAME_COLOR, defColor);
mColor = defColor;
applyEditorColor(); // 空指针保护

```

#### 3. 实现效果界面截图

- 在主界面溢出菜单中点击"设置"选项进入设置界面

![扩展功能2.1.1-导航栏选项.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD2.1.1-%E5%AF%BC%E8%88%AA%E6%A0%8F%E9%80%89%E9%A1%B9.png)

- 偏好设置界面展示各项设置选项

![扩展功能2.1.2-偏好设置界面.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD2.1.2-%E5%81%8F%E5%A5%BD%E8%AE%BE%E7%BD%AE%E7%95%8C%E9%9D%A2.png)

---

### （三）导出笔记为文本

#### 1. 功能点

- 编辑页“导出”将当前笔记保存为 .txt（UTF-8）
- 使用系统文件创建界面（SAF），无需存储权限

#### 2. 实现思路和关键实现

(1) 在编辑界面菜单中添加"导出"选项

```xml
<!-- res/menu/editor_options_menu.xml -->
<item android:id="@+id/menu_export"
    android:title="@string/menu_export"
    app:showAsAction="never" />
```

(2) 实现导出功能，使用 SAF 创建文档并写入内容

```
// NoteEditor.java（片段）
else if (id == R.id.menu_export) {
    String title = mTitle != null ? mTitle.getText().toString().trim() : "";
    String base = (title.isEmpty() ? "note" : title).replace('\n',' ').replace('\r',' ');
    Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    create.addCategory(Intent.CATEGORY_OPENABLE);
    create.setType("text/plain");
    create.putExtra(Intent.EXTRA_TITLE, base + ".txt");
    startActivityForResult(create, REQUEST_EXPORT);
}
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
        Uri uri = data.getData();
        String title = mTitle != null ? mTitle.getText().toString() : "";
        String text = mText != null ? mText.getText().toString() : "";
        try (OutputStream os = getContentResolver().openOutputStream(uri);
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, "UTF-8"))) {
            pw.println(title);
            pw.println();
            pw.println(text);
            pw.flush();
            Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        }
    }
}
```

#### 3. 实现效果界面截图

- 在编辑界面点击溢出菜单显示"导出"选项

![扩展功能3.1.1.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD3.1.1.png)

- 点击"导出"后弹出系统文件保存对话框

![扩展功能3.1.2.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD3.1.2.png)

- 导出成功后显示提示信息

![扩展功能3.1.3.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD3.1.3.png)

---

### （四）笔记颜色管理

#### 1. 功能点

- 长按笔记弹出"更改颜色"对话框，选择颜色（默认/黄/绿/蓝/红）
- 卡片背景随颜色变化，列表外部背景不受影响
- 数据库新增颜色字段，版本升级兼容旧表

#### 2. 实现思路和关键实现

(1) 在 NotePad 类中定义颜色列常量，并在数据库中添加颜色字段

```
// NotePad.java（新增颜色列）
public static final String COLUMN_NAME_COLOR = "color";

// NotePadProvider.java（数据库版本升级与表结构）
private static final int DATABASE_VERSION = 3;

db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
   + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
   + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
   + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
   + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
   + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
   + NotePad.Notes.COLUMN_NAME_COLOR + " INTEGER DEFAULT 0"
   + ");");

// onUpgrade：旧表添加 color 列
if (oldVersion < 3) {
    b.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME
       + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_COLOR + " INTEGER DEFAULT 0;");
}

```

(2) 在列表上下文菜单中添加颜色更改选项

```xml
<!-- res/menu/list_context_menu.xml（长按菜单） -->
<item android:id="@+id/context_color"
    android:title="@string/menu_color_change" />
```

(3) 实现长按弹窗选择颜色功能与列表卡片背景变化

```
// NotesList.java片段（长按弹窗选择颜色）
else if (id == R.id.context_color) {
    String[] labels = new String[] {
        getString(R.string.menu_color_default),
        getString(R.string.menu_color_yellow),
        getString(R.string.menu_color_green),
        getString(R.string.menu_color_blue),
        getString(R.string.menu_color_red)
    };
    new AlertDialog.Builder(this)
        .setTitle(R.string.menu_color_change)
        .setItems(labels, (dialog, which) -> {
            int color = which; // 0~4 对应数据库颜色值
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_COLOR, color);
            getContentResolver().update(noteUri, values, null, null);
        }).show();
    return true;
}

// NotesList.java片段（列表中仅卡片背景变色）
else if (columnIndex == COLUMN_INDEX_COLOR) {
    int colorIdx = cursor.getInt(columnIndex);
    int resolved = /* 映射至 R.color.noteColorX 或 colorSurface */;
    if (view instanceof com.google.android.material.card.MaterialCardView) {
       ((com.google.android.material.card.MaterialCardView) view).setCardBackgroundColor(resolved);
    }
    return true;
}
```

#### 3. 实现效果界面截图

- 长按笔记项弹出上下文菜单，选择"更改颜色"选项

![扩展功能4.1.1.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD4.1.1.png)

- 点击"更改颜色"后弹出颜色选择对话框

![扩展功能4.1.2.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD4.1.2.png)

- 选择颜色后笔记卡片背景随之改变，列表外部背景不受影响

![扩展功能4.2.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD4.2.png)

---

### （五）编辑交互优化

#### 1. 功能点

- **标题与正文联动编辑保存**：编辑页顶部标题输入框，保存时同步写入数据库
- **返回弹窗区分场景**：
  - 新建：无内容直接取消创建；有内容弹窗"取消创建/保存/继续编辑"
  - 粘贴：弹窗"取消粘贴/保存/继续编辑"
  - 编辑：有变化弹窗"保存/撤销更改/继续编辑"，无变化直接退出
- **撤销更改不退出编辑页**：回滚标题、正文、颜色到进入时状态，停留在编辑页
- **粘贴适配 Android 12+**：在窗口获得焦点后执行粘贴，避免前台权限拒绝

#### 2. 关键实现

```xml
<!-- res/layout/note_editor.xml（标题输入框） -->
<com.google.android.material.textfield.TextInputLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/text_title"
    app:endIconMode="clear_text">
<com.google.android.material.textfield.TextInputEditText
   android:id="@+id/title"
   android:layout_width="match_parent"
   android:layout_height="wrap_content"
   android:maxLines="1"
   android:textColor="@color/colorOnSurface"/>
</com.google.android.material.textfield.TextInputLayout>
```

```
// NoteEditor.java（标题保存）
private final void updateNote(String text, String title) {
    ContentValues values = new ContentValues();
    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
    if (mState == STATE_INSERT) {
       if (title == null || title.isEmpty()) {
           // 从正文截断生成标题（≤30 字符）
           title = text.substring(0, Math.min(30, text.length()));
       }
       values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
    } else if (title != null) {
       values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
    }
    values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
    values.put(NotePad.Notes.COLUMN_NAME_COLOR, mColor);
    getContentResolver().update(mUri, values, null, null);
}

// NoteEditor.java（返回弹窗区分场景）
private void promptSaveOnExit() {
if (mIsFromPaste) {
   // 粘贴：弹窗"取消粘贴/保存/继续编辑"
   new AlertDialog.Builder(this)
       .setTitle("粘贴")
       .setMessage("要取消粘贴并丢弃内容吗？")
       .setPositiveButton("取消粘贴", (d, w) -> { deleteNote(); finish(); })
       .setNegativeButton(R.string.menu_save, (d, w) -> { updateNote(currentText, currentTitle); finish(); })
       .setNeutralButton(android.R.string.cancel, null)
       .show();
   return;
}
if (mState == STATE_INSERT) {
   boolean empty = (currentText == null || currentText.trim().isEmpty())
           && (currentTitle == null || currentTitle.trim().isEmpty());
   if (empty) { deleteNote(); finish(); return; }
   // 新建：有内容弹窗"取消创建/保存/继续编辑"
   new AlertDialog.Builder(this)
       .setTitle("新建")
       .setMessage("要取消新建并丢弃内容吗？")
       .setPositiveButton("取消创建", (d, w) -> { deleteNote(); finish(); })
       .setNegativeButton(R.string.menu_save, (d, w) -> { updateNote(currentText, currentTitle); finish(); })
       .setNeutralButton(android.R.string.cancel, null)
       .show();
   return;
}
// 编辑：有变化则弹窗"保存/撤销更改/继续编辑"
boolean hasChanges = (!currentText.equals(savedText)) || (!currentTitle.equals(savedTitle)) || (mColor != savedColor);
if (!hasChanges) { finish(); return; }
new AlertDialog.Builder(this)
   .setTitle(R.string.menu_save)
   .setMessage("是否保存更改？")
   .setPositiveButton(R.string.menu_save, (d, w) -> { updateNote(currentText, currentTitle); finish(); })
   .setNegativeButton(R.string.menu_revert, (d, w) -> { cancelNote(); finish(); })
   .setNeutralButton(android.R.string.cancel, null)
   .show();
}

// NoteEditor.java（撤销更改不退出）
private final void cancelNote() {
    if (mUri != null) {
       ContentValues values = new ContentValues();
       if (mState == STATE_EDIT) {
           values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent != null ? mOriginalContent : "");
           values.put(NotePad.Notes.COLUMN_NAME_TITLE, mOriginalTitle != null ? mOriginalTitle : "");
           values.put(NotePad.Notes.COLUMN_NAME_COLOR, mOriginalColor);
       } else if (mState == STATE_INSERT) {
           values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
           values.put(NotePad.Notes.COLUMN_NAME_TITLE, "");
           values.put(NotePad.Notes.COLUMN_NAME_COLOR, 0);
           mOriginalContent = ""; mOriginalTitle = ""; mOriginalColor = 0;
       }
       getContentResolver().update(mUri, values, null, null);
       // 同步界面与内部状态
       mText.setTextKeepState(mOriginalContent != null ? mOriginalContent : "");
       if (mTitle != null) mTitle.setTextKeepState(mOriginalTitle != null ? mOriginalTitle : "");
       mColor = (mState == STATE_EDIT) ? mOriginalColor : 0;
       applyEditorColor();
       if (mCursor != null) { mCursor.requery(); mCursor.moveToFirst(); }
       supportInvalidateOptionsMenu();
    }
    setResult(RESULT_CANCELED); 
}
```

```
// NoteEditor.java（粘贴延迟执行，适配 Android 12+）
private boolean mPendingPaste = false;

@Override
public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus && mPendingPaste) {
       performPaste(); // 仅前台获焦后执行
       mState = STATE_EDIT;
       mPendingPaste = false;
    }
}

private final void performPaste() {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = clipboard.getPrimaryClip();
    if (clip != null) {
       ClipData.Item item = clip.getItemAt(0);
       Uri uri = item.getUri();
       if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(getContentResolver().getType(uri))) {
           Cursor orig = getContentResolver().query(uri,
                   new String[]{NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_TITLE},
                   null, null, null);
           if (orig != null && orig.moveToFirst()) {
               int colNoteIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
               int colTitleIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
               String text = colNoteIndex != -1 ? orig.getString(colNoteIndex) : "";
               String title = colTitleIndex != -1 ? orig.getString(colTitleIndex) : "";
               orig.close();
               updateNote(text, title);
               mText.setTextKeepState(text);
               if (mTitle != null && title != null) { mTitle.setTextKeepState(title); mOriginalTitle = title; }
               mOriginalContent = text; mState = STATE_EDIT;
           }
       }
    }
}
```

#### 3. 实现效果界面截图

- 编辑界面顶部添加标题输入框，支持清除图标

![扩展功能5.1.1.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD5.1.1.png)

- 新建笔记时的返回弹窗提示

![扩展功能5.1.2.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD5.1.2.png)

- 粘贴笔记时的返回弹窗提示

![扩展功能5.2.1.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD5.2.1.png)

- 编辑已有笔记时的返回弹窗提示

![扩展功能5.2.2.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD5.2.2.png)

- 撤销更改后停留在编辑界面

![扩展功能5.3.1.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD5.3.1.png)

![扩展功能5.3.2.png](screenshot/%E6%89%A9%E5%B1%95%E5%8A%9F%E8%83%BD5.3.2.png)
