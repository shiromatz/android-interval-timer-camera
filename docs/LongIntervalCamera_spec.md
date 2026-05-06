# Android長期インターバル撮影アプリ 仕様書

## 1. 目的

Androidスマートフォンを、長期無人観測用のインターバル撮影カメラとして使う。

通常のタイムラプス動画作成アプリではなく、以下を重視する。

- 1か月などの長期期間にわたり、指定間隔で静止画を撮影する
- 撮影はアウトカメラのみ
- 画面表示を最小化し、消費電力を抑える
- 撮影成功・失敗をログに残す
- APKを手元の端末に直接インストールして使用する
- Google Play公開、ストア審査、広告、課金、クラウド同期は対象外
- JPEG群からの動画生成は本アプリの対象外とし、PC等の外部ツールで後処理する

---

## 2. 対象環境

### 2.1 開発環境

- Android Studio 最新安定版
- Kotlin
- Gradle Kotlin DSL
- minSdk: 26 以上
- targetSdk: 最新安定版でよい
- Jetpack Compose または通常の XML UI のどちらでも可
- カメラ実装は CameraX を使用する

### 2.2 実行環境

- ユーザー所有のAndroidスマートフォン1台
- アウトカメラ搭載端末
- 充電しながらの長期運用を想定
- APK直接インストール
- Google Play公開なし

---

## 3. 基本要件

### 3.1 必須機能

アプリは以下を満たすこと。

1. アウトカメラのみを使用する
2. 指定した開始日時から撮影を開始できる
3. 指定した終了日時まで撮影を継続できる
4. 撮影間隔を指定できる
5. 例として「1か月間、1時間間隔」の設定が可能であること
6. 静止画をJPEGとして保存する
7. 撮影中は画面表示を最小化する
8. 撮影中は常駐通知を表示する
9. 撮影成功・失敗をログに残す
10. 撮影セッションの状態を永続化し、アプリ再起動後に状態を確認できる
11. 端末再起動後、自動再開は必須ではないが、未完了セッションを検出できること
12. JPEGファイル名は撮影日時順に辞書順ソートできる形式にする
13. 各セッションごとに保存フォルダを分ける

---

## 4. 非対象

以下は初期版では実装しない。

- Google Play公開対応
- 広告
- 課金
- ユーザーアカウント
- クラウドアップロード
- 動画生成
- インカメラ撮影
- 複数カメラ選択
- AI解析
- 顔認識
- モーション検知
- Web管理画面
- 遠隔操作
- バックグラウンドでユーザーに見えない隠し撮影

---

## 5. Android制約に基づく設計方針

### 5.1 Foreground Service を使用する

長期撮影中は、アプリが明示的に起動した foreground service を維持する。

完全に不可視のバックグラウンド撮影ではなく、ユーザーが明示的に開始した撮影セッションとして扱う。

### 5.2 Doze 対応

画面オフ・未操作・未充電などの条件では Android の Doze により通常のアラームやジョブは遅延される。

1時間間隔撮影では秒単位の厳密性は不要なので、初期版では以下の方針とする。

- 基本は `AlarmManager` で次回撮影を予約する
- 撮影時刻の厳密性より、長期継続性を優先する
- Dozeによる多少の遅延は許容する
- ログには予定時刻と実際の撮影時刻の両方を保存する

### 5.3 再起動時の扱い

Androidのアラームは端末が再起動されるとクリアされる。

そのため、初期版では以下を実装する。

- セッション状態は永続化する
- 端末再起動後にアプリを開くと、未完了セッションを検出する
- 必要であれば「再開」ボタンで再予約する
- 自動再開は任意機能とする

---

## 6. アプリ名

仮称:

```text
LongIntervalCamera
```

パッケージ名:

```text
com.example.longintervalcamera
```

---

## 7. 画面仕様

### 7.1 メイン画面

メイン画面には以下を表示する。

#### 入力項目

- 撮影開始日時
- 撮影終了日時
- 撮影間隔
  - 数値
  - 単位: 分 / 時間
- 保存先フォルダ名
- JPEG品質
  - 初期値: 90
- 解像度
  - 初期値: 端末標準
  - 初期版では固定でも可
