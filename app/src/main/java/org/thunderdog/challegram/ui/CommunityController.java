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
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

import me.vkryl.core.StringUtils;

/**
 * Shows a Telegram community: its name and photo, the current user's role, the chats that
 * belong to it and, for administrators, a few counters. Data arrives through updateCommunity /
 * updateCommunityFullInfo (cached by TdlibCache); getCommunityFullInfo results are fed into the same cache.
 *
 * FORK-TDLIB: chat list editing (setCommunityChatIsHidden, removeCommunityChat) and the add-chat
 * requests screen use functions from tdlib/source/patches/td-communities.patch; switch to the
 * official methods once TDLib exposes them.
 */
public class CommunityController extends RecyclerViewController<CommunityController.Args> implements View.OnClickListener, View.OnLongClickListener, TdlibCache.CommunityDataChangeListener {
  public static class Args {
    public final long communityId;

    public Args (long communityId) {
      this.communityId = communityId;
    }
  }

  private SettingsAdapter adapter;

  public CommunityController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long communityId () {
    return getArgumentsStrict().communityId;
  }

  @Override
  public int getId () {
    return R.id.controller_community;
  }

  @Override
  public CharSequence getName () {
    TdApi.Community community = tdlib.cache().community(communityId());
    return community != null && !StringUtils.isEmpty(community.name) ? community.name : Lang.getString(R.string.Community);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        TdApi.CommunityFullInfo fullInfo = tdlib.cache().communityFull(communityId());
        final int itemId = item.getId();
        // Administrators and banned users can't be listed (no server call yet): show them as plain counters.
        view.setClickable(itemId == R.id.btn_communityAddRequests);
        if (fullInfo == null) {
          view.setData(null);
        } else if (itemId == R.id.btn_communityAdmins) {
          view.setData(Integer.toString(fullInfo.administratorCount));
        } else if (itemId == R.id.btn_communityBanned) {
          view.setData(Integer.toString(fullInfo.bannedCount));
        } else if (itemId == R.id.btn_communityAddRequests) {
          view.setData(Integer.toString(fullInfo.addChatRequestCount));
        }
      }
    };
    buildCells();
    recyclerView.setAdapter(adapter);

    tdlib.cache().subscribeToCommunityUpdates(communityId(), this);
    TdApi.Community community = tdlib.cache().community(communityId());
    if (community == null || community.haveAccess) {
      tdlib.send(new TdApi.GetCommunityFullInfo(communityId()), (fullInfo, error) -> {
        if (error != null) {
          UI.showError(error);
        } else {
          tdlib.cache().onCommunityFullInfoLoaded(communityId(), fullInfo);
        }
      });
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().unsubscribeFromCommunityUpdates(communityId(), this);
  }

  // Updates arrive on the TDLib thread

  @Override
  public void onCommunityUpdated (TdApi.Community community) {
    runOnUiThreadOptional(() -> {
      if (headerView != null && isFocused()) {
        headerView.setTitle(this);
      }
      buildCells();
    });
  }

  @Override
  public void onCommunityFullUpdated (long id, TdApi.CommunityFullInfo community) {
    runOnUiThreadOptional(this::buildCells);
  }

  // Cells

  private static boolean isAdministrator (@Nullable TdApi.Community community) {
    if (community == null || community.status == null) {
      return false;
    }
    switch (community.status.getConstructor()) {
      case TdApi.CommunityMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.CommunityMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.CommunityMemberStatusAdministrator) community.status).rights.canManageCommunity;
    }
    return false;
  }

  private static boolean canChangeInfo (@Nullable TdApi.Community community) {
    if (community == null || community.status == null || !community.haveAccess) {
      return false;
    }
    switch (community.status.getConstructor()) {
      case TdApi.CommunityMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.CommunityMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.CommunityMemberStatusAdministrator) community.status).rights.canChangeInfo;
    }
    return false;
  }

  static boolean canEditChatList (@Nullable TdApi.Community community) {
    if (community == null || community.status == null || !community.haveAccess) {
      return false;
    }
    switch (community.status.getConstructor()) {
      case TdApi.CommunityMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.CommunityMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.CommunityMemberStatusAdministrator) community.status).rights.canEditChatList;
    }
    return false;
  }

  /** Members may add their own chats; without the chat-list right the server turns it into a request. */
  private static boolean canAddChats (@Nullable TdApi.Community community) {
    if (community == null || community.status == null || !community.haveAccess) {
      return false;
    }
    switch (community.status.getConstructor()) {
      case TdApi.CommunityMemberStatusCreator.CONSTRUCTOR:
      case TdApi.CommunityMemberStatusAdministrator.CONSTRUCTOR:
      case TdApi.CommunityMemberStatusMember.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  private static int roleRes (@Nullable TdApi.Community community) {
    if (community == null || community.status == null) {
      return 0;
    }
    switch (community.status.getConstructor()) {
      case TdApi.CommunityMemberStatusCreator.CONSTRUCTOR:
        return R.string.CommunityRoleCreator;
      case TdApi.CommunityMemberStatusAdministrator.CONSTRUCTOR:
        return R.string.CommunityRoleAdmin;
      case TdApi.CommunityMemberStatusMember.CONSTRUCTOR:
        return R.string.CommunityRoleMember;
      case TdApi.CommunityMemberStatusLeft.CONSTRUCTOR:
        return R.string.CommunityRoleLeft;
      case TdApi.CommunityMemberStatusBanned.CONSTRUCTOR:
        return R.string.CommunityRoleBanned;
    }
    return 0;
  }

  static DoubleTextWrapper newCommunityWrapper (Tdlib tdlib, long communityId, @Nullable TdApi.Community community) {
    String name = community != null && !StringUtils.isEmpty(community.name) ? community.name : Lang.getString(R.string.Community);
    int roleRes = roleRes(community);
    TdlibAccentColor accentColor = tdlib.accentColor(TdlibAccentColor.defaultAccentColorIdForUserId(communityId));
    DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, name, roleRes != 0 ? Lang.getString(roleRes) : null, new AvatarPlaceholder.Metadata(accentColor, TD.getLetters(name)));
    if (community != null) {
      wrapper.setPhoto(community.photo);
    }
    return wrapper;
  }

  private void buildCells () {
    final TdApi.Community community = tdlib.cache().community(communityId());
    final TdApi.CommunityFullInfo fullInfo = community != null && community.haveAccess ? tdlib.cache().communityFull(communityId()) : null;

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_community).setData(newCommunityWrapper(tdlib, communityId(), community)));
    if (canChangeInfo(community)) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_communityRename, R.drawable.baseline_edit_24, R.string.CommunityRename));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (community != null && !community.haveAccess) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityInaccessible));
      adapter.setItems(items, false);
      return;
    }

    if (fullInfo != null && isAdministrator(community)) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_communityAdmins, R.drawable.baseline_stars_24, R.string.CommunityAdministrators));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_communityBanned, R.drawable.baseline_block_24, R.string.CommunityBanned));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_communityAddRequests, R.drawable.baseline_group_add_24, R.string.CommunityAddChatRequests));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityAdminInfo));
    }

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.CommunityChats));
    if (fullInfo == null) {
      items.add(new ListItem(ListItem.TYPE_PROGRESS));
    } else {
      ArrayList<ListItem> chatItems = new ArrayList<>();
      for (TdApi.CommunityChat communityChat : fullInfo.chats) {
        TdApi.Chat chat = tdlib.chat(communityChat.chatId);
        if (chat == null) {
          // Rebuild once TDLib knows the chat; community chats may not be in the local cache yet.
          tdlib.chat(communityChat.chatId, loadedChat -> runOnUiThreadOptional(this::buildCells));
          continue;
        }
        DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, chat, true, false);
        wrapper.setAdminSignVisible(false, false);
        if (communityChat.isHidden) {
          wrapper.setForcedSubtitle(Lang.getString(R.string.CommunityChatHidden));
        } else if (!communityChat.canViewHistory) {
          wrapper.setForcedSubtitle(Lang.getString(R.string.CommunityChatNoHistory));
        }
        if (!chatItems.isEmpty()) {
          chatItems.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        chatItems.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_communityChat).setData(wrapper).setLongId(chat.id));
      }
      final boolean canAddChats = canAddChats(community);
      if (chatItems.isEmpty() && !canAddChats) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityNoChats));
      } else {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        if (canAddChats) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_communityAddChat, R.drawable.baseline_group_add_24, R.string.CommunityAddChat).setTextColorId(ColorId.textNeutral));
          if (!chatItems.isEmpty()) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
        }
        items.addAll(chatItems);
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        if (chatItems.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityNoChats));
        }
        if (canEditChatList(community)) {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityChatsHint));
        }
      }
    }
    adapter.setItems(items, false);
  }

  // Actions

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_communityChat) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getLongId() != 0) {
        tdlib.ui().openChat(this, item.getLongId(), null);
      }
    } else if (viewId == R.id.btn_communityRename) {
      renameCommunity();
    } else if (viewId == R.id.btn_communityAddChat) {
      CommunityAddChatController c = new CommunityAddChatController(context, tdlib);
      c.setArguments(new CommunityController.Args(communityId()));
      navigateTo(c);
    } else if (viewId == R.id.btn_communityAddRequests) {
      if (canEditChatList(tdlib.cache().community(communityId()))) {
        CommunityAddChatRequestsController c = new CommunityAddChatRequestsController(context, tdlib);
        c.setArguments(new CommunityController.Args(communityId()));
        navigateTo(c);
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() != R.id.btn_communityChat) {
      return false;
    }
    ListItem item = (ListItem) v.getTag();
    TdApi.Community community = tdlib.cache().community(communityId());
    TdApi.CommunityFullInfo fullInfo = tdlib.cache().communityFull(communityId());
    if (item == null || fullInfo == null || !canEditChatList(community)) {
      return false;
    }
    final long chatId = item.getLongId();
    TdApi.CommunityChat communityChat = null;
    for (TdApi.CommunityChat chat : fullInfo.chats) {
      if (chat.chatId == chatId) {
        communityChat = chat;
        break;
      }
    }
    if (communityChat == null) {
      return false;
    }
    final boolean isHidden = communityChat.isHidden;
    showOptions(tdlib.chatTitle(chatId),
      new int[] {isHidden ? R.id.btn_communityChatShow : R.id.btn_communityChatHide, R.id.btn_communityChatRemove},
      new String[] {Lang.getString(isHidden ? R.string.CommunityChatShow : R.string.CommunityChatHide), Lang.getString(R.string.CommunityChatRemove)},
      new int[] {OptionColor.NORMAL, OptionColor.RED},
      new int[] {isHidden ? R.drawable.baseline_visibility_24 : R.drawable.baseline_eye_off_24, R.drawable.baseline_delete_24},
      (itemView, id) -> {
        if (id == R.id.btn_communityChatShow || id == R.id.btn_communityChatHide) {
          tdlib.send(new TdApi.SetCommunityChatIsHidden(communityId(), chatId, id == R.id.btn_communityChatHide), (ok, error) -> {
            if (error != null) {
              UI.showError(error);
            }
          });
        } else if (id == R.id.btn_communityChatRemove) {
          showOptions(Lang.getStringBold(R.string.CommunityChatRemoveConfirm, tdlib.chatTitle(chatId)),
            new int[] {R.id.btn_communityChatRemove, R.id.btn_cancel},
            new String[] {Lang.getString(R.string.CommunityChatRemove), Lang.getString(R.string.Cancel)},
            new int[] {OptionColor.RED, OptionColor.NORMAL},
            new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
            (confirmView, confirmId) -> {
              if (confirmId == R.id.btn_communityChatRemove) {
                tdlib.send(new TdApi.RemoveCommunityChat(communityId(), chatId), (ok, error) -> {
                  if (error != null) {
                    UI.showError(error);
                  }
                });
              }
              return true;
            });
        }
        return true;
      });
    return true;
  }

  private void renameCommunity () {
    TdApi.Community community = tdlib.cache().community(communityId());
    if (!canChangeInfo(community)) {
      return;
    }
    openInputAlert(Lang.getString(R.string.CommunityRename), Lang.getString(R.string.CommunityName), R.string.Save, R.string.Cancel, community.name, (inputView, result) -> {
      String name = result.trim();
      if (StringUtils.isEmpty(name)) {
        return false;
      }
      if (!name.equals(community.name)) {
        tdlib.send(new TdApi.SetCommunityName(communityId(), name), (ok, error) -> {
          if (error != null) {
            UI.showError(error);
          }
        });
      }
      return true;
    }, true);
  }
}
