#!/usr/bin/env python3
"""Controlled playback probe: serve a local clip, make the device play it, sample resource usage.

The app registers an ``ACTION_VIEW`` filter for ``http(s)`` + ``video/*``, so a locally served file
can be played without depending on any third-party VOD source. That makes it possible to measure
what the app costs *while actually decoding*, instead of guessing from an idle home screen.

Resource samples are taken from ``dumpsys meminfo`` (TOTAL PSS) and ``top`` (%CPU), plus an
``audio_flinger`` check for an active output, which is the cheapest reliable "is it really
playing" signal on these devices.

Usage
-----
    python playback_probe.py --device home-rk:5555 --file media/ep1.mp4
    python playback_probe.py --device 192.168.1.202:5555 --file media/ep1.mp4 --samples 8
"""

import argparse
import functools
import re
import subprocess
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from preload_e2e import RangeHandler, adb, free_port  # noqa: E402

from http.server import ThreadingHTTPServer  # noqa: E402

PKG = "com.fongmi.android.tv"


def total_pss(device):
    out = adb(device, "shell", "dumpsys", "meminfo", PKG)
    match = re.search(r"TOTAL PSS:\s+(\d+)", out)
    return int(match.group(1)) if match else -1


def cpu_percent(device):
    out = adb(device, "shell", "top", "-n", "1", "-b", "-o", "PID,%CPU,ARGS")
    total = 0.0
    for line in out.splitlines():
        if PKG not in line:
            continue
        parts = line.split()
        try:
            total += float(parts[1])
        except (IndexError, ValueError):
            pass
    return total


def audio_active(device):
    """True when the app owns an enabled output on audio flinger."""
    out = adb(device, "shell", "dumpsys", "media.audio_flinger")
    return bool(re.search(r"Standby: no|state=ACTIVE|active tracks?\s*[:=]\s*[1-9]", out))


def focused_window(device):
    out = adb(device, "shell", "dumpsys", "window")
    match = re.search(r"mCurrentFocus=(\S+ \S+ \S+)", out)
    return match.group(1) if match else "?"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", required=True)
    parser.add_argument("--file", required=True, help="media file to serve and play")
    parser.add_argument("--samples", type=int, default=6)
    parser.add_argument("--interval", type=float, default=3.0)
    parser.add_argument("--mime", default="video/mp4")
    args = parser.parse_args()

    target = Path(args.file).resolve()
    if not target.is_file():
        print(f"missing media file: {target}")
        return 2

    port = free_port()
    server = ThreadingHTTPServer(("127.0.0.1", port),
                                 functools.partial(RangeHandler, directory=str(target.parent)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{port}/{target.name}"
    print(f"serving {target.parent} on 127.0.0.1:{port}\nurl={url}")

    try:
        adb(args.device, "reverse", f"tcp:{port}", f"tcp:{port}")
        adb(args.device, "shell", "am", "force-stop", PKG)
        time.sleep(1.0)
        adb(args.device, "logcat", "-c")
        print("\n-- baseline (stopped) --")
        print(f"   pss={total_pss(args.device)}KB cpu={cpu_percent(args.device)}%")
        adb(args.device, "shell", "am", "start", "-a", "android.intent.action.VIEW",
            "-d", url, "-t", args.mime, PKG)
        time.sleep(4.0)
        print(f"focus={focused_window(args.device)}")

        pss_samples, cpu_samples = [], []
        for index in range(args.samples):
            time.sleep(args.interval)
            pss, cpu = total_pss(args.device), cpu_percent(args.device)
            pss_samples.append(pss)
            cpu_samples.append(cpu)
            print(f"   sample {index + 1}: pss={pss}KB cpu={cpu}% audio={audio_active(args.device)}")

        print("\n==== summary ====")
        if pss_samples:
            print(f"  PSS   min={min(pss_samples)}KB max={max(pss_samples)}KB last={pss_samples[-1]}KB")
            print(f"  CPU   min={min(cpu_samples)}% max={max(cpu_samples)}% avg={sum(cpu_samples) / len(cpu_samples):.1f}%")
        print(f"  focus={focused_window(args.device)}")
        print("\n-- app errors --")
        pid = adb(args.device, "shell", "pidof", PKG).strip()
        log = adb(args.device, "logcat", "-d", "--pid", pid)
        errors = [line for line in log.splitlines()
                  if re.search(r"FATAL|VerifyError|NoSuchMethod|NoClassDefFound|IllegalState", line)]
        print("\n".join(errors[:10]) if errors else "   (none)")

        print(f"\n-- app log touching the served url / player --")
        interesting = [line for line in log.splitlines()
                       if re.search(rf":{port}|Exo|Mpv|Player|player|MediaItem|playback|Playback", line)]
        print("\n".join(interesting[:25]) if interesting else "   (nothing about the url or the player)")
    finally:
        adb(args.device, "reverse", "--remove", f"tcp:{port}")
        server.shutdown()
    return 0


if __name__ == "__main__":
    sys.exit(main())
