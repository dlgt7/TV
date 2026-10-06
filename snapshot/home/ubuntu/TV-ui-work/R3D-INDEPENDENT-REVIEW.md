# R3D independent review

Reviewed 2026-10-03 by the read-only QA agent. Scope: all 38 R3D changed/new files, seven PNGs under `r3-review`, eleven final PNGs under `r3-final-review/r3-final`, and the subsequently supplied Samsung `r3-evidence/samsung/home.png`. No cloud, ADB, device, installation, build, or app-source modification was performed by this reviewer.

## Current verdict

**Do not mark R3D fully accepted:** the subsequently supplied Samsung home screenshot shows the history row overlapping the top navigation. Final demo screenshots confirm the search and player-panel improvements below, but the real-configuration home defect requires correction and a fresh screenshot. The earlier static review did not detect this runtime ordering failure.

## Final-frame supplement

Directly inspected all eleven final PNGs. Search suggestions/history are spaced clearly; fullscreen results show four complete posters and untruncated demo titles. `search-back` visually restores the same history item; its JSON focus text and bounds match `search-suggestions`.

`audio-right`, `subtitle-right`, and `danmaku-right` share the same inset right-card geometry and surface. Visible text, icons, and focus outlines are not clipped. The subtitle image is **track selection**, not subtitle-style settings. Danmaku selection has no data rows and therefore only proves the empty-list action state. `danmaku-settings-right` confirms readable vertical section buttons and the beginning of scrolling settings content; lower controls are not covered.

`drawer-return-idle` visibly retains the audio command focus; `drawer-close-idle` contains no control drawer and its JSON focus returns to the video. Root reports seven-second waits before both captures; the still frames establish the resulting states, not elapsed duration independently. `seek-hint` shows the complete hint line with space above the action controls. `player-detail` shows a rounded player outline, readable action buttons, and an aligned eight-episode row without an obvious overlap.

Two follow-ups remain from direct visuals:

- **Blocking — Samsung home overlap.** `r3-evidence/samsung/home.png` places history cards behind the navigation at the top of the screen. Source review identifies an initial-focus request consumed while the notification permission window prevents focus, followed by hero insertion changing the content inset and no window-focus recovery to realign the list. Root has been given a guarded pending-top restoration proposal; no fixed-device evidence reviewed yet.
- **Visual consistency — danmaku section button corners.** The vertically spaced MaterialButtonToggleGroup still applies joined-group corner treatment: first/last buttons have only the outer corners rounded, middle buttons are square. This is visible in the final image and differs from separated rounded drawer rows. Root plans to restore each vertical button's four corners after the group's measurement; that fix is not yet reviewed here.

## Direct visual observations

| Evidence | What is visible | Limit |
| --- | --- | --- |
| `r3-baseline/poster-focus.png` and `r3-candidate/poster-focus.png` | The same “城市的夜” poster is focused. Candidate outline follows the rounded image corners; the baseline's sharper outline corners no longer protrude. Candidate shows a five-column first row and a second row with consistent spacing. | Scroll positions differ, so this verifies the focused card and row structure, not identical viewport framing. Second-row labels are below the captured viewport; that alone is not clipping of the focused item. |
| `r3-candidate/home-ready.png` | The upper-right site name is transparent text with a dropdown arrow. Top navigation text/icon appear vertically aligned without visible clipping. | Baseline hero is “远山来信”, candidate hero is “冬日旅人”; hero artwork cannot be judged as a strict same-content comparison. |
| `r3-candidate/poster-focus.png` | A second-row fallback poster displays “星” within a rounded card. | It is not focused, so missing-art focus behavior is not established. |
| Baseline audio/search/seek images | These show the old bottom audio sheet, embedded narrow search results, and old seek hint. | They are baseline evidence only; no candidate counterpart was available at this review time. |

No new definite visual defect is apparent in the two candidate frames. Neither their limited coverage nor build/test success is a full visual pass.

## Static completeness

- Inspected the complete 38-file change inventory: 34 tracked modifications plus four new files. `git diff --check` passed, and all 13 changed/new XML files parsed successfully.
- TV/mobile playback selection bases are both present. The six migrated selection dialogs have no remaining bottom-sheet-only API dependency. Existing subtitle, offset and danmaku side-sheet facades retain mobile presentation branches.
- Track-to-subtitle opens the child before dismissing the parent. Subtitle opening hides controls immediately. Dismissal/window-focus restoration checks active dialogs and Activity/window validity; newer Activity key-down input cancels a pending return target.
- Timer correction removes older hide posts before scheduling, suppresses scheduling with a drawer open, checks drawer state again when a queued callback runs, and restarts the normal timeout after closing the drawer. This covers the `showControl` then `showGroup` restoration ordering statically.
- Search freezes pending suggestion responses before leaving, restores a clicked history record after its move to index zero, persists added history, and avoids automatic resubmission on input-page initialization. Result-edit returns to the existing input page for suggestion-origin searches.
- Home grid splitting uses the same column calculation as card sizing; refresh removal covers all recommendation rows. Poster outline radius and image radius share the same constant. The new continued-watching border change affects thickness only.
- Narrow playback dialogs keep their binding IDs. Track/danmaku titles have bounded heights, long selection lists receive remaining height, and danmaku section buttons are vertical within scrolling content.
- Shared text metrics and explicit seek-hint line height address the known baseline geometry issue without changing playback selection logic.

## Remaining coverage

Still needed for independent visual coverage: corrected Samsung home, last home row, subtitle style/offset, video-track selection, populated danmaku/search and lower settings controls, parser/player/edition/chapter selection, result-edit and suggestion-item return paths, global settings action focus, and long/edge-focused selection lists. The supplied search return path covers a history item. Root's seven-second timer results are supported by the final drawer-visible/drawer-hidden frames.

Nested dialogs still return to the originating command drawer rather than reconstructing a dismissed track/danmaku selection list. AI command wiring is unchanged. Neither behavior should be described as newly implemented nested navigation or complete AI-option unification.

Root reports R3D original and preview builds passing, Samsung preview installation, and sixty seconds of process stability with copied configuration. This reviewer did not independently run those checks and treats them separately from visual acceptance. The Samsung home overlap found after installation remains a reason to continue the repair.

## R3F static follow-up

Confirmed the subsequent HomeActivity correction in source: initial pending is retained until navigation focus succeeds; result handling has a separate `else if (mInitialFocusPending) requestNavFocus()` branch; window-focus recovery and history insertion request navigation focus only for initial pending or existing navigation focus, rather than all toolbar descendants. This addresses the identified pending-request cancellation race and preserves the site-title focus when its dialog closes. Navigation restoration resets both list selection and scroll position. `git diff --check` passed again.

The vertical toggle group's `onMeasure` restores all four 12 dp corners after MaterialButtonToggleGroup's measurement; horizontal groups retain their existing behavior. No new definite static blocker was found in this small follow-up. Root reports the R3F original build passing, with preview completion in progress. No new device screenshot was reviewed in this follow-up, so the Samsung home overlap and revised button appearance remain pending runtime/visual confirmation; R3D's images must not be relabeled as R3F verification.
