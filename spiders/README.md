# Python 内容源

## 红果短剧

[`hongguo.py`](hongguo.py) 提供分类、排行榜、搜索、详情及分集播放。此版本修复了 deferred JSON 解析、分页、私有缓存启动和 HTTP Range 处理，并移除了没有独立上游地址的清晰度线路。

使用支持 Python Spider 的 TV 客户端，配置为 `type: 3`，`api` 指向该文件。通过 jsdmirror 使用 GitHub 文件时，URL 格式为 `https://cdn.jsdmirror.com/gh/wobuhui666/TV@提交SHA/spiders/hongguo.py`；正式配置固定完整提交 SHA。

源提供单页搜索建议，未提供筛选组；超长搜索词可能触发上游 HTTP 500。实测版本的 SHA-256 为 `72c2a119e65453335f9ed9f118c3fd23139ff73d5a102ad51f6d42b5e3bd2fcf`。
