# 首页、搜索与复用列表视觉重做方案

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

状态：已直接查看用户 IMG_1661、IMG_1662、IMG_1663，旧视觉验收不合格；此文件不是通过记录。

## 缺陷与共享根因

- 首页 logo 与导航文字视觉中心不一致：导航 Column 在文字下方始终保留 3dp spacer + 2dp 指示条，使文字中心上移；焦点还将 42dp 目标缩放到 host 48dp 内。导航 host 自管左右/OK，同时子 combinedClickable 又可聚焦，形成两套焦点状态。
- 搜索显示结果后建议区固定 104dp，标题 + 下间距 + 建议行 margin/padding + scroll底部不足，建议底边紧贴结果标题。
- 来源筛选与首排海报缺独立安全区。CollectFragment 将所有左右 padding 清零，卡片宽度按整个 viewport 平分，没有预留焦点向外放大的边距。
- Collect/Type 默认 CustomRowPresenter 启用 Leanback SMALL 缩放，卡片自身 Animator 又缩放 1.08，反馈存在重复所有者；首页/发现虽关闭 Leanback zoom，仍采用过大的卡片 1.08。
- ViewPager 页边是裁切边界，仅设置 clipChildren=false 不能替代内容内实际焦点余量。复用列表应修几何而不是任意关闭全部裁切。

## 采用与实施

保留 Leanback ListRowPresenter/GridView 的方向导航、选择与滚动；默认关闭它的额外 zoom，交由既有单一卡片动画提供约 1.04/120ms（全局常量由主代理协调）。每个结果页左右至少 8dp 实际安全区，卡片宽度计算扣除该区。紧凑搜索采用4列，以便128dp建议区之后完整显示海报与标题元信息；外部搜索和片库沿用原列数。共享行网格取消子裁切，保留既有 horizontalInset，首页/发现原48dp keyline不变。复用 fragment_type 布局的片库/收藏/外部结果一并受益；检查独立历史/发现列表是否已有足够 padding。

首页导航采用既有 TV Material 1.0.0 wrapper，把导航交互统一到成熟 TV Surface，并保留 View/Compose 焦点交接与长按当前源功能；42dp目标不缩放，文字Box几何居中，选中指示绝对底部定位不参与文字中心布局。toolbar/logo/nav 显式垂直居中。

搜索建议区调整为完整单行可见并留底部间距；建议多行仍由成熟 RecyclerView/NestedScrollView 滚动。右区 title/status/filter/pager 各有独立固定间隔。筛选首末目标和海报首末边界都在自身viewport内预留8dp，不靠越界绘制。

## 待云端视觉与遥控验收（尚未执行）

同一个 fast 查询，普通/首建议焦点/末建议焦点/来源首末焦点/首末海报焦点逐一前后截图；长中英文建议、长标题、无来源、无结果、缺图、3列满行及多行滚动。遥控输入→建议→筛选→海报→详情→Back保持位置；再次修改查询验证异步不抢焦点。首页logo与6导航中心线、每一项普通/选中/聚焦、长按换源、无直播配置5项、下进Hero/无Hero列表再Back恢复。片库/收藏/历史/发现首末行焦点与缺图长文须独立画面检查。

本代理不构建、不使用云端、不操作模拟器、不提交推送。源码完成只记录静态检查，实际视觉结果由后续独立QA给出。

## 本地源码完成记录（待独立复核与云端验收）

