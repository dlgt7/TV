# TV UI 全面返修视觉审查

日期：2026-10-02。只读审查；未改代码、未构建、未启动监控。读取了仓库 AGENTS.md，逐张查看用户六张原图，并查看既有 final-qa 原始截图和 contact-existing.jpg。此报告不是视觉验收通过证明；未重放全部 28 段录像，也未操作实机。截图确证、代码确证和待动态核查分别说明。

## 结论与修复顺序

当前交付存在共享视觉基础不一致，不能只修六处红圈。优先修：统一焦点尺寸及容器余量 → 统一圆角/描边形状 → 复合设置行及标准按钮 → 导航文字居中 → 详情布局 → 所有页面/状态走查。功能测试和录像能解码均不能替代视觉验收。

## 确认问题（P1 为交付阻断，P2 为必须处理的品质问题）

| ID / 严重性 | 页面与证据 | 复现/表现 | 代码根因与推荐组件 | 可验证验收条件 |
|---|---|---|---|---|
| V01 / P1 | 详情；用户 IMG_1660；final-qa/detail-final.png | 聚焦小播放器，四角白边断开，直线像被圆角切断 | activity_video.xml 的 shape_video_window 半径 28dp；JetStreamPageSurfaces.kt:1576 的 jetStreamVideoWindowFocusDrawable 半径仅 12dp，外层 clipToOutline=true。明确形状冲突。保留 Media3 PlayerView，用同一形状参数的成熟 MaterialShapeDrawable/容器装饰实现外框；不要换播放器或手工逐角遮盖 | 未聚焦/聚焦、加载/播放、全屏往返后四角连续，描边厚度一致；描边不盖视频、不改 PlayerView 身份 |
| V02 / P1 | 搜索；IMG_1662、IMG_1663 | 筛选紧贴海报，首卡聚焦向上压住筛选，左边和标题首字符被裁掉 | View JetStreamAnimator:22 是 1.08 倍/90ms，Compose JetStreamAnimations 为 1.04/120ms；整个卡片连文字一起缩放；view_search_results 的 pager 距筛选仅 8dp；TypeFragment:152 将列表左右 padding 强制设为 0。不能只在根上 clipChildren=false：ViewPager/滚动层边界和首末卡余量必须共同处理。沿用 Leanback GridView，使用一个焦点动画实现/共享 token | 首/中/末列及最底行，聚焦不裁图、边框或文字；动画途中不与筛选、邻卡相交；布局位置不跳；未聚焦与聚焦成对截图，左右上下快速往返录像 |
| V03 / P1 | 设置来源；IMG_1665，IMG_1664 | 主操作是大块白矩形，旁边胶囊次操作悬浮；内部边距和高亮层级不协调；首页图标靠左边显挤 | SettingRow:416-437 把 fillMaxHeight 的 weight(1f) 左 Column 直接当白色焦点主体，并在已裁剪外 Row 内再缩放；ActionChip 又独立复刻胶囊、边框和 padding。优先 TV Material ListItem/Surface 和 Button/IconButton，或既有 Material Components 的明确同级操作区，保留最近修复的兄弟焦点结构 | 主操作和次操作视觉层级清楚，所有状态保持内边距，图标不贴边；D-pad 左右可进入/离开每个动作，边界不困焦；长名称不挤掉动作；弹窗关闭恢复原操作 |
| V04 / P1 | 首页；IMG_1661 | 首页字在白色导航胶囊里偏上；logo、文字、当前来源按钮视觉规格不统一 | JetStreamHomeNavView:196-231 用 Column 居中“Text + 3dp Spacer + 2dp underline”，即使聚焦下划线透明仍占 5dp，使文字单独偏上；activity_home 中 logo 30dp/nav 48dp/source40dp。用 TV Material TabRow/Tab 或既有标准导航按钮，选中标记独立定位，不参与文字居中 | 每个导航聚焦/失焦/选中状态文字中心稳定；logo、导航、来源在同一视觉中线；下划线切换不挪文字；中文/英文/长来源名均通过 |
| V05 / P1 | 详情；IMG_1660、detail-final.png | 左播放器结束后右侧还多一整行按钮；选集区左起点与实际 chips 起点不齐，信息区和操作区留白失衡 | activity_video.xml 左432×243dp，右固定288dp，下方滚动区依右侧底部+20dp；不同固定高度并无统一下边界。JetStreamVodDetailView 内权重/固定动作高度要整体协调。用 ConstraintLayout 约束组/一致网格，不逐个绝对偏移补丁 | 视频、信息、主次操作形成清楚统一网格；左右组下边界有明确一致设计；选集标题/首项与页面安全区对齐；长标题2行、元数据缺失/超长、集数少/多都不溢出 |
| V06 / P2 | 搜索建议；IMG_1662/1663 | 建议 chips 行与下一标题缺少清楚节奏，字号/密度与结果区不一致 | activity_search 建议行负 start margin -8dp；建议、来源、结果来自不同控件规格。用共享 TV AssistChip/FilterChip 或 Material Chip 样式，不新增第三套 | 建议与结果标题有稳定 section 间距；建议一行溢出可滚动且边缘完整；长中英建议不裁文字/不抢结果焦点 |
| V07 / P2 | 首页右上来源；final-hero-focus-stable.png | “演示片库”几乎贴到左右描边，按钮呈窄竖块，和导航差异大 | activity_home title wrap_content、高40dp；需要核查 JetStreamHomeTitleView 的最终 padding/背景。用与辅助操作一致的标准按钮 contentPadding | 短/长来源名均有稳定水平留白；最大宽度省略正确；焦点状态与动作能力一致 |
| V08 / P2 | 继续观看/历史；final-hero-focus-stable.png | 横图装进窄竖图片区中间，两端大块空白；相邻竖图满高，同排图片占比差异明显，画面质量不完整 | adapter_history 的 image 固定84×120dp fitCenter，未按横图/竖图角色选卡片结构。保留不拉伸、不强裁的约束，用统一横向缩略图槽或有意设计的竖海报槽与独立缩略图来源；复用成熟 Card | 横图、竖图、无图混排时卡片结构稳定，不出现悬空小图；标题/进度条不被放大裁掉；进度端点遵守卡片圆角 |
| V09 / P2 | 空收藏；recovery-empty-favorites.png | 巨大边框面板、彩色纸箱和颜文字与其余克制中性色界面脱节；没有明确下一步 | 旧 ProgressLayout/empty art 与新 JetStreamEmptyState 混用可能存在。先查实际绑定，复用统一空态组件和既有 icon | 收藏/历史/搜索/无源/网络失败使用明确且各自准确的文案和适当动作；统一图标/字号/间距；空态可返回且焦点可见 |
| V10 / P1 待当前版本动态复现 | 直播侧栏；recovery-live-overlay.png | 第一频道焦点块右边是突兀直切边，左边圆角，疑似放大被列表边界裁掉 | 旧截图确有视觉问题；需用当前产物确认仍存在。与 V02 同类父容器裁剪/缩放余量风险，不能直接宣称当前已复现。沿用 Leanback 列表/成熟 ListItem，统一形状和安全 padding | 两列首末项聚焦外形完整；长频道名、EPG/收藏变化、侧栏进入退出不裁切；浅色/高细节视频背景上文字仍可读 |

