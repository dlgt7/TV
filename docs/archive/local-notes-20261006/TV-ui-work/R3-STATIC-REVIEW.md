# R3 static review

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

Reviewed the current uncommitted changes in `/home/ubuntu/TV-ui-redesign` on 2026-10-03. Read-only source review; no build, emulator, device, cloud, signing, or installation actions performed.

## Final disposition

- No outstanding definite static blocker found in the reviewed paths.
- **Resolved — Track → subtitle-style transition consumed the return target too early.** The former 100 ms gap is removed: `TrackDialog.onSubtitle()` now opens the child directly before dismissing the parent. `VideoActivity.onSubtitleClick()` immediately hides controls and stops their hide timer. The new side-sheet dismissal listener posts restoration even when an empty dialog closes before losing window focus; the existing visible-dialog/window/activity guards remain in place. These changes address the identified source-level cases; actual callback ordering still requires runtime verification.

## Verified corrections

- Playback restoration no longer waits 180 ms, and a subsequent Activity ACTION_DOWN cancels its pending callback and command. This resolves the previously reported stale restoration after a new user action.
- Danmaku settings tabs are now vertical, full width, and inside the scrolling content; the former four-column English-label truncation is removed structurally.
- Track and danmaku titles now have explicit 64 dp height, providing bounded space for their Compose-backed headers.
- Search cancels pending suggestion requests and advances their generation before opening results. History clicks restore record index 0 after the selected record moves there; additions now persist history immediately.
- Search result navigation uses the full result Activity. Returning to edit from a suggestions-origin result finishes back to the existing input page; initialization no longer immediately resubmits its keyword.
- Home recommendation rows are split using the same column count used for poster sizing. Refresh removal covers the full recommendation range, including multiple rows.
- Playback selection dialogs use a TV side-sheet base and retain the mobile bottom-sheet base. No reviewed selection class depends on a removed bottom-sheet-only method. Existing side-sheet facades retain their mobile behavior.
- Playback card surface uses 16 dp corners and alpha 245. Progress hint uses an explicit 20 sp line height inside a minimum 32 dp container with 6 dp top/bottom padding.

## Checks and limits

- `git diff --check` passed.
- All 12 changed XML files parsed successfully using Python ElementTree.
- No additional definite static blocker found in the reviewed paths. XML parsing does not establish Android resource linkage, Kotlin/Java compilation, runtime sheet geometry, or remote-focus behavior.
- AI command wiring is unchanged and outside this review pass. Back from nested subtitle/danmaku dialogs still returns to the command drawer rather than reconstructing the dismissed parent selection list.
- Runtime follow-up should cover long track lists, empty-track actions, child-dialog transitions, rapid Back, local file chooser return, English danmaku tabs, multiple home rows and refresh, and search results → Back/edit → restored item.
