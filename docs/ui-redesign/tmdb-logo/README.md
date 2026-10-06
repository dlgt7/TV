# TMDB 透明 Logo 轮廓预览与验证

> 记录范围：以下结论对应报告所列日期、提交和设备；后续版本应重新核对。公开证据见本目录；原文提到的 APK、私有文件及临时脚本不随本次归档。来源见 [导入清单](../../archive/local-notes-20261006/IMPORTS.json)。

已在现有详情页标题图片中加入细轮廓与柔影。暗色笔画对应淡灰轮廓，浅色笔画对应深灰轮廓，颜色平滑过渡；不采样或追踪动态背景，不增加请求与每帧处理。正常高对比场景维持轻微轮廓，低对比背景上可辨认字形。透明图之外不生成矩形底板。

- 唯一实际剧集 logo 入口：VideoActivity → JetStreamVodDetailView。首页 Featured / Discover hero 当前使用海报和文字，未新增展示入口。
- 使用 Glide BitmapTransformation，在后台解码阶段处理并缓存；显式禁止硬件 Bitmap；透明边距包含轮廓和完整柔影。
- 无透明外缘的图片跳过；原图不透明像素逐像素保留，不改品牌色。标题图片区域由 48 dp 增至 54 dp，给轮廓上下各 3 dp 的独立透明空间；先按预留区域 fitCenter，再生成轮廓，保留原 48 dp 图形显示高度，不再被新增留白缩小。超宽 Logo 的宽度仍受现有详情栏宽度限制。
- 未改按钮、焦点、网络策略和 TMDB API 查询。仅标题图片区域增加 6 dp，由现有弹性间隔吸收，不改变按钮位置。

## 预览

[四个真实 TMDB Logo 前后对比](logo-before-after.png)

[极低对比场景](logo-low-contrast.png)

预览直接调用生产 Java 像素处理方法生成，但属于离线渲染，不是 Android 设备截图。第一个对照图使用 TMDB 原图；第二个将相同透明形状变为深灰/浅灰测试颜色，以验证与背景颜色完全相同的极端情况。原图地址见 sample-provenance*.json。

## 已验证

- 使用 Android API 37 android.jar 与项目实际 Glide 5.0.7 编译新增 Java 类通过。只出现缺失独立 Kotlin annotation 元数据的警告，没有编译错误。
- 4 个真实 Logo + 2 个极端低对比形状调用生产方法；不透明原像素、透明空图、无透明外缘图片判断、边缘留白，以及缓存 equals/hashCode/density key 检查通过。
- 本机样例单次约 10–76 毫秒，属于主机 Java 数据，不能替代 Android 性能实测。
- git diff --check 通过。

## 构建、发布与安装

完整 Android/Compose 构建已通过：[验证构建 37475548128](https://github.com/wobuhui666/TV/actions/runs/37475548128)。主分支已推送至 `9b451ccbff2dd5273e6a2a7cc12ca273162f3880`；[正式发布 build-37477478120](https://github.com/wobuhui666/TV/releases/tag/build-37477478120) 的 ARM64/ARMv7 构建、资产上传均成功。

对应 CI 预览 APK 已重新签名并装入三星，全部 1834 个非签名 ZIP 项与原 CI APK 一致；安装前后 11 个配置/数据库文件哈希相同。HomeActivity 正常恢复，进程存活，当前进程无 AndroidRuntime fatal；独立源测试包和设备临时安装文件已清理。详见 installation.json / preview-signing.json。

预览包 SHA-256：`2d7034436d2ccc1d70bd32e2843db9b97c14e7681b0e05a6b8cd55844d30ec3d`。上面的效果图仍是生产算法离线渲染，未将其声称为设备详情页截图。新增类只由当前这一处显式禁止硬件位图的 Glide 请求使用。