## 跨页共享根因

1. **两套焦点规格并存**：View 卡片 1.08/90ms；Compose 1.04/120ms。JetStreamVodCardRootLayout 对整卡缩放，标题跟着偏移；只让部分父容器不裁剪无法解决 pager 与滚动窗口裁剪。必须统一 token 并按 `ceil(尺寸×(scale−1)/2 + 描边外扩)` 预留最小余量，再加 section 视觉间隔。缩放比例本身也不替代容器检查。
2. **形状重复定义**：视频28dp背景与12dp焦点框是确定错误；海报、历史、频道、弹窗同样有各自半径/背景/foreground 逻辑，应按语义组件只定义一次。
3. **组件名统一不等于实现统一**：JetStreamButtons/Cards/Chips 和页面内私有 NavButton、ActionButton、SettingRow、ActionChip 均用基础 Row/Column、combinedClickable、graphicsLayer 手工重复焦点和视觉。当前 app/build.gradle 只声明 Compose Material3，没有 TV Material 依赖；引入 TV 专用成熟组件需要验证兼容版本/API24，而非假装已经在使用。
4. **View/Compose 边界无共同几何契约**：传统 XML 给固定尺寸，Compose 内部再 padding/scale；Activity 又覆盖 RecyclerView padding。尺寸调整需要同时检查声明与运行时设置。
5. **验收只证明路径跑通**：旧 functional passed 标记不能关闭上述问题。整体成品不能以“有截图/录像、录像解码成功”代替逐页视觉审阅。

