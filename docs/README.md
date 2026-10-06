# 文档导航

这里汇总使用说明、实现约束、验证报告和历史记录。实测结论以各报告标注的日期、提交、设备与限制为准；历史交付记录不等同于当前版本全部通过。

## 使用与配置

| 主题 | 文档 |
| --- | --- |
| 配置字段与示例 | [CONFIG](CONFIG.md) |
| 直播与节目单 | [LIVE](LIVE.md) |
| 本地服务与接口 | [LOCAL](LOCAL.md) |
| 爬虫接口 | [SPIDER](SPIDER.md) · [新旧接口兼容](spider-api-compatibility.md) |
| TV 界面 | [UI 改版与验收](ui-redesign/README.md) |
| 一起看 | [Syncplay 服务端、房间、TLS 与同步行为](syncplay.md) |
| 多设备同步 | [WebDAV 收藏、观看进度与冲突处理](webdav-sync.md) |
| 本地构建环境 | [工具链与构建说明](../LOCAL_BUILD_ENV.md) |
| 宣传素材 | [视频、封面与来源](media/README.md) |

## 实现与验证

| 主题 | 从这里开始 |
| --- | --- |
| 播放器、字幕与预加载 | [内核同步说明](player-core-sync.md) |
| ECH 与 DoH | [当前行为和验证边界](ech-validation.md) |
| 源、播放器及设备回归 | [验证报告目录](testing/README.md) · [测试运行方法](testing/source-validation.md) |
| TMDB 透明 Logo 轮廓 | [实现、预览和安装验证](ui-redesign/tmdb-logo/README.md) |
| Girigiri 加载优化 | [优化前后同设备测量](testing/reports/2026-10-06-girigiri-loading/README.md) |
| 两个动漫源的发布与 CDN | [发布和 WEX 更新记录](testing/reports/2026-10-06-ciallo-publishing/README.md) |

## 研究与历史

- [研究目录](research/README.md)：Cloudflare 优选路由设计、触屏静态审计；与已完成的设备验证分开记录。
- [构建耗时核对](process/build-time-audit-20261006.md)：对应指定旧对话快照，不是持续更新的性能指标。
- [历史记录与来源清单](archive/local-notes-20261006/README.md)：旧 UI 方案、接续记录、调查与重复文档的去向。
- [完整备份分支](https://github.com/wobuhui666/TV/tree/docs/local-notes-backup-20261006)：保留早先未整理快照，供追溯原路径。

本次归档覆盖已有备份、五个本机导出实测目录的更新，以及 `/data` 下五个 TV 相关目录。仅收录文档、公开验证摘要和经过查看的说明图片；APK、私有配置、凭据及原始私有日志不放入文档目录。来源、读取时间、脱敏记录、校验值和未收录链接见 [IMPORTS.json](archive/local-notes-20261006/IMPORTS.json)。

## 维护说明

当前仓库的 `.gitignore` 含 `/docs`：已经跟踪的文档可以正常更新，新文件可能不会出现在普通 `git status` 中。新增公开文档应先检查内容，再用 `git add -f -- docs/明确的文件路径` 逐项纳入；不要把设备配置或整个临时证据目录批量强制提交。本次保留原忽略规则，已显式纳入清单中的文件。
