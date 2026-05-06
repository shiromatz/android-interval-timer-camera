# LongIntervalCamera

Androidスマートフォンを長期観測用のインターバル静止画カメラとして使うための個人向けアプリです。

Google Play配布ではなく、手元の端末へAPKを直接インストールして使うことを前提にしています。指定した開始日時から終了日時まで、指定間隔でアウトカメラ撮影を行い、JPEG画像とCSVログをセッション単位で保存します。

## 主な機能

- アウトカメラのみを使ったJPEG撮影
- 開始日時、終了日時、撮影間隔の指定
- 分単位、時間単位のインターバル設定
- セッションごとの保存フォルダ作成
- 撮影成功、失敗、スキップ結果のCSVログ保存
- Foreground Serviceと通知による長時間撮影
- 一時停止、再開、停止
- 画面暗転モード
- 端末再起動後の未完了セッション検出
- 保存済み画像の直接表示、共有、削除

## 対象環境

- Android 8.0以上
- アウトカメラ搭載端末
- Kotlin / Android Gradle Plugin
- CameraX

開発と動作確認はPixel 6aを主対象にしています。

## ビルド

Android SDKが利用できる環境で以下を実行します。

```powershell
.\gradlew.bat :app:assembleDebug
```

テストとlintを含めて確認する場合:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
```

生成されるAPK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## インストール

USBデバッグを有効にした端末を接続し、ADBでインストールします。

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

端末が認識されているか確認する場合:

```powershell
adb devices -l
```

## 使い方

1. アプリを起動します。
2. 撮影開始日時、撮影終了日時、撮影間隔を設定します。
3. 必要に応じて保存先フォルダ名、JPEG品質、バッテリー下限、空き容量下限を設定します。
4. `撮影開始` を押します。
5. 撮影中は通知から状態を確認できます。
6. 画面表示を抑えたい場合は黒画面表示を使います。

撮影後は `最新画像を開く` で最新JPEGを直接開けます。任意のファイルを操作したい場合は `保存フォルダを開く` から、各ファイルの `開く`、`共有`、`削除` を使います。

## 保存先

撮影画像は共有Pictures配下に保存します。

```text
Pictures/LongIntervalCamera/{session_id}/
```

各セッションフォルダにはJPEG画像が保存されます。Google Filesなどのファイル管理アプリからも参照できます。CSVログはアプリ内の `ログ表示` から確認できます。

画像ファイル名:

```text
yyyyMMdd_HHmmss_SSS.jpg
```

ログファイル:

```text
capture_log.csv
```

## 注意

- 端末の省電力設定やDozeにより、撮影時刻が遅れる場合があります。
- 長時間運用では充電しながら使うことを推奨します。
- 安定運用のため、必要に応じてバッテリー最適化から除外してください。
- 本アプリは動画生成を行いません。タイムラプス動画化はPC等の外部ツールで行ってください。

## ライセンス

このプロジェクトはMIT Licenseで公開しています。詳細は [LICENSE](LICENSE) を参照してください。
