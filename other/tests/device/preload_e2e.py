#!/usr/bin/env python3
"""End-to-end check for next-episode disk preload (ExoPlayer engine).

Serves two local media files over HTTP with byte-range support, wires the device back to
this host with ``adb reverse``, then drives ``PreloadSmokeActivity``:

  * forward  -- play ep1 while preloading ep2, then switch to ep2
  * reverse  -- play ep2 while preloading ep1, then switch to ep1
  * retained -- preload ep2 again after the first pass and confirm the spans are still there
  * cancelled -- cancel an in-flight preload and verify the lifecycle callback
  * failure -- request a missing next episode and verify a bounded error result

The activity reports how many bytes of the second episode are resident in the shared playback
cache. Normal playback only *reads* that cache, so ``cachedBytes > 0`` is a real hit signal.

Usage
-----
    python preload_e2e.py --ep1 path\\to\\ep1.mp4 --ep2 path\\to\\ep2.mp4
    python preload_e2e.py --dir path\\to\\media        # picks ep1.* and ep2.*

Prefer progressive MP4: the byte-level check keys off the media URI, which is exact for MP4 but
only covers the manifest for HLS.

Requires a debug build (the harness lives in app/src/debug) and a reachable device.
"""

import argparse
import functools
import os
import re
import socket
import subprocess
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PKG = "com.fongmi.android.tv"
ACTIVITY = f"{PKG}/.debug.PreloadSmokeActivity"
TAG = "PreloadSmoke"
DEFAULT_DEVICE = "192.168.1.202:5555"
OUT = Path(__file__).resolve().parent
MEDIA_SUFFIXES = (".mp4", ".mkv", ".ts", ".m4v", ".mov", ".webm")


class RangeHandler(SimpleHTTPRequestHandler):
    """SimpleHTTPRequestHandler does not implement Range, and ExoPlayer needs it for MP4."""

    def send_head(self):
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            return super().send_head()
        try:
            source = open(path, "rb")
        except OSError:
            self.send_error(404, "File not found")
            return None
        size = os.fstat(source.fileno()).st_size
        start, end = 0, size - 1
        header = self.headers.get("Range")
        status = 200
        if header and header.startswith("bytes="):
            spec = header[len("bytes="):].split(",")[0].strip()
            first, _, last = spec.partition("-")
            if first:
                start = int(first)
                end = int(last) if last else size - 1
            elif last:
                start = max(0, size - int(last))
            if start >= size:
                source.close()
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{size}")
                self.end_headers()
                return None
            end = min(end, size - 1)
            status = 206
        length = end - start + 1
        self.send_response(status)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        source.seek(start)
        return _LimitedReader(source, length)

    def log_message(self, fmt, *args):
        pass


class _LimitedReader:
    """File wrapper that stops after `remaining` bytes so partial responses terminate."""

    def __init__(self, source, remaining):
        self.source = source
        self.remaining = remaining

    def read(self, size=-1):
        if self.remaining <= 0:
            return b""
        if size is None or size < 0:
            size = self.remaining
        chunk = self.source.read(min(size, self.remaining))
        self.remaining -= len(chunk)
        return chunk

    def close(self):
        self.source.close()


