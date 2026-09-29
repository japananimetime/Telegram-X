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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Shared editor for {@link TdApi.BusinessRecipients}. Lets the user pick which
 * private chats automatic messages apply to via category toggles (all existing,
 * all new, contacts, non-contacts), choose between an "include" and an "exclude"
 * interpretation, AND pick explicit per-chat identifiers to include
 * ({@link TdApi.BusinessRecipients#chatIds}) or exclude
 * ({@link TdApi.BusinessRecipients#excludedChatIds}). The explicit per-chat
 * selection reuses {@link SelectChatsController} (the same multi-select chat
 * picker that powers chat-folder include/exclude editing). The result is handed
 * back to the launching editor through {@link Delegate}; this controller does
 * not itself send any Set* request.
 */
public class BusinessRecipientsController extends EditBaseController<BusinessRecipientsController.Args> implements View.OnClickListener, SelectChatsController.Delegate {

  public interface Delegate {
    void onRecipientsChanged (@NonNull TdApi.BusinessRecipients recipients);
  }

  public static class Args {
    public final @NonNull TdApi.BusinessRecipients recipients;
    public final @NonNull Delegate delegate;
    public final boolean allowExcludedChats;

    public Args (@Nullable TdApi.BusinessRecipients recipients, @NonNull Delegate delegate) {
      this(recipients, delegate, false);
    }

    public Args (@Nullable TdApi.BusinessRecipients recipients, @NonNull Delegate delegate, boolean allowExcludedChats) {
      this.recipients = recipients != null ? recipients : emptyRecipients();
      this.delegate = delegate;
      this.allowExcludedChats = allowExcludedChats;
    }
  }

  public static TdApi.BusinessRecipients emptyRecipients () {
    return new TdApi.BusinessRecipients(new long[0], new long[0], false, false, false, false, false);
  }

  private SettingsAdapter adapter;
  private TdApi.BusinessRecipients recipients;
  private boolean allowExcludedChats;

  public BusinessRecipientsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_businessRecipients;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BusinessRecipients);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    // Defensive copy so cancelling does not mutate the caller's instance.
    TdApi.BusinessRecipients src = args.recipients;
    this.recipients = new TdApi.BusinessRecipients(
      src.chatIds != null ? src.chatIds.clone() : new long[0],
      src.excludedChatIds != null ? src.excludedChatIds.clone() : new long[0],
      src.selectExistingChats, src.selectNewChats, src.selectContacts, src.selectNonContacts, src.excludeSelected);
    this.allowExcludedChats = args.allowExcludedChats;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int id = item.getId();
        if (id == R.id.btn_folderIncludeChats) {
          view.setData(chatCountSubtitle(recipients.chatIds));
        } else if (id == R.id.btn_folderExcludeChats) {
          view.setData(chatCountSubtitle(recipients.excludedChatIds));
        }
      }
    };

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessRecipientsCategories));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_recipientsExistingChats, 0, R.string.BusinessRecipientsExistingChats, recipients.selectExistingChats));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_recipientsNewChats, 0, R.string.BusinessRecipientsNewChats, recipients.selectNewChats));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_recipientsContacts, 0, R.string.BusinessRecipientsContacts, recipients.selectContacts));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_recipientsNonContacts, 0, R.string.BusinessRecipientsNonContacts, recipients.selectNonContacts));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Explicit per-chat selection. "Include Chats" always adds to chatIds; when
    // excluded chats are supported (businessConnectedBot) a second row edits
    // excludedChatIds.
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Chats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_folderIncludeChats, 0, R.string.IncludeChats));
    if (allowExcludedChats) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_folderExcludeChats, 0, R.string.ExcludeChats));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_recipientsExcludeSelected, 0, R.string.BusinessRecipientsExclude, recipients.excludeSelected));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessRecipientsExcludeHint).setTextColorId(ColorId.textLight));

    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  private CharSequence chatCountSubtitle (@Nullable long[] chatIds) {
    int count = chatIds != null ? chatIds.length : 0;
    if (count == 0) {
      return Lang.getString(R.string.Nobody);
    }
    return Lang.plural(R.string.xChats, count);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_folderIncludeChats) {
      openChatPicker(/* excluded */ false);
      return;
    } else if (id == R.id.btn_folderExcludeChats) {
      openChatPicker(/* excluded */ true);
      return;
    }
    boolean value = adapter.toggleView(v);
    if (id == R.id.btn_recipientsExistingChats) {
      recipients.selectExistingChats = value;
    } else if (id == R.id.btn_recipientsNewChats) {
      recipients.selectNewChats = value;
    } else if (id == R.id.btn_recipientsContacts) {
      recipients.selectContacts = value;
    } else if (id == R.id.btn_recipientsNonContacts) {
      recipients.selectNonContacts = value;
    } else if (id == R.id.btn_recipientsExcludeSelected) {
      recipients.excludeSelected = value;
    }
  }

  /**
   * Opens the shared {@link SelectChatsController} multi-chat picker, pre-selected
   * with the current include/exclude chat ids. We reuse the chat-folder
   * include/exclude modes by handing the picker a throwaway {@link TdApi.ChatFolder}
   * that carries our ids; the new selection comes back via
   * {@link #onSelectedChatsChanged(int, Set, Set)} and is read off the {@code chatIds}
   * we passed (chat types are not shown, so {@code chatTypes} stays empty).
   */
  private void openChatPicker (boolean excluded) {
    long[] currentIds = excluded ? recipients.excludedChatIds : recipients.chatIds;
    if (currentIds == null) {
      currentIds = new long[0];
    }
    // Throwaway folder used purely as the picker's id carrier. showChatTypes=false
    // so no chat-type pseudo-entries are offered (BusinessRecipients has no notion
    // of them — those are the category checkboxes above).
    TdApi.ChatFolder carrier = new TdApi.ChatFolder(
      new TdApi.ChatFolderName(new TdApi.FormattedText("", new TdApi.TextEntity[0]), false),
      null, -1, false,
      new long[0],
      excluded ? new long[0] : currentIds.clone(),
      excluded ? currentIds.clone() : new long[0],
      false, false, false, false, false, false, false, false);
    SelectChatsController c = new SelectChatsController(context, tdlib);
    if (excluded) {
      c.setArguments(SelectChatsController.Arguments.excludedChats(this, 0, carrier, /* showChatTypes */ false));
    } else {
      c.setArguments(SelectChatsController.Arguments.includedChats(this, 0, carrier, /* showChatTypes */ false));
    }
    navigateTo(c);
  }

  @Override
  public void onSelectedChatsChanged (int mode, Set<Long> chatIds, Set<Integer> chatTypes) {
    long[] ids = new long[chatIds.size()];
    int i = 0;
    for (long chatId : chatIds) {
      ids[i++] = chatId;
    }
    if (mode == SelectChatsController.MODE_FOLDER_EXCLUDE_CHATS) {
      recipients.excludedChatIds = ids;
      if (adapter != null) {
        adapter.updateValuedSettingById(R.id.btn_folderExcludeChats);
      }
    } else {
      recipients.chatIds = ids;
      if (adapter != null) {
        adapter.updateValuedSettingById(R.id.btn_folderIncludeChats);
      }
    }
  }

  @Override
  protected boolean onDoneClick () {
    Args args = getArgumentsStrict();
    args.delegate.onRecipientsChanged(recipients);
    navigateBack();
    return true;
  }
}
