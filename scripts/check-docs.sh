#!/usr/bin/env bash
set -euo pipefail

# Usage: bash scripts/check-docs.sh [publication-directory] [version] [older-version]
# Python's standard library handles JSON and HTML; no pip dependencies are needed.
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
python3 - "${1:-$repo_root/better-french-tts/build/dokka/html}" "${2:-dev}" "${3:-}" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve(strict=True)
version, older = sys.argv[2:]
if json.loads((root / 'version.json').read_text(encoding='utf-8'))['version'] != version:
    raise SystemExit('Unexpected documentation version')
index = (root / 'index.html').read_text(encoding='utf-8')
for required in ('Documentation versions', 'DEVELOPER.md', '_versions/2.0.0/', 'Search'):
    if required not in index:
        raise SystemExit(f'Missing overview feature: {required}')
pages = list((root / 'better-french-tts').rglob('*.html'))
if not any('github.com/kvnpetit/better-french-tts/tree/' in p.read_text(encoding='utf-8') for p in pages):
    raise SystemExit('Missing API source links')
if older:
    old_index = root / 'older' / older / 'index.html'
    if not old_index.is_file() or f'older/{older}/index.html' not in index.replace('\\', '/'):
        raise SystemExit('Missing older publication or version menu link')
    for page in pages:
        if 'aria-label="Select version"' not in page.read_text(encoding='utf-8'):
            raise SystemExit(f'Missing version menu: {page}')
    if version not in old_index.read_text(encoding='utf-8'):
        raise SystemExit('Missing return navigation')
print(f'Dokka checks passed: {version} ({len(pages)} API pages)')
PY
