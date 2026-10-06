# 全页面缺陷与组件采用清单

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

状态：OPEN=待修或待验证；IMPLEMENTED=代码已改但未视觉验收；VERIFIED需绑定本轮截图和独立评审。不得用单测/解码判视觉通过。

| ID | 页面/根因 | 采用方案 | 状态/验收 |
|---|---|---|---|
| V01 | 详情视频28dp裁剪与另一半径边框不一致，白边四角断裂 | Media3 PlayerView原位保留；统一View圆角/前景边框几何，完整视频渲染实际观察 | OPEN |
| V02 | 详情右区288dp、视频243dp，按钮底与选集起点参差 | 左右同高243dp、统一48dp安全线、选集一条基线；长标题/缺meta/缺图 | OPEN |
| V03 | 首页logo/nav垂直偏移，自绘按键与Compose焦点混用 | TV Surface + View宿主入口；Box中心文字，logo固定同高对齐 | IMPLEMENTED/待截图与Back边界 |
| V04 | 搜索建议/结果/筛选挤贴 | 保留Leanback列表；按540dp可用高分区、紧凑4列、明确12dp分隔 | IMPLEMENTED/长查询与普通两源 |
| V05 | 列表双重zoom、焦点边缘裁切 | Leanback只负责导航，单一1.04/120ms卡片动画；真实focus安全padding、尺寸计算同步 | IMPLEMENTED/各页首尾卡与上下边缘 |
| V06 | 设置图标贴边、父行白色大块、主题行挤压 | TV Button/Surface成熟交互，深灰白字细边、不缩放，独立主子焦点与16dp内容padding | IMPLEMENTED/全部设置类+长按+主题 |
| V07 | 共享原生chip/button与Compose焦点样式不一致 | 共用中性control palette，保留现有MaterialButton/Chip/Dialog；移除白色大块与多层描边 | OPEN |
| V08 | 播放控制/选集/抽屉手写focus和缩放可能裁切 | 成熟TV控件局部采用，保留播放状态/服务/Layer Back顺序；边界留白 | OPEN |
| V09 | 我的/推送按钮、QR、文件/投屏/弹窗不同规则 | Tv控件+共享原生palette；工具页统一页边距，QR内容白底与焦点分离 | IMPLEMENTED/部分；全页待评审 |
| V10 | 发现/片库/收藏/完整历史的边缘放大与标题截断 | 复用Leanback+统一focus余量；内容尺寸扣减，空/长文/缺图实测 | IMPLEMENTED/待独立评审 |
| V11 | 加载/空数据/错误配置入口缺少全套画面审查 | 现有Progress/Empty/Dialog入口保留，统一文字/按钮与48dp边界 | OPEN |
| V12 | 验收将功能/解码混同视觉，漏掉组合路径 | 同内容同焦点普通/选中/长文/空/缺图/边缘/弹窗逐页截图；独立评审，真实Dpad完整路径另记 | OPEN |

## 成熟组件边界

不全面迁移Leanback或播放器；当前BOM不大升级。TV Material负责Compose单一操作焦点/按键/语义，原生使用已存在MaterialButton/Chip及Leanback导航。Host跨View/Compose边界保留必要桥接，禁止父子clickable叠加或重造焦点树。具体接口见VISUAL-COMPONENT-PLAN.md；页面实现细则见VISUAL-HOME-SEARCH-PLAN.md与VISUAL-SETTINGS-PLAN.md。

## 全页面验收矩阵

首页/继续观看、片库/分类筛选、发现首页/详情/结果、搜索输入/建议/来源/结果、收藏/完整历史、详情/选集/线路、小窗/全屏控制/设置抽屉、直播频道/分类/节目、设置每类及主题、我的/推送/投屏/文件、源选择/编辑/一般弹窗、加载/空/错误：每类记录普通+焦点+边缘，适用时加入长文/空数据/缺图；同内容同焦点baseline与after，记录无法复现状态而非假通过。

