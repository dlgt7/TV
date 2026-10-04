#!/usr/bin/env python3
"""Rebuild only the extended JNI bridge against the bundled FFmpeg 9 runtime."""

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
FFMPEG = "03d9533176e98bb9fbf569c1f34968e73e948dd9"
MPV = "cca559b41ceb0bb7731cf6ef2e1f33276cd30c42"
ABIS = {"arm64-v8a": ("aarch64", "aarch64-linux-android"),
        "armeabi-v7a": ("arm", "armv7a-linux-androideabi")}


def download(url, destination, expected):
    if not destination.exists():
        urllib.request.urlretrieve(url, destination)
    actual = hashlib.sha256(destination.read_bytes()).hexdigest()
    if actual != expected:
        raise RuntimeError(f"SHA-256 mismatch for {destination}: {actual}")


def run(*command, cwd=None):
    subprocess.run([str(part) for part in command], cwd=cwd, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", required=True, type=Path, help="Android NDK r29 directory")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "../../mpv/build/native-bridge")
    args = parser.parse_args()
    ndk = args.ndk.resolve()
    if "Pkg.Revision = 29.0.14206865" not in (ndk / "source.properties").read_text():
        raise RuntimeError("Use NDK r29 (29.0.14206865), matching the FFmpeg 9 bundle")
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    toolchain = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    archive = work / "ffmpeg.tar.gz"
    download(f"https://codeload.github.com/FongMi/FFmpeg/tar.gz/{FFMPEG}", archive,
             "ba7070db2f8a0590e3bbad428c8ffbec33f80b0a430bcad79c2cfb756e84ff8b")
    source = work / "ffmpeg"
    source.mkdir(exist_ok=True)
    run("tar", "-xzf", archive, "--strip-components=1", "-C", source)
    client = work / "client.h"
    download(f"https://raw.githubusercontent.com/FongMi/mpv/{MPV}/include/mpv/client.h", client,
             "1acf99ee77c8c2a6f1d1993bd81bbc8a91d27fb5924e80171670e6139a4bd353")
    runtime = work / "runtime"
    with zipfile.ZipFile(ROOT / "mpv-android-ffmpeg9-native.zip") as bundle:
        bundle.extractall(runtime)

    for abi, (arch, triple) in ABIS.items():
        build = work / f"headers-{abi}"
        build.mkdir(exist_ok=True)
        prefix = work / "headers" / abi
        # Configure the public headers for the target ABI; no FFmpeg code is built.
        run(source / "configure", f"--prefix={prefix}", "--enable-cross-compile",
            "--target-os=android", f"--arch={arch}",
            f"--cc={toolchain / (triple + '24-clang')}",
            f"--cxx={toolchain / (triple + '24-clang++')}",
            f"--ar={toolchain / 'llvm-ar'}", f"--ranlib={toolchain / 'llvm-ranlib'}",
            f"--strip={toolchain / 'llvm-strip'}", f"--nm={toolchain / 'llvm-nm'}",
            "--disable-everything", "--disable-autodetect", "--disable-programs",
            "--disable-doc", "--disable-x86asm", cwd=build)
        run("make", "install-headers", cwd=build)
        (prefix / "include/mpv").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(client, prefix / "include/mpv/client.h")

    run(ndk / "ndk-build", f"-j{min(os.cpu_count() or 2, 8)}", "NDK_PROJECT_PATH=null",
        f"APP_BUILD_SCRIPT={ROOT / 'bridge/Android.mk'}",
        f"NDK_APPLICATION_MK={ROOT / 'bridge/Application.mk'}",
        f"NDK_OUT={work / 'obj'}", f"NDK_LIBS_OUT={work / 'libs'}",
        f"MPV_RUNTIME={runtime}", f"MPV_HEADERS={work / 'headers'}")
    output = ROOT / "mpv-android-ffmpeg9-bridge.zip"
    candidate = work / "mpv-android-ffmpeg9-bridge.zip"
    with zipfile.ZipFile(candidate, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        for abi in ABIS:
            entry = zipfile.ZipInfo(f"{abi}/libplayer.so", (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            bundle.writestr(entry, (work / "libs" / abi / "libplayer.so").read_bytes())
    run("python3", ROOT / "verify_bridge.py", "--readelf", toolchain / "llvm-readelf",
        "--bridge", candidate)
    shutil.copyfile(candidate, output)
    print(f"Built {output}\nSHA-256: {hashlib.sha256(output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
