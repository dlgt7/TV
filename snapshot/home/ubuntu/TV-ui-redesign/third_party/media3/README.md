# Media3 本地补丁

上游仓库：`https://github.com/wobuhui666/media`。

固定基准：`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`。CI 的 release 与 validation 构建都检出该提交，不跟随浮动分支。

补丁：`paused-danmaku-toggle.patch`，SHA-256：`463eba72b1c56c19e38065a8d1f4476ed7a8e6b0d5bd39f0a8c05ceb47c169d7`。

该补丁修复暂停播放时关闭弹幕再开启后，画面中有效弹幕没有重新建立的问题。它只改动 `libraries/ui_danmaku/src/main/java/androidx/media3/ui/danmaku/` 下的 `DanmakuController.java` 和 `DanmakuView.java`，保留原上游许可。

TV 根 `settings.gradle` 在 `includeBuild(MEDIA3_SOURCE_DIR)` 前调用 `apply_patches.py`。CI、现有本地构建模板和手工构建使用同一入口，无需在各工作流复制应用命令。也可对选定 checkout 显式预检/应用：

```bash
python3 third_party/media3/apply_patches.py "$MEDIA3_SOURCE_DIR"
```

脚本只使用 Python 3 标准库与 Git：

- 校验补丁 SHA-256、checkout 根目录及固定 HEAD。
- 校验两个目标文件是补丁中的完整原始 blob，或均是完整修改后 blob；额外本地改动、文件类型/模式异常及部分应用会失败。
- 原始状态必须通过 `git apply --check` 后才能应用；已应用状态必须通过反向 `git apply --reverse --check`。
- 应用后复核完整 blob ID。不会执行 reset、checkout、clean、反向应用或修改 index；其他文件的改动保留。

如果脚本拒绝已有依赖目录，请先检查该目录的 HEAD 和本地改动，或另建基准提交的 checkout。不要删除改动来绕过校验。更新补丁时需一并审查完整补丁、脚本中的 SHA、此处 SHA 和固定基准；用 `git diff --full-index` 保留两个文件的完整 old/new blob ID。

宿主机保护逻辑测试（临时 Git 仓库，不启动 Android 构建）：

```bash
python3 -m unittest discover -s third_party/media3 -p 'test_*.py' -v
```
