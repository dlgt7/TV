# TV视觉返工：进行中，未达到交付条件

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

更新时间：2026-10-03T00:59:04.935283+00:00。历史推进日志已归档至 VISUAL-REWORK-STATUS-ARCHIVE-20261002.md；本文件只保留当前有效状态。

## 源码与审查

- 仅 /home/ubuntu/TV-ui-redesign，分支 ui/apple-tv-redesign；HEAD仍325a4523fb817c3a35b13f1f21c1609d728ccf36。本轮未提交/推送，PR62仍草稿；不合并/强推、不改其他树。
- R1：35文件冻结包已由云端构建一次，assemble+unitTest+lint成功7m15s、exit0；不是视觉通过。
- 最新后续候选 **R2E**：当前46项源/资源改动，相对R1差量17文件。VISUAL-R2E-SOURCE.json + visual-r2e-payload.json，payload SHA d1c3a4c936a7195640c2fddae5a88bc332e5b4b2d5b2f7e3bb8b321eb2bd1dc3。新增实图驱动修复：建议viewport裁剪、真实输入框焦点、首页末行上下边界约束。首页边界独立复核已PASS，来源按钮12dp内边距小diff独立复核已PASS。旧R2/R2B不可变包保留但已被替代。
- R2C包含：静态中性空态、直播边缘焦点、主题固定指示槽、首页/完整历史112dp一致、选集真实焦点同步/滚动后归属/长名280dp上限、无Hero80dp keyline保留、抽屉本地化名称+当前值/最小40dp行高。
- 独立静态审查：VISUAL-R2-STATIC-REVIEW.md（R2B）与VISUAL-DRAWER-LABEL-REVIEW.md（最终抽屉4文件）均STATIC PASS，最终R2C15文件SHA已核对。**R2E未同步、未构建、未安装、未视觉/遥控验证。**

## 云端真实状态

- 唯一owner：/root/visual_cloud_qa，沿用原代理；不更换、不等待更换授权。
- **R1安装已成功**：owner回传visual-r1-install-safe.sh session61084 exit0、Push Install Success、HomeActivity启动；AVD/fixture/assets恢复均exit0。日志本地visual-r1-install.log。
- ARM SHA：856ac1cff3f8491903ae780b296cd7b3dec2f6934495e36ae7389c82ed12eb4c；UI包装SHA：145cf24a6440399416d90ed313cb65665ee41b3ddcc0fe12aade3e13e3c6bba0。两包已在安装前持久归档。
- root16:03只读SSH宿主机独立核验：qemu4559运行，归档两APK SHA与owner完全一致，r1-build.exit=0；没有调用adb或操作模拟器/构建安装。
- 首页/搜索10张R1实图已本地落盘并开始直接对照：海报裁切改善，但搜索建议越界覆盖结果仍BLOCKER；首页末行空白仍待新修。设置/详情因再次/tmp丢失尚未采到。owner持久恢复脚本session50545已启动，模拟器/fixture/素材落/workspaces/TV-ui-runtime，正在wait-for-device，随后同脚本安装归档R1并采设置/详情。root暂停额外SSH连接避免恢复并发。
- Codespace可用；owner尝试idle240后API仍30，不反复修改，维持当前执行会话，最终清理由监控协调。

## 证据及待验

- 本地36张baseline及其SHA已登记VISUAL-EVIDENCE-INDEX.json，均未自动判视觉通过。
- root已直接看详情/抽屉基线；独立VISUAL-PLAYER-VISUAL-CHECKLIST.md记录圆角白线、按钮/选集对齐、底部设置撑宽、抽屉缺名称。当前源码未见旧撑宽绘制，但必须after验证。
- 下一步：owner回传首批R1同内容/同焦点图→root逐页直接对照→确认缺陷后唯一owner同步有效冻结包增量构建→全页矩阵/真实D-pad/独立视觉评审。
- 全页与边界范围见VISUAL-REWORK-ISSUES.md、VISUAL-R2-ACCEPTANCE.md；短云端接续见VISUAL-CLOUD-HANDOFF.md。普通/选中/长文/空/缺图/边缘/弹窗及完整Back路径不可漏。

## 交付边界

