# 两个新源发布与 WEX 更新

> 记录范围：以下结论对应报告所列日期、提交和设备；后续版本应重新核对。公开证据见本目录；原文提到的 APK、私有文件及临时脚本不随本次归档。来源见 [导入清单](../../../archive/local-notes-20261006/IMPORTS.json)。

- 源码与已验证 JAR 已推送到 [CatVodSpider 分支](https://github.com/wobuhui666/CatVodSpider/tree/feat/ciallo-anime-sources)，提交 `0cfe42a0cb1a42081da51a682a5110afa4ed23ad`。仓库写权限邀请已接受，先前的 403 阻塞已解除。[仓库构建通过](https://github.com/wobuhui666/CatVodSpider/actions/runs/37466307308)。
- 远端 wex.json 已从 76 个源增加至 78 个，加入 `ciallo_girigiri` / `csp_Girigiri` 和 `ciallo_sorani` / `csp_Sorani`。两个新源使用单独的 jar 字段；原有 76 项、全局 spider 和其他顶层字段均保持原样。
- 使用 ETag / If-Match 条件更新，WebDAV PUT 返回 204，随后 GET 返回 200，回读 JSON 与候选完全一致。原配置及更新后配置保存在本目录 private 子目录，权限限制为当前用户。
- CDN 选择 `gh-proxy.com`：主机实下 273ms，三星实下 0.755 秒；两端均 HTTP 200，150485 字节，SHA-256 完全一致。`ghfast.top` 同样通过，三星耗时 2.863 秒。
- `cdn.jsdmirror.com`、`cdn.jsdmirror.cn`、`gcore.jsdelivr.net`、`fastly.jsdelivr.net`、`cdn.jsdelivr.net` 对该 JAR 均为 403；JSON 200 并不能证明 JAR 可用。cdnjs/jQuery/staticfile/bootcdn/unpkg 不属于通用 GitHub 仓库文件镜像，不能直接替换域名使用。
- JAR SHA-256：`dc0e5fcd5f95308a0a169fa31383e75bc213c38b5d9378138e13dacdd3f20ab4`。WEX 的两个站点使用固定提交 URL 及 `;md5;` 格式。
- 本轮没有修改 Java 实现，继续使用之前已实测的新旧 App 兼容 JAR。本轮验证发布地址、CDN 下载和远端配置写回；Sorani 站点自身的设备 TCP 超时属于之前记录的独立问题，JAR 下载成功不代表该站播放网络已恢复。

刷新 App 内 WEX 配置后可看到 `girigiri 爱动漫` 与 `青空 Sorani`。

完整私有 WEX 配置没有上传到 GitHub 或任何 CDN。公开的 ciallo-cdn.json 只包含两个新源。
