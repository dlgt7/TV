#!/usr/bin/env python3
"""Click-test mobile settings rows by label; record before/after response."""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

DEVICE = os.environ.get("PHONE_SERIAL", "192.168.1.202:5555")
OUT = "Release/device-test/settings-report-phone.json"


def adb(*args, timeout=45):
    return subprocess.run(
        ["adb", "-s", DEVICE, *args],
        capture_output=True, text=True, encoding="utf-8", errors="ignore", timeout=timeout
    ).stdout


def wake():
    adb("shell", "svc power stayon true; input keyevent KEYCODE_WAKEUP; input keyevent 82; wm dismiss-keyguard")


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
    xml = adb("shell", "cat", "/sdcard/u.xml")
    return ET.fromstring(xml)


def all_labels(tree):
    out = []
    for n in tree.iter("node"):
        for a in ("text", "content-desc"):
            v = (n.get(a) or "").strip()
            if v and v not in out:
                out.append(v)
    return out


def centers_for(tree, needle):
    hits = []
    for n in tree.iter("node"):
        texts = []
        for a in ("text", "content-desc"):
            v = (n.get(a) or "").strip()
            if v:
                texts.append(v)
        # also collect subtree
        for ch in n.iter():
            for a in ("text", "content-desc"):
                v = (ch.get(a) or "").strip()
                if v and v not in texts:
                    texts.append(v)
        joined = " / ".join(texts)
        if needle not in joined:
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        hits.append((
            n.get("clickable") == "true",
            (x2 - x1) * (y2 - y1),
            ((x1 + x2) // 2, (y1 + y2) // 2),
            joined[:80],
        ))
    if not hits:
        return None
    hits.sort(key=lambda h: (not h[0], h[1]))
    return hits[0][2], hits[0][3]


def classify(before, after):
    new = [x for x in after if x not in before]
    gone = [x for x in before if x not in after]
    if any(k in " ".join(new) for k in ("取消", "确定", "输入", "清除", "默认", "保存", "选择", "完成")):
        return "dialog", new[:12], gone[:8]
    if len(new) + len(gone) >= 5:
        return "navigation", new[:12], gone[:8]
    if new or gone:
        return "value-change", new[:12], gone[:8]
    return "no-change", [], []


def tap_needle(needle):
    tree = dump()
    found = centers_for(tree, needle)
    if not found:
        return False, None
    (x, y), label = found
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.85)
    return True, label


def back(n=1):
    for _ in range(n):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.55)


def open_settings():
    adb("shell", "monkey", "-p", "com.fongmi.android.tv", "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(1.6)
    ok, _ = tap_needle("设置")
    time.sleep(0.8)
    return ok


def test_items(items, page, results):
    print(f"\n== {page} ==")
    for needle in items:
        wake()
        pre = all_labels(dump())
        ok, label = tap_needle(needle)
        post = all_labels(dump())
        kind, new, gone = classify(pre, post)
        rec = {
            "page": page, "needle": needle, "tapped": ok, "hit": label,
            "kind": kind, "new": new, "gone": gone,
        }
        results.append(rec)
        print(f"  [{kind:12}] {needle!r} tapped={ok} new={new[:5]}")
        if kind == "dialog":
            back(1)
        elif kind == "navigation":
            back(2)
        # value-change / no-change: stay


def main():
    results = []
    wake()
    open_settings()

    test_items([
        "点播", "直播", "壁纸", "播放设置", "弹幕设置", "无痕模式",
        "图片尺寸", "主题色彩", "DoH", "缓存", "备份", "恢复", "版本",
    ], "main", results)

    # player page
    open_settings()
    tap_needle("播放设置")
    time.sleep(0.8)
    test_items([
        "播放引擎", "解码设置", "智能去广", "渲染方式", "缩放比例", "字幕样式",
        "长按倍速", "后台播放", "预载设置", "User-Agent", "字幕搜索", "字幕字体",
        "缓冲", "HTTP", "延迟", "mpv", "Vulkan", "gpu-next", "音频效果", "视频效果",
    ], "player", results)

    # decode
    open_settings(); tap_needle("播放设置"); time.sleep(0.6); tap_needle("解码设置"); time.sleep(0.8)
    test_items(["隧道", "直通", "音频", "视频", "AAC", "DV"], "decode", results)

    # danmaku
    open_settings(); tap_needle("弹幕设置"); time.sleep(0.8)
    test_items(["加载", "API", "自动", "爬虫", "弹幕"], "danmaku", results)

    # preload
    open_settings(); tap_needle("播放设置"); time.sleep(0.6); tap_needle("预载设置"); time.sleep(0.8)
    test_items(["预载", "线程", "大小", "时间", "计量", "诊断"], "preload", results)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    from collections import Counter
    print("\nSUMMARY", dict(Counter(r["kind"] for r in results)))
    for r in results:
        if r["kind"] in ("no-change",) or not r["tapped"]:
            print("  REVIEW:", r["page"], r["needle"], r["kind"], "tapped", r["tapped"])
    print("WROTE", OUT)


if __name__ == "__main__":
    main()
