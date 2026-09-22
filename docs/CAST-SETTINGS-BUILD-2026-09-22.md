# 投屏、设置与构建测试说明（2026-09-22）

> 本文汇总本轮在 **leanback（电视）/ mobile（手机）** 上的投屏链路、设置项验证、构建与发布说明。  
> 设备：`home-rk`（rk3588，Android 12，eth0 `10.0.0.107`）、Redmi Note 8（mobile）。

---

## 目录

1. [架构速览](#1-架构速览)
2. [投屏协议与发现](#2-投屏协议与发现)
3. [本轮问题与修复](#3-本轮问题与修复)
4. [设置项矩阵](#4-设置项矩阵)
5. [构建与安装](#5-构建与安装)
6. [GitHub Actions 发布](#6-github-actions-发布)
7. [验收清单](#7-验收清单)
8. [排查手册](#8-排查手册)

---

## 1. 架构速览

```
┌──────────── 手机 (mobile) ────────────┐
│ CastDialog                            │
│  ├─ ScanTask        HTTP /device:9978 │  FongMi 互联（单播）
│  ├─ DLNACastManager jUPnP SSDP        │  MediaRenderer 发现
│  └─ DLNACast        SetURI→Play→Seek  │
└───────────────────┬───────────────────┘
                    │ UPnP AVTransport
┌───────────────────▼──── 电视 (leanback) ──────────────┐
│ DLNARendererService (jUPnP DMR)                       │
│  ├─ MediaRenderer + AVTransport/RenderingControl/CM   │
│  ├─ SocketHttpStreamServer  :49152  /upnp/dev/<udn>/… │
│  └─ DlnaMulticastLock（SSDP 组播）                    │
│                                                       │
│ AirPlayService (UxPlay + NsdServiceManager)           │
│  ├─ mDNS  _airplay._tcp / _raop._tcp                  │
│  └─ 与 DLNA 经 CastConflict 互斥（只停会话不停服务）  │
│                                                       │
│ NetworkStorage：SMB (smbj) / WebDAV                   │
└───────────────────────────────────────────────────────┘
```

| 模块 | 路径 | 作用 |
|------|------|------|
| DMR 注册 | `service/DLNARendererService.java` | MediaRenderer 注册、SSDP alive |
| AVTransport | `dlna/DLNAAvTransportImpl.java` | SetURI/Play/Stop/Seek |
| 描述 HTTP | `dlna/SocketHttpStreamServer.java` | `/upnp/dev/<udn>/desc` 等 |
| 组播锁 | `dlna/DlnaMulticastLock.java` | Wi-Fi 下收发 SSDP |
| 手机发现 | `mobile/.../DLNACastManager.java` | MediaRenderer 搜索 |
| 互斥 | `service/CastConflict.java` | AirPlay ↔ DLNA |
| AirPlay | `airplay/.../AirPlayService.kt` | 镜像/音频接收 |
| SMB | `storage/SmbClientHelper.java` | 共享枚举/列表 |

---

## 2. 投屏协议与发现

| 协议 | 发现 | 端口/路径 | 备注 |
|------|------|-----------|------|
| **AirPlay** | mDNS/NSD | `_airplay._tcp` / `_raop._tcp`，默认 7000 | 广播名默认 **Android AirPlay** |
| **DLNA DMR** | SSDP | `239.255.255.250:1900` | LOCATION → `http://<ip>:49152/upnp/dev/<udn>/desc` |
| FongMi 互联 | HTTP 单播 | `:9978` `/device` `/action?do=cast` | ScanTask 扫 /24 |

**描述 URL 形态（jUPnP Namespace）：**

```text
/dev/<udn-identifier>/desc          # 不带 base path
/upnp/dev/<udn-identifier>/desc     # 本项目 base path = /upnp
/upnp/dev/<udn>/svc/upnp-org/AVTransport/desc
/upnp/dev/<udn>/svc/upnp-org/AVTransport/action
```

`UDN.getIdentifierString()` 为 UUID 本体（无 `uuid:` 前缀）。

**SSDP 要求：** 必须持有 `WifiManager.MulticastLock`，否则 Android 会丢弃组播，搜不到设备。

---

## 3. 本轮问题与修复

### 3.1 安卓只能看到「Android AirPlay」、搜不到 DLNA

| # | 根因 | 修复 | 文件 |
|---|------|------|------|
| 1 | jUPnP **无 MulticastLock**，SSDP 组播被 Wi-Fi 滤掉 | `DlnaMulticastLock` | `DlnaMulticastLock.java` + 三个 jUPnP Service |
| 2 | `registerLocalDevice` **吞异常**，注册失败无人知 | 打 log / 抛出 | `DLNARendererService` |
| 3 | R8 **剥掉 `@UpnpService` 注解**，`read()` 失败 | `-keepattributes *Annotation*` | `proguard-rules.pro` |
| 4 | 具体类 `@UpnpService` **覆盖父类**，丢掉 `stringConvertibleTypes=LastChange` | 注解补全 | `DLNAAvTransportImpl` / `DLNARenderingControlImpl` |
| 5 | 描述接口 **412**（`hasHostHeader()==false`） | 显式 `HostHeader` | `SocketHttpStreamServer` |
| 6 | 误用绝对 URI → `Registry.getResource` 抛异常 | 保持相对 path | 同上 |
| 7 | 默认监听端口 0（临时端口） | 默认 **49152** | `DLNAServiceConfiguration` |

真机确认：

```text
I DlnaRenderer: MediaRenderer registered udn=uuid:… name=rockchip rk3588_docker iface=eth0
GET /upnp/dev/<udn>/desc → 200 OK + MediaRenderer XML
```

### 3.2 连一次 DLNA 后二次搜不到

| 根因 | 修复 |
|------|------|
| `HomeActivity.onDestroy` → `DLNARendererService.stop()` | DLNA 开启时服务常驻 |
| 会话结束不重发 `ssdp:alive` | `advertiseLocalDevices()` + 60s 续播 |
| 卡住的 HTTP 连接拖死描述口 | 每连接独立线程 + 8s 读超时 |

```text
republish alive isDlnaActive=true
republish alive isDlnaActive=false
GET /upnp/dev/<udn>/desc → 200 OK   ← 会话后仍可发现
```

### 3.3 DLNA 正常后 AirPlay 搜不到（回归）

**根因**：互斥 `yieldToDlna` 误发 `ACTION_STOP_SERVER`，整个 AirPlay + NSD 被拆掉。

**修复**：`AirPlayServer.stopLocalSession()` — 只 `stopLocalSession("dlna")`，保留 mDNS。

```text
AirPlay registered: Android AirPlay
… DLNA session …
（无 Server stopped / unregistered）
```

### 3.4 SMB 出现两个 share（含大写 Share）

`SmbClientHelper.listShareNames()` 试探 `COMMON_SHARES` 里的 `share` 与 `Share`；SMB 名不区分大小写 → 同一共享列两次。

**修复**：按 `toLowerCase` 去重，保留先出现的写法。

### 3.5 EXO 解码文案

EXO 只有 **软解 / 硬解**（`PREFER_SOFTWARE` / `MediaCodec.DEFAULT`）。  
「兼容硬解 / 性能硬解」是 MPV 三档。`getDecodeText()` 已按引擎区分文案。

### 3.6 首页滑动

`HomeGridView` 的 `WINDOW_ALIGN_NO_EDGE` + 强制 `pinTopRow` 导致 D-pad 发粘。已恢复默认对齐，并限制 pin 时机。

---

## 4. 设置项矩阵

### 主设置

| 项 | 存储/API | 响应 | 说明 |
|----|----------|------|------|
| 点播/直播/壁纸配置 | `Vod/Live/WallConfig` | ✅ | ConfigDialog |
| 播放设置 | `SettingPlayer*` | ✅ | 子页 |
| 弹幕设置 | `SettingDanmaku*` | ✅ | 子页 |
| 网络存储 | SMB/WebDAV | ✅ | 仅 leanback |
| 投屏设置 | DLNA / AirPlay | ✅ | 仅 leanback |
| 媒体库 | `Setting.putDlnaLibrary` | ✅ | leanback |
| 无痕模式 | `putIncognito` | ✅ | |
| 图片尺寸 | `PlayerSetting.putSize` | ✅ | |
| 主题色彩 | `putThemeColor` | ✅ | |
| DoH | `putDoh` | ✅ | 腾讯/阿里/360… |
| 缓存 | `FileUtil.clearCache` | ✅ | |
| 备份/恢复 | `BackupManager` | ✅ | 需存储权限 |
| 版本 | `Updater` | ✅ | |

### 播放设置

| 项 | API | 条件 |
|----|-----|------|
| 播放引擎 EXO/MPV | `putEngine` | 联动显示 |
| 解码设置 / 智能去广 / HTTP | EXO 专用 | MPV 隐藏 |
| mpv.conf / gpu-next / Vulkan | MPV 专用 | EXO 隐藏 |
| 缓冲 / 直播延迟 / 渲染 / 缩放 / 字幕 / 倍速 | 通用 | |
| 长按倍速 → 音量增益 | `putVolumeGain` | |
| 音频效果 / 视频效果 | `AudioSetting` / `VideoSetting` | |
| 后台播放 | `putBackground` | |
| 预载 | `PreloadSetting` | |
| UA / Assrt Token / 字幕字体 | `Setting` / `SubtitleSetting` | |

### 解码设置

隧道、音频直通、音频软解、视频软解、AAC 优先、DV7 回退 — 均有 `PlayerSetting.put*`。  
MPV 下「隧道」点击无效（有意：`if (isMpv()) return`）。

### 弹幕 / 预载

弹幕加载 → API → 自动 → 爬虫优先（级联显示）。  
预载开关 → 线程/容量/时间/计量/诊断。

---

## 5. 构建与安装

### 远程 Linux（推荐出包）

```bash
ssh zyq@192.168.42.153
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export ANDROID_HOME=/data/home/zyq/Android/Sdk
export FONGMI_NATIVE_ABIS=arm64-v8a
export PATH=/data/home/zyq/.local/bin:$JAVA_HOME/bin:/usr/local/bin:/usr/bin:/bin
cd /data/home/zyq/IdeaProjects/github/FongMi/TV
./gradlew :app:assembleMobileArm64_v8aRelease :app:assembleLeanbackArm64_v8aRelease
```

APK：

```text
app/build/outputs/apk/mobileArm64_v8a/release/mobile-arm64_v8a.apk
app/build/outputs/apk/leanbackArm64_v8a/release/leanback-arm64_v8a.apk
```

### 注意点

| 项 | 说明 |
|----|------|
| Chaquopy | 需 Python 3.10；pip 用国内镜像（`pypi.tuna.tsinghua.edu.cn`） |
| libmpv | Docker 全量编译很慢；已有 `.so` 时可 skip（见 `other/tools/patch_libmpv_skip.py`） |
| airplay 原生 | openssl 外网不稳时，可用预编译 jniLibs 并 skip cmake |
| R8 | 必须保留注解，否则 DLNA 注册失败 |
| 签名 | `local.properties` 的 `release-local.keystore` |

### 安装

```powershell
adb connect home-rk
adb connect 192.168.1.202:5555
adb -s home-rk:5555 install -r leanback-arm64_v8a.apk
adb -s 192.168.1.202:5555 install -r mobile-arm64_v8a.apk
```

---

## 6. GitHub Actions 发布

工作流：`.github/workflows/source-build.yml`  
仓库：https://github.com/zyqfork/TV

| 触发 | 行为 |
|------|------|
| `push` tags `v*` | 构建 **arm64-v8a + armeabi-v7a** × leanback/mobile，**创建 Release** |
| `workflow_dispatch` | 只构建上传 artifact |
| `pull_request` → `fongmi` | 只构建 |

**发布步骤：**

```bash
git add <源码与文档>
git commit -m "fix(cast,dlna,smb): …"
git push origin release
git tag v5.5.6-source.6
git push origin v5.5.6-source.6
```

产物：4 个 APK + `SHA256SUMS`。  
监控：`gh run watch` / Actions 页。

---

## 7. 验收清单

- [ ] iPhone AirPlay 镜像连接/断开正常  
- [ ] 安卓 DLNA 投屏可发现 **设备名（MediaRenderer）** 与 **Android AirPlay**  
- [ ] DLNA 断开后再扫描仍可见、可二次投屏  
- [ ] 用过 DLNA 后 AirPlay 仍可见（互斥不再拆服务）  
- [ ] SMB 浏览只有一个 `share`（无大小写重复）  
- [ ] EXO 解码文案为「软解 / 硬解」  
- [ ] 首页 D-pad 滑动跟手  
- [ ] 设置各子项可点、值可改  

---

## 8. 排查手册

```bash
# 电视：MediaRenderer / 续播
adb -s home-rk:5555 logcat -d | grep -E 'DlnaRenderer|DlnaHttp|DlnaMulticast'

# AirPlay 是否被误停
adb -s home-rk:5555 logcat -d | grep -E 'AirPlay registered|Server stopped|unregistered'

# 描述是否 200
adb -s home-rk:5555 shell \
  "echo -ne 'GET /upnp/dev/<udn>/desc HTTP/1.0\r\nHost: 10.0.0.107:49152\r\n\r\n' | nc -w 3 10.0.0.107 49152 | head"

# 同网段 SSDP 探测
python other/tools/ssdp_probe.py urn:schemas-upnp-org:device:MediaRenderer:1

# 服务是否仍在
adb -s home-rk:5555 shell dumpsys activity services com.fongmi.android.tv
```

| 症状 | 查 |
|------|----|
| 只见 AirPlay | MulticastLock？MediaRenderer registered？desc 200？ |
| 二次搜不到 | republish 日志？服务是否被 stop？ |
| AirPlay 消失 | 是否出现 `Server stopped` / `unregistered`？ |
| 跨网段搜不到 | 手机与电视是否同网段（SSDP 不跨路由） |
| SMB 双 share | `SMB shares found names=` 日志 |

### 相关测试脚本

| 脚本 | 用途 |
|------|------|
| `other/tools/ssdp_probe.py` | 局域网 SSDP 探测 |
| `other/tools/test_settings_ids.py` | 设置项 resource-id 点击矩阵 |
| `other/tools/test_player_precise.py` | 播放设置逐项 |
| `other/tools/test_settings_tv2.py` | 电视 D-pad 设置遍历 |
| `other/tools/phone_nav.py` / `tv_nav.py` | UI 驱动 |
| `other/tools/remote.py` | 构建机 ssh/scp 封装 |
