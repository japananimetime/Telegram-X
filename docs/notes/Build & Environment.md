# Build & Environment

## Pinned versions (`version.properties`, `gradle/wrapper`)
| Thing | Version |
|---|---|
| App version | `version.app=1785` |
| Gradle | 9.2.1 (wrapper) |
| compileSdk / targetSdk | 36 |
| build-tools | 36.0.0 |
| NDK (primary = legacy) | `23.2.8568313` |
| CMake | 3.22.1 |
| cmdline-tools | 11076708 |
| JDK | **21** on the Windows dev box (CLAUDE.md). `BUILD_FROM_SCRATCH.md` says 17; Gradle 9 needs 17+, so either works, 21 is what is proven. |
| TDLib | custom bundle from `tdlib/td@cecbf129` via the forks in [[Repositories & Branches]] |

Native deps built by `scripts/setup.sh`: ffmpeg `release/6.1`, libvpx 1.14, opus 1.3.1, webp 1.2, flac 1.3.3, plus tgcalls/webrtc/libtgvoip for calls.

## Windows (the proven setup)
Two shells: PowerShell for gradle/git/adb, Git Bash for `scripts/*.sh`.
```powershell
$env:GRADLE_USER_HOME = "F:\DevCache\.gradle"    # REQUIRED every time (junction bug); any stable path outside the repo
.\gradlew.bat :app:compileLatestX64DebugJavaWithJavac   # fast compile check
.\gradlew.bat :app:assembleLatestArm64Debug             # phone (ARM64)
.\gradlew.bat :app:assembleLatestX64Debug               # emulator
.\gradlew.bat assembleUniversalRelease                   # release (needs keystore)
```
- Outputs: `app/build/outputs/apk/<flavor>/<type>/Neurogram-X-<version>-<abi>-<type>.apk`
- `app/.cxx` may be junctioned to `G:\TgxBuild\cxx`; `F:` is chronically full, prefer `C:`/`G:`.
- The 2026-06-15 handoff notes that on that machine only the **x64** ABI flavor was configured (`assembleLatestArm64Debug` did not exist). Check `local.properties` / flavor config if an ABI task is missing.
- `gradle.properties` already forces `kotlin.compiler.execution.strategy=in-process` (Windows file-lock workaround) and `-Xmx4G`.

## Linux box status (2026-09-06)
JDK 21 (`jdk21-openjdk`), Android SDK at `~/Android/Sdk` with platform 36, build-tools 36, NDK 23.2.8568313 and CMake 3.22.1 installed by `scripts/setup-sdk.sh` (run with `ANDROID_SDK_ROOT=$HOME/Android/Sdk` exported to skip its prompt). `local.properties` exists with **dummy** `telegram.api_id=1` / `api_hash` — good for `compileLatestArm64DebugJavaWithJavac` compile checks only, never install such a build. Submodules initialised so far: `tdlib`, `vkryl/*`, `thirdparty/androidx-media/*`, `jni-utils`, `webrtc` (shallow). Native deps (`scripts/setup.sh`, ffmpeg/vpx/tgcalls) not built yet, so `assemble*` will not work here until that runs.

Two build-script fallbacks were needed for the flat TDLib snapshot (no `tdlib/source/{td,openssl}`): `app/build.gradle.kts` now finds `opensslv.h` under `tdlib/openssl/<abi>/include` and reports the TDLib version as `prebuilt-<commit>` when `CMakeLists.txt` is absent. The Windows box may have had those directories from the old nested submodules.

First full APK built here 2026-09-06 (`assembleLatestArm64Debug`, 80 MB, signed with the real keystore, signature fingerprint verified against `keystore/neurogram.jks`). Native chain that worked: `source scripts/set-env.sh`, then `bash scripts/private/patch-native-impl.sh` (the file has no exec bit in git, hence `bash`), `bash scripts/private/build-vpx-impl.sh`, `bash scripts/private/build-ffmpeg-impl.sh`, then gradle. `scripts/setup.sh` was avoided because it runs `reset.sh` when `local.properties` exists. Secrets came from the Windows box over Taildrop (never through chat): `local.properties`, `keystore/key.properties`, `keystore/neurogram.jks`, all gitignored, chmod 600.

Fast loop here:
```bash
export ANDROID_SDK_ROOT=$HOME/Android/Sdk ANDROID_HOME=$HOME/Android/Sdk
./gradlew --console=plain :app:compileLatestArm64DebugJavaWithJavac
```

## Fresh machine, step by step
1. Packages: `git git-lfs` (`git lfs install`), a JDK 17 or 21 (`jdk21-openjdk` on Arch), `python`, `perl`, `cmake`, `ninja`, `wget`, `unzip`.
2. Clone: `git clone --recursive git@github.com:japananimetime/Telegram-X.git` then `git checkout all-features-combined && git submodule update --init --recursive`. Already cloned at `~/Projects/Telegram-X` **without** submodules.
3. `scripts/setup.sh` installs cmdline-tools + platform 36 + NDK into `ANDROID_SDK_ROOT` (default `~/Android/Sdk` on Linux, via `scripts/set-env.sh`), applies the native patches, builds vpx/ffmpeg. Expect a long run and several GB. `--skip-sdk-setup` if the SDK exists.
4. `local.properties` (gitignored, created by setup or by hand):
```properties
sdk.dir=/home/you/Android/Sdk
telegram.api_id=...            # https://my.telegram.org
telegram.api_hash=...
app.id=space.hikaro.tgx        # MUST match app/google-services.json
app.name=Neurogram X
keystore.file=/abs/path/keystore/key.properties   # optional for debug, required for push
app.download_url=https://...
app.sources_url=https://github.com/japananimetime/Telegram-X
```
5. `./gradlew assembleLatestArm64Debug`, then `adb install -r <apk>`.

## Signing and the "experimental build" flag
`buildSrc/.../ConfigurationPlugin.kt`: `isExperimentalBuild = isExampleBuild || keystore == null || app.experimental`. An experimental build **skips the google-services plugin and FCM registration** (`TdlibManager.checkDeviceToken` → `EXPERIMENTAL_BUILD_DETECTED`). Consequences:
- No push notifications unless the build is signed with the real keystore (`keystore/neurogram.jks` + `key.properties`, both gitignored, never committed, keep a backup).
- The keystore signs debug and release alike. Changing it means uninstall + reinstall on the phone.
- `app/google-services.json` is committed for package `space.hikaro.tgx` (Firebase project `telegram-x-9e82c`). A stale copy at repo root can be deleted.
- `app.ntgcalls` in `local.properties` is a dead flag.

## Native patches (do not skip)
`scripts/private/patch-native-impl.sh` (run by `setup.sh`) applies `app/jni/patches/tgcalls-screencast-bitrate.patch` and `webrtc-audio-device-graceful.patch` to the read-only `tgcalls`/`webrtc` submodules. Idempotent; must be re-run after every `git submodule update` / `scripts/reset.sh`. Without the webrtc patch group calls SIGABRT on join. Manual run:
```bash
export THIRDPARTY_LIBRARIES="$(pwd)/app/jni/third_party" && bash scripts/private/patch-native-impl.sh
```
Never edit the tgcalls submodule itself (no push access); the fork's call code lives in `app/jni/{tgvoip,group_call}.cpp`, `app/jni/video_capture_context.h`, `voip/TgCallsController.java`, `voip/GroupCallInstance.java`.

## Other scripts
`scripts/reset.sh` (reset submodules + clean), `scripts/force-clean.sh`, `scripts/update-dependencies.sh`, `scripts/version_bump.sh`, `scripts/print-env.sh`.
