# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Neurogram X** — a feature-extended Android fork of [Telegram X](https://github.com/TGX-Android/Telegram-X) (TDLib-based Telegram client), renamed in honour of the AI VTuber Neuro-sama.

* Application id: `space.hikaro.tgx`, display name `Neurogram X` (set via `app.name` in `local.properties` → generated `AppName` string).
* Upstream is read-only; all original Telegram X architecture still applies. This file documents the fork's specifics on top of it.
* User-facing build/run docs live in [README.md](/README.md).

## Environment & Build (Windows)

Development happens on **Windows** with **JDK 21**. Two shells are in play: **PowerShell** (gradle, git, adb) and **Git Bash** (POSIX `scripts/*.sh`).

**Always set `GRADLE_USER_HOME` before building** — there is a Gradle junction bug on this machine that fails builds otherwise (point it at any stable path outside the repo):

```powershell
$env:GRADLE_USER_HOME = "<path-outside-the-repo>\.gradle"
.\gradlew.bat :app:compileLatestX64DebugJavaWithJavac   # fast compile check
.\gradlew.bat :app:assembleLatestArm64Debug             # device (ARM64) APK
.\gradlew.bat :app:assembleLatestX64Debug               # emulator (x86_64) APK
.\gradlew.bat assembleUniversalRelease                   # release
```

* APK outputs: `app/build/outputs/apk/`, named `Neurogram-X-<version>-<abi>-<type>.apk`.
* Native build works (`externalNativeBuild*`); `app/.cxx` may be junctioned to another drive to dodge a full `F:`.
* The `F:` drive is chronically near-full — prefer `C:`/`G:` for heavy/cold builds.
* Setup after fresh clone: `scripts/setup.sh` (Git Bash), or `scripts/setup.sh --skip-sdk-setup`.

### Signing & the "experimental build" flag (important)

A build is flagged **experimental** when no keystore is configured (`buildSrc/.../ConfigurationPlugin.kt`: `isExperimentalBuild = isExampleBuild || keystore == null || app.experimental`). An experimental build **short-circuits FCM push registration** (`TdlibManager.checkDeviceToken` → `EXPERIMENTAL_BUILD_DETECTED`) **and skips applying the `google-services` plugin** (`app/build.gradle.kts`). So:

* Push / notifications only work in a **keystore-signed** build.
* The keystore lives in a gitignored, never-committed `keystore/` dir (a `.jks` + a `key.properties`), pointed to by `keystore.file` in `local.properties`. `/keystore` and `/local.properties` are gitignored.
* The keystore signs **both debug and release**, so even debug device builds register push.
* Changing the signing key requires an **uninstall+reinstall** on-device (signature mismatch).
* Note: `app.ntgcalls` in `local.properties` is a **dead/no-op flag** (referenced nowhere in the build) — see calls note below.

### On-device testing

The dev device is a phone over **wireless ADB** (`adb connect <ip:port>`). Build the matching ABI (the phone is ARM → `assembleLatestArm64Debug`), `adb install -r`, and read crashes with `adb logcat`. Prefer verifying by building/running over guessing.

## Git Workflow

**Push to `fork`, not `origin`:**

```bash
git push fork <branch-name>     # fork = git@github.com:japananimetime/Telegram-X.git
```

* `fork` is SSH; if SSH fails on this machine, push over HTTPS to the same repo.
* `origin` / `upstream` = `TGX-Android/Telegram-X` (read-only).
* Commit/push only when asked. Never commit directly to `all-features-combined` — work in a `feature/*` branch and merge.

### Branches

| Branch | Purpose |
|--------|---------|
| `main` | Tracks upstream Telegram X |
| `base/tdlib`, `core/tdlib` | Shared core (TDLib upgrade + crash routing) |
| `feature/*` | Isolated features: `native-video-calls`/`calls`, `stories`, `mini-apps`, `gifts`, `stars`, `premium-billing`, `quotes`, `forum-topics`, `saved-tags`, `profile-notes`, `playback-speed`, `disposable-voices`, `reactions-improvements`, `voice-transcription`, `rich-messages`, `community-features` |
| `all-features-combined` | Integration of feature branches |
| `parity-fixes/*` | Active working branch (calls + parity fixes + on-device hardening) |

## Calls (native tgcalls)

