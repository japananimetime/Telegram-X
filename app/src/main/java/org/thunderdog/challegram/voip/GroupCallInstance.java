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

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.UI;

/**
 * Java handle to the native tgcalls GROUP engine (GroupInstanceCustomImpl), used
 * to join video chats / voice chats. Native methods live in
 * {@code app/jni/group_call.cpp} and bind statically by name into
 * {@code libtgcallsjni.so}.
 *
 * <p>Join handshake: after {@link #emitJoinPayload()} the native engine produces a
 * join payload, delivered to {@link #handleEmitJoinPayload(int, String)}. The
 * caller relays {audioSource, json} to TDLib via {@code JoinVideoChat} and feeds
 * the response back through {@link #setJoinResponsePayload(String)}. Connection
 * state arrives via {@link #handleNetworkStateChange(boolean)}.</p>
 *
 * <p>Slice 2a: lifecycle + handshake + mute/volume. The foreground service,
 * TDLib orchestration, participant roster, and UI land in later slices.</p>
 */
public class GroupCallInstance {
  /** Callbacks delivered from the native engine. May arrive on a native thread. */
  public interface Listener {
    /** The join payload is ready; relay it to TDLib JoinVideoChat. */
    void onJoinPayloadEmitted (int audioSource, String json);
    /** Network/connection state changed. */
    void onNetworkStateChanged (boolean connected);
  }

  /** tgcalls VideoChannelDescription quality (min is always Thumbnail). */
  public static final int VIDEO_QUALITY_THUMBNAIL = 0;
  public static final int VIDEO_QUALITY_MEDIUM = 1;
  public static final int VIDEO_QUALITY_FULL = 2;

  private long nativePtr;
  // Owns the camera capturer (a native VideoCaptureContext, same type as
  // TgCallsController's), created on demand and handed to the instance.
  private long videoCapturePtr;
  // Mirrors videoCapturePtr != 0 but readable from any thread without seeing a
  // torn long. Set in enableOutgoingVideo, cleared in disableOutgoingVideo/stop.
  private volatile boolean videoEnabled;
  // Current camera facing, tracked so re-routes / mirroring don't hardcode front.
  private boolean frontCamera = true;
  // Whether the current outgoing capturer is a screen-share (vs. camera).
  private volatile boolean screencast;
  // Whether this instance is the SECOND (presentation) connection used for screen
  // sharing — created with videoContentType = Screencast natively. A presentation
  // instance carries only the screencast video; the main instance carries camera/voice.
  private final boolean presentation;
  // The audio source (ssrc) emitted during the join handshake; for a presentation
  // instance this becomes the audioSourceId passed to StartGroupCallScreenSharing.
  // Written on the native callback thread, read on the UI thread after the listener fires.
  private volatile int audioSource;
  // Set once stop() has run; all public video methods become no-ops afterwards
  // so a late call can't touch a torn-down native instance.
  private volatile boolean destroyed;
  private @Nullable Listener listener;

  /**
   * Creates the main native group-call instance (camera + voice).
   *
   * @param muted whether the local microphone starts muted
   */
  public GroupCallInstance (boolean muted) {
    this(muted, false);
  }

  /**
   * Creates a native group-call instance.
   *
   * @param muted whether the local microphone starts muted
   * @param presentation when {@code true}, creates the SECOND screen-sharing connection
   *                      (videoContentType = Screencast) instead of the main camera/voice one
   */
  public GroupCallInstance (boolean muted, boolean presentation) {
    this.presentation = presentation;
    // Initialize the WebRTC Java application context (ContextUtils) + native buffer size BEFORE
    // creating the native group instance. The 1:1 path does this in TGCallService.initialize(); the
    // group path uses a separate service that never did, so org.webrtc.ApplicationContextProvider
    // returned a null context and tgcalls' CreateAndroidAudioDeviceModule aborted on join (native
    // SIGABRT: Check failed: !env->ExceptionCheck()). VoIP.initialize() is idempotent.
    VoIP.initialize(UI.getAppContext());
    this.nativePtr = newInstance(muted, presentation);
  }

  /** Whether this is the presentation (screen-sharing) connection rather than the main call. */
  public boolean isPresentation () {
    return presentation;
  }

  /**
   * The audio source (ssrc) the engine emitted in its join payload. For a presentation
   * instance this is the {@code audioSourceId} that {@code StartGroupCallScreenSharing}
   * expects. Valid only after {@link Listener#onJoinPayloadEmitted}.
   */
  public int getAudioSource () {
    return audioSource;
  }

  public void setListener (@Nullable Listener listener) {
    this.listener = listener;
  }

  public boolean isValid () {
    return nativePtr != 0;
  }

