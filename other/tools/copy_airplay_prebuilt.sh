#!/bin/bash
set -euo pipefail
SRC=/data/home/zyq/IdeaProjects/github/FongMi/TV-fongmi-port/airplay
DST=/data/home/zyq/IdeaProjects/github/FongMi/TV/airplay
mkdir -p "$DST/src/main/jniLibs/arm64-v8a"
cp -a "$SRC/build/intermediates/stripped_native_libs/arm64_v8aRelease/stripArm64_v8aReleaseDebugSymbols/out/lib/arm64-v8a/." \
  "$DST/src/main/jniLibs/arm64-v8a/"
ls -la "$DST/src/main/jniLibs/arm64-v8a/"
# seed cmake/openssl download cache if present
if [ -d "$SRC/.cxx" ]; then
  mkdir -p "$DST/.cxx"
  cp -a "$SRC/.cxx/." "$DST/.cxx/" || true
fi
# also copy openssl build tree stamps so ExternalProject can resume
if [ -d "$SRC/build/intermediates/cxx" ]; then
  mkdir -p "$DST/build/intermediates"
  # only copy if dest cxx missing or incomplete
  if [ ! -f "$DST/build/intermediates/cxx/RelWithDebInfo/6q3r2j4f/obj/arm64-v8a/libairplay_native.so" ]; then
    mkdir -p "$DST/build/intermediates/cxx"
    cp -a "$SRC/build/intermediates/cxx/." "$DST/build/intermediates/cxx/" || true
  fi
fi
echo COPY_OK
find "$DST/build/intermediates/cxx" -name 'libairplay_native.so' 2>/dev/null | head
