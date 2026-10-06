> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

TV focus borders used different poster radii, search results shared the suggestion screen, and playback options mixed bottom and right sheets. This change aligns poster geometry, uses multi-row home recommendations, moves confirmed searches into fullscreen results, and presents playback options in consistent right-side cards. It also fixes clipped seek instructions and restores the home position after window interruptions.

Settings and playback panels now provide a scoped Material 3 example using theme color roles and actual Material buttons, with distinct filled, tonal, outlined and text actions. Subtitle size/position controls use MaterialButton icon placement; native and Compose controls preserve D-pad focus and long-press callbacks.

Validation: TV arm64 debug assembly, 223 unit tests passed, lint 0 errors (313 warnings). API 24 emulator D-pad/screenshot checks covered settings navigation, subtitle/audio panels and drawer focus return. Samsung SM-F900F API33 uses a separately signed preview package; the original installation and configuration were preserved. Source-plugin isolation remains intact. No playback-engine changes.

Screenshots and private APK/signing artifacts are stored outside the repository. Current credentials lack upstream write permission, so this description is prepared locally and has not been posted to PR62.
