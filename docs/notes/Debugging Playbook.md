# Debugging Playbook

## Device
```powershell
adb connect <phone-ip>:<port>            # wireless ADB (phone is ARM64)
adb install -r app\build\outputs\apk\latestArm64\debug\Neurogram-X-*-arm64-debug.apk
adb shell pidof space.hikaro.tgx
adb logcat --pid=$(adb shell pidof space.hikaro.tgx)      # everything from the app
adb logcat -s tgx:* AndroidRuntime:E                       # app log tag is "tgx" (Log.java LOG_TAG)
adb logcat *:E | findstr /i "space.hikaro.tgx tgx"         # crashes only
adb uninstall space.hikaro.tgx                             # needed after changing the signing key
```
App logs also go to a logs directory managed by `Log.java` (tag bitmask stored in `Settings.KEY_LOG_TAGS`); TDLib log messages arrive through `Client.setLogMessageHandler` in `TdlibManager` (fatal level 0 is routed to crash handling).

## From a screen to its code
1. Read a visible string in the UI → search it in `app/src/main/res/values/strings.xml` → search the `R.string.<name>` usages (`grep -rn "R.string.<name>" app/src/main/java`).
2. Screens are `ui/*Controller.java`; their ids are `controller_*` in `res/values/ids.xml`. Menu/button ids are `btn_*` in the same file.
3. "How is this screen opened?" → `TdlibUi.java` (all `open*` helpers and deep links) or `navigateTo(new XController` grep.
4. Chat screen internals: `MessagesController` (13.6k lines; use the field names in [[Forum Topics]]) plus `component/chat/*`.
5. Chat list rows: `component/dialogs/`, `widget/ChatView`, `BetterChatView`, `VerticalChatView`, data in `data/TGChat`.
6. Data / TDLib: `telegram/Tdlib.java` caches and helpers, listeners in `telegram/*Listener.java`.

## Typical failure patterns in this codebase
- **Screen appears late or "hangs"** → `needAsynchronousAnimation()` true but `executeScheduledAnimation()` never called after data load.
- **Stale UI after an update** → listener not registered, or registered but `notifyDataSetChanged` path only on some updates; check `destroy()` unsubscribes (soft leaks stack listeners on re-open).
- **Wrong topic / wrong chat** → compare `TdApi.Message.topicId` with `Td.matchesTopic`; check `MessageListManager` filter and `updateNewMessage` handling.
- **Crash on a new TDLib update type** → `ClassCastException` in the `Tdlib.java` update switch after a TdApi bump.
- **Money paths** → `CurrencyUtils.buildAmount`, `tdlib.currentTimeMillis()`, and `StarAmount` copies (never alias Tdlib's cached objects).
- **Push silent** → experimental build (no keystore) or `app.id` ≠ `google-services.json` package; on Android 12+ the FGS-from-background fix must be present.
- **Group call crashes on join** → webrtc patch not applied after a submodule update (see [[Build & Environment]]).

## Fast compile loop
`.\gradlew.bat :app:compileLatestX64DebugJavaWithJavac` (Windows) / `./gradlew :app:compileLatestArm64DebugJavaWithJavac` is the quickest correctness check; native tasks are incremental after the first build.

## Reading history
`git log -S "someIdentifier" --oneline all-features-combined -- app/src` finds the commit that introduced or removed a symbol; commit subjects carry the Mantis id. The June 2026 rebase means dates are not chronology; use the ticket ids.
