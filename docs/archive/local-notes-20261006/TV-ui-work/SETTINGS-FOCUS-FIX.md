# 设置复合行 D-pad 焦点修复（待云端回归）

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

- 本地文件：`/home/ubuntu/TV-ui-redesign/app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamSettingView.kt`
- SHA-256：`dfdc063d7532e95d8f57e943161397806ff1e67587a0f089cc78ad66bfb4a514`
- 仅修改此生产文件；未提交、未推送、未构建、未操作模拟器。

## 原因与修复

复合行的整行 combinedClickable 创建可聚焦父节点，右侧 ActionChip 是其后代；Compose 1.7.4 二维焦点搜索不会从父焦点 Right 进入这些子节点。theme_color 的父点击没有业务动作。

- 有 ActionChip 的行外层只保留布局/背景及已有 FocusRequester，不创建可点击/可聚焦父目标。
- 主操作移至左侧 label/value Column，与右侧 ActionChip 同属兄弟焦点；保留 VOD/LIVE/WALL 及日志等行的 click/long-click 原事件。
- theme_color 左侧 label 不可点击且不可聚焦，五色 ActionChip 是该行实际操作目标。
- 无子操作的普通行/开关仍维持整行点击及长按。
- 复合行左侧主操作聚焦时仅左侧白底深字并缩放；右侧按钮独立显示焦点，移除依赖旧父焦点的文字/描边着色。单操作行维持原焦点样式。
- 未增加 Tab 专用绕过或拦截方向键；D-pad/Tab 使用同一组实际焦点目标。原首行 FocusRequester 附于无焦点外层时指向第一个可聚焦后代。

## 已做与待做

本地仅静态复核与 git diff --check 通过，确认 JetStreamShapes.Medium 已存在；无编译/运行通过声明。此前105源码清单已因本修复失效1项，最终必须刷新哈希与构建/QA报告。

QA 按已授权流程单文件同步、必要构建/重装，确认同步哈希后验证：主题五色 D-pad 进入、左右切换与 OK 选择、退出/重入及冷启持久化、左右/上下边界与返回；VOD/LIVE/WALL 主操作 click/long-click 和右侧按钮；普通值行及开关；聚焦样式。最终 qa-recovery.complete.json 必须覆盖此哈希及回归结论。
