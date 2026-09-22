#!/usr/bin/env python3
"""Precise player-settings item test using dump IDs after opening the page each time."""
from __future__ import annotations

import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

DEVICE = os.environ.get("PHONE_SERIAL", "192.168.1.202:5555")
OUT = "Release/device-test/settings-report-player-precise.json"

IDS = [
    "engine", "decode", "adblock", "buffer", "http", "liveLatency",
    "mpvConf", "mpvGpuNext", "mpvVulkan", "render", "scale", "caption",
    "speed", "audioEffect", "videoEffect", "background", "preload", "ua",
    "subtitleAssrt", "subtitleFont",
]


def adb(*args, timeout=30):
    return subprocess.run(["adb", "-s", DEVICE, *args], capture_output=True,
                          text=True, encoding="utf-8", errors="ignore", timeout=timeout).stdout


def wake():
    adb("shell", "svc power stayon true; input keyevent KEYCODE_WAKEUP; input keyevent 82; wm dismiss-keyguard")


def labels():
    adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
    try:
        root = ET.fromstring(adb("shell", "cat", "/sdcard/u.xml"))
    except Exception:
        return [], None
    out = []
    for n in root.iter("node"):
        for a in ("text", "content-desc"):
            v = (n.get(a) or "").strip()
            if v and v not in out:
                out.append(v)
    return out, root


def tap_id(root, suffix):
    best = None
    for n in root.iter("node"):
        rid = n.get("resource-id") or ""
        if not rid.endswith("/" + suffix):
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        c = ((x1 + x2) // 2, (y1 + y2) // 2)
        score = 5 if n.get("clickable") == "true" else 0
        if best is None or score > best[0]:
            best = (score, c)
    if not best:
        return False
    adb("shell", "input", "tap", str(best[1][0]), str(best[1][1]))
    time.sleep(0.8)
    return True


def classify(before, after):
    new = [x for x in after if x not in before]
    gone = [x for x in before if x not in after]
    blob = " ".join(new)
    if any(k in blob for k in ("取消", "确定", "输入", "清除", "默认", "保存", "选择", "完成", "编辑", "Assrt", "Token")):
        return "dialog", new[:12]
    if new or gone:
        return "value-change", new[:8] + ["|"] + gone[:6]
    return "no-change", []


def back(n=1):
    for _ in range(n):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.5)


def open_player():
    wake()
    adb("shell", "monkey", "-p", "com.fongmi.android.tv", "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(1.2)
    adb("shell", "input", "tap", "900", "2197")
    time.sleep(0.55)
    # tap 播放设置 by id
    pre, root = labels()
    if root is not None:
        tap_id(root, "player")
    time.sleep(0.6)


def main():
    results = []
    for id_suffix in IDS:
        open_player()
        pre, root = labels()
        if root is None:
            results.append({"id": id_suffix, "kind": "dump-fail"})
            continue
        ok = tap_id(root, id_suffix)
        post, _ = labels()
        kind, new = classify(pre, post)
        results.append({"id": id_suffix, "tapped": ok, "kind": kind, "new": new,
                        "pre_sample": pre[:15], "post_sample": post[:15]})
        print(f"[{kind:12}] {id_suffix:16} ok={ok} {new}")
        if kind == "dialog":
            back(1)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    from collections import Counter
    print("SUMMARY", dict(Counter(r["kind"] for r in results)))
    print("WROTE", OUT)


if __name__ == "__main__":
    main()
