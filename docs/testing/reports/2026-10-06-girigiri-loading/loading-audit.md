# Girigiri 加载审计

> 研究快照（2026-10-06）：保留分析过程，不作为完整构建或设备验收结论。

当前进入源需要 7 个顺序请求：6 个分类页面用于完整筛选，随后 App 请求首页列表。主机本轮取得全部 6 类 HTTP 200 后，首页请求在 30 秒读取超时；网络/站点耗时之外，串行请求放大了等待。

|分类|筛选字段（不含排序）|类型数量|
|---|---|---|
|日番|class, area, year, lang, version, state|25|
|美番|class, area, year, lang|15|
|真人番劇|class, area, year, lang|26|
|劇場版|class, area, year, lang|23|
|BD副音軌|class, area, year|23|
|演唱會&周邊活動&其他|class, area, year|4|

只有季度 area 与排序 by 相同；class、year、lang 不同，部分分类没有 version/state。不能用日番筛选复制到全部分类。旧 App 只在 homeContent 回包时绑定筛选，无法由源 JAR 单独实现完整兼容的筛选懒加载。

建议：缓存经过验证码/登录检查的首页和分类文档 60 秒，最多 12 页；默认类别页与显式第 1 页共享缓存键；首个类别页之后，以最多 3 个并发请求取得其余全部筛选，首页可同时预取。缓存读写均 clone 文档，避免解析器调用者修改共享 DOM。

生命周期：保留旧 client() 与现有全局 Call 跟踪；为 home 批次建立独立 Call 集合，异常/中断时只取消该批次。Future.cancel(true) 本身不足以停止同步 OkHttp 的 Socket 读取，要显式 Call.cancel()。finally 关闭线程池，destroy/init 时防止旧响应回填。

未修改生产文件，未把主机时序测量当作三星设备测速。原始筛选和分析见 loading-audit.json。
