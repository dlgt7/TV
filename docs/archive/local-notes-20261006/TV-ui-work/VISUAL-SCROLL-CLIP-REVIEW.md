# 有界滚动视口裁切独立静态复核

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

结论：STATIC PASS（限定init裁切修复）；RUNTIME / VISUAL AFTER PENDING。未操作云端、未修改源码，不是运行通过。

所审 `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamPageSurfaces.kt` SHA256：`66b7e17abb1d7a3c51a645afba0c4b8c4025a99be893215620d15320e693afe6`，与root提供的66b7e17abb1d7a3c51a645afba0c4b8c4025a99be893215620d15320e693afe6匹配。工作树同文件其他视觉改动不在此小diff的通过结论中。

## 图像直接观察

已分别直接查看baseline/search-first-poster.png与r1/search-first-poster.png。

R1首张海报左侧边与标题在viewport内完整显示，但建议第2/3行明显穿透到“fast的搜索结果”标题、条目计数与筛选区域：文字层叠是明确BLOCKER。baseline也存在建议底部被截、海报左缘被切的问题；不能因为R1海报更完整就忽略新覆盖问题。

## 修复机制

JetStreamPageContentScrollView继承NestedScrollView；applyJetStreamScrollableSurface原本统一设置clipChildren=false、clipToPadding=false。新init在该函数之后明确恢复二者true，限制滚动内容绘制在视口内容区域内。它不改变滚动范围、测量高度、焦点查找或子按钮语义；内层非裁切容器保留，但无法再把越界后代画到视口兄弟结果区。调用顺序正确，未被init后续语句覆盖。

## 三个消费者的内部余量

| 消费者 | 静态证据 | 判断与待测 |
| --- | --- | --- |
| 搜索建议/历史 | SearchActivity结果出现时设scroll.height=128dp；scroll自身end4dp/bottom12dp；adapter_search_word与adapter_search_record均四周margin8dp；JetStreamRoundItemTextView当前focus scale=1.0、无焦点位移 | 8dp item margin承担实际focus余量，不依赖父clip=false。裁切128dp以外建议是正确行为；必须D-pad到第2/最后行，确认NestedScrollView真实滚入该项且焦点完整，不是仅把内容隐藏。 |
| 详情选集 | scroll左右margin44dp；内部ChipRow LazyRow contentPadding横/纵4dp、无focus scale；各行48dp高容纳40dp按钮；内容bottom8dp，section bottom margin18dp | 左右4dp内余量可容纳1dp TV focus边，48dp内容keyline保留。首/末chip以及最后一行上下滚动仍需实图，不能用整页不裁切来补边。 |
| AI字幕设置 | scroll fillViewport=true；子内容bottom24dp；两大PagePanel均padding24dp，交互控件在panel内；外层page左右48dp与bottom32dp | 24dp面板内部余量覆盖focus边；没有看到按钮贴scroll外界依赖溢出绘制。应测试最末保存按钮和内层长列表滚动后的边界聚焦。 |

没有找到因这两行true而必然裁切正常聚焦描边的静态反例，暂不要求扩大范围添加padding。滚动边缘部分可见的非焦点内容被裁切属于正确视口行为；焦点到达时必须滚入完整项。

## 运行验收门槛

1. 新构建绑定上述最终SHA。搜索建议第一行/最后行与首张海报三个焦点分别截图；结果标题/计数/筛选始终不被建议覆盖。
2. D-pad进入第2/最后建议后NestedScrollView自动滚动，四周焦点边完整；Up返回、Down转结果/输入符合既有路径。
3. 详情选集首/末/长列表及底部集数范围，横纵交叉滚动后focus余量完整；小窗/详情不被下部列表绘制覆盖。
4. AI字幕设置从首开关到最末保存，长文与展开内容后无半截focus描边；Back恢复正确。

这些条件均未在本次只读审查中运行，需after原图与遥控证据后再判定视觉/功能。

## 附加复核：搜索输入框伪焦点删除

结论：STATIC PASS；RUNTIME PENDING。所审`app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomSearchView.java` SHA256为`e175b9dab7914838f34e5e453e809442e7632acf97309ddf2bb1707bba49ceb8`，匹配root提供值。小diff仅删除isFocused()永远返回true的override，保留onFocusChanged动画和CustomEditText。

独立解析R1/search-first-poster.xml确认两个focused=true：keyword EditText bounds[108,160][736,272]以及首张海报父项bounds[848,628][1068,1024]。R1图片的输入框白边与海报白边并存，与错误焦点报告一致。恢复真实isFocused是正确的组件契约修复；白边最终是否消失还需候选画面，因为输入容器也有自己的背景状态逻辑。

已检查依赖：

- SearchActivity导航依据getCurrentFocus()的真实View身份、RecyclerView.findContainingItemView和selection边界，不用keyword.isFocused()当操作许可。
- CustomKeyboard.onTextClick直接读取Editable/getSelectionStart，StringBuilder插入后setText/setSelection；左右图标直接setSelection；删除直接改字符串并恢复selection；长按删除setText空串。它们并不要求EditText拥有焦点，也没有isFocused分支。
- SearchActivity.setKeyword明确setText后setSelection至尾；编辑器完成触发onSearch；CustomEditText内部按真实收到的按键和selection位置处理左右/上下导航，也无伪焦点依赖。
- KeyboardAdapter的点击/长按直接回调CustomKeyboard，不依赖输入框focus；因此遥控焦点留在屏幕键盘时仍可程序化编辑文字。
- 程序化selection作为文本数据可保留在未获焦EditText中；移除伪focus后光标/选区视觉只随真实输入焦点呈现，这是预期变化，不能再为常亮光标伪造控件焦点。

待测补充：海报/建议/筛选/屏幕键盘各位置XML只能有真实单个focused控件；输入框失焦无focus白边/闪烁。屏幕键盘输入、从中间插入、左右移选区、单次删除、长按清空后再输入全部实走，确认selection非负且位置正确；回输入框实体D-pad左右编辑、末端Right进建议/海报；IME完成、语音/推送文本恢复、Back返回再搜保持实际位置。该检查没有运行新版本，不将XML旧问题或源码修复写为新功能PASS。
