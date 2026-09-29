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
 * File created for the gift-crafting picker (Slice 8)
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.GiftView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.core.StringUtils;

/**
 * Multi-select picker for crafting a new upgraded gift from several of the
 * current user's own upgraded gifts of the same regular-gift family
 * ({@link TdApi.GetGiftsForCrafting} / {@link TdApi.CraftGift}).
 *
 * The source gifts are <b>permanently lost</b>; the crafted gift keeps the
 * number of the <b>primary</b> (first) gift. The primary defaults to the lowest
 * {@code uniqueGiftNumber} among the selection and can be changed via a
 * long-press "Set as primary" action. At least 2 gifts must be selected.
 *
 * Layout: a spanned header (selection count + odds/persistence hint), the gift
 * grid, and a spanned footer that doubles as the "Craft" button.
 *
 * Paging is offset-based via {@link TdApi.GiftsForCrafting#nextOffset}.
 */
public class GiftCraftController extends RecyclerViewController<GiftCraftController.Args> {
  private static final int PAGE_LIMIT = 30;
  private static final int SPAN_COUNT = 3;
  private static final int MIN_SELECTION = 2;
  private static final int MAX_SELECTION = 4; // server caps crafting at 4 source gifts

  public static class Args {
    public final long regularGiftId;
    public final @Nullable String title;

    public Args (long regularGiftId, @Nullable String title) {
      this.regularGiftId = regularGiftId;
      this.title = title;
    }
  }

