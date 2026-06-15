#!/bin/bash
set -e
#
# Applies the Telegram X fork's native submodule patches:
#   - tgcalls : raise the 1:1 outgoing screencast max bitrate
#   - webrtc  : convert RTC_CHECK_NOTREACHED() AudioDeviceModule stubs to graceful
#               returns (fixes the native SIGABRT on group-call join)
#
# These modify the read-only upstream submodules under app/jni/third_party, so the
# changes can't live in the submodule gitlinks. They are kept as patch files under
# app/jni/patches and re-applied here after every submodule init/reset. See
# app/jni/patches/README.md.
#
# Idempotent: a patch already applied is detected (reverse-check) and skipped, so
# running setup.sh repeatedly is safe.

# $THIRDPARTY_LIBRARIES is exported by set-env.sh as <repo>/app/jni/third_party.
PATCHES_DIR="$(dirname "$THIRDPARTY_LIBRARIES")/patches"

apply_patch () {
  local submodule_dir="$1"
  local patch_file="$2"
  if [[ ! -d "$submodule_dir/.git" && ! -f "$submodule_dir/.git" ]]; then
    echo "WARNING: submodule not checked out: $submodule_dir (run 'git submodule update --init' first)"
    return 0
  fi
  if [[ ! -f "$patch_file" ]]; then
    echo "WARNING: patch not found: $patch_file"
    return 0
  fi
  if git -C "$submodule_dir" apply --reverse --check "$patch_file" >/dev/null 2>&1; then
    echo "Native patch already applied: $(basename "$patch_file")"
  elif git -C "$submodule_dir" apply --check "$patch_file" >/dev/null 2>&1; then
    git -C "$submodule_dir" apply "$patch_file"
    echo "Applied native patch: $(basename "$patch_file")"
  else
    echo "WARNING: could not apply $(basename "$patch_file") cleanly — the submodule may have"
    echo "         diverged from the pinned commit. Inspect manually against $patch_file."
  fi
}

apply_patch "$THIRDPARTY_LIBRARIES/tgcalls" "$PATCHES_DIR/tgcalls-screencast-bitrate.patch"
apply_patch "$THIRDPARTY_LIBRARIES/webrtc"  "$PATCHES_DIR/webrtc-audio-device-graceful.patch"

echo "Native submodule patches up to date."
