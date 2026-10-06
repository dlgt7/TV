# “继续 TV UI 设置页与片库 tab 筛选”构建耗时核对

> 记录范围：以下结论对应报告所列日期、提交和设备；后续版本应重新核对。公开证据见本目录；原文提到的 APK、私有文件及临时脚本不随本次归档。来源见 [导入清单](../archive/local-notes-20261006/IMPORTS.json)。

对话 ID：`01a10625-d3cf-7412-8ed9-474b2e7dadad`。统计记录始于 2026-10-04 09:15:36 UTC，本次读取到的最后一条命令时间为 2026-10-06 08:14:17 UTC。

## 结果

| 口径 | 耗时 |
| --- | --- |
| Codespace 构建命令，46 次 | 约 1小时18分7秒 |
| GitHub Actions 构建与随附检查步骤，去除并行重叠 | 2小时1分56秒 |
| 以上两项合计，去除并行重叠 | 约 3小时20分3秒 |
| 连同 CI 等待运行器、环境准备、上传等全部作业阶段 | 约 4小时41分52秒 |
| 其中：未分配运行器、没有执行任何步骤的 CI 时间 | 1小时0分9秒 |
| 其中：失败的 CI 构建／检查步骤 | 0小时12分27秒 |

Codespace 另外有三次失败的构建命令，合计约 6 分 19 秒；与 CI 失败构建合计约 18 分 46 秒。失败可能来自编译或检查，不能一概认定为无价值。等待运行器期间，对话也做了本地验证，所以不能把所有构建运行时间认定为用户额外等待时间。

## 统计方法与限制

- 读取对话数据库中的实际命令、开始／结束时间和输出；核对 GitHub Actions 30 个工作流运行、48 个作业（含重新运行）。
- 本地部分主要为 Codespace Gradle 构建。优先匹配实际启动命令，排除只读取文件、查看日志、安装 APK、下载产物和设备测试的命令。
- Codespace 通常采用命令执行时间，含少量 SSH、同步开销；R10 异步构建按 Gradle 日志的 6 分 38 秒计，R11 按日志的 5 分钟计。因此整体使用近似值。
- CI 构建步骤包括 Gradle 编译、单元测试、lint、打包；并非编译器 CPU 时间。其他 CI 阶段只计入较宽口径。
- 将时间区间取并集，避免 preview、sourceprobe、release 并行导致重复计算；反复读取同一份构建日志不增加构建次数。
- 排除前序 Claude 对话留下的 8 分 03 秒构建，以及文档引用的其他历史构建。零散 javac／D8 编译未逐项计入，故这是已核对主要构建记录的近似统计。
- 不把用户离线、思考、写代码、设备操作时间计为编译。由于构建期间可以同时做别的工作，不能从这些数据精确推导“若不编译可节省多久”。

## 明确的运行器空等

以下作业 `runner_id=0`、运行器名称为空、没有任何步骤，最终取消。并行作业合并后共 1 小时 00 分 09 秒。

