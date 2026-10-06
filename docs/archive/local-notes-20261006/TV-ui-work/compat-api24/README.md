# API 24 ART MediaMatcher compatibility verification

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../../README.md)。

Result: PASS. The standalone harness actually executed on the Codespace Android 7.0 / API 24 x86_64 emulator, using dalvikvm / ART 2.1.0. Its exit code was 0 and all 31 checks completed (30 production behavior checks plus the device API assertion).

Production MediaMatcher.class and MediaIdentity.class were taken from the current UI worktree's successful Java compilation at `/workspaces/TV-ui-redesign/app/build/intermediates/javac/leanbackArm64_v8aDebug/compileLeanbackArm64_v8aDebugJavaWithJavac/classes`. Their SHA-256 values exactly match the previously retained PR #61 classes. No production source was changed for this verification. The production classes and standalone harness were converted by D8 with `--min-api 24`; no Gradle task was invoked.

Coverage: production static Pattern initialization, title/year/season normalization, numeric episode parsing, the production episode-name character scanner (Chinese episode suffixes, fullwidth digits, non-episode titles, supplementary Han characters, null), and MediaIdentity compatibility/equality/key behavior. The historical regex control `\p{IsHan}` was rejected on this ART with `U_ILLEGAL_ARGUMENT_ERROR`, while production initialization and all assertions succeeded.

Limits: excludes the full APK, UI, native libraries, Vod overloads, and the app-dependent Trans implementation. With Trans omitted, the production normalize() method uses its existing caught-Throwable fallback. This is direct ART coverage of the named classes, not proof of complete app compatibility on Android 7.

The original retained harness first failed before calling production code because standalone dalvikvm does not register the Android framework SystemProperties natives used by android.os.Build. Only the harness metadata read was changed to accept SDK/release from getprop executed in the same adb shell. The initial failure is preserved in api24-runtime-initial.log. The successful run is api24-runtime.log, with exit-code.txt containing 0. A wrapper-only rg-not-found error occurred after the successful run; the final verification uses grep and completed successfully without rerunning or changing the device.

No UI input, activity switching, emulator restart, SDK installation, or repository changes were performed. Other agent screenshots and the root build were left running.

Key files:
- api24-runtime.log: complete successful ART output, including 31 PASS results and limits.
- exit-code.txt: actual adb/dalvikvm return code.
- runtime-environment.txt: same device SDK/release/ABI and initial provenance.
- current-class-sha256.txt: production classes and final DEX hashes.
- CompatMediaMatcherHarness.java and dex/classes.dex: executable validation artifacts.
- class-sha256.txt and original-classes.dex: original retained provenance/DEX.
