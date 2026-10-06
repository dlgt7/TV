# 抽屉功能名称修复

状态：本地保存，待下一轮冻结/构建/运行验收。未修改R2B冻结包，未操作云端，未提交。

## 问题与机制

直接查看baseline/player-drawer.png确认“1.00/原始/EXO/硬解”等值缺功能上下文。当前CommandState已有title，两Activity通常调用不带title的重载。

仅修改JetStreamVodControlView.kt与leanback默认/简中/繁中文案：优先使用显式非空title；否则按speed、scale、player、decode、opening、ending提供明确名称，edition/chapter/text/audio/video/parse复用现有本地化功能名。未知命令保持原label。名称与值相同不重复；片头/片尾默认OP/ED占位只显示“跳过片头/跳过片尾”，设置时间后显示名称和实际时间。

同一TvFocusableSurface内以名称15sp/20sp、值13sp/18sp分两行，2dp间隔、纵8dp/横16dp内距，长文省略并由原LazyColumn滚动。未改变命令key、onClick/onLongClick、选中状态、FocusRequester和Live/VOD业务回调。

## 底部横条只读审查

当前ControlIcon的显示标签分支仅height(40dp)、内容padding；没有fillMaxWidth、weight或selected Canvas。selected传入TvActionButton仅改变样式，外层HeaderRow的fillMaxWidth属于整行正常布局。此前baseline中设置active横条不能据此宣称视觉已修复，仍需候选开/关抽屉同焦点截图；本轮不为无源码依据的风险改布局。

## 文件SHA

| 文件 | 修前SHA256 | 修后SHA256 |
| --- | --- | --- |
| `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamVodControlView.kt` | `473456228525f3f8ef7111c66e1fdc30e15e99e71a0f717e57fdb1398617c9dd` | `fa19307e9af6786e0038d1578e3f2b36db1f47b0a88664a12aa389605ce2a7f0` |
| `app/src/leanback/res/values/strings.xml` | `9e09aa25045964856acf8c2d0c748da4892288794140227e8c92993acc43c44a` | `299b34d483b9da54d527baabcee1fcb9a74da627a47876b195979168b98bcd4f` |
| `app/src/leanback/res/values-zh-rCN/strings.xml` | `298a603c5e3b15e2daa832a154de6496fd8574ba88347fe6ab2eba2ccabb7982` | `2643fbe15a45f0a3ab99be49253dea0354b03bfa4ae01af8b75a17abe5a2b278` |
| `app/src/leanback/res/values-zh-rTW/strings.xml` | `7ecca47d26e99ef6e3c17eeaa67b90bc900dbdfda24f49fbffbef6adb2ce8fff` | `d9525c6f0e01961b800ffbd1b1de9fc0a05d11efca02a73107ad8169cb397234` |

## 已验证与待验证

已验证：git diff --check；三份XML可解析且资源name无重复。未执行构建/测试APK/模拟器。

待下一轮：简中/繁中/默认语言各看名称+当前值；默认片头/片尾不重复、设定时间后显示值；显式title优先及同文字不重复；VOD/Live速度/比例/内核/解码显示准确；长名长值两行不溢出，末项可滚动且焦点边不裁切；OK仍单次动作、片头/片尾长按复位、Back回到播放按钮；开关抽屉时底部设置按钮尺寸位置保持。证据需绑定本轮最终SHA，旧截图和R2B不算该修复通过。

root复核后补充：同一TV Surface添加heightIn(min=40.dp)，单行有最低高度、双行随内容增长；已更新最终SHA，独立复核中。
