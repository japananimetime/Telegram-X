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
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import tgx.td.ChatId;

/**
 * Picks a chat to add to a community: supergroups and channels created by the current user and
 * bots owned by them, which don't belong to any community yet.
 *
 * FORK-TDLIB: addCommunityChat comes from tdlib/source/patches/td-communities.patch; switch to the
 * official method once TDLib exposes it.
 */
public class CommunityAddChatController extends RecyclerViewController<CommunityController.Args> implements View.OnClickListener {
  private static final int CHAT_LIMIT = 1000;

  private SettingsAdapter adapter;
  private final ArrayList<Long> candidateChatIds = new ArrayList<>();
  private boolean isLoading = true;

  public CommunityAddChatController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long communityId () {
    return getArgumentsStrict().communityId;
  }

  @Override
  public int getId () {
    return R.id.controller_communityAddChat;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.CommunityAddChat);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    buildCells();
    recyclerView.setAdapter(adapter);
    collectCandidates();
  }

  // Candidates

  private void collectCandidates () {
    final Set<Long> supergroupChatIds = new LinkedHashSet<>();
    final Set<Long> botUserIds = new LinkedHashSet<>();
    tdlib.send(new TdApi.GetChats(new TdApi.ChatListMain(), CHAT_LIMIT), (mainChats, mainError) -> {
      addOwnedSupergroups(mainChats, supergroupChatIds);
      tdlib.send(new TdApi.GetChats(new TdApi.ChatListArchive(), CHAT_LIMIT), (archivedChats, archiveError) -> {
        addOwnedSupergroups(archivedChats, supergroupChatIds);
        tdlib.send(new TdApi.GetCreatedPublicChats(new TdApi.PublicChatTypeHasUsername()), (publicChats, publicError) -> {
          addOwnedSupergroups(publicChats, supergroupChatIds);
          tdlib.send(new TdApi.GetOwnedBots(), (bots, botsError) -> {
            if (bots != null) {
              for (long userId : bots.userIds) {
                botUserIds.add(userId);
              }
            }
            filterByCommunity(supergroupChatIds, botUserIds);
          });
        });
      });
    });
  }

  private void addOwnedSupergroups (TdApi.Chats chats, Set<Long> out) {
    if (chats == null) {
      return;
    }
    for (long chatId : chats.chatIds) {
      if (!ChatId.isSupergroup(chatId)) {
        continue;
      }
      TdApi.Supergroup supergroup = tdlib.cache().supergroup(ChatId.toSupergroupId(chatId));
      if (supergroup != null && TD.isCreator(supergroup.status)) {
        out.add(chatId);
      }
    }
  }

  /** Keeps only chats and bots that aren't in a community; full info tells which community a chat is in. */
  private void filterByCommunity (Set<Long> supergroupChatIds, Set<Long> botUserIds) {
    final int total = supergroupChatIds.size() + botUserIds.size();
    final ArrayList<Long> result = new ArrayList<>();
    if (total == 0) {
      runOnUiThreadOptional(() -> setCandidates(result));
      return;
    }
    final Object lock = new Object();
    final AtomicInteger remaining = new AtomicInteger(total);
    final Runnable onDone = () -> {
      if (remaining.decrementAndGet() == 0) {
        runOnUiThreadOptional(() -> setCandidates(result));
      }
    };
    for (long chatId : supergroupChatIds) {
      tdlib.send(new TdApi.GetSupergroupFullInfo(ChatId.toSupergroupId(chatId)), (fullInfo, error) -> {
        if (fullInfo != null && fullInfo.communityId == 0) {
          synchronized (lock) {
            result.add(chatId);
          }
        }
        onDone.run();
      });
    }
    for (long userId : botUserIds) {
      tdlib.send(new TdApi.GetUserFullInfo(userId), (fullInfo, error) -> {
        if (fullInfo != null && fullInfo.communityId == 0) {
          synchronized (lock) {
            result.add(ChatId.fromUserId(userId));
          }
        }
        onDone.run();
      });
    }
  }

  private void setCandidates (ArrayList<Long> chatIds) {
    candidateChatIds.clear();
    candidateChatIds.addAll(chatIds);
    isLoading = false;
    buildCells();
  }

  // Cells

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (isLoading) {
      items.add(new ListItem(ListItem.TYPE_PROGRESS));
    } else if (candidateChatIds.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityAddChatEmpty));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (long chatId : candidateChatIds) {
        DoubleTextWrapper wrapper;
        if (ChatId.isUserChat(chatId)) {
          wrapper = new DoubleTextWrapper(tdlib, ChatId.toUserId(chatId), true);
        } else {
          TdApi.Chat chat = tdlib.chat(chatId);
          if (chat == null) {
            continue;
          }
          wrapper = new DoubleTextWrapper(tdlib, chat, true, false);
          wrapper.setAdminSignVisible(false, false);
        }
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_communityCandidate).setData(wrapper).setLongId(chatId));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityAddChatHint));
    }
    adapter.setItems(items, false);
  }

  // Actions

  @Override
  public void onClick (View v) {
    if (v.getId() != R.id.btn_communityCandidate) {
      return;
    }
    ListItem item = (ListItem) v.getTag();
    if (item == null || item.getLongId() == 0) {
      return;
    }
    final long chatId = item.getLongId();
    showOptions(Lang.getString(R.string.CommunityCreateVisibility),
      new int[] {R.id.btn_communityVisibleAll, R.id.btn_communityVisibleAdmins},
      new String[] {Lang.getString(R.string.CommunityCreateVisible), Lang.getString(R.string.CommunityCreateHidden)},
      null,
      new int[] {R.drawable.baseline_group_24, R.drawable.baseline_stars_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_communityVisibleAll || optionId == R.id.btn_communityVisibleAdmins) {
          addChat(chatId, optionId == R.id.btn_communityVisibleAdmins);
        }
        return true;
      });
  }

  private void addChat (long chatId, boolean isHidden) {
    if (ChatId.isUserChat(chatId)) {
      // TDLib must know the bot's private chat before it can be referenced by chat identifier.
      tdlib.send(new TdApi.CreatePrivateChat(ChatId.toUserId(chatId), true), (chat, error) -> {
        if (error != null) {
          UI.showError(error);
        } else {
          sendAddChat(chat.id, isHidden);
        }
      });
    } else {
      sendAddChat(chatId, isHidden);
    }
  }

  private void sendAddChat (long chatId, boolean isHidden) {
    tdlib.send(new TdApi.AddCommunityChat(communityId(), chatId, isHidden), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        UI.showToast(R.string.CommunityAddToDone, Toast.LENGTH_LONG);
        navigateBack();
      }
    }));
  }
}
