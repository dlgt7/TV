#!/usr/bin/env bash
set -e
CORE_FIXTURE_DIR="${1:?Usage: player-core-fixtures.sh OUTPUT_DIRECTORY}"
CORE_FIXTURE_TOOLS="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$CORE_FIXTURE_DIR"
cd "$CORE_FIXTURE_DIR"
python3 - <<'PY'
from pathlib import Path
Path('sample.ass').write_text('''[Script Info]
ScriptType: v4.00+
PlayResX: 640
PlayResY: 360
WrapStyle: 0
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,DejaVu Sans,32,&H0000FFFF,&H00FFFFFF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,20,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.00,0:01:10.00,Default,,0,0,0,,{\\pos(320,300)\\c&H00FFFF&}ASS primary, styled
Dialogue: 0,0:00:00.00,0:00:20.00,Default,,0,0,0,,{\\move(40,60,500,60)\\fs20}Animated ASS
''')
Path('second.ass').write_text(Path('sample.ass').read_text().replace('ASS primary, styled','ASS secondary').replace('00FFFF','00FF00').replace('Animated ASS','Second animation'))
Path('english.srt').write_text('1\n00:00:00,000 --> 00:01:10,000\nEnglish secondary track\n')
Path('chinese.srt').write_text('1\n00:00:00,000 --> 00:01:10,000\n中文第一字幕\n')
PY
ffmpeg -nostdin -hide_banner -loglevel error -y -f lavfi -i color=c=0x152030:s=640x360:r=24 -f lavfi -i sine=frequency=1000:sample_rate=48000 -t 70 -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac -b:a 96k base.mp4
ffmpeg -nostdin -hide_banner -loglevel error -y -i base.mp4 -i sample.ass -i english.srt -i second.ass -map 0 -map 1 -map 2 -map 3 -c copy -metadata:s:s:0 language=zho -metadata:s:s:1 language=eng -metadata:s:s:2 language=jpn -disposition:s:0 default -disposition:s:1 0 -disposition:s:2 0 -attach /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf -metadata:s:t:0 mimetype=application/x-truetype-font styled.mkv
ffmpeg -nostdin -hide_banner -loglevel error -y -i base.mp4 -i chinese.srt -i english.srt -map 0 -map 1 -map 2 -c copy -metadata:s:s:0 language=zho -metadata:s:s:1 language=eng -disposition:s:0 default -disposition:s:1 0 dual-srt.mkv
python3 "$CORE_FIXTURE_TOOLS/player-core-complex-fixtures.py" .
ffmpeg -nostdin -hide_banner -loglevel error -y -i base.mp4 -i complex.ass -map 0 -map 1 -c copy -metadata:s:s:0 language=zho -attach /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf -metadata:s:t:0 mimetype=application/x-truetype-font complex.mkv
if [[ "${CORE_REAL_SAMPLES:-0}" == "1" ]]; then
    python3 "$CORE_FIXTURE_TOOLS/player-core-sample-fixtures.py" .
fi
ls -lh *.mkv
