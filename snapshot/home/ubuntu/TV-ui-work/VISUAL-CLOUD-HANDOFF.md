# 云端恢复最短执行交接

目标：恢复真实R1前后图，随后精确同步R2C并增量构建、全页视觉/D-pad回归。最终没有视觉通过前不提交完成标志。

## 所有权
当前仍 /root/visual_cloud_qa。监控已确认原owner恢复实际活动，换owner待确认事项已撤销。无其他代理操作模拟器。

## 确认事实
- Codespace tv-build-5g6j76qj97vg37wpr；root于13:58 UTC只读GitHub API返回Shutdown，last_used_at=11:37:11Z。不要沿用旧PID或Available。
- R1 assemble+unitTest+lint一次成功7m15s，日志/workspaces/TV-ui-results/visual-rework/r1-build.log。不得重建R1。
- 12:14 SSH exit1之后没有可靠安装成功证据；新版after尚未本地落盘。
- 本地baseline目录/home/ubuntu/TV-ui-work/visual-rework/baseline/及baseline.tar.gz保留。
- 恢复前检查/workspaces/TV-ui-results/visual-rework/artifacts与APK SHA；/tmp可能丢失。
- visual-r1-install.sh首行会把/tmp/tv-ui-new-ui.apk当baseline，结尾才归档R1。不要盲重跑：先识别与保存R1包，再单次--no-streaming安装。
- visual-restore-avd.sh会--force重建/tmp AVD与下载模拟器，先检查真实进程与存量目录，避免重复启动和无必要清数据。

## 候选身份
R1不可变包visual-r1-payload.json，manifest VISUAL-R1-SOURCE.json。首轮只取6个用户问题对应after，与同内容同焦点baseline比较，普通/焦点图分别记录。先回传首页/搜索1–2组，不要等全套才反馈。

下一轮有效候选R2C：VISUAL-R2C-SOURCE.json + visual-r2c-payload.json，payload SHAafa85cfb5fbd993414c9dc21484e1d21b72bd457c90f14da286c7563526085b7。15文件差量old_sha以R1为基准，新文件为null。旧R2/R2B包已被替代但保留。R2C独立STATIC PASS见VISUAL-R2-STATIC-REVIEW.md与VISUAL-DRAWER-LABEL-REVIEW.md，未同步/构建/安装/运行；另含抽屉功能名/当前值3语言及40dp最小行高。

具体回归VISUAL-R2-ACCEPTANCE.md；全页面矩阵VISUAL-REWORK-ISSUES.md。新增重点：无Hero padding/keyline同80dp、有Hero后续行16dp；首页/完整历史112dp一致；选集同labels换selected的真实焦点/OK一致；滚动后不抢离开View的焦点；超长集名max280dp；主题固定22dp槽；空态与直播边缘。

## 边界
仅/workspaces/TV-ui-redesign对应UI树；不要动/workspaces/TV。源码本地/home/ubuntu/TV-ui-redesign分支ui/apple-tv-redesign。真机、ARM真实播放、Python/MPV/硬解/真实投屏未测如实保留；UI包装/软件模拟器不代表生产性能。不得用测试数、录像解码替代看图。

每个实际步骤立即小结：时间、真实命令退出/运行状态、进程与包身份、证据路径。不要无反馈长时间推理，不读大历史或整批图。

最终覆盖：有效候选现R2E，VISUAL-R2E-SOURCE.json+visual-r2e-payload.json（d1c3a4c936a7195640c2fddae5a88bc332e5b4b2d5b2f7e3bb8b321eb2bd1dc3，17文件），用visual-r2e-sync-build.py，之前C/D不得再构建。owner磁盘恢复后补R1设置/详情即可预检构建E，不等待root例行确认。

## Oct3 最新接续与helper版本

root只读API确认Codespace Shutdown（last_used_at 00:03:50Z）；原visual_cloud_qa继续唯一owner。最新本地visual-resume-capture.log已boot completed/R1安装Success，随后/tmp/tv-ui-fixture/state.db目标缺失。先核实恢复后实际进程/已装身份，恢复持久fixture兼容路径，仅续数据库/取图阶段，不整段重装。

R2E源码46项/相对R1差量17项仍完全冻结；使用最新visual-r2e-sync-build.py，SHA f5fac25864d65630d34194d2cedfbb63d1da489df15b4a4274f7ebd6f247fa34。它在flock内校验真实R1与17项old_sha再同步；read-only预检不写源码。若已有started或部分同步失败，先核对进程/日志/17项old/new，再明确恢复路径，禁止只删marker重跑。任何Gradle daemon都会保守拒绝，先查实际忙闲，勿盲杀。

已授权恢复后补R1设置/详情，随后一次R2E增量构建/安装/全页回归，无需重复确认。root不操作模拟器。现有*-long截图片名只有5字，后续另用实际超宽内容验长标题，不能凭命名判覆盖。
