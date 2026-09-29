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
package org.thunderdog.challegram.widget.voip;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.voip.VideoCameraCapturer;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.Screen;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSink;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Renders both sides of a 1:1 video call: the remote stream fills the background and the local
 * camera preview sits in a small draggable corner card.
 *
 * <p>Both renderers are initialised against the process-wide shared root EGL context
 * ({@link VideoCameraCapturer#getRootEglBaseContext()}), the SAME context the capture pipeline's
 * {@code SurfaceTextureHelper} uses. This is REQUIRED for correctness of the local preview: local
 * frames carry an OES camera texture whose name is only valid in the capturer's GL context, so a
 * renderer in a separate (non-shared) context would render them black. The root context is
 * long-lived and is NOT released here.
 *
 * <p>CRITICAL: {@link #release()} must be called exactly once when the call ends / the controller
 * is destroyed, otherwise the {@link SurfaceViewRenderer}s leak their EGL surfaces.
 */
public class CallVideoView extends FrameLayoutFix {
  private final SurfaceViewRenderer remoteRenderer;
  private final SurfaceViewRenderer localRenderer;

  private boolean renderersInitialized;
  private boolean released;

  public CallVideoView (@NonNull Context context) {
    super(context);

    remoteRenderer = new SurfaceViewRenderer(context);
    remoteRenderer.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    remoteRenderer.setVisibility(View.GONE);
    addView(remoteRenderer);

    FrameLayoutFix.LayoutParams localParams = FrameLayoutFix.newParams(Screen.dp(100f), Screen.dp(150f), Gravity.TOP | Gravity.RIGHT);
    localParams.topMargin = Screen.dp(48f);
    localParams.rightMargin = Screen.dp(12f);
    localRenderer = new SurfaceViewRenderer(context) {
      private float downX, downY, startTranslationX, startTranslationY;
      private boolean dragging;

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            downX = event.getRawX();
            downY = event.getRawY();
            startTranslationX = getTranslationX();
            startTranslationY = getTranslationY();
            dragging = false;
            return true;
          case MotionEvent.ACTION_MOVE: {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (!dragging && Math.max(Math.abs(dx), Math.abs(dy)) > Screen.getTouchSlop()) {
              dragging = true;
            }
            if (dragging) {
              setTranslationX(clampTranslationX(startTranslationX + dx));
              setTranslationY(clampTranslationY(startTranslationY + dy));
            }
            return true;
          }
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            return true;
        }
        return super.onTouchEvent(event);
      }
    };
    localRenderer.setLayoutParams(localParams);
    // Float the preview above the remote SurfaceView (both are SurfaceViews).
    localRenderer.setZOrderMediaOverlay(true);
    localRenderer.setVisibility(View.GONE);
    addView(localRenderer);
  }

  private float clampTranslationX (float translationX) {
    View parent = (View) getParent();
    // Skip clamping until both the preview and its parent are laid out; otherwise the
    // bounds are degenerate (min > max) and would snap the preview off-screen on first touch.
    if (parent == null || parent.getWidth() == 0 || localRenderer.getWidth() == 0) {
      return translationX;
    }
    float min = -localRenderer.getLeft();
    float max = parent.getWidth() - localRenderer.getRight();
    return Math.max(min, Math.min(max, translationX));
  }

  private float clampTranslationY (float translationY) {
    View parent = (View) getParent();
    if (parent == null || parent.getHeight() == 0 || localRenderer.getHeight() == 0) {
      return translationY;
    }
    float min = -localRenderer.getTop();
    float max = parent.getHeight() - localRenderer.getBottom();
    return Math.max(min, Math.min(max, translationY));
  }

  private void ensureInitialized () {
    if (renderersInitialized || released) {
      return;
    }
    try {
      // Share the process-wide root EGL context (same one the capturer's SurfaceTextureHelper
      // uses) so the local OES camera-texture preview renders correctly instead of black.
      final org.webrtc.EglBase.Context rootContext = VideoCameraCapturer.getRootEglBaseContext();

      remoteRenderer.init(rootContext, null);
      remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
      remoteRenderer.setEnableHardwareScaler(true);

      localRenderer.init(rootContext, null);
      localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
      localRenderer.setEnableHardwareScaler(true);
      localRenderer.setMirror(true);

      renderersInitialized = true;
    } catch (Throwable t) {
      Log.e("Unable to init call video renderers", t);
    }
  }

  /**
   * Returns the remote {@link VideoSink} to attach to the call's incoming video output, lazily
   * initialising the renderers. Null once released.
   */
  @Nullable
  public VideoSink getRemoteSink () {
    if (released) {
      return null;
    }
    ensureInitialized();
    return remoteRenderer;
  }

  /**
   * Returns the local-preview {@link VideoSink} to attach to the call's local video output, lazily
   * initialising the renderers. Null once released.
   */
  @Nullable
  public VideoSink getLocalSink () {
    if (released) {
      return null;
    }
    ensureInitialized();
    return localRenderer;
  }

  public void setRemoteVisible (boolean visible) {
    remoteRenderer.setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  public void setLocalVisible (boolean visible) {
    localRenderer.setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  public void setLocalMirror (boolean mirror) {
    localRenderer.setMirror(mirror);
  }

  /**
   * Releases both renderers. Idempotent. Must be called once at the end of the call's lifecycle.
   * The shared root EGL context is process-wide and long-lived, so it is intentionally NOT
   * released here.
   */
  public void release () {
    if (released) {
      return;
    }
    released = true;
    if (renderersInitialized) {
      remoteRenderer.release();
      localRenderer.release();
    }
  }
}
