# ネイティブエンジンのビルド

エンジンは Docker コンテナの中でソースからビルドします。設計は ADR-0005 を参照してください。

```sh
native/run.sh selftest
native/run.sh ffmpeg
```

成果物は `native/out/<エンジン>/arm64-v8a/` に出ます。各エンジンモジュールは、ここを jniLibs として取り込みます。

| ファイル | 役割 |
|---|---|
| `Dockerfile` | NDK とビルド用ツールを入れた環境 |
| `versions.sh` | ソースの版と SHA-256 |
| `lib/common.sh` | ツールチェーンの設定と補助関数 |
| `recipes/<名前>.sh` | エンジンごとのビルド手順 |
