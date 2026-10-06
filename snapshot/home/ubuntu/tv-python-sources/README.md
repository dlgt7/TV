# TV Python 内容源

独立存放供支持 Python Spider 的 TV 客户端使用的内容源。

## 红果短剧

文件：[hongguo.py](hongguo.py)。提供分类、排行榜、搜索、详情和分集播放，已修复 deferred JSON、分页、私有缓存启动和 HTTP Range 处理。

镜像地址：

```text
https://cdn.jsdmirror.com/gh/zhhshss/tv-python-sources@main/hongguo.py
```

配置示例（实际部署可将 `main` 换为已验证的完整提交 SHA）：

```json
{
  "key": "py_hongguo",
  "name": "红果短剧",
  "type": 3,
  "api": "https://cdn.jsdmirror.com/gh/zhhshss/tv-python-sources@main/hongguo.py",
  "searchable": 1,
  "quickSearch": 1,
  "filterable": 0
}
```

源提供单页搜索建议，未提供筛选组；超长搜索词可能触发上游 HTTP 500。文件校验值见 [SHA256SUMS.txt](SHA256SUMS.txt)。
