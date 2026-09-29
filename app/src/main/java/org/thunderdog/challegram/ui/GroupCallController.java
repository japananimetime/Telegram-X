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
package org.thunderdog.challegram.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.util.SenderAvatarDrawModifier;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.GroupCallListener;
import org.thunderdog.challegram.telegram.GroupCallManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.voip.GroupCallInstance;
import org.thunderdog.challegram.widget.voip.GroupCallVideoView;
import org.webrtc.VideoSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

/**
 * Read-only view of a group call / video chat (GetGroupCall): title, type
 * (voice chat / video chat / live stream / RTMP), live status (active / scheduled
 * for a date / ended), participant count, and the recent speakers. Subscribes to
 * GroupCallListener so the state refreshes live.
 *
 * This is the read-only slice of the group-calls feature — joining, muting, and
 * the full participant roster (which require the WebRTC join pipeline) are
 * separate follow-up slices.
 */
public class GroupCallController extends RecyclerViewController<GroupCallController.Args> implements GroupCallListener, GroupCallManager.Listener, View.OnClickListener {

  public static class Args {
    public final int groupCallId;
    public Args (int groupCallId) {
      this.groupCallId = groupCallId;
    }
  }

  private SettingsAdapter adapter;
  private int groupCallId;
  private @Nullable TdApi.GroupCall groupCall;

  // Video tiles. The container overlays the top of the recycler; tiles are keyed by
  // endpointId (remote) or GroupCallVideoView.SELF_ENDPOINT (local preview).
  private @Nullable GroupCallVideoView videoView;
  // Roster of participants we've seen (accumulated from updates + initial load),
  // keyed by a stable sender key. Used to drive tile sync + requested channels.
  private final Map<String, TdApi.GroupCallParticipant> participants = new LinkedHashMap<>();
  // endpointIds we currently have an incoming sink attached for.
  private final java.util.Set<String> attachedEndpoints = new java.util.HashSet<>();

