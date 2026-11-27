# OmniConverter

オフラインで動く、Android 向けのファイル変換アプリです。
FFmpeg、ImageMagick、libheif、libarchive、MuPDF、pandoc をアプリに内蔵しています。

## 特長

- ネットワーク不要
- 複数ファイルの一括変換
- エンジンへ直接コマンドを渡す上級モード

## 対応環境

Android 10 以降、arm64-v8a 端末

## ビルド

Android SDK はコンテナに入っています。手元に SDK を用意する必要はありません。

```sh
docker build -t omniconverter-android .devcontainer
docker run --rm -v "$PWD":/workspace -v omniconverter-gradle:/root/.gradle -w /workspace omniconverter-android ./gradlew build lint
```

VS Code などで Dev Container として開いても同じ環境になります。

ネイティブエンジンのビルド手順は `native/` を参照してください。

## 設計

設計判断は [docs/adr](docs/adr) にまとめています。

## ライセンス

AGPL-3.0