  public GiftCraftController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_giftCraft;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.GiftCraftTitle);
  }

  private static final int VIEW_TYPE_HEADER = 0;
  private static final int VIEW_TYPE_GIFT = 1;
  private static final int VIEW_TYPE_FOOTER = 2;

  private final List<TdApi.ReceivedGift> gifts = new ArrayList<>();
  // Selected receivedGiftIds, in insertion order (used as a stable secondary
  // ordering when the primary can't be derived from gift numbers).
  private final Set<String> selectedIds = new LinkedHashSet<>();
  // The primary gift id (kept number). null -> default to the lowest-numbered
  // selected gift at craft time.
  private @Nullable String primaryId;

  private @Nullable TdApi.AttributeCraftPersistenceProbability[] persistence;

  private GiftsAdapter adapter;
  private @Nullable HeaderInfoView headerView;
  private @Nullable FooterButtonView footerView;

  private String nextOffset = "";
  private boolean isLoading;
  private boolean endReached;
  private boolean crafting;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new GiftsAdapter();

    GridLayoutManager manager = new GridLayoutManager(context, SPAN_COUNT);
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        int type = adapter.getItemViewType(position);
        return (type == VIEW_TYPE_HEADER || type == VIEW_TYPE_FOOTER) ? SPAN_COUNT : 1;
      }
    });
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

  // Paging

  private void loadMore () {
    if (isLoading || endReached || isDestroyed()) {
      return;
    }
    isLoading = true;
    final long regularGiftId = getArgumentsStrict().regularGiftId;
    final String offset = nextOffset;
    tdlib.send(new TdApi.GetGiftsForCrafting(regularGiftId, offset, PAGE_LIMIT), (result, error) -> runOnUiThreadOptional(() -> {
      isLoading = false;
      if (error != null) {
        endReached = true;
        UI.showError(error);
        return;
      }
      onGiftsLoaded(result, offset.isEmpty());
    }));
  }

  private void onGiftsLoaded (TdApi.GiftsForCrafting result, boolean isInitial) {
    if (isDestroyed()) {
      return;
    }
    if (result.attributePersistenceProbabilities != null) {
      persistence = result.attributePersistenceProbabilities;
    }
    final int insertStart = gifts.size();
    if (isInitial) {
      gifts.clear();
    }
    if (result.gifts != null) {
      for (TdApi.ReceivedGift gift : result.gifts) {
        if (gift != null && !StringUtils.isEmpty(gift.receivedGiftId)) {
          gifts.add(gift);
        }
      }
    }
    nextOffset = result.nextOffset;
    endReached = StringUtils.isEmpty(result.nextOffset);
    if (isInitial) {
      adapter.notifyDataSetChanged();
    } else {
      adapter.notifyItemRangeInserted(insertStart + 1, gifts.size() - insertStart);
    }
    updateChromeViews();
  }

  // Selection

  private @Nullable TdApi.ReceivedGift giftById (@Nullable String receivedGiftId) {
    if (StringUtils.isEmpty(receivedGiftId)) {
      return null;
    }
    for (TdApi.ReceivedGift g : gifts) {
      if (receivedGiftId.equals(g.receivedGiftId)) {
        return g;
      }
    }
    return null;
  }

  private void toggleSelection (TdApi.ReceivedGift gift) {
    if (gift == null || StringUtils.isEmpty(gift.receivedGiftId)) {
      return;
    }
    final String id = gift.receivedGiftId;
    if (selectedIds.contains(id)) {
      selectedIds.remove(id);
      if (id.equals(primaryId)) {
        primaryId = null;
      }
    } else {
      if (selectedIds.size() >= MAX_SELECTION) {
        UI.showToast(Lang.getString(R.string.GiftCraftSelectHint), Toast.LENGTH_SHORT);
        return;
      }
      selectedIds.add(id);
    }
    notifyGiftChanged(id);
    updateChromeViews();
  }

  private void setPrimary (TdApi.ReceivedGift gift) {
    if (gift == null || StringUtils.isEmpty(gift.receivedGiftId)) {
      return;
    }
    final String id = gift.receivedGiftId;
    // A gift withdrawn to TON (non-empty giftAddress) can't be the first/primary
    // gift per the CraftGift contract. We can't read giftAddress from ReceivedGift
    // here, but GetGiftsForCrafting only returns eligible gifts, so any returned
    // gift is a valid primary. Selecting it also selects it.
    if (!selectedIds.contains(id)) {
      if (selectedIds.size() >= MAX_SELECTION) {
        UI.showToast(Lang.getString(R.string.GiftCraftSelectHint), Toast.LENGTH_SHORT);
        return;
      }
      selectedIds.add(id);
    }
    primaryId = id;
    adapter.notifyDataSetChanged();
    updateChromeViews();
    UI.showToast(R.string.GiftCraftPrimarySet, Toast.LENGTH_SHORT);
  }

  // Resolves the effective primary id: the explicit one if still selected,
  // otherwise the selected gift with the lowest uniqueGiftNumber (number 0 =
  // "unassigned" is treated as the largest so assigned numbers win).
  private @Nullable String effectivePrimaryId () {
    if (primaryId != null && selectedIds.contains(primaryId)) {
      return primaryId;
    }
    String best = null;
    long bestNumber = Long.MAX_VALUE;
    for (String id : selectedIds) {
      TdApi.ReceivedGift g = giftById(id);
      if (g == null) {
        continue;
      }
      long number = g.uniqueGiftNumber > 0 ? g.uniqueGiftNumber : Long.MAX_VALUE - 1;
      if (best == null || number < bestNumber) {
        best = id;
        bestNumber = number;
      }
    }
    return best;
  }

  private void notifyGiftChanged (String receivedGiftId) {
    for (int i = 0; i < gifts.size(); i++) {
      if (receivedGiftId.equals(gifts.get(i).receivedGiftId)) {
        adapter.notifyItemChanged(i + 1 /* header */);
        return;
      }
    }
  }

  private void updateChromeViews () {
    if (headerView != null) {
      headerView.update();
    }
    if (footerView != null) {
      footerView.update();
    }
  }

  // Odds / persistence hint. Mapping the per-mille arrays to specific backdrop vs
  // symbol attributes is ambiguous from the API (two probability objects per
  // count, not labelled), so we surface a generic, honest explanatory line rather
  // than fabricating precise per-attribute percentages.
  private CharSequence buildHint () {
    final int count = selectedIds.size();
    StringBuilder sb = new StringBuilder();
    if (count < MIN_SELECTION) {
      sb.append(Lang.getString(R.string.GiftCraftSelectHint));
    } else {
      sb.append(Lang.getString(R.string.GiftCraftOddsHint));
      sb.append("\n").append(Lang.getString(R.string.GiftCraftPrimaryHint));
    }
    return sb.toString();
  }

  // Craft

  private void confirmCraft () {
    if (crafting || isDestroyed()) {
      return;
    }
    final int count = selectedIds.size();
    if (count < MIN_SELECTION) {
      UI.showToast(Lang.getString(R.string.GiftCraftSelectHint), Toast.LENGTH_SHORT);
      return;
    }
    showOptions(Lang.getString(R.string.GiftCraftConfirm, count),
      new int[] {R.id.btn_giftCraft, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.GiftCraftConfirmButton), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_premium_star_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftCraft) {
          performCraft();
        }
        return true;
      });
  }

  private String[] buildOrderedIds () {
    // Put the primary first (its number is kept), then the rest in selection order.
    final String primary = effectivePrimaryId();
    final ArrayList<String> ordered = new ArrayList<>(selectedIds.size());
    if (primary != null) {
      ordered.add(primary);
    }
    for (String id : selectedIds) {
      if (!id.equals(primary)) {
        ordered.add(id);
      }
    }
    return ordered.toArray(new String[0]);
  }

  private void performCraft () {
    if (crafting) {
      return;
    }
    final String[] orderedIds = buildOrderedIds();
    if (orderedIds.length < MIN_SELECTION) {
      return;
    }
    crafting = true;
    updateChromeViews();
    tdlib.send(new TdApi.CraftGift(orderedIds), (result, error) -> runOnUiThreadOptional(() -> {
      crafting = false;
      updateChromeViews();
      if (error != null) {
        UI.showError(error);
        return;
      }
      handleCraftResult(result);
    }));
  }

  private void handleCraftResult (TdApi.CraftGiftResult result) {
    if (result == null) {
      return;
    }
    switch (result.getConstructor()) {
      case TdApi.CraftGiftResultSuccess.CONSTRUCTOR: {
        TdApi.CraftGiftResultSuccess success = (TdApi.CraftGiftResultSuccess) result;
        UI.showToast(R.string.GiftCraftDone, Toast.LENGTH_SHORT);
        // Open the resulting collectible, then pop this picker so back returns to
        // the originating screen rather than the (now consumed) selection.
        if (success.gift != null) {
          UpgradedGiftController.Args args = new UpgradedGiftController.Args(
            success.gift, success.receivedGiftId, false, 0, 0, 0);
          UpgradedGiftController.open(this, tdlib, args);
        }
        navigateBack();
        break;
      }
      case TdApi.CraftGiftResultTooEarly.CONSTRUCTOR: {
        int retryAfter = ((TdApi.CraftGiftResultTooEarly) result).retryAfter;
        UI.showToast(Lang.getString(R.string.GiftCraftTooEarly, Lang.getDurationFull(Math.max(0, retryAfter))), Toast.LENGTH_LONG);
        break;
      }
      case TdApi.CraftGiftResultInvalidGift.CONSTRUCTOR: {
        UI.showToast(R.string.GiftCraftInvalidGift, Toast.LENGTH_LONG);
        break;
      }
      case TdApi.CraftGiftResultFail.CONSTRUCTOR:
      default: {
        UI.showToast(R.string.GiftCraftFailed, Toast.LENGTH_LONG);
        break;
      }
    }
  }

  // Adapter

  private class GiftHolder extends RecyclerView.ViewHolder {
    public GiftHolder (View itemView) {
      super(itemView);
    }
  }

  private class GiftsAdapter extends RecyclerView.Adapter<GiftHolder> {
    @Override
    public int getItemViewType (int position) {
      if (position == 0) {
        return VIEW_TYPE_HEADER;
      }
      if (position == gifts.size() + 1) {
        return VIEW_TYPE_FOOTER;
      }
      return VIEW_TYPE_GIFT;
    }

    @NonNull
    @Override
    public GiftHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == VIEW_TYPE_HEADER) {
        HeaderInfoView view = new HeaderInfoView(context());
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerView = view;
        view.update();
        return new GiftHolder(view);
      }
      if (viewType == VIEW_TYPE_FOOTER) {
        FooterButtonView view = new FooterButtonView(context());
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        footerView = view;
        view.update();
        return new GiftHolder(view);
      }
      GiftView view = new GiftView(context());
      view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(112)));
      view.setOnClickListener(v -> {
        TdApi.ReceivedGift gift = ((GiftView) v).getGift();
        if (gift != null) {
          toggleSelection(gift);
        }
      });
      view.setOnLongClickListener(v -> {
        TdApi.ReceivedGift gift = ((GiftView) v).getGift();
        if (gift != null) {
          showGiftMenu(gift);
          return true;
        }
        return false;
      });
      return new GiftHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull GiftHolder holder, int position) {
      int type = getItemViewType(position);
      if (type == VIEW_TYPE_HEADER) {
        ((HeaderInfoView) holder.itemView).update();
        return;
      }
      if (type == VIEW_TYPE_FOOTER) {
        ((FooterButtonView) holder.itemView).update();
        return;
      }
      TdApi.ReceivedGift gift = gifts.get(position - 1);
      GiftView view = (GiftView) holder.itemView;
      view.setGift(tdlib, gift);
      final boolean checked = selectedIds.contains(gift.receivedGiftId);
      final boolean isPrimary = checked && gift.receivedGiftId.equals(effectivePrimaryId());
      view.setCheckedState(checked, isPrimary);
    }

    @Override
    public void onViewAttachedToWindow (@NonNull GiftHolder holder) {
      if (holder.itemView instanceof AttachDelegate) {
        ((AttachDelegate) holder.itemView).attach();
      }
    }

    @Override
    public void onViewDetachedFromWindow (@NonNull GiftHolder holder) {
      if (holder.itemView instanceof AttachDelegate) {
        ((AttachDelegate) holder.itemView).detach();
      }
    }

    @Override
    public void onViewRecycled (@NonNull GiftHolder holder) {
      if (holder.itemView instanceof GiftView) {
        ((GiftView) holder.itemView).setGift(tdlib, null);
      }
    }

    @Override
    public int getItemCount () {
      // header + gifts + footer
      return gifts.size() + 2;
    }
  }

  private void showGiftMenu (TdApi.ReceivedGift gift) {
    if (gift == null || StringUtils.isEmpty(gift.receivedGiftId)) {
      return;
    }
    showOptions(null,
      new int[] {R.id.btn_giftCraftSetPrimary, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.GiftCraftSetPrimary), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_premium_star_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftCraftSetPrimary) {
          setPrimary(gift);
        }
        return true;
      });
  }

  // Spanned header: selection count + odds/persistence hint.
  private class HeaderInfoView extends LinearLayout {
    private final TextView countView;
    private final TextView hintView;

    public HeaderInfoView (Context context) {
      super(context);
      setOrientation(VERTICAL);
      int pad = Screen.dp(16);
      setPadding(pad, Screen.dp(12), pad, Screen.dp(4));

      countView = new TextView(context);
      countView.setTypeface(Typeface.DEFAULT_BOLD);
      countView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      addView(countView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

      hintView = new TextView(context);
      hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      lp.topMargin = Screen.dp(4);
      addView(hintView, lp);
    }

    public void update () {
      final int count = selectedIds.size();
      countView.setText(Lang.plural(R.string.GiftCraftSelected, count));
      countView.setTextColor(Theme.getColor(ColorId.text));
      hintView.setText(buildHint());
      hintView.setTextColor(Theme.getColor(ColorId.textLight));
    }
  }

  // Spanned footer that doubles as the "Craft" button. Disabled (dimmed) until
  // the minimum selection is reached or while a craft is in flight.
  private class FooterButtonView extends FrameLayout {
    private final TextView button;

    public FooterButtonView (Context context) {
      super(context);
      int padH = Screen.dp(16);
      setPadding(padH, Screen.dp(8), padH, Screen.dp(16));

      button = new TextView(context);
      button.setGravity(Gravity.CENTER);
      button.setTypeface(Typeface.DEFAULT_BOLD);
      button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      button.setAllCaps(true);
      int padV = Screen.dp(13);
      button.setPadding(0, padV, 0, padV);
      button.setOnClickListener(v -> {
        if (isEnabledForCraft()) {
          confirmCraft();
        }
      });
      addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
      update();
    }

    private boolean isEnabledForCraft () {
      return !crafting && selectedIds.size() >= MIN_SELECTION;
    }

    public void update () {
      final boolean enabled = isEnabledForCraft();
      button.setText(Lang.getString(R.string.GiftCraftButton));
      android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
      bg.setCornerRadius(Screen.dp(8));
      bg.setColor(Theme.getColor(enabled ? ColorId.fillingPositive : ColorId.filling));
      button.setBackground(bg);
      button.setTextColor(enabled ? 0xFFFFFFFF : Theme.getColor(ColorId.textLight));
      button.setAlpha(enabled ? 1f : 0.7f);
    }
  }

  // Static opener

  public static void open (ViewController<?> context, Tdlib tdlib, long regularGiftId, @Nullable String title) {
    if (context == null || tdlib == null || regularGiftId == 0) {
      return;
    }
    GiftCraftController c = new GiftCraftController(context.context(), tdlib);
    c.setArguments(new Args(regularGiftId, title));
    context.context().navigation().navigateTo(c);
  }
}