## 组件采用建议（保证效果，不机械全量迁移）

- Compose 焦点操作优先评估 `androidx.tv:tv-material` 的 Button、IconButton、Surface/Card、TabRow/Tab、ListItem；共享外观参数，不再每页手写点击、焦点缩放、颜色与边框。先核对可用 API、依赖兼容和 Android 7，再落地。
- 现有大量 View/Leanback 页面保留成熟 GridView/RecyclerView/ConstraintLayout；按钮、chips、形状优先使用已安装的 Material Components。一个控件只保留一个焦点动画来源，避免 Leanback zoom 与自定义 scale 叠加。
- 播放器继续使用既有 Media3 PlayerView 和 service；修容器装饰，不以改渲染引擎掩盖 UI 错误。
- 不为了“全部现成”引入移动端默认触控尺寸、触摸反馈、嵌套可点击行，也不为了统一外观强行一次性重写整个播放器/列表框架。

## 全页面复核矩阵（未检查的不得写通过）

| 页面 | 必须检查的状态/动作 |
|---|---|
| 首页 | 有/无hero、有/无继续观看、导航各项、首末卡、下滚返回、冷启动及返回原焦点、长来源名、横竖图/无图 |
| 片库/推荐/发现 | 分类/筛选首末项、横排/网格切换、详情返回、滚动多排、空/失败/加载、长标题与图片角色 |
| 搜索 | 键盘→建议→来源→首卡→最后一列/底行；空建议、多建议、多来源、单结果/多结果、旧慢响应回流、返回保留查询/焦点 |
| 详情 | 短/长/无标题、无元数据、无海报、加载/失败/重试、小窗→全屏→返回、简介/收藏/换源、1/8/数百集与分页/倒序 |
| 播放 | 暂停/续播/拖动、设置抽屉/字幕音轨/播放列表、长视频名、横竖视频、控制条最底边、主题切换保留播放身份和位置 |
| 直播 | 两列边缘焦点、频道长名/多项、收藏/EPG、侧栏/设置返回、浅亮视频可读性、主题切换无重建崩溃 |
| 我的/收藏/历史 | 每个入口、空/少/多数据、横竖图混排、长按管理/删除、确认取消后焦点、进度与卡片圆角 |
| 设置 | 每个分类、每种行型（单动作/复合动作/开关/值/颜色）、各行所有子按钮、长值、弹窗返回、第一/最后行 |
| 推送/投屏/文件 | 长地址、二维码/复制/推送焦点、无网络/空目录/权限拒绝后的可行动状态、实内容列表 |
| 弹窗/加载/错误 | 每种来源/密码/列表/确认/错误/进度弹窗；焦点唯一且回原处，背景适度压暗，无裁剪/未统一旧控件 |

系统 Android 权限弹窗（files.png 白色权限对话框）属于平台界面，不应为了视觉统一仿造或改写权限流程；应验收允许/拒绝后的应用页面。

## 重新交付最低证据

- 对六张用户图对应状态提供当前真实 APK 的未聚焦/聚焦前后图，原始分辨率，不靠手机浏览器截图；每个确认问题逐条登记关闭证据。
- 对共享控件至少录一段连续 D-pad：首卡→中卡→末卡→上下跨区→返回，显示动画中间态；设置同级操作及弹窗恢复单独录像。
- 每页至少正常/边界/空或失败状态；不能只用演示短标题、少量数据和整齐图片。真实源素材与受控边界数据都要覆盖。
- 整体截图比较时保留同一设备尺寸/密度/字体缩放，检查安全边距、文字中心、形状连续、放大留白和对比；标准化截图并不意味着主观美观已自动验收。
- 功能回归、视觉复核、性能和实机兼容分别记录。既有软件模拟器99.2% janky不能当性能通过；API24替换库测试 APK不能替代生产 ARM 真机播放/硬解/投屏验收。
- 仍未验证的状态明确列为待验，不能把“完美”当成没有证据支持的交付措辞。
