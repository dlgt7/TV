# 设置、我的、推送与弹窗视觉修复

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

已直接查看 IMG_1664/1665：旧设置主操作白色覆盖整块区域，辅助图标靠近裁切边，主/子操作的视觉权重失衡。旧视觉验收不合格。

采用现有 AndroidX TV Material 包装 TvFocusableSurface / TvActionButton 替换 Compose 手写 clickable+scale+clip；主操作与辅助按钮保持兄弟焦点，主题标签不可点。主题选择独立完整宽度，预留滚动边界。分类和主操作深灰细描边，无布局放大；图标固定20dp，按钮水平14dp以上内边距。

我的保留原生 LinearLayout 单焦点和业务监听，使用与共享组件一致的中性色/细边、不缩放。推送动作采用成熟 TV Button，保留复制/推送成功回调和初始焦点。原 MaterialButton/Chip/Dialog/RecyclerView 保留生命周期、原生导航和事件；统一半径、内边距及取消缩放，避免 popup 中按钮出界。

待验收：设置所有分类从上到下、来源主操作与首页/历史左右往返及长按、五色切换持久化/边界退出、长URL与隐藏设置；我的每入口与返回；推送空地址/长地址/复制成功/剪贴板失败；配置弹窗主按钮与钥匙/禁用操作、输入/列表/确认取消和Back焦点恢复。每条需要同内容普通/焦点前后图；源码完成不等于视觉通过。本代理不运行构建或模拟器。

## 已落地（源码阶段，等待独立审查/云端编译和视觉验收）

- `JetStreamSettingView.kt`：TV Material分类、主动作和辅助按钮；来源72dp组行、普通设置60dp行；主题独立整宽条；viewport内4dp边界，图标20dp与14dp水平按钮padding；保留原action keys/长按/初始焦点/隐藏恢复。
- `JetStreamPushView.kt`：复制、剪贴板、可点击QR采用TV Material；成功反馈仍仅由真实回调触发，QR内部白色底保留扫码静区。
- `MyActionView.kt`：保持原生单焦点，共享palette中性细边，不放大；长标题/描述省略，24dp内边距保留。
- `JetStreamDialogSurfaces.kt`：原MaterialButton、Chip、AlertDialog按钮中性焦点，10dp角与按钮内边距；取消列表控件放大；保留所有窗口/Back/导航实现。原生颜色统一调用主执行代理负责的JetStreamPalette，未另建颜色状态体系。
- 未修改播放器、设置业务Activity、任何分支或云端状态。
- `git diff --check`通过；本轮没有编译、设备或视觉PASS声明。

原生Live配置弹窗的主条/钥匙/禁用按钮由共享JetStreamLargeChipRoundTextView与JetStreamChipRoundIconView提供，已通知主执行代理纳入全局控件样式修复；此处不能遗漏。用户图中此弹窗仍需单独前后截图。
