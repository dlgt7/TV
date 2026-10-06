# R8 UI 交付说明（设置页/播放面板回退 + 片库残留修复）

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

日期：2026-10-04
分支：ui/apple-tv-redesign（本地工作树 /home/ubuntu/TV-ui-redesign，基于 codex 提交 0390d72dd）

## 本次交付
1. 设置页改回去 —— JetStreamSettingView.kt 整体回退到 Material 试点前版本
2. 播放侧边面板配色改回去 —— 去掉试点强调色方案（materialColorScheme 已删除），
   播放侧板背景回到 jetstream_surface，按钮回到中性 controlContainer/controlText/controlOutline
3. 搜索结果页删除「修改搜索」按钮 —— activity_collect.xml 移除按钮，CollectActivity 去掉专用路由
4. 片库切换分类左侧残留上一分类海报 —— 根因：分页器被左右 48dp margin 内缩（1200px < 屏幕 1334px），
   ViewPager 相邻页永远停在一页宽处，滚动后上一页最右侧 67px 落在屏幕左缘留白里。
   修复：分页器改满宽（activity_vod.xml）+ 页面内容承载原 48dp 内缩（fragment_folder.xml），
   几何上保证邻页完全移出屏幕（版面像素零变化，内容区 A/B 差异 0.00/255）

## 验证
- 云端门禁（Codespace tv-r3-build-q7jwxvq6r4j5f9xrj）：assembleLeanbackArm64_v8aDebug +
  testLeanbackArm64_v8aDebugUnitTest + lintLeanbackArm64_v8aDebug 全绿；预览包构建同样通过
- 真机：增量补丁安装，设备端重建 sha 与构建产物一致（APK_DELTA_HASH_VERIFIED -> Success）
- 真机截图与量化数据：见 r8-evidence/r8-verification-summary.txt
- 关键证据：r8-evidence/r8-m3-panel.png（播放侧板中性配色）、r6-evidence/r6-95-settings.png（设置页）、
  r6-evidence/r6-96-results.png（结果页无「修改搜索」）、r8-evidence/r8-tab0..4.png（片库五个分类左缘干净）、
  r8-evidence/r8-before-band.png / r8-after-band.png（修复前后左缘对比）

## 产物
- r8-evidence/r8-preview-arm64.apk（sha256 2a4694459a7ed3831e239096ce8c5cc8399f081675a7a6bf163cdafa31cb293c，
  并行包 com.fongmi.android.tv.preview）
- r8-evidence/r8-preview.delta + r8-preview-install.sh（设备端增量重建安装脚本，脚本内校验基线与目标 sha）
- 云端产物：/workspaces/TV-ui-results/visual-rework/artifacts/r8-*.apk（含生产包 6b9f4bbc…）

## 安装 / 回滚
- 安装：`adb push r8-preview.delta /data/local/tmp/` 后 `adb shell sh -s < r8-preview-install.sh`
- 回滚：r6-evidence/r6-preview-arm64.apk、r5-evidence/r5b-preview-arm64.apk 可直接 `pm install -r`

## 备注
- 设备上 com.fongmi.android.tv（原应用）与用户配置未被改动；安装的是并行包 .preview
- 云端 Codespace 已自动关机（30 分钟空闲），产物保留在 /workspaces/TV-ui-results/visual-rework/artifacts
- 来源插件 GoProxy 原生库加载失败为既有问题（logcat crash buffer），与本次改动无关