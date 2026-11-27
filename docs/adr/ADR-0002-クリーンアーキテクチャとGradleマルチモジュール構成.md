# ADR-0002 クリーンアーキテクチャと Gradle マルチモジュール構成

- ステータス: 承認
- 日付: 2026-11-27

## 文脈
エンジンは今後も増える。変換の規則や画面を、特定のエンジンや Android の API から切り離しておきたい。
依存方向の約束は、レビューだけでは守り続けられない。

## 決定
Domain、Application、Infrastructure、Presentation の 4 層に分け、層ごとに Gradle モジュールを作る。

| モジュール | 種類 | 依存してよい先 |
|---|---|---|
| `:domain` | Kotlin/JVM | なし |
| `:application` | Kotlin/JVM | `:domain` |
| `:infrastructure:core` | Android ライブラリ | `:domain` `:application` |
| `:infrastructure:engine-*` | Android ライブラリ | `:domain` `:application` `:infrastructure:core` |
| `:presentation` | Android ライブラリ | `:domain` `:application` |
| `:app` | Android アプリ | すべて |

- `:domain` と `:application` は純粋な Kotlin/JVM モジュールにする。Android の API は使えない。
- `:domain` が依存してよい外部ライブラリは kotlinx-coroutines だけとする。
- `:app` は合成ルートとし、Infrastructure の実装をポートに結びつける唯一の場所とする。
- 依存方向はルートの `checkLayerDependencies` タスクで検査し、`check` に組み込む。違反があるとビルドが失敗する。
- 共通のビルド設定は `build-logic` の convention plugin にまとめる。

## 結果
Presentation から Infrastructure を参照するコードはビルドを通らない。
Domain と Application は JVM 上で単体テストでき、端末が要らない。
モジュール数が多いため、設定の重複は convention plugin で吸収する。
