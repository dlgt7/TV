# Ciallo 动漫源

配置入口为 `json/ciallo.json`，Java 入口 `csp_Girigiri`、`csp_Sorani`。两者使用旧 `Spider.client()` 共享客户端及 JSON 返回格式，不依赖新 `net`、`local`、`Result.page`，可供保留旧接口的新 App 与旧 App 使用。代理、DNS、ECH 等沿用宿主客户端。编译 SDK 补回 `Spider.client()` 的旧方法声明；SDK 为 compileOnly，不打进爬虫 JAR。

| 源 | 功能 |
| --- | --- |
| Girigiri | 首页、6 类分类、站点筛选、真实分类分页、搜索、详情、多线路与分集、HLS/MP4 解析 |
| Sorani | 首页、分类与筛选、列表/搜索真实分页、详情、播放线路、分集取票与镜像媒体地址 |

`ext.host` 可设置站点地址。Sorani 还可用 `ext.apiBase` 指定 API 根路径（默认使用同镜像 `/__upstream__/api.sorani.cc/sorani-cms`）。默认地址已写入配置，不需要填账号。

Girigiri 的公开搜索建议接口不提供服务端分页，源取其声明的完整结果后本地切页；不把重复的 page/pg 响应冒充下一页。媒体请求带浏览器 User-Agent 和 Referer。Sorani 按网站 API 分页，播放时才取临时票据；遇到限流、登录要求、HTTP/JSON 错误会报错，不返回伪成功空列表，也不自动绕过镜像连接原站。

使用仓库标准 `gradlew.bat spiderJar` 生成 `jar/custom_spider.jar` 与 MD5。Windows CI 同样执行完整 R8 与现有 checkJar 检查，产物作为 workflow artifact 提供。JAR 和本配置保持相对目录后可导入。

源的主机验证与真实 App 加载/解码分开记录；主机取到 HLS、密钥和分片不等于设备已经成功解码。最终设备兼容性结果以本轮 TV 验证报告为准。
