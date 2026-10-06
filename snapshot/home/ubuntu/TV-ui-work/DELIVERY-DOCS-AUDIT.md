# 交付文档与精选证据只读审计

时间：2026-10-02 UTC。范围：仓库 `docs/ui-redesign/README.md`、`verification.json`、`api24-art.txt`、`evidence/` 清单与 JSON，以及工作区 `QA-RECOVERY-RESULTS.md`、`FINAL-BLOCKERS-REVIEW.md`。未改源码、构建、操作云端/模拟器、提交或推送。仅新增本审计记录。

结论：**需在最终 QA 后刷新交付材料；当前材料不能作为最终源码已验收证明。** 静态复核 PASS 与运行验收应保持分开。

## 最终提交前必须处理

1. **verification.json 仍是旧构建。** 105 个源码 SHA 中恰好 3 个不匹配当前文件；均为最终修复：
   - HomeActivity.java：`1b231a4f7fe422e75fdbb20e584cff16ece7ccdbe94b85eca8878e5cd5d7d8cf`
   - LiveActivity.java：`da2d805521fb38f5894d607bdbfa8f1a76138522f422bcd7b086caceb3100acf`
   - VideoActivity.java：`c047471be61bae3a62322e48d05878b961e39b60e3874da0169b536e9ae30f0c`
   `delivery_validation_status` 正确写了 pending，但 `source_state` 同时写 “Final source identified ...”，互相矛盾。应由新 QA 的实际构建报告更新源码 SHA、两个 APK SHA、日期/结果及专项通过记录，而非手工替换 SHA 沿用旧测试结论；最终完成标志须明确匹配上述 3 SHA。216 tests / lint 269 warning、6 hint 当前只能归属旧构建，等待新报告确定是否相同。
2. **README 云端验证缺少版本归属说明。** 当前直接列构建与测试成功，普通读者易理解为所有最终改动已验证。最终 QA 前加“这是主题焦点修复后的旧构建，不含随后首页/播放页修复”；最终 QA 后改为精确的新报告归属。可单独记录既有 mobile 编译未重跑（目前已准确说明）。
3. **精选运行证据缺少可发现的索引和限制。** README 只链接 5 张首页/详情/搜索图，没有链接已纳入仓库的 5 个视频、theme-dpad.png、resume-evidence.json、performance-summary.json。应补小型索引，明确旧录像覆盖设置 D-pad/主子操作、搜索替换、继续观看、直播导航；它们不覆盖后来 3 文件修复。新首页无配置与组合主题回归应使用新 QA 的独立证据，不能由旧 settings-themes 视频推导“组合主题路径通过”。
4. **性能限制应在 README/PR 直接可见。** 当前虽说真机待验收，但未披露已采样 500 帧中 496 帧 janky（99.2%）、p50 53ms / p95 85ms，以及短测 PSS 增长 602 KiB。该数据来自 API24 x86 软件模拟器，不能等同真机表现，也不能据此宣称流畅/60fps/无泄漏。建议用一两句摘要并链接 JSON；保留真实硬件未验收的说明。

## 已确认准确及链接情况

- README 当前所有 7 个 Markdown 相对链接均有本地目标，无失效链接。
- video-validation.json 中 5 个文件名均存在；5 个 SHA-256 均与实际仓库视频完全一致。probe/decode exit=0 是 QA 记录；本次仅验文件和 hash，没有重新解码或把解码成功当功能通过。
- home-after.png 与归档 final-qa/home-initial.png 相同；search-after.png 对应 search-results.png；detail-after.png 对应 detail-final.png。README 已准确限制为非逐像素/性能对照；搜索静态图与延迟录像条目数差异见 QA 报告，应在视频索引注明“静态重拍有第二 fixture 源，原延迟录像为单源”。
- api24-art.txt 实际有设备检查与 production 类检查；README 的“31 项，其中 30 项 production / 1 项设备”及 Trans/Vod 重载限制比日志末行概括更准确，无需扩大结论。
- README 已准确区分生产 ARM APK 和替换 x86 原生库、跳过测试 DEX PyLoader 初始化的测试 APK，并声明 Python/MPV/硬解/真实投屏及两台目标硬件未验收。
- resume-evidence.json 保留“截图早于 XML、控制层隐藏”的限制，进度依据为数据库 51241ms/120095ms + 控制栏 57 秒 + 视频；不可改成凭截图证明精确恢复到同一毫秒。

## 可在 PR 中合并列出的范围限制

既有设置焦点验收实际覆盖七色（含横向隐藏两色），不应缩写成仅五色；独立冷启主题通过不等于组合路径通过。配置历史无其他条目、仅空日志导出、Cast 空闲页，不应写成配置历史内容回归/实际文件导出/真实投屏通过。无配置捕获 NPE 与系统 UIAutomator 自身 FATAL 均有记录，不应写“全程日志无异常”。本轮最终源码仅消除主题触发重建的已复现链路，静态审查未声称修复所有系统重建、进程死亡或独立服务断连竞态。
