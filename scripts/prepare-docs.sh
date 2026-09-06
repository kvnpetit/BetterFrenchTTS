#!/usr/bin/env bash
set -euo pipefail

# Usage: bash scripts/prepare-docs.sh publication-directory new-deployment-directory [base-url]
python3 - "${1:?Pass the publication directory}" "${2:?Pass a new deployment directory}" "${3:-https://kvnpetit.github.io/better-french-tts/}" <<'PY'
import html
import json
import re
import shutil
import sys
from pathlib import Path
from urllib.parse import urljoin

publication = Path(sys.argv[1]).resolve(strict=True)
destination = Path(sys.argv[2]).resolve()
base_url = sys.argv[3]
if destination.exists():
    raise SystemExit('Use a new deployment directory; existing data will not be overwritten')
if destination.is_relative_to(publication):
    raise SystemExit('Deployment directory must be outside the publication directory')
current = json.loads((publication / 'version.json').read_text(encoding='utf-8'))['version']
if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z.-]*', current):
    raise SystemExit('Invalid current version metadata')
publications = [(current, publication, '')]
if (publication / 'older').is_dir():
    publications += [(p.name, p, f'older/{p.name}/') for p in sorted((publication / 'older').iterdir()) if p.is_dir()]
for version, source, _ in publications:
    if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z.-]*', version):
        raise SystemExit(f'Invalid archive name: {version}')
    if json.loads((source / 'version.json').read_text(encoding='utf-8'))['version'] != version:
        raise SystemExit(f'Archive metadata mismatch: {version}')
shutil.copytree(publication, destination)
attribute = re.compile(r'(?P<attr>href|src)="(?P<url>[^"]+)"')
for version, source, relative_root in publications:
    archive = destination / '_versions' / version
    shutil.copytree(source, archive, ignore=shutil.ignore_patterns('older', '_versions'))
    for page in archive.rglob('*.html'):
        page_url = urljoin(base_url, relative_root + page.relative_to(archive).as_posix())

        def archive_link(match):
            url = html.unescape(match['url']).replace('\\', '/')
            if re.match(r'^(?:[a-zA-Z][a-zA-Z0-9+.-]*:|/|#)', url):
                return match[0]
            resolved = urljoin(page_url, url)
            if not resolved.startswith(base_url):
                raise ValueError(f'Unexpected documentation link: {resolved}')
            target = resolved[len(base_url):]
            if target.startswith('older/'):
                target = '_versions/' + target[len('older/'):]
            else:
                target = f'_versions/{current}/' + target
            return f'{match["attr"]}="{html.escape(base_url + target, quote=True)}"'

        page.write_text(attribute.sub(archive_link, page.read_text(encoding='utf-8')), encoding='utf-8')
# Dokka on Windows emits backslashes in some navigation URLs.
for page in destination.rglob('*.html'):
    page.write_text(attribute.sub(lambda m: m[0].replace('\\', '/'), page.read_text(encoding='utf-8')), encoding='utf-8')
(destination / '.nojekyll').touch()
print(f'Prepared documentation and {len(publications)} navigable archives in {destination}')
PY
