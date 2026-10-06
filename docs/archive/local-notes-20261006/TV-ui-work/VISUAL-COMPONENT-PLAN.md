# TV 视觉重做：成熟组件采用方案

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

状态：源码基础已落地，尚未完成本轮云端编译和遥控/视觉验收。旧 QA 不代表本轮通过。

## 直接查看的失败证据

已逐张直接查看 IMG_1660–1665.jpeg。详情窗口描边与实际裁切形状分离、选集不沿同一安全线；首页 logo/nav 的垂直几何不一致；搜索建议/筛选/结果没有独立焦点余量，海报缩放被父 viewport 裁掉；设置大块亮白和图标贴边。不能通过仅更换库自动解决版式问题。共同根因是各页重复自定义 clickable + scale + clip + border，且 View 与 Compose 两套尺寸/焦点反馈缺乏统一约束。

## 已核验的版本证据

采用稳定 `androidx.tv:tv-material:1.0.0`，仅 `leanbackImplementation`。不增加 TV Foundation、不升级 BOM、不迁移播放器/Leanback 网格。

- 官方 POM：https://dl.google.com/dl/android/maven2/androidx/tv/tv-material/1.0.0/tv-material-1.0.0.pom
- 官方 AAR：https://dl.google.com/dl/android/maven2/androidx/tv/tv-material/1.0.0/tv-material-1.0.0.aar
- 官方源码：https://dl.google.com/dl/android/maven2/androidx/tv/tv-material/1.0.0/tv-material-1.0.0-sources.jar
- 发布页：https://developer.android.com/jetpack/androidx/releases/tv#1.0.0
- 下载并解压核验，存档在 `/home/ubuntu/TV-ui-work/tv-material-source/`。
- AAR manifest `minSdkVersion=21`，aar metadata `minCompileSdk=34`，项目 API24/compile37 满足声明要求。
- POM Compose animation/foundation/runtime/ui 依赖 1.6.8；现有 BOM 2024.10.00 官方 POM 明确上述模块为 1.7.4，Material3 1.3.0。采用现有 BOM 向上对齐，无全栈升级。
- POM Kotlin stdlib 1.8.22；项目 Kotlin/Compose compiler plugin 2.2.21 保留。
- 这是声明与源码兼容性核验，最终 Gradle 解析、编译、API24 运行仍需本轮云端证据。

## 采用边界与 API

新文件：`app/src/leanback/java/com/fongmi/android/tv/ui/components/TvFocusComponents.kt`。

1. `TvActionButton(onClick, modifier, onLongClick, enabled, selected, shape, contentPadding, interactionSource, content: RowScope)` 包装 AndroidX TV `Button`。由成熟库负责 D-pad center/Enter/数字键盘 Enter、长按、焦点 interaction、semantics。最小 40dp 高，默认 padding 横16dp/纵8dp，10dp角，不聚焦放大。浅色正文+深灰聚焦面+1dp中性细线；不会把整行变成亮白面。
2. `TvFocusableSurface(onClick, modifier, onLongClick, enabled, selected, shape, containerColor, interactionSource, content: BoxScope)` 包装 TV `Surface`，用于设置主操作、导航项等单动作区域。不添加内部 padding，调用方设置一致的label/value布局。默认不缩放。不可嵌套二级可聚焦按钮；主操作和辅助按钮在非focusable Row中并列。
3. Wrapper 桥接 `androidx.tv.material3.LocalContentColor` 到现有 Compose Material3 `LocalContentColor`，保留 JetStreamTheme/MiSans。调用方去掉旧的 focused 黑字、clickable、focusable、scale、clip、border，不重复截获 OK。仍可使用 Modifier.focusRequester/onFocusChanged 完成跨 View 焦点交接。disabled 显式 canFocus=false 保留原跳过禁用项语义。
4. 已有 Compose 海报将优先复用 TV `Card`/`Surface` 单焦点交互；保持图片加载、宽高比、缺图状态。真正使用中的 View 海报保留 Leanback Presenter/GridView，它们已是成熟 TV 组件；修外层 padding/clipChildren/clipToPadding 和焦点层，而非强制 Compose 重写。
5. 原有 AppCompat/Material Dialog 保留窗口、Back、焦点恢复和业务回调；统一现有 dialog surface/按钮/列表样式。TV Material 1.0.0 不提供可直接替换全部现有 Dialog 的完整策略；不为外观改动重写弹窗生命周期。
6. 小窗 PlayerView/播放器服务和播放状态对象不迁移到 Compose。圆角/边框/布局只修其既有容器；保留 SurfaceView API24 限制并以截图检查，不用透明层口头声称遮罩成功。

## 各页面落实与验收

首页/nav：统一 logo 与导航容器中心线、普通与焦点尺寸一致。片库/发现/收藏历史：继续用 Leanback 网格，给海报焦点至少4dp外余量，长标题与缺图不改变卡片几何。搜索：建议、标题、筛选、海报独立间隔与焦点安全区。详情：视频/信息底线、选集左keyline统一，视频画面裁切与同形描边一致。设置：左右分栏、分类/主操作/辅助按钮并列，主题五色是兄弟focus target。播放/直播/推送/投屏/文件/全部弹窗：统一中性按钮反馈，保留成熟View控件及业务入口。

每轮验收需要同内容同焦点前后图，普通/选中/长文/空数据/缺图/边缘焦点/弹窗，完整 D-pad 路径和 Back 恢复；截图视觉与业务路径分别记录。软件模拟器性能、真机/ARM/Python/MPV/硬解/投屏限制继续如实记录。当前方案不等于任何一页视觉已通过。

## 详情、选集与播放控件的实际采用（本轮本地源码，待运行验收）

- `JetStreamVodDetailView.kt`：4个主次操作使用TvActionButton真实子焦点；移除宿主dispatchKeyEvent的手工OK/索引模拟，宿主获焦仅产生entry token，由FocusRequester进入保留的动作。左边界继续调用onFocusVideo，次级动作Down继续onFocusList；内部二维导航与OK由Compose/TV负责。禁用简介时跳过，若原聚焦简介被禁用则重新进入可用动作。title 24/28sp最多2行、remark13/16sp、metadata一行、people两条单行，双排40dp按钮，相邻8dp。最坏布局约233dp，留给父243dp；具体MiSans实测以截图为准。
- `JetStreamChipRow.kt`：每个chip使用TvActionButton，保留点击/长按、selected/focused position接口及nextFocusUp/Down。列表自身不再拦截左右/OK；显式View边界导航保留。使用LazyRow成熟滚动与焦点定位，FocusRequester仅在宿主入焦、显式setFocusedPosition或当前含焦点的数据更新时请求，后台数据不抢走别处焦点。默认4dp焦点外余量，父48dp高容纳40dp按钮。
- `JetStreamVodControlView.kt`：播放/前后/重复/命令分组改TvActionButton；抽屉从单个父focusable模拟索引改TvFocusableSurface真实条目和LazyColumn，每项最多两行。AndroidX处理OK/长按，边界Up/Down和Right留在抽屉，Left/Back沿原closeSettingsDrawer回到播放按钮。保留seek确认/取消/步进、Player对象轮询、所有listener与服务关系。
- 所有替换移除叠加的clickable/combinedClickable、自定义缩放和亮白聚焦块，没有改Activity、播放器内核或服务。
- 重点待回归：小窗→全屏按钮→三次级动作→选集→长按；选集长列表自动滚动及上/下跨View恢复；播放抽屉全项可达/长按/Back、seek待确认取消；长标题/缺metadata/禁用简介/浅深主题；同内容同焦点截图与录像。
