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
package org.thunderdog.challegram.telegram;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.GroupCallInstance;

import me.vkryl.core.reference.ReferenceList;

/**
 * Orchestrates joining/leaving a video chat (group call bound to a chat) and the
 * local microphone state, bridging TDLib's {@code JoinVideoChat} handshake to the
 * native tgcalls group engine ({@link GroupCallInstance}).
 *
 * <p>One active call at a time per account. The caller (UI) is responsible for
 * obtaining the RECORD_AUDIO permission before {@link #joinVideoChat(int)}.</p>
 *
 * <p>Slice 2b: join/leave/mute against TDLib + native. The foreground service
 * (background survival, audio focus, notification) and the interactive UI are
 * separate follow-ups.</p>
 */
public class GroupCallManager implements GroupCallInstance.Listener {
  public static final int STATE_NONE = 0;
  public static final int STATE_JOINING = 1;
  public static final int STATE_CONNECTED = 2;

  public interface Listener {
    /** Join state of a video chat changed (one of STATE_*). Called on the UI thread. */
    @UiThread
    void onGroupCallJoinStateChanged (int groupCallId, int state, boolean micMuted);
  }

  private final Tdlib tdlib;
  private final ReferenceList<Listener> listeners = new ReferenceList<>();

  private @Nullable GroupCallInstance instance;
  private int groupCallId;
  private int state = STATE_NONE;
  private boolean micMuted = true;

  public GroupCallManager (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  public void addListener (Listener listener) {
    listeners.add(listener);
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  @AnyThread
  public int getActiveGroupCallId () {
    return state != STATE_NONE ? groupCallId : 0;
  }

  @AnyThread
  public int getState (int groupCallId) {
    return this.groupCallId == groupCallId ? state : STATE_NONE;
  }

  @AnyThread
  public boolean isMicMuted () {
    return micMuted;
  }

  // region join / leave

  /**
   * Joins the given video chat. Microphone starts muted. The caller must hold the
   * RECORD_AUDIO permission. If another call is active, it is left first.
   */
  @MainThread
  public void joinVideoChat (int groupCallId) {
    if (groupCallId == 0) {
      return;
    }
    if (this.groupCallId == groupCallId && state != STATE_NONE) {
      return; // already joining/joined this call
    }
    if (state != STATE_NONE) {
      leave();
    }

    this.micMuted = true;
    GroupCallInstance instance = new GroupCallInstance(micMuted);
    if (!instance.isValid()) {
      instance.stop();
      return;
    }
    instance.setListener(this);
    this.instance = instance;
    this.groupCallId = groupCallId;
    setState(STATE_JOINING);

    // Kicks off the handshake; the payload arrives in onJoinPayloadEmitted().
    instance.emitJoinPayload();
  }

  /** Leaves the active call, stopping the native engine and notifying TDLib. */
  @MainThread
  public void leave () {
    final int leftGroupCallId = groupCallId;
    final boolean wasActive = state != STATE_NONE;
    if (instance != null) {
      instance.stop();
      instance = null;
    }
    this.groupCallId = 0;
    setState(STATE_NONE);
    if (wasActive && leftGroupCallId != 0) {
      tdlib.send(new TdApi.LeaveGroupCall(leftGroupCallId), tdlib.typedOkHandler());
    }
  }

  /** Mutes/unmutes the local microphone, updating both the engine and TDLib. */
  @MainThread
  public void setMicMuted (boolean muted) {
    this.micMuted = muted;
    if (instance != null) {
      instance.setMuted(muted);
    }
    if (groupCallId != 0 && state != STATE_NONE) {
      TdApi.MessageSender self = new TdApi.MessageSenderUser(tdlib.myUserId());
      tdlib.send(new TdApi.ToggleGroupCallParticipantIsMuted(groupCallId, self, muted), tdlib.typedOkHandler());
    }
    notifyListeners();
  }

  /** Adjusts the playback volume of a participant (0.0 .. 1.0+, 1.0 = normal). */
  @MainThread
  public void setParticipantVolume (int audioSource, double volume) {
    if (instance != null) {
      instance.setVolume(audioSource, volume);
    }
  }

  // endregion

  // region GroupCallInstance.Listener (native thread → marshal to UI)

  @Override
  public void onJoinPayloadEmitted (int audioSource, String json) {
    final int targetGroupCallId = this.groupCallId;
    UI.post(() -> {
      if (instance == null || groupCallId != targetGroupCallId || state == STATE_NONE) {
        return; // left before the payload arrived
      }
      TdApi.GroupCallJoinParameters params =
        new TdApi.GroupCallJoinParameters(audioSource, json, micMuted, false);
      tdlib.send(new TdApi.JoinVideoChat(targetGroupCallId, null, params, ""), (result, error) -> UI.post(() -> {
        if (instance == null || groupCallId != targetGroupCallId) {
          return;
        }
        if (error != null) {
          leave();
          return;
        }
        if (result != null) {
          instance.setJoinResponsePayload(result.text);
        }
      }));
    });
  }

  @Override
  public void onNetworkStateChanged (boolean connected) {
    final int targetGroupCallId = this.groupCallId;
    UI.post(() -> {
      if (groupCallId != targetGroupCallId || state == STATE_NONE) {
        return;
      }
      setState(connected ? STATE_CONNECTED : STATE_JOINING);
    });
  }

  // endregion

  @UiThread
  private void setState (int state) {
    if (this.state != state) {
      this.state = state;
      notifyListeners();
    }
  }

  @UiThread
  private void notifyListeners () {
    final int groupCallId = this.groupCallId;
    final int state = this.state;
    final boolean micMuted = this.micMuted;
    for (Listener listener : listeners) {
      listener.onGroupCallJoinStateChanged(groupCallId, state, micMuted);
    }
  }
}
