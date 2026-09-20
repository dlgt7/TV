#!/usr/bin/env bash
# Runs a Gradle build for the FongMi/TV fork on the build server.
#
# Usage: build-tv.sh <project-dir> [gradle tasks...]
#
# Kept as a script on purpose: passing JAVA_HOME/ANDROID_HOME inline over `ssh`
# is fragile because the local shell expands $PATH before the remote sees it.
set -euo pipefail

PROJECT_DIR="${1:-/data/home/zyq/IdeaProjects/github/FongMi/TV-review}"
shift || true

export JAVA_HOME=/data/home/zyq/.local/share/mise/installs/java/21.0.2
export ANDROID_HOME=/data/home/zyq/Android/Sdk
# Chaquopy resolves buildPython from PATH; python3.10 lives in ~/.local/bin.
export PATH="/data/home/zyq/.local/bin:${JAVA_HOME}/bin:/usr/local/bin:/usr/bin:/bin"

if [ "$#" -eq 0 ]; then
    set -- :app:assembleMobileArm64_v8aRelease :app:assembleLeanbackArm64_v8aRelease
fi

cd "${PROJECT_DIR}"
echo "[build-tv] dir=${PROJECT_DIR} tasks=$*"
exec ./gradlew "$@" --console=plain
