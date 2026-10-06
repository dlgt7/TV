# R2 独立静态审查

结论：STATIC PASS（修订后），不是运行PASS、视觉PASS或完成标记。只读审查，未操作云端、未构建、未修改源码。

## 审查发现与本地闭环

1. 初审发现 HomeActivity.getHistorySpec仍以124dp覆盖adapter/HistoryPresenter的112dp，导致首页与完整历史规格不一致。已通知root；复核最新源码getHistorySpec、HistoryPresenter默认高度、WatchHistoryActivity显式高度和adapter_history均为112dp。标题区域仍允许2行，112dp减上下28dp内距余84dp，次信息与时间各12sp，需真实长文图确认字号度量和省略。
2. 初审发现JetStreamChipRow.setItems在texts相同、selected变更且已有焦点时，snapshot这个LaunchedEffect key不变，因此真实Compose焦点不会跟随focusedIndex，getFocusedPosition/OK目标可能不同。root已增加previousFocus比较，目标变化且hasFocus则增加entryFocusToken；本次复核已覆盖该确定路径。
3. 初审发现scrollToItem为挂起调用，之前只在调用前检查hasFocus，可能退出View后才执行requestFocus。root已捕获token/target，scroll结束重新检查hasFocus、token、items快照后才请求固定target；新列表/新请求/离开View不再直接套用旧请求。

## 其余检查

- 空态：view_empty提供稳定empty_state id；ProgressLayout构造时已inflate并添加，因此KeepActivity初始化findViewById可写收藏专属文案。新空态仅Icon/Text，没有新可点击目标或数据库行为；Back为既有Activity行为。min180dp不等于强制截高，文本最多3行、wrap_content，三语言资源均存在。
- 直播行：两种行focus动画均为1.0，不再向父列表之外扩张；focused/pressed使用同一10dp形状+1dp描边，clipToOutline形状一致。未更改频道点击、收藏/EPG、导航逻辑。仍需真实浅色画面与首末行截图确认可读性及父侧栏边界。
- 主题固定槽：所有swatch按钮固定6dp间隔+16dp Box，仅内部勾选Icon改变显示；上次P2的22dp宽度变化已消除。普通无swatch且非selected动作没有无意义占位。主题实际像素反馈、五色持久化仍需运行。
- JetStreamChipRow：每个LazyRow item有独立FocusRequester+TV Button，内部Left/Right/OK由成熟组件处理；只拦Up/Down交给明确nextFocus目标或View.focusSearch。先scrollToItem再requestFocus的顺序正确，且本轮已添加挂起后失效检查。未发现可凭当前源码确定的离屏requester异常；这不是其运行证明，必须测数百集/离屏选中/反序/跨行返回。
- 播放控制按钮：本批固定40/44dp，图标18/24dp与内容padding可容纳；未通过换PlayerView或服务规避视觉问题。

## 保留待运行验证的风险

- LazyRow离屏FocusRequester挂载时机、长按只触发一次、Left/Right跨多个viewport与Up/Down后返回原集；同labels更新selected；播放自动切集时焦点与选中状态分别检查。
- 长集名无限测量风险已在末次复核修正：JetStreamChipRow导入widthIn，并在TV Button modifier最外层加入widthIn(max = 280.dp)，使LazyRow内按钮获得有限最大宽度，既有maxLines=1/ellipsis可实际生效。源码限制合理且未改焦点/回调；长中英文集名真实截图仍待运行，不因静态修复就写已通过。
- 空收藏/无内容Back、直播首末频道与长名称、主题OK前后位置、首页/完整历史长标题和两行元信息。所有仍需本轮APK截图和遥控实测。

## 验证和SHA

`git diff --check`通过；R2清单4份XML语法解析通过。R2 12文件SHA与当前清单一致：是。

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java` `f3be4d0e2a108ca3bfbbdfb73d960c2ec62ea5c9e15d8439cfa0ef10c7dfcfe1`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/KeepActivity.java` `3c88a1fa9b536b223367aff9ef562ee668a0195e3715546610aa3d9405b7b0a1`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/WatchHistoryActivity.java` `2a97db9d0caeff8442d918e5ad7688d6b50f05c3017f95deb5c523a61f9774f5`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamChipRow.kt` `448b617695826f75e74245fddf9a836c1b85a74cce5cef48357d3001569c4033`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamEmptyStateView.kt` `70ec8b116da7f77f168ce5b426cfec9da2a8b567b4b1743c39ee29752d731f1a`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamLiveSidebarLayout.kt` `14f5c78f01b204834b6819b16a4d4b5417d942555b92d32786b6061a27f3614c`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamSettingView.kt` `18c40893b93f305c34e2d565c428e5313645dbfb25f391ef60e820e656b97da6`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/JetStreamVodControlView.kt` `473456228525f3f8ef7111c66e1fdc30e15e99e71a0f717e57fdb1398617c9dd`
- `app/src/leanback/res/layout/view_empty.xml` `da31c93d2a7cc35eb449e71377c7be357dd653c3768f3cd7d83bfec004450539`
- `app/src/leanback/res/values-zh-rCN/visual_empty.xml` `6a83802a939ceb32b9d7401d3c88b6057ecc97425948679227c8da381a00b090`
- `app/src/leanback/res/values-zh-rTW/visual_empty.xml` `49457aad9839eac3d74db2135db0c55efd8c9e2a6077eea068ba62c2447ad2f9`
- `app/src/leanback/res/values/visual_empty.xml` `78563407e4fdeb109afc893b26d116da40b6e7339f798d32e7ce446aad11f260`

附加核对历史布局/默认presenter：

- `app/src/leanback/res/layout/adapter_history.xml` `3e842d0147b630210f61d2079e989af64c8e890e9ad0fac66b271cce40ded3b2`
- `app/src/leanback/java/com/fongmi/android/tv/ui/presenter/HistoryPresenter.java` `0b9bea39c187e9731e51755b38e6e7bec7bc30f18e296740928de64dc766ecc5`

末次单文件复核：ChipRow widthIn(max=280.dp)及import已核实，SHA已更新为448b6176…c4033；`git diff --check`通过。结论维持STATIC PASS，运行/视觉待验。

## R2b 首页keyline补充静态复核

此前选中普通行时固定16dp会覆盖无Hero页面已经设置的80dp keyline。现已确认代码按像素计算：FeaturedVodRow为0；EmptyHome为dp80；普通行为max(recycler.getPaddingTop(),dp16)，直接传setWindowAlignmentOffset(inset)，没有二次dp转换。无Hero普通行保留80dp、有Hero后续普通行保留16dp，Hero与空态原规格不变。

HomeActivity最终SHA256：`f3be4d0e2a108ca3bfbbdfb73d960c2ec62ea5c9e15d8439cfa0ef10c7dfcfe1`。`git diff --check`通过。结论维持STATIC PASS；R2b尚未云端同步，不能把原R2包或原运行证据写为此修复已测，无HeroDown/Back与有Hero连续Down截图仍待运行。原始R2包保持不可变，以R2b新包及最终SHA归档。
