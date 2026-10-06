# 新旧爬虫接口兼容

本实现对接 FongMi/CatVodSpider 2026-10-05 发布的 SDK 和开发文档，保留 TV 原有的 Java、QuickJS、Python 入口。参考 SDK 提交为 `3d7a0fa1a6737603231f78fb7cbaea6539d9d6e3`；核对时 FongMi/TV 公开源码仍在 `c616c0aa3613e87529791587a9f71b78c278c991`，不能将本实现称为已公开的上游 TV 实现。

## 接口行为

| 类型 | 新接口 | 旧接口兼容措施 |
| --- | --- | --- |
| Java JAR | 在 `init` 前注入 `net`、`local`，提供分页、结果、加密、Activity 和代理辅助方法 | 保留 `Spider.client()`、`safeDns()` 和所有旧入口；旧 JAR 自带的 Result/Vod/Crypto 等辅助类优先由该 JAR 加载 |
| QuickJS | `net`、按站点存储、分页、代理；数据入口可返回对象或 Promise | 保留旧字符串返回、`req/http/getProxy`、三参数 local，以及代理数组和布尔返回 |
| Python | 原生网络适配器、按站点存储、分页和字典代理参数 | 保留 requests 的 fetch/post 与 Response 行为、缓存方法、显式 `getProxyUrl(True/False)`；源自定义的 net/local 属性保留 |

新网络请求继承 App 的代理、重定向、DNS 和 ECH 客户端。关闭某个源只取消该源拥有的请求、WebSocket、缓存任务和子会话。`local` 与 HTTP 缓存分开，清 HTTP 缓存不会清除站点持久数据。需要逐跳代理选择的重定向继续走 App 的现有实现。

增加宿主辅助类时，普通父优先加载可能遮住旧 JAR 中同名但不同版本的类。`SpiderClassLoader` 仅对本次新增的 Result、Vod、Class、Filter、Sub、Danmaku、Crypto 采用源内优先查找；Spider、Net、Local、OkHttp 和已有宿主类保持父优先，避免类身份冲突。

## 验证入口

- JVM：`:catvod:testDebugUnitTest`、`:quickjs:testDebugUnitTest`。
- Python 协议：`python -m unittest discover -s chaquo/src/test/python -v`。
- Android：`SpiderJavaPythonApiTest`、`QuickJsSpiderApiTest` 检查真实虚拟机、网络与存储桥接。
- 已配置旧 JS：`QuickJsLegacySourcesTest` 读取测试包私有目录内的脚本和校验计划，不把配置凭据写入仓库。
- 同一旧接口源 JAR：`CialloSourceCompatibilityTest` 在隔离 sourceprobe 包内加载 `files/ciallo-source.jar`，参数 `ciallo_source=Girigiri|Sorani`、`ciallo_sha256=<SHA256>`。测试只调用旧 API，可在升级前后两个 App 上运行。播放信息留在私有 `source-validation/playback-cases.json`，可供已有 Exo/MPV 播放测试使用。

协议测试和解析成功不能代替真实 App 加载、原生桥接或视频解码。设备报告应注明 App 提交、JAR 哈希和具体执行的测试，外部站点错误与接口回归分开记录。
