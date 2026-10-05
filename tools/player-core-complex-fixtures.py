"""Generate original ASS/WebVTT/TTML regression captions; no third-party media."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
header = (root / 'sample.ass').read_text().split('Dialogue:', 1)[0]
(root / 'complex.ass').write_text(header + r'''Dialogue: 0,0:00:02.00,0:00:18.00,Default,,0,0,0,,{\pos(320,310)}Overlapping long dialogue
Dialogue: 1,0:00:02.00,0:00:18.00,Default,,0,0,0,,{\pos(320,70)\1c&H00FFFF&\2c&HFF0000&}{\k200}Ka{\k200}ra{\k200}o{\k200}ke
Dialogue: 2,0:00:02.00,0:00:18.00,Default,,0,0,0,,{\pos(320,140)\frz-15\t(0,12000,\frz15\fscx140)\bord3\blur1}Rotate and transform
Dialogue: 3,0:00:02.00,0:00:18.00,Default,,0,0,0,,{\an7\pos(60,100)\bord0\shad0\c&HFFFF00&\clip(60,100,90,130)\p1}m 0 0 l 60 0 60 60 0 60
Dialogue: 4,0:00:02.00,0:00:18.00,Default,,0,0,0,,{\move(100,210,540,210)\clip(120,175,520,225)\fs24}Moving clipped line
Dialogue: 5,0:00:02.00,0:00:18.00,Default,,0,0,0,,{\pos(320,270)\fs24}العربية עברית — Unicode
Dialogue: 0,0:00:30.00,0:01:00.00,Default,,0,0,0,,{\pos(320,310)}Later dialogue after empty interval
''')
(root / 'complex.vtt').write_text('''WEBVTT

STYLE
::cue(.yellow) { color: yellow; }
::cue(.green) { color: lime; }

00:00:02.000 --> 00:00:18.000 line:15% position:50% size:80% align:center
<c.yellow><b>Top WebVTT region</b></c>

00:00:02.000 --> 00:00:18.000 line:80% position:50% size:80% align:center
<c.green><i>Bottom overlapping region</i></c>
Second line &amp; Unicode العربية

00:00:30.000 --> 00:01:00.000 line:75% position:50% align:center
Later WebVTT dialogue
''')
(root / 'complex.ttml').write_text('''<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml" xmlns:tts="http://www.w3.org/ns/ttml#styling">
<head><styling>
<style xml:id="base" tts:fontSize="110%" tts:textAlign="center"/>
<style xml:id="yellow" style="base" tts:color="yellow" tts:fontWeight="bold"/>
<style xml:id="green" style="base" tts:color="lime" tts:fontStyle="italic"/>
</styling><layout>
<region xml:id="top" tts:origin="10% 10%" tts:extent="80% 25%" tts:displayAlign="center"/>
<region xml:id="bottom" tts:origin="10% 65%" tts:extent="80% 30%" tts:displayAlign="after"/>
</layout></head><body><div>
<p begin="2s" end="18s" region="top" style="yellow">Top TTML region</p>
<p begin="2s" end="18s" region="bottom" style="green">Bottom overlapping region<br/><span tts:textDecoration="underline">Underlined second line</span></p>
<p begin="30s" end="60s" region="bottom" style="base">Later TTML dialogue</p>
</div></body></tt>
''')
