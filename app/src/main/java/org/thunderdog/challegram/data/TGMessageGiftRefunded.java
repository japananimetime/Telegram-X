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
 *
 * File created for refunded upgraded gift message support
 */
package org.thunderdog.challegram.data;

import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;

import tgx.td.Td;

/**
 * Renders {@link TdApi.MessageRefundedUpgradedGift} — a gift whose purchase,
 * upgrade or transfer was refunded.
 */
public class TGMessageGiftRefunded extends TGMessageGiveawayBase {
  private final TdApi.MessageRefundedUpgradedGift refundedGift;

  public TGMessageGiftRefunded (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessageRefundedUpgradedGift refundedGift) {
    super(manager, msg);
    this.refundedGift = refundedGift;
  }

  @Override
  protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    final TdApi.Gift gift = refundedGift.gift;

    content.padding(Screen.dp(18));
    TdApi.Sticker sticker = gift != null ? gift.sticker : null;
    content.add(new ContentGiftCard(tdlib, msg.id, sticker, null));
    content.padding(Screen.dp(18));

    content.add(Lang.boldify(Lang.getString(R.string.RefundedGift)), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));
    content.add(Lang.getString(R.string.RefundedGiftDesc), getTextColorSet(), currentViews);

    if (!msg.isOutgoing && refundedGift.senderId != null) {
      content.padding(Screen.dp(6));
      content.add(new ContentBubbles(this, maxWidth - Screen.dp(CONTENT_PADDING_DP * 2 + 60)).setOnClickListener(this::onBubbleClick).addChatId(Td.getSenderId(refundedGift.senderId)));
    }

    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  private void onBubbleClick (TdApi.MessageSender senderId) {
    tdlib.ui().openChat(controller(), Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().openProfileInCaseOfPrivateChat());
  }

  @Override
  protected String getButtonText () {
    return null; // No action for a refunded gift.
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) { }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
