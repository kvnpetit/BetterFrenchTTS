#!/usr/bin/env bash
set -euo pipefail

# Usage: bash scripts/check-docs-links.sh deployment-directory [base-url]
python3 - "${1:?Pass the assembled documentation directory}" "${2:-https://kvnpetit.github.io/better-french-tts/}" <<'PY'
import sys
import os
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

root = Path(sys.argv[1]).resolve(strict=True)
base_url = sys.argv[2]
versions = {p.name for p in (root / '_versions').iterdir() if p.is_dir()}
if not versions:
    raise SystemExit('No documentation archives')

class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.urls, self.titles = [], set()
        self.full_page = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        self.full_page |= tag == 'html'
        self.urls.extend(attrs[key] for key in ('href', 'src') if attrs.get(key))
        if attrs.get('role') == 'option' and 'title' in attrs:
            self.titles.add(attrs['title'])

pages = list(root.rglob('*.html'))
broken = []
existing_paths = {}
for page in pages:
    links = Links()
    links.feed(page.read_text(encoding='utf-8'))
    if links.full_page and page.name not in ('navigation.html', 'not-found-version.html') and len(versions) > 1:
        for version in sorted(versions - links.titles):
            broken.append(f'Missing menu version {version}: {page}')
    for url in links.urls:
        url = url.replace('\\', '/')
        if url.startswith(base_url):
            url, base = url[len(base_url):], root
        elif urlsplit(url).scheme or url.startswith(('/', '#')):
            continue
        else:
            base = page.parent
        path = unquote(urlsplit(url).path)
        if path:
            target = os.path.abspath(base / path)
            if target not in existing_paths:
                existing_paths[target] = Path(target).exists()
            if not existing_paths[target]:
                broken.append(f'{page}: {url}')
if broken:
    print('\n'.join(broken[:20]))
    raise SystemExit(f'{len(broken)} documentation navigation failures')
print(f'Verified {len(pages)} HTML pages: {len(versions)} menu versions, no broken file links.')
PY
