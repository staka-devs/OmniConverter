#!/usr/bin/env bash
# ホストから呼ぶ。イメージを用意してコンテナ内で build.sh を実行する。使い方: native/run.sh ffmpeg
set -euo pipefail

on_git_bash() { command -v cygpath >/dev/null; }

here=$(cd "$(dirname "$0")" && pwd)
if on_git_bash; then
    here=$(cygpath -w "$here")
    export MSYS_NO_PATHCONV=1
fi
docker build -q -t omniconverter-native "$here" >/dev/null
docker run --rm -v "$here":/native omniconverter-native "$@"
