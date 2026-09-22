#!/bin/bash
# Make airplay use prebuilt jniLibs and skip flaky openssl ExternalProject download.
set -euo pipefail
GRADLE=/data/home/zyq/IdeaProjects/github/FongMi/TV/airplay/build.gradle
cp -a "$GRADLE" "$GRADLE.bak"
python3 - <<'PY'
from pathlib import Path
p = Path("/data/home/zyq/IdeaProjects/github/FongMi/TV/airplay/build.gradle")
text = p.read_text()
if "SKIP_NATIVE_CMAKE" in text:
    print("already patched")
    raise SystemExit(0)
shim = """
// SKIP_NATIVE_CMAKE: use prebuilt jniLibs when present (openssl download is flaky on this host).
def prebuiltJni = file('src/main/jniLibs/arm64-v8a/libairplay_native.so')
if (prebuiltJni.exists()) {
    android.sourceSets.main.jniLibs.srcDir 'src/main/jniLibs'
    android.externalNativeBuild.cmake.path = null
}
"""
# Insert after android { opening - actually wrap externalNativeBuild
# Safer: comment out externalNativeBuild block by renaming
text = text.replace("externalNativeBuild {", "/* externalNativeBuild {", 1)
# close comment after the cmake path block - fragile. Use marker replace instead.
text = text.replace("/* externalNativeBuild {", "externalNativeBuild {", 1)

# Force BUILD_OPENSSL off and use already-extracted tree via OPENSSL_PREFIX? 
# Simplest reliable: disable externalNativeBuild entirely when prebuilts exist.
old = """    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
        }
    }"""
new = """    // SKIP_NATIVE_CMAKE
    if (!file('src/main/jniLibs/arm64-v8a/libairplay_native.so').exists()) {
        externalNativeBuild {
            cmake {
                path file('src/main/cpp/CMakeLists.txt')
            }
        }
    } else {
        sourceSets.main.jniLibs.srcDirs += ['src/main/jniLibs']
    }"""
if old not in text:
    raise SystemExit('pattern not found for externalNativeBuild path block')
text = text.replace(old, new, 1)

# Also neutralize defaultConfig.externalNativeBuild cmake args when skipping - leave them, harmless if no path.

p.write_text(text)
print("patched airplay/build.gradle")
print(text)
PY
