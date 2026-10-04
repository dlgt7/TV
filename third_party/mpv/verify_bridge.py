#!/usr/bin/env python3
"""Check both ARM bridges retain the AAR JNI API and resolve FFmpeg/MPV imports."""

import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
ABIS = ("arm64-v8a", "armeabi-v7a")


def symbols(readelf, path):
    output = subprocess.check_output([str(readelf), "--wide", "--dyn-syms", str(path)], text=True)
    imports, exports = set(), set()
    for line in output.splitlines():
        fields = line.split()
        if len(fields) < 8 or not fields[0].rstrip(":").isdigit():
            continue
        name = fields[7].replace("@@", "@")
        if fields[6] == "UND":
            if fields[4] != "WEAK":
                imports.add(name)
        else:
            exports.add(name)
            if "@@" in fields[7]:
                exports.add(name.split("@")[0])
    return imports, exports


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--readelf", default="readelf")
    parser.add_argument("--bridge", type=Path, default=ROOT / "mpv-android-ffmpeg9-bridge.zip")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="mpv-bridge-check-") as directory:
        work = Path(directory)
        for archive, target in ((ROOT / "mpv-android-ffmpeg9-native.zip", "runtime"),
                                (ROOT / "../../app/libs/mpv-android-lib-v0.0.3.aar", "base"),
                                (args.bridge, "bridge")):
            with zipfile.ZipFile(archive) as bundle:
                bundle.extractall(work / target)
        for abi in ABIS:
            bridge = work / "bridge" / abi / "libplayer.so"
            imports, exports = symbols(args.readelf, bridge)
            _, original = symbols(args.readelf, work / "base/jni" / abi / "libplayer.so")
            jni_api = {name for name in original if name.startswith("Java_is_xyz_mpv_MPVLib_")}
            missing_api = jni_api - exports
            if missing_api:
                raise RuntimeError(f"{abi}: missing JNI API: {sorted(missing_api)}")
            runtime_exports = set()
            for library in (work / "runtime" / abi).glob("*.so"):
                if library.name != "libplayer.so":
                    runtime_exports.update(symbols(args.readelf, library)[1])
            media_imports = {name for name in imports if re.match(r"(?:av_|avcodec_|avformat_|avio_|sws_|mpv_)", name)}
            unresolved = media_imports - runtime_exports
            if unresolved:
                raise RuntimeError(f"{abi}: unresolved runtime symbols: {sorted(unresolved)}")
            if "av_jni_set_java_vm@LIBAVCODEC_63" not in imports:
                raise RuntimeError(f"{abi}: bridge must link against FFmpeg 9 / libavcodec 63")
            print(f"{abi}: retained all {len(jni_api)} JNI methods; resolved {len(media_imports)} media imports")


if __name__ == "__main__":
    main()
