# R2E 构建预检独立复核

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

结论：STATIC PASS（最终helper，限定既有单owner/同锁执行约定）。未执行云端命令，未运行同步/构建，不代表远端已预检通过。

- helper：`visual-r2e-sync-build.py` SHA256 `f5fac25864d65630d34194d2cedfbb63d1da489df15b4a4274f7ebd6f247fa34`。
- payload：`visual-r2e-payload.json` SHA256 `d1c3a4c936a7195640c2fddae5a88bc332e5b4b2d5b2f7e3bb8b321eb2bd1dc3`，匹配脚本EXPECTED及VISUAL-R2E-SOURCE.json。
- 独立本地解析：17项键集合/目标SHA与manifest完全匹配，17项base64解码字节SHA全部正确。未读取或假定远端当前源码。

## 锁与身份

初版源码检查在flock之前存在具体检查/写入间隙；root已修复。最终run-build先取非阻塞排他flock，再verify_source核对payload身份、R1清单全部条目、17项old_sha及写入数据SHA，才检测Gradle、started并写源。只读预检仍可单独调用，但其成功不能替代run-build锁内重验。

锁覆盖同步、Gradle进程等待与exit文件写入；第二个同helper并发调用无法获锁。持久started在首次文件同步前写入，可阻止锁释放后重复启动。同步各文件后立即核验新SHA，再产出合成source清单。构建仅指定UI树cwd，MEDIA3_SOURCE_DIR为既有依赖路径，不写受保护原树。

## daemon与失败恢复

进程检查对GradleWrapperMain和任何org.gradle.launcher.daemon一律拒绝。空闲daemon也会被判为已有Gradle：这是保守的可用性误拒绝，不会造成重复构建。遇到拒绝应由owner检查该PID当前任务/日志/状态，确认空闲后按环境协调处置；不得因为看到daemon就自动杀进程或绕过检查。

部分同步失败/进程中断后started仍保留；文件可能是old/new混合，甚至写入被中断的其他SHA。重新运行会因R1/old_sha或started失败而停止，这是fail-closed，不支持自动恢复，也不会假装构建成功。非零Gradle退出会记录exit并保留started；同步异常可能没有build.log/exit。

恢复步骤必须基于实际证据：owner先确认无仍运行的构建/锁持有者；保存started、log、exit及source清单；逐项比对全部基线和17项old/new目标，识别任何第三种SHA并停止覆盖未知修改；选择经核验的完整基线恢复或明确审查的完成同步恢复方案，再决定下一次构建。只删started不够：旧脚本仍期待R1，且删除标志会丢失重复启动防护。已成功构建则复用对应产物，不能为“清状态”重跑。

最终未发现既有单owner同锁流程下会导致重复构建或使用锁前旧检查覆盖源码的残留阻断。远端预检/构建及产物SHA仍由云端owner实证记录。
