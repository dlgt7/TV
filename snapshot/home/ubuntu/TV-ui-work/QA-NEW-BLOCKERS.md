# 当前必须处理的两个UI问题（2026-10-02，QA尚未结束）

1. 无配置空状态标题与顶栏重叠。云端 final-qa/recovery-no-config-clean.xml：标题[96,56][1824,130]，nav[200,32][1736,128]。功能选择源→设置正常，但布局不通过。只读复核建议：HomeActivity用小helper统一无hero时的recycler paddingTop/windowAlignmentOffset=80dp，hero时=0，覆盖初始化/setFeatured/removeFeatured；不能只改EmptyHome内部padding。根代理已收。
2. 组合旅程中选主题颜色会进入CrashActivity。步骤：Home→Search(fast)→结果详情→Back→Live→设置源页直播主页dialog→壁纸配置dialog→应用主题色彩→B站粉。独立冷启主题七色与持久化通过，但该组合路径在curated_retake出现真正“意外错误”页，云端 fixed-theme-2.xml/png（已覆盖原正常图，保留异常证据），主题pref已写入-39271，之后冷启恢复。theme-sequence-logcat.txt确认03:22:47启动 com.fongmi.android.tv:error_activity/CrashActivity。原logcat详细throwable被chatty压缩/过滤，正用增大buffer并在错误页点错误详情重现。当前执行session10540（qa_recovery.py theme_sequence_repro），不要同时碰模拟器。

已完成：设置焦点修复dfdc063d…a514增量构建/216单测/lint；105源码SHA匹配；七色D-pad及持久化；共享行主子按钮；真实选集/51秒续播、源切换、直播频道、慢搜旧结果隔离、Back层级、空历史收藏、缺图长标题、快连按后台；17录像全部ffprobe+全帧解码通过。性能模拟器99.2%janky，仅观测不能算性能通过。

当前没有 qa-recovery.complete.json。不得据旧阶段报告写全验收通过或提交最终交付。
