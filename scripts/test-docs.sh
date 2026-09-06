#!/usr/bin/env bash
set -euo pipefail

# Small deterministic regression fixtures; no network or Gradle build required.
# Usage: bash scripts/test-docs.sh new-work-directory
work_directory=${1:?Pass a new test directory}
[[ ! -e $work_directory ]] || { echo 'Test directory already exists' >&2; exit 1; }
mkdir -p -- "$work_directory"
work_root=$(cd -- "$work_directory" && pwd)
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$repo_root"
python3 - "$work_root" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1]) / 'publication'
for version, directory, latest, previous in (
    ('2.0.0', root, 'index.html', 'older/1.0.0/index.html'),
    ('1.0.0', root / 'older/1.0.0', '../../index.html', 'index.html'),
):
    directory.mkdir(parents=True)
    (directory / 'version.json').write_text(json.dumps({'version': version}), encoding='utf-8')
    menu = f'<a role="option" title="2.0.0" href="{latest}">2.0.0</a><a role="option" title="1.0.0" href="{previous}">1.0.0</a>'
    (directory / 'index.html').write_text('<html>' + menu + '</html>', encoding='utf-8')
PY
expect_failure() {
    if "$@"; then
        echo "Expected failure: $*" >&2
        exit 1
    fi
}
bash scripts/prepare-docs.sh "$work_root/publication" "$work_root/deploy"
bash scripts/check-docs-links.sh "$work_root/deploy"
expect_failure bash scripts/prepare-docs.sh "$work_root/publication" "$work_root/deploy"
expect_failure bash scripts/prepare-docs.sh "$work_root/publication" "$work_root/publication/nested"
python3 - "$work_root" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
(root / 'publication/version.json').write_text(json.dumps({'version': '../escape'}), encoding='utf-8')
with (root / 'deploy/index.html').open('a', encoding='utf-8') as page:
    page.write('<a href="missing.html">broken</a>')
PY
expect_failure bash scripts/prepare-docs.sh "$work_root/publication" "$work_root/invalid"
expect_failure bash scripts/check-docs-links.sh "$work_root/deploy"
echo 'Documentation regression tests passed (valid site and four rejected failure cases).'
