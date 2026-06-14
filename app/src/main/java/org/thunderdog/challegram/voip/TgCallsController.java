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
 *
 * File created on 28/03/2023
 */
package org.thunderdog.challegram.voip;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.voip.annotation.AudioState;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.thunderdog.challegram.voip.annotation.VideoState;

@SuppressWarnings("JavaJniMissingFunction")
public class TgCallsController extends VoIPInstance {
  private final String version;
  private long nativePtr;
  private long videoCapturePtr;
  public TgCallsController (@NonNull Tdlib tdlib, @NonNull TdApi.Call call, @NonNull CallConfiguration configuration, @NonNull CallOptions options, @NonNull ConnectionStateListener stateListener, String version) {
    super(tdlib, call, configuration, options, stateListener);
    if (configuration.state.encryptionKey.length != 256)
      throw new IllegalArgumentException(Integer.toString(configuration.state.encryptionKey.length));
    this.version = version;
    this.nativePtr = newInstance(version, configuration, options);
  }

  private long nativePtr () {
    long ptr = nativePtr;
    if (ptr == 0)
      throw new IllegalStateException();
    return ptr;
  }

  private native long newInstance (
    @NonNull String version,
    @NonNull CallConfiguration configuration,
    @NonNull CallOptions options
  );

  private native long preferredConnectionId (long ptr);
  private native @Nullable String lastError (long ptr);
  private native @Nullable String debugLog (long ptr);
  private native void fetchNetworkStats (long ptr, NetworkStats out);

  private native void processIncomingSignalingData (long ptr, byte[] buffer);

  private native void updateNetworkType (long ptr, @CallNetworkType int newType);
  private native void updateMicrophoneDisabled (long ptr, boolean isDisabled);
  private native void updateEchoCancellationStrength (long ptr, int strength);
  private native void updateAudioOutputGainControlEnabled (long ptr, boolean isEnabled);
  private native void destroyInstance (long ptr);

  // Video (Stage 1: native plumbing). The capturer is owned by a separate native
  // pointer (videoCapturePtr) created via nativeCreateVideoCapturer and handed to
  // the instance via nativeSetVideoCapture.
  private native long nativeCreateVideoCapturer (String deviceId, boolean isScreencast);
  private native void nativeDestroyVideoCapturer (long capturePtr);
  private native void nativeSwitchCamera (long capturePtr, boolean useFrontCamera);
  private native void nativeSetVideoState (long capturePtr, @VideoState int state);
  private native void nativeSetVideoCapture (long ptr, long capturePtr);
  private native void nativeSetIncomingVideoOutput (long ptr, @Nullable org.webrtc.VideoSink sink);
  private native void nativeSetVideoCaptureLocalOutput (long capturePtr, @Nullable org.webrtc.VideoSink sink);

  @Override
  public String getLibraryName () {
    return "tgcalls";
  }

  @Override
  public String getLibraryVersion () {
    return version;
  }

  @Override
  public void initializeAndConnect () {
    // Nothing to do?
  }

  @Override
  protected void handleAudioOutputGainControlEnabled (boolean isEnabled) {
    updateAudioOutputGainControlEnabled(nativePtr(), isEnabled);
  }

  @Override
  protected void handleEchoCancellationStrengthChange (int strength) {
    updateEchoCancellationStrength(nativePtr(), strength);
  }

  @Override
  protected void handleMicDisabled (boolean isDisabled) {
    updateMicrophoneDisabled(nativePtr(), isDisabled);
  }

  @Override
  protected void handleNetworkTypeChange (@CallNetworkType int type) {
    updateNetworkType(nativePtr(), type);
  }

  @Override
  public long getConnectionId () {
    return preferredConnectionId(nativePtr());
  }

  @Nullable
  public String getLastError () {
    return lastError(nativePtr());
  }

  @Override
  public CharSequence collectDebugLog () {
    return debugLog(nativePtr());
  }

  @Override
  public void getNetworkStats (NetworkStats out) {
    fetchNetworkStats(nativePtr(), out);
  }

