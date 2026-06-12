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
 * File created for upgraded gift purchase offer message support
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
 * Renders {@link TdApi.MessageUpgradedGiftPurchaseOffer} — an offer to purchase
 * an upgraded gift. Shows the gift title, offered price and offer state.
 */
public class TGMessageGiftPurchaseOffer extends TGMessageGiveawayBase {
  private final TdApi.MessageUpgradedGiftPurchaseOffer offer;

  public TGMessageGiftPurchaseOffer (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessageUpgradedGiftPurchaseOffer offer) {
    super(manager, msg);
    this.offer = offer;
  }

  @Override
  protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    final TdApi.UpgradedGift gift = offer.gift;

    content.padding(Screen.dp(18));
    TdApi.Sticker sticker = gift != null && gift.model != null ? gift.model.sticker : null;
    TdApi.UpgradedGiftBackdropColors backdropColors = gift != null && gift.backdrop != null ? gift.backdrop.colors : null;
    content.add(new ContentGiftCard(tdlib, msg.id, sticker, backdropColors));
    content.padding(Screen.dp(18));

    content.add(Lang.boldify(Lang.getString(R.string.UpgradedGiftPurchaseOffer)), getTextColorSet(), currentViews);
    content.padding(Screen.dp(4));

    if (gift != null && gift.title != null && !gift.title.isEmpty()) {
      content.add(gift.title, getTextColorSet(), currentViews);
      content.padding(Screen.dp(6));
    }

    String priceLabel = GiftRarityUtil.priceLabel(offer.price);
    if (priceLabel != null) {
      content.add(Lang.getString(R.string.UpgradedGiftOfferPrice, priceLabel), getTextColorSet(), currentViews);
      content.padding(Screen.dp(6));
    }

    int stateRes = stateStringRes(offer.state);
    if (stateRes != 0) {
      content.add(Lang.getString(stateRes), getTextColorSet(), currentViews);
    }

    // TODO(Slice 5): if the offer is pending and incoming, render Accept/Reject buttons
    //   wired to ProcessGiftPurchaseOffer.

    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  private static int stateStringRes (TdApi.GiftPurchaseOfferState state) {
    if (state == null) {
      return 0;
    }
    switch (state.getConstructor()) {
      case TdApi.GiftPurchaseOfferStatePending.CONSTRUCTOR:
        return R.string.UpgradedGiftOfferPending;
      case TdApi.GiftPurchaseOfferStateAccepted.CONSTRUCTOR:
        return R.string.UpgradedGiftOfferAccepted;
      case TdApi.GiftPurchaseOfferStateRejected.CONSTRUCTOR:
        return R.string.UpgradedGiftOfferRejected;
      default:
        return 0;
    }
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
