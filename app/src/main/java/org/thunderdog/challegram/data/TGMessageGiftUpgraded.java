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
 * File created for upgraded (collectible) gift message support
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
 * Renders {@link TdApi.MessageUpgradedGift} — the collectible / NFT upgraded gift.
 * Shows the backdrop gradient card with the model sticker centered, the gift
 * title, "Gift #N of M", the model rarity and a sender/receiver context line.
 */
public class TGMessageGiftUpgraded extends TGMessageGiveawayBase {
  private final TdApi.MessageUpgradedGift upgradedGift;

  public TGMessageGiftUpgraded (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessageUpgradedGift upgradedGift) {
    super(manager, msg);
    this.upgradedGift = upgradedGift;
  }

  @Override
  protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    final TdApi.UpgradedGift gift = upgradedGift.gift;

    content.padding(Screen.dp(18));
    TdApi.Sticker sticker = gift != null && gift.model != null ? gift.model.sticker : null;
    TdApi.UpgradedGiftBackdropColors backdropColors = gift != null && gift.backdrop != null ? gift.backdrop.colors : null;
    content.add(new ContentGiftCard(tdlib, msg.id, sticker, backdropColors));
    content.padding(Screen.dp(18));

    // Title
    if (gift != null && gift.title != null && !gift.title.isEmpty()) {
      content.add(Lang.boldify(gift.title), getTextColorSet(), currentViews);
      content.padding(Screen.dp(4));
    } else {
      content.add(Lang.boldify(Lang.getString(R.string.GiftUpgraded)), getTextColorSet(), currentViews);
      content.padding(Screen.dp(4));
    }

    // "Gift #N of M"
    if (gift != null && gift.number > 0) {
      content.add(Lang.getString(R.string.UpgradedGiftNumber, gift.number, Math.max(gift.totalUpgradedCount, gift.number)), getTextColorSet(), currentViews);
      content.padding(Screen.dp(6));
    }

    // Model rarity label
    if (gift != null && gift.model != null && gift.model.rarity != null) {
      String rarity = GiftRarityUtil.rarityLabel(gift.model.rarity);
      if (rarity != null) {
        content.add(Lang.getString(R.string.UpgradedGiftRarity, rarity), getTextColorSet(), currentViews);
        content.padding(Screen.dp(6));
      }
    }

    // Sender/receiver context line
    if (!msg.isOutgoing && upgradedGift.senderId != null) {
      content.add(new ContentBubbles(this, maxWidth - Screen.dp(CONTENT_PADDING_DP * 2 + 60)).setOnClickListener(this::onBubbleClick).addChatId(Td.getSenderId(upgradedGift.senderId)));
      content.padding(Screen.dp(6));
    }

    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  private void onBubbleClick (TdApi.MessageSender senderId) {
    tdlib.ui().openChat(controller(), Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().openProfileInCaseOfPrivateChat());
  }

  @Override
  protected String getButtonText () {
    return Lang.getString(R.string.ViewGift);
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    // TODO(gift-detail / Slice 4): open the upgraded gift detail screen via GetUpgradedGift
    // using upgradedGift.gift.name / receivedGiftId. For now this is a no-op.
  }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
