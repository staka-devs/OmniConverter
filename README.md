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

```sh
./gradlew build
```

ネイティブエンジンのビルド手順は `native/` を参照してください。

## 設計

設計判断は [docs/adr](docs/adr) にまとめています。

## ライセンス

AGPL-3.0
