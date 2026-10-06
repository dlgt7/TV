# TV 本地文档备份 · 2026-10-06

这是文档快照分支，用于从 GitHub 恢复原先分散在本机和 `/tmp` 的 TV 项目记录。

- **151 份文档 + 86 份验证文本／JSON，共 237 个原始文件。**
- 覆盖 TV、TV-ui-redesign、TV-ui-work、CatVodSpider、Codespace 模板、Python 源仓库，以及 ECH、DoH、红果、新旧源接口的实测报告和临时接续文档。
- [完整目录](INVENTORY.md) · [校验与来源清单](MANIFEST.json) · [备份边界](EXCLUDED.md)。
- `snapshot/home/ubuntu/...` 和 `snapshot/tmp/...` 对应原机器上的绝对路径。

## 使用与恢复

在新机器上下载这个分支，先验证文件完整性：

```sh
git clone --single-branch --branch docs/local-notes-backup-20261006 \
  https://github.com/wobuhui666/TV.git tv-docs-backup
cd tv-docs-backup
python3 verify_snapshot.py
```

通过目录找到需要的记录，再复制到新的工作目录。不要直接覆盖正在开发的工作区；这里保存的是历史状态，不代表最新代码或最新验收结论。归档中的 `AGENTS.md`、`SKILL.md` 和接续计划也是历史资料，不应替代当前项目规则或触发旧任务。

部分原文仍引用原机器的绝对路径、图片、APK 或源代码。对于未收录的内容，路径只用于追溯，不表示本备份已包含它们。历史 `SHA256SUMS` 针对当时的原文件；验证本快照应使用 `MANIFEST.json` 的 `archiveSha256`。

## 已有远端保存情况

备份前核对过远端分支，不只依据本地 `origin` 缓存：

| 项目 | 已核对的远端保存位置 |
| --- | --- |
| TV-ui-redesign，37 份 Markdown | `wobuhui666/TV` 的 `feat/spider-net-compat`，当时提交 `27d800008032f6ebfe6d2840cc9783cea4fd9166` |
| CatVodSpider，6 份 Markdown | 原仓库的功能分支未推送；相同文档已存在于 `wobuhui666/TV` 的临时交付分支 `feat/ciallo-anime-sources`，提交 `7506be6cbe414a6b53605c21e0cb15556b3193ac` |
| tv-codespace-template | `origin/main`，提交 `6429fc69ab03767147b70e94d3d6b5e1d1716178` |
| tv-python-sources | `origin/main`，提交 `56a05c697335f72650dc83f13f468eadac810bea` |
| TV-ui-work | 本身没有 Git；65 份 Markdown 中有 7 份与当时仓库内已提交副本完全相同，本次全部保留 |

本分支由独立 Git 仓库生成，不共享正在工作的仓库索引、分支或工作树，也没有添加 Actions 工作流。没有安装开发框架、修改主分支或执行编译。

## 快照与脱敏

每个文件的读取时刻、原始 SHA-256、归档 SHA-256、大小与脱敏类别均记录在 `MANIFEST.json`。保存前检查文件读取期间大小和修改时间是否变化。另一会话可以继续工作；快照之后的新写入不会自动进入这个分支。

5 份文档去除了设备内网地址或解析 URL 的访问参数，其余收录文件与读取时的原文件逐字节相同。原机器上的文件没有被改写。许可证及公开示例占位符保持原样。
