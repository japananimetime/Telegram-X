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
  private @Nullable Listener listener;

  /**
   * Creates the native group-call instance.
   *
   * @param muted whether the local microphone starts muted
   */
  public GroupCallInstance (boolean muted) {
    this.nativePtr = newInstance(muted);
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
    return videoCapturePtr != 0;
  }

  /**
   * Creates (if needed) the camera capturer and attaches it to the running call,
   * starting capture. Pass the local-preview sink to mirror the self tile.
   */
  public void enableOutgoingVideo (boolean useFrontCamera, @Nullable org.webrtc.VideoSink localSink) {
    if (nativePtr == 0) {
      return;
    }
    if (videoCapturePtr == 0) {
      videoCapturePtr = nativeCreateVideoCapturer(useFrontCamera ? "front" : "back", false);
    }
    if (videoCapturePtr != 0) {
      if (localSink != null) {
        nativeSetVideoCaptureLocalOutput(videoCapturePtr, localSink);
      }
      nativeSetVideoState(videoCapturePtr, 2 /* VideoState.ACTIVE */);
      nativeSetVideoCapture(nativePtr, videoCapturePtr);
    }
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
  }

  public void switchCamera (boolean useFrontCamera) {
    if (videoCapturePtr != 0) {
      nativeSwitchCamera(videoCapturePtr, useFrontCamera);
    }
  }

  // endregion

  // region incoming participant video

  /**
   * Attaches a renderer (org.webrtc.VideoSink) to the remote video identified by
   * {@code endpointId} (from {@code GroupCallParticipantVideoInfo.endpointId}).
   */
  public void addIncomingVideoOutput (String endpointId, org.webrtc.VideoSink sink) {
    if (nativePtr != 0 && endpointId != null && sink != null) {
      nativeAddIncomingVideoOutput(nativePtr, endpointId, sink);
    }
  }

  /** Drops the renderer for {@code endpointId} (participant stopped video / tile gone). */
  public void removeIncomingVideoOutput (String endpointId) {
    if (nativePtr != 0 && endpointId != null) {
      nativeRemoveIncomingVideoOutput(nativePtr, endpointId);
    }
  }

  /**
   * Sets the set of remote video channels we want to receive. Parallel arrays:
   * {@code endpointIds[i]} at {@code qualities[i]} (one of VIDEO_QUALITY_*), with
   * {@code ssrcGroups[i]} encoded as {@code "SEMANTICS:ssrc,ssrc;..."}.
   */
  public void setRequestedVideoChannels (String[] endpointIds, int[] qualities, String[] ssrcGroups) {
    if (nativePtr != 0 && endpointIds != null) {
      nativeSetRequestedVideoChannels(nativePtr, endpointIds, qualities, ssrcGroups);
    }
  }

  // endregion

  public void stop () {
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

  private native long newInstance (boolean muted);
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
