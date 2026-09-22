# Release notes — v5.5.6-source.6

日期：2026-09-22  
分支：`release`  
详细文档：[docs/CAST-SETTINGS-BUILD-2026-09-22.md](docs/CAST-SETTINGS-BUILD-2026-09-22.md)

## Highlights

- **DLNA MediaRenderer 可被安卓/系统投屏稳定发现**（MulticastLock、R8 注解、HostHeader、正确描述路径）
- **连一次后二次仍可发现**（服务常驻 + `ssdp:alive` 续播）
- **AirPlay 与 DLNA 并存**：互斥只停会话，不再拆掉 AirPlay/NSD
- **SMB 共享列表去重**（不再出现 `share` + `Share`）
- EXO 解码文案改为「软解 / 硬解」
- 首页 D-pad 滑动更跟手（HomeGridView 对齐/回顶）

## Fixes

### Cast / DLNA
- `DlnaMulticastLock`：jUPnP SSDP 组播锁（原先只有 AirPlay 有）
- `DLNARendererService`：注册失败打日志；`republish()` + 60s alive 续播；服务在 DLNA 开启时常驻
- `@UpnpService` 补全 `stringConvertibleTypes = LastChange`（否则 LocalDevice 注册失败）
- proguard：`-keepattributes *Annotation*`（R8 不再剥注解）
- `SocketHttpStreamServer`：typed `HostHeader`（修 412）；相对 URI（修绝对 URI 导致的 404/异常）；每连接独立线程 + 8s 超时
- `DLNAServiceConfiguration`：默认监听端口 49152
- `DLNACastManager`：定向搜索 MediaRenderer；关面板不拆 jUPnP

### AirPlay
- `CastConflict.yieldToDlna` → `AirPlayServer.stopLocalSession`（不再 `ACTION_STOP_SERVER`）
- `AirPlayService.stopServer`：native 拆解异步化，断开更快

### SMB
- `SmbClientHelper.listShareNames`：大小写不敏感去重

### UI / Player
- EXO `getDecodeText`：硬解文案
- `HomeGridView` / `HomeActivity`：滑动对齐与 pin 策略

## Artifacts

| 文件 | 用途 |
|------|------|
| `leanback-arm64_v8a.apk` | 电视 |
| `mobile-arm64_v8a.apk` | 手机 |
| `leanback-armeabi-v7a.apk` / `mobile-armeabi-v7a.apk` | 32 位 |
| `SHA256SUMS` | 校验 |

## Verify

1. iPhone AirPlay 镜像  
2. 安卓 DLNA 发现「设备名」+「Android AirPlay」  
3. DLNA 断开后再扫仍可见  
4. DLNA 用过之后 AirPlay 仍可见  
5. SMB 只有一个 share  
