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
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Shared editor for {@link TdApi.BusinessRecipients}. Lets the user pick which
 * private chats automatic messages apply to via category toggles (all existing,
 * all new, contacts, non-contacts) and choose between an "include" and an
 * "exclude" interpretation. Any explicitly-selected chat identifiers from an
 * existing configuration are preserved untouched (picking individual chats is a
 * follow-up). The result is handed back to the launching editor through
 * {@link Delegate}; this controller does not itself send any Set* request.
 */
public class BusinessRecipientsController extends EditBaseController<BusinessRecipientsController.Args> implements View.OnClickListener {

  public interface Delegate {
    void onRecipientsChanged (@NonNull TdApi.BusinessRecipients recipients);
  }

  public static class Args {
    public final @NonNull TdApi.BusinessRecipients recipients;
    public final @NonNull Delegate delegate;

    public Args (@Nullable TdApi.BusinessRecipients recipients, @NonNull Delegate delegate) {
      this.recipients = recipients != null ? recipients : emptyRecipients();
      this.delegate = delegate;
    }
  }

  public static TdApi.BusinessRecipients emptyRecipients () {
    return new TdApi.BusinessRecipients(new long[0], new long[0], false, false, false, false, false);
  }

  private SettingsAdapter adapter;
  private TdApi.BusinessRecipients recipients;

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
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);

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

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_recipientsExcludeSelected, 0, R.string.BusinessRecipientsExclude, recipients.excludeSelected));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessRecipientsExcludeHint).setTextColorId(ColorId.textLight));

    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
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

  @Override
  protected boolean onDoneClick () {
    Args args = getArgumentsStrict();
    args.delegate.onRecipientsChanged(recipients);
    navigateBack();
    return true;
  }
}
