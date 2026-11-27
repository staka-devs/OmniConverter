#!/usr/bin/env bash
# ホストから呼ぶ。イメージを用意してコンテナ内で build.sh を実行する。使い方: native/run.sh ffmpeg
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
# Windows の Git Bash ではパスを変換させない
command -v cygpath >/dev/null && { here=$(cygpath -w "$here"); export MSYS_NO_PATHCONV=1; }
docker build -q -t omniconverter-native "$here" >/dev/null
docker run --rm -v "$here":/native omniconverter-native "$@"
