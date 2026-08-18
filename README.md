# Notification Saver

Personal Android app that reads notifications on the phone and forwards the ones you choose to a Telegram chat.

The bot token and chat ID stay on the device. Nothing is hardcoded. The app does not post its own status-bar notifications.

| | |
| --- | --- |
| Display name | Notification Saver |
| Application ID | `com.notificationsaver.app` |
| Debug ID | `com.notificationsaver.app.debug` |
| Min Android | 8.0 (API 26) |
| Target / compile | Android 16 (API 36) |
| UI | Jetpack Compose, always dark |

Install the **debug** APK. The release APK from this project is unsigned and will not install.

---

## What it does

1. You create a Telegram bot and paste its token plus your chat ID into the app.
2. You grant **notification access** so Android will deliver other apps’ notifications to this app.
3. You pick which apps to forward (WhatsApp, Gmail, SMS, and so on).
4. When one of those apps posts a notification, Notification Saver sends a Telegram message with the app name, title, and body.

Forwarding is **on by default** after setup. You can turn it off on Home. **Reset all** on Home wipes token, chat ID, selected apps, and delivery logs and returns you to first-run setup.

---

## How it works

```
Other app posts a notification
        │
        ▼
ForwardNotificationListener  (system NotificationListenerService)
        │  allowlist + dedupe + ignore Telegram’s own notifs
        ▼
Room outbox  (queued delivery log)
        │
        ▼
WorkManager  SendTelegramWorker
        │  needs network
        ▼
Telegram Bot API  POST /bot<token>/sendMessage
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

If forwarding is on and the listener disconnects, it asks the system to rebind. A silent `HealthWorker` runs about every 15 minutes: if forwarding is on, access is granted, and the listener is dead, it rebinds, then bounces the service component if needed. There is **no persistent notification** and no foreground service.

`BootReceiver` listens for boot / quickboot / app update so WorkManager and rebind can start again after reboot.

### Sending

Home **Send test to Telegram** calls the API immediately (not via the queue). Real notifications go Room → `SendTelegramWorker`. Failed sends that Telegram marks retryable are retried. Permanent failures (bad token, bad chat) are logged on the Logs tab.

The token is trimmed and a leading `bot` prefix is stripped so a paste like `bot123456:AA…` does not become `/botbot123456:…`. Telegram `404 Not Found` means an invalid token, not a missing chat.

### Settings storage

DataStore on the phone:

- Bot token, chat ID
- Forwarding on/off
- Allowlist of package names
- Ignore Telegram

Room stores delivery logs (queued / sent / failed). Backup is disabled.

---

## Screens

**Setup** (first launch until token + chat ID are saved)

- Bot token and chat ID fields
- Save and continue
- Test connection

**Home**

- Forward to Telegram switch
- Notification access, Listener, Target apps
- Battery (unrestricted) and Autostart / background (OEM)
- Send test to Telegram
- Reset all

**Telegram** — edit token / chat ID, ignore-Telegram switch, test again.

**Apps** — search installed apps, check the ones to forward.

**Logs** — recent deliveries and errors.

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

1. Save token and chat ID, or use **Test connection** first.
2. Home → **Notification access** → enable **Notification Saver**.
3. **Apps** tab → check at least one app.
4. Leave **Forward to Telegram** on.
5. **Battery** → Unrestricted / allow ignoring battery optimizations.
6. Samsung: Autostart → **Open app info** → **Battery** → Unrestricted / allow background. Do not expect **Never sleeping apps → +** to list this app when it is newly installed.

You must grant notification access again after a package-id change or a fresh install.

---

## Permissions

| Permission / access | Why |
| --- | --- |
| `INTERNET` | Telegram HTTPS |
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
app/src/main/java/com/notificationsaver/app/
  MainActivity.kt
  NotificationSaverApp.kt
  data/                 DataStore, Room, Telegram HTTP
  service/              Listener, boot receiver, OEM autostart
  worker/               Send + health WorkManager jobs
  ui/                   Compose screens (home, apps, telegram, logs, setup)
app/src/main/AndroidManifest.xml
app/build.gradle.kts
```

Stack: Kotlin, AGP 8.13, Gradle 8.13, Compose BOM 2025.10.01, Room, DataStore, WorkManager, OkHttp.

---

## Privacy

- Token and chat ID never leave the phone except as the URL path / JSON body to `https://api.telegram.org`.
- Notification text is sent only to the chat you configured.
- No analytics, no backup of settings to the cloud.

Treat the bot token like a password. Anyone with it can send as your bot.
