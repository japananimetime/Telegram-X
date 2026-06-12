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

/**
 * Java handle to the native tgcalls GROUP engine (GroupInstanceCustomImpl), used
 * to join video chats / voice chats. Native methods are implemented in
 * {@code app/jni/group_call.cpp} and bound statically by name into
 * {@code libtgcallsjni.so}.
 *
 * Slice 2a (initial): lifecycle only — create, mute toggle, stop. The TDLib
 * JoinVideoChat handshake (emit/setJoinResponsePayload), participant volume, and
 * audio-level callbacks land in subsequent slices.
 */
public class GroupCallInstance {
  private long nativePtr;

  /**
   * Creates the native group-call instance.
   *
   * @param muted whether the local microphone starts muted
   */
  public GroupCallInstance (boolean muted) {
    this.nativePtr = newInstance(muted);
  }

  public boolean isValid () {
    return nativePtr != 0;
  }

  public void setMuted (boolean muted) {
    if (nativePtr != 0) {
      setMuted(nativePtr, muted);
    }
  }

  public void stop () {
    if (nativePtr != 0) {
      stopNative(nativePtr);
      nativePtr = 0;
    }
  }

  private native long newInstance (boolean muted);
  private native void setMuted (long ptr, boolean muted);
  private native void stopNative (long ptr);
}
