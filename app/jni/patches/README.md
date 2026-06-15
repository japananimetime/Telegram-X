# Native submodule patches

These patches modify the **read-only upstream** native submodules under
`app/jni/third_party/`. Because those submodules track upstream remotes we can't push to,
their changes don't live in this repo's history on their own — a fresh
`git submodule update` would reset them and silently regress group calls. The patches are
kept here so the changes are recoverable and reviewable, and must be re-applied after any
submodule reset/reclone.

## Apply

```bash
git -C app/jni/third_party/tgcalls apply ../../patches/tgcalls-screencast-bitrate.patch
git -C app/jni/third_party/webrtc  apply ../../patches/webrtc-audio-device-graceful.patch
```

(Or from the repo root, adjust the relative path to `app/jni/patches/<file>`.)

## What each patch does

### `tgcalls-screencast-bitrate.patch` → `tgcalls/v2/InstanceV2Impl.cpp`
Raises the outgoing **screencast** channel max bitrate (1:1 calls) so shared screens
aren't crushed to an unreadable bitrate on otherwise-idle content.

### `webrtc-audio-device-graceful.patch` → `sdk/android/src/jni/audio_device/audio_device_module.cc`
Converts the unimplemented `RTC_CHECK_NOTREACHED()` stubs in the Android
`AudioDeviceModule` into graceful return values. Several of these are reached by
`CreateAndroidAudioDeviceModule` during a **group-call join**; the hard checks aborted the
process (native SIGABRT) on join. Returning `-1`/`0` instead lets the join proceed.

## TODO

Persist these by pushing the submodules to writable forks and re-pinning the gitlinks,
so a clean clone builds a working group-call APK without manual patching. Until then,
applying these patches is a required post-clone build step.