- 最低バッテリー残量
  - 初期値: 10%
- 最低空き容量
  - 初期値: 1GB
- 画面暗転モード
  - ON/OFF
  - 初期値: ON

#### 表示項目

- 現在のセッション状態
  - 未設定
  - 待機中
  - 撮影中
  - 一時停止中
  - 完了
  - エラー停止
- 次回撮影予定時刻
- 撮影済み枚数
- 最後の撮影時刻
- 最後の撮影結果
- 保存先パス
- バッテリー残量
- 空き容量

#### ボタン

- 撮影セッション開始
- 一時停止
- 再開
- 停止
- 黒画面表示
- ログ表示
- 保存フォルダを開く

---

## 8. 黒画面仕様

### 8.1 BlackoutActivity

撮影中の画面表示を最小化するため、黒画面専用 Activity を用意する。

要件:

- 背景は完全な黒
- 画面上には最小限の状態表示のみ
  - 例: 小さく「撮影中」「次回 14:00」程度
- 画面輝度を最低にする
- 誤操作防止のため、終了操作は長押しまたは二段階操作にする

注意:

- 画面を完全に消すかどうかは端末・OS挙動に依存する
- 画面が消えても foreground service とアラーム予約は維持する
- 端末の省電力設定によっては停止する可能性があるため、初回起動時にバッテリー最適化除外を案内する

---

## 9. 撮影仕様

### 9.1 カメラ

- CameraX を使用する
- `CameraSelector.DEFAULT_BACK_CAMERA` を使う
- インカメラは使わない
- プレビュー表示は必須ではない
- 撮影時のみカメラをバインドし、撮影後に解放する設計を優先する

### 9.2 撮影処理

1回の撮影処理は以下の順序で行う。

1. セッション状態を確認する
2. 現在時刻が終了日時を過ぎていれば完了にする
3. バッテリー残量を確認する
4. 空き容量を確認する
5. カメラを初期化する
6. アウトカメラを選択する
7. JPEGファイル名を生成する
8. `ImageCapture.takePicture()` で撮影する
9. 成功または失敗をログに記録する
10. カメラを解放する
11. 次回撮影時刻を計算する
12. 次回アラームを予約する

---

## 10. ファイル保存仕様

### 10.1 保存先

共有Pictures配下に保存する。

例:

```text
Pictures/LongIntervalCamera/{session_id}/
```

Android 10以降のscoped storageに対応するため、共有PicturesへのJPEG保存はMediaStore経由で行う。

### 10.2 ファイル名

ファイル名は以下の形式とする。

```text
yyyyMMdd_HHmmss_SSS.jpg
```

例:

```text
20260505_140000_123.jpg
```

この形式により、ファイル名の辞書順ソートが撮影日時順と一致する。

### 10.3 セッションID

セッションIDは開始日時ベースで生成する。

```text
session_yyyyMMdd_HHmmss
```

例:

```text
session_20260505_120000
```

---

## 11. 動画生成について

本アプリでは動画生成を行わない。

多数のJPEGファイルからタイムラプス動画を作成する処理は、PCや他のアプリで後処理する。

推奨する前提:

- JPEGファイルはセッション単位で1フォルダに保存する
- JPEGファイル名は `yyyyMMdd_HHmmss_SSS.jpg` 形式にする
- `capture_log.csv` に `scheduled_time`, `actual_time`, `file_path` を記録する

PCで動画化する場合の例:

```bash
printf "file '%s'\n" *.jpg > files.txt
ffmpeg -f concat -safe 0 -i files.txt -r 24 -c:v libx264 -pix_fmt yuv420p timelapse.mp4
```

---

## 12. ログ仕様

### 12.1 ログ形式

CSVまたはJSON Linesで保存する。初期版はCSVでよい。

ファイル名:

```text
capture_log.csv
```

保存先:

```text
アプリ管理領域の capture_log.csv
```

ログはアプリのログ表示から確認できる。JPEG画像の保存先は共有Pictures配下とする。

### 12.2 ログ項目

CSV列:

```text
session_id
scheduled_time
actual_time
result
file_path
battery_percent
free_space_bytes
error_type
error_message
```