  public GroupCallController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    if (groupCall != null && !StringUtils.isEmpty(groupCall.title)) {
      return groupCall.title;
    }
    return Lang.getString(R.string.VideoChat);
  }

  @Override
  public int getId () {
    return R.id.controller_groupCall;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.groupCallId = args.groupCallId;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        // Info rows (Type/Status/Participants) and recent-speaker rows carry their value text in
        // stringValue; the base adapter leaves valued settings blank unless this is overridden.
        view.setData(item.getStringValue());
        // Recent-speaker rows additionally show the participant's avatar on the left.
        if (item.getId() == R.id.btn_groupCallParticipant && item.getData() instanceof TdApi.MessageSender) {
          view.forcePadding(Screen.dp(SenderAvatarDrawModifier.LEFT_PADDING_DP), 0);
          view.setDrawModifier(new SenderAvatarDrawModifier((TdApi.MessageSender) item.getData()).requestFiles(view.getComplexReceiver(), tdlib));
        } else {
          view.forcePadding(0, 0);
          view.setDrawModifier(null);
        }
      }
    };
    recyclerView.setAdapter(adapter);
    buildLoadingCells();
    tdlib.listeners().subscribeToGroupCallUpdates(groupCallId, this);
    tdlib.groupCalls().addListener(this);
    tdlib.send(new TdApi.GetGroupCall(groupCallId), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        showError(TD.toErrorString(error));
        return;
      }
      groupCall = result;
      setName(getName());
      buildCells();
      loadParticipants();
    }));
  }

  /** Lazily creates the video-tiles overlay on top of the recycler. */
  private GroupCallVideoView ensureVideoView () {
    if (videoView == null && !isDestroyed()) {
      videoView = new GroupCallVideoView(context());
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(184f), Gravity.TOP);
      videoView.setLayoutParams(params);
      View root = getValue();
      if (root instanceof ViewGroup) {
        ((ViewGroup) root).addView(videoView);
      }
    }
    return videoView;
  }

  private void loadParticipants () {
    tdlib.send(new TdApi.LoadGroupCallParticipants(groupCallId, 100), (result, error) -> { /* updates arrive via listener */ });
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromGroupCallUpdates(groupCallId, this);
    tdlib.groupCalls().removeListener(this);
    // Detach all native sinks BEFORE releasing the renderers (frames must stop
    // first), including the SELF camera preview. The capturer is kept alive by
    // GroupCallService after this controller closes, so if the self sink were left
    // attached it would keep pushing frames into the released SurfaceViewRenderer
    // (UAF). Routing the local output to null drops the self sink.
    final GroupCallManager calls = tdlib.groupCalls();
    if (calls.getState(groupCallId) != GroupCallManager.STATE_NONE) {
      for (String endpointId : attachedEndpoints) {
        calls.removeIncomingVideoOutput(endpointId);
      }
      // Drop only the self-preview sink — keep broadcasting the camera to the call
      // (the call survives this read-only screen via GroupCallService); we just must
      // stop feeding the renderer that is about to be released.
      if (calls.isVideoEnabled()) {
        calls.setLocalPreviewSink(null);
      }
    }
    attachedEndpoints.clear();
    participants.clear();
    if (videoView != null) {
      videoView.release();
      videoView = null;
    }
  }

  @Override
  public void onGroupCallJoinStateChanged (int groupCallId, int state, boolean micMuted) {
    if (groupCall != null && (groupCallId == this.groupCallId || state == GroupCallManager.STATE_NONE)) {
      // On (re-)joining this call, rebuild the roster from scratch — syncVideoTiles
      // cleared it on the previous leave, so request a fresh participant load.
      if (groupCallId == this.groupCallId && state != GroupCallManager.STATE_NONE && participants.isEmpty()) {
        loadParticipants();
      }
      buildCells();
      syncVideoTiles();
    }
  }

  @Override
  public void onGroupCallVideoStateChanged (int groupCallId, boolean videoEnabled) {
    if (groupCallId == this.groupCallId) {
      buildCells();
      syncVideoTiles();
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_groupCallJoinLeave) {
      if (tdlib.groupCalls().getState(groupCallId) == GroupCallManager.STATE_NONE) {
        tdlib.ui().joinVideoChat(this, groupCallId);
      } else {
        tdlib.groupCalls().leave();
      }
    } else if (id == R.id.btn_groupCallMute) {
      tdlib.groupCalls().setMicMuted(!tdlib.groupCalls().isMicMuted());
    } else if (id == R.id.btn_groupCallVideo) {
      toggleOutgoingVideo();
    } else if (id == R.id.btn_groupCallScreenShare) {
      toggleScreenShare();
    } else if (id == R.id.btn_camera_switch) {
      final GroupCallManager calls = tdlib.groupCalls();
      if (calls.isVideoEnabled()) {
        calls.switchCamera(!calls.isFrontCamera());
      }
    } else if (id == R.id.btn_groupCallParticipant) {
      Object data = v.getTag() instanceof ListItem ? ((ListItem) v.getTag()).getData() : null;
      if (data instanceof TdApi.MessageSender) {
        tdlib.ui().openSenderProfile(this, (TdApi.MessageSender) data, null);
      }
    } else {
      handleAdminClick(id);
    }
  }

  @Override
  public void onGroupCallUpdated (TdApi.GroupCall updatedCall) {
    if (updatedCall != null && updatedCall.id == groupCallId) {
      runOnUiThreadOptional(() -> {
        groupCall = updatedCall;
        setName(getName());
        buildCells();
      });
    }
  }

  @Override
  public void onGroupCallParticipantUpdated (int callId, TdApi.GroupCallParticipant participant) {
    if (callId == groupCallId && participant != null) {
      runOnUiThreadOptional(() -> {
        String key = senderKey(participant.participantId);
        // An empty order means the participant left — drop them.
        if (StringUtils.isEmpty(participant.order)) {
          participants.remove(key);
        } else {
          participants.put(key, participant);
        }
        buildCells();
        syncVideoTiles();
      });
    }
  }

  private static String senderKey (TdApi.MessageSender sender) {
    if (sender instanceof TdApi.MessageSenderUser) {
      return "u" + ((TdApi.MessageSenderUser) sender).userId;
    } else if (sender instanceof TdApi.MessageSenderChat) {
      return "c" + ((TdApi.MessageSenderChat) sender).chatId;
    }
    return "?";
  }

  /**
   * Reconciles the live video tiles with the current roster + self camera state:
   * creates a tile + attaches an incoming sink for each participant with active
   * video, removes tiles for participants who stopped, manages the self preview, and
   * updates the requested-channel set. Sinks are always detached before a renderer
   * is released.
   */
  private void syncVideoTiles () {
    if (isDestroyed()) {
      return;
    }
    final GroupCallManager calls = tdlib.groupCalls();
    final boolean connected = calls.getState(groupCallId) != GroupCallManager.STATE_NONE;
    if (!connected) {
      // Not in the call: tear down any tiles + sinks.
      if (videoView != null) {
        for (String endpointId : new ArrayList<>(attachedEndpoints)) {
          videoView.removeTile(endpointId);
        }
        videoView.removeTile(GroupCallVideoView.SELF_ENDPOINT);
      }
      attachedEndpoints.clear();
      // Drop the roster so a re-join doesn't replay stale tiles / requested
      // channels; it is rebuilt from a fresh LoadGroupCallParticipants on re-join.
      participants.clear();
      // Restore the recycler inset (the early return below would otherwise leave the 184dp video
      // gap reserved after leaving the call).
      updateVideoInset();
      return;
    }

    // Collect endpoints that currently have active (non-self) video.
    List<String> liveEndpoints = new ArrayList<>();
    List<int[]> liveSsrcGroups = new ArrayList<>();
    for (TdApi.GroupCallParticipant participant : participants.values()) {
      if (participant.isCurrentUser) {
        continue;
      }
      TdApi.GroupCallParticipantVideoInfo info = participant.videoInfo;
      if (info != null && !StringUtils.isEmpty(info.endpointId)) {
        liveEndpoints.add(info.endpointId);
      }
    }

    GroupCallVideoView view = liveEndpoints.isEmpty() && !calls.isVideoEnabled() ? videoView : ensureVideoView();

    // Attach new endpoints.
    for (TdApi.GroupCallParticipant participant : participants.values()) {
      if (participant.isCurrentUser) {
        continue;
      }
      TdApi.GroupCallParticipantVideoInfo info = participant.videoInfo;
      if (info == null || StringUtils.isEmpty(info.endpointId) || view == null) {
        continue;
      }
      if (!attachedEndpoints.contains(info.endpointId)) {
        VideoSink sink = view.obtainTile(info.endpointId, false);
        if (sink != null) {
          calls.addIncomingVideoOutput(info.endpointId, sink);
          attachedEndpoints.add(info.endpointId);
        }
      }
    }

    // Detach endpoints that are gone.
    for (String endpointId : new ArrayList<>(attachedEndpoints)) {
      if (!liveEndpoints.contains(endpointId)) {
        calls.removeIncomingVideoOutput(endpointId);
        if (view != null) {
          view.removeTile(endpointId);
        }
        attachedEndpoints.remove(endpointId);
      }
    }

    // Self preview — camera and screen presentation are independent sources, each with its
    // own self tile, and can be shown simultaneously.
    if (view != null) {
      if (calls.isVideoEnabled()) {
        if (!view.hasTile(GroupCallVideoView.SELF_ENDPOINT)) {
          // Mirror only for the front camera (matches a natural selfie view).
          boolean mirror = calls.isFrontCamera();
          VideoSink selfSink = view.obtainTile(GroupCallVideoView.SELF_ENDPOINT, mirror);
          if (selfSink != null) {
            // Video is already on (the capturer survives this controller), so only
            // re-route the preview into the freshly-created self tile — don't
            // recreate the capturer.
            calls.setLocalPreviewSink(selfSink);
          }
        }
      } else {
        view.removeTile(GroupCallVideoView.SELF_ENDPOINT);
      }
      // Only provision the self-screen tile once the screencast is actually LIVE (capturer started
      // + handshake announced) — not during the brief "starting" window — so a failed startCapture
      // never leaves an orphaned tile. isScreenSharingActive() flips on in onScreencastStarted,
      // which re-runs this sync.
      if (calls.isScreenSharingActive()) {
        if (!view.hasTile(GroupCallVideoView.SELF_SCREEN_ENDPOINT)) {
          VideoSink screenSink = view.obtainTile(GroupCallVideoView.SELF_SCREEN_ENDPOINT, false);
          if (screenSink != null) {
            calls.setScreenPreviewSink(screenSink);
          }
        }
      } else {
        view.removeTile(GroupCallVideoView.SELF_SCREEN_ENDPOINT);
      }
    }

    // Update the requested channels: ask for the visible remote endpoints at medium quality.
    if (!liveEndpoints.isEmpty()) {
      String[] endpointIds = liveEndpoints.toArray(new String[0]);
      int[] qualities = new int[endpointIds.length];
      String[] ssrcGroups = new String[endpointIds.length];
      for (int i = 0; i < endpointIds.length; i++) {
        qualities[i] = GroupCallInstance.VIDEO_QUALITY_MEDIUM;
        ssrcGroups[i] = encodeSsrcGroups(endpointIds[i]);
      }
      calls.setRequestedVideoChannels(endpointIds, qualities, ssrcGroups);
    } else {
      calls.setRequestedVideoChannels(new String[0], new int[0], new String[0]);
    }

    updateVideoInset();
  }

  /**
   * The video-tiles overlay is pinned to the top {@value} dp of the screen, floating over the
   * recycler. Without reserving space it covers the list's top items (Leave / Mute / Stop video /
   * Share screen) — so once video starts the user can't reach "Stop video". Reserve matching top
   * padding while the overlay shows content, and hide the empty overlay otherwise.
   */
  private void updateVideoInset () {
    CustomRecyclerView recyclerView = getRecyclerView();
    if (recyclerView == null) {
      return;
    }
    final GroupCallManager calls = tdlib.groupCalls();
    boolean hasVideo = videoView != null &&
      (!attachedEndpoints.isEmpty() || calls.isVideoEnabled() || calls.isScreenSharingActive());
    int topPadding = hasVideo ? Screen.dp(184f) : 0;
    if (recyclerView.getPaddingTop() != topPadding) {
      recyclerView.setClipToPadding(true);
      recyclerView.setPadding(recyclerView.getPaddingLeft(), topPadding, recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
    }
    if (videoView != null) {
      videoView.setVisibility(hasVideo ? View.VISIBLE : View.GONE);
    }
  }

  /** Encodes a participant's video source groups as "SEMANTICS:ssrc,ssrc;..." for the given endpoint. */
  private String encodeSsrcGroups (String endpointId) {
    for (TdApi.GroupCallParticipant participant : participants.values()) {
      TdApi.GroupCallParticipantVideoInfo info = participant.videoInfo;
      if (info == null || !endpointId.equals(info.endpointId) || info.sourceGroups == null) {
        continue;
      }
      StringBuilder b = new StringBuilder();
      for (TdApi.GroupCallVideoSourceGroup group : info.sourceGroups) {
        if (group == null || group.sourceIds == null || group.sourceIds.length == 0) {
          continue;
        }
        if (b.length() > 0) {
          b.append(';');
        }
        b.append(group.semantics != null ? group.semantics : "");
        b.append(':');
        for (int i = 0; i < group.sourceIds.length; i++) {
          if (i > 0) {
            b.append(',');
          }
          b.append(group.sourceIds[i] & 0xFFFFFFFFL);
        }
      }
      return b.toString();
    }
    return "";
  }

  private void buildLoadingCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void showError (String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private int typeRes () {
    if (groupCall == null) {
      return R.string.VideoChat;
    }
    if (groupCall.isRtmpStream) {
      return R.string.GroupCallRtmp;
    }
    if (groupCall.isLiveStory) {
      return R.string.GroupCallLiveStream;
    }
    return groupCall.isVideoChat ? R.string.VideoChat : R.string.VoiceChat;
  }

  private String statusValue () {
    if (groupCall == null) {
      return "";
    }
    if (groupCall.scheduledStartDate != 0) {
      return Lang.getString(R.string.GroupCallScheduledFor,
        Lang.getMessageTimestamp(groupCall.scheduledStartDate, TimeUnit.SECONDS));
    }
    return Lang.getString(groupCall.isActive ? R.string.GroupCallActive : R.string.GroupCallEnded);
  }

  private static void addRow (List<ListItem> items, CharSequence title, CharSequence value, boolean first) {
    if (!first) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, 0, title, false);
    item.setStringValue(value);
    items.add(item);
  }

  private void buildCells () {
    if (groupCall == null) {
      return;
    }
    List<ListItem> items = new ArrayList<>();

    // Join / Leave / Mute — only for live (non-scheduled) calls.
    if (groupCall.scheduledStartDate == 0) {
      final int callState = tdlib.groupCalls().getState(groupCallId);
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (callState == GroupCallManager.STATE_NONE) {
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallJoinLeave, R.drawable.baseline_call_24, R.string.GroupCallJoin)
          .setTextColorId(ColorId.textNeutral));
      } else {
        int leaveLabel = callState == GroupCallManager.STATE_CONNECTED ? R.string.GroupCallLeave : R.string.GroupCallConnecting;
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallJoinLeave, R.drawable.baseline_call_end_24, leaveLabel)
          .setTextColorId(ColorId.textNegative));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        boolean muted = tdlib.groupCalls().isMicMuted();
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallMute, R.drawable.baseline_mic_24,
          muted ? R.string.GroupCallUnmute : R.string.GroupCallMute));
        if (groupCall.isVideoChat) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          // Camera and screen presentation are independent sources (separate group connections)
          // and can be on at the same time, so the camera label tracks only the camera state.
          boolean videoOn = tdlib.groupCalls().isVideoEnabled();
          boolean screenOn = tdlib.groupCalls().isScreenSharing();
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallVideo, R.drawable.baseline_videocam_24,
            videoOn ? R.string.GroupCallStopVideo : R.string.GroupCallStartVideo));
          if (videoOn) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_camera_switch, R.drawable.baseline_camera_front_24, R.string.GroupCallSwitchCamera));
          }
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallScreenShare, R.drawable.baseline_devices_other_24,
            screenOn ? R.string.GroupCallStopScreen : R.string.GroupCallStartScreen));
        }
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    addRow(items, Lang.getString(R.string.GroupCallType), Lang.getString(typeRes()), true);
    addRow(items, Lang.getString(R.string.GroupCallStatus), statusValue(), false);
    if (groupCall.scheduledStartDate == 0) {
      addRow(items, Lang.getString(R.string.GroupCallParticipants),
        Lang.plural(R.string.xParticipants, groupCall.participantCount), false);
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (groupCall.recentSpeakers != null && groupCall.recentSpeakers.length > 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.GroupCallSpeaking));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (TdApi.GroupCallRecentSpeaker speaker : groupCall.recentSpeakers) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        String name = tdlib.senderName(speaker.participantId);
        // Tappable → opens the participant's profile. The sender is carried in data; the value text
        // (e.g. "speaking now") lives in stringValue so setValuedSetting can render it.
        ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_groupCallParticipant, 0, name, false);
        item.setData(speaker.participantId);
        if (speaker.isSpeaking) {
          item.setStringValue(Lang.getString(R.string.GroupCallSpeakingNow));
        }
        items.add(item);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    // Admin actions.
    if (groupCall.canBeManaged) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.GroupCallManage));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      if (groupCall.scheduledStartDate != 0) {
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallStart, R.drawable.baseline_call_24, R.string.GroupCallStartNow)
          .setTextColorId(ColorId.textNeutral));
        first = false;
      }
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallInvite, R.drawable.baseline_link_24, R.string.GroupCallCopyInvite));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallRename, R.drawable.baseline_edit_24, R.string.GroupCallRename));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_groupCallEnd, R.drawable.baseline_call_end_24, R.string.GroupCallEnd)
        .setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, false);
  }

  private void toggleOutgoingVideo () {
    final GroupCallManager calls = tdlib.groupCalls();
    if (calls.getState(groupCallId) == GroupCallManager.STATE_NONE) {
      return;
    }
    // Camera and screen presentation are independent (separate connections); the camera toggle
    // only affects the camera self tile and never touches the screen presentation.
    if (calls.isVideoEnabled()) {
      calls.disableOutgoingVideo();
      if (videoView != null) {
        videoView.removeTile(GroupCallVideoView.SELF_ENDPOINT);
      }
      buildCells();
      return;
    }
    requestCameraPermissionThen(() -> {
      if (isDestroyed() || calls.getState(groupCallId) == GroupCallManager.STATE_NONE) {
        return;
      }
      GroupCallVideoView view = ensureVideoView();
      if (view != null) {
        view.removeTile(GroupCallVideoView.SELF_ENDPOINT);
      }
      VideoSink selfSink = view != null ? view.obtainTile(GroupCallVideoView.SELF_ENDPOINT, true) : null;
      calls.enableOutgoingVideo(true, selfSink);
      buildCells();
    });
  }

  private void toggleScreenShare () {
    final GroupCallManager calls = tdlib.groupCalls();
    if (calls.getState(groupCallId) == GroupCallManager.STATE_NONE) {
      return;
    }
    if (calls.isScreenSharing()) {
      calls.stopScreenSharing();
      if (videoView != null) {
        videoView.removeTile(GroupCallVideoView.SELF_SCREEN_ENDPOINT);
      }
      buildCells();
      return;
    }
    // Request MediaProjection permission; on grant the result is stored in VoIPScreenCapture
    // and we start the SECOND (presentation) connection that streams the screen as a distinct
    // server-side source (StartGroupCallScreenSharing). Camera, if on, keeps running.
    context().requestScreenCapturePermission(() -> {
      if (isDestroyed() || calls.getState(groupCallId) == GroupCallManager.STATE_NONE) {
        return;
      }
      GroupCallVideoView view = ensureVideoView();
      // Don't provision the self-screen tile up front: the capturer may fail to start on the media
      // thread, which would orphan it. The tile is created in syncVideoTiles once the screencast is
      // actually live (isScreenSharingActive, driven by onScreencastStarted → notifyVideoListeners),
      // and the preview is routed there via setScreenPreviewSink. Clear any stale tile from a prior
      // attempt first.
      if (view != null) {
        view.removeTile(GroupCallVideoView.SELF_SCREEN_ENDPOINT);
      }
      boolean started = calls.startScreenSharing(null);
      if (!started) {
        // FGS re-assert / presentation create failed synchronously — surface an error so the user
        // can retry. (No tile was provisioned, so there's nothing to clean up.)
        UI.showToast(R.string.VoipScreenShareFailed, android.widget.Toast.LENGTH_SHORT);
      }
      buildCells();
    }, null);
  }

  private void requestCameraPermissionThen (Runnable onGranted) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
        context().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      context().requestCustomPermissions(new String[] {Manifest.permission.CAMERA}, (code, permissions, grantResults, grantCount) -> {
        if (grantCount == permissions.length && !isDestroyed()) {
          onGranted.run();
        }
      });
    } else {
      onGranted.run();
    }
  }

  private void handleAdminClick (int id) {
    if (id == R.id.btn_groupCallStart) {
      tdlib.send(new TdApi.StartScheduledVideoChat(groupCallId), tdlib.typedOkHandler());
    } else if (id == R.id.btn_groupCallInvite) {
      tdlib.send(new TdApi.GetVideoChatInviteLink(groupCallId, false), (result, error) -> runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showToast(TD.toErrorString(error), android.widget.Toast.LENGTH_SHORT);
        } else if (result != null) {
          UI.copyText(result.url, R.string.CopiedLink);
        }
      }));
    } else if (id == R.id.btn_groupCallRename) {
      openInputAlert(Lang.getString(R.string.GroupCallRename), Lang.getString(R.string.GroupCallType),
        R.string.Save, R.string.Cancel, groupCall != null ? groupCall.title : "", (inputView, title) -> {
          tdlib.send(new TdApi.SetVideoChatTitle(groupCallId, title != null ? title.trim() : ""), tdlib.typedOkHandler());
          return true;
        }, true);
    } else if (id == R.id.btn_groupCallEnd) {
      showConfirm(Lang.getString(R.string.GroupCallEndConfirm), Lang.getString(R.string.GroupCallEnd), () ->
        tdlib.send(new TdApi.EndGroupCall(groupCallId), tdlib.typedOkHandler()));
    }
  }
}
