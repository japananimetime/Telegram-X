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
package org.telegram.messenger.voip;

import android.content.Context;
import android.os.Build;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.webrtc.Camera1Capturer;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.ContextUtils;
import org.webrtc.EglBase;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;

/**
 * Java side of the tgcalls Android camera capturer.
 *
 * <p>The package and method signatures here are dictated by the native code in
 * {@code tgcalls/platform/android/AndroidContext.cpp} and {@code VideoCameraCapturer.cpp}, which
 * resolve the class {@code org/telegram/messenger/voip/VideoCameraCapturer} and invoke
 * {@link #init(long, boolean)}, {@link #onStateChanged(long, int)},
 * {@link #onAspectRatioRequested(float)} and {@link #onDestroy()} on it, plus the static native
 * {@link #nativeGetJavaVideoCapturerObserver(long)} (implemented in C++) to obtain the
 * {@link CapturerObserver} that bridges captured frames into the native video track source.
 *
 * <p>This is a faithful port of upstream Telegram-Android's class of the same name, adapted to
 * Telegram X conventions. It owns one {@link org.webrtc.CameraVideoCapturer} feeding the native
 * observer through a shared {@link EglBase} + {@link SurfaceTextureHelper}.
 */
@Keep
@SuppressWarnings("unused")
public class VideoCameraCapturer {
  // Default capture geometry; tgcalls negotiates the actual stream via the aspect-ratio request.
  private static final int CAPTURE_WIDTH = 1280;
  private static final int CAPTURE_HEIGHT = 720;
  private static final int CAPTURE_FPS = 30;

  private VideoCapturer videoCapturer;
  private SurfaceTextureHelper surfaceTextureHelper;
  private EglBase eglBase;
  private boolean useFrontCamera = true;
  private boolean isRunning;

  // Native pointer to the C++ tgcalls::VideoCameraCapturer that owns this instance, used by
  // nativeGetJavaVideoCapturerObserver to retrieve the matching native CapturerObserver.
  private long nativePtr;

  public VideoCameraCapturer () {
  }

  // Native: returns the org.webrtc.CapturerObserver wrapping the JavaVideoTrackSource for the
  // given native tgcalls::VideoCameraCapturer pointer. Implemented in VideoCameraCapturer.cpp.
  private static native @Nullable CapturerObserver nativeGetJavaVideoCapturerObserver (long ptr);

  /**
   * Called from native code once the C++ capturer is constructed. Builds the org.webrtc capture
   * pipeline and starts streaming frames into the native observer.
   */
  @Keep
  public void init (long ptr, boolean useFrontCamera) {
    this.nativePtr = ptr;
    this.useFrontCamera = useFrontCamera;

    final Context context = ContextUtils.getApplicationContext();
    if (context == null) {
      return;
    }

    this.eglBase = EglBase.create();
    this.surfaceTextureHelper = SurfaceTextureHelper.create("VideoCameraCapturerThread", eglBase.getEglBaseContext());

    final CapturerObserver observer = nativeGetJavaVideoCapturerObserver(ptr);
    if (observer == null || surfaceTextureHelper == null) {
      return;
    }

    final boolean useCamera2 = Camera2Enumerator.isSupported(context);
    final CameraEnumerator enumerator = useCamera2
      ? new Camera2Enumerator(context)
      : new Camera1Enumerator(false);

    final String deviceName = selectDevice(enumerator, useFrontCamera);
    if (deviceName == null) {
      return;
    }

    if (useCamera2) {
      this.videoCapturer = new Camera2Capturer(context, deviceName, null);
    } else {
      this.videoCapturer = new Camera1Capturer(deviceName, null, true);
    }

    videoCapturer.initialize(surfaceTextureHelper, context, observer);
    videoCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
    isRunning = true;
  }

  private static @Nullable String selectDevice (CameraEnumerator enumerator, boolean front) {
    final String[] deviceNames = enumerator.getDeviceNames();
    for (String name : deviceNames) {
      if (front ? enumerator.isFrontFacing(name) : enumerator.isBackFacing(name)) {
        return name;
      }
    }
    // Fall back to any available camera if the preferred facing is unavailable.
    return deviceNames.length > 0 ? deviceNames[0] : null;
  }

  /**
   * Called from native code to reflect tgcalls VideoState changes (0 Inactive / 1 Paused /
   * 2 Active). Pause/resume is mapped to stop/start of the underlying capturer.
   */
  @Keep
  public void onStateChanged (long ptr, int state) {
    if (videoCapturer == null) {
      return;
    }
    // VideoState.Active == 2 (see org.thunderdog.challegram.voip.annotation.VideoState).
    final boolean shouldCapture = state == 2;
    try {
      if (shouldCapture && !isRunning) {
        videoCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
        isRunning = true;
      } else if (!shouldCapture && isRunning) {
        videoCapturer.stopCapture();
        isRunning = false;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Called from native code when tgcalls requests a preferred capture aspect ratio. The org.webrtc
   * camera capturer negotiates resolution internally, so this is currently advisory only.
   */
  @Keep
  public void onAspectRatioRequested (float aspectRatio) {
    // No-op: org.webrtc CameraCapturer selects the closest supported format itself. Kept to match
    // the native call into VideoCameraCapturer::setPreferredCaptureAspectRatio.
  }

  /**
   * Switches between front and back cameras on the running capturer.
   */
  @Keep
  public void switchCamera (boolean useFront) {
    if (!(videoCapturer instanceof CameraVideoCapturer)) {
      return;
    }
    this.useFrontCamera = useFront;
    ((CameraVideoCapturer) videoCapturer).switchCamera(null);
  }

  /**
   * Called from native code (AndroidContext destructor) to release all capture resources.
   */
  @Keep
  public void onDestroy () {
    if (videoCapturer != null) {
      try {
        videoCapturer.stopCapture();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      videoCapturer.dispose();
      videoCapturer = null;
    }
    isRunning = false;
    if (surfaceTextureHelper != null) {
      surfaceTextureHelper.dispose();
      surfaceTextureHelper = null;
    }
    if (eglBase != null) {
      eglBase.release();
      eglBase = null;
    }
    nativePtr = 0;
  }
}
