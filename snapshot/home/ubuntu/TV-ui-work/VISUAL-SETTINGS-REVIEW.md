# 设置/弹窗/推送/我的 独立静态审查

结论：PASS（静态结构与API审查），附1项应在视觉验收前完善的P2外观项。不是编译通过、遥控通过或最终视觉验收标记。只读审查，没有改源码、构建、云端操作。

## 确认范围

- 已读 VISUAL-REWORK-AUDIT.md 与当前源码diff；本代理未实现此处被审的设置/弹窗/推送/我的改动。
- API：TV Material1.0.0官方Surface/Button源码与wrapper签名一致；既有Compose1.7.4 API使用无明显静态不兼容。MaterialButton的insetTop/insetBottom/iconPadding/shapeAppearanceModel均为项目既有Material Components接口；toArgb只颜色转换，不引入API24以上图形调用。正式依赖解析/编译需云端完成。
- 设置：复合配置行的主操作Surface与辅助按钮Row是非focusable行下的兄弟目标；父容器已无clickable，不复现此前可点击父行拦截theme子chip根因。主题label无动作、横向scroll内五个按钮独立。Switch onCheckedChange=null仅显示状态，行Surface为单一操作目标，未引入嵌套toggle焦点。
- 设置长文：主label和value各单行省略；普通行label weight、value max260dp；配置辅助动作是既有固定本地文案，主区域weight吸收剩余宽度。主题长英文名称允许横向滚动，需实测首末项自动带入/上下退出。
- 图标内边距：ActionChip 20dp图标+8dp间距+横14dp；推送复制按钮44dp容器/10dp内距/22dp图标，满足44-20=24dp可用；MyAction 24dp外内距+30dp图标+20dp文案间距，无明显贴边裁切。
- 推送：复制/推送/QR改为单一TV目标，不叠旧clickable或scale；复制地址可省略且独立复制按钮完整；QR白底内缩6dp保留焦点框，白色只是二维码必要背景。
- 我的：一个native可点击父目标、FOCUS_BLOCK_DESCENDANTS，图标与文字duplicateParentState；标题单行/描述最多两行ellipsis，未引入新的子焦点。
- Palette：presentationPalette primaryContainer改深灰时onPrimaryContainer同步改浅色；controlText焦点文字与controlContainer/Outline相配，不存在该路径黑字+深灰面组合。主题accent仍由tertiary等角色携带；五主题反馈应由真实截图确认，不能因token存在推定。

## P2建议（源码可确定，非功能阻断）

`JetStreamSettingView.kt:436-438` 仅selected主题插入6dp Spacer+16dp勾选图标；按OK选择另一色会令旧chip缩窄22dp、新chip加宽22dp，其间chips水平挪动。此前实现也有同样条件，但本轮追求固定焦点几何应清理：对有swatch的所有主题按钮预留22dp选中指示槽，仅切换Icon显示/alpha；不要给无swatch的普通动作额外空槽。用同焦点按OK前后截图验证位置不挪。

## 必须由本轮运行证据覆盖

设置分类→主配置→每个辅助动作→弹窗→Back恢复；主题五色左右到边界、OK持久化、Up/Down退出；开关行只触发一次；列表第一/最后行与长URL；英文长主题名称滚动。推送复制/剪贴板失败与成功反馈、QR打开/返回；我的6入口上下左右及长说明。MaterialAlertDialog正负按钮、字幕图标、弹幕4等分tab、checkbox checked/unchecked都需真实焦点图。

## 被审SHA256

- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamSettingView.kt` `ac8e12f2c069a300f2ac47aeb4b439cd41c9d7e30716b70fc2c63d36f0fc8d35`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamDialogSurfaces.kt` `b0cb08eebb0228e735fb764354218fa4d99b429b176ac9eb9d679861589be0cc`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamPushView.kt` `a5dd3d47858f33742c57eea6d5281f9642e86a21ca7946756bdaeddf5e3ae571`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/MyActionView.kt` `9f02d7547335ba62c21417e248cd7675fc0dd337b10bcf50d0f94036fe3a7a7c`
- `app/src/leanback/java/com/fongmi/android/tv/ui/theme/JetStreamPalette.kt` `1449fa0ab7ca710c3de0e88f9f89235151b63d122d0e6534feeae3ed50f45e83`
- `app/src/leanback/java/com/fongmi/android/tv/ui/components/TvFocusComponents.kt` `f2dc665e7af08c051e7549641746183dd5a0318be69a00328ef4672a2469d3bb`