Voice/video calling is **native `tgcalls`** (with `libtgvoip` + `webrtc`), built via `app/jni/BuildTgCalls.cmake`; sources under `app/jni/third_party/{tgcalls,libtgvoip,webrtc}`. It supports 1:1 video, group video chats, and screen sharing. The **`NTgCalls`** library was evaluated and **abandoned** — `deprecated/ntgcalls*` branches are superseded by the native tgcalls work (#856). Do not reintroduce ntgcalls.

## Issue Tracking (MantisBT)

**Project ID: 1**. Categories: General, Interface, Purchases, Stories, Topics (created via web UI only).

Workflow: before work, check/assign an issue; during, add progress notes; when done, add an implementation summary note (files modified, key changes, TDLib functions used, **commit hash + branch + PR link**) and set status `resolved` / resolution `fixed`. Create issues for bugs discovered during development. Statuses: `new` → `assigned` → `resolved` → `closed`. Reached via `mcp__mantisbt__*` MCP tools (requires `MANTIS_API_TOKEN`).

## Local tooling (neuro-pipeline)

This repo has a local, gitignored `.claude/` toolset (Ollama executor + MantisBT tracker + autonomous audit loop). Relevant agents/skills: `code-author`, `code-reviewer`, `commit-msg`, `ticket-triager`, and the `/audit-loop`, `/parity-*`, `*-regression` skills. They delegate to local Ollama (`mcp__ollama__*`) and are local-only (not part of the app).

## Project Architecture

### Core Package Structure (`app/src/main/java/org/thunderdog/challegram/`)

| Package | Purpose |
|---------|---------|
| `telegram/` | TDLib wrapper (`Tdlib.java`), account management (`TdlibManager.java`), listeners, caching |
| `ui/` | All screen controllers (see ViewController pattern below) |
| `navigation/` | Custom navigation framework (`NavigationController`, `ViewController`, `HeaderView`) |
| `data/` | Data models for messages (`TGMessage*`), chats (`TGChat`), reactions (`TGReaction*`) |
| `component/` | Complex UI components: chat (`/chat/`), dialogs (`/dialogs/`), stickers, popups |
| `widget/` | Reusable custom views (`AvatarView`, `ChatView`, `StoryBarView`) |
| `theme/` | Theme system, color management |
| `tool/` | Static utilities (`Screen`, `Fonts`, `Views`, `Strings`, `Drawables`) |
| `util/` | Helper classes, text rendering (`Text.java`), formatters |

### Submodules / native

| Module | Path | Purpose |
|--------|------|---------|
| `tdlib` | `/tdlib/` | TDLib native library + JNI bindings |
| `vkryl:core` | `/vkryl/core/` | Core utilities (not Telegram-specific) |
| `vkryl:android` | `/vkryl/android/` | Android utilities, animators |
| `vkryl:leveldb` | `/vkryl/leveldb/` | LevelDB Java bindings |
| `vkryl:td` | `/vkryl/td/` | TDLib utility extensions |
| native calls | `app/jni/third_party/{tgcalls,libtgvoip,webrtc}` | Native voice/video calling |

### Navigation System (NOT standard Android)

Telegram X uses a custom navigation framework instead of Activities/Fragments:

- **`BaseActivity`** → Single activity host, manages `NavigationController`
- **`NavigationController`** → Manages navigation stack, transitions
- **`ViewController<T>`** → Base class for all screens

Creating a new screen:
1. Add ID to `app/src/main/res/values/ids.xml` with `controller_` prefix
2. Create class in `ui/` package extending `ViewController<T>` or subclass
3. Navigate via `navigationController.navigateTo(new MyController(context, tdlib))`

Key lifecycle methods: `onCreateView()`, `onFocus()`/`onBlur()`, `needAsynchronousAnimation()`, `destroy()`. `ViewController` implements `BaseActivity.ActivityListener` and self-registers (`context.addActivityListener(this)`), so `onActivityPause()`/`onActivityResume()` reach it even inside a `PopupLayout`.

Common controller types: `RecyclerViewController<T>` (lists), `ViewPagerController<T>` (tabs), `EditBaseController<T>` (edit screens).

### Animation System

Use `me.vkryl.android.animator` classes instead of standard Android animators: `BoolAnimator`, `FactorAnimator`, `ListAnimator<T>`, `ReplaceAnimator<T>`.

### Theme Colors

Access colors via `Theme.getColor(R.id.theme_color_*)` — optimized for `onDraw()`. Definitions: `app/src/main/other/themes/colors-and-properties.xml`.

### Strings/Translations

- Main strings: `app/src/main/res/values/strings.xml` (English only). The launcher label `AppName` is **generated** from `app.name` (do not add `AppName` to strings.xml).
- Use `Lang.getString()`, `Lang.plural()`, `Lang.getRelativeDate()`. Translations are managed via translations.telegram.org.

## TDLib Integration

TDLib functions are accessed via the `Tdlib` class:
- `tdlib.client().send(new TdApi.Function(), handler)` / `tdlib.send(fn, (result, error) -> {})`
- Async helpers: `tdlib.getChat()`, `tdlib.getUser()`, etc.
- Listeners: implement interfaces in `telegram/` (`ChatListener`, `MessageListener`, …)

```java
tdlib.client().send(new TdApi.GetChat(chatId), result -> {
  if (result.getConstructor() == TdApi.Chat.CONSTRUCTOR) {
    TdApi.Chat chat = (TdApi.Chat) result;
    // handle chat
  }
});
```

**Never hand-edit** `TdCompileAssert.kt` / `TdUnsupported.kt` / `TdEqualsTo.kt` in `vkryl/td` — they are regenerated from `TdApi.java` by `generateResourcesAndThemes`.

## Key Implementation Patterns

### Message Types
New message types go in `data/TGMessage*.java`. Register in `TGMessage.valueOf()`.

### Custom Views
- Inherit from `View` or `BaseView` (for 3D-touch/preview support)
- Use `Screen.dp()` for dimensions; draw in `onDraw()`; **never allocate in drawing methods**

### Resources
- IDs: `app/src/main/res/values/ids.xml`
- Strings: `app/src/main/res/values/strings.xml`
- Icons: vector drawables, 24×24 viewport, size in filename suffix

## Code Style

- Double whitespace as tab
- Space before method parameter brace: `void method () {`
- Kotlin allowed in `me.vkryl.*` packages only (must interop with Java)
- Match surrounding code's comment density, naming, and idiom
