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
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.voip.VoIPScreenCapture;
import org.webrtc.Camera1Capturer;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.ContextUtils;
import org.webrtc.EglBase;
import org.webrtc.ScreenCapturerAndroid;
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

  // Process-wide shared root EGL context for all VoIP video. The capturer's
  // SurfaceTextureHelper and the CallVideoView renderers must share ONE context,
  // otherwise the local-preview OES camera texture (valid only in the capturer's
  // GL context) renders black in a non-shared renderer context. Created lazily on
  // the first VoIP-video use and kept for the app lifetime (never released while a
  // call is active).
  private static EglBase rootEglBase;

  /**
   * Returns the shared root {@link EglBase.Context} used across the camera capturer's
   * {@link SurfaceTextureHelper} and the call-video renderers, creating it lazily.
   * Must be called on the main thread (or otherwise externally serialised); VoIP
   * setup runs on the UI thread.
   */
  public static synchronized EglBase.Context getRootEglBaseContext () {
    if (rootEglBase == null) {
      rootEglBase = EglBase.create();
    }
    return rootEglBase.getEglBaseContext();
  }

  private static final String TAG = "VideoCameraCapturer";

  /**
   * Out-of-band handoff flag telling the next {@link #init} to build a
   * {@link ScreenCapturerAndroid} (screen sharing) instead of a camera capturer.
   *
   * <p>Upstream tgcalls' native {@code VideoCameraCapturer.cpp} only invokes Java
   * {@code init(long, boolean)} — it derives {@code useFrontCamera} from the deviceId and has
   * no parameter for the screencast intent. Rather than patch the (unpushable) tgcalls
   * submodule to add a parameter, the screencast-create callers set this static flag
   * immediately before the {@code nativeCreateVideoCapturer("screen", true)} call. tgcalls
   * constructs the capturer asynchronously on its own media thread and then calls back into
   * {@link #init}; the field is {@code volatile} and written before the native call, so the
   * write happens-before and is visible to that thread. {@link #init} reads it once at the top
   * and clears it, so a stale {@code true} can never leak into a later camera capture.
   *
   * <p>This relies on there being at most ONE outgoing capturer being created at a time
   * (Telegram X creates outgoing camera/screen capturers serially from the UI thread and they
   * are mutually exclusive), which holds for the VoIP / group-call flows here.
   */
  private static volatile boolean sNextCaptureIsScreencast;

  /**
   * Marks the next {@link #init} (i.e. the capturer about to be created by the immediately
   * following {@code nativeCreateVideoCapturer}) as a screen-share capturer. Must be called on
   * the same logical flow right before the native create call; the camera path leaves it
   * {@code false}.
   */
  public static void setNextCaptureIsScreencast (boolean v) {
    sNextCaptureIsScreencast = v;
  }

  /**
   * Out-of-band handoff for screen-share teardown originating below the controller layer:
   * either the {@link ScreenCapturerAndroid}'s {@link MediaProjection.Callback#onStop} fired
   * (system revoked the projection / user hit "Stop sharing" in the system UI) or the
   * screencast {@link #init} failed to start capture. The capturer doesn't hold a reference to
   * the controller/service, so the start-of-screencast flow registers a callback here and the
   * capturer drives the SAME teardown as a user stop (native source torn down, FGS type
   * dropped, UI toggle reflected). Routed via this static field rather than a back-reference
   * because the capturer is constructed on tgcalls' media thread.
   *
   * <p>Relies on a single outgoing screencast capturer at a time (camera/screen are mutually
   * exclusive here), the same invariant the screencast handoff flag depends on.
   */
  public interface ScreencastStateCallback {
    /**
     * Screen sharing is no longer available (revoked by the system or failed to start).
     * Invoked on the main thread; must run the full stop-screencast teardown.
     */
    void onScreencastUnavailable ();
  }

  private static volatile @Nullable ScreencastStateCallback sScreencastStateCallback;

  /**
   * Registers (or clears, with {@code null}) the callback driven when a screencast is revoked
   * by the system or fails to start. Set right before starting a screencast; cleared on stop.
   */
  public static void setScreencastStateCallback (@Nullable ScreencastStateCallback callback) {
    sScreencastStateCallback = callback;
  }

  private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

  /** Posts the screencast-unavailable teardown to the main thread (single-use; clears the callback). */
  private static void dispatchScreencastUnavailable () {
    final ScreencastStateCallback callback = sScreencastStateCallback;
    if (callback == null) {
      return;
    }
    MAIN_HANDLER.post(() -> {
      // Re-read and clear so a later capture can't re-trigger a stale teardown.
      final ScreencastStateCallback cb = sScreencastStateCallback;
      sScreencastStateCallback = null;
      if (cb != null) {
        cb.onScreencastUnavailable();
      }
    });
  }

  private VideoCapturer videoCapturer;
  private SurfaceTextureHelper surfaceTextureHelper;
  // The MediaProjection.Callback registered with a ScreenCapturerAndroid; kept so it can be
  // unregistered before the capturer is disposed (avoids touching a half-torn projection).
  private @Nullable MediaProjection.Callback projectionCallback;
  private boolean useFrontCamera = true;
  private boolean isRunning;
  // Synchronises capturer/isRunning access between the media thread (init/onStateChanged/
  // onDestroy) and the MediaProjection.Callback.onStop thread.
  private final Object captureLock = new Object();

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
    // Read AND clear the screencast handoff flag up front (see sNextCaptureIsScreencast):
    // capture it into a local immediately so a stale true can never leak into a later camera
    // capture, and so concurrent reads on the media thread can't see it twice.
    final boolean isScreencast = sNextCaptureIsScreencast;
    sNextCaptureIsScreencast = false;

    this.nativePtr = ptr;
    this.useFrontCamera = useFrontCamera;

    final Context context = ContextUtils.getApplicationContext();
    if (context == null) {
      return;
    }

    // Share the process-wide root EGL context so the local camera/screen OES texture is
    // valid in the CallVideoView renderers (which init() against the same context).
    this.surfaceTextureHelper = SurfaceTextureHelper.create("VideoCameraCapturerThread", getRootEglBaseContext());

    final CapturerObserver observer = nativeGetJavaVideoCapturerObserver(ptr);
    if (observer == null || surfaceTextureHelper == null) {
      cleanup();
      return;
    }

    if (isScreencast) {
      this.videoCapturer = createScreenCapturer();
      if (videoCapturer == null) {
        // No pending MediaProjection permission result (request not granted / already
        // consumed). Fail gracefully — the native side keeps a capturer with no frames
        // rather than crashing; the UI flow re-requests permission on the next toggle.
        Log.w(TAG, "Screencast requested but no MediaProjection permission result available");
        cleanup();
        // Signal failure so the controller/service can reconcile state (drop the FGS type,
        // reset the UI toggle) instead of believing screen sharing is live.
        dispatchScreencastUnavailable();
        return;
      }
      // GUARDED screencast start: ScreenCapturerAndroid.startCapture() synchronously calls
      // MediaProjectionManager.getMediaProjection() / createVirtualDisplay(), which THROW
      // SecurityException (Android 10+) / IllegalStateException (Android 14) if the
      // mediaProjection FGS type isn't live or the token is invalid. This runs on tgcalls'
      // media thread, so an uncaught throw would unwind into native @Keep init() and crash
      // the process. Catch everything, tear down cleanly, and signal failure so the call
      // survives and the UI re-prompts.
      try {
        videoCapturer.initialize(surfaceTextureHelper, context, observer);
        videoCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
        isRunning = true;
      } catch (Throwable t) {
        Log.e(TAG, "Failed to start screen capture", t);
        try {
          videoCapturer.dispose();
        } catch (Throwable ignored) {
          // best-effort: dispose may throw if initialize() didn't complete.
        }
        videoCapturer = null;
        isRunning = false;
        cleanup();
        dispatchScreencastUnavailable();
      }
      return;
    }

    final boolean useCamera2 = Camera2Enumerator.isSupported(context);
    final CameraEnumerator enumerator = useCamera2
      ? new Camera2Enumerator(context)
      : new Camera1Enumerator(false);

    final String deviceName = selectDevice(enumerator, useFrontCamera);
    if (deviceName == null) {
      cleanup();
      return;
    }

    if (useCamera2) {
      this.videoCapturer = new Camera2Capturer(context, deviceName, null);
    } else {
      this.videoCapturer = new Camera1Capturer(deviceName, null, true);
    }

    // Camera path keeps the historical unguarded call: Camera2/Camera1 capturers don't throw
    // the projection-token exceptions the screencast path must defend against.
    videoCapturer.initialize(surfaceTextureHelper, context, observer);
    videoCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
    isRunning = true;
  }

  /**
   * Builds an {@link ScreenCapturerAndroid} from the pending MediaProjection permission
   * result held in {@link VoIPScreenCapture}. Returns {@code null} when no grant is
   * available (so the caller can fail gracefully instead of crashing).
   *
   * <p>Requires the call foreground service to already be running with the
   * {@code mediaProjection} foreground-service type (see AndroidManifest), otherwise
   * {@code MediaProjectionManager.getMediaProjection} throws on Android 10+.
   */
  private @Nullable VideoCapturer createScreenCapturer () {
    final Intent permissionResult = VoIPScreenCapture.consumePendingPermissionResult();
    if (permissionResult == null) {
      return null;
    }
    this.projectionCallback = new MediaProjection.Callback() {
      @Override
      public void onStop () {
        // The system revoked screen capture (or the user hit "Stop sharing" in the system
        // UI). Stop streaming so we don't leak the virtual display, THEN drive the same
        // teardown as a user stop through the service/controller (native source torn down,
        // FGS type dropped, UI toggle reset) — the local capturer alone can't reconcile that.
        synchronized (captureLock) {
          try {
            if (videoCapturer != null && isRunning) {
              videoCapturer.stopCapture();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            isRunning = false;
          }
        }
        dispatchScreencastUnavailable();
      }
    };
    return new ScreenCapturerAndroid(permissionResult, projectionCallback);
  }

  /**
   * Releases partially-initialised capture resources after a failed {@link #init} so a
   * failed init doesn't leak the SurfaceTextureHelper / its GL thread. Does NOT touch the
   * shared root EGL context (long-lived, owned statically).
   */
  private void cleanup () {
    if (surfaceTextureHelper != null) {
      surfaceTextureHelper.dispose();
      surfaceTextureHelper = null;
    }
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
    // VideoState.Active == 2 (see org.thunderdog.challegram.voip.annotation.VideoState).
    final boolean shouldCapture = state == 2;
    synchronized (captureLock) {
      if (videoCapturer == null) {
        return;
      }
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
    synchronized (captureLock) {
      // Unregister the screen-projection callback BEFORE disposing the capturer so a late
      // onStop() can't touch a half-torn ScreenCapturerAndroid / re-trigger teardown.
      if (projectionCallback != null && videoCapturer instanceof ScreenCapturerAndroid) {
        try {
          MediaProjection projection = ((ScreenCapturerAndroid) videoCapturer).getMediaProjection();
          if (projection != null) {
            projection.unregisterCallback(projectionCallback);
          }
        } catch (Throwable ignored) {
          // Projection may already be stopped / never started; ignore.
        }
      }
      projectionCallback = null;
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
    }
    if (surfaceTextureHelper != null) {
      surfaceTextureHelper.dispose();
      surfaceTextureHelper = null;
    }
    // The root EGL context is process-wide and shared with the renderers; never released here.
    nativePtr = 0;
  }
}
