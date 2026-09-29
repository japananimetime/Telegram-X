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
 * File created for the gift-auction detail screen (Slice 6)
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.GiftAuctionHeaderView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Detail screen for a single gift auction ({@link TdApi.GetGiftAuctionState}).
 * Renders a hero header (gift sticker + background gradient) and the live auction
 * state. For an active auction it shows round/time/min-bid/your-bid/items info and
 * exposes a "Place Bid" ({@link TdApi.PlaceGiftAuctionBid}) or "Raise Bid"
 * ({@link TdApi.IncreaseGiftAuctionBid}) action, both via a Stars-amount input
 * that enforces the minimum. For a finished auction it shows the summary (average
 * price, items acquired, Fragment link) and no bid actions.
 *
 * Live updates: subscribes to {@link Tdlib.GiftAuctionListener}; the displayed
 * state refreshes when {@link TdApi.UpdateGiftAuctionState} arrives for this
 * auction. Unsubscribes in {@link #destroy()}.
 */
public class GiftAuctionController extends RecyclerViewController<GiftAuctionController.Args> implements View.OnClickListener, Tdlib.GiftAuctionListener {
  private static final int CUSTOM_HEADER = 0;

  public static class Args {
    public final String auctionId;
    public final @Nullable TdApi.GiftAuctionState initialState;

    public Args (String auctionId) {
      this(auctionId, null);
    }

    public Args (String auctionId, @Nullable TdApi.GiftAuctionState initialState) {
      this.auctionId = auctionId;
      this.initialState = initialState;
    }
  }

  public GiftAuctionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_giftAuction;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.GiftAuction);
  }

  private SettingsAdapter adapter;
  private GiftAuctionHeaderView headerView;
  private @Nullable TdApi.GiftAuctionState state;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.state = getArgumentsStrict().initialState;

    adapter = new SettingsAdapter(this) {
      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        GiftAuctionHeaderView view = new GiftAuctionHeaderView(context());
        view.setLayoutParams(new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        GiftAuctionController.this.headerView = view;
        bindHeader();
        return new SettingHolder(view);
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setData(item.getStringValue());
      }
    };

    adapter.setItems(buildItems().toArray(new ListItem[0]), false);
    recyclerView.setAdapter(adapter);

    tdlib.addGiftAuctionListener(this);

    // Always refresh from the server so the screen reflects the latest state even
    // when opened from a stale snapshot or a deep link.
    requestState();
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return state == null;
  }

  private void requestState () {
    final String auctionId = getArgumentsStrict().auctionId;
    if (StringUtils.isEmpty(auctionId)) {
      return;
    }
    tdlib.send(new TdApi.GetGiftAuctionState(auctionId), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      applyState(result);
    }));
  }

  private void applyState (@Nullable TdApi.GiftAuctionState newState) {
    if (newState == null || isDestroyed()) {
      return;
    }
    this.state = newState;
    bindHeader();
    if (adapter != null) {
      adapter.setItems(buildItems().toArray(new ListItem[0]), false);
    }
  }

  private void bindHeader () {
    if (headerView == null) {
      return;
    }
    TdApi.Gift gift = state != null ? state.gift : null;
    String title = gift != null && gift.sticker != null && !StringUtils.isEmpty(gift.sticker.emoji) ? gift.sticker.emoji : Lang.getString(R.string.GiftAuction);
    String caption = buildHeaderCaption();
    headerView.setGift(tdlib, gift, title, caption);
  }

  private @Nullable String buildHeaderCaption () {
    if (state == null || state.state == null) {
      return null;
    }
    if (state.state.getConstructor() == TdApi.AuctionStateActive.CONSTRUCTOR) {
      TdApi.AuctionStateActive active = (TdApi.AuctionStateActive) state.state;
      if (active.totalRoundCount > 0) {
        return Lang.getString(R.string.GiftAuctionRoundOf, active.currentRoundNumber, active.totalRoundCount);
      }
    } else if (state.state.getConstructor() == TdApi.AuctionStateFinished.CONSTRUCTOR) {
      return Lang.getString(R.string.GiftAuctionFinished);
    }
    return null;
  }

  // Item building

  private List<ListItem> buildItems () {
    final List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_CUSTOM - CUSTOM_HEADER));

    if (state == null || state.state == null) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      return items;
    }

    switch (state.state.getConstructor()) {
      case TdApi.AuctionStateActive.CONSTRUCTOR:
        buildActiveItems(items, (TdApi.AuctionStateActive) state.state);
        break;
      case TdApi.AuctionStateFinished.CONSTRUCTOR:
        buildFinishedItems(items, (TdApi.AuctionStateFinished) state.state);
        break;
    }

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    return items;
  }

  private void buildActiveItems (List<ListItem> items, TdApi.AuctionStateActive active) {
    final int now = (int) (tdlib.currentTimeMillis() / 1000L);

    final List<ListItem> info = new ArrayList<>();
    // Round / time.
    if (active.totalRoundCount > 0) {
      addValue(info, R.string.GiftAuctionRound, Lang.getString(R.string.GiftAuctionRoundOf, active.currentRoundNumber, active.totalRoundCount));
    }
    if (active.startDate > now) {
      addValue(info, R.string.GiftAuctionStarts, Lang.getDuration(Math.max(0, active.startDate - now)));
    } else {
      if (active.currentRoundEndDate > now) {
        addValue(info, R.string.GiftAuctionRoundEnds, Lang.getDuration(Math.max(0, active.currentRoundEndDate - now)));
      }
      if (active.endDate > now) {
        addValue(info, R.string.GiftAuctionEnds, Lang.getDuration(Math.max(0, active.endDate - now)));
      }
    }
    // Bids.
    addValue(info, R.string.GiftAuctionMinBid, Lang.plural(R.string.xStars, active.minBid));
    if (active.userBid != null) {
      addValue(info, R.string.GiftAuctionYourBid, Lang.plural(R.string.xStars, active.userBid.starCount));
      if (active.userBid.nextBidStarCount > 0) {
        addValue(info, R.string.GiftAuctionNextBid, Lang.plural(R.string.xStars, active.userBid.nextBidStarCount));
      }
    }
    // Items.
    if (active.leftItemCount > 0) {
      addValue(info, R.string.GiftAuctionItemsLeft, Integer.toString(active.leftItemCount));
    }
    if (active.distributedItemCount > 0) {
      addValue(info, R.string.GiftAuctionItemsDistributed, Integer.toString(active.distributedItemCount));
    }
    if (active.acquiredItemCount > 0) {
      addValue(info, R.string.GiftAuctionItemsAcquired, Integer.toString(active.acquiredItemCount));
    }
    appendSection(items, info);

    // If the user's previous bid was outbid and returned, note it.
    if (active.userBid != null && active.userBid.wasReturned) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.GiftAuctionBidReturned));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    // Actions: place or raise a bid. Auctions that have not started yet still
    // accept bids in TDLib, so the action is shown whenever the auction is active.
    final List<ListItem> actions = new ArrayList<>();
    if (active.userBid == null) {
      actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftAuctionPlaceBid, R.drawable.baseline_star_24, R.string.GiftAuctionPlaceBid));
    } else {
      actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftAuctionRaiseBid, R.drawable.baseline_star_24, R.string.GiftAuctionRaiseBid));
    }
    appendSection(items, actions);
  }

  private void buildFinishedItems (List<ListItem> items, TdApi.AuctionStateFinished finished) {
    final List<ListItem> info = new ArrayList<>();
    if (finished.averagePrice > 0) {
      addValue(info, R.string.GiftAuctionAveragePrice, Lang.plural(R.string.xStars, finished.averagePrice));
    }
    if (finished.acquiredItemCount > 0) {
      addValue(info, R.string.GiftAuctionItemsAcquired, Integer.toString(finished.acquiredItemCount));
    }
    appendSection(items, info);

    if (!StringUtils.isEmpty(finished.fragmentUrl)) {
      final List<ListItem> actions = new ArrayList<>();
      actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftAuctionFragment, R.drawable.baseline_open_in_browser_24, R.string.GiftAuctionFragment));
      appendSection(items, actions);
    }
  }

  private static void addValue (List<ListItem> dst, int labelRes, CharSequence value) {
    dst.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, 0, labelRes)
      .setStringValue(value != null ? value.toString() : ""));
  }

  private static void appendSection (List<ListItem> items, List<ListItem> rows) {
    if (rows.isEmpty()) {
      return;
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(rows.get(i));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  // Actions

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_giftAuctionPlaceBid) {
      promptPlaceBid();
    } else if (id == R.id.btn_giftAuctionRaiseBid) {
      promptRaiseBid();
    } else if (id == R.id.btn_giftAuctionFragment) {
      openFragment();
    }
  }

  private void promptPlaceBid () {
    if (state == null || state.state == null || state.state.getConstructor() != TdApi.AuctionStateActive.CONSTRUCTOR) {
      return;
    }
    final TdApi.AuctionStateActive active = (TdApi.AuctionStateActive) state.state;
    final long minBid = active.minBid;
    openInputAlert(
      Lang.getString(R.string.GiftAuctionPlaceBid),
      Lang.getString(R.string.GiftAuctionBidHint, Lang.plural(R.string.xStars, minBid)),
      R.string.GiftAuctionPlaceBid, R.string.Cancel, null,
      (inputView, result) -> {
        long stars = parseStars(result);
        if (stars <= 0) {
          UI.showToast(R.string.GiftAuctionBidInvalid, Toast.LENGTH_SHORT);
          return false;
        }
        if (stars < minBid) {
          UI.showToast(R.string.GiftAuctionBidTooLow, Toast.LENGTH_SHORT);
          return false;
        }
        placeBid(stars);
        return true;
      }, true);
  }

  private void promptRaiseBid () {
    if (state == null || state.state == null || state.state.getConstructor() != TdApi.AuctionStateActive.CONSTRUCTOR) {
      return;
    }
    final TdApi.AuctionStateActive active = (TdApi.AuctionStateActive) state.state;
    if (active.userBid == null) {
      return;
    }
    // The next bid must be at least nextBidStarCount when known, otherwise just
    // greater than the current bid.
    final long currentBid = active.userBid.starCount;
    final long minNext = active.userBid.nextBidStarCount > 0 ? active.userBid.nextBidStarCount : currentBid + 1;
    openInputAlert(
      Lang.getString(R.string.GiftAuctionRaiseBid),
      Lang.getString(R.string.GiftAuctionRaiseHint, Lang.plural(R.string.xStars, currentBid)),
      R.string.GiftAuctionRaiseBid, R.string.Cancel, null,
      (inputView, result) -> {
        long stars = parseStars(result);
        if (stars <= 0) {
          UI.showToast(R.string.GiftAuctionBidInvalid, Toast.LENGTH_SHORT);
          return false;
        }
        if (stars < minNext) {
          UI.showToast(R.string.GiftAuctionRaiseTooLow, Toast.LENGTH_SHORT);
          return false;
        }
        raiseBid(stars);
        return true;
      }, true);
  }

  private static long parseStars (@Nullable String input) {
    if (StringUtils.isEmpty(input)) {
      return 0;
    }
    try {
      return Long.parseLong(input.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private long giftId () {
    return state != null && state.gift != null ? state.gift.id : 0;
  }

  private void placeBid (long starCount) {
    final long giftId = giftId();
    if (giftId == 0) {
      return;
    }
    // The bid receiver is the current user (the gift goes to self). userId is the
    // recipient: pass our own user id. No gift text and a public bid by default.
    final long userId = tdlib.myUserId();
    tdlib.send(new TdApi.PlaceGiftAuctionBid(giftId, starCount, userId, null, false), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        // On insufficient balance, offer an in-app Stars top-up; otherwise show the error.
        if (!tdlib.ui().showStarsBalanceLowPrompt(this, error, starCount)) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_LONG);
        }
        return;
      }
      UI.showToast(R.string.GiftAuctionBidPlaced, Toast.LENGTH_SHORT);
      requestState();
    }));
  }

  private void raiseBid (long starCount) {
    final long giftId = giftId();
    if (giftId == 0) {
      return;
    }
    tdlib.send(new TdApi.IncreaseGiftAuctionBid(giftId, starCount), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        // On insufficient balance, offer an in-app Stars top-up; otherwise show the error.
        if (!tdlib.ui().showStarsBalanceLowPrompt(this, error, starCount)) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_LONG);
        }
        return;
      }
      UI.showToast(R.string.GiftAuctionBidRaised, Toast.LENGTH_SHORT);
      requestState();
    }));
  }

  private void openFragment () {
    if (state == null || state.state == null || state.state.getConstructor() != TdApi.AuctionStateFinished.CONSTRUCTOR) {
      return;
    }
    String url = ((TdApi.AuctionStateFinished) state.state).fragmentUrl;
    if (!StringUtils.isEmpty(url)) {
      tdlib.ui().openUrl(this, url, null);
    }
  }

  // Live updates

  @Override
  public void onGiftAuctionStateChanged (TdApi.GiftAuctionState newState) {
    if (newState == null || isDestroyed()) {
      return;
    }
    if (matchesThisAuction(newState)) {
      applyState(newState);
    }
  }

  @Override
  public void onActiveGiftAuctionsChanged (TdApi.GiftAuctionState[] states) {
    if (states == null || isDestroyed()) {
      return;
    }
    for (TdApi.GiftAuctionState s : states) {
      if (matchesThisAuction(s)) {
        applyState(s);
        return;
      }
    }
  }

  private boolean matchesThisAuction (@Nullable TdApi.GiftAuctionState s) {
    final String auctionId = getArgumentsStrict().auctionId;
    if (s == null || s.gift == null || s.gift.auctionInfo == null || StringUtils.isEmpty(auctionId)) {
      return false;
    }
    return auctionId.equals(s.gift.auctionInfo.id);
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.removeGiftAuctionListener(this);
    if (headerView != null) {
      headerView.performDestroy();
    }
  }

  // Static openers

  public static void open (ViewController<?> context, Tdlib tdlib, TdApi.GiftAuctionState state) {
    if (context == null || tdlib == null || state == null || state.gift == null || state.gift.auctionInfo == null) {
      return;
    }
    GiftAuctionController c = new GiftAuctionController(context.context(), tdlib);
    c.setArguments(new Args(state.gift.auctionInfo.id, state));
    context.context().navigation().navigateTo(c);
  }

  /**
   * Resolves an auction by id ({@link TdApi.InternalLinkTypeGiftAuction}) and
   * opens the detail screen.
   */
  public static void openById (ViewController<?> context, Tdlib tdlib, String auctionId) {
    if (context == null || tdlib == null || StringUtils.isEmpty(auctionId)) {
      return;
    }
    tdlib.send(new TdApi.GetGiftAuctionState(auctionId), (result, error) -> context.runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (result == null) {
        UI.showToast(R.string.GiftAuctionNotFound, Toast.LENGTH_SHORT);
        return;
      }
      GiftAuctionController c = new GiftAuctionController(context.context(), tdlib);
      c.setArguments(new Args(auctionId, result));
      context.context().navigation().navigateTo(c);
    }));
  }
}
