#!/usr/bin/env python3
"""Remaining player items: engine, mpv*, preload/ua/subtitle* with EXO+MPV coverage."""
from __future__ import annotations

import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

DEVICE = os.environ.get("PHONE_SERIAL", "192.168.1.202:5555")
OUT = "Release/device-test/settings-report-player-remaining.json"


def adb(*args, timeout=30):
    return subprocess.run(["adb", "-s", DEVICE, *args], capture_output=True,
                          text=True, encoding="utf-8", errors="ignore", timeout=timeout).stdout


def wake():
    adb("shell", "svc power stayon true; input keyevent KEYCODE_WAKEUP; input keyevent 82; wm dismiss-keyguard")


def labels_root():
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
    if any(k in blob for k in ("取消", "确定", "输入", "清除", "默认", "保存", "选择", "完成", "编辑", "Assrt", "Token", "字体")):
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
    time.sleep(0.5)
    pre, root = labels_root()
    if root is not None:
        tap_id(root, "player")
    time.sleep(0.7)


def one(id_suffix):
    open_player()
    pre, root = labels_root()
    if root is None:
        return {"id": id_suffix, "kind": "dump-fail"}
    # scroll down to reveal lower items
    if id_suffix in ("preload", "ua", "subtitleAssrt", "subtitleFont", "audioEffect", "videoEffect", "speed"):
        adb("shell", "input", "swipe", "540", "1600", "540", "700", "400")
        time.sleep(0.4)
        pre, root = labels_root()
    ok = tap_id(root, id_suffix)
    post, _ = labels_root()
    kind, new = classify(pre, post)
    rec = {"id": id_suffix, "tapped": ok, "kind": kind, "new": new, "visible_in_pre": id_suffix in str(pre) or any(id_suffix in x for x in pre)}
    # also record if any related label was in pre
    rec["pre_has"] = [x for x in pre if any(k in x.lower() or k in x for k in (id_suffix, "缓冲", "http", "延迟", "mpv", "vulkan", "gpu", "预载", "ua", "token", "字体", "倍速"))][:12]
    print(f"[{kind:12}] {id_suffix:16} ok={ok} {new}")
    if kind == "dialog":
        back(1)
    return rec


def main():
    results = []
    # First force MPV to reveal mpv* items
    open_player()
    pre, root = labels_root()
    tap_id(root, "engine")
    time.sleep(0.3)
    for id_suffix in ["engine", "mpvConf", "mpvGpuNext", "mpvVulkan", "preload", "ua", "subtitleAssrt", "subtitleFont", "speed", "audioEffect"]:
        results.append(one(id_suffix))

    # Force EXO for buffer/http (already tested) and confirm engine
    results.append(one("engine"))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    from collections import Counter
    print("SUMMARY", dict(Counter(r["kind"] for r in results)))
    print("WROTE", OUT)


if __name__ == "__main__":
    main()