  // Video (Stage 1: native plumbing; Stage 3 wires these into CallController via
  // the VoIPInstance overrides). These expose the camera + remote-video pipeline.

  private @Nullable org.webrtc.VideoSink pendingLocalSink;
  // Written on the native VoIP thread (handleRemoteMediaStateChange), read on the UI thread.
  private volatile @VideoState int remoteVideoState = VideoState.INACTIVE;

  @Override
  public boolean isVideoOutgoing () {
    return videoCapturePtr != 0;
  }

  /**
   * Creates (if needed) the camera capturer and attaches it to the running call.
   *
   * @param useFrontCamera whether to start on the front-facing camera.
   */
  @Override
  public void enableOutgoingVideo (boolean useFrontCamera) {
    if (videoCapturePtr == 0) {
      videoCapturePtr = nativeCreateVideoCapturer(useFrontCamera ? "front" : "back", false);
      if (videoCapturePtr != 0 && pendingLocalSink != null) {
        nativeSetVideoCaptureLocalOutput(videoCapturePtr, pendingLocalSink);
      }
    }
    if (videoCapturePtr != 0) {
      nativeSetVideoState(videoCapturePtr, VideoState.ACTIVE);
      nativeSetVideoCapture(nativePtr(), videoCapturePtr);
    }
  }

  /**
   * Detaches and releases the camera capturer from the running call.
   */
  @Override
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

  /**
   * Switches between front and back camera. No-op if video is not active.
   */
  @Override
  public void switchCamera (boolean useFrontCamera) {
    if (videoCapturePtr != 0) {
      nativeSwitchCamera(videoCapturePtr, useFrontCamera);
    }
  }

  /**
   * Updates the local capture state. No-op if video is not active.
   */
  public void setVideoState (@VideoState int state) {
    if (videoCapturePtr != 0) {
      nativeSetVideoState(videoCapturePtr, state);
    }
  }

  /**
   * Routes incoming (remote) video frames to the given sink, or clears the output
   * when {@code sink} is null.
   */
  @Override
  public void setIncomingVideoOutput (@Nullable org.webrtc.VideoSink sink) {
    if (nativePtr != 0) {
      nativeSetIncomingVideoOutput(nativePtr, sink);
    }
  }

  /**
   * Routes local (preview) video frames to the given sink. Stored until the
   * capturer exists so it survives an enable/disable cycle.
   */
  @Override
  public void setLocalVideoOutput (@Nullable org.webrtc.VideoSink sink) {
    this.pendingLocalSink = sink;
    if (videoCapturePtr != 0) {
      nativeSetVideoCaptureLocalOutput(videoCapturePtr, sink);
    }
  }

  @Override
  public @VideoState int getRemoteVideoState () {
    return remoteVideoState;
  }

  @Override
  public void performDestroy () {
    if (videoCapturePtr != 0) {
      if (nativePtr != 0) {
        nativeSetVideoCapture(nativePtr, 0);
      }
      nativeDestroyVideoCapturer(videoCapturePtr);
      videoCapturePtr = 0;
    }
    if (nativePtr != 0) {
      destroyInstance(nativePtr);
      nativePtr = 0;
    }
  }

  // Called from tgvoip.cpp

  @Keep
  protected final void handleRemoteMediaStateChange (@AudioState int audioState, @VideoState int videoState) {
    this.remoteVideoState = videoState;
    connectionStateListener.onRemoteMediaStateChanged(this, audioState, videoState);
  }

  @Keep
  protected final void handleAudioLevelChange (float audioLevel) {
    // TODO
  }
  @Keep
  protected final void handleStop (@NonNull NetworkStats totalStats, @Nullable String debugLog) {
    connectionStateListener.onStopped(this, totalStats, debugLog);
  }

  // Called from TDLib

  @Override
  public void handleIncomingSignalingData (byte[] buffer) {
    processIncomingSignalingData(nativePtr(), buffer);
  }
}
