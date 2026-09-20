#!/usr/bin/env python3
"""Touch-based UI helper for the phone (mobile flavor).

The TV box is driven with a D-pad (see `tv_nav.py`); the phone has a real
touchscreen, so this drives it with `input tap` at the centre of a labelled
node read back from `uiautomator dump`.

Caveats learned on this device (Android 16 / Redmi Note 8):
  * the lock screen comes back quickly, so unlock + act must share one adb call
    (handled by `ensure_awake`).
  * `uiautomator dump` occasionally returns an empty/degenerate tree; retried a
    few times before giving up.

Usage:
    python phone_nav.py texts                 # list every visible label
    python phone_nav.py tap "直播"            # tap the node whose label matches
    python phone_nav.py tapid <resource-id>   # tap by resource-id (suffix match)
    python phone_nav.py dump                  # raw ids + labels + bounds
    python phone_nav.py wake                  # unlock and stay awake
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

DEVICE = os.environ.get("PHONE_SERIAL", "192.168.1.202:5555")
REMOTE_XML = "/sdcard/phone_nav.xml"


def adb(*args, timeout=60):
    return subprocess.run(["adb", "-s", DEVICE, *args], capture_output=True,
                          text=True, encoding="utf-8", errors="ignore",
                          timeout=timeout).stdout


def ensure_awake():
    """Unlock and keep the screen on, in a single adb invocation."""
    adb("shell",
        "svc power stayon true; "
        "input keyevent KEYCODE_WAKEUP; "
        "input keyevent 82; "
        "wm dismiss-keyguard")


def subtree_text(node):
    parts = []
    for attr in ("text", "content-desc"):
        value = (node.get(attr) or "").strip()
        if value:
            parts.append(value)
    for child in node:
        parts.extend(p for p in subtree_text(child) if p not in parts)
    return parts


def root(attempts=4):
    last = None
    for _ in range(attempts):
        adb("shell", "uiautomator", "dump", REMOTE_XML)
        xml = adb("shell", "cat", REMOTE_XML)
        try:
            tree = ET.fromstring(xml)
        except ET.ParseError as exc:
            last = exc
            time.sleep(0.6)
            continue
        if any(True for _ in tree.iter("node")):
            return tree
        last = RuntimeError("empty hierarchy")
        time.sleep(0.6)
    raise SystemExit(f"uiautomator dump unusable after {attempts} tries: {last}")


def bounds_center(value):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", value or "")
    if not m:
        return None
    x1, y1, x2, y2 = (int(g) for g in m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(label, by_id=False):
    """Return (center, label) of the most specific node matching `label`.

    Matching must not stop at the first hit: in this UI the whole screen is one
    container whose subtree contains every label, so the first hit is always the
    root and tapping its centre lands in the middle of the page. Instead collect
    every candidate and prefer, in order: clickable, then smallest area.
    """
    candidates = []
    for node in root().iter("node"):
        rid = node.get("resource-id") or ""
        texts = subtree_text(node)
        hit = (label in rid) if by_id else any(label in t for t in texts)
        if not hit:
            continue
        bounds = node.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not m:
            continue
        x1, y1, x2, y2 = (int(g) for g in m.groups())
        center = ((x1 + x2) // 2, (y1 + y2) // 2)
        candidates.append((node.get("clickable") == "true", (x2 - x1) * (y2 - y1),
                           center, " / ".join(texts) or rid))
    if not candidates:
        return None, None
    candidates.sort(key=lambda c: (not c[0], c[1]))
    _, _, center, text = candidates[0]
    return center, text


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "texts"

    if cmd == "wake":
        ensure_awake()
        print("awake")
        return 0

    if cmd == "texts":
        seen = []
        for node in root().iter("node"):
            for t in subtree_text(node):
                if t not in seen:
                    seen.append(t)
        for t in seen:
            print(f"  {t}")
        return 0

    if cmd == "dump":
        for node in root().iter("node"):
            texts = subtree_text(node)
            if texts:
                click = " [CLICK]" if node.get("clickable") == "true" else ""
                print(f"  {node.get('resource-id') or '-'} {node.get('bounds')}{click}")
                print(f"      {' / '.join(texts)}")
        return 0

    if cmd in ("tap", "tapid"):
        if len(sys.argv) < 3:
            print("need a label", file=sys.stderr)
            return 2
        target = sys.argv[2]
        ensure_awake()
        center, label = find(target, by_id=(cmd == "tapid"))
        if not center:
            print(f"not found: {target!r}", file=sys.stderr)
            return 1
        print(f"tap {target!r} -> {center} ({label})")
        adb("shell", "input", "tap", str(center[0]), str(center[1]))
        time.sleep(1.0)
        return 0

    print(__doc__)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
