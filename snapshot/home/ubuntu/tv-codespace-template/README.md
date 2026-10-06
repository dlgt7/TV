# TV Codespace 环境模板

为 [wobuhui666/TV](https://github.com/wobuhui666/TV) 提供可复用的 Android TV 构建与 UI 联调环境：
JDK 21、Python 3.10、Android SDK、Media3 复合构建源码、headless 模拟器与本地测试影视源。

模板支持两种入口：新建 Codespace 时自动安装，或在已有 Ubuntu 24.04 Codespace 中执行 `bootstrap.sh`。
安装依赖和首次构建需要联网。推荐 **4 核 / 16GB 内存 / 至少 64GB 存储**；同时运行 Gradle 与模拟器需要预留内存。

## 新建 Codespace

在本仓库选择 **Code → Codespaces → Create codespace**，或先通过 **Use this template** 创建自己的仓库。
`.devcontainer` 会在创建时安装工具链、下载 SDK、克隆 TV / Media3 并生成测试素材。
首次安装需要等待 `postCreateCommand` 完成；详细输出在 Codespaces 创建日志中。

打开终端，在模板仓库目录运行：

```bash
source scripts/env.sh
scripts/build-tv.sh
```

默认构建 `/workspaces/TV-ui-redesign` 的 `:app:assembleLeanbackArm64_v8aDebug`，产物为：

```text
/workspaces/TV-ui-redesign/app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
```

该 ARM64 APK 用于 ARM64 电视；安装时指定真实设备的 adb serial：

```bash
scripts/install-apk.sh "$TV_UI_DIR/app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk" <设备IP:端口>
```

## 模拟器 UI 验证

内置模拟器是 **x86_64 / Android API 24**，不能直接安装 ARM64 APK。
下面的辅助流程沿用 TV UI 开发时使用的测试包处理方法：保留原始 APK，制作单独的 x86_64 UI 包，移除 ARM 库、保留缓存中的 x86 UI 库并跳过 Python 初始化。

```bash
source scripts/env.sh
scripts/start-fixture.sh
scripts/start-emulator.sh
scripts/prepare-ui-apk.sh
scripts/install-apk.sh "$TV_RESULTS_DIR/tv-ui-emulator.apk"
tv_adb shell am start -W -n com.fongmi.android.tv/.ui.activity.HomeActivity
```

在 App 中配置测试源 `http://10.0.2.2:8765/config.json`，可浏览演示影片、搜索与测试直播源。

```bash
tv_adb exec-out screencap -p > "$TV_RESULTS_DIR/tv-home.png"
scripts/stop-emulator.sh
```

**UI 测试包不能用于发布，也不能证明 ARM 原生播放器或 Python 源可用。**
这类能力仍需用原始 ARM64 APK 在真机验证。字节码布局不兼容时，处理脚本会失败，不会修改原始 APK。
UI 包用独立测试密钥签名；同一模拟器若已经装有其他签名的同包名 App，请先自行备份并处理签名冲突。

## 已有 Codespace / Ubuntu 24.04 主机

把模板复制到独立目录，然后运行：

```bash
cp .env.example .env            # 按需填写，不需要密钥也能构建公开仓库的 debug 包
bash bootstrap.sh
source scripts/env.sh
```

`.env` 是 shell 配置，脚本会按模板的绝对位置自动加载；其中设置优先于继承的环境变量。
也可以只使用 Codespaces secrets 或终端导出的环境变量。
`bootstrap.sh` 需要 sudo，会安装系统包；已有 TV 工作树及 Media3 自定义设置会保留，不会自动拉取覆盖本地修改。
模板更新后可重跑引导；已存在的仓库不会自动更新到远端最新提交。

## 固定参数

| 组件 | 默认值 |
| --- | --- |
| JDK / Python | 21 / 3.10 |
| Node | 22（devcontainer feature，独立 bootstrap 不安装） |
| cmdline-tools | 13114758 |
| Android platform / build-tools | `platforms;android-37.0` / `37.0.0` |
| 模拟器 | SDK 当前版本，下载失败时回退到 11237101 |
| 系统镜像 | `system-images;android-24;default;x86_64` |
| AVD | `tv_ui_api24`，1920×1080，320dpi，2 核，2GB RAM |
| AVD 数据分区 | 请求 2GB，可用 `TV_AVD_DATA_SIZE` 调整；模拟器可能提高最低容量 |
| 模拟器 / 测试源端口 | 5580 / 8765 |
| 主仓库 / UI 分支 | `sync/fongmi-20260628` / `ui/apple-tv-redesign` |
| Media3 分支 | `release-1.11.0-fongmi` |

```text
/workspaces/
├── tv-codespace-template/     # 本模板（实际目录由 GitHub 仓库名称决定）
├── android-sdk/
├── TV/
│   └── .media3/
├── TV-ui-redesign/           # 独立 Git worktree
├── TV-ui-runtime/avd/        # 持久化 AVD 与测试签名密钥
├── TV-ui-results/            # 日志、UI 测试包与截图
└── .gradle/                  # 持久化构建缓存
```

测试素材默认在 `/tmp/tv-ui-fixture`，重启后会按需重新生成。所有主要路径均可通过 `.env` 覆盖。

## 常用操作

| 命令 | 用途 |
| --- | --- |
| `source scripts/env.sh` | 加载路径、JDK 与 `tv_adb` 便捷函数 |
| `scripts/build-tv.sh [Gradle任务/选项…]` | 默认构建 ARM64 TV debug APK |
| `scripts/prepare-ui-apk.sh [输入APK] [输出APK]` | 制作独立的 x86 UI 测试包 |
| `scripts/install-apk.sh <APK> [serial]` | 默认装入 `emulator-5580` |
| `scripts/start-emulator.sh` / `scripts/stop-emulator.sh` | 启停模拟器 |
| `scripts/start-fixture.sh` | 生成素材并启动测试源 |
| `scripts/connect-device.sh <IP> [port]` | 连接 adb 真机 |
| `scripts/connect-device.sh --up` | 使用 `TS_AUTHKEY` 加入 Tailscale |

额外构建示例：

```bash
scripts/build-tv.sh :app:testLeanbackArm64_v8aDebugUnitTest :app:lintLeanbackArm64_v8aDebug --max-workers=2
```

Release 构建需要 TV 仓库自身的签名配置，参见其 `LOCAL_BUILD_ENV.md`。不要提交 `.env`、签名密钥、`local.properties` 或 APK。

## 测试源

提供 `/config.json`、`/api`、`/live.m3u`、图片与支持单段 Range 的 `/sample.mp4`。
数据均为虚构演示内容。素材由 Python / Pillow（或 ffmpeg）生成，不依赖外部影视源。

- `/api?wd=slow` 延迟 25 秒，`wd=fast` 立即返回，用于搜索竞态验证。
- `/api?ids=1` 返回详情。
- 在 `$TV_FIXTURE_ROOT` 创建 `empty-home` 文件可测试首页空态，删除即可恢复。
- 第七条影片故意引用缺失海报，用于占位图验证。
- 模拟器通过 `10.0.2.2` 访问宿主；真机需设置 `TV_FIXTURE_BASE` 为真机可达的宿主地址。

## 已处理的问题与排查

- **Java 版本**：Codespaces 可能预设 Java 25；脚本会改选带 `javac` 的 JDK 21，避免选到仅有运行环境的 JRE。`TV_KEEP_JAVA=1` 可保留自行管理的 Java。
- **SDK target**：AGP 的整数 `compileSdk=37` 与 SDK 的 `android-37.0` 名称不同；构建脚本自动带上 `sdk-compat.init.gradle`，使用官方 `compileSdkMinor=0`，不改名或软链 SDK。
- **KVM**：模拟器需要 `/dev/kvm`。容器组号不匹配时脚本用 `setpriv` 加入实际设备 gid；devcontainer 显式映射 `/dev/kvm`，宿主没有该设备时需改用支持 KVM 的宿主；独立脚本无 KVM 时可能无法启动或极慢。
- **磁盘不足**：查看 `$TV_RESULTS_DIR/emulator.log` 和 `df -h /workspaces`。32GB Codespace 在构建后可能不足以创建 AVD；推荐 64GB。脚本会立即显示模拟器退出原因。
- **启动超时**：默认 240 秒，可设置 `TV_EMULATOR_TIMEOUT`。脚本单次 adb 探测也有超时，不会无限等待；失败后先用 `stop-emulator.sh` 停止再重试。
- **内存压力**：完整构建在原测试中内存峰值约 10.4GiB；若同时运行多个 Gradle daemon 和模拟器，应先关闭模拟器、限制 `--max-workers=2`。
- **旧 fork / 分支**：模板不覆盖已有代码。出现 `lib-decoder-av3a` 缺失等错误时，先核对仓库与 ref，而不是修改 SDK。

## 验证

```bash
python3 -m unittest discover -s tests -v
```

覆盖测试源配置、搜索、空态、视频 Range、跨目录 `.env` 加载、模拟器失败诊断，以及重复引导保留已有工作。
云端验证记录见 [docs/VALIDATION.md](docs/VALIDATION.md)。
