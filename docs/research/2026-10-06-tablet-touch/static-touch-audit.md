# TV 页面触屏只读审计（2026-10-06）

> 研究快照（2026-10-06）：保留分析过程，不作为完整构建或设备验收结论。

范围：首页、片库、详情、设置；没有修改项目代码、没有操作设备。本结论是静态审计，不等于平板实测通过。

## 结论

当前 TV 版不是完全不能触控：普通 View 卡片、RecyclerView、ViewPager 和 Compose 列表已有基础触摸与滑动。但主要 Compose TV 按钮未处理普通指针点击；再加上焦点恢复和播放进度控制，顺畅的纯触屏测试需要一组适配与遥控回归，而非单一开关。按用户“若麻烦或影响正常遥控就算了”的约束，建议本轮维持 TV 版，偶尔在平板用蓝牙键盘/遥控测试。代码有 mobile 源集，可另作触屏版本，未在本轮构建验证。

## 关键证据

- `gradle/libs.versions.toml:26`：当前 `androidx.tv:tv-material` 精确版本 1.0.0。
- 已读取该版本 Google Maven sources JAR。`androidx-tv-material-1.0.0/Surface.kt:410` 中 `tvClickable` 仅串接 `handleDPadEnter`、`focusable` 和 semantics，不含 pointerInput 或 foundation clickable；普通触屏不会因传入 onClick 就自动可用。
- `TvFocusComponents.kt` 的 `TvActionButton` 和 `TvFocusableSurface` 是主要共享入口，覆盖首页导航、设置行、片源/集数、播放器按钮、推送页。点击层可通过仅处理 pointer 的桥接小范围补齐，保持 TV Material 外观与键盘逻辑；不能叠加第二个可聚焦 clickable。
- `JetStreamVodDetailView.kt:383/391` 直接使用 TV Button / OutlinedButton，需单独补齐。
- `MaterialSettingsButton.kt:53` 也直接用 TV Button，但本轮搜索未找到生产调用点。
- `VodRectHolder/VodListHolder/VodOvalHolder`、`TypeAdapter`、`FilterPresenter` 已有标准 View 点击回调。
- 设置 `LazyColumn`、主题色 `horizontalScroll`、片源集数 `LazyRow`、片库 ViewPager 和 RecyclerView 保留框架基础滑动。`CustomScroller` 按 SCROLL_STATE_IDLE 判断底部加载，不要求键盘事件。

## 需要回归的焦点/滑动风险

- `VodActivity.java:108` 的 onPageSelected 无输入模式区分，横划分页也走 requestRecyclerFocus，50 ms 后把焦点交给顶部分类 tab。是否出现可见回拉需设备验证。
- `HomeActivity.java:368` 的 requestNavFocus 会滚回第 0 行；异步焦点恢复没有 isInTouchMode 分流，可能在触摸操作中抢回焦点或滚动位置。
- `TypeFragment.java:265/367` 展开筛选、页面重新显示后恢复 selectedPosition 和焦点，也没有触摸模式分流。
- `CustomRecyclerView.java:114` 的 scrollToPosition 50 ms 后自动请求目标焦点；该类不是所有片库列表的基类，不能把风险概括到每一次滑动。
- 多个 XML 卡片为 focusableInTouchMode=true。系统默认 View 首触先聚焦等行为应在纯触控/遥控切换时验证，不能只测 semantics.performClick。
- `CustomVerticalGridView` 的隐藏/显示页头主要依赖方向键 pressUp/pressDown 状态，触摸滚动不会有同等行为；遥控与触屏混用时旧键盘状态也需检查。
- `CustomRecyclerView.dispatchTouchEvent` 已有单指嵌套滚动拦截；多指直接返回 false，不是完整手势适配。

播放器进度条与视频手势由 root 独立审计。本轮未实现任何适配。
