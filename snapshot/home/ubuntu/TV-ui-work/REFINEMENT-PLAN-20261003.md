# R3 视觉精修与浏览流程

基线 a9c65efa9（R2K）。用户明确要求：海报焦点/普通态都使用现有普通态圆角；更清楚的焦点描边；排查并修复按钮图文不齐与边缘裁切；首页来源推荐多行向下浏览；搜索先建议后独立全屏结果。追加首页右上站点入口去掉突兀外框。仅 ui/apple-tv-redesign，保留 SourcePluginRuntime 修复/API24/播放器所有权。

确定根因：普通海报 ShapeableImageView 16dp，foreground 8dp；继承图片的 DiscoverHeroPoster 同样不匹配。不是 Glide 圆角变换。原生chip的通用文字初始化未设置字体padding/垂直gravity，部分图标的14/8dp内边距使图形可用区域不正方；TV Material 按钮只桥接 M3颜色而未桥接标签排版，内层M3 Text使用正文行高。水平/垂直滚动焦点余量不一致，部分滚动容器无上下余量。

实施：共享16dp海报参数；海报3dp描边、按钮2dp描边；原生控件中线与正方图标区域；TV Button标签排版；有界scroll继续clip，内部预留focus空间。站点入口透明常态+下拉提示，聚焦用柔和底色/细下划线，保留最大宽度与长名省略。首页复用Leanback ListRow按列数分行（不再所有海报塞进一行）；搜索复用已有CollectActivity/ResultsController全屏结果，SearchActivity只输入/建议并保存返回焦点，避免嵌入结果分栏。

验证：已有组件真实使用核对、静态独立复核、云端构建/单测/lint；隔离模拟器重点同内容普通/焦点图、首页首中末行、设置图文与边缘、搜索建议→全屏→详情→Back保持焦点/关键词；三星覆盖安装后原源启动和代表页面复核。不会把编译或截图文件名当作全视觉通过。运行证据覆盖与限制单独记录。