- 首页导航已使用 TV Material `TvFocusableSurface`，删除 host 手工左右/OK/长按分发，View 焦点进入后只转交一次 Compose FocusRequester；异步转交须 host 自身仍聚焦且已挂载，避免抢回新焦点。下划线改为overlay，文字固定居中，toolbar logo/nav明确center_vertical。
- 搜索建议高度104→128dp，移除建议/记录负margin（保留item自身8dp）；来源条左右8dp；pager上间距8→12dp；结果标题单行省略避免长查询挤占卡片。
- Collect/Type结果左右8dp、顶部12dp，尺寸同步扣16dp；compact搜索4列、底部24dp，其他保留原列数/底部。
- 收藏、完整历史、发现结果同样加入8dp左右/12dp顶部焦点安全区，明确同步扣减card宽度，防边缘裁切。未改其数据库、异步任务、播放进入与返回焦点guard。
- `CustomRowPresenter` 默认禁用Leanback额外zoom，保持它的导航/选择/滚动；明确row和grid不裁clipChildren；卡片动画单一1.04、120ms。
- 已静态确认 JetStreamPageSurfaces 的 pager/grid/progress/scroll 根本身已禁clip；没有为修局部问题额外关闭页面裁切。
- 检查：`git diff --check`通过；6份变更XML由Python ElementTree解析通过。未构建、未在模拟器运行，未声称视觉通过。

特别回归风险：TV Material导航必须逐一验证冷启动、nav下进Hero/无Hero列表、Back/nav重新requestFocus、左右边界至换源、长按换源只触发一次；普通D-pad与Enter路径都需要。本次以成熟组件接管不等于已验证混合View/Compose焦点。

### 修后SHA256

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/DiscoverResultActivity.java` `555ea4daad5928037bdd2d8a547921a5a6e009ce90409e57ee2df89a7a23a4ba`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SearchActivity.java` `5d5c846cb8ea39e14922f03bfef22c6870de2cf1d13cb0560f3c4090d52ac0eb`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SearchResultsController.java` `9a88bc301582e36deb2b83406fe931f79a2c86374770da200d94829b44550a8a`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/WatchHistoryActivity.java` `9602b6528836d4bbba1cdb9e818693f8016de58ad23c21fad88fdb228b4e67ae`
- `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/KeepAdapter.java` `dd50dc08644cb68ce6ab7a1d41d2cd4ae58b1eb515223419d922e00a8c32d632`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomRowPresenter.java` `f94ecb7059a5895d2d6e641a55e67dda1d408667b90858f9b0ca5ee16f249f7d`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamAnimator.kt` `31b163d99b8324697ac8bccd1bbec8f1975e62bb1dd7739261af33de03aa6091`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamDialogSurfaces.kt` `a729fb3f59a713add34c6bf651aa382752c1af67cce2265c24df38b184cb9446`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamHomeNavView.kt` `989af3c139070f430a487f919ee0510701411349f515d48b0849bad4c21ef8b5`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamPushView.kt` `deed60225ccb8ef2d43686896021fecb57bbb63209afbcf8853b06d7f5903b7b`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamSettingView.kt` `ee9d0dafae85febe1e27c4e12957e4e0bd2ce2b4ba0a497e6f3906c6007185d6`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/MyActionView.kt` `9b6c0baf4d47495f764e0ab33f95fd3570c0aba818fb83e2edfab9b9b15b1b6e`
- `app/src/leanback/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java` `11bbb6cd14c017d0a55006a5dd6f5d6412c3ef5c1ddd778724a71d2e17106b2f`
- `app/src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java` `3102590f92109d2946826bc7fadabb0484ead1d0e82dc5bd50c9fd2957bae1ad`
- `app/src/leanback/res/layout/activity_discover_result.xml` `51bbcc4748fef07b32568434d7c527e86b079d9a5c2ecd57ab55fbf1ac31d4e4`
- `app/src/leanback/res/layout/activity_home.xml` `fa1f1731253018a2b4947914d4ebf47f6d752eeae26304324a1c28eef4fc7d84`
- `app/src/leanback/res/layout/activity_keep.xml` `b6f2e851f7ba68918131d6e879bff48c37e7c891beb684cd10de16e4e0642c2b`
- `app/src/leanback/res/layout/activity_search.xml` `90155c0e938ba38e98d410eda229b8e548e38b98dc64ce24d581a72b1128f42b`
- `app/src/leanback/res/layout/activity_watch_history.xml` `04cd7b854bcd0efa2a9b50c68f9c6b8bf35cb3cad4cfda11d4e3c6f8516b3637`
- `app/src/leanback/res/layout/view_search_results.xml` `f32d43cf8d7269d8c77ff450a594e0a7633588f29a1a97e33c6cfac3b84733e0`
