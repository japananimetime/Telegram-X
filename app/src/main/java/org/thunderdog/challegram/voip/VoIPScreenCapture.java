/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.voip;

import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * Process-wide holder for the most recent {@code MediaProjection} screen-capture
 * permission result.
 *
 * <p>The org.webrtc {@link org.webrtc.ScreenCapturerAndroid} constructor requires the
 * {@code Intent} returned from {@code MediaProjectionManager.createScreenCaptureIntent()}
 * via {@code startActivityForResult}. That result is delivered to
 * {@code BaseActivity.onActivityResult}, which stores it here; the Java
 * {@link org.telegram.messenger.voip.VideoCameraCapturer} (constructed on the native
 * VoIP thread when a screencast capturer is created) reads it back to build the
 * screen capturer.
 *
 * <p>The held result is consumed exactly once (cleared on read) so a stale permission
 * grant can't silently start a later, unrelated screen-share. Callers that fail to
 * obtain a fresh grant get {@code null} and must request permission again.
 */
public final class VoIPScreenCapture {
  private static @Nullable Intent pendingPermissionResult;

  private VoIPScreenCapture () {
  }

  /**
   * Stores the screen-capture permission result {@code Intent} (from a
   * {@code RESULT_OK} {@code onActivityResult}). Replaces any previous pending result.
   */
  public static synchronized void setPendingPermissionResult (@Nullable Intent data) {
    pendingPermissionResult = data;
  }

  /**
   * Returns and clears the pending screen-capture permission result, or {@code null}
   * if none is available (no grant, or already consumed). The result is single-use:
   * {@code ScreenCapturerAndroid} takes ownership of the {@code Intent}.
   */
  public static synchronized @Nullable Intent consumePendingPermissionResult () {
    Intent data = pendingPermissionResult;
    pendingPermissionResult = null;
    return data;
  }

  /** Whether a screen-capture permission result is currently held. */
  public static synchronized boolean hasPendingPermissionResult () {
    return pendingPermissionResult != null;
  }

  /** Clears any held permission result (e.g. on screen-share stop / call end). */
  public static synchronized void clear () {
    pendingPermissionResult = null;
  }
}
