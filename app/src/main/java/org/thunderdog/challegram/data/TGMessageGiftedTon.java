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
 * File created for gifted TON message support
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

/**
 * Renders {@link TdApi.MessageGiftedTon} — a Toncoin gift.
 * {@code tonAmount} is given in nanotons (1 TON = 1_000_000_000 nanotons).
 */
public class TGMessageGiftedTon extends TGMessageGiveawayBase {
  private static final int TON_DECIMALS = 9;

  private final TdApi.MessageGiftedTon giftedTon;

  public TGMessageGiftedTon (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessageGiftedTon giftedTon) {
    super(manager, msg);
    this.giftedTon = giftedTon;
  }

  @Override
  protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    content.padding(Screen.dp(18));
    if (giftedTon.sticker != null) {
      content.add(new ContentGiftCard(tdlib, msg.id, giftedTon.sticker, null));
    } else {
      content.add(new ContentDrawable(R.drawable.baseline_gift_72));
    }
    content.padding(Screen.dp(18));

    String amount = GiftRarityUtil.formatTon(giftedTon.tonAmount, TON_DECIMALS);
    content.add(Lang.boldify(Lang.getString(R.string.GiftedTonAmount, amount)), getTextColorSet(), currentViews);

    if (!msg.isOutgoing && giftedTon.gifterUserId != 0) {
      content.padding(Screen.dp(6));
      content.add(new ContentBubbles(this, maxWidth - Screen.dp(CONTENT_PADDING_DP * 2 + 60)).setOnClickListener(this::onBubbleClick).addChatId(giftedTon.gifterUserId));
    }

    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  private void onBubbleClick (TdApi.MessageSender senderId) {
    tdlib.ui().openChat(controller(), tgx.td.Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().openProfileInCaseOfPrivateChat());
  }

  @Override
  protected String getButtonText () {
    return null;
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) { }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
