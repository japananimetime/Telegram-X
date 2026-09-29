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
 * File created for the gift auctions list screen (Slice 6)
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
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.GiftView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Lists the gift auctions the current user is taking part in. TDLib has no
 * "get active auctions" function, so the screen seeds itself from the most recent
 * {@link TdApi.UpdateActiveGiftAuctions} snapshot cached on {@link Tdlib} and then
 * stays in sync via live updates ({@link Tdlib.GiftAuctionListener}).
 *
 * Each cell shows the gift sticker and the current bid context (your bid or the
 * minimum bid in Stars). Tapping a cell opens {@link GiftAuctionController}.
 */
public class GiftAuctionsController extends RecyclerViewController<Void> implements Tdlib.GiftAuctionListener {
  private static final int SPAN_COUNT = 3;

  public GiftAuctionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_giftAuctions;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.GiftAuctions);
  }

  private final List<TdApi.GiftAuctionState> auctions = new ArrayList<>();
  private AuctionsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new AuctionsAdapter();

    GridLayoutManager manager = new GridLayoutManager(context, SPAN_COUNT);
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        // The empty-state row spans the full width.
        return auctions.isEmpty() ? SPAN_COUNT : 1;
      }
    });
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);

    seedFromSnapshot(tdlib.activeGiftAuctions());
    tdlib.addGiftAuctionListener(this);
  }

  private void seedFromSnapshot (@Nullable TdApi.GiftAuctionState[] states) {
    auctions.clear();
    if (states != null) {
      for (TdApi.GiftAuctionState s : states) {
        if (isValid(s)) {
          auctions.add(s);
        }
      }
    }
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }

  private static boolean isValid (@Nullable TdApi.GiftAuctionState s) {
    return s != null && s.gift != null && s.gift.auctionInfo != null && !StringUtils.isEmpty(s.gift.auctionInfo.id);
  }

  private static @Nullable String auctionIdOf (@Nullable TdApi.GiftAuctionState s) {
    return isValid(s) ? s.gift.auctionInfo.id : null;
  }

  private int indexOf (String auctionId) {
    if (StringUtils.isEmpty(auctionId)) {
      return -1;
    }
    for (int i = 0; i < auctions.size(); i++) {
      if (auctionId.equals(auctionIdOf(auctions.get(i)))) {
        return i;
      }
    }
    return -1;
  }

  private static @Nullable String bidLabel (TdApi.GiftAuctionState s) {
    if (s == null || s.state == null) {
      return null;
    }
    if (s.state.getConstructor() == TdApi.AuctionStateActive.CONSTRUCTOR) {
      TdApi.AuctionStateActive active = (TdApi.AuctionStateActive) s.state;
      if (active.userBid != null) {
        return Lang.plural(R.string.xStars, active.userBid.starCount);
      }
      return Lang.plural(R.string.xStars, active.minBid);
    }
    return Lang.getString(R.string.GiftAuctionFinished);
  }

  private void onAuctionClick (TdApi.GiftAuctionState s) {
    if (!isValid(s) || isDestroyed()) {
      return;
    }
    GiftAuctionController.open(this, tdlib, s);
  }

  // Live updates

  @Override
  public void onGiftAuctionStateChanged (TdApi.GiftAuctionState state) {
    if (!isValid(state) || isDestroyed()) {
      return;
    }
    int index = indexOf(auctionIdOf(state));
    if (index != -1) {
      auctions.set(index, state);
      if (adapter != null) {
        adapter.notifyItemChanged(index);
      }
    }
    // New auctions are introduced by UpdateActiveGiftAuctions; we don't insert
    // here to avoid showing an auction the user isn't participating in.
  }

  @Override
  public void onActiveGiftAuctionsChanged (TdApi.GiftAuctionState[] states) {
    if (isDestroyed()) {
      return;
    }
    seedFromSnapshot(states);
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.removeGiftAuctionListener(this);
  }

  // Adapter

  private class AuctionHolder extends RecyclerView.ViewHolder {
    public AuctionHolder (View itemView) {
      super(itemView);
    }
  }

  private static final int VIEW_TYPE_AUCTION = 0;
  private static final int VIEW_TYPE_EMPTY = 1;

  private class AuctionsAdapter extends RecyclerView.Adapter<AuctionHolder> {
    @Override
    public int getItemViewType (int position) {
      return auctions.isEmpty() ? VIEW_TYPE_EMPTY : VIEW_TYPE_AUCTION;
    }

    @NonNull
    @Override
    public AuctionHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == VIEW_TYPE_EMPTY) {
        android.widget.TextView emptyView = new android.widget.TextView(context());
        emptyView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(180)));
        emptyView.setGravity(android.view.Gravity.CENTER);
        emptyView.setText(Lang.getString(R.string.GiftAuctionsEmpty));
        emptyView.setTextColor(org.thunderdog.challegram.theme.Theme.textDecentColor());
        addThemeTextDecentColorListener(emptyView);
        return new AuctionHolder(emptyView);
      }
      GiftView view = new GiftView(context());
      view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(112)));
      view.setOnClickListener(v -> {
        TdApi.GiftAuctionState s = ((GiftView) v).getAuctionGift();
        if (s != null) {
          onAuctionClick(s);
        }
      });
      return new AuctionHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull AuctionHolder holder, int position) {
      if (holder.itemView instanceof GiftView && !auctions.isEmpty()) {
        TdApi.GiftAuctionState s = auctions.get(position);
        ((GiftView) holder.itemView).setAuctionGift(tdlib, s, bidLabel(s));
      }
    }

    @Override
    public void onViewAttachedToWindow (@NonNull AuctionHolder holder) {
      if (holder.itemView instanceof AttachDelegate) {
        ((AttachDelegate) holder.itemView).attach();
      }
    }

    @Override
    public void onViewDetachedFromWindow (@NonNull AuctionHolder holder) {
      if (holder.itemView instanceof AttachDelegate) {
        ((AttachDelegate) holder.itemView).detach();
      }
    }

    @Override
    public void onViewRecycled (@NonNull AuctionHolder holder) {
      if (holder.itemView instanceof GiftView) {
        ((GiftView) holder.itemView).setAuctionGift(tdlib, null, null);
      }
    }

    @Override
    public int getItemCount () {
      return auctions.isEmpty() ? 1 : auctions.size();
    }
  }

  // Static opener

  public static void open (ViewController<?> context, Tdlib tdlib) {
    if (context == null || tdlib == null) {
      return;
    }
    GiftAuctionsController c = new GiftAuctionsController(context.context(), tdlib);
    context.context().navigation().navigateTo(c);
  }
}
