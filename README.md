# Notification Saver

Personal Android app that reads notifications on the phone and forwards the ones you choose to Telegram and/or an [npoint.io](https://www.npoint.io/) JSON bin.

Telegram credentials, the npoint URL, and the encode/decode keys stay on the device. Nothing is hardcoded. The app does not post its own status-bar notifications. npoint items are sealed with libsodium `crypto_box_seal` so a public bin URL is not readable plaintext.

| | |
| --- | --- |
| Display name | Notification Saver |
| Application ID | `com.notificationsaver.app` |
| Debug ID | `com.notificationsaver.app.debug` |
| Min Android | 8.0 (API 26) |
| Target / compile | Android 16 (API 36) |
| UI | Jetpack Compose, always dark |

Install the **debug** APK. The release APK from this project is unsigned and will not install.

**Docs:** [ENCRYPTION.md](ENCRYPTION.md) — npoint encode/decode keys, sealed JSON format, and decrypt recipes.

## Contents

- [What it does](#what-it-does)
- [How it works](#how-it-works)
- [Screens](#screens)
- [First-run on the phone](#first-run-on-the-phone)
- [Permissions](#permissions)
- [Build the APK (Windows)](#build-the-apk-windows)
- [Install](#install)
- [Keep it alive on Samsung (One UI)](#keep-it-alive-on-samsung-one-ui)
- [Project layout](#project-layout)
- [Privacy](#privacy)
- [ENCRYPTION.md](ENCRYPTION.md)

---

## What it does

1. You set at least one destination: a Telegram bot (token + chat ID) and/or an npoint API URL.
2. You grant **notification access** so Android will deliver other apps’ notifications to this app.
3. You pick which apps to forward (WhatsApp, Gmail, SMS, and so on).
4. When one of those apps posts a notification, Notification Saver sends it to every destination that is on. Telegram gets HTML text. npoint gets an appended sealed JSON item.

Optional **OTP only** (off by default) drops notifications that do not look like one-time codes.

Telegram forwarding is **on by default** after Telegram setup. npoint is **off** until you turn it on. **Reset all** on Home wipes destinations, keys, selected apps, and delivery logs and returns you to first-run setup.

---

## How it works

```
Other app posts a notification
        │
        ▼
ForwardNotificationListener  (system NotificationListenerService)
        │  allowlist + dedupe + optional OTP-only filter
        ▼
Room outbox  (queued delivery log)
        │
        ▼
WorkManager  SendTelegramWorker
        │  needs network
        ├─► Telegram Bot API  POST /bot<token>/sendMessage
        └─► npoint            POST https://api.npoint.io/{id}
                              (libsodium crypto_box_seal items)
```

### Listener

`ForwardNotificationListener` is a `NotificationListenerService`. Android only delivers notifications after you enable **Notification Saver listener** in system settings.

It skips:

- This app’s own package
- Telegram apps, if **Ignore Telegram** is on (avoids a loop)
- Ongoing / persistent notifications
- Group-summary notifications
- Apps not on the allowlist
- Duplicate keys within a short window
- Non-OTP notifications, if **OTP only** is on

The listener stays bound while **either** Telegram or npoint forwarding is on. If both are off, it unbinds. A silent `HealthWorker` runs about every 15 minutes: if a destination is on, access is granted, and the listener is dead, it rebinds, then bounces the service component if needed. There is **no persistent notification** and no foreground service.

`BootReceiver` listens for boot / quickboot / app update so WorkManager and rebind can start again after reboot.

### Sending

Home **Send test to Telegram** calls the Telegram API immediately (not via the queue). npoint **Test connection** appends a sealed test item. Real notifications go Room → `SendTelegramWorker`, which sends each enabled destination independently and retries only the one that failed.

The Telegram token is trimmed and a leading `bot` prefix is stripped so a paste like `bot123456:AA…` does not become `/botbot123456:…`. Telegram `404 Not Found` means an invalid token, not a missing chat.

npoint writes use **POST** to `https://api.npoint.io/{id}`. Unowned bins (created while logged out on npoint.io) accept POST with no token. Owned bins need a bearer token and a premium npoint account. Cloudflare caches GET, so this app does not read-modify-write the bin: it keeps the last 50 sealed items locally and POSTs the full document.

### OTP only

Off by default. When on, a notification is queued only if the title/body looks like a one-time code: 4–8 digits (or a short alphanumeric code) near language such as OTP, verification, security code, WhatsApp code, or Hindi ओटीपी / कोड. Phone numbers, dates, years, and rupee amounts are skipped.

### npoint encryption

Full spec, field meanings, wire format, and decrypt code: **[ENCRYPTION.md](ENCRYPTION.md)**.

The app generates a Curve25519 **encode key** (public) and **decode key** (secret). Each npoint item is libsodium `crypto_box_seal`. The public bin is ciphertext. Copy the decode key into other apps; it is never uploaded.

**Bearer token** on the npoint screen is an npoint *account* key for owned/premium bins. Leave it blank. It is not the encode or decode key.

Telegram is not sealed; only the npoint JSON is.

### Settings storage

DataStore on the phone:

- Bot token, chat ID, Telegram forwarding on/off
- npoint URL, optional bearer, npoint forwarding on/off, encode/decode keys
- OTP-only
- Allowlist of package names
- Ignore Telegram
- Hourly Telegram ping

Room stores delivery logs and the local npoint item buffer. Backup is disabled.

---

## Screens

**Setup** (first launch until Telegram or npoint is saved)

- Bot token and chat ID, or an npoint API URL
- Save and continue / test Telegram

**Home**

- Forward to Telegram switch
- Forward to npoint switch
- npoint bin (URL, keys, test, clear)
- OTP only switch (off by default)
- Notification access, Listener, Target apps
- Battery (unrestricted) and hourly Telegram ping
- Send test to Telegram
- Reset all

**Telegram** — edit token / chat ID, ignore-Telegram switch, test again.

**npoint** — API URL, optional bearer (owned bins only — leave blank), copy/reset encode and decode keys, test, clear bin. See [ENCRYPTION.md](ENCRYPTION.md).

**Apps** — search installed apps, check the ones to forward.

**Logs** — recent deliveries, extracted OTP when present, and errors.

Tapping **Target apps** opens the Apps tab. Tapping **Listener** opens notification-access settings if it is off, or rebinds if it is on. Tapping **Battery** after it is already Unrestricted opens this app’s system battery / app-info page (the allow-dialog is a no-op once granted).

On Samsung, **Autostart / background** opens this app’s system **App info** page. Tap **Battery** and set Unrestricted / allow background usage. Samsung’s **Never sleeping apps → +** search often **will not show** a newly installed or debug-sideloaded app — Device Care only offers apps it has already been managing. That is a Samsung filter, not a missing icon in this project.

---

## First-run on the phone

### 1. Telegram bot

1. Open Telegram, search **@BotFather**, send `/newbot`, follow the prompts.
2. Copy the token (`123456789:AA…`). Do not include the word `bot` in front.
3. Open **your** new bot and tap **Start** (otherwise the bot cannot message you).

### 2. Chat ID

1. Search **@userinfobot**, tap **Start**, copy the number it replies with.
2. That is your personal chat ID. Paste it into the app.

For a **group**: add the bot, send a message in the group, then use the group’s ID (usually a negative number such as `-100…`). `@userinfobot` only gives your personal ID.

### 3. In the app

1. Save Telegram, or paste an npoint API URL and continue (or both).
2. Home → **Notification access** → enable **Notification Saver**.
3. **Apps** tab → check at least one app.
4. Leave **Forward to Telegram** and/or **Forward to npoint** on.
5. Optional: **OTP only** if you only want one-time codes.
6. **Battery** → Unrestricted / allow ignoring battery optimizations.
7. Samsung: Autostart → **Open app info** → **Battery** → Unrestricted / allow background. Do not expect **Never sleeping apps → +** to list this app when it is newly installed.

### 4. npoint bin (optional)

1. On [npoint.io](https://www.npoint.io/), stay **logged out**, create a JSON bin, copy `https://api.npoint.io/…`.
2. In the app: Home → **npoint bin** → paste the URL → Save. Leave **Bearer token** empty.
3. Copy **encode key** and **decode key**. Other apps need the **decode key** ([ENCRYPTION.md](ENCRYPTION.md)).
4. Turn **Forward to npoint** on. **Test connection** should append a sealed “Connection test” item.
5. **Clear bin** replaces the document with an empty `items` list.

You must grant notification access again after a package-id change or a fresh install.

---

## Permissions

| Permission / access | Why |
| --- | --- |
| `INTERNET` | Telegram and npoint HTTPS |
| `RECEIVE_BOOT_COMPLETED` | Restart workers after reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask for unrestricted battery |
| `QUERY_ALL_PACKAGES` | List apps on the Apps tab |
| Notification listener | Read other apps’ notifications |

There is no `POST_NOTIFICATIONS` and no foreground-service notification. The app will not appear as a persistent “running” tile.

`QUERY_ALL_PACKAGES` is a Play-restricted permission. This project is meant for sideloading on your own phone, not Play Store distribution as-is.

---

## Build the APK (Windows)

### Tools

- **JDK 17** (Temurin is fine). Example: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- **Android SDK** with platform **android-36**, build-tools, and platform-tools (ADB). Typical location: `%LOCALAPPDATA%\Android\Sdk`
- `local.properties` in the project root must contain:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Gradle Wrapper is included (`gradlew.bat`). You do not need a global Gradle install.

### Debug APK (this is the one to install)

In PowerShell, from the project folder:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

Output:

`app\build\outputs\apk\debug\app-debug.apk`

A copy is often placed at `dist\notification-saver-debug.apk` after a manual copy. The Gradle task itself writes under `app\build\outputs\apk\debug\`.

### Release APK (unsigned — will not install)

```powershell
.\gradlew.bat assembleRelease
```

Output: `app\build\outputs\apk\release\app-release-unsigned.apk` (or similar). Android refuses unsigned packages. Use debug, or sign release yourself with `apksigner` / Android Studio if you need a release build.

### Clean rebuild

```powershell
.\gradlew.bat clean assembleDebug
```

---

## Install

### USB debugging (recommended)

1. On the phone: Settings → About phone → tap **Build number** seven times.
2. Settings → Developer options → **USB debugging** on.
3. Unlock the phone, plug in a **data** cable (not charge-only), choose **File transfer / MTP**.
4. Accept the “Allow USB debugging?” prompt.

Check the device:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

You want `device`, not `unauthorized` or an empty list.

Install (replace over an older debug build of the **same** application ID):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = "app\build\outputs\apk\debug\app-debug.apk"
& $adb install -r -d $apk
```

Open it:

```powershell
& $adb shell am start -n com.notificationsaver.app.debug/com.notificationsaver.app.MainActivity
```

If `adb` reports no device: unlock the screen, try another cable/port, set USB to File transfer, then `adb kill-server` and `adb start-server`.

Signature mismatch (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): uninstall the old package first, then install again:

```powershell
& $adb uninstall com.notificationsaver.app.debug
& $adb install -r -d $apk
```

The previous ID `com.reetam.notificationsaver.debug` is a different app. Uninstall that separately if it is still on the phone.

### Sideload without a PC

Copy `app-debug.apk` to the phone and open it. Enable **Install unknown apps** for Files / your browser. Debug builds are signed with the debug keystore, so this works. You still need USB debugging only if you want `adb`.

---

## Keep it alive on Samsung (One UI)

Phone makers kill background listeners. This app cannot fully prevent that without a persistent notification (which this project refuses).

Do all of these:

1. **Battery** row → Unrestricted.
2. **Autostart / background** → App info → Battery → Unrestricted / allow background.
3. Settings → Apps → Notification Saver → Battery → allow background usage.
4. Do not force-stop the app. After a reboot, unlock the phone once so the listener can bind.

Xiaomi / Oppo / Vivo / Huawei / OnePlus / Asus open their own autostart screens when that row is shown.

---

## Project layout

```
README.md
ENCRYPTION.md          npoint sealed-box format and decrypt recipes
app/src/main/java/com/notificationsaver/app/
  MainActivity.kt
  NotificationSaverApp.kt
  data/                 DataStore, Room, Telegram HTTP, npoint, OTP, sealed box
  service/              Listener, boot receiver, OEM autostart
  worker/               Send + health WorkManager jobs
  ui/                   Compose screens (home, apps, telegram, npoint, logs, setup)
app/src/main/AndroidManifest.xml
app/build.gradle.kts
```

Stack: Kotlin, AGP 8.13, Gradle 8.13, Compose BOM 2025.10.01, Room, DataStore, WorkManager, OkHttp, lazysodium-android (libsodium).

---

## Privacy

- Token, chat ID, npoint URL, and keys never leave the phone except as needed to call `https://api.telegram.org` or `https://api.npoint.io`. The npoint decode key is not uploaded.
- Notification text is sent in plaintext to the Telegram chat you configured. The same text is sealed before it is written to npoint.
- No analytics, no backup of settings to the cloud.

Treat the bot token and the npoint decode key like passwords. Anyone with the decode key can read the bin. Details: [ENCRYPTION.md](ENCRYPTION.md).
