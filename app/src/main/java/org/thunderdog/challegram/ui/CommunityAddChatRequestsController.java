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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

import me.vkryl.core.StringUtils;

/**
 * Pending requests to add chats to a community, for administrators who can edit its chat list.
 *
 * FORK-TDLIB: getCommunityAddChatRequests / processCommunityAddChatRequest(s) come from
 * tdlib/source/patches/td-communities.patch; switch to the official methods once TDLib exposes them.
 */
public class CommunityAddChatRequestsController extends RecyclerViewController<CommunityController.Args> implements View.OnClickListener {
  private static final int PAGE_SIZE = 100;

  private SettingsAdapter adapter;
  private final ArrayList<TdApi.CommunityAddChatRequest> requests = new ArrayList<>();
  private boolean isLoading;

  public CommunityAddChatRequestsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long communityId () {
    return getArgumentsStrict().communityId;
  }

  @Override
  public int getId () {
    return R.id.controller_communityAddChatRequests;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.CommunityAddChatRequests);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    recyclerView.setAdapter(adapter);
    reload();
  }

  private void reload () {
    requests.clear();
    isLoading = true;
    buildCells();
    loadPage("");
  }

  private void loadPage (String offset) {
    tdlib.send(new TdApi.GetCommunityAddChatRequests(communityId(), offset, PAGE_SIZE), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        isLoading = false;
        buildCells();
        return;
      }
      for (TdApi.CommunityAddChatRequest request : result.requests) {
        requests.add(request);
      }
      if (!StringUtils.isEmpty(result.nextOffset) && result.requests.length > 0) {
        loadPage(result.nextOffset);
      } else {
        isLoading = false;
        buildCells();
      }
    }));
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (isLoading) {
      items.add(new ListItem(ListItem.TYPE_PROGRESS));
    } else if (requests.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityRequestsEmpty));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_communityApproveAll, R.drawable.baseline_check_24, R.string.CommunityRequestsApproveAll));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_communityDeclineAll, R.drawable.baseline_cancel_24, R.string.CommunityRequestsDeclineAll).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (TdApi.CommunityAddChatRequest request : requests) {
        TdApi.Chat chat = tdlib.chat(request.chatId);
        if (chat == null) {
          continue;
        }
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, chat, true, false);
        wrapper.setAdminSignVisible(false, false);
        String requestedBy = Lang.getString(R.string.CommunityRequestBy, tdlib.cache().userDisplayName(request.requesterUserId, false, false));
        wrapper.setForcedSubtitle(request.isHidden ? Lang.getString(R.string.CommunityRequestHidden, requestedBy) : requestedBy);
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_communityRequest).setData(wrapper).setLongId(request.chatId));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    adapter.setItems(items, false);
  }

  private void onProcessed (boolean all, long chatId) {
    if (all) {
      requests.clear();
    } else {
      for (int i = requests.size() - 1; i >= 0; i--) {
        if (requests.get(i).chatId == chatId) {
          requests.remove(i);
        }
      }
    }
    buildCells();
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_communityApproveAll || viewId == R.id.btn_communityDeclineAll) {
      final boolean approve = viewId == R.id.btn_communityApproveAll;
      tdlib.send(new TdApi.ProcessCommunityAddChatRequests(communityId(), approve), (ok, error) -> runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showError(error);
        } else {
          onProcessed(true, 0);
        }
      }));
    } else if (viewId == R.id.btn_communityRequest) {
      ListItem item = (ListItem) v.getTag();
      if (item == null) {
        return;
      }
      final long chatId = item.getLongId();
      showOptions(tdlib.chatTitle(chatId),
        new int[] {R.id.btn_communityRequestApprove, R.id.btn_communityRequestDecline},
        new String[] {Lang.getString(R.string.CommunityRequestApprove), Lang.getString(R.string.CommunityRequestDecline)},
        new int[] {OptionColor.NORMAL, OptionColor.RED},
        new int[] {R.drawable.baseline_check_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          if (id == R.id.btn_communityRequestApprove || id == R.id.btn_communityRequestDecline) {
            boolean approve = id == R.id.btn_communityRequestApprove;
            tdlib.send(new TdApi.ProcessCommunityAddChatRequest(communityId(), chatId, approve), (ok, error) -> runOnUiThreadOptional(() -> {
              if (error != null) {
                UI.showError(error);
              } else {
                onProcessed(false, chatId);
              }
            }));
          }
          return true;
        });
    }
  }
}
