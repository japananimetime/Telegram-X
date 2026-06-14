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
import org.thunderdog.challegram.service.GroupCallService;
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

    /** Local outgoing-camera state changed. Called on the UI thread. */
    @UiThread
    default void onGroupCallVideoStateChanged (int groupCallId, boolean videoEnabled) { }
  }

  private final Tdlib tdlib;
  private final ReferenceList<Listener> listeners = new ReferenceList<>();

  private @Nullable volatile GroupCallInstance instance;
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

    // Keep the process alive + hold audio focus while the call is active.
    GroupCallService.start(UI.getAppContext());

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
    GroupCallService.stop(UI.getAppContext());
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

  // region video

  @AnyThread
  public boolean isVideoEnabled () {
    final GroupCallInstance instance = this.instance;
    return instance != null && instance.isVideoEnabled();
  }

  /**
   * Enables the local outgoing camera and broadcasts it via TDLib. The caller must
   * hold the CAMERA permission. {@code localSink} mirrors the self preview tile.
   */
  @MainThread
  public void enableOutgoingVideo (boolean useFrontCamera, @Nullable org.webrtc.VideoSink localSink) {
    if (instance == null || state == STATE_NONE) {
      return;
    }
    boolean wasScreencast = instance.isScreencast();
    if (wasScreencast) {
      // Leaving the screencast: stop listening for its teardown and drop the stale projection
      // token so a later capture can't reuse it.
      org.telegram.messenger.voip.VideoCameraCapturer.setScreencastStateCallback(null);
      org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
    }
    instance.enableOutgoingVideo(useFrontCamera, localSink);
    if (wasScreencast) {
      // Switched screen -> camera: drop the mediaProjection FGS type.
      org.thunderdog.challegram.service.GroupCallService.setScreenSharing(false);
    }
    if (groupCallId != 0) {
      tdlib.send(new TdApi.ToggleGroupCallIsMyVideoEnabled(groupCallId, true), tdlib.typedOkHandler());
    }
    notifyVideoListeners();
  }

  /** Whether the current outgoing video source is a screen-share (vs. camera). */
  @AnyThread
  public boolean isScreencast () {
    final GroupCallInstance instance = this.instance;
    return instance != null && instance.isScreencast();
  }

  /**
   * Enables outgoing screen sharing as the broadcast video source (replacing the camera).
   * The MediaProjection permission result must already be stored in
   * {@link org.thunderdog.challegram.voip.VoIPScreenCapture}. Returns {@code true} on success;
   * {@code false} (without enabling) if the mediaProjection FGS type couldn't be asserted or the
   * screencast capturer failed to come up — the caller must surface an error / re-prompt.
   *
   * <p>NOTE: screen sharing in a group call is a DISTINCT TDLib concept from camera video
   * ({@link TdApi.StartGroupCallScreenSharing} / {@link TdApi.EndGroupCallScreenSharing}, which
   * require a SEPARATE tgcalls screencast endpoint producing its own {@code audioSourceId} +
   * join {@code payload}). The native bridge here reuses the main call's single outgoing-video
   * slot and does NOT produce a screencast join payload, so the full
   * {@code StartGroupCallScreenSharing} handshake is OUT OF SCOPE. We therefore do NOT announce
   * screen sharing to TDLib at all here (rather than MISusing
   * {@code ToggleGroupCallIsMyVideoEnabled}, which is the CAMERA-video toggle): the local
   * screencast still streams as the outgoing video, but it is not registered as a separate
   * presentation source server-side. See {@code TODO(group-screencast)} when wiring the full API.
   */
  @MainThread
  public boolean enableOutgoingScreencast (@Nullable org.webrtc.VideoSink localSink) {
    if (instance == null || state == STATE_NONE) {
      return false;
    }
    // Android 10+: add the mediaProjection FGS type before the capturer obtains a MediaProjection.
    // If it fails, ABORT — don't run into a startCapture that will throw on the media thread.
    if (!org.thunderdog.challegram.service.GroupCallService.setScreenSharing(true)) {
      org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
      notifyVideoListeners();
      return false;
    }
    // Register the teardown callback (system-revoke / start-failure) before starting.
    org.telegram.messenger.voip.VideoCameraCapturer.setScreencastStateCallback(this::onScreencastUnavailable);
    instance.enableOutgoingScreencast(localSink);
    // Reconcile: if creation failed, drop the FGS type back and report failure so we don't
    // pretend screen sharing is live. Do NOT announce camera-video to TDLib (see note above).
    if (!instance.isScreencast()) {
      org.telegram.messenger.voip.VideoCameraCapturer.setScreencastStateCallback(null);
      org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
      org.thunderdog.challegram.service.GroupCallService.setScreenSharing(false);
      notifyVideoListeners();
      return false;
    }
    notifyVideoListeners();
    return true;
  }

  /**
   * Drives screen-share teardown when the source becomes unavailable below the controller layer
   * (system revoked the projection, or the screencast capturer failed to start). Invoked on the
   * main thread by {@link org.telegram.messenger.voip.VideoCameraCapturer}.
   */
  @MainThread
  private void onScreencastUnavailable () {
    if (instance != null && instance.isScreencast()) {
      disableOutgoingVideo();
    } else {
      // Already torn down natively; reconcile FGS type / preview state.
      org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
      org.thunderdog.challegram.service.GroupCallService.setScreenSharing(false);
      notifyVideoListeners();
    }
  }

  @MainThread
  public void disableOutgoingVideo () {
    if (instance == null) {
      return;
    }
    boolean wasEnabled = instance.isVideoEnabled();
    boolean wasScreencast = instance.isScreencast();
    if (wasScreencast) {
      // Leaving the screencast: stop listening for its teardown and drop the stale projection
      // token so a later capture can't reuse it.
      org.telegram.messenger.voip.VideoCameraCapturer.setScreencastStateCallback(null);
      org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
    }
    instance.disableOutgoingVideo();
    if (wasScreencast) {
      // Drop the mediaProjection FGS type once screen sharing stops.
      org.thunderdog.challegram.service.GroupCallService.setScreenSharing(false);
    }
    // Only un-announce camera video if the camera (not a screencast) was the announced source:
    // screen sharing is never announced via ToggleGroupCallIsMyVideoEnabled (see
    // enableOutgoingScreencast note), so don't send a spurious video-off for it.
    if (wasEnabled && !wasScreencast && groupCallId != 0 && state != STATE_NONE) {
      tdlib.send(new TdApi.ToggleGroupCallIsMyVideoEnabled(groupCallId, false), tdlib.typedOkHandler());
    }
    notifyVideoListeners();
  }

  /** Re-routes the local camera preview into a (new) sink without restarting capture. */
  @MainThread
  public void setLocalPreviewSink (@Nullable org.webrtc.VideoSink localSink) {
    if (instance != null) {
      instance.setLocalPreviewSink(localSink);
    }
  }

  /** Whether the local camera is front-facing (for self-tile mirroring). */
  @AnyThread
  public boolean isFrontCamera () {
    final GroupCallInstance instance = this.instance;
    return instance == null || instance.isFrontCamera();
  }

  @MainThread
  public void switchCamera (boolean useFrontCamera) {
    if (instance != null) {
      instance.switchCamera(useFrontCamera);
    }
  }

  /** Attaches a renderer to a participant's remote video by endpointId. */
  @MainThread
  public void addIncomingVideoOutput (String endpointId, org.webrtc.VideoSink sink) {
    if (instance != null) {
      instance.addIncomingVideoOutput(endpointId, sink);
    }
  }

  /** Detaches the renderer for a participant's remote video by endpointId. */
  @MainThread
  public void removeIncomingVideoOutput (String endpointId) {
    if (instance != null) {
      instance.removeIncomingVideoOutput(endpointId);
    }
  }

  /** Updates the set of remote video channels we want to receive (visible tiles). */
  @MainThread
  public void setRequestedVideoChannels (String[] endpointIds, int[] qualities, String[] ssrcGroups) {
    if (instance != null) {
      instance.setRequestedVideoChannels(endpointIds, qualities, ssrcGroups);
    }
  }

  @UiThread
  private void notifyVideoListeners () {
    final int groupCallId = this.groupCallId;
    final boolean videoEnabled = isVideoEnabled();
    for (Listener listener : listeners) {
      listener.onGroupCallVideoStateChanged(groupCallId, videoEnabled);
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
