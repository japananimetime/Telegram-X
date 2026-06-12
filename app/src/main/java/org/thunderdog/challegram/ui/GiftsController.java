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
 * File created for received gifts surface (Slice 2)
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.navigation.ViewController.OptionColor;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.GiftView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

/**
 * Standalone surface that lists the gifts received by a user or channel
 * ({@link TdApi.GetReceivedGifts}). For the self-owner it also exposes
 * save/unsave and pin/unpin actions through a per-gift detail sheet.
 *
 * Paging is offset-based via {@link TdApi.ReceivedGifts#nextOffset}.
 */
public class GiftsController extends RecyclerViewController<GiftsController.Args> {
  private static final int PAGE_LIMIT = 30;
  private static final int SPAN_COUNT = 3;

  public static class Args {
    public final TdApi.MessageSender ownerId;
    public final boolean isSelf;

    public Args (TdApi.MessageSender ownerId, boolean isSelf) {
      this.ownerId = ownerId;
      this.isSelf = isSelf;
    }
  }

  public GiftsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_gifts;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Gifts);
  }

  private final List<TdApi.ReceivedGift> gifts = new ArrayList<>();
  private GiftsAdapter adapter;

  private String nextOffset = "";
  private boolean isLoading;
  private boolean endReached;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new GiftsAdapter();

    GridLayoutManager manager = new GridLayoutManager(context, SPAN_COUNT);
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView rv, int dx, int dy) {
        if (dy <= 0) {
          return;
        }
        int lastVisible = manager.findLastVisibleItemPosition();
        if (lastVisible >= adapter.getItemCount() - SPAN_COUNT * 2) {
          loadMore();
        }
      }
    });

    loadMore();
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return gifts.isEmpty() && !endReached;
  }

  private void loadMore () {
    if (isLoading || endReached || isDestroyed()) {
      return;
    }
    isLoading = true;
    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    final String offset = nextOffset;
    tdlib.send(new TdApi.GetReceivedGifts(
      null, ownerId, 0,
      false, false, false, false, false, false, false, false, false,
      offset, PAGE_LIMIT
    ), (result, error) -> runOnUiThreadOptional(() -> {
      isLoading = false;
      if (error != null) {
        endReached = true;
        UI.showError(error);
        return;
      }
      onGiftsLoaded(result, offset.isEmpty());
    }));
  }

  private void onGiftsLoaded (TdApi.ReceivedGifts result, boolean isInitial) {
    if (isDestroyed()) {
      return;
    }
    final int insertStart = gifts.size();
    if (isInitial) {
      gifts.clear();
    }
    if (result.gifts != null) {
      for (TdApi.ReceivedGift gift : result.gifts) {
        gifts.add(gift);
      }
    }
    nextOffset = result.nextOffset;
    endReached = StringUtils.isEmpty(result.nextOffset);
    if (isInitial) {
      adapter.notifyDataSetChanged();
    } else {
      adapter.notifyItemRangeInserted(insertStart, gifts.size() - insertStart);
    }
  }

  private int indexOfGift (String receivedGiftId) {
    if (StringUtils.isEmpty(receivedGiftId)) {
      return -1;
    }
    for (int i = 0; i < gifts.size(); i++) {
      if (receivedGiftId.equals(gifts.get(i).receivedGiftId)) {
        return i;
      }
    }
    return -1;
  }

  // Detail sheet + actions

  private void openGiftDetails (TdApi.ReceivedGift gift) {
    if (gift == null || isDestroyed()) {
      return;
    }
    final boolean isSelf = getArgumentsStrict().isSelf;

    String info = buildGiftInfo(tdlib, gift);

    final ArrayList<Integer> ids = new ArrayList<>();
    final ArrayList<String> titles = new ArrayList<>();
    final ArrayList<Integer> icons = new ArrayList<>();

    if (isSelf && !StringUtils.isEmpty(gift.receivedGiftId)) {
      ids.add(R.id.btn_giftToggleSaved);
      titles.add(Lang.getString(gift.isSaved ? R.string.GiftUnsave : R.string.GiftSave));
      icons.add(gift.isSaved ? R.drawable.baseline_eye_off_24 : R.drawable.baseline_visibility_24);

      // Pin requires an upgraded + saved gift.
      boolean isUpgraded = gift.gift != null && gift.gift.getConstructor() == TdApi.SentGiftUpgraded.CONSTRUCTOR;
      if (isUpgraded && (gift.isSaved || gift.isPinned)) {
        ids.add(R.id.btn_giftTogglePinned);
        titles.add(Lang.getString(gift.isPinned ? R.string.GiftUnpin : R.string.GiftPin));
        icons.add(gift.isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24);
      }

      // Convert (sell) a regular gift for Stars. Upgraded gifts can't be sold this way.
      if (!isUpgraded && gift.sellStarCount > 0 && !gift.wasRefunded) {
        ids.add(R.id.btn_giftSell);
        titles.add(Lang.getString(R.string.ConvertGiftToStars));
        icons.add(R.drawable.baseline_star_24);
      }

      // Upgrade a regular gift into a unique collectible (Slice 4).
      if (!isUpgraded && gift.canBeUpgraded && !gift.wasRefunded) {
        ids.add(R.id.btn_giftUpgrade);
        titles.add(Lang.getString(R.string.UpgradeGift));
        icons.add(R.drawable.baseline_premium_star_24);
      }

      // Open the collectible detail screen (which itself offers Transfer / Wear /
      // Export / Drop-details) for upgraded gifts (Slice 4).
      if (isUpgraded) {
        ids.add(R.id.btn_giftTransfer);
        titles.add(Lang.getString(R.string.ViewGift));
        icons.add(R.drawable.baseline_visibility_24);
      }
    }

    if (ids.isEmpty()) {
      // Read-only: still show the info as a non-actionable popup.
      ids.add(R.id.btn_cancel);
      titles.add(Lang.getString(R.string.OK));
      icons.add(R.drawable.baseline_check_24);
    }

    int[] idArray = new int[ids.size()];
    int[] iconArray = new int[icons.size()];
    for (int i = 0; i < ids.size(); i++) {
      idArray[i] = ids.get(i);
      iconArray[i] = icons.get(i);
    }

    final OptionDelegate delegate = (itemView, id) -> {
      if (id == R.id.btn_giftToggleSaved) {
        toggleSaved(gift);
      } else if (id == R.id.btn_giftTogglePinned) {
        togglePinned(gift);
      } else if (id == R.id.btn_giftSell) {
        confirmSell(gift);
      } else if (id == R.id.btn_giftUpgrade) {
        confirmUpgrade(gift);
      } else if (id == R.id.btn_giftTransfer) {
        openUpgradedDetail(gift);
      }
      return true;
    };

    showOptions(info, idArray, titles.toArray(new String[0]), null, iconArray, delegate);
  }

  private static String buildGiftInfo (Tdlib tdlib, TdApi.ReceivedGift gift) {
    StringBuilder info = new StringBuilder();
    if (gift.senderId != null && !gift.isPrivate) {
      info.append(Lang.getString(R.string.GiftFrom)).append(": ").append(tdlib.chatTitle(Td.getSenderId(gift.senderId)));
    } else {
      info.append(Lang.getString(R.string.GiftFrom)).append(": ").append(Lang.getString(R.string.GiftPrivate));
    }
    info.append("\n").append(Lang.getString(R.string.GiftDate)).append(": ")
      .append(Lang.getString(R.string.format_GiveawayDateTime,
        Lang.dateYearFull(gift.date, TimeUnit.SECONDS),
        Lang.time(gift.date, TimeUnit.SECONDS)));
    long starCount = giftStarCount(gift.gift);
    if (starCount > 0) {
      info.append("\n").append(Lang.plural(R.string.xGiftValue, (int) starCount));
    }
    if (gift.text != null && !StringUtils.isEmpty(gift.text.text)) {
      info.append("\n\n").append(gift.text.text);
    }
    return info.toString();
  }

  /**
   * Fetches a received gift by id and shows a read-only detail sheet from an
   * arbitrary controller (e.g. a gift message bubble - Slice 1 entry point).
   */
  public static void openGiftDetailsById (org.thunderdog.challegram.navigation.ViewController<?> context, Tdlib tdlib, String receivedGiftId) {
    if (context == null || tdlib == null || StringUtils.isEmpty(receivedGiftId)) {
      return;
    }
    tdlib.send(new TdApi.GetReceivedGift(receivedGiftId), (result, error) -> context.runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      String info = buildGiftInfo(tdlib, result);
      context.showOptions(info,
        new int[] {R.id.btn_cancel},
        new String[] {Lang.getString(R.string.OK)},
        null,
        new int[] {R.drawable.baseline_check_24});
    }));
  }

  private void toggleSaved (TdApi.ReceivedGift gift) {
    final String id = gift.receivedGiftId;
    final boolean newValue = !gift.isSaved;
    tdlib.send(new TdApi.ToggleGiftIsSaved(id, newValue), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      int index = indexOfGift(id);
      if (index != -1) {
        gifts.get(index).isSaved = newValue;
        if (!newValue) {
          gifts.get(index).isPinned = false;
        }
        adapter.notifyItemChanged(index);
      }
      UI.showToast(newValue ? R.string.GiftDisplayedOnPage : R.string.GiftHiddenFromPage, android.widget.Toast.LENGTH_SHORT);
    }));
  }

  private void togglePinned (TdApi.ReceivedGift gift) {
    final String id = gift.receivedGiftId;
    final boolean shouldPin = !gift.isPinned;

    // Rebuild the pinned id list from the current state.
    final ArrayList<String> pinnedIds = new ArrayList<>();
    for (TdApi.ReceivedGift g : gifts) {
      if (StringUtils.isEmpty(g.receivedGiftId)) {
        continue;
      }
      boolean pinned = id.equals(g.receivedGiftId) ? shouldPin : g.isPinned;
      if (pinned) {
        pinnedIds.add(g.receivedGiftId);
      }
    }

    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    tdlib.send(new TdApi.SetPinnedGifts(ownerId, pinnedIds.toArray(new String[0])), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showToast(R.string.GiftPinFailed, android.widget.Toast.LENGTH_SHORT);
        return;
      }
      int index = indexOfGift(id);
      if (index != -1) {
        gifts.get(index).isPinned = shouldPin;
        adapter.notifyItemChanged(index);
      }
      UI.showToast(shouldPin ? R.string.GiftPinnedToTop : R.string.GiftUnpinned, android.widget.Toast.LENGTH_SHORT);
    }));
  }

  private void confirmSell (TdApi.ReceivedGift gift) {
    if (gift == null || StringUtils.isEmpty(gift.receivedGiftId) || gift.sellStarCount <= 0) {
      return;
    }
    showOptions(Lang.pluralBold(R.string.ConvertGiftConfirm, (int) gift.sellStarCount),
      new int[] {R.id.btn_giftSell, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.ConvertGiftToStars), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_star_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_giftSell) {
          sellGift(gift);
        }
        return true;
      });
  }

  private void sellGift (TdApi.ReceivedGift gift) {
    final String id = gift.receivedGiftId;
    tdlib.send(new TdApi.SellGift(null, id), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      int index = indexOfGift(id);
      if (index != -1) {
        gifts.remove(index);
        adapter.notifyItemRemoved(index);
      }
      UI.showToast(R.string.GiftConvertedToStars, android.widget.Toast.LENGTH_SHORT);
    }));
  }

  // Upgrade a regular gift into a unique collectible (Slice 4).

  private void confirmUpgrade (TdApi.ReceivedGift gift) {
    if (gift == null || StringUtils.isEmpty(gift.receivedGiftId) || gift.gift == null
      || gift.gift.getConstructor() != TdApi.SentGiftRegular.CONSTRUCTOR) {
      return;
    }
    final TdApi.Gift regularGift = ((TdApi.SentGiftRegular) gift.gift).gift;
    final long regularGiftId = regularGift != null ? regularGift.id : 0;
    if (regularGiftId == 0) {
      return;
    }
    // Fetch the upgrade preview to confirm the gift is upgradeable and to get a
    // star-cost fallback when nothing was prepaid.
    tdlib.send(new TdApi.GetGiftUpgradePreview(regularGiftId), (preview, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      // If the sender prepaid the upgrade, pass 0; otherwise pay the current price.
      final long starCount;
      if (gift.prepaidUpgradeStarCount > 0) {
        starCount = 0;
      } else {
        long price = 0;
        if (preview != null && preview.prices != null && preview.prices.length > 0) {
          // prices run from the maximum price to the minimum price; take the lowest.
          price = preview.prices[preview.prices.length - 1].starCount;
        }
        if (price == 0 && regularGift.upgradeStarCount > 0) {
          price = regularGift.upgradeStarCount;
        }
        starCount = price;
      }
      showUpgradeConfirm(gift, starCount);
    }));
  }

  private void showUpgradeConfirm (TdApi.ReceivedGift gift, long starCount) {
    final CharSequence message;
    if (starCount > 0) {
      message = Lang.getString(R.string.UpgradeGiftConfirm, Lang.plural(R.string.xStars, starCount));
    } else {
      message = Lang.getString(R.string.UpgradeGiftFree);
    }
    // The "keep original details" toggle is captured via two distinct options
    // rather than a custom checkbox widget.
    showOptions(message,
      new int[] {R.id.btn_giftUpgrade, R.id.btn_giftKeepOriginalDetails, R.id.btn_cancel},
      new String[] {
        Lang.getString(R.string.UpgradeGift),
        Lang.getString(R.string.UpgradeGiftKeepDetails),
        Lang.getString(R.string.Cancel)
      },
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_premium_star_24, R.drawable.baseline_visibility_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_giftUpgrade) {
          performUpgrade(gift, false, starCount);
        } else if (id == R.id.btn_giftKeepOriginalDetails) {
          performUpgrade(gift, true, starCount);
        }
        return true;
      });
  }

  private void performUpgrade (TdApi.ReceivedGift gift, boolean keepOriginalDetails, long starCount) {
    final String id = gift.receivedGiftId;
    tdlib.send(new TdApi.UpgradeGift(null, id, keepOriginalDetails, starCount), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      UI.showToast(R.string.UpgradeGiftDone, android.widget.Toast.LENGTH_SHORT);
      // Remove the old (regular) gift entry; the upgraded gift lives under a new id.
      int index = indexOfGift(id);
      if (index != -1) {
        gifts.remove(index);
        adapter.notifyItemRemoved(index);
      }
      if (result != null && result.gift != null) {
        UpgradedGiftController.Args args = new UpgradedGiftController.Args(
          result.gift, result.receivedGiftId, result.canBeTransferred,
          result.transferStarCount, result.dropOriginalDetailsStarCount, result.exportDate);
        UpgradedGiftController.open(this, tdlib, args);
      }
    }));
  }

  private void openUpgradedDetail (TdApi.ReceivedGift gift) {
    if (gift == null || gift.gift == null || gift.gift.getConstructor() != TdApi.SentGiftUpgraded.CONSTRUCTOR) {
      return;
    }
    final TdApi.UpgradedGift upgraded = ((TdApi.SentGiftUpgraded) gift.gift).gift;
    if (upgraded == null) {
      return;
    }
    UpgradedGiftController.Args args = new UpgradedGiftController.Args(
      upgraded, gift.receivedGiftId, gift.canBeTransferred,
      gift.transferStarCount, gift.dropOriginalDetailsStarCount, gift.exportDate);
    UpgradedGiftController.open(this, tdlib, args);
  }

  private static long giftStarCount (@Nullable TdApi.SentGift sentGift) {
    if (sentGift != null && sentGift.getConstructor() == TdApi.SentGiftRegular.CONSTRUCTOR) {
      TdApi.Gift g = ((TdApi.SentGiftRegular) sentGift).gift;
      return g != null ? g.starCount : 0;
    }
    return 0;
  }

  // Adapter

  private class GiftHolder extends RecyclerView.ViewHolder {
    public GiftHolder (View itemView) {
      super(itemView);
    }
  }

  private class GiftsAdapter extends RecyclerView.Adapter<GiftHolder> {
    @NonNull
    @Override
    public GiftHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      GiftView view = new GiftView(context());
      view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(112)));
      view.setOnClickListener(v -> {
        TdApi.ReceivedGift gift = ((GiftView) v).getGift();
        if (gift != null) {
          openGiftDetails(gift);
        }
      });
      return new GiftHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull GiftHolder holder, int position) {
      ((GiftView) holder.itemView).setGift(tdlib, gifts.get(position));
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
      ((GiftView) holder.itemView).setGift(tdlib, null);
    }

    @Override
    public int getItemCount () {
      return gifts.size();
    }
  }
}
