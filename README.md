# ScriptWatch — Android Google Apps Script Monitor

An Android MVP for monitoring and manually running Google Apps Script projects.

## Current features

- Save multiple Apps Script projects on-device.
- Authorize directly with Google Identity Services.
- Read recent Apps Script executions.
- Show 7-day execution and failure totals.
- Run a selected Apps Script function manually.
- Add script-specific OAuth scopes where needed.

## Google setup required

1. Create/open a Google Cloud project.
2. Enable **Google Apps Script API**.
3. Create an **Android OAuth client** for package `com.charlie.scriptwatch` and your signing certificate SHA-1.
4. For every script you want to run remotely, switch the Apps Script project to the **same standard Google Cloud project** used by this Android app.
5. In Apps Script: **Deploy > New deployment > API Executable**. Copy its deployment ID.
6. In the Apps Script dashboard/account settings, enable Apps Script API access if required.
7. Add the Script ID, API Executable deployment ID, function name, and any extra OAuth scopes in ScriptWatch.

Monitoring needs:
- `https://www.googleapis.com/auth/script.processes`
- `https://www.googleapis.com/auth/script.metrics`

Remote execution additionally needs every OAuth scope required by the target Apps Script. Find these under the script project's Overview / Project OAuth Scopes.

## Important Google limitation

`scripts.run` only works when the calling app and the Apps Script use the same Google Cloud project. It also runs against an **API Executable deployment ID**, not merely a Script ID.

## Build

Open the folder in Android Studio, let Gradle sync, then run on an Android device with Google Play Services.

The project uses:
- Kotlin
- Jetpack Compose
- Google Identity Services `AuthorizationClient`
- OkHttp

## Suggested V2

- Background monitoring via WorkManager.
- Push alerts on FAILED / TIMED_OUT executions.
- Discover Apps Script projects from Google Drive instead of entering IDs manually.
- Per-script function presets and parameters.
- View structured error details and execution duration.
- Trigger enable/disable controls via a companion management endpoint.