旧qa-recovery.complete.json、DELIVERY-RESULT.md只代表旧功能证据。当前视觉不合格问题未完成验收；不创建VISUAL-REWORK-DELIVERY.md完成标志。成熟TV Material/Leanback已采用，保留API24、PlayerView/服务/播放进度与原业务。

真机、生产ARM实际播放、Python/MPV/硬解/真实投屏未测限制保留；软件模拟器和旧99.2% jank不是性能通过。功能测试/视频解码不能替代画面质量。

当前实际更新：2026-10-02T18:57:50.754994+00:00。新裁剪/真实焦点静态PASS见VISUAL-SCROLL-CLIP-REVIEW.md；首页三组实图独立review进行中。

## 2026-10-02T20:02:22.924407+00:00 当前云端真实阻断：AVD磁盘空间不足

owner19:00核验qemu不存在，emulator-stable.log明确Not enough disk space to run AVD；adb wait PID3025是无效等待，不是构建或安装。owner正在停止该等待并查磁盘，只清本任务可重生缓存/调整AVD空间，保留R1包与全部证据，不重构建。root不操作模拟器。另已指出persistent脚本恢复证明误写baseline/restored-home.png的风险，要求改r1路径，原本地36图和baseline.tar.gz安全。R1home/search实际证据已保存，设置/详情仍未成功；R2D未运行。

## 2026-10-02T22:05:15.364345+00:00 最新冻结候选R2E

独立首页三组图评审已完成，VISUAL-R1-HOME-REVIEW.md记录六图SHA：首页仍不通过，顶部/导航改善但末行空白、来源文字贴边未解决。Home BOTH_EDGE+preferHigh=false独立静态PASS；root再给HomeTitle左右12dp内边距（保持40dp高/maxWidth120dp）并请求小diff复核。最终新冻结VISUAL-R2E-SOURCE.json+visual-r2e-payload.json，SHA d1c3a4c936a7195640c2fddae5a88bc332e5b4b2d5b2f7e3bb8b321eb2bd1dc3，46当前源/17相对R1差量。旧D被替代，不再构建。owner获授权磁盘恢复后补R1设置/详情，随即预检并一次增量构建E，不必再等root例行确认；无重复构建/安装。所有新修复运行待验。

2026-10-03T00:47:06.139266+00:00：R2E全部新修改独立静态复核已齐，最终17 SHA零不匹配。云端恢复日志退出143对应owner终止无效adb wait，磁盘恢复/后续运行尚待新证据。

## 2026-10-03T00:59:04.935283+00:00 主执行恢复核验

- 本地46项源码与实际Git变更集合完全一致，SHA零不匹配；R2E17项差量全部匹配，payload SHA仍d1c3a4c936a7195640c2fddae5a88bc332e5b4b2d5b2f7e3bb8b321eb2bd1dc3；git diff --check通过。无新增源码变动、无提交/推送。
- 首页来源12dp内边距最终独立STATIC PASS已确认；已纠正PENDING文件旧review pending文字。R2E运行仍未验证。
- 原visual_cloud_qa确认为running，已要求其回传磁盘恢复、真实进程、安装SHA及取图证据；最后可证实状态仍是AVD空间不足，不能引用历史qemu PID作为当前状态。root没有连接云端或操作模拟器。
- 本地补齐R2E搜索viewport/单焦点、共享ScrollView三页、首页末行/少数据/来源、抽屉专项清单；复用foundation独立复核构建helper，避免环境恢复后误启动。下一步原owner恢复后补R1设置/详情并执行R2E唯一增量构建，再分页面取图独立审查。

## 2026-10-03T00:59:43.665940+00:00 本地较新恢复日志纠正

root读取visual-resume-capture.log（mtime Oct3 00:06）：boot completed、R1 Push Install Success，然后复制fixture数据库至/tmp/tv-ui-fixture/state.db发生FileNotFoundError，shell exit1。此前“磁盘不足”是旧阶段原因，不能继续作为最新确定阻塞。当前qemu和已装SHA仍须owner实时确认，root未操作云端。已通知原owner检查持久fixture及兼容symlink并仅续跑缺失步骤，不重复安装/构建。
