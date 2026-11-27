#!/usr/bin/env bash
# コンテナ内の入口。使い方: build.sh <レシピ名>...
set -euo pipefail
source /native/lib/common.sh
source /native/versions.sh

[ $# -gt 0 ] || { echo "使い方: build.sh <レシピ名>... 例: build.sh ffmpeg" >&2; ls /native/recipes; exit 2; }
for recipe in "$@"; do
    log "recipe: $recipe"
    source "/native/recipes/$recipe.sh"
done
