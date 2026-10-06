# 抽屉功能名称与当前值 独立静态复核

结论：STATIC PASS。没有发现此次小diff引入的确定功能回归；不代表编译、运行或视觉通过。只读审查，无云端操作、构建或源码修改。

依据：读取VISUAL-DRAWER-LABEL-FIX.md；从不可变visual-r2b-payload.json解码Control原文，在内存与当前文件作diff；三语言strings对比git diff。

## 逐项结论

- title优先：command.title非blank时trim返回，不会被默认映射覆盖；只有空title按speed/scale/player/decode/opening/ending等key补名称。未知key返回空title，显示原label，不会丢失原有文本。
- 去重：title与trim后的value相同只显示一次；原label前后空格不会破坏去重。非空不同值显示第二行；名称15sp/20sp，值13sp/18sp，2dp间隔。
- OP/ED默认：使用本地化R.string.play_op/play_ed比较，与VideoActivity实际设置默认占位的同一resource一致；简中片头/片尾、繁中片頭/片尾、默认OP/ED均会抑制重复占位，显示Skip intro/outro或对应中文功能名称。设定Util.timeMs时间值后不匹配占位，显示名称+时间。
- 本地化：新增6个key在默认/简中/繁中齐全；edition/chapter/text/audio/video/parse继续引用既有本地化资源；无本轮硬编码中文名称进入默认语言。三份XML解析和name唯一检查通过。
- 几何：Surface新增heightIn(min=40.dp)是下限而非固定高度，不会裁掉名称+值两行；原LazyColumn继续滚动。Drawer宽度及contentPadding提供有限横向约束，Text单行ellipsis有效。单独名称允许两行。
- 回调/焦点：与不可变R2B逐行对比，onClick、onLongClick、command key、selected、FocusRequester以及onFocusChanged均保持；只增加minHeight及内容布局/名称映射。没有改PlayerView/服务/原Action调用路径，也没有改抽屉进入退出代码。

## 必须运行确认

三语言同焦点截图、默认/有值OP/ED、长显式title和value、省略与末项滚动；VOD/Live功能名和值正确对应；OK单次、长按复位、Back返回；底部设置按钮开/关抽屉时几何。旧R2B画面不覆盖此新源码。

`git diff --check`通过。说明文件里Control修后SHA仍为root补minHeight之前的9ced…，本次最终审查使用下列fa193…，后续冻结须以此最终值为准。

## 最终SHA256

- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamVodControlView.kt` `fa19307e9af6786e0038d1578e3f2b36db1f47b0a88664a12aa389605ce2a7f0`
- `app/src/leanback/res/values/strings.xml` `299b34d483b9da54d527baabcee1fcb9a74da627a47876b195979168b98bcd4f`
- `app/src/leanback/res/values-zh-rCN/strings.xml` `2643fbe15a45f0a3ab99be49253dea0354b03bfa4ae01af8b75a17abe5a2b278`
- `app/src/leanback/res/values-zh-rTW/strings.xml` `d9525c6f0e01961b800ffbd1b1de9fc0a05d11efca02a73107ad8169cb397234`
