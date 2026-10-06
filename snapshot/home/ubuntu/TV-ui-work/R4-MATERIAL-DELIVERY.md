# Material 3 示范交付 — R4B

已完成用户授权的设置页与播放侧边面板示范，并更新到三星 SM-F900F API33 的 TV R3（com.fongmi.android.tv.preview）。原应用com.fongmi.android.tv及原配置保留。

APK SHA256: e4a61c7ff407d95cd4b44f6bb4cfd828ac56f8d0edc030fe917205dedf2a69af
源码提交: 0390d72dd9c9eed0ed31da9ca08de1b5cb877264，ui/apple-tv-redesign，43文件，工作区干净。
构建源码与本地43文件SHA清单逐一一致；设备完整APK哈希与云机产物一致。

## 实现
局部Material3主题恢复用户所选accent。Compose使用TV Material3 Button，XML字幕/音轨/弹幕等操作使用MaterialButton。按钮提供filled、tonal、outlined、text层级，保留D-pad、长按、选中语义；图标间距8dp，按钮最小高度48dp，操作间距12dp。侧边面板28dp圆角，保持统一右侧边界。首页/海报继续使用既有样式，不将这次示范强行推广到全App。

## 验证与证据
- R4B TV arm64 original和preview构建通过；223单测零失败；lint零错误、313警告。
- API24模拟器实际按键与截图覆盖来源/播放/应用设置、音轨、字幕四按钮及drawer返回停留超过7秒。
- 三星已安装、哈希验证、冷启动及按键进入设置。证据 r3-evidence/samsung-r4b。
- R4A直看图片 r4a-settings-source.png、r4a-settings-playback.png、r4a-subtitle-settings.png；R4B仅补图标描述，最终截图 r4b-review/r3-material/r4b-settings-source.png 和 r4b-settings-bottom.png。
- 视觉评审 R4-MATERIAL-INDEPENDENT-REVIEW.md；评审者参与Compose设置实现，明确该部分为视觉自审，Native部分为交叉评审。

## 边界
这是已安装的Material3示范，不表示全App所有页面已经迁移。模拟器是UI wrapper，不代表ARM/Python播放能力验证。字幕截图处于测试片结束状态，只证明布局和焦点；实际字幕渲染调整语义沿用原回调。源插件错误隔离保留，外部源本身的失效并未修复。
当前凭据对上游仓库push=false，PR62未更新。已备份本地提交补丁 r4b-ui-delivery.patch 与待发布说明 R4-PR-BODY.md；未创建替代PR、未合并或强推。
