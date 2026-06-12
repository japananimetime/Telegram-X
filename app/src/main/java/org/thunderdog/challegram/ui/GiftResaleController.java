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
 * File created for the gift resale marketplace browser (Slice 5)
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.GiftRarityUtil;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.GiftView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Marketplace browser for upgraded gifts listed for resale. Given the regular
 * gift id of a collectible model family ({@link TdApi.UpgradedGift#regularGiftId}),
 * pages {@link TdApi.SearchGiftsForResale} and shows the listed gifts in a grid.
 * Tapping one opens a buy confirmation that runs {@link TdApi.SendResoldGift}.
 *
 * No attribute filter UI is built in this slice; the catalog is shown ordered by
 * price ({@link TdApi.GiftForResaleOrderPrice}).
 */
public class GiftResaleController extends RecyclerViewController<GiftResaleController.Args> {
  private static final int PAGE_LIMIT = 30;
  private static final int SPAN_COUNT = 3;

  public static class Args {
    public final long regularGiftId;
    public final @Nullable String title;

    public Args (long regularGiftId, @Nullable String title) {
      this.regularGiftId = regularGiftId;
      this.title = title;
    }
  }

  public GiftResaleController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_giftResale;
  }

  @Override
  public CharSequence getName () {
    Args args = getArguments();
    if (args != null && !StringUtils.isEmpty(args.title)) {
      return args.title;
    }
    return Lang.getString(R.string.GiftResale);
  }

  private final List<TdApi.GiftForResale> gifts = new ArrayList<>();
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
    final long giftId = getArgumentsStrict().regularGiftId;
    final String offset = nextOffset;
    tdlib.send(new TdApi.SearchGiftsForResale(
      giftId, new TdApi.GiftForResaleOrderPrice(), false, false,
      new TdApi.UpgradedGiftAttributeId[0], offset, PAGE_LIMIT
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

  private void onGiftsLoaded (TdApi.GiftsForResale result, boolean isInitial) {
    if (isDestroyed()) {
      return;
    }
    final int insertStart = gifts.size();
    if (isInitial) {
      gifts.clear();
    }
    if (result.gifts != null) {
      for (TdApi.GiftForResale gift : result.gifts) {
        if (gift != null && gift.gift != null) {
          gifts.add(gift);
        }
      }
    }
    nextOffset = result.nextOffset;
    endReached = StringUtils.isEmpty(result.nextOffset);
    if (isInitial) {
      adapter.notifyDataSetChanged();
      if (gifts.isEmpty()) {
        UI.showToast(R.string.GiftResaleEmpty, Toast.LENGTH_SHORT);
      }
    } else {
      adapter.notifyItemRangeInserted(insertStart, gifts.size() - insertStart);
    }
  }

  // Buy confirmation

  /**
   * Builds the {@link TdApi.GiftResalePrice} matching a listed gift's resale
   * parameters. Toncoin-only listings produce a TON price; otherwise Stars.
   */
  private static @Nullable TdApi.GiftResalePrice resalePriceOf (@NonNull TdApi.UpgradedGift gift) {
    TdApi.GiftResaleParameters params = gift.resaleParameters;
    if (params == null) {
      return null;
    }
    if (params.toncoinOnly) {
      return new TdApi.GiftResalePriceTon(params.toncoinCentCount);
    }
    if (params.starCount > 0) {
      return new TdApi.GiftResalePriceStar(params.starCount);
    }
    if (params.toncoinCentCount > 0) {
      return new TdApi.GiftResalePriceTon(params.toncoinCentCount);
    }
    return null;
  }

  private void onGiftClick (TdApi.GiftForResale resale) {
    if (resale == null || resale.gift == null || isDestroyed()) {
      return;
    }
    final TdApi.UpgradedGift gift = resale.gift;
    final TdApi.GiftResalePrice price = resalePriceOf(gift);
    if (price == null || StringUtils.isEmpty(gift.name)) {
      UI.showToast(R.string.GiftResaleEmpty, Toast.LENGTH_SHORT);
      return;
    }
    final String priceLabel = GiftRarityUtil.priceLabel(price);
    final String title = !StringUtils.isEmpty(gift.title) ? gift.title : Lang.getString(R.string.UpgradedGift);
    showOptions(Lang.getString(R.string.GiftResaleBuyConfirm, title, priceLabel),
      new int[] {R.id.btn_giftResaleBuy, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.GiftResaleBuy), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_star_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftResaleBuy) {
          buyGift(gift.name, price);
        }
        return true;
      });
  }

  private void buyGift (String giftName, TdApi.GiftResalePrice price) {
    // Star-priced resales can offer an in-app top-up; TON-priced ones cannot (different balance).
    final long starCost = price instanceof TdApi.GiftResalePriceStar ? ((TdApi.GiftResalePriceStar) price).starCount : 0;
    // The resold gift is sent to the current user (self).
    tdlib.send(new TdApi.SendResoldGift(giftName, tdlib.mySender(), price), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        // On insufficient Stars balance (star-priced resale), offer a top-up; otherwise show the error.
        if (starCost == 0 || !tdlib.ui().showStarsBalanceLowPrompt(this, error, starCost)) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_LONG);
        }
        return;
      }
      UI.showToast(R.string.GiftResaleBought, Toast.LENGTH_SHORT);
      navigateBack();
    }));
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
        TdApi.GiftForResale gift = ((GiftView) v).getResaleGift();
        if (gift != null) {
          onGiftClick(gift);
        }
      });
      return new GiftHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull GiftHolder holder, int position) {
      TdApi.GiftForResale resale = gifts.get(position);
      String priceText = null;
      if (resale.gift != null) {
        TdApi.GiftResalePrice price = resalePriceOf(resale.gift);
        priceText = GiftRarityUtil.priceLabel(price);
      }
      ((GiftView) holder.itemView).setResaleGift(tdlib, resale, priceText);
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
      ((GiftView) holder.itemView).setResaleGift(tdlib, null, null);
    }

    @Override
    public int getItemCount () {
      return gifts.size();
    }
  }

  // Static opener

  public static void open (ViewController<?> context, Tdlib tdlib, long regularGiftId, @Nullable String title) {
    if (context == null || tdlib == null || regularGiftId == 0) {
      return;
    }
    GiftResaleController c = new GiftResaleController(context.context(), tdlib);
    c.setArguments(new Args(regularGiftId, title));
    context.context().navigation().navigateTo(c);
  }
}
