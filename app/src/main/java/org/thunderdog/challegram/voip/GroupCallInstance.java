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

  private long nativePtr;
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

  public void stop () {
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
}
