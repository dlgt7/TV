#!/usr/bin/env python3
"""D-pad navigation helper for the Leanback TV box.

The TV has no touch input and the settings activities are not exported, so the
only way to reach a screen is to drive the remote with `input keyevent` and read
back focus with `uiautomator dump`.

Note: on this UI the focused node is usually a container, and the visible label
lives in a child TextView, so focus text is taken from the node's whole subtree.

Usage:
    python tv_nav.py focus
    python tv_nav.py dump               # list every labelled node on screen
    python tv_nav.py right N            # press DPAD_RIGHT N times, print focus each time
    python tv_nav.py goto "设置" [max]   # press RIGHT until focus contains the label, then ENTER
    python tv_nav.py enter
    python tv_nav.py back
    python tv_nav.py key up|down|left|right|enter|back|menu|play
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

DEVICE = "home-rk:5555"
REMOTE_XML = "/sdcard/tvnav.xml"

KEYS = {
    "up": "KEYCODE_DPAD_UP",
    "down": "KEYCODE_DPAD_DOWN",
    "left": "KEYCODE_DPAD_LEFT",
    "right": "KEYCODE_DPAD_RIGHT",
    "enter": "KEYCODE_DPAD_CENTER",
    "back": "KEYCODE_BACK",
    "menu": "KEYCODE_MENU",
    "play": "KEYCODE_MEDIA_PLAY_PAUSE",
}


def adb(*args, timeout=60):
    return subprocess.run(["adb", "-s", DEVICE, *args], capture_output=True,
                          text=True, encoding="utf-8", errors="ignore", timeout=timeout).stdout


def subtree_text(node):
    parts = []
    for attr in ("text", "content-desc"):
        value = (node.get(attr) or "").strip()
        if value:
            parts.append(value)
    for child in node:
        parts.extend(p for p in subtree_text(child) if p not in parts)
    return parts


def root():
    adb("shell", "uiautomator", "dump", REMOTE_XML)
    xml = adb("shell", "cat", REMOTE_XML)
    return ET.fromstring(xml)


def show_focus():
    for node in root().iter("node"):
        if node.get("focused") == "true":
            texts = subtree_text(node)
            return " | ".join(texts), node.get("bounds") or "", node.get("resource-id") or ""
    return "", "", ""


def summary():
    text, bounds, rid = show_focus()
    print(f"focus={text!r} bounds={bounds} id={rid}")
    return text


def key(name, times=1, pause=0.35):
    for _ in range(times):
        adb("shell", "input", "keyevent", KEYS[name])
        time.sleep(pause)


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "focus"
    if cmd == "focus":
        summary()
    elif cmd == "dump":
        for node in root().iter("node"):
            texts = subtree_text(node)
            if texts:
                click = " [CLICK]" if node.get("clickable") == "true" else ""
                print(f"  {node.get('resource-id') or ''}\n      {' / '.join(texts)}{click}")
    elif cmd == "right":
        for i in range(int(sys.argv[2]) if len(sys.argv) > 2 else 1):
            key("right")
            text, bounds, _ = show_focus()
            print(f"  step {i + 1}: {text!r} {bounds}")
    elif cmd in ("enter", "back"):
        key(cmd)
        time.sleep(0.8)
        summary()
    elif cmd == "key":
        key(sys.argv[2])
        time.sleep(0.5)
        summary()
    elif cmd == "goto":
        target = sys.argv[2]
        steps = int(sys.argv[3]) if len(sys.argv) > 3 else 10
        for i in range(steps):
            text, bounds, _ = show_focus()
            print(f"  [{i}] {text!r}")
            if target in text:
                key("enter")
                time.sleep(1.2)
                summary()
                return 0
            key("right")
        print(f"target {target!r} not reached in {steps} steps", file=sys.stderr)
        return 1
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
