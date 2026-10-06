# TV UI visual rework — draft, runtime acceptance pending

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

The previous TV UI delivery had visible clipping, inconsistent player corners and alignment, crowded search results, and oversized focus surfaces. This rework uses AndroidX TV Material buttons/surfaces for Compose controls and retains Leanback navigation and Media3 PlayerView/service ownership.

The candidate unifies focus geometry, spacing and player corners; separates search suggestions, filters and results; corrects landscape history cards and empty states; preserves the 80dp toolbar inset without a Hero; restores real episode focus when selection changes; and gives player drawer values clear localized labels. Changes remain in the TV source set except the TV dependency configuration. Playback callbacks, service/progress ownership and API24 compatibility remain requirements of runtime verification.

Current status: **draft / not visually accepted**. R1 built successfully once. The frozen R2E candidate (17 files relative to R1; 46 changed/new files overall) has independent static review and matching SHA manifests, but has no verified build or runtime result yet. R1 screenshot review found remaining home bottom whitespace and a search suggestion viewport overlap; R2E addresses both and removes the search input’s false focus override. These fixes still require matching screenshots and remote navigation checks. No screenshot comparison or D-pad acceptance is implied by static checks.

Before marking ready:

- Bind final source and APK SHA to the build and installation evidence.
- Directly compare matching content/focus screenshots across the full page matrix, including long text, empty data, missing art, edge focus and dialogs.
- Record real remote navigation, focus restoration, selection/long-press callbacks, playback continuity and Back behavior separately from visual review.
- Resolve material findings and obtain independent visual review.

Historical functional QA, prior videos and the old delivery result do not establish acceptance of this visual rework. Real-device/production ARM playback, Python/MPV, hardware decoding, real casting and performance remain unverified unless new evidence explicitly covers them; software-emulator jank is not production performance acceptance.

Final evidence links and validated commands will replace this pending section only after the matching candidate passes. Do not merge or mark ready from this draft.