### 12.3 result

```text
SUCCESS
SKIPPED_LOW_BATTERY
SKIPPED_LOW_STORAGE
FAILED_CAMERA_INIT
FAILED_CAPTURE
FAILED_SAVE
SESSION_COMPLETED
SESSION_STOPPED_BY_USER
```

---

## 13. セッション状態管理

### 13.1 保存方式

初期版では `DataStore` または `SharedPreferences` を使う。

複雑な履歴管理が必要になったら Room に移行する。

### 13.2 SessionConfig

Kotlin data class の例:

```kotlin
data class SessionConfig(
    val sessionId: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val intervalMillis: Long,
    val saveDirectory: String,
    val jpegQuality: Int,
    val minBatteryPercent: Int,
    val minFreeSpaceBytes: Long,
    val blackoutModeEnabled: Boolean,
    val status: SessionStatus,
    val nextCaptureTimeMillis: Long?,
    val capturedCount: Int,
    val lastCaptureTimeMillis: Long?,
    val lastResult: String?
)
```

### 13.3 SessionStatus

```kotlin
enum class SessionStatus {
    NOT_CONFIGURED,
    WAITING,
    RUNNING,
    PAUSED,
    COMPLETED,
    ERROR,
    STOPPED
}
```

---

## 14. スケジューリング仕様

### 14.1 基本方針

- 各撮影後に次回撮影を1回だけ予約する
- 繰り返しアラームは使わない
- 予定時刻を永続化する
- アプリ再起動時に未完了セッションを検出する

### 14.2 次回撮影時刻

次回撮影時刻は、原則として前回予定時刻に interval を足す。

```text
nextScheduledTime = previousScheduledTime + intervalMillis
```

ただし、端末スリープやDozeにより実際の撮影時刻が遅れた場合でも、予定時刻ベースで計算する。

例:

- 予定: 13:00
- 実撮影: 13:07
- 間隔: 1時間
- 次回予定: 14:00

これにより、遅延が累積しない。

### 14.3 遅延が大きい場合

現在時刻が次回予定時刻を大きく過ぎている場合、過去分を連続撮影で取り戻さない。

例:

- 予定: 13:00, 14:00, 15:00
- 端末停止後、復帰: 16:30

この場合、過去分をまとめ撮りしない。
次回は 17:00 に設定する。

---

## 15. 権限仕様

### 15.1 必須権限

Manifestに以下を設定する。

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

必要に応じて以下も検討する。

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

ただし、1時間間隔の観測用途では秒単位の厳密性は不要なので、`SCHEDULE_EXACT_ALARM` を必須にしない設計を優先する。

### 15.2 実行時権限

初回起動時に以下を要求する。

- カメラ権限
- 通知権限
- 必要に応じてバッテリー最適化除外の案内

---

## 16. 通知仕様

撮影セッション中は常駐通知を表示する。

通知タイトル:

```text
LongIntervalCamera 撮影中
```

通知本文:

```text
次回撮影: 2026-05-05 14:00 / 撮影済み: 12枚
```

通知アクション:

- 一時停止
- 停止
- アプリを開く

---

## 17. エラー処理

### 17.1 カメラ初期化失敗

- ログに `FAILED_CAMERA_INIT` を記録
- 1回だけ短時間後にリトライしてよい
- 連続失敗回数が3回以上なら `ERROR` 状態にする

### 17.2 撮影失敗

- ログに `FAILED_CAPTURE` を記録
- 次回予定は通常どおり予約する
- 失敗分を直後に取り戻さない

### 17.3 保存失敗

- ログに `FAILED_SAVE` を記録
- 空き容量を再確認する
- 空き容量不足ならセッションを停止する

### 17.4 バッテリー不足

- 最低バッテリー残量を下回った場合、撮影をスキップする
- ログに `SKIPPED_LOW_BATTERY` を記録
- 次回予定は通常どおり予約する

### 17.5 空き容量不足

- 最低空き容量を下回った場合、撮影をスキップする
- ログに `SKIPPED_LOW_STORAGE` を記録
- 以後の撮影継続は設定で選べる
- 初期版では `ERROR` 停止でよい

---

