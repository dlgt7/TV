# TV 集成验证（2026-10-06）

应用代码：`1249ec448a0dd29c4038e6ef3b413f490545dba7`。默认分支为 `ui/apple-tv-redesign`。
JAR 发布：`74a91c878b9c9e902ef975cb546072b9dd2bff9e`；JAR 对应源码 `75ce558ad38e0b1e2c1038dc7bcb374c35289399`。

## 已完成的功能和验证

| 项目 | 已完成的范围 |
| --- | --- |
| TV 微动效 | 实际 View 海报、Compose Material 按钮和设置行、胶囊 Tab、内容淡入淡出与面板开合；保留遥控焦点响应和原布局。 |
| Syncplay | 官方 1.7.7 协议兼容；三星实际 Exo 完成加入、播放、暂停、远程跳转、本机紧接拖动与退出恢复等 9 个检查点；主线程网络违规 0。 |
| WebDAV | 收藏/进度合并、删除墓碑及并发保护；39 项 JVM 测试；两个生产 Java 客户端在坚果云验证首次创建竞争、ETag 合并和旧版本拒绝。 |
| 坚果云适配 | 支持裸 ETag；首次创建使用临时上传和 `MOVE / Overwrite:F`，避开服务端忽略 `If-None-Match:*` 的行为。 |
| Cloudflare 优选 | 保持最多两个并行 DNS 查询及 1500ms 等待上限；修复完成任务与线程接收下个任务之间的误拒绝，28 项相关回归通过。 |
| 希番 Next | 通过站点播放 API 获取新票据；三星 Exo、MPV 各两集真实解码、前后 seek、切集及播放控制通过。 |
| 其他实播 | 此前已验证 MXdm 的 Exo/MPV 双集，以及 DM84 的原生 WebView 嗅探至 Exo 双集播放。 |
| WEX | 仅替换既有 12 个 Kazumi 与 2 个 Ciallo 的 JAR，90 条配置及其他内容保留；运行时按原有去重规则显示 86 个源，14 个新 JAR 的 MD5 全部匹配。 |
| CDN | 新 JAR 的主机和三星下载均成功，SHA-256 与 CI 成品一致；使用实际可用的 gh-proxy 地址。 |

## 证据

- [Syncplay 三星结果](syncplay-samsung.json)
- [希番 Exo 双集](xfdmnext-exo-samsung.json)
- [希番 MPV 双集](xfdmnext-mpv-samsung.json)
- [生产 WebDAV 双客户端实测](webdav-production-live.json)
- [坚果云禁止覆盖 MOVE 实测](webdav-move-live.json)

## 实测边界

Syncplay 的设备测试是一台三星上的真实 Exo 与协议对端，尚未完成两台真实设备的同步精度验收；TLS 另有官方服务端协议测试。WebDAV 的数据库集成使用同一台三星上的两个 Room 实例，真实坚果云测试使用固定测试记录，没有上传用户收藏或历史。

Kazumi 已对照主程序的规则缓存、动态页面和播放器实现；没有找到可直接解决以下限制的可靠追加改动：部分源要求验证码，AGE 依赖的外部脚本不可达，白猫播放器混淆，部分 ezdmw 线路失效。Sorani 最近一次旧接口探测连接超时。不会把解析成功或 HTTP 成功记作全站实播通过。

## 最终交付核对

[完整 CI 37541890756](https://github.com/wobuhui666/TV/actions/runs/37541890756) 通过，包含共享网络、Python/JavaScript 兼容、同步与播放器策略测试，以及 TV/手机相关构建。

[正式 Release build-37541816195](https://github.com/wobuhui666/TV/releases/tag/build-37541816195) 的 ARM64、ARMv7 两包已核对源码归属、生产签名、包名、ABI、GitHub SHA-256 及 16KB zipalign。

三星现有 preview 测试版已更新至相同源码，安装前后 11 个偏好/数据库文件逐一校验完全一致。更新后的 [8 项真实 Room 测试](webdav-room-device.log) 通过；[应用内存中的 WEX 检查](wex-memory-final.json) 确认 86 个去重来源、14 个新 JAR 全匹配，检查的偏好和当前选择未改变。[交付回执](delivery.json) 记录安装 SHA 与验证范围。
