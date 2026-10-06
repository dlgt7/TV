# 唯一owner下一步，仅执行这一条

owner仍/root/visual_cloud_qa，模型/ultra保持。不要重读历史。

```sh
python3 /tmp/tv-ui-auth-ku9f2b2i/remote.py /home/ubuntu/TV-ui-work/visual-minimal-recovery-probe.sh > /home/ubuntu/TV-ui-work/visual-minimal-recovery-probe.log 2>&1
```

先执行并回报exit、日志，不再写计划。此脚本只读宿主机date/ps/df/fixture/AVD/包路径，不调用adb，不安装、不构建。SSH若失败原错误已在本地日志，立即回传。若连接长时间未返回，回传实际session/PID。

已知恢复点：00:06 R1 Push Install Success，之后复制数据库至/tmp/tv-ui-fixture/state.db失败（目标路径缺失）。API后续为Shutdown，不能引用旧进程。探测成功后再依据真实状态恢复持久fixture兼容路径，从数据库/取图续，不整段重装。R2E冻结，尚无运行结果；具体构建留到环境恢复之后。
