# LongIntervalCamera

English | [日本語](README.ja.md)

LongIntervalCamera is a personal Android app that turns an Android phone into a long-term interval still-image camera.

It is distributed as a sideload APK rather than through Google Play. From a specified start date/time to an end date/time, it captures JPEG images with the rear camera at a configured interval and stores images and CSV logs by session.

## Try It Quickly

1. Open [GitHub Releases](https://github.com/shiromatz/android-interval-timer-camera/releases/latest).
2. Download the latest `LongIntervalCamera-release.apk` to your Android device.
3. Open the downloaded APK and install it.
4. If Android asks for permission to install an unknown app, allow installation for this APK.

If you downloaded the APK to a PC and want to install it with ADB:

```powershell
adb install -r LongIntervalCamera-release.apk
```

## Features

- JPEG capture with the rear camera only
- Configurable start date/time, end date/time, and capture interval
- Minute-based and hour-based interval settings
- Session-specific save folders
- CSV logging for capture success, failure, and skipped captures
- Long-running capture via foreground service and notification
- Pause, resume, and stop controls
- Blackout mode
- Detection of unfinished sessions after device reboot
- Open, share, and delete saved images

## Requirements

- Android 8.0 or later
- Device with a rear camera

Development and testing primarily target Pixel 6a.

## Usage

1. Open the app.
2. Set the capture start date/time, end date/time, and interval.
3. Configure the save folder name, JPEG quality, minimum battery level, and minimum free space as needed.
4. Tap `Start capture`.
5. While capture is active, status is available from the notification.
6. Use blackout mode when you want to reduce screen output.

After capture, use `Open latest image` to open the latest JPEG directly. To work with any saved file, use `Open save folder`, then `Open`, `Share`, or `Delete` for each file.

## Storage

Captured images are stored under shared Pictures.

```text
Pictures/LongIntervalCamera/{session_id}/
```

Each session folder contains JPEG images. The files are also visible from file manager apps such as Google Files. CSV logs are available from `Show log` inside the app.

Image filename:

```text
yyyyMMdd_HHmmss_SSS.jpg
```

Log filename:

```text
capture_log.csv
```

## Notes

- Device power-saving settings and Doze may delay captures.
- For long-running operation, keep the device charging.
- For stable operation, exclude this app from battery optimization if needed.
- This app does not create videos. Use an external PC tool if you want to turn captured images into a timelapse video.

## For Developers

### Build

The development environment uses Kotlin, Android Gradle Plugin, and CameraX.

Run the following in an environment with Android SDK available to build a debug APK.

```powershell
.\gradlew.bat :app:assembleDebug
```

To verify with tests and lint:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
```

Generated debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The release APK for public distribution requires release signing environment variables.

```powershell
.\gradlew.bat :app:assembleRelease
```

Generated release APK:

```text
app/build/outputs/apk/release/app-release.apk
```

The public `applicationId` is `com.shiromatz.longintervalcamera`.

### Install a Locally Built APK

Enable USB debugging, connect an Android device, and install with ADB.

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

To confirm that the device is detected:

```powershell
adb devices -l
```

### Release

Signed APKs for GitHub Releases are generated automatically when a `v*` tag is pushed.

Register the following GitHub repository secrets first.

```text
RELEASE_KEYSTORE_BASE64
RELEASE_KEYSTORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

`RELEASE_KEYSTORE_BASE64` is the Base64-encoded release keystore. You can create it in PowerShell as follows.

```powershell
New-Item -ItemType Directory -Force .release
keytool -genkeypair -v -keystore .release\longintervalcamera-release.jks -alias longintervalcamera -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LongIntervalCamera, O=shiromatz, C=JP"
```

`keytool` is included in the JDK and Android Studio. Set `RELEASE_KEY_ALIAS` to `longintervalcamera`. Set `RELEASE_KEYSTORE_PASSWORD` and `RELEASE_KEY_PASSWORD` to the passwords entered when creating the keystore.

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".release\longintervalcamera-release.jks"))
```

When the tag is pushed, GitHub Release assets will include `LongIntervalCamera-release.apk` and `LongIntervalCamera-release.apk.sha256`.

```powershell
git tag v1.0.1
git push origin v1.0.1
```

## License

This project is released under the MIT License. See [LICENSE](LICENSE) for details.
