#!/usr/bin/env python3
"""Patch media3compat scripts: LF endings + skip native rebuild when libs exist."""
from pathlib import Path

ROOT = Path("/data/home/zyq/IdeaProjects/github/FongMi/TV/media3compat/scripts")
MARKER = "# SKIP_IF_PREBUILT"

SHIM = r'''# SKIP_IF_PREBUILT
# Fast path: reuse already-built libs so Java-only rebuilds skip Docker.
# Force a full rebuild with FONGMI_FORCE_NATIVE=1.
if [ "${FONGMI_FORCE_NATIVE:-}" != "1" ]; then
  OUT="${3:-}"
  ABIS="${4:-arm64-v8a}"
  ok=1
  for abi in $(echo "$ABIS" | tr ',' ' '); do
    for f in libmpv.so libplayer.so libavcodec.so libavformat.so libavutil.so \
             libswresample.so libswscale.so libavfilter.so libavdevice.so \
             libc++_shared.so libffmpegJNI.so; do
      if [ ! -f "$OUT/$abi/$f" ]; then
        ok=0
        break
      fi
    done
    [ "$ok" -eq 1 ] || break
  done
  if [ "$ok" -eq 1 ]; then
    echo "[build_libmpv] prebuilt libs present in $OUT, skipping native rebuild"
    exit 0
  fi
fi
'''

def normalize(path: Path) -> None:
    text = path.read_text(encoding="utf-8", errors="replace").replace("\r\n", "\n").replace("\r", "\n")
    path.write_text(text, encoding="utf-8")
    print(f"normalized {path.name}: {len(text.splitlines())} lines, has_cr={chr(13) in text}")


def patch_libmpv() -> None:
    path = ROOT / "build_libmpv_android.sh"
    text = path.read_text(encoding="utf-8", errors="replace").replace("\r\n", "\n").replace("\r", "\n")
    if MARKER in text:
        # Keep existing marker block at top, drop any broken first pass.
        idx = text.find("#!/bin/bash")
        if idx == -1:
            idx = text.find("#!/")
        body = text[idx:] if idx != -1 else text
        # Remove a previous SKIP block if present at start of body.
        if body.lstrip().startswith("# SKIP_IF_PREBUILT"):
            # find end of shim: first line starting with '#!' or original shebang already consumed
            pass
        text = SHIM + body
        # If body still contains an old shim, strip duplicates after first.
        first = text.find(MARKER)
        second = text.find(MARKER, first + 1)
        if second != -1:
            text = text[:second]
    else:
        text = SHIM + text
    path.write_text(text, encoding="utf-8")
    print(f"patched {path.name}: {len(text.splitlines())} lines")


for name in ("build_libmpv_android.sh", "build_ffmpeg_x86_64.sh", "build_dav1d_android.sh"):
    p = ROOT / name
    if p.exists():
        normalize(p)

patch_libmpv()
print("OK")
