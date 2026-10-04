"""Download hash-pinned public regression samples; remux subtitles onto generated video.

Run after player-core-fixtures.sh. Downloaded samples stay outside the repository.
"""
import hashlib
from pathlib import Path
import subprocess
import sys
from urllib.request import urlopen

root = Path(sys.argv[1]).resolve()
sources = root / 'sources'
sources.mkdir(exist_ok=True)
# Source names and SHA-256 also serve as the reproducible provenance manifest.
SAMPLES = [
    ('pgs.mkv', 'PGS/supsample.mkv', 'e6c8f93f57d0371603704d7e7b16933e6c4c5df669da42b42a2a84de881e0f27'),
    ('bluray.sup', 'BluRay/title03_track2.sup', '81119ca0a20d65ffd03765b246b247792995fdac861d432c497f67eef097c7b2'),
    ('vob-large.mkv', 'largeres_vobsub.mkv', 'f7e22df7bf1a32a4cb9cba490d99cb0508920785a4e0f78ade41cba3c9a22830'),
    ('dvb-complete.ts', 'dvbsub/dvbsubtest.ts', '344e29d35c4581942e85ba4061e2721eee78220eb7bbdb55e8bcb1d2f1b719e5'),
]
for name, url, digest in SAMPLES:
    target = sources / name
    if not target.exists():
        with urlopen('https://samples.ffmpeg.org/sub/' + url, timeout=60) as response:
            target.write_bytes(response.read())
    if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
        raise RuntimeError('Sample hash mismatch: ' + name)
    print(name, digest, flush=True)

def ffmpeg(*args):
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', *map(str, args)], cwd=root, check=True)

ffmpeg('-i', sources / 'pgs.mkv', '-map', '0:s:0', '-c:s', 'copy', '-f', 'sup', 'sample.SUP')
ffmpeg('-i', 'base.mp4', '-i', 'sample.SUP', '-i', 'sample.ass', '-map', '0', '-map', '1', '-map', '2', '-c', 'copy', '-metadata:s:s:0', 'language=eng', '-metadata:s:s:1', 'language=zho', '-disposition:s:0', 'default', '-disposition:s:1', '0', 'bitmap.mkv')
ffmpeg('-i', 'base.mp4', '-itsoffset', '-37.790', '-i', sources / 'vob-large.mkv', '-t', '70', '-map', '0', '-map', '1:s:0', '-c', 'copy', '-metadata:s:s:0', 'language=eng', 'dvd.mkv')
# Retain the broadcast subtitle stream and its original composition/ancillary page IDs.
ffmpeg('-i', 'base.mp4', '-ss', '15', '-i', sources / 'dvb-complete.ts', '-t', '70', '-map', '0', '-map', '1:s:0', '-c', 'copy', '-metadata:s:s:0', 'language=eng', '-f', 'mpegts', 'dvb.ts')
