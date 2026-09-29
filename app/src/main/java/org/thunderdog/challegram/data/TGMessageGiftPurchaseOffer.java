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
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

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

    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  /**
   * Whether this offer is incoming and still pending — i.e. the current user
   * received it and can accept/reject it via {@link TdApi.ProcessGiftPurchaseOffer}.
   */
  private boolean isActionable () {
    return !msg.isOutgoing
      && offer.state != null
      && offer.state.getConstructor() == TdApi.GiftPurchaseOfferStatePending.CONSTRUCTOR;
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
    return isActionable() ? Lang.getString(R.string.GiftOfferRespond) : null;
  }

  private boolean processing;

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    if (!isActionable() || processing) {
      return;
    }
    ViewController<?> c = controller();
    if (c == null) {
      return;
    }
    c.showOptions(Lang.getString(R.string.GiftOfferRespond),
      new int[] {R.id.btn_giftOfferAccept, R.id.btn_giftOfferReject, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.GiftOfferAccept), Lang.getString(R.string.GiftOfferReject), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_check_24, R.drawable.baseline_cancel_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftOfferAccept) {
          processOffer(true);
        } else if (optionId == R.id.btn_giftOfferReject) {
          processOffer(false);
        }
        return true;
      });
  }

  private void processOffer (boolean accept) {
    if (processing) {
      return;
    }
    processing = true;
    tdlib.send(new TdApi.ProcessGiftPurchaseOffer(msg.id, accept), (ok, error) -> UI.post(() -> {
      processing = false;
      if (error != null) {
        UI.showError(error);
        return;
      }
      UI.showToast(accept ? R.string.GiftOfferAccepted_done : R.string.GiftOfferRejected_done, Toast.LENGTH_SHORT);
    }));
  }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
