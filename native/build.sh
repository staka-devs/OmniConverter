#!/usr/bin/env bash
# コンテナ内の入口。使い方: build.sh <レシピ名>...
set -euo pipefail
source /native/lib/common.sh
source /native/versions.sh

# need <レシピ名>: 依存ライブラリを 1 回だけビルドする。レシピか版が変わったら作り直す
need() {
    local stamp="$PREFIX/.built-$1" key
    key=$(cat "/native/recipes/$1.sh" /native/versions.sh /native/lib/common.sh | sha256sum | cut -c1-16)
    [ "$(cat "$stamp" 2>/dev/null)" = "$key" ] && return
    log "build: $1"
    source "/native/recipes/$1.sh"
    echo "$key" > "$stamp"
}

[ $# -gt 0 ] || { echo "使い方: build.sh <レシピ名>... 例: build.sh ffmpeg" >&2; ls /native/recipes; exit 2; }
for recipe in "$@"; do
    log "recipe: $recipe"
    source "/native/recipes/$recipe.sh"
done
