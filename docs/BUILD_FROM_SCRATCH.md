# Building the APK from scratch (disaster recovery)

How to rebuild this fork's APK on a brand-new machine, assuming only access to the
GitHub fork. Target branch: **`all-features-combined`** (identical to
`parity-fixes/2026-06-14` — the device-tested build with all features + native calls).

## 0. Prerequisites

- JDK 17, Android SDK + NDK (setup script can install the SDK/NDK), CMake, Python, Perl,
  a POSIX shell (Git Bash on Windows).
- The Telegram **api_id / api_hash** (your secret — not in the repo; get from
  https://my.telegram.org). Without these the app builds but can't connect.

## 1. Clone with submodules

```bash
git clone https://github.com/japananimetime/Telegram-X.git
cd Telegram-X
git checkout all-features-combined
git submodule update --init --recursive
```

All submodules are reachable from public remotes, including the two **forks this build
depends on** (keep these accounts/repos alive — they are the long pole of recovery):

| Submodule | Remote | Why forked |
|---|---|---|
| `tdlib` | `github.com/japananimetime/tdlib` (branch `tgx/tdlib-flat`) | TDLib with rich-messages API as a **flat snapshot with prebuilt `.so` committed as regular blobs** — no native TDLib build needed |
| `vkryl/td` | `github.com/japananimetime/tdlib-utils` (branch `tgx/td-richmessages-bindings`) | TDLib Java bindings for the rich-messages API |

`tgcalls` and `webrtc` point at `TGX-Android` (upstream, `production` branch commits) and
are patched locally — see step 3.

## 2. Run setup

```bash
./scripts/setup.sh                 # full: installs SDK/NDK, patches deps, builds vpx/ffmpeg
# or, if SDK/NDK already configured:
./scripts/setup.sh --skip-sdk-setup
```

`setup.sh` will prompt to create `local.properties` (git-ignored, machine-specific). The
values this fork uses:

```properties
sdk.dir=<path to Android SDK>
app.id=space.hikaro.tgx          # MUST match app/google-services.json (committed)
app.name=Telegram X
keystore.file=<path to keystore settings file>   # debug builds use the auto debug key
telegram.api_id=<your api_id>
telegram.api_hash=<your api_hash>
app.download_url=https://...
app.sources_url=https://github.com/japananimetime/Telegram-X
```

> **app.id must be `space.hikaro.tgx`** — the committed `app/google-services.json` (FCM /
> push) is registered for that package; a mismatch breaks the google flavor / push.

## 3. Native submodule patches (now automatic)

`setup.sh` runs `scripts/private/patch-native-impl.sh`, which applies the two required
patches under `app/jni/patches/` to the read-only `tgcalls` / `webrtc` submodules:

- `tgcalls-screencast-bitrate.patch` — readable 1:1 screen-share bitrate
- `webrtc-audio-device-graceful.patch` — fixes the **group-call join SIGABRT**

It is idempotent (skips already-applied patches) and must be re-run after any
`git submodule update` / reset. To apply manually without the full setup:

```bash
export THIRDPARTY_LIBRARIES="$(pwd)/app/jni/third_party"
bash scripts/private/patch-native-impl.sh
```

Without these patches the build still completes but group calls crash on join — so do not
skip this step.

## 4. Build

```bash
./gradlew assembleLatestArm64Debug     # arm64 device/emulator debug (most common)
./gradlew assembleLatestX64Debug       # x86_64 emulator debug
./gradlew assembleUniversalRelease     # signed universal release (needs keystore)
```

Output: `app/build/outputs/apk/`.

### Windows notes
- `gradle.properties` already sets `kotlin.compiler.execution.strategy=in-process` (avoids
  the Kotlin-daemon memory-mapped file lock).
- If the SDK lives on a path with junction/locking issues, set a dedicated Gradle home:
  `export GRADLE_USER_HOME=<fast-local-path>/.gradle` before building.

## What is NOT in this build (by design)
- The alternate calls stacks: `feature/ntgcalls` (Laky-64 NTgCalls) and `feature/calls`
  ("Invite to Video Chat", NTgCalls-coupled). This build uses its own native tgcalls
  integration instead.