## 18. 省電力仕様

### 18.1 基本方針

- 撮影時だけカメラを開く
- 撮影後はカメラを解放する
- プレビューは原則使わない
- 画面は黒画面または消灯
- 充電しながらの運用を推奨する

### 18.2 初回案内

初回起動時に以下を表示する。

```text
このアプリは長時間の定期撮影を行います。
端末の省電力機能により、画面オフ中や長時間放置中に撮影が遅延または停止する場合があります。
安定運用のため、バッテリー最適化から除外し、充電しながら使用してください。
```

---

## 19. UI文言

### 19.1 開始確認

```text
撮影セッションを開始します。

開始: 2026-05-05 12:00
終了: 2026-06-05 12:00
間隔: 1時間
推定撮影枚数: 744枚

撮影中は通知が表示されます。
端末の省電力設定により撮影時刻が遅れる場合があります。
```

### 19.2 停止確認

```text
撮影セッションを停止しますか？
停止後も撮影済み画像とログは保存されます。
```

---

## 20. 推定撮影枚数

開始日時、終了日時、間隔から推定枚数を計算する。

```text
estimatedCount = floor((endTime - startTime) / interval) + 1
```

例:

```text
30日間、1時間間隔 = 721枚
```

開始時刻と終了時刻の両方で撮影するため、単純な 30 × 24 = 720 より1枚多くなる場合がある。

---

## 21. テスト仕様

### 21.1 単体テスト

以下をテストする。

- 次回撮影時刻計算
- 推定撮影枚数計算
- 終了判定
- 低バッテリー判定
- 低ストレージ判定
- ファイル名生成
- セッション状態遷移

### 21.2 実機テスト

最低限、以下を行う。

#### 短時間テスト

- 間隔: 1分
- 期間: 10分
- 期待結果: 10〜11枚程度撮影される

#### 画面オフテスト

- 間隔: 5分
- 期間: 1時間
- 画面オフ
- 期待結果: 撮影が継続する、または遅延がログに残る

#### 充電長時間テスト

- 間隔: 1時間
- 期間: 24時間
- 充電接続
- 期待結果: 24〜25枚撮影される

#### 再起動テスト

- セッション中に端末を再起動
- アプリ起動
- 未完了セッションを検出
- 再開操作で次回撮影を予約できる

---

## 22. 受け入れ基準

初期版の完成条件は以下。

1. APKをビルドできる
2. Android端末に直接インストールできる
3. カメラ権限を要求できる
4. アウトカメラでJPEG撮影できる
5. 撮影間隔を指定できる
6. 開始日時と終了日時を指定できる
7. 1か月・1時間間隔の設定を保存できる
8. 撮影中に常駐通知が出る
9. 黒画面モードを表示できる
10. 撮影画像が保存される
11. 撮影ログが保存される
12. 撮影失敗時にアプリがクラッシュしない
13. 端末再起動後、未完了セッションを検出できる
14. JPEGファイルがファイル名順で撮影日時順に並ぶ
15. 動画生成機能を実装しない

---

## 23. 推奨ディレクトリ構成

```text
app/
  src/main/
    java/com/example/longintervalcamera/
      MainActivity.kt
      BlackoutActivity.kt
      service/
        CaptureForegroundService.kt
      camera/
        CameraCaptureManager.kt
      scheduler/
        CaptureAlarmScheduler.kt
        CaptureAlarmReceiver.kt
      data/
        SessionConfig.kt
        SessionStatus.kt
        SessionRepository.kt
      logging/
        CaptureLogger.kt
      storage/
        ImageStorageManager.kt
      util/
        BatteryUtils.kt
        StorageUtils.kt
        TimeUtils.kt
    AndroidManifest.xml
```

---

## 24. 実装方針詳細

### 24.1 MainActivity

責務:

- 設定入力
- セッション開始
- セッション停止
- 状態表示
- 権限要求
- 黒画面起動

### 24.2 CaptureForegroundService

責務:

- 撮影セッション中の常駐処理
- 通知表示
- 撮影要求の受け取り
- 撮影後の次回予約
- 停止・一時停止処理

### 24.3 CaptureAlarmReceiver

責務:

