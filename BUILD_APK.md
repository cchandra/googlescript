# Build ScriptWatch APK

## Easiest automated build

This repository contains `.github/workflows/build-apk.yml`.

1. Push the project to a GitHub repository.
2. Open **Actions** → **Build Android APK**.
3. Choose **Run workflow**.
4. After the job completes, download the artifact named **ScriptWatch-debug-apk**.
5. Inside the artifact is `app-debug.apk`, installable on Android.

The workflow provisions Java 17, Android SDK 35, Build Tools 35.0.0, and Gradle 8.9 automatically.

## Local Android Studio build

Open the project in Android Studio and choose **Build → Build APK(s)**. The debug APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`

## Google OAuth setup before real use

The APK can be built without your OAuth client configuration, but Google authorization for your Apps Script account requires an Android OAuth client matching:

- Package: `com.charlie.scriptwatch`
- SHA-1: the certificate used to sign the APK

For production, create a release keystore and corresponding Android OAuth client, then build a signed release APK/AAB.
