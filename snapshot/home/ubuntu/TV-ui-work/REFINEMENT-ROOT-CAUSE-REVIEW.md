# 海报、按钮与页面布局：根因复核

2026-10-03。本轮只读检查仓库源码，并读取当前精确依赖 `androidx.tv:tv-material:1.0.0` 的官方源码包核对默认行为；未修改应用代码、未操作三星设备。以下区分确定的源码不一致与仍需设备测量的表现，不能代替真实画面验证。

## 1. 海报常态与焦点圆角：确定是双层半径不一致

核心调用链为 `VodPresenter → VodRectHolder → adapter_vod_rect.xml`：外层 `JetStreamVodPosterLayout` 绘制焦点 foreground，内层 `JetStreamPosterImageView` 绘制并裁剪图片。

- [JetStreamPageSurfaces.kt:447](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamPageSurfaces.kt:447)：海报外框半径 **8dp**，描边 **2dp**。
- [JetStreamPageSurfaces.kt:589](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamPageSurfaces.kt:589)：内层 ShapeableImageView 的 shape、placeholder 均为 **16dp**，并启用 `clipToOutline`。
- [adapter_vod_rect.xml](/home/ubuntu/TV-ui-redesign/app/src/leanback/res/layout/adapter_vod_rect.xml)：poster 设置 `duplicateParentState=true`，获得根卡片焦点后显示 8dp foreground，但图片继续按 16dp 裁剪。两条弧线不重合，足以解释“聚焦后圆角变了”。
- `JetStreamDiscoverHeroPosterView` 继承 16dp 图片，却另加 8dp 焦点 foreground；`adapter_discover_landscape.xml`、`adapter_discover_rank.xml` 也复用上述海报容器与图片组合。

`VodPresenter` 被 Home 推荐、TypeFragment 片库、Discover、CollectFragment 搜索结果等复用。KeepAdapter 也复用 VodRectHolder，因此这不是某一页单独的圆角问题。

**建议：以用户要求保留的非选中形状为准，统一图片 shape、placeholder、outline 与其焦点 foreground；本组应保留 16dp，而非把图片改成 8dp。** 其他 preview/shelf/facet 图片常态分别有 8/10/9dp，必须按实际类型对应，不应把所有图片区统一改成 16dp。加粗描边时同时检查描边中心线与图片外缘的几何关系。

### Glide 不是当前这条链的圆角来源

[ImgUtil.java:110](/home/ubuntu/TV-ui-redesign/app/src/main/java/com/fongmi/android/tv/utils/ImgUtil.java:110) 普通海报仅使用 `centerCrop()`；其他分支为 fitCenter、blur 或 dontTransform。未发现海报 `RoundedCorners/GranularRoundedCorners` 变换。实际圆角来自 ShapeableImageView，不能把本案误诊为 Glide 两套圆角变换。

错误占位图的 TextDrawable 自带较小圆角，但在这条 16dp ShapeableImageView 链中仍被外层 shape 裁剪。主因是 foreground 与 shape 不一致；不建议为修此问题新增 bitmap 圆角变换，形成第三个裁剪来源。

## 2. 描边过细：各实现直接写死，需统一可见性标准

- [TvFocusComponents.kt:70](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/components/TvFocusComponents.kt:70)：Compose Button/Surface 的 focused Border 为 **1dp**。
- [JetStreamDialogSurfaces.kt:593](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamDialogSurfaces.kt:593)：原生 MaterialButton 的 strokeWidth 同为 **1dp**。
- JetStreamPageSurfaces 的多组 chip、media item、round item drawable 也是 1dp，而海报、Discover panel 又是 2/3dp。视频另有 tv_visual.xml 尺寸。

建议建立有限的共享焦点描边尺寸（动作按钮、海报/视频），按电视观看距离与用户反馈加粗；不同渲染体系都取同一设计值。仅改 Compose Border 不会修复原生按钮和海报，单纯加粗也不会修复第 1 节的圆角错配。

