# 无配置首页与播放页主题重建修复（待回归）

仅本地保存三个 leanback 生产文件，未提交/推送/构建/操作模拟器。请监控协调 QA 做精确文件同步及必要增量验证；既有设置焦点修复保持不变。

## 修复

1. HomeActivity：新增统一 updateHomeContentInsets(hasFeatured)，有主视觉时 paddingTop 与 windowAlignmentOffset 同为0，无主视觉时同为80dp。初始化、setFeatured、removeFeatured 三条路径都使用它，避免选中空状态时 Leanback 将标题滚到顶栏之下。保留内部空状态布局、Hero锚定与已有焦点generation守卫。
2. LiveActivity / VideoActivity：覆盖 onThemeChanged，不走 BaseActivity 默认 recreate。BaseActivity.onThemeEvent 已刷新 JetStreamThemeController，其 Compose snapshot version 会让仍存活的 JetStreamTheme 原位重组。方法不触及播放器、服务连接、播放请求、LiveData、频道或焦点，因此主题改变不再触发后台直播页重新创建与未连接服务竞态，并保留点播 PlayerView 和进度。

未修改通用服务连接状态机，也未引入判空后丢事件的处理；此修复消除主题改变触发的不必要重建。系统自身 Activity 重建的连接竞态不作为本修复已验证范围。

## 核验

本地 git diff --check 通过，仅静态结论。待独立复核和云端构建/运行：无配置冷启/选择源返回、历史存在但推荐空、空推荐↔Hero刷新、普通首页焦点；完整 Home→Search→详情→Live→设置主子操作→多次换色，返回直播频道与点播进度/焦点保持，不出现 CrashActivity。

## 同步边界与修前/后 SHA-256

修前SHA来自上轮云端 final-verification.json，修后SHA为此刻本地文件。仅同步以下三个文件；若云端旧SHA不同先核对，勿覆盖未知改动。此前 JetStreamSettingView.kt 的修复SHA仍为 dfdc063d7532e95d8f57e943161397806ff1e67587a0f089cc78ad66bfb4a514。

[
  {
    "path": "app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java",
    "before_sha256": "235040044cbc2a995b44ca9978163d5e96cfd425175e591c73249825f9e5f6b6",
    "after_sha256": "1b231a4f7fe422e75fdbb20e584cff16ece7ccdbe94b85eca8878e5cd5d7d8cf"
  },
  {
    "path": "app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java",
    "before_sha256": "bfc2701578935e3bf9a21444299f6ccb2b3db1af78b13996c2bbd9d83c2f4e6f",
    "after_sha256": "da2d805521fb38f5894d607bdbfa8f1a76138522f422bcd7b086caceb3100acf"
  },
  {
    "path": "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
    "before_sha256": "32d85b56da5763ee7c689df79c1b5adcc48157ece5d360f19f68116445175e0a",
    "after_sha256": "c047471be61bae3a62322e48d05878b961e39b60e3874da0169b536e9ae30f0c"
  }
]

最终 qa-recovery.complete.json 必须覆盖这轮新源码、必要构建与上述专项回归；不能沿用 e6275e… 生产APK结果声明本轮通过。
