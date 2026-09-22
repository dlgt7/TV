#!/bin/bash
set -euo pipefail
JNI=/data/home/zyq/IdeaProjects/github/FongMi/TV/airplay/src/main/jniLibs/arm64-v8a
# media3compat already provides libc++_shared.so; duplicate breaks mergeNativeLibs
rm -f "$JNI/libc++_shared.so"
ls -la "$JNI"
echo CLEANED
