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
 * File created for upgraded gift purchase offer rejected message support
 */
package org.thunderdog.challegram.data;

import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

/**
 * Renders {@link TdApi.MessageUpgradedGiftPurchaseOfferRejected} — a purchase
 * offer that was rejected or expired.
 */
public class TGMessageGiftPurchaseOfferRejected extends TGMessageGiveawayBase {
  private final TdApi.MessageUpgradedGiftPurchaseOfferRejected rejected;

  public TGMessageGiftPurchaseOfferRejected (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessageUpgradedGiftPurchaseOfferRejected rejected) {
    super(manager, msg);
    this.rejected = rejected;
  }

  @Override
  protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    final TdApi.UpgradedGift gift = rejected.gift;

    content.padding(Screen.dp(18));
    TdApi.Sticker sticker = gift != null && gift.model != null ? gift.model.sticker : null;
    TdApi.UpgradedGiftBackdropColors backdropColors = gift != null && gift.backdrop != null ? gift.backdrop.colors : null;
    content.add(new ContentGiftCard(tdlib, msg.id, sticker, backdropColors));
    content.padding(Screen.dp(18));

    content.add(Lang.boldify(Lang.getString(rejected.wasExpired ? R.string.UpgradedGiftOfferExpired : R.string.UpgradedGiftOfferRejected)), getTextColorSet(), currentViews);

    if (gift != null && gift.title != null && !gift.title.isEmpty()) {
      content.padding(Screen.dp(6));
      content.add(gift.title, getTextColorSet(), currentViews);
    }

    String priceLabel = GiftRarityUtil.priceLabel(rejected.price);
    if (priceLabel != null) {
      content.padding(Screen.dp(6));
      content.add(Lang.getString(R.string.UpgradedGiftOfferPrice, priceLabel), getTextColorSet(), currentViews);
    }

    invalidateGiveawayReceiver();
    return content.getHeight();
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
