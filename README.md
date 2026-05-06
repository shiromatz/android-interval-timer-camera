# LongIntervalCamera

Androidスマートフォンを長期観測用のインターバル静止画カメラとして使うための個人向けアプリです。

Google Play配布ではなく、手元の端末へAPKを直接インストールして使うことを前提にしています。指定した開始日時から終了日時まで、指定間隔でアウトカメラ撮影を行い、JPEG画像とCSVログをセッション単位で保存します。

## すぐ試す

1. [GitHub Releases](https://github.com/shiromatz/android-interval-timer-camera/releases/latest) を開きます。
2. 最新リリースの `LongIntervalCamera-release.apk` をAndroid端末へダウンロードします。
3. ダウンロードしたAPKを開いてインストールします。
4. 端末で提供元不明アプリの許可を求められた場合は、このAPKのインストールを許可します。

PCにダウンロードしてADBでインストールする場合:

```powershell
adb install -r LongIntervalCamera-release.apk
```

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

開発と動作確認はPixel 6aを主対象にしています。

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

## 開発者向け

### ビルド

開発環境ではKotlin、Android Gradle Plugin、CameraXを使用します。

Android SDKが利用できる環境でdebug APKを生成します。

```powershell
.\gradlew.bat :app:assembleDebug
```

テストとlintを含めて確認する場合:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
```

生成されるdebug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

公開配布用のrelease APKは、release署名用の環境変数を設定した状態で生成します。

```powershell
.\gradlew.bat :app:assembleRelease
```

生成されるrelease APK:

```text
app/build/outputs/apk/release/app-release.apk
```

公開用の `applicationId` は `com.shiromatz.longintervalcamera` です。

### ローカルビルドしたAPKをインストール

USBデバッグを有効にした端末を接続し、ADBでインストールします。

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

端末が認識されているか確認する場合:

```powershell
adb devices -l
```

### リリース

GitHub Releases向けの署名済みAPKは、`v*` タグをpushするとGitHub Actionsで自動生成されます。

事前にGitHub repository secretsへ以下を登録してください。

```text
RELEASE_KEYSTORE_BASE64
RELEASE_KEYSTORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

`RELEASE_KEYSTORE_BASE64` はrelease keystoreをBase64化した値です。PowerShellでは以下で作成できます。

```powershell
New-Item -ItemType Directory -Force .release
keytool -genkeypair -v -keystore .release\longintervalcamera-release.jks -alias longintervalcamera -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LongIntervalCamera, O=shiromatz, C=JP"
```

`keytool` はJDKまたはAndroid Studioに含まれます。`RELEASE_KEY_ALIAS` には `longintervalcamera` を設定します。`RELEASE_KEYSTORE_PASSWORD` と `RELEASE_KEY_PASSWORD` には、keystore作成時に入力したパスワードを設定します。

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".release\longintervalcamera-release.jks"))
```

タグをpushすると、GitHub Releaseに `LongIntervalCamera-release.apk` と `LongIntervalCamera-release.apk.sha256` が添付されます。

```powershell
git tag v1.0.0
git push origin v1.0.0
```

## ライセンス

このプロジェクトはMIT Licenseで公開しています。詳細は [LICENSE](LICENSE) を参照してください。
