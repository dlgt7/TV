# Prepared QuickJS device run

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../../README.md)。

Do not execute until the root agent confirms its old-App probes and new-App installation are finished and hands over exclusive device use.

The script is already syntax-checked. It streams the four pinned private source files directly through `adb shell -T run-as` into `files/quickjs-regression` with umask 077, then checks device SHA-256. It never uses a shared /data/local/tmp copy of source code. It does not install an APK or edit a configuration.

Run after handoff:

```bash
python3 /tmp/tv-new-spider-api/device-quickjs/run_quickjs_regression.py --serial [REDACTED_DEVICE_IP]:5555 --tests all
```

`--tests api` runs only the self-contained loopback/JNI test. `--tests legacy` stages sources and runs the exact Tencent/Bili init/home/category two pages/search/detail/player cases; `--legacy-home-only` limits it to init/home. `--legacy-source` can select one source. Category/keyword overrides are optional. Shell stage names and raw test output cannot publish source credentials: raw logs and copied reports stay private, while stdout only prints whitelisted stage/count/hash fields. A deadline force-stops only the dedicated sourceprobe packages.

Files/results are private under this directory. The remote fixture files are app-private. No device command was run merely by preparing this script or calling --help.
