#!/usr/bin/env python3
"""Verify archived bytes and inventory links without accessing original files."""
from pathlib import Path
import hashlib
import json
import re

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'MANIFEST.json').read_text())
expected = set()
for item in manifest['files']:
    rel = Path(item['archive'])
    assert not rel.is_absolute() and '..' not in rel.parts, rel
    assert rel.parts[0] == 'snapshot', rel
    assert str(rel) not in expected, rel
    expected.add(str(rel))
    path = root / rel
    assert not path.is_symlink(), rel
    data = path.read_bytes()
    assert len(data) == item['archiveBytes'], rel
    assert hashlib.sha256(data).hexdigest() == item['archiveSha256'], rel
    if not item['redactions']:
        assert item['archiveSha256'] == item['sourceSha256'], rel
    if path.suffix == '.json':
        json.loads(data.decode('utf-8-sig'))

actual = {str(p.relative_to(root)) for p in (root / 'snapshot').rglob('*') if p.is_file()}
assert expected == actual, {'missing': sorted(expected - actual), 'extra': sorted(actual - expected)}
links = re.findall(r'\[查看\]\(([^)]+)\)', (root / 'INVENTORY.md').read_text())
assert len(links) == len(expected) and set(links) == expected
assert not (root / '.github' / 'workflows').exists()
documents = sum(x['kind'] == 'document' for x in manifest['files'])
redacted = sum(bool(x['redactions']) for x in manifest['files'])
print(f'PASS: {len(expected)} files; {documents} documents; {redacted} redacted files; all hashes and inventory links verified.')
