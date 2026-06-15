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

  // Routes the presentation instance's handshake to StartGroupCallScreenSharing (instead of
  // JoinVideoChat, which the main instance uses). Kept as a field so the same listener instance
  // is reused for the lifetime of the manager.
  private final GroupCallInstance.Listener presentationListener = new GroupCallInstance.Listener() {
    @Override
    public void onJoinPayloadEmitted (int audioSource, String json) {
      onPresentationJoinPayloadEmitted(audioSource, json);
    }

    @Override
    public void onNetworkStateChanged (boolean connected) {
      // The presentation connection's own network state isn't surfaced separately; the main
      // instance drives the call-level STATE_*. Nothing to do here.
    }
  };

  private @Nullable volatile GroupCallInstance instance;
  // The SECOND group connection used for screen sharing (videoContentType =
  // Screencast). Lives independently of the main instance so the user's camera and
  // their screen presentation can be broadcast simultaneously as distinct sources.
  // Non-null only while a screen share is active (or starting).
  private @Nullable volatile GroupCallInstance presentationInstance;
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
    // Tear down any active screen-sharing presentation first (drops the FGS type + projection,
    // ends it server-side). LeaveGroupCall below ends the whole call, so a separate
    // EndGroupCallScreenSharing is redundant — just stop the presentation locally.
    if (presentationInstance != null) {
      tearDownPresentation();
    }
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
   *
   * <p>The camera lives on the MAIN instance and is independent of a screen
   * presentation (which is its own connection) — they can be broadcast at once.</p>
   */
  @MainThread
  public void enableOutgoingVideo (boolean useFrontCamera, @Nullable org.webrtc.VideoSink localSink) {
    if (instance == null || state == STATE_NONE) {
      return;
    }
    instance.enableOutgoingVideo(useFrontCamera, localSink);
    if (groupCallId != 0) {
      tdlib.send(new TdApi.ToggleGroupCallIsMyVideoEnabled(groupCallId, true), tdlib.typedOkHandler());
    }
    notifyVideoListeners();
  }

  /**
   * Whether a screen presentation is currently active (a second screen-sharing
   * connection is up or coming up). This is now SEPARATE from the camera, which
   * lives on the main instance — see {@link #isVideoEnabled()}.
   */
  @AnyThread
  public boolean isScreenSharing () {
    return presentationInstance != null;
  }

  /**
   * Starts a server-side screen-sharing presentation: a SECOND group connection
   * (videoContentType = Screencast) joins the call via
   * {@link TdApi.StartGroupCallScreenSharing} and streams the screen as a distinct
   * presentation source, coexisting with the user's camera on the main instance.
   *
   * <p>The MediaProjection permission result must already be stored in
   * {@link org.thunderdog.challegram.voip.VoIPScreenCapture}. Returns {@code true} once the
   * presentation instance + capturer are up and the join handshake has been kicked off;
   * {@code false} (after rolling everything back) if the mediaProjection FGS type couldn't be
   * asserted, the native presentation instance couldn't be created, or the screencast capturer
   * failed to come up — the caller must surface an error / re-prompt.</p>
   *
   * <p>Handshake: the presentation instance emits a join payload (ssrc + JSON); the ssrc becomes
   * the {@code audioSourceId} and the JSON the {@code payload} for
   * {@code StartGroupCallScreenSharing(groupCallId, audioSourceId, payload)}, whose
   * {@code Text} response is fed back via {@code setJoinResponsePayload}.</p>
   */
  @MainThread
  public boolean startScreenSharing (@Nullable org.webrtc.VideoSink localSink) {
    if (instance == null || state == STATE_NONE || groupCallId == 0) {
      return false;
    }
    if (presentationInstance != null) {
      return true; // already sharing
    }
    // Android 10+: add the mediaProjection FGS type before the capturer obtains a MediaProjection.
    // If it fails, ABORT — don't run into a startCapture that will throw on the media thread.
    if (!org.thunderdog.challegram.service.GroupCallService.setScreenSharing(true)) {
      org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
      notifyVideoListeners();
      return false;
    }
    // Create the SECOND (screencast) connection. Muted: a screen presentation carries no mic.
    GroupCallInstance presentation = new GroupCallInstance(true, true);
    if (!presentation.isValid()) {
      presentation.stop();
      org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
      org.thunderdog.challegram.service.GroupCallService.setScreenSharing(false);
      notifyVideoListeners();
      return false;
    }
    presentation.setListener(presentationListener);
    this.presentationInstance = presentation;
    // Register the teardown callback (system-revoke / start-failure) before starting capture.
    org.telegram.messenger.voip.VideoCameraCapturer.setScreencastStateCallback(this::onScreencastUnavailable);
    // Attach the screencast capturer (ScreenCapturerAndroid via the static screencast flag).
    presentation.enableOutgoingScreencast(localSink);
    if (!presentation.isScreencast()) {
      // Capturer failed to come up — roll the whole presentation back.
      tearDownPresentation();
      notifyVideoListeners();
      return false;
    }
    // Kick off the screencast join handshake; payload arrives in presentationListener.
    presentation.emitJoinPayload();
    notifyVideoListeners();
    return true;
  }

  /** Stops the active screen-sharing presentation (if any): notifies TDLib and tears it down. */
  @MainThread
  public void stopScreenSharing () {
    if (presentationInstance == null) {
      return;
    }
    final int targetGroupCallId = groupCallId;
    tearDownPresentation();
    if (targetGroupCallId != 0) {
      tdlib.send(new TdApi.EndGroupCallScreenSharing(targetGroupCallId), tdlib.typedOkHandler());
    }
    notifyVideoListeners();
  }

  /** Pauses/resumes the presentation video without leaving the screen-sharing connection. */
  @MainThread
  public void setScreenSharingPaused (boolean paused) {
    if (presentationInstance == null || groupCallId == 0) {
      return;
    }
    tdlib.send(new TdApi.ToggleGroupCallScreenSharingIsPaused(groupCallId, paused), tdlib.typedOkHandler());
  }

  /**
   * Tears down the presentation instance + capturer and drops the mediaProjection FGS type,
   * WITHOUT notifying TDLib (callers that need the EndGroupCallScreenSharing request send it
   * themselves). Idempotent.
   */
  @MainThread
  private void tearDownPresentation () {
    final GroupCallInstance presentation = this.presentationInstance;
    this.presentationInstance = null;
    // Stop listening for this presentation's teardown and drop the stale projection token so a
    // later capture can't reuse it.
    org.telegram.messenger.voip.VideoCameraCapturer.setScreencastStateCallback(null);
    org.thunderdog.challegram.voip.VoIPScreenCapture.clear();
    if (presentation != null) {
      presentation.stop();
    }
    // Drop the mediaProjection FGS type once screen sharing stops.
    org.thunderdog.challegram.service.GroupCallService.setScreenSharing(false);
  }

  /**
   * Drives screen-share teardown when the source becomes unavailable below the controller layer
   * (system revoked the projection, or the screencast capturer failed to start). Invoked on the
   * main thread by {@link org.telegram.messenger.voip.VideoCameraCapturer}. Notifies TDLib so the
   * server-side presentation is ended too.
   */
  @MainThread
  private void onScreencastUnavailable () {
    stopScreenSharing();
  }

  @MainThread
  public void disableOutgoingVideo () {
    if (instance == null) {
      return;
    }
    boolean wasEnabled = instance.isVideoEnabled();
    instance.disableOutgoingVideo();
    if (wasEnabled && groupCallId != 0 && state != STATE_NONE) {
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

  /** Re-routes the screen-presentation preview into a (new) sink without restarting capture. */
  @MainThread
  public void setScreenPreviewSink (@Nullable org.webrtc.VideoSink localSink) {
    final GroupCallInstance presentation = this.presentationInstance;
    if (presentation != null) {
      presentation.setLocalPreviewSink(localSink);
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

  /**
   * Relays the presentation instance's join payload to TDLib via StartGroupCallScreenSharing.
   * The emitted ssrc is the {@code audioSourceId}; the JSON is the {@code payload}. The Text
   * response is fed back into the presentation engine via setJoinResponsePayload.
   */
  private void onPresentationJoinPayloadEmitted (int audioSource, String json) {
    final int targetGroupCallId = this.groupCallId;
    UI.post(() -> {
      final GroupCallInstance presentation = this.presentationInstance;
      if (presentation == null || groupCallId != targetGroupCallId || state == STATE_NONE) {
        return; // screen share stopped before the payload arrived
      }
      tdlib.send(new TdApi.StartGroupCallScreenSharing(targetGroupCallId, audioSource, json), (result, error) -> UI.post(() -> {
        if (presentationInstance != presentation || groupCallId != targetGroupCallId) {
          return; // stopped / replaced in the meantime
        }
        if (error != null) {
          // Server rejected the presentation: tear it down locally so we don't pretend it's live.
          tearDownPresentation();
          notifyVideoListeners();
          return;
        }
        if (result != null) {
          presentation.setJoinResponsePayload(result.text);
        }
      }));
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