- AlarmManagerからの通知を受け取る
- ForegroundServiceに撮影要求を渡す

### 24.4 CameraCaptureManager

責務:

- CameraX初期化
- アウトカメラ選択
- ImageCapture実行
- 撮影成功・失敗の結果返却

### 24.5 CaptureAlarmScheduler

責務:

- 次回撮影時刻の予約
- 既存予約のキャンセル
- Doze考慮のアラーム設定

### 24.6 SessionRepository

責務:

- セッション設定の保存
- セッション状態の更新
- アプリ起動時の状態復元

### 24.7 CaptureLogger

責務:

- CSVログ作成
- 各撮影結果の追記
- エラー内容の記録

---

## 25. Codexへの実装指示

以下をCodexに渡す。

```text
Build an Android Kotlin app named LongIntervalCamera.

The app is for personal sideload use only, not for Google Play distribution.

Purpose:
Create a long-duration interval camera app that captures still JPEG images using only the rear camera at a user-defined interval, for example once every hour for one month.

Core requirements:
- Kotlin Android app.
- Use CameraX for camera capture.
- Use only the rear camera.
- Save still images as JPEG files.
- User can configure:
  - start datetime
  - end datetime
  - interval value and unit
  - JPEG quality
  - minimum battery percentage
  - minimum free storage
  - blackout screen mode on/off
- The app must support long sessions such as 1 month with 1 hour interval.
- During a session, run a foreground service with a persistent notification.
- Show notification actions for pause and stop.
- Provide a black screen Activity with minimum brightness for low-power operation.
- Capture images at scheduled intervals using AlarmManager.
- Do not use repeating alarms. Schedule the next capture after each capture.
- Store session state persistently using DataStore or SharedPreferences.
- Store a CSV log for each session.
- Each log row must include:
  session_id, scheduled_time, actual_time, result, file_path, battery_percent, free_space_bytes, error_type, error_message.
- Use CameraX ImageCapture.takePicture().
- Open the camera only when capturing if feasible, then release it after capture.
- If a capture fails, log the failure and continue with the next scheduled capture.
- If storage is too low, stop the session with an error.
- If battery is too low, skip the capture and schedule the next one.
- On app launch, detect unfinished sessions and allow the user to resume scheduling.
- On device reboot, automatic resume is optional, but unfinished session detection is required.
- The app must not crash if the camera is temporarily unavailable.
- Do not implement video generation. The app only captures and stores JPEG files.
- File names must be sortable by capture datetime using yyyyMMdd_HHmmss_SSS.jpg.

Suggested package:
com.example.longintervalcamera

Suggested modules/classes:
- MainActivity
- BlackoutActivity
- CaptureForegroundService
- CaptureAlarmReceiver
- CaptureAlarmScheduler
- CameraCaptureManager
- SessionConfig
- SessionStatus
- SessionRepository
- CaptureLogger
- ImageStorageManager
- BatteryUtils
- StorageUtils
- TimeUtils

Use a simple UI. Functionality and reliability are more important than visual design.
```

---

## 26. 実装上の優先順位

### Phase 1: 最小実用版

- カメラ権限
- アウトカメラ撮影
- JPEG保存
- 手動撮影テスト
- 1分間隔テスト
- CSVログ

### Phase 2: 長期撮影版

- 開始日時・終了日時
- 1時間間隔対応
- Foreground Service
- AlarmManager
- 黒画面
- バッテリー・空き容量チェック

### Phase 3: 安定運用版

- 再起動後の未完了セッション検出
- リトライ制御
- 詳細ログ
- 保存フォルダ表示
- 設定エクスポート

---

## 27. 最終判断

この用途では、動画タイムラプスアプリではなく、長期観測用の静止画インターバル撮影アプリとして設計するべきである。

Google Play公開なしなら、仕様はかなり単純化できる。重要なのは、ストア審査対応ではなく、次の3点である。

1. Foreground Service + 通知で明示的に撮影中であること
2. AlarmManagerで1回ずつ次回撮影を予約すること
3. 撮影成功・失敗・遅延をすべてログに残すこと

この仕様なら、Codexで段階的に実装させる対象として現実的である。
