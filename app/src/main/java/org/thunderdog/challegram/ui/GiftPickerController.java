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
 * File created for the send-a-gift picker (Slice 3)
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.GiftView;

import java.util.ArrayList;
import java.util.List;

/**
 * Picker for choosing and sending a {@link TdApi.Gift} to a user or channel
 * ({@link TdApi.SendGift}). Loads the catalog via {@link TdApi.GetAvailableGifts},
 * shows the buyable gifts in a grid, and runs a small send-confirmation step
 * (optional message, private toggle, pay-for-upgrade toggle).
 *
 * Auction gifts (gift.auctionInfo != null) are not directly buyable in this slice
 * and are skipped. Sold-out gifts are shown disabled.
 */
public class GiftPickerController extends RecyclerViewController<GiftPickerController.Args> {
  private static final int SPAN_COUNT = 3;

  public static class Args {
    public final TdApi.MessageSender ownerId;

    public Args (TdApi.MessageSender ownerId) {
      this.ownerId = ownerId;
    }
  }

  public GiftPickerController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_giftPicker;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.SendGift);
  }

  private final List<TdApi.AvailableGift> gifts = new ArrayList<>();
  private GiftsAdapter adapter;
  private boolean loaded;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new GiftsAdapter();

    GridLayoutManager manager = new GridLayoutManager(context, SPAN_COUNT);
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);

    load();
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return !loaded;
  }

  private void load () {
    tdlib.send(new TdApi.GetAvailableGifts(), (result, error) -> runOnUiThreadOptional(() -> {
      loaded = true;
      if (error != null) {
        UI.showError(error);
        return;
      }
      gifts.clear();
      if (result.gifts != null) {
        for (TdApi.AvailableGift gift : result.gifts) {
          if (gift == null || gift.gift == null) {
            continue;
          }
          // Auction gifts can't be purchased directly in this slice.
          if (gift.gift.auctionInfo != null) {
            continue;
          }
          gifts.add(gift);
        }
      }
      adapter.notifyDataSetChanged();
    }));
  }

  private static boolean isSoldOut (@NonNull TdApi.Gift gift) {
    if (gift.userLimits != null && gift.userLimits.remainingCount == 0) {
      return true;
    }
    return gift.overallLimits != null && gift.overallLimits.remainingCount == 0;
  }

  // Send confirmation

  private void onGiftClick (TdApi.AvailableGift available) {
    if (available == null || available.gift == null || isDestroyed()) {
      return;
    }
    final TdApi.Gift gift = available.gift;
    if (isSoldOut(gift)) {
      UI.showToast(R.string.GiftSoldOut, Toast.LENGTH_SHORT);
      return;
    }
    // Gate behind canSendGift if the next send date is in the future.
    if (gift.nextSendDate > (int) (System.currentTimeMillis() / 1000L)) {
      tdlib.send(new TdApi.CanSendGift(gift.id), (result, error) -> runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showError(error);
          return;
        }
        if (result.getConstructor() == TdApi.CanSendGiftResultFail.CONSTRUCTOR) {
          TdApi.FormattedText reason = ((TdApi.CanSendGiftResultFail) result).reason;
          UI.showToast(reason != null ? reason.text : Lang.getString(R.string.GiftCannotSend), Toast.LENGTH_LONG);
          return;
        }
        promptMessage(gift);
      }));
      return;
    }
    promptMessage(gift);
  }

  private void promptMessage (TdApi.Gift gift) {
    // Optional message step, then the confirm sheet.
    openInputAlert(Lang.getString(R.string.SendGift), Lang.getString(R.string.GiftMessageHint),
      R.string.Continue, R.string.Cancel, null, (inputView, text) -> {
        showSendConfirm(gift, text != null ? text.trim() : "");
        return true;
      }, true);
  }

  private void showSendConfirm (TdApi.Gift gift, String message) {
    final boolean canUpgrade = gift.upgradeStarCount > 0;

    final List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_giftIsPrivate, 0, R.string.MakePrivate, false));
    if (canUpgrade) {
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_giftPayForUpgrade, 0,
        Lang.plural(R.string.PayForUpgradeStars, (int) gift.upgradeStarCount), false));
    }

    final CharSequence header = Lang.pluralBold(R.string.SendGiftConfirm, (int) gift.starCount);

    showSettings(new SettingsWrapBuilder(R.id.btn_giftSend)
      .addHeaderItem(header)
      .setRawItems(items.toArray(new ListItem[0]))
      .setSaveStr(R.string.SendGift)
      .setIntDelegate((id, result) -> {
        boolean isPrivate = result.get(R.id.btn_giftIsPrivate) != 0;
        boolean payForUpgrade = canUpgrade && result.get(R.id.btn_giftPayForUpgrade) != 0;
        sendGift(gift, message, isPrivate, payForUpgrade);
      }));
  }

  private void sendGift (TdApi.Gift gift, String message, boolean isPrivate, boolean payForUpgrade) {
    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    TdApi.FormattedText text = null;
    if (message != null && !message.isEmpty()) {
      text = new TdApi.FormattedText(message, new TdApi.TextEntity[0]);
    }
    tdlib.send(new TdApi.SendGift(gift.id, ownerId, text, isPrivate, payForUpgrade), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        // Insufficient balance surfaces here; we show the error string.
        // TODO(Slice 3+): the Stars purchase flow (SettingsStarsController.purchaseStars)
        //   is stubbed, so we cannot offer an in-app top-up yet.
        UI.showToast(TD.toErrorString(error), Toast.LENGTH_LONG);
        return;
      }
      UI.showToast(R.string.GiftSent, Toast.LENGTH_SHORT);
      navigateBack();
    }));
  }

  // Adapter

  private static class GiftHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
    public GiftHolder (View itemView) {
      super(itemView);
    }
  }

  private class GiftsAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<GiftHolder> {
    @NonNull
    @Override
    public GiftHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      GiftView view = new GiftView(context());
      view.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(112)));
      view.setOnClickListener(v -> {
        TdApi.AvailableGift gift = ((GiftView) v).getAvailableGift();
        if (gift != null) {
          onGiftClick(gift);
        }
      });
      return new GiftHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull GiftHolder holder, int position) {
      ((GiftView) holder.itemView).setAvailableGift(tdlib, gifts.get(position), isSoldOut(gifts.get(position).gift));
    }

    @Override
    public void onViewAttachedToWindow (@NonNull GiftHolder holder) {
      ((AttachDelegate) holder.itemView).attach();
    }

    @Override
    public void onViewDetachedFromWindow (@NonNull GiftHolder holder) {
      ((AttachDelegate) holder.itemView).detach();
    }

    @Override
    public void onViewRecycled (@NonNull GiftHolder holder) {
      ((GiftView) holder.itemView).setAvailableGift(tdlib, null, false);
    }

    @Override
    public int getItemCount () {
      return gifts.size();
    }
  }
}
