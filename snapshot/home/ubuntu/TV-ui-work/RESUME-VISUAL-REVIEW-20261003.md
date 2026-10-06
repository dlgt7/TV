# 2026-10-03 独立视觉复核

结论：R2E 核心页面显著改善，可以关闭对应画面上的旧缺陷；全页面最终验收仍不能通过。此评审只读源码/本地证据、未操作云端。已读 AGENTS.md、VISUAL-REWORK-AUTHORIZATION.md、VISUAL-REWORK-ISSUES.md，并逐张直接查看用户 IMG_1660–1665。

## 已直接确认的改善

以下路径均相对 `/home/ubuntu/TV-ui-work/visual-rework/`。这些是有限画面结论，不代表遥控路径或全状态通过。

- V01/V02：baseline/detail.png 与 r2e/detail.png。视频白色描边四角由断裂变连续；视频底约 y488、右侧按钮底约 y485，已基本对齐。选集左沿统一。两图播放集数不同，故可确认几何改善，不能当同集数焦点回归。
- V03：用户 IMG_1661 与 r2e/home-nav.png。logo、首页文字、其余 nav 的中心线已协调，首页用深色细边焦点，未见旧垂直偏移。
- V04/V05：baseline/search-first-poster.png 与 r2e/search-first-poster.png、search-last-poster.png。建议、结果标题、统计、来源、海报明确分离；首卡左侧描边与标题 fast 完整；两张结果卡焦点边界均完整。建议不再覆盖标题/来源。这组仅短查询 fast、2 结果，不覆盖多行长建议或四列满宽。
- 首页 V05/V15：baseline/home-posters-first.png 首卡上沿裁断且下半屏空白；r2e/home-posters-first.png 保留上一行上下文、完整栏目标题和完整首卡边框，海报与副标题在屏幕安全区内。r2e/home-missing-art.png 与 home-long-title.png 右边缘焦点完整，缺图占位正常。顶部上一行标题滚出屏幕属于非焦点滚动内容，不能误报首卡裁切。
- V06：r2e/settings-live-primary.png、settings-live-chip.png 对比用户图/基线，整行白块已改深灰细边；主页小图标左右有空间、无旧左边裁切。r2e/settings-live-dialog.png 弹窗主项也使用一致深色焦点。
- 历史：baseline/history.png 与 r2e-qa/history-first.png、history-next.png，图片有内边距，横图不再贴左裁切，横/竖图自然保留比例，两个焦点均在安全区内。长标题显示两行省略，没有撑破卡片。
- 我的/收藏/推送：r2e-qa/my-keep.png、favorites.png、push.png、push-action.png、push-action-result.png、cast-return.png。所见焦点边框完整，icon 不裁切，推送二维码与按钮分区清晰；复制成功提示不压内容。cast-dialog.png 文本完整，弹窗留白足够；静态截图不证明确定键功能。

## 真实残留视觉问题

1. **V07/V09 文件页焦点样式仍未收敛（应修后复核）**：r2e-qa/files.png 和 files-next.png 都有很粗的白色整行外框，并同时给文件夹 icon 加圆形白框。与设置/推送/我的的细边单层焦点明显不一致。比 baseline/file.png 的整行白底改善，但仍不是共享单层细边规则。主执行方已告知本地两处修复，尚无修后截图，故保持待验证。
2. **V10/V11 发现页当前画面不合格，持续性需确认**：r2e-qa/discover-home.png 与 baseline/discover.png 一样，上方约八成页面空白，仅底部显示今日趋势/本周 Top10；discover-focused.png 显示无内容的栏目、下方骨架，无明确错误/空数据说明。这是当前截图确见的视觉问题；不能仅凭截图断言长期卡加载或数据逻辑错误。需加载稳定后的内容/空/错误图来决定修复，不能把骨架截图当页面已通过。

没有从所见图中确认其他新的高优先级布局 blocker。不重新扩大 UI 范围。

## 无效或缺失证据（不是已证实产品缺陷）

- **V12 片库取证失效**：r2e-qa/library-first.png、library-card.png、library-last.png 实际全部是竖屏 Android 相机位置权限弹窗，完全不是片库。这三条路径必须重跑；不能按文件名标记通过，也不能据此断言 TV 片库本身会启动相机。
- r2e/search-input.png 实际焦点在“全部”来源按钮，不是输入框；输入焦点验收缺失。
- 所查 r2e/r2e-qa 没有全屏控制、播放设置抽屉修后图；baseline/player-drawer.png 确有只有值无标题和底部撑宽焦点旧缺陷。V08/V13/V14 的实现不能靠代码或旧基线关闭。
- 设置其余类别/主题选中前后、AI 字幕裁切回归、搜索长文/空结果、详情长标题/缺 meta/选集滚动、直播、一般空/错误页未由本次所见证据覆盖。按原清单补齐，不新增需求。
- 截图只可判断图像，不代替真实 Dpad 进入、OK、Back、焦点恢复和真机性能/播放内核验证。

## 新补图复核

另直接看 `/home/ubuntu/TV-ui-work/resume-evidence-20261003/r2e-qa/`：

- source-vod-primary-open.png、source-vod-edit-open.png：确实有配置弹窗；输入框长 URL 横向滚至光标尾部，可见文本左端截断是编辑框滚动，不作为海报式裁切缺陷。
- source-vod-home-open.png：实际仍在来源页、主页按钮聚焦，没有弹窗；source-vod-home-back.png 实际是 launcher。不能认定主页子按钮打开/返回成功。主执行方正查运行/fixture 原因，本评审不先断言代码故障。
- detail-start.png：实际只有 loading，不能替代详情或播放器焦点证据。

建议下一步先修取证前置状态，再补真实片库/播放器/稳定发现状态；文件页在新包按首行、第二行、底边焦点重拍。保留已确认改善，不重做它们，不把未覆盖状态误当缺陷已修或功能已坏。

更新：主执行方随后定位上述 sources 无弹窗/详情 loading 为 QA 使用 CLEAR_TASK 销毁 Home，触发配置清空；已改为 CLEAR_TOP。旧 09:04–09:08 批次归为取证脚本失效，不计产品缺陷。新有效批次尚待直接看图复核。
