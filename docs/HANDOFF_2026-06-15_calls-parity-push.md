# Handoff — Parity + Calls + Push (2026-06-15)

Pickup doc for a fresh session. Everything below is on branch **`parity-fixes/2026-06-14`** (off `all-features-combined`), **NOT pushed**. Every commit is compile-gated; full APK + native builds verified green throughout. **Nothing here is device-tested.**

## ⚡ DO THIS FIRST (remaining finalize — 3 steps)
1. **Build the google-flavor APK** (FCM-included; confirmed `FirebaseListenerService` is in the `latestX64Debug` merged manifest):
   ```
   $env:GRADLE_USER_HOME = "F:\DevCache\.gradle"   # REQUIRED every gradle call (junction bug)
   .\gradlew.bat assembleLatestX64Debug --console=plain
   ```
   APK → `app/build/outputs/apk/latestX64/debug/TGX-Example-*-x64-debug.apk` (only the x64 ABI flavor is configured; `assembleLatestArm64Debug` does NOT exist).
2. **Fast-forward the combined branch** (linear, safe): `git checkout all-features-combined && git merge --ff-only parity-fixes/2026-06-14`.
3. **Push to fork** — ⚠️ needs the user's explicit go-ahead: local `all-features-combined` has **diverged** from `fork/all-features-combined` (~52 fork-only / ~71 local-only commits from the earlier reconstruction), so this is a **force-push**, and the `fork` SSH remote fails here — push via **HTTPS**: `git push https://github.com/japananimetime/Telegram-X.git all-features-combined --force` (and the working branch). The user said they'll **test the APK after the push**.

## Push notifications — DONE except the user's Firebase, which is now wired
- **Root cause of "no background push" was two things, both addressed:**
  1. **Code (FIXED, commit `d1b083227`):** on Android 12+ the app tried to start a foreground service from the background to process a push → `ForegroundServiceStartNotAllowedException` → push silently dropped. Ported null-nick's fix (push-processing only): `PushProcessor` skips the eager FGS-start on API≥S (processes inline, escalates via the 7s timeout), `ForegroundService` try/catches the start, `PushHandler`/`PushManagerBridge` take a `Context` not a `Service`. (Did **not** port null-nick's call-service changes — they'd regress our calls work.)
  2. **Config (NOW FIXED):** `app/google-services.json` was a dummy (`scenic-parity-125411`, package `org.thunderdog.challegram`) ≠ `app.id` (`space.hikaro.tgx` in `local.properties`). The build validator (`ValidateApiTokensTask`) requires `package_name == app.id`. The user supplied a **real** json (project `telegram-x-9e82c`, package `space.hikaro.tgx`) — it's now placed at `app/google-services.json`. There's also a stale copy at repo-root `google-services.json` (can be deleted; build only reads `app/`).
- FCM provider lives in the `app/src/google` source set (gated by `tgx.extension` ≠ `hms`); the receive chain `FirebaseListenerService → PushManagerBridge → PushHandler → PushProcessor → ForegroundService → TDLib` is intact. Background push should now work once built with this json. **Device-test required to confirm.**

## What was accomplished this program (branch `parity-fixes/2026-06-14`)
Three big phases, each fully ticketed in **MantisBT project #1** and re-audited:
1. **Bug audit-loop** → 18 fixes (tickets #813–#837): forum-topic regressions, P0 GroupCallService FGS crash, player/quote/transcription bugs, WebView hardening, etc.
2. **Parity & coverage audit** (70 gaps, #838–#859) → fixed in-loop: the AFC reconstruction had **disconnected finished community features** (dropped entry-points/deep-links) — re-wired gifts/stars/mini-app/story deep-links + entry points; built Business editors (hours/greeting/away/connected-bot/start-page sticker+location, multi-interval, recipients), rich-text notes/captions, story album viewer, LiveStory link, etc.
3. **Native calls (#856) — FULLY IMPLEMENTED**: the native tgcalls/WebRTC libs already had video (no rebuild). Wired:
   - **1:1 video** (capture + render + `CallController` UI: toggle/switch/accept-with-video).
   - **Group video** (interactive `GroupCallController` + per-participant tiles + outgoing camera, `setRequestedVideoChannels`).
   - **Screen share** (1:1 + group): `ScreenCapturerAndroid` + MediaProjection + FGS `mediaProjection` type; **group screen-share is a real server-side presentation** (2nd group instance, `StartGroupCallScreenSharing` handshake). Done WITHOUT editing the tgcalls submodule (static screencast-flag handoff, since `app/jni/third_party/tgcalls` → upstream `TGX-Android/tgcalls`, no push access).
   Each stage was regression-re-audited; caught + fixed a P1 EGL black-preview, a P0 UAF on close, a P0 screencast-flag race (camera+screen concurrent), money-path billing bugs, etc.
- **All deferred TODOs resolved** (final scan = zero `TODO()/FIXME` added by this branch). Honest remaining limitations: full LiveStory live-*broadcast viewer* (separate feature), and everything is **compile/link-verified, not device-tested**.

## Build / env gotchas (still apply)
- Every gradle call: `$env:GRADLE_USER_HOME="F:\DevCache\.gradle"` + `.\gradlew.bat`.
- Native build works: `externalNativeBuildLatestX64Debug` (incremental ~secs; `app/.cxx` junctioned to `G:\TgxBuild\cxx`). Compile check: `compileLatestX64DebugJavaWithJavac`.
- Push via **HTTPS** (SSH `fork` remote fails: publickey).
- Calls/native code: `app/jni/tgvoip.cpp`, `app/jni/group_call.cpp`, `app/jni/video_capture_context.h`; Java VoIP: `voip/TgCallsController.java`, `voip/GroupCallInstance.java`, `voip/VideoCameraCapturer.java` (org.telegram.messenger.voip — required by the tgcalls native ABI), `widget/voip/CallVideoView.java`, `widget/voip/GroupCallVideoView.java`. Do NOT touch the tgcalls submodule.

## After push — recommended QA on device (two devices for calls)
1:1 + group **video** (camera frames, tiles, switch-camera, accept-with-video), **screen share** (projection permission, FGS type, group presentation), **background push** (send a test from Firebase console + a real Telegram message with app backgrounded on Android 12/13/14). Then triage any device bugs back into MantisBT #1.

## Audit workflows (reusable, in `.claude/workflows/`)
`parity-coverage`, `parity-regression`/`2`/`3`, `video-regression`, `groupvideo-regression`, `screenshare-regression`, `todofix-regression`, `audit-loop`. Pattern: map (parallel finders) → adversarially-verify → fix-plan; lead applies/compiles/commits serially.
