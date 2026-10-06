# 最新补丁 R2K：三星真实源崩溃修复

- 已覆盖安装三星 SM-F900F（API33），配置保留。[最新 ARM64 APK](r2k-evidence/artifacts/r2k-arm64.apk)。
- 同一 GoProxy 坏库错误在原源冷启动时再次触发，已隔离；PID 371674 与首页保持不变，另观察到真实搜索/播放页面。源下载返回 XML NoSuchKey 的服务端问题未修复，相关组件功能仍可能不可用。
- 223 单测（7 专项）通过，lint 0 error，mobile Java 编译通过；129 源码 SHA 匹配。SHA-256 `bdda4c638ffb03288f4218ea9b2e6780a48204a48c67a224c9735bcf24295f64`。
- 已推送提交 `a9c65efa9b09db7616d619231783fd4fbcfddd6b`，PR #62 已更新且仍保留草稿。详细证据见仓库 [source-crash/README.md](/home/ubuntu/TV-ui-redesign/docs/ui-redesign/source-crash/README.md)。

以下为之前 R2H 交付历史，最新安装包以上述 R2K 为准。

# R2H 交付记录（2026-10-03）

交付状态：已构建并归档的 UI 修复候选；全页面全状态与真机验收尚未全部关闭，不使用旧完成标记。

- APK：[TV R2H ARM64 debug](r2h-evidence/artifacts/r2h-arm64.apk)
- SHA-256：`ea254083ad14410a2590afa7a0cf65f95213f6c4209470f8d668156ec6f2759c`
- PR：https://github.com/wobuhui666/TV/pull/62（草稿）
- 工作树：`/home/ubuntu/TV-ui-redesign`；分支 `ui/apple-tv-redesign`。未改动原工作树 `/home/ubuntu/TV`，未合并、未强推。
- 源码冻结清单：`VISUAL-CURRENT-SOURCE.json`（R2H），122 个文件与云端实际构建一致；所有已修改/新增应用源码均在清单内。
- 构建日志：`r2h-evidence/r2h-build.log`；安装身份：`r2h-evidence/r2h-install-verified.json`；测试汇总：`r2h-test-summary.json`。
- 本轮运行及截图记录见仓库 [交付说明](/home/ubuntu/TV-ui-redesign/docs/ui-redesign/visual-rework/README.md)。已排除失效的早期片库权限弹窗、旧 XML 及自然换集造成的倒序断言，不冒充通过证据。

216 单测通过；lint 0 错误、311 warning、6 hint。R2H 未配置 TMDB key，已验证发现失败空态、长按确认稳定与 Back，未验证联网成功加载。遥控运行使用 API24 x86 UI 包装 APK，生产 ARM/硬解/Python/MPV/真实投屏和真机性能仍未测。详细覆盖边界以仓库报告为准。


交付更新：2026-10-03T11:38:05.539349+00:00

- 已提交并推送：UI 修复 `298182efc`；独立复核归档 `08690b855`。远端 PR head 已核对为 `08690b85572e85d5695cd261448065f5ea80f7cb`。
- PR #62 标题/说明/截图/限制已更新，仍为草稿；本地工作树干净。
- [最终独立复核](R2H-INDEPENDENT-REVIEW.md)在实际检查范围无明确 blocker；未测项保持开放。
