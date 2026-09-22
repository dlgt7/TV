#!/bin/bash
set -euo pipefail
export PATH=/data/home/zyq/.local/bin:/usr/lib/jvm/java-25-openjdk-amd64/bin:/usr/local/bin:/usr/bin:/bin
PY=/data/home/zyq/IdeaProjects/github/FongMi/TV/chaquo/build/python/env/arm64_v8aRelease/bin/python
echo "PY=$PY"
ls -la "$PY"
"$PY" -m pip install --index-url https://pypi.tuna.tsinghua.edu.cn/simple \
  lxml ujson pyquery requests cachetools pycryptodome beautifulsoup4
echo PIP_OK
"$PY" -c "import lxml,ujson,pyquery,requests,cachetools,Crypto,bs4; print('imports ok')"
