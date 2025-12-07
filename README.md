# NotePad介绍文档

## 简介

一个基于 ContentProvider 的简洁记事本应用，已完成现代化 UI 美化、搜索与颜色管理、暗黑模式适配，以及更合理的编辑与返回交互。

- 运行环境：Android 5.0+（minSdk 21），targetSdk 34，AndroidX + Material3。

---

## 实验要求与完成情况

1. 基本要求（必须完成）
    - NoteList 界面中笔记条目增加时间戳显示（已完成：相对时间展示，支持暗黑模式）
    - 添加笔记查询功能（根据标题或内容查询）（已完成：标题/内容/全部 3 种模式，防抖，大小写不敏感）

2. 附加功能（至少两项）
    - UI美化（主题设定，更改背景，优化编辑器、卡片化列表、悬浮新建按钮、暗黑模式适配）（已完成）
    - 记事本偏好设置（主题跟随系统/浅色/深色）（已完成）
    - 笔记颜色管理（长按列表卡片直接更改颜色，卡片背景随颜色变化）（已完成）
    - 编辑交互优化（返回弹窗区分新建/粘贴/编辑；撤销更改不退出编辑页；粘贴前台权限适配）（已完成）

---

## 功能概览

- 主界面（NotesList）
    - 卡片化笔记项：显示标题、内容预览（单行）、相对时间（如“5分钟前”、“昨天”）
    - 搜索：支持“全部/仅标题/仅内容”三种模式，防抖处理，不区分大小写
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

### 构建与运行

- 依赖
    - AndroidX AppCompat：`androidx.appcompat:appcompat:1.7.0`
    - Material3：`com.google.android.material:material:1.12.0`
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

### （一）. 笔记条目增加时间戳显示（相对时间）

#### 1. 功能要求

- 新建笔记保存创建时间；修改笔记更新为最新修改时间
- 列表显示更直观的相对时间（例如“1分钟前”、“1小时前”、“昨天”等），超出7天显示完整日期

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

![基本功能1.1.png](screenshot/%E5%9F%BA%E6%9C%AC%E5%8A%9F%E8%83%BD1.1.png)

(2) 修改后显示为最新修改时间（相对显示）

![基本功能1.2.png](screenshot/%E5%9F%BA%E6%9C%AC%E5%8A%9F%E8%83%BD1.2.png)

---

### （二）. 添加笔记查询功能（支持标题/内容/全部）

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

### （一）. UI美化（Material3 主题、Toolbar、卡片化）
#### 1. 功能要求
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