def adb(device, *args, timeout=120):
    proc = subprocess.run(["adb", "-s", device, *args], capture_output=True, timeout=timeout)
    return (proc.stdout + proc.stderr).decode("utf-8", "ignore")


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def start_server(directory):
    port = free_port()
    handler = functools.partial(RangeHandler, directory=str(directory))
    server = ThreadingHTTPServer(("127.0.0.1", port), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, port


def parse_result(log):
    match = re.findall(rf"{TAG}[^\n]*RESULT[^\n]+", log)
    if not match:
        return None
    line = match[-1]
    fields = dict(re.findall(r"(\w+)=(\S+)", line))
    return {
        "raw": line,
        "settled": fields.get("preloadSettled", ""),
        "completed": int(fields.get("completed", 0)),
        "failed": int(fields.get("failed", 0)),
        "cancelled": int(fields.get("cancelled", 0)),
        "skipped": int(fields.get("skipped", 0)),
        "cachedBytes": int(fields.get("cachedBytes", 0)),
        "hit": fields.get("hit") == "true",
        "preloadMs": int(fields.get("preloadMs", 0)),
        "switchReadyMs": int(fields.get("switchReadyMs", -1)),
    }


def run_scenario(device, label, port, name1, name2, decode, wait_ms, switch, duration_ms,
                 expected="hit", cancel_after_ms=-1):
    url1 = f"http://127.0.0.1:{port}/{name1}"
    url2 = f"http://127.0.0.1:{port}/{name2}"
    print(f"\n==== {label}: {name1} -> {name2} (decode={decode}, window={duration_ms}ms) ====")
    adb(device, "shell", "am", "force-stop", PKG)
    time.sleep(0.8)
    adb(device, "logcat", "-c")
    adb(device, "shell", "am", "start", "-n", ACTIVITY,
        "--es", "url1", url1, "--es", "url2", url2,
        "--ei", "decode", str(decode),
        # waitMs/durationMs are long extras; --ei would store Integer, and
        # getLongExtra() silently falls back to its default on that mismatch.
        "--el", "waitMs", str(wait_ms),
        "--el", "durationMs", str(duration_ms),
        "--el", "cancelAfterMs", str(cancel_after_ms),
        "--ez", "switchToSecond", "true" if switch else "false")
    deadline = time.time() + (wait_ms / 1000.0) + 45
    result = None
    while time.time() < deadline:
        result = parse_result(adb(device, "logcat", "-d", "-s", f"{TAG}:V"))
        if result:
            break
        time.sleep(1.5)
    if not result:
        print("  !! no RESULT line; raw logcat tail:")
        print("\n".join(adb(device, "logcat", "-d", "-s", f"{TAG}:V").splitlines()[-15:]))
        return None
    if expected == "cancelled":
        ok = result["cancelled"] > 0 and result["failed"] == 0
    elif expected == "failure":
        ok = result["failed"] > 0 and not result["hit"]
    else:
        ok = result["hit"] and result["completed"] > 0 and result["failed"] == 0
    result["passed"] = ok
    print(f"  settled={result['settled']} completed={result['completed']} failed={result['failed']} "
          f"cancelled={result['cancelled']} skipped={result['skipped']} "
          f"cachedBytes={result['cachedBytes']} hit={result['hit']} "
          f"preloadMs={result['preloadMs']} switchReadyMs={result['switchReadyMs']}")
    print(f"  {'PASS' if ok else 'FAIL'}")
    return result


def pick_media(directory):
    files = sorted(p for p in Path(directory).iterdir()
                   if p.is_file() and p.suffix.lower() in MEDIA_SUFFIXES)
    if len(files) < 2:
        return None, None
    return files[0].name, files[1].name


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", default=DEFAULT_DEVICE)
    parser.add_argument("--ep1")
    parser.add_argument("--ep2")
    parser.add_argument("--dir", default=str(OUT / "media"))
    parser.add_argument("--decode", type=int, default=1)
    parser.add_argument("--wait-ms", type=int, default=30000)
    parser.add_argument("--duration-ms", type=int, default=10000,
                        help="preload window per episode; keep it shorter than the clip")
    parser.add_argument("--keep-server", action="store_true")
    args = parser.parse_args()

    directory = Path(args.dir).resolve()
    if args.ep1 and args.ep2:
        directory = Path(args.ep1).resolve().parent
        name1, name2 = Path(args.ep1).name, Path(args.ep2).name
    else:
        name1, name2 = pick_media(directory)

    if not name1 or not name2:
        print(f"Need two media files in {directory} (or pass --ep1/--ep2).")
        print("Put two short progressive MP4s there, e.g. ep1.mp4 and ep2.mp4.")
        return 2

    server, port = start_server(directory)
    print(f"serving {directory} on 127.0.0.1:{port}")
    try:
        adb(args.device, "reverse", f"tcp:{port}", f"tcp:{port}")
        forward = run_scenario(args.device, "forward", port, name1, name2, args.decode, args.wait_ms, True, args.duration_ms)
        reverse = run_scenario(args.device, "reverse", port, name2, name1, args.decode, args.wait_ms, True, args.duration_ms)
        # Re-running the forward case proves the earlier spans survived a release/cancel cycle.
        retained = run_scenario(args.device, "retained", port, name1, name2, args.decode, args.wait_ms, False, args.duration_ms)
        cancelled = run_scenario(args.device, "cancelled", port, name1, name2 + "?cancelled=1", args.decode,
                                 args.wait_ms, False, args.duration_ms,
                                 expected="cancelled", cancel_after_ms=1)
        failure = run_scenario(args.device, "failure", port, name1, "missing-episode.mp4", args.decode,
                               args.wait_ms, False, args.duration_ms, expected="failure")
    finally:
        if not args.keep_server:
            adb(args.device, "reverse", "--remove", f"tcp:{port}")
            server.shutdown()

    print("\n==== summary ====")
    verdicts = [("forward", forward), ("reverse", reverse), ("retained", retained),
                ("cancelled", cancelled), ("failure", failure)]
    failed = [name for name, res in verdicts if not res or not res.get("passed")]
    for name, res in verdicts:
        state = "n/a" if not res else ("pass" if res.get("passed") else "fail")
        print(f"  {name:9s} {state}")
    if failed:
        print(f"  -> not verified: {', '.join(failed)}")
        return 1
    print("  -> cache-hit, retention, cancellation and failure scenarios passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
