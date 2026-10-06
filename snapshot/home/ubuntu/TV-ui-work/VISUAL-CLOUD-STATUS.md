# 视觉重做云端执行状态

2026-10-02 UTC：唯一执行者 /root/visual_cloud_qa。已直接逐张查看 IMG_1660–1665，确认旧交付视觉不合格；旧 QA/测试数不作为本轮视觉通过证据。

- Codespace `tv-build-5g6j76qj97vg37wpr` 启动前 API 状态 Shutdown；现通过 gh codespace ssh 启动，连接进行中。
- 尚未同步本轮新源码、构建或安装。等待根代理冻结源码清单；不会操作 `/workspaces/TV`。
- 先核验云端进程、存量 API24 模拟器/fixture/旧 APK，恢复 325a4523f 对应生产源码运行基线；保存包和源码 SHA，旧基线须与旧最终完成清单对应。

## 基线与专项采集方案

所有场景使用同一 fixture、主题、1920×1080/320dpi、同焦点目标，记录按键和截图/XML；动态播放器暂停到相同时点后比较。画面截图实际查看；XML仅辅助确认焦点，录像解码只用于素材完整性。

1. 首页 logo/nav 普通与聚焦、hero/继续观看/海报首尾；片库/发现分类和边缘海报。
2. 搜索 fast 同内容：输入、建议、来源过滤、首张/末张海报；长文、缺图、空结果。
3. 详情深空回声：小窗四角与描边、主按钮、选集keyline；全屏播放/抽屉/Back恢复/续播。
4. 设置：来源左栏、点播/直播/壁纸主操作与全部子按钮、主题五色与边界；对应弹窗普通/聚焦/退出。
5. 收藏历史、我的、推送、投屏入口、文件、直播频道/节目/播放菜单；无配置、空历史、错误与重试、加载。
6. 完整真实 D-pad 录像路径：找片→详情→选集→全屏→换源→返回→续播；设置五色→返回活动；我的各入口及 Back。

原始截图与录像分别归档 baseline / candidate，产生可直接并排复核索引。根代理及独立评审判定视觉，功能运行另列。API24软件模拟器不能代表真机/ARM/Python/MPV/硬解/真实投屏性能。

## 09:12 UTC 恢复进展

Codespace 已运行。云端 105 个源码与旧最终清单零不匹配（云端 Git HEAD 仍 b7c0ae，内容SHA对应旧交付，不能用云端HEAD认包）。Shutdown 已清空 /tmp，故重新恢复 API24 AVD/fixture，未重编译生产代码。

- qemu PID 2023，API24 x86_64 1920×1080/320dpi，软件 SwiftShader。
- 旧 ARM APK 派生 x86 UI测试包装成功，Python/ARM路径不覆盖。adb install PID 3584 首次安装正在执行，禁止重复安装。
- 原始照片8张、120s视频恢复；基线证据将放 `/workspaces/TV-ui-results/visual-rework/baseline`，本地随后抓取 `/home/ubuntu/TV-ui-work/visual-rework/baseline`。
- 无本轮新源码同步或构建进程。

## 09:44 UTC 最新状态（覆盖09:12条目）

旧APK默认streaming安装挂起已定位：无设备安装/dex活动、host CPU 0；终止唯一 PID 3584 后顺序 `--no-streaming` 安装立即成功。旧基线已运行，不存在安装中状态。生产代码未同步、未构建。

- qemu PID 2023 持续运行；fixture server已恢复。此次基线pages采集脚本刚完成，暂无录屏/构建/安装任务。
- 已采集并本地直接查看：首页nav、首屏、hero、继续观看、首尾/缺图海报；搜索输入/来源/首尾海报；设置来源/直播主操作/子按钮/弹窗/退出；详情小窗。
- 本地材料 `/home/ubuntu/TV-ui-work/visual-rework/baseline/`。新页采集（发现、收藏、我的、推送、权限弹窗、直播菜单）已在云端同路径生成，准备抓取直接查看。
- 发现额外旧缺陷：首页连续Down海报顶端滚到y=0，上边聚焦描边裁掉且页面下方大空白，已通知根代理。搜索建议chip底部横切、首卡放大左边和标题首字裁切已直接复现。
- `home-reopen/library/library-first`本次路径因活动栈保留播放页未到目标，不算该页证据，将明确重采；其余页面仍待截图视觉查看，不提前判通过。
- 等待根代理冻结后才能同步新源码与构建。下一步补足片库、文件权限后页面、历史/投屏入口、空数据/长文/详情播放器固定状态基线。

