# R1 首页独立画面对照与末行滚动审查

结论：PARTIAL IMPROVEMENT / 首页未通过整体视觉验收。已逐组实际打开baseline和R1三对原始1920×1080截图，每次2张；另读对应UI XML确认焦点语义。R1身份来自VISUAL-R1-INSTALL-VERIFIED.json：测试UI APK SHA145cf24a6440399416d90ed313cb65665ee41b3ddcc0fe12aade3e13e3c6bba0。R2C及最新末行修改未运行，不可并入这些图片的结论。

## 三组实际所见

| 对照 | 同内容/同焦点核实 | 可以确认改善 | 未解决/不能据此通过 |
|---|---|---|---|
| home-nav | 均为深空回声山景Hero、相同导航六项、焦点首页；旧XML焦点是nav host，新XML为首页单目标，语义相同而技术节点不同 | 首页由亮白块改深灰细边，文字在pill内基本垂直居中，和logo/其余导航中心线一致；选中线独立呈现 | 右上“演示片库”仍几乎顶到左右边框，source目标padding未明显改善；此图不覆盖其长来源名、其他nav项/无直播场景，不能关闭整个导航矩阵 |
| home-continue | XML均为“深空回声, 第2集, 剩余2分钟”，另一项相同长标题/竖图，焦点一致 | 旧焦点card顶边在y0被截，新图完整顶边约y28，四角完整；横图槽变大且有内缩，纵图也有内边距；首图不再贴左外边 | 长标题新图显示更少文字并省略，这是固定图片区扩大后的剩余宽度结果；仍需缺图/多行标题/完整历史画面评审；R1 card高度仍124dp，不能拿此图证明后续112dp已验收 |
| home-posters-first | 两份XML焦点子文案均“深空回声/2026/完整影片”，卡片及邻卡相同 | 首卡顶边由y0裁切变成约y22完整可见，左右/下角均完整；缩放幅度明显收敛，焦点文字未从左边界被裁 | 主要内容约y557结束，之后至1080约半屏为空；该末行强制顶到顶部的构图仍不合格，源分区标题也已滚出屏幕。不能只因修好顶边就宣称首页通过 |

同焦点的含义是同一业务目标，不要求布局修复后坐标相同。以上只观察静帧，不能证明动画中途、快按、Back恢复或播放功能。

## 末行空白根因与最小成熟组件修复

独立读取官方leanback1.2.0 POM，确认依赖leanback-grid1.0.0；读取其BaseGridView/WindowAlignment官方源码。WINDOW_ALIGN_LOW_EDGE只约束起始边，末行仍可对齐16dp keyline，造成下半屏空白。WINDOW_ALIGN_BOTH_EDGE同时约束末行高边，但默认preferKeyLineOverHighEdge=true，故还需显式false。low edge默认优先edge，无需重复设置。

已只读复核root最新两行：

```java
setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_BOTH_EDGE);
setWindowAlignmentPreferKeyLineOverHighEdge(false);
```

这是沿用Leanback原生边缘滚动约束的正确最小修复，没有新增手工scroll或计算内容高度。首Hero仍受low edge/padding0约束；无Heropadding80/keyline80不变；有Hero中间普通行保持16dpkeyline；最后一行到达后由高边padding48约束停止继续上滚。少数据不足一屏时应由low edge保持顶部，不强行沉底，需运行确认。HomeActivity最新SHA7b05fefd3ebd58b279d1b14f4587162f7f4630b17d4cd39ec7156e8c737fffc5。此段是STATIC PASS，未把空白问题标记运行已解决。

下一轮必须重截同内容同焦点三图：nav首屏稳定；Down继续观看边框完整；再Down最后海报时来源标题/上行可合理保留、底部只有正常48dp安全区。另测无Hero一行/多行/空页、末行左右、Up返回Hero、Back恢复顶部及异步数据加载后边缘重新计算。

## 六张图SHA256

- `baseline/home-nav.png` `c3514410af76a0525f83f67e04173332f5c1c83a3c20ad235a7b71307835e158`
- `baseline/home-continue.png` `3bfa586bdd16e6c655e6a416e01782c99d115563ea06319fe99f08fc578a54f0`
- `baseline/home-posters-first.png` `4ea3e2ed59e5a831760d5b13345cf7391b1679e8101adb04af40f14d8b68e7e8`
- `r1/home-nav.png` `3426c7f23345c6ed3ed2693facde2b2e203960c204ab76b02f19eec300b262d7`
- `r1/home-continue.png` `40ab389e34dfc8d61ab6033462033cddeb38d71396477ac4ceb4e7589f7365dd`
- `r1/home-posters-first.png` `54108176a92f3f97076a341166480ab85b3263e4e69fab6e3f0bb63d4e5d4c14`

## R2E 来源标签padding补充静态复核

只读复核JetStreamHomeTitleView新增`setPaddingRelative(jetStreamDpInt(12), paddingTop, jetStreamDpInt(12), paddingBottom)`：start/end各12dp，保留原上下padding；位于背景/typeface设置之后，不会被同一init后续语句覆盖。XML仍为40dp高、wrap_content宽、maxWidth120dp、单行end省略和center gravity；不改变原点击/长按回调。短来源可自然测量“文字+24dp”，长来源在最大宽度内省略，语义上正确修复贴边。STATIC PASS。

JetStreamPageSurfaces最终SHA256：`32ed6908c096fb842b712c615640d40c71870b696b1bbc782c285125a23b1326`。该文件其他ScrollViewclip修改未在本单点复核中重复审核，以其既有独立报告为准。候选R2E已冻结但未构建，R1的“来源贴边”运行问题仍保持待验：下一轮须短/长来源、聚焦/失焦真实截图确认12dp留白及不挤压导航。未重新读取六图，也未把源码修复改写为新画面PASS。
