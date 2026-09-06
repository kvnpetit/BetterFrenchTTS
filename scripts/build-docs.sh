#!/usr/bin/env bash
set -euo pipefail

# Shared read/build/check pipeline. Publication is deliberately a separate CI step.
# Usage: bash scripts/build-docs.sh version source-ref new-work-directory
version=${1:?Pass the documented version}
source_ref=${2:?Pass the source ref}
work_directory=${3:?Pass a new work directory}
[[ $version =~ ^[0-9A-Za-z][0-9A-Za-z.-]*$ ]] || { echo 'Invalid documentation version' >&2; exit 1; }
[[ ! -e $work_directory ]] || { echo 'Work directory already exists' >&2; exit 1; }
mkdir -p -- "$work_directory"
work_root=$(cd -- "$work_directory" && pwd)
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$repo_root"
mkdir -p "$work_root/archive" "$work_root/older"
if [[ -n $(git ls-remote --heads origin gh-pages) ]]; then
    git fetch origin gh-pages:refs/remotes/origin/gh-pages
    if git cat-file -e origin/gh-pages:_versions 2>/dev/null; then
        git archive origin/gh-pages _versions | tar -x -C "$work_root/archive"
        for archived in "$work_root/archive/_versions/"*; do
            [[ -d $archived ]] || continue
            [[ -f $archived/version.json ]] || { echo "Missing archive metadata: $archived" >&2; exit 1; }
            if [[ $(basename "$archived") != "$version" ]]; then
                cp -a "$archived" "$work_root/older/"
            fi
        done
    fi
fi
older_directory="$work_root/older"
publication="$work_root/publication"
if command -v cygpath >/dev/null 2>&1; then
    older_directory=$(cygpath -m "$older_directory")
    publication=$(cygpath -m "$publication")
fi
bash ./gradlew :better-french-tts:dokkaGeneratePublicationHtml \
    "-PdokkaVersion=$version" "-PdokkaSourceRef=$source_ref" \
    "-PdokkaOlderVersionsDir=$older_directory" "-PdokkaOutputDirectory=$publication" --console=plain
bash scripts/check-docs.sh "$work_root/publication" "$version"
bash scripts/prepare-docs.sh "$work_root/publication" "$work_root/deploy"
bash scripts/check-docs-links.sh "$work_root/deploy"