## R1 首轮构建完成

35文件冻结清单已经捕获独立payload后同步；云端同步前105旧源SHA与35目标旧SHA全匹配。完整113变更源码清单 `/workspaces/TV-ui-results/visual-rework/r1-source-sha256.json`。

`assembleLeanbackArm64_v8aDebug + testLeanbackArm64_v8aDebugUnitTest + lintLeanbackArm64_v8aDebug` 一次联网构建 BUILD SUCCESSFUL，7m15s，exit0。日志 `visual-rework/r1-build.log`，未以此声称视觉通过。

当前顺序生成并安装R1 x86 UI包装（使用已证有效的 --no-streaming，非重复构建）；生产ARM及测试APK存 `visual-rework/artifacts/`。之后立即按用户6图相同内容/焦点采集R1 after，视觉继续独立审查。

## 2026-10-02 14:34 UTC 实际恢复核查

SSH启动既有Codespace成功。ps无qemu、Gradle、安装、fixture或QA；adb devices为空。R1构建exit=0，现有ARM APK 98075446 bytes（11:43）仍在；artifacts目录为空。R1尚未包装成功或安装，无candidate图。此前模型回合被中断，未执行恢复动作；此记录覆盖旧安装中状态。

现在顺序恢复已丢失的/tmp API24 AVD/fixture，再从现有R1生产包派生测试包装、先持久归档SHA再安装。不重构建，不同步R2/R2B或实时工作树。

## R1 已真实安装（本次session61084 exit0）

现有生产包派生x86包装已在安装前持久归档，`--no-streaming`日志明确 `Success` 并启动HomeActivity。完整日志本地 `visual-r1-install.log`。

- ARM SHA256 `856ac1cff3f8491903ae780b296cd7b3dec2f6934495e36ae7389c82ed12eb4c`
- UI SHA256 `145cf24a6440399416d90ed313cb65665ee41b3ddcc0fe12aade3e13e3c6bba0`
- 云端包 `/workspaces/TV-ui-results/visual-rework/artifacts/r1-arm64.apk` / `r1-ui.apk`。
- AVD/fixture/assets恢复均exit0。当前正在恢复旧fixture数据库并取首页/搜索R1截图；尚无视觉通过。
- PATCH idle_timeout_minutes=240 返回仍30，未成功调长。原值30保留，不能声称已消除闲置停止风险。

## 19:00 UTC 持久环境恢复失败的真实原因

emulator-stable.log 明确 `Not enough disk space to run AVD`；qemu没有运行，唯一adb wait PID3025等待设备。已发出终止该等待并核查df/本任务目录容量。不得把脚本打印started当启动成功。R1归档APK未受损，无R2构建。后续只续恢复步骤，不再次无条件重建AVD；R1恢复图使用r1目录，避免旧恢复脚本覆盖baseline身份。

## 2026-10-03T02:30:27.415471+00:00 新唯一owner已实际恢复并完成R1补图

按VISUAL-CLOUD-OWNER.json，现有主执行线程为唯一云端owner，旧代理已退役，不再followup。02:20探测exit0：Codespace连接恢复、无qemu/构建/安装进程；工作盘1.2GB可用，/tmp111GB；持久fixture和R1两包SHA正确，/tmp兼容fixture目录缺失。

已重建兼容symlink并启动fixture PID1448、唯一AVD launcher1453（这些为本次启动观测，后续须核实），在/tmp较大磁盘运行。boot成功后adbd短暂重连已恢复。pm list确认包缺失（旧/tmp AVD停机丢失），才安装归档R1一次，Success；设备内base.apk SHA145cf24a6440399416d90ed313cb65665ee41b3ddcc0fe12aade3e13e3c6bba0已核对。02:28数据库从持久fixture恢复成功，随后设置主/子/弹窗/返回五状态及详情截图全部采集，session24470最终exit0。原始日志visual-root-state-capture.log。

当前下载r1-recovered.tar（session5084），准备实际看图；接着按冻结R2E预检并一次增量构建。无新源码修改、未把R1画面算R2E验收、无R2E构建通过证据。

2026-10-03T02:51:23.050119+00:00：R2E唯一增量构建session49936已exit0（assemble/unitTest/lint）；当前归档生产APK并生成/安装UI包装session77702。设备身份验证结果待该脚本返回。R1新增补图已本地直接比较详情/设置两类，改善记录到证据索引，未计R2E通过。