| 工作流 | 尝试 | 起止时间（UTC） | 耗时 |
| --- | --- | --- | --- |
| [37364398536](https://github.com/wobuhui666/TV/actions/runs/37364398536) | 1 | 10-05 19:35:31—19:50:34 | 0小时15分3秒 |
| [37364398536](https://github.com/wobuhui666/TV/actions/runs/37364398536) | 2 | 10-05 19:52:57—20:07:59 | 0小时15分2秒 |
| [37367866351](https://github.com/wobuhui666/TV/actions/runs/37367866351) | 1 | 10-05 20:08:20—20:23:22 | 0小时15分2秒 |
| [37367866351](https://github.com/wobuhui666/TV/actions/runs/37367866351) | 2 | 10-05 20:31:04—20:46:06 | 0小时15分2秒 |

## CI 明细

下表每行已合并该运行内部的并行步骤；不同工作流之间也可能重叠，因此不能直接将各行相加得到实际经过时间。

| 运行 | 结果 | 构建／检查步骤 | 全部作业阶段 |
| --- | --- | --- | --- |
| [37267111022](https://github.com/wobuhui666/TV/actions/runs/37267111022) | success | 0小时9分18秒 | 0小时10分31秒 |
| [37303641302](https://github.com/wobuhui666/TV/actions/runs/37303641302) | success | 0小时4分57秒 | 0小时6分5秒 |
| [37305860339](https://github.com/wobuhui666/TV/actions/runs/37305860339) | success | 0小时3分59秒 | 0小时5分21秒 |
| [37305923204](https://github.com/wobuhui666/TV/actions/runs/37305923204) | success | 0小时3分46秒 | 0小时4分51秒 |
| [37306914672](https://github.com/wobuhui666/TV/actions/runs/37306914672) | success | 0小时3分38秒 | 0小时4分58秒 |
| [37306915307](https://github.com/wobuhui666/TV/actions/runs/37306915307) | success | 0小时5分5秒 | 0小时6分16秒 |
| [37311309464](https://github.com/wobuhui666/TV/actions/runs/37311309464) | success | 0小时3分59秒 | 0小时5分17秒 |
| [37311310435](https://github.com/wobuhui666/TV/actions/runs/37311310435) | success | 0小时3分19秒 | 0小时4分47秒 |
| [37323072749](https://github.com/wobuhui666/TV/actions/runs/37323072749) | success | 0小时6分44秒 | 0小时7分45秒 |
| [37323075194](https://github.com/wobuhui666/TV/actions/runs/37323075194) | success | 0小时7分13秒 | 0小时8分20秒 |
| [37331335223](https://github.com/wobuhui666/TV/actions/runs/37331335223) | success | 0小时4分22秒 | 0小时5分33秒 |
| [37332497623](https://github.com/wobuhui666/TV/actions/runs/37332497623) | success | 0小时5分26秒 | 0小时6分51秒 |
| [37358396848](https://github.com/wobuhui666/TV/actions/runs/37358396848) | failure | 0小时4分3秒 | 0小时4分48秒 |
| [37359756519](https://github.com/wobuhui666/TV/actions/runs/37359756519) | success | 0小时8分32秒 | 0小时9分39秒 |
| [37363183425](https://github.com/wobuhui666/TV/actions/runs/37363183425) | success | 0小时2分53秒 | 0小时3分58秒 |
| [37364398536](https://github.com/wobuhui666/TV/actions/runs/37364398536) | failure | 0小时3分1秒 | 0小时30分5秒 |
| [37367866351](https://github.com/wobuhui666/TV/actions/runs/37367866351) | failure | 0小时0分0秒 | 0小时30分4秒 |
| [37395642484](https://github.com/wobuhui666/TV/actions/runs/37395642484) | success | 0小时7分34秒 | 0小时8分31秒 |
| [37398331981](https://github.com/wobuhui666/TV/actions/runs/37398331981) | success | 0小时2分56秒 | 0小时3分58秒 |
| [37399371147](https://github.com/wobuhui666/TV/actions/runs/37399371147) | success | 0小时9分15秒 | 0小时10分26秒 |
| [37404706669](https://github.com/wobuhui666/TV/actions/runs/37404706669) | success | 0小时4分26秒 | 0小时5分25秒 |
| [37406032802](https://github.com/wobuhui666/TV/actions/runs/37406032802) | success | 0小时4分17秒 | 0小时5分36秒 |
| [37409022443](https://github.com/wobuhui666/TV/actions/runs/37409022443) | success | 0小时5分1秒 | 0小时6分1秒 |
| [37412116582](https://github.com/wobuhui666/TV/actions/runs/37412116582) | success | 0小时4分36秒 | 0小时5分48秒 |
| [37426241188](https://github.com/wobuhui666/TV/actions/runs/37426241188) | failure | 0小时7分1秒 | 0小时7分48秒 |
| [37427648357](https://github.com/wobuhui666/TV/actions/runs/37427648357) | failure | 0小时1分23秒 | 0小时2分2秒 |
| [37428099682](https://github.com/wobuhui666/TV/actions/runs/37428099682) | success | 0小时2分6秒 | 0小时2分45秒 |
| [37428214817](https://github.com/wobuhui666/TV/actions/runs/37428214817) | success | 0小时7分10秒 | 0小时8分2秒 |
| [37431072302](https://github.com/wobuhui666/TV/actions/runs/37431072302) | success | 0小时1分0秒 | 0小时2分10秒 |
| [37433049153](https://github.com/wobuhui666/TV/actions/runs/37433049153) | success | 0小时2分53秒 | 0小时3分55秒 |

运行 37267111022 的 API 已返回 404，其数据来自该对话当时保存的完整 GitHub 作业响应。
