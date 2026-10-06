# Material 3 设置与播放面板示范

用户已同意先在设置页与播放侧边面板展示更接近官方 Material 3 的风格，再决定全局推广。保留已有 R3 圆角、搜索、源崩溃隔离和焦点修复；不改播放引擎。

- Compose 使用真实 AndroidX TV Material3 Button：Filled/Tonal/Outlined/Text，保留D-pad、长按和选中语义。
- XML 字幕操作、播放选项和工具按钮使用 MaterialButton；保留回调与id，无自绘点击处理。
- 示例局部使用原始主题accent，不再经过presentationPalette中性化；全局海报/首页主题不变。
- 48dp按钮最小高度，20/24dp水平留白，12dp按钮间距，图标20dp+8dp文字间距。
- 播放面板28dp圆角/实色surfaceContainer；按钮按用途24dp/12dp，焦点不改变圆角或尺寸。
- 主操作填充，辅助操作描边/文字，选中和焦点分开表达。

构建轮次 R4A，云机 tv-r3-build-q7jwxvq6r4j5f9xrj。当前原包构建+unit+lint退出0，preview构建中；等待实际截图和三星更新，不作视觉完成声明。