真机、ARM真实播放、Python/MPV、硬解/真实投屏限制不变；软件模拟器性能单独记录，旧99.2% jank不代表真机，也不能宣称性能通过。


## 首轮代码与审查增补

- V01/V02：R1已统一video背景/前景16dp形状、1dp边线、左右243dp等高、选集48dp行高，待真实渲染和同焦点图。
- V03–V07：R1已使用TV Material及共享深灰白字palette，移除双缩放和原生列表放大；TypeFragment运行时padding已8/12/8/48且尺寸扣减，不再归零。
- V08：R1详情/ChipRow/播放操作使用TV Material按钮与Surface，View边界桥接保留；功能仍待Dpad路径。
- V09/V11：R2移除电视旧空态彩色无限动画/面板，简洁静态icon与准确收藏文案，未同步验证。
- 监控AUDIT V08横图竖槽：R1继续观看采用112×84横槽、R2完整历史高度同步112dp。
- 新P2：主题勾选增加22dp导致邻项跳位，独立review发现，R2已预留固定槽，待OK前后位置截图。
- 新边界：首页首卡y0裁顶已直接查看基线；R1非Hero selected row使用16dpkeyline，必须看after关闭。

PR #62已转草稿，旧功能验收不代表本轮视觉完成；本轮仍不提交完成标志。

R2独立静态复核增补：HomeActivity.getHistorySpec遗漏124dp覆盖已修112dp；ChipRow相同文本更新选中集数时真实焦点未同步已补entry token与滚动后的归属校验。两个修复待独立复核与真实遥控回归，不记VERIFIED。R2目前12文件。

## 播放器基线独立复核增补

- V13：设置抽屉只显示1.00/原始/EXO等值，缺功能名；已直接看基线并由源码确认title未渲染，正补标题+当前值，仍为同一个TV Surface操作节点，未运行验证。
- V14：旧抽屉打开后底部设置active背景撑宽；当前ControlIcon已无fillMaxWidth/weight及selected Canvas，静态暂未见旧根因残留，必须同内容开/关抽屉after确认，不写已修复。
- V15：无Hero普通行选择回调将80dp keyline改16dp，R2B已改max(paddingTop,dp16)，独立STATIC PASS，运行待验。

R1实图：home-posters-first顶部描边已完整但下半屏空白未解决；search-first-poster海报/标题左裁已改善，却新增建议区越过128dp viewport覆盖结果标题/状态/来源的BLOCKER。root已修共享JetStreamPageContentScrollView启用clipChildren/clipToPadding，保留内部padding，作用于搜索/详情选集/AI字幕设置，待独立review与下一轮三页回归。R2C不可作为最终候选，下一轮待冻结。

## 2026-10-03 R2H 交付候选复核

当前状态以 `VISUAL-REWORK-DELIVERY.md` 和仓库 `docs/ui-redesign/visual-rework/README.md` 为准；早期 OPEN/IMPLEMENTED 表是过程记录。

- V01/V02：所见圆角、边线、对齐及真实长标题画面已验证；缺 meta 等全组合未关闭。
- V03/V04/V05/V15：R2E 独立实图确认导航/搜索/首卡改善；长搜索与所有列表三态覆盖未完整。
- V06/V07/V09：R2F 文件双重焦点关闭；七主题按钮焦点/宽度独立实图通过，其他各类设置已走查，全部弹窗/权限分支未完整。
- V08/V13/V14：R2F 抽屉名称和值可见，首末项可达；R2G 全屏返回 WATCH 与窗口焦点实测通过。倒序专项因自然换集未形成有效通过。
- V10/V11：片库真实路径已补；R2H 修复发现失败空白并验证缺 key 空态/长按确认/Back。发现在线成功态缺 key 未实测，保持 OPEN。
- V12：已区分构建/视觉/遥控/真机证据，排除无效权限截图与旧 XML。全矩阵覆盖仍 OPEN，PR 保留草稿，不生成全通过标记。
