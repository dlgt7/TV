# R4 Material visual and cross-review

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

Reviewed 2026-10-03. Directly viewed these three real R4A captures:

- `r4a-settings-source.png`
- `r4a-settings-playback.png`
- `r4a-subtitle-settings.png`

Reviewer scope: no cloud/device/build/installation actions in this review. This agent authored the Compose settings implementation and shared MaterialSettingsButton; its settings observations are a visual self-review, not an independent implementation review. The native subtitle buttons, palette integration, and playback drawer were authored by root and received this agent's separate source review.

## Visual conclusion

No blocking visual defect is apparent in these three frames. They demonstrate the requested Material pilot on the visible settings sections and subtitle-style panel; they do not constitute whole-app visual acceptance.

The source settings frame has distinct selected-navigation, focused-action, tonal-action, and outlined-secondary treatments. The blue selected section includes a check mark. The focused source button's label and supporting value remain dark and readable on the light accent. Home/history icons have visible breathing room and align with their labels; neither is clipped. The larger group layout displays two complete source cards and the start of the next card. This is a density tradeoff requiring scrolling, not proof that the lower card is unreachable.

The playback settings frame shows the same selected-navigation treatment, a clearly focused engine row, aligned values, consistent rounded rows, and a readable checked switch. The visible labels and values fit without overlapping. The lower subtitle-style row is partially outside the scrolling viewport; this screenshot does not verify last-row navigation or scrolling.

The subtitle-style frame shows a rounded inset right card, a text-style reset action, and four consistently rounded Material buttons. The focused larger-text action has clear contrast. All four icons and labels are visibly centered as groups with adequate space, and their outlines are not clipped. This image was taken at `03:00 / 03:00`; it establishes panel appearance, not successful subtitle resizing/repositioning during active playback.

## Source checks

- Native controls extend MaterialButton and retain Material's background rendering, icon placement, ripple, and click handling. Subtitle action IDs and explicit up/down focus links remain intact.
- Compose controls use AndroidX TV Material3 Button, retaining remote-click/long-click handling and the existing callbacks/focus requesters. The Material color scheme is scoped to settings and the playback command drawer.
- Native state lists pair focused primary with onPrimary and selected primaryContainer with onPrimaryContainer. Compose supporting values follow LocalContentColor with alpha 0.8 instead of retaining a light fixed color on focused light backgrounds.
- Static calculation for 21 fixed-palette primary/primaryContainer/secondaryContainer text pairs found a minimum contrast ratio of 6.72:1. This calculation does not cover every rendered state, image background, or user-supplied color.
- The six track/danmaku icon actions now have explicit localized content descriptions in R4B source. Their presence is confirmed statically; screen-reader announcements were not exercised.
- `git diff --check` passed. Root reports R4B original and preview builds, unit tests, and lint exiting successfully; this reviewer did not run those builds or inspect their complete logs.

## Limits and follow-up

The screenshots are R4A. Root states R4B adds only the six icon descriptions; these frames must not be labeled as a direct capture of R4B. Final Samsung installation/validation was still in progress at review time.

Not visually covered here: playback command drawer, track/danmaku selection and their icon-only buttons, lower settings rows, long values, alternate theme colors, narrow/font-scaled layouts, long press, source-card auxiliary focus, theme selection, navigation restoration, timer dwell, or last-row focus. The new descriptions address semantics in code but require accessibility runtime verification for a stronger claim. Existing R3 home corrections and other app pages are outside these three R4 frames.

Recommended final runtime checks: traverse primary and auxiliary source actions; scroll to the final settings row and return; open/close the subtitle panel while playing and adjust/reset its controls; verify track/danmaku icon centering and return focus on Samsung. Record those outcomes separately from this visual review.
