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

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.GroupCallListener;
import org.thunderdog.challegram.telegram.GroupCallManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    adapter = new SettingsAdapter(this);
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
    }));
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromGroupCallUpdates(groupCallId, this);
    tdlib.groupCalls().removeListener(this);
  }

  @Override
  public void onGroupCallJoinStateChanged (int groupCallId, int state, boolean micMuted) {
    if (groupCall != null && (groupCallId == this.groupCallId || state == GroupCallManager.STATE_NONE)) {
      buildCells();
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
    if (callId == groupCallId) {
      runOnUiThreadOptional(this::buildCells);
    }
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
    item.setData(value);
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
        ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, 0, name, false);
        if (speaker.isSpeaking) {
          item.setData(Lang.getString(R.string.GroupCallSpeakingNow));
        }
        items.add(item);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, false);
  }
}
