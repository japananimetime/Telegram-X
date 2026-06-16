# Neurogram X

**Neurogram X** is a feature-extended Android fork of [Telegram X](https://github.com/TGX-Android/Telegram-X) — the slick, [TDLib](https://core.telegram.org/tdlib)-based alternative Telegram client. It is named in honour of [**Neuro-sama**](https://en.wikipedia.org/wiki/Neuro-sama), the AI VTuber created by Vedal.

> This is an **unofficial** fork maintained for personal use and experimentation. It is **not** affiliated with, endorsed by, or supported by Telegram or the Telegram X team. For the official client, use [Telegram X](https://play.google.com/store/apps/details?id=org.thunderdog.challegram).

* Application id: `space.hikaro.tgx`
* Based on: [TGX-Android/Telegram-X](https://github.com/TGX-Android/Telegram-X) (`origin` / `upstream`)
* License: GPL-3.0 (inherited from Telegram X) — see [LICENSE](/LICENSE)

## What this fork adds

Work in Neurogram X extends stock Telegram X across these areas (developed across `feature/*` branches and integrated on working branches such as `parity-fixes/*`):

| Area | Highlights |
|------|-----------|
| **Calls** | Native 1:1 **video calls** and **group video chats**, screen sharing (incl. group server-side presentation) via native `tgcalls` (`libtgvoip` + `webrtc`) |
| **Stories** | Viewing + posting, story bar in the chat list, story composer/preview, viewer with reactions/replies |
| **Mini Apps** | Full Web Apps (Mini Apps) support — `WebAppController` + JS bridge |
| **Gifts & Stars** | Telegram Stars balance/purchases, TON, gift economy |
| **Premium / Billing** | Payment forms, Stars store purchases via Play Billing |
| **Messaging** | Quotes / reply-in-other-chat, forum topics, saved-message tags, rich (formatted) messages with inline math/code |
| **Media & Voice** | Playback-speed controls, disposable voice messages, voice transcription |
| **Reactions** | Big reactions, attach-button improvements |
| **Profile / Community** | Profile notes, photo-resolution and community quality-of-life features |
| **Push** | Reworked FCM registration (Android-12+ FGS fix), self-hosted Firebase project |

Not everything above is merged into a single branch at once — see **Branches** below.

## Build

Neurogram X is developed and built primarily on **Windows** (PowerShell + Git Bash). The original Telegram X Linux/macOS instructions still apply; the notes below cover this fork's specifics.

### Prerequisites

* **Android SDK** (with NDK `23.2.8568313`, CMake `3.22.1` — pinned in `version.properties`)
* **JDK 21** (e.g. Eclipse Temurin)
* **git** with **LFS**: `git lfs install`
* ~**5 GB+** free disk for sources + build outputs
* Windows: a POSIX shell (Git Bash / MSYS2) for `scripts/setup.sh`

### 1. Clone with submodules

```bash
git clone --recursive https://github.com/japananimetime/Telegram-X tgx
cd tgx
# if you forgot --recursive:
git submodule update --init --recursive --depth=1
```

### 2. `local.properties`

Create `local.properties` in the project root ([obtain Telegram API credentials](https://core.telegram.org/api/obtaining_api_id)):

```properties
# Android SDK location
sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk

# Telegram API credentials
telegram.api_id=YOUR_TELEGRAM_API_ID
telegram.api_hash=YOUR_TELEGRAM_API_HASH

# App identity (the fork)
app.id=space.hikaro.tgx
app.name=Neurogram X

# Signing keystore — see step 3. REQUIRED for working push / Firebase.
keystore.file=ABSOLUTE/PATH/TO/keystore/key.properties
```

### 3. Signing keystore (required for push)

The build is flagged **experimental** when no keystore is configured (`ConfigurationPlugin.kt`), and an experimental build **disables FCM push registration and skips the `google-services` plugin**. To get working notifications you must sign with a real keystore:

```bash
keytool -genkeypair -v -keystore keystore/neurogram.jks \
  -alias neurogram -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <pw> -keypass <pw> \
  -dname "CN=Neurogram X, O=Neurogram, C=US"
```

Then point `keystore.file` (in `local.properties`) at a `key.properties` that contains the real values:

```properties
keystore.file=ABSOLUTE/PATH/TO/keystore/neurogram.jks
keystore.password=<pw>
key.alias=neurogram
key.password=<pw>
```

> Keep the keystore safe — it is the app's signing identity. Changing it requires uninstalling the app on-device (signature mismatch). `/keystore` and `/local.properties` are gitignored.

### 4. Firebase

This fork ships an `app/google-services.json` for the `space.hikaro.tgx` package (Firebase project `telegram-x-9e82c`). If you change `app.id`, [set up your own Firebase project](https://firebase.google.com/docs/android/setup) and replace `app/google-services.json`.

### 5. Native dependencies & build

```bash
scripts/setup.sh                 # downloads SDK packages + builds native deps
# or, if the SDK is already set up:
scripts/setup.sh --skip-sdk-setup
```

Build (Windows / PowerShell — if you hit a Gradle file-lock/junction error, point `GRADLE_USER_HOME` at any stable path outside the repo first):

```powershell
$env:GRADLE_USER_HOME = "<path-outside-the-repo>\.gradle"
.\gradlew.bat assembleLatestArm64Debug     # ARM64 device build
.\gradlew.bat assembleLatestX64Debug       # x86_64 emulator build
.\gradlew.bat assembleUniversalRelease      # release
```

Outputs land in `app/build/outputs/apk/`, named `Neurogram-X-<version>-<abi>-<type>.apk`.

#### ABI flavors

`arm64` (arm64-v8a), `arm32` (armeabi-v7a), `x64` (x86_64), `x86`, and `universal` (all ABIs). SDK dimension: `latest` / `legacy`.

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Tracks upstream Telegram X |
| `base/tdlib` / `core/tdlib` | Shared core (TDLib upgrade, crash routing) |
| `feature/*` | Isolated feature work (calls, stories, mini-apps, gifts, stars, quotes, forum topics, rich-messages, …) |
| `all-features-combined` | Integration of feature branches |
| `parity-fixes/*` | Active working branch (calls + parity fixes + on-device hardening) |

Remotes: `fork` = `git@github.com:japananimetime/Telegram-X.git` (push here), `origin`/`upstream` = read-only TGX-Android.

## Credits

Neurogram X is built entirely on top of [**Telegram X**](https://github.com/TGX-Android/Telegram-X) by the TGX-Android team, which is in turn based on [TDLib](https://github.com/tdlib/td) and the [Telegram API](https://core.telegram.org/api). All credit for the underlying client belongs to them. This fork merely extends it.

## License

`Neurogram X`, like `Telegram X`, is licensed under the **GNU General Public License v3.0**. See [LICENSE](/LICENSE). Third-party components are listed in [docs/THIRDPARTY.md](/docs/THIRDPARTY.md) and may carry their own licenses.
