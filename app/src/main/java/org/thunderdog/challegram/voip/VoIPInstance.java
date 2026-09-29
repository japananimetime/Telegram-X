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

import android.os.SystemClock;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.voip.annotation.AudioState;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.annotation.VideoState;

import me.vkryl.core.lambda.Destroyable;

public abstract class VoIPInstance implements Destroyable {
  protected final Tdlib tdlib;
  protected final TdApi.Call call;
  protected final CallConfiguration configuration;
  protected final CallOptions options;
  protected final @NonNull ConnectionStateListener connectionStateListener;

  public VoIPInstance (@NonNull Tdlib tdlib,
                       @NonNull TdApi.Call call,
                       @NonNull CallConfiguration configuration,
                       @NonNull CallOptions options,
                       @NonNull ConnectionStateListener stateListener) {
    this.tdlib = tdlib;
    this.call = call;
    this.configuration = configuration;
    this.options = options;
    this.connectionStateListener = stateListener;
  }

  public abstract void initializeAndConnect ();

  // Getters

  public final Tdlib tdlib () {
    return tdlib;
  }

  public final @NonNull TdApi.Call getCall () {
    return call;
  }

  public final @NonNull CallConfiguration getConfiguration () {
    return configuration;
  }

  public final @NonNull CallOptions getOptions () {
    return options;
  }

  // Connection state

  private long callStartTime;

  protected final void dispatchCallStateChanged (@CallState int state) {
    // this.callState = state;
    if (state == CallState.ESTABLISHED && callStartTime == 0) {
      callStartTime = SystemClock.elapsedRealtime();
    }
    connectionStateListener.onConnectionStateChanged(this, state);
  }

  public static long DURATION_UNKNOWN = -1;

  public final long getCallDuration () {
    return callStartTime != 0 ? SystemClock.elapsedRealtime() - callStartTime : DURATION_UNKNOWN;
  }

  // Setters

  public final void setAudioOutputGainControlEnabled (boolean isEnabled) {
    options.audioGainControlEnabled = isEnabled;
    handleAudioOutputGainControlEnabled(isEnabled);
  }
  protected abstract void handleAudioOutputGainControlEnabled (boolean isEnabled);

  public final void setEchoCancellationStrength (int strength) {
    options.echoCancellationStrength = strength;
    handleEchoCancellationStrengthChange(strength);
  }
  protected abstract void handleEchoCancellationStrengthChange (int strength);

  public final void setMicDisabled (boolean isDisabled) {
    options.isMicDisabled = isDisabled;
    handleMicDisabled(isDisabled);
  }
  protected abstract void handleMicDisabled (boolean isDisabled);

  public void setNetworkType (@CallNetworkType int type) {
    options.networkType = type;
    handleNetworkTypeChange(type);
  }

  protected abstract void handleNetworkTypeChange (@CallNetworkType int type);

  // Getters

  public abstract CharSequence collectDebugLog ();
  public abstract long getConnectionId ();
  public abstract void getNetworkStats (NetworkStats out);

  public abstract String getLibraryName ();
  public abstract String getLibraryVersion ();

  // Video (1:1 calls). Default no-ops so non-tgcalls backends stay audio-only;
  // TgCallsController overrides these to drive the native camera + render pipeline.

  /** Whether the local camera or screen is currently being captured and sent. */
  public boolean isVideoOutgoing () {
    return false;
  }

  /** Whether the current outgoing video source is a screen-share (vs. camera). */
  public boolean isScreenSharing () {
    return false;
  }

  /** Starts capturing + sending the local camera. */
  public void enableOutgoingVideo (boolean useFrontCamera) { }

  /**
   * Starts capturing + sending the screen (mutually exclusive with the camera).
   * Requires the MediaProjection permission result to be stored in
   * {@link VoIPScreenCapture} beforehand.
   */
  public void enableOutgoingScreencast () { }

  /** Stops capturing + sending the local camera. */
  public void disableOutgoingVideo () { }

  /** Switches between front and back camera. No-op when video is off. */
  public void switchCamera (boolean useFrontCamera) { }

  /** Routes incoming (remote) frames to the given sink, or clears when null. */
  public void setIncomingVideoOutput (@Nullable org.webrtc.VideoSink sink) { }

  /** Routes local (preview) frames to the given sink, or clears when null. */
  public void setLocalVideoOutput (@Nullable org.webrtc.VideoSink sink) { }

  /** Last known remote video state (see {@link VideoState}). */
  public @VideoState int getRemoteVideoState () {
    return VideoState.INACTIVE;
  }

  // called from native code

  @Keep
  protected final void handleStateChange (@CallState int state) {
    dispatchCallStateChanged(state);
  }

  @Keep
  protected final void handleSignalBarsChange (int count) {
    connectionStateListener.onSignalBarCountChanged(count);
  }

  @Keep
  protected final void handleEmittedSignalingData (byte[] buffer) {
    connectionStateListener.onSignallingDataEmitted(buffer);
  }

  // called from TDLib

  public abstract void handleIncomingSignalingData (byte[] buffer);
}
