#!/usr/bin/env bash
set -euo pipefail

# Usage: bash scripts/regenerate-docs.sh output-directory [version ...]
output_directory=${1:?Pass an output directory}
shift
mkdir -p -- "$output_directory"
output_root=$(cd -- "$output_directory" && pwd)
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$repo_root"
if (( $# == 0 )); then
    set -- 1.0.0 1.1.0 2.0.0 2.1.0
fi
for version in "$@"; do
    [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid stable version: $version" >&2; exit 1; }
    checkout="$output_root/sources/$version"
    publication="$output_root/versions/$version"
    [[ ! -e $checkout && ! -e $publication ]] || { echo "Snapshot already exists: $version" >&2; exit 1; }
    mkdir -p -- "$checkout"
    git archive "v$version" --output="$output_root/$version.tar"
    tar -xf "$output_root/$version.tar" -C "$checkout"
    sources="$checkout/better-french-tts/src/main/java"
    [[ -d $sources ]] || { echo "Missing sources for v$version" >&2; exit 1; }
    # Native Windows Java needs native paths, even when launched by Git Bash.
    if command -v cygpath >/dev/null 2>&1; then
        sources=$(cygpath -m "$sources")
        publication=$(cygpath -m "$publication")
    fi
    bash ./gradlew :better-french-tts:dokkaGeneratePublicationHtml \
        "-PdokkaVersion=$version" "-PdokkaSourceRef=v$version" \
        -PdokkaIncludes=docs/historical-api.md "-PdokkaSourceDirectory=$sources" \
        "-PdokkaOutputDirectory=$publication" --console=plain
    [[ -f "$output_root/versions/$version/version.json" ]] || { echo "Missing metadata for $version" >&2; exit 1; }
done
