# Cloudflare 优选域名设置接入

> 研究快照（2026-10-06）：保留分析过程，不作为完整构建或设备验收结论。该功能在归档时仍处于其他会话的开发中。

- TV：应用设置 DoH / ECH 附近添加一行，默认显示“关闭”。
- mobile：在相同位置添加原生设置行，显示域名，单行省略。
- 使用 MaterialAlertDialogBuilder + TextInputLayout/TextInputEditText。纯域名输入，留空关闭；错误提交保留对话框并显示错误，不修改旧设置。
- 对话框文案说明：仅识别为 Cloudflare 的 HTTPS 网站使用优选地址；请求仍保留原站域名，失败自动回退原站地址；修改仅对新连接生效。具体回退表述以核心实现为准。
- API 契约待确认；域名验证共享核心逻辑，不在 UI 重复实现。
- setApiUrl 仅 trim，自动关闭且没有校验，不直接复用。
- 所有新增文案补齐默认英文、简体中文、繁体中文。

## 已完成实现

- app/src/main/java/com/fongmi/android/tv/ui/dialog/CloudflarePreferredDialog.java：共用Material字段，提交调用CloudflarePreferredSettings.setDomain；IllegalArgumentException时保留对话框和原配置。
- TV和mobile设置均显示当前域名，空值显示关闭，独立ECH开关。
- 三语文案齐全，不执行DNS输入探测，不接受URL/端口/IP（共享核心校验）。
- XML解析、git diff --check通过；完整编译由root安排CI。
- 实测建议：进入应用设置新增行，提交https://example.com应保持对话框并显示错误；提交合法优选域名后行值更新；重新打开清空保存应显示关闭。过程中关注遥控器上下移动和Done提交，确认无主线程网络异常。
