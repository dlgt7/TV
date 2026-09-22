#!/usr/bin/env python3
"""TV settings: walk home func row carefully and enter SettingActivity."""
from __future__ import annotations

import json
import os
import subprocess
import time
import xml.etree.ElementTree as ET

DEVICE = os.environ.get("TV_SERIAL", "home-rk:5555")
OUT = "Release/device-test/settings-report-tv.json"


def adb(*args, timeout=40):
    return subprocess.run(["adb", "-s", DEVICE, *args], capture_output=True,
                          text=True, encoding="utf-8", errors="ignore", timeout=timeout).stdout


def labels():
    adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
    try:
        root = ET.fromstring(adb("shell", "cat", "/sdcard/u.xml"))
    except Exception:
        return []
    out = []
    for n in root.iter("node"):
        for a in ("text", "content-desc"):
            v = (n.get(a) or "").strip()
            if v and v not in out:
                out.append(v)
    return out


def key(code, pause=0.5):
    adb("shell", "input", "keyevent", code)
    time.sleep(pause)


def top_activity():
    out = adb("shell", "dumpsys", "activity", "activities")
    for line in out.splitlines():
        if "topResumedActivity" in line:
            return line.strip()
    return ""


def classify(before, after):
    new = [x for x in after if x not in before]
    gone = [x for x in before if x not in after]
    blob = " ".join(new)
    if any(k in blob for k in ("取消", "确定", "输入", "清除", "默认", "保存", "选择", "完成", "编辑")):
        return "dialog", new[:12]
    if new or gone:
        return "value-change", new[:8] + ["|"] + gone[:6]
    return "no-change", []


def ensure_settings():
    adb("shell", "monkey", "-p", "com.fongmi.android.tv", "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(2.0)
    # Prefer staying on home; if already settings, done.
    if "SettingActivity" in top_activity():
        return True
    key("KEYCODE_DPAD_DOWN", 0.8)
    # Func row: 点播 直播 搜索 收藏 推送 [网络存储] [媒体库] 设置
    # Press RIGHT up to 10 times while dumping; stop when ENTER would likely hit 设置.
    for i in range(10):
        key("KEYCODE_DPAD_RIGHT", 0.35)
        # Use a lightweight probe: after 6+ rights we are near the end
        if i >= 5:
            before = labels()
            key("KEYCODE_DPAD_CENTER", 1.0)
            act = top_activity()
            print("  probe", i, act[-60:], labels()[:8])
            if "SettingActivity" in act:
                return True
            if "LiveActivity" in act:
                key("KEYCODE_BACK", 0.8)
            elif "VodActivity" in act or "Activity" in act and "HomeActivity" not in act:
                key("KEYCODE_BACK", 0.8)
            # go back to func row and continue right
            key("KEYCODE_DPAD_DOWN", 0.3)
            # re-enter grid top
    return "SettingActivity" in top_activity()


def walk(name, steps, results):
    print(f"\n== {name} ==")
    print("  activity", top_activity())
    print("  labels", labels()[:25])
    for i in range(steps):
        key("KEYCODE_DPAD_DOWN", 0.35)
        before = labels()
        key("KEYCODE_DPAD_CENTER", 0.7)
        after = labels()
        kind, new = classify(before, after)
        results.append({"page": name, "index": i, "kind": kind, "new": new,
                        "before": before[:18], "after": after[:18], "act": top_activity()})
        print(f"  [{kind:12}] #{i:02d} {new} | {top_activity()[-50:]}")
        if kind == "dialog":
            key("KEYCODE_BACK", 0.6)
        # if we left settings, go back
        if "SettingActivity" not in top_activity():
            key("KEYCODE_BACK", 0.6)


def open_player_page():
    ensure_settings()
    # from main settings: vod live wall player ...
    key("KEYCODE_DPAD_DOWN", 0.35)
    key("KEYCODE_DPAD_DOWN", 0.35)
    key("KEYCODE_DPAD_DOWN", 0.35)
    key("KEYCODE_DPAD_DOWN", 0.35)  # player
    key("KEYCODE_DPAD_CENTER", 1.0)


def main():
    results = []
    ok = ensure_settings()
    print("settings_open", ok, top_activity())
    if ok:
        walk("tv-main", 16, results)
        open_player_page()
        walk("tv-player", 20, results)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    from collections import Counter
    print("SUMMARY", dict(Counter(r["kind"] for r in results)))
    print("WROTE", OUT)


if __name__ == "__main__":
    main()
