"""Local-only playback fixtures, including per-media header assertions and HTTP Range."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
import json, os, re
import sys
os.chdir(sys.argv[1])
class Handler(SimpleHTTPRequestHandler):
 def do_GET(self):
  path=Path(self.translate_path(self.path))
  token=self.headers.get('X-Core-Media')
  print(json.dumps({'path':self.path,'media':token,'range':self.headers.get('Range')}),flush=True)
  if self.path.endswith(('.mkv','.mp4')) and token!=path.name:
   self.send_error(403,'Wrong per-item header');return
  if not path.is_file():self.send_error(404);return
  size=path.stat().st_size
  start,end=0,size-1
  match=re.fullmatch(r'bytes=(\d+)-(\d*)',self.headers.get('Range',''))
  if match:
   start=int(match[1]);end=min(int(match[2]) if match[2] else size-1,size-1)
   if start>=size:self.send_error(416);return
  self.send_response(206 if match else 200)
  self.send_header('Content-Type',self.guess_type(str(path)))
  self.send_header('Accept-Ranges','bytes')
  self.send_header('Content-Length',str(end-start+1))
  if match:self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
  self.end_headers()
  try:
   with path.open('rb') as f:
    f.seek(start);left=end-start+1
    while left:
     data=f.read(min(65536,left));self.wfile.write(data);left-=len(data)
  except (BrokenPipeError,ConnectionResetError):pass
ThreadingHTTPServer(('127.0.0.1',9980),Handler).serve_forever()