  /** Begins the join handshake; result arrives via the listener. */
  public void emitJoinPayload () {
    if (nativePtr != 0) {
      emitJoinPayload(nativePtr);
    }
  }

  /** Supplies TDLib's JoinVideoChat response payload to the engine. */
  public void setJoinResponsePayload (String json) {
    if (nativePtr != 0 && json != null) {
      setJoinResponsePayload(nativePtr, json);
    }
  }

  public void setMuted (boolean muted) {
    if (nativePtr != 0) {
      setMuted(nativePtr, muted);
    }
  }

  public void setVolume (int audioSource, double volume) {
    if (nativePtr != 0) {
      setVolume(nativePtr, audioSource, volume);
    }
  }

  // region outgoing camera video

  public boolean isVideoEnabled () {
    return videoEnabled;
  }

  /** Whether the camera is currently front-facing (for self-tile mirroring). */
  public boolean isFrontCamera () {
    return frontCamera;
  }

  /** Whether the current outgoing video source is a screen-share (vs. camera). */
  public boolean isScreencast () {
    return screencast;
  }

  /**
   * First-time start of outgoing screen sharing: creates a screencast capturer, attaches it
   * and starts capture, routing the local preview into {@code localSink}. Mutually exclusive
   * with the camera — an active camera capturer is torn down first. The MediaProjection
   * permission result must already be stored in
   * {@link org.thunderdog.challegram.voip.VoIPScreenCapture}.
   */
  public void enableOutgoingScreencast (@Nullable org.webrtc.VideoSink localSink) {
    if (destroyed || nativePtr == 0) {
      return;
    }
    if (videoCapturePtr != 0) {
      if (screencast) {
        // Already screen-sharing — just re-route the preview.
        setLocalPreviewSink(localSink);
        return;
      }
      // Switching camera -> screen: tear down the camera capturer first.
      disableOutgoingVideo();
    }
    // Hand off the screencast intent to the Java VideoCameraCapturer out-of-band: upstream
    // tgcalls only passes useFrontCamera to init(), so we enqueue the flag (TRUE = screencast)
    // IMMEDIATELY before the native create, which constructs the capturer on tgcalls' media
    // thread. The FIFO queue keeps this correct even when a camera (main instance) and screen
    // (presentation instance) capturer are created concurrently — see PENDING_SCREENCAST.
    org.telegram.messenger.voip.VideoCameraCapturer.enqueueNextCaptureIsScreencast(true);
    videoCapturePtr = nativeCreateVideoCapturer("screen", true);
    if (videoCapturePtr != 0) {
      screencast = true;
      if (localSink != null) {
        nativeSetVideoCaptureLocalOutput(videoCapturePtr, localSink);
      }
      nativeSetVideoState(videoCapturePtr, 2 /* VideoState.ACTIVE */);
      nativeSetVideoCapture(nativePtr, videoCapturePtr);
      videoEnabled = true;
    }
  }

  /**
   * First-time start of the outgoing camera: creates the capturer, attaches it to
   * the running call and starts capture, routing the local preview into
   * {@code localSink}. If video is already on this only re-routes the preview sink
   * (use {@link #setLocalPreviewSink} for an explicit re-route).
   */
  public void enableOutgoingVideo (boolean useFrontCamera, @Nullable org.webrtc.VideoSink localSink) {
    if (destroyed || nativePtr == 0) {
      return;
    }
    if (videoCapturePtr != 0) {
      if (!screencast) {
        // Already capturing the camera — don't recreate / re-attach, just re-route the preview.
        setLocalPreviewSink(localSink);
        return;
      }
      // Switching screen -> camera: tear down the screencast capturer first.
      disableOutgoingVideo();
    }
    // Enqueue the camera flag (FALSE) right before the native create so the concurrent-capturer
    // FIFO in VideoCameraCapturer routes THIS init() to a camera, not a screencast. See
    // PENDING_SCREENCAST + the create→init FIFO invariant.
    org.telegram.messenger.voip.VideoCameraCapturer.enqueueNextCaptureIsScreencast(false);
    videoCapturePtr = nativeCreateVideoCapturer(useFrontCamera ? "front" : "back", false);
    if (videoCapturePtr != 0) {
      screencast = false;
      frontCamera = useFrontCamera;
      if (localSink != null) {
        nativeSetVideoCaptureLocalOutput(videoCapturePtr, localSink);
      }
      nativeSetVideoState(videoCapturePtr, 2 /* VideoState.ACTIVE */);
      nativeSetVideoCapture(nativePtr, videoCapturePtr);
      videoEnabled = true;
    }
  }

  /**
   * Re-routes the local camera preview into a (possibly new) sink without
   * recreating the capturer. No-op if the camera isn't running.
   */
  public void setLocalPreviewSink (@Nullable org.webrtc.VideoSink localSink) {
    if (destroyed || videoCapturePtr == 0) {
      return;
    }
    nativeSetVideoCaptureLocalOutput(videoCapturePtr, localSink);
  }