## 3. Material 默认状态形状：已核对，不能误归因

实际依赖是 `tv-material:1.0.0`。读取该版本官方 [sources.jar](https://dl.google.com/dl/android/maven2/androidx/tv/tv-material/1.0.0/tv-material-1.0.0-sources.jar) 后确认：

- `ButtonDefaults.shape()` 的 focusedShape、pressedShape、disabledShape 默认均继承传入 shape；focusedDisabledShape 继承 disabledShape。
- `ClickableSurfaceDefaults.shape()` 也遵循相同默认继承。
- 当前 `TvActionButton/TvFocusableSurface` 传入 shape，border 也使用同一 shape；圆形播放器按钮显式传 CircleShape。

因此，“只传 shape、没传 focusedShape 导致此版本焦点时变默认圆角”**不是已证实根因**。可以显式统一各状态便于维护，但不要把这个当成海报修复的替代品。

## 4. 按钮图标/文字对齐：跨 Material 文本度量与调用约束不统一

### Compose 文本样式跨体系未桥接

TV Button 内部使用 `androidx.tv.material3.ProvideTextStyle(labelLarge)`，并用 Row 的 Center/CenterVertically 安排内容；调用方却普遍使用 `androidx.compose.material3.Text`。当前 [TvFocusComponents.kt](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/components/TvFocusComponents.kt) **只桥接 LocalContentColor，没有桥接 Material3 LocalTextStyle**。

所以不能假定按钮文字采用 TV labelLarge 的字号/行高：它可能继承外层 Material3 的 bodyLarge（[JetStreamTheme.kt:159](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/theme/JetStreamTheme.kt:159) 为 24sp 行高）。调用处常只设置 fontSize=12/14/15sp，不同步设置 lineHeight。图标盒居中和字形视觉中心因而不是同一个度量标准。

对照：[JetStreamVodDetailView.kt:350](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamVodDetailView.kt:350) 为动作文字明确设 18sp 行高、`includeFontPadding=false`；Setting 的 ActionChip 与播放器 ControlIcon 多数只给字号。它们虽然复用同一 Button，却不保证相同基线与内容盒高度。

**建议：在共享按钮中给 Material3 子 Text 提供明确的按钮文字样式，保留 MiSans，统一字号/行高/font padding；允许大小规格明确覆写。图标使用固定容器和中心对齐，图标与文本作为一个明确排列的内容行处理，避免逐页用任意 offset 补视觉差。** 如需整组在宽按钮中居中，显式定义内容行可用宽度与对齐，不只依赖 Button 内部 intrinsic Row 的居中排列。

### 固定高度覆盖共享 minHeight

调用方有 `.height(40.dp)`、`.size(44.dp)`，共享层再加 `.heightIn(min=40.dp)` 无法解除上层已确定的精确高度。再叠加 6/8dp 上下 padding、不同字号与继承行高，字体缩放后可能没有足够内容空间。`requiredSize()` 图标则可在不足空间中坚持自己的尺寸。

这是确定存在的约束组合；是否造成三星某一具体按钮裁切仍需读取该控件实际尺寸、fontScale 和画面。建议动作按钮优先用最小高度配合一致文字度量；必须固定尺寸的图标按钮单独定义，避免把文字按钮和纯图标按钮混为一个测量规则。

### 原生控件度量没有完全统一

- [JetStreamPageSurfaces.kt:1388](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamPageSurfaces.kt:1388) 原生文字按钮显式 gravity=CENTER、includeFontPadding=false，并按 `hasNoPadding()` 条件补 padding。
- ChipTextSurface 没有同样统一 gravity/fontPadding；调用 XML 或父主题已有 padding 时，`hasNoPadding()` 不会覆盖，实际取值会因控件来源而不同。
- 原生 MaterialButton 的 dialog helper 已消除 insetTop/insetBottom、设 iconPadding=8dp，但保留原 paddingTop/paddingBottom，也未统一 includeFontPadding、iconGravity 和 iconSize，仍受 Material 主题/XML 影响。
- 输入框显式 includeFontPadding=true 是另一类需求；不应为修动作按钮把输入框也一刀切。

建议按“文字动作按钮、图标动作按钮、输入框、信息文字”分别统一测量规则。复查 vector 的可见路径边界，而非只比较 24×24 viewport；相同 iconSize 不代表不同图标有相同光学中心。

## 5. 边缘裁切：缩放/描边没有改变布局占位，祖先裁剪仍存在

[JetStreamAnimator.kt:22](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamAnimator.kt:22) 卡片聚焦 scale=1.04，围绕默认中心放大。布局测量仍是未放大的尺寸：每侧至少多出宽/高的 2%，另需容纳描边。只给水平 inset 而未留垂直边缘空间，不能保证第一/末行完整。

部分链已设 `clipChildren=false/clipToPadding=false`，但 [JetStreamPageSurfaces.kt:1264](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamPageSurfaces.kt:1264) 的 panel 仍 `clipToOutline=true`；RecyclerView/ScrollView 的视口及 Compose LazyRow/LazyColumn 的滚动裁剪也不会被子节点的 clip 标志取消。

建议逐层记录“卡片实际 bounds → 行容器 → recycler → panel → 页面边界”，对第一/最后列与首/末行验证缩放后外缘。优先使用真实 contentPadding 和正确 item 尺寸计算预留空间；不能仅关闭根节点 clip，也不能因旧图某一位置未截断就推断整页都安全。新描边加粗后需要重新核对这些边缘。

## 6. 两项布局需求的当前根因与最小入口

### 首页“来自 xxx”下海报要上下多行

[HomeActivity.java:596](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java:596) 的 `addGrid()` 名称虽叫 Grid，实际把 **全部 items 塞进一个 ArrayObjectAdapter，再添加单个 ListRow**，因此只能是一条横向长行。不是 spanCount 丢失。

建议复用现有 VodPresenter/尺寸计算，按当前 style 的列数分片生成多个 ListRow；保留前面的来源 header、hero 和历史顺序。需复核 Home 的刷新删除范围、首焦点恢复、首末行对齐是否仍按 row 索引工作；不要在同一个水平 ListRow 里嵌套第二套纵向滚动容器。

### 搜索先建议，确认后全屏结果

[SearchActivity.java:200](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/activity/SearchActivity.java:200) 当前 `onSearch()` 直接显示同页 results，并调用 `SearchResultsController(..., compact=true)`；`showResults(true)` 把右侧建议压到固定 128dp，左侧键盘仍占位。这是明确设计为同页紧凑结果，而非全屏结果入口错误。

仓库已有 [CollectActivity.java:34](/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java:34)，使用同一 controller 的 `compact=false` 并接受 keyword，适合复用为确认后的全屏结果页。建议 SearchActivity 保留输入/历史/建议，确认建议或搜索时进入 CollectActivity，Back 返回原输入与建议焦点。

迁移时必须同时处理 `SearchActivity.initView()` 当前自动 `onSearch()`：CollectActivity 的“编辑”动作会带 keyword 再打开 SearchActivity，若保留自动提交，会立即跳回结果形成往返循环。保留搜索历史记录、过期建议回调 guard、返回焦点及 voice/IME 的提交语义，避免通过删除 controller 后遗留空引用来实现页面切换。

## 推荐实施顺序与证据

先统一海报真实常态半径与焦点框，再统一按钮文字/图标度量及描边，然后处理祖先裁剪和页面布局。设备验证至少覆盖：图片成功/失败占位、normal/focus/pressed、首末列与上下边界、文字按钮/纯图标按钮、主题色，以及搜索编辑往返。所有“已修复”结论应来自新截图和真实遥控焦点，不能沿用此前仅说明特定状态可见的视觉报告。