  /** Detaches and releases the camera capturer from the running call. */
  public void disableOutgoingVideo () {
    if (nativePtr != 0) {
      nativeSetVideoCapture(nativePtr, 0);
    }
    if (videoCapturePtr != 0) {
      nativeSetVideoCaptureLocalOutput(videoCapturePtr, null);
      nativeDestroyVideoCapturer(videoCapturePtr);
      videoCapturePtr = 0;
    }
    videoEnabled = false;
    screencast = false;
  }

  public void switchCamera (boolean useFrontCamera) {
    if (destroyed) {
      return;
    }
    if (videoCapturePtr != 0) {
      nativeSwitchCamera(videoCapturePtr, useFrontCamera);
      frontCamera = useFrontCamera;
    }
  }

  // endregion

  // region incoming participant video

  /**
   * Attaches a renderer (org.webrtc.VideoSink) to the remote video identified by
   * {@code endpointId} (from {@code GroupCallParticipantVideoInfo.endpointId}).
   */
  public void addIncomingVideoOutput (String endpointId, org.webrtc.VideoSink sink) {
    if (!destroyed && nativePtr != 0 && endpointId != null && sink != null) {
      nativeAddIncomingVideoOutput(nativePtr, endpointId, sink);
    }
  }

  /** Drops the renderer for {@code endpointId} (participant stopped video / tile gone). */
  public void removeIncomingVideoOutput (String endpointId) {
    if (!destroyed && nativePtr != 0 && endpointId != null) {
      nativeRemoveIncomingVideoOutput(nativePtr, endpointId);
    }
  }

  /**
   * Sets the set of remote video channels we want to receive. Parallel arrays:
   * {@code endpointIds[i]} at {@code qualities[i]} (one of VIDEO_QUALITY_*), with
   * {@code ssrcGroups[i]} encoded as {@code "SEMANTICS:ssrc,ssrc;..."}.
   */
  public void setRequestedVideoChannels (String[] endpointIds, int[] qualities, String[] ssrcGroups) {
    if (!destroyed && nativePtr != 0 && endpointIds != null) {
      nativeSetRequestedVideoChannels(nativePtr, endpointIds, qualities, ssrcGroups);
    }
  }

  // endregion

  public void stop () {
    if (destroyed) {
      return;
    }
    destroyed = true;
    // Clear any stale screen-capture permission token + teardown callback so a later call
    // can't pick up this call's projection grant or fire a teardown against a dead instance.
    org.telegram.messenger.voip.VideoCameraCapturer.setScreencastStateCallback(null);
    org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
    disableOutgoingVideo();
    if (nativePtr != 0) {
      stopNative(nativePtr);
      nativePtr = 0;
    }
    listener = null;
  }

  // Called from native (app/jni/group_call.cpp) — keep names & signatures in sync.

  @Keep
  void handleEmitJoinPayload (int audioSource, String json) {
    this.audioSource = audioSource;
    final Listener listener = this.listener;
    if (listener != null) {
      listener.onJoinPayloadEmitted(audioSource, json);
    }
  }

  @Keep
  void handleNetworkStateChange (boolean connected) {
    final Listener listener = this.listener;
    if (listener != null) {
      listener.onNetworkStateChanged(connected);
    }
  }

  private native long newInstance (boolean muted, boolean isPresentation);
  private native void emitJoinPayload (long ptr);
  private native void setJoinResponsePayload (long ptr, String json);
  private native void setMuted (long ptr, boolean muted);
  private native void setVolume (long ptr, int audioSource, double volume);
  private native void stopNative (long ptr);

  // Video — outgoing camera (capturer owned by a separate native VideoCaptureContext).
  private native long nativeCreateVideoCapturer (String deviceId, boolean isScreencast);
  private native void nativeDestroyVideoCapturer (long capturePtr);
  private native void nativeSwitchCamera (long capturePtr, boolean useFrontCamera);
  private native void nativeSetVideoState (long capturePtr, int state);
  private native void nativeSetVideoCaptureLocalOutput (long capturePtr, @Nullable org.webrtc.VideoSink sink);
  private native void nativeSetVideoCapture (long ptr, long capturePtr);

  // Video — incoming participant tiles (keyed by endpointId).
  private native void nativeAddIncomingVideoOutput (long ptr, String endpointId, org.webrtc.VideoSink sink);
  private native void nativeRemoveIncomingVideoOutput (long ptr, String endpointId);
  private native void nativeSetRequestedVideoChannels (long ptr, String[] endpointIds, int[] qualities, String[] ssrcGroups);
}
