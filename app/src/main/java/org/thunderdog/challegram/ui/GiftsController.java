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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
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
    public int initialCollectionId; // 0 = "All"; non-zero pre-filters to a collection (deep link)

    public Args (TdApi.MessageSender ownerId, boolean isSelf) {
      this.ownerId = ownerId;
      this.isSelf = isSelf;
    }

    public Args setInitialCollectionId (int collectionId) {
      this.initialCollectionId = collectionId;
      return this;
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

  private static final int VIEW_TYPE_HEADER = 0;
  private static final int VIEW_TYPE_GIFT = 1;

  private final List<TdApi.ReceivedGift> gifts = new ArrayList<>();
  private GiftsAdapter adapter;

  private String nextOffset = "";
  private boolean isLoading;
  private boolean endReached;

  // Collections state
  private final List<TdApi.GiftCollection> collections = new ArrayList<>();
  private int selectedCollectionId; // 0 = "All"
  private boolean collectionsLoaded;
  @Nullable private CollectionStripView stripView;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    selectedCollectionId = getArgumentsStrict().initialCollectionId;
    adapter = new GiftsAdapter();

    GridLayoutManager manager = new GridLayoutManager(context, SPAN_COUNT);
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return adapter.getItemViewType(position) == VIEW_TYPE_HEADER ? SPAN_COUNT : 1;
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

    loadCollections();
    loadMore();
  }

  private boolean hasHeader () {
    // Show the collections strip for owners that have at least one collection,
    // or always for the self-owner (so the "+ New" affordance is reachable).
    return getArgumentsStrict().isSelf || !collections.isEmpty();
  }

  private void loadCollections () {
    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    tdlib.send(new TdApi.GetGiftCollections(ownerId), (result, error) -> runOnUiThreadOptional(() -> {
      collectionsLoaded = true;
      if (error != null) {
        // Non-fatal: just show the unfiltered grid without a strip.
        return;
      }
      collections.clear();
      if (result != null && result.collections != null) {
        for (TdApi.GiftCollection c : result.collections) {
          collections.add(c);
        }
      }
      // If the requested initial collection no longer exists, fall back to "All".
      if (selectedCollectionId != 0 && indexOfCollection(selectedCollectionId) == -1) {
        selectedCollectionId = 0;
      }
      rebuildStrip();
      // The header occupies position 0; refresh the whole list so it appears.
      adapter.notifyDataSetChanged();
    }));
  }

  private int indexOfCollection (int collectionId) {
    for (int i = 0; i < collections.size(); i++) {
      if (collections.get(i).id == collectionId) {
        return i;
      }
    }
    return -1;
  }

  @Nullable
  private TdApi.GiftCollection selectedCollection () {
    int index = indexOfCollection(selectedCollectionId);
    return index != -1 ? collections.get(index) : null;
  }

  private void rebuildStrip () {
    if (stripView != null) {
      stripView.build(collections, selectedCollectionId, getArgumentsStrict().isSelf);
    }
  }

  // Switches the active collection filter and re-queries the grid from scratch.
  private void selectCollection (int collectionId) {
    if (selectedCollectionId == collectionId) {
      return;
    }
    selectedCollectionId = collectionId;
    rebuildStrip();
    reloadGifts();
  }

  private void reloadGifts () {
    gifts.clear();
    nextOffset = "";
    endReached = false;
    isLoading = false;
    adapter.notifyDataSetChanged();
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
    final int collectionId = selectedCollectionId;
    tdlib.send(new TdApi.GetReceivedGifts(
      null, ownerId, collectionId,
      false, false, false, false, false, false, false, false, false,
      offset, PAGE_LIMIT
    ), (result, error) -> runOnUiThreadOptional(() -> {
      if (collectionId != selectedCollectionId) {
        // The user switched collections while this page was in flight; drop it.
        return;
      }
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
      adapter.notifyItemRangeInserted(insertStart + headerOffset(), gifts.size() - insertStart);
    }
  }

  // Number of non-gift items rendered before the gift grid (the collections strip).
  private int headerOffset () {
    return hasHeader() ? 1 : 0;
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

      // Collections (Slice 7): add the gift to a collection, or - when viewing
      // within a collection - remove it from that collection.
      if (!collections.isEmpty()) {
        ids.add(R.id.btn_giftCollectionAddTo);
        titles.add(Lang.getString(R.string.GiftCollectionAddTo));
        icons.add(R.drawable.baseline_create_new_folder_24);
      }
      if (selectedCollectionId != 0) {
        ids.add(R.id.btn_giftCollectionRemoveFrom);
        titles.add(Lang.getString(R.string.GiftCollectionRemoveFrom));
        icons.add(R.drawable.baseline_folder_24);
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
      } else if (id == R.id.btn_giftCollectionAddTo) {
        pickCollectionForGift(gift);
      } else if (id == R.id.btn_giftCollectionRemoveFrom) {
        removeGiftFromCollection(gift, selectedCollectionId);
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
        adapter.notifyItemChanged(index + headerOffset());
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
        adapter.notifyItemChanged(index + headerOffset());
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
        adapter.notifyItemRemoved(index + headerOffset());
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
        // On insufficient balance, offer an in-app Stars top-up; otherwise show the error.
        if (!tdlib.ui().showStarsBalanceLowPrompt(this, error, starCount)) {
          UI.showError(error);
        }
        return;
      }
      UI.showToast(R.string.UpgradeGiftDone, android.widget.Toast.LENGTH_SHORT);
      // Remove the old (regular) gift entry; the upgraded gift lives under a new id.
      int index = indexOfGift(id);
      if (index != -1) {
        gifts.remove(index);
        adapter.notifyItemRemoved(index + headerOffset());
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

  // Collection management (Slice 7). All mutating ops are gated on isSelf at the
  // call sites (the strip "+ New" and per-gift collection actions are only shown
  // for the self-owner). The server additionally enforces ownership.

  private void showManageCollectionsMenu () {
    final boolean isSelf = getArgumentsStrict().isSelf;
    if (!isSelf || isDestroyed()) {
      return;
    }
    final ArrayList<Integer> ids = new ArrayList<>();
    final ArrayList<String> titles = new ArrayList<>();
    final ArrayList<Integer> icons = new ArrayList<>();

    ids.add(R.id.btn_giftCollectionCreate);
    titles.add(Lang.getString(R.string.GiftCollectionCreate));
    icons.add(R.drawable.baseline_create_new_folder_24);

    final TdApi.GiftCollection selected = selectedCollection();
    if (selected != null) {
      ids.add(R.id.btn_giftCollectionRename);
      titles.add(Lang.getString(R.string.GiftCollectionRename));
      icons.add(R.drawable.baseline_edit_24);

      ids.add(R.id.btn_giftCollectionDelete);
      titles.add(Lang.getString(R.string.GiftCollectionDelete));
      icons.add(R.drawable.baseline_delete_24);
    }

    int[] idArray = new int[ids.size()];
    int[] iconArray = new int[icons.size()];
    int[] colors = new int[ids.size()];
    for (int i = 0; i < ids.size(); i++) {
      idArray[i] = ids.get(i);
      iconArray[i] = icons.get(i);
      colors[i] = idArray[i] == R.id.btn_giftCollectionDelete ? OptionColor.RED : OptionColor.NORMAL;
    }

    showOptions(Lang.getString(R.string.GiftCollectionManage), idArray, titles.toArray(new String[0]), colors, iconArray, (itemView, id) -> {
      if (id == R.id.btn_giftCollectionCreate) {
        promptCreateCollection();
      } else if (id == R.id.btn_giftCollectionRename && selected != null) {
        promptRenameCollection(selected);
      } else if (id == R.id.btn_giftCollectionDelete && selected != null) {
        confirmDeleteCollection(selected);
      }
      return true;
    });
  }

  private void promptCreateCollection () {
    openInputAlert(Lang.getString(R.string.GiftCollectionNew), Lang.getString(R.string.GiftCollectionNameHint),
      R.string.GiftCollectionCreate, R.string.Cancel, null, (inputView, result) -> {
        final String name = result != null ? result.trim() : "";
        if (name.isEmpty() || name.length() > 12) {
          UI.showToast(R.string.GiftCollectionNameInvalid, android.widget.Toast.LENGTH_SHORT);
          return false;
        }
        createCollection(name);
        return true;
      }, true);
  }

  private void createCollection (String name) {
    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    // v1: create an empty collection. Multi-select seeding isn't implemented yet.
    tdlib.send(new TdApi.CreateGiftCollection(ownerId, name, new String[0]), (collection, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (collection != null) {
        collections.add(collection);
        selectedCollectionId = collection.id;
        rebuildStrip();
        reloadGifts();
      }
      UI.showToast(R.string.GiftCollectionCreated, android.widget.Toast.LENGTH_SHORT);
    }));
  }

  private void promptRenameCollection (TdApi.GiftCollection collection) {
    openInputAlert(Lang.getString(R.string.GiftCollectionRename), Lang.getString(R.string.GiftCollectionNameHint),
      R.string.GiftCollectionRename, R.string.Cancel, collection.name, (inputView, result) -> {
        final String name = result != null ? result.trim() : "";
        if (name.isEmpty() || name.length() > 12) {
          UI.showToast(R.string.GiftCollectionNameInvalid, android.widget.Toast.LENGTH_SHORT);
          return false;
        }
        renameCollection(collection.id, name);
        return true;
      }, true);
  }

  private void renameCollection (int collectionId, String name) {
    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    tdlib.send(new TdApi.SetGiftCollectionName(ownerId, collectionId, name), (collection, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      int index = indexOfCollection(collectionId);
      if (index != -1 && collection != null) {
        collections.set(index, collection);
        rebuildStrip();
      }
      UI.showToast(R.string.GiftCollectionRenamed, android.widget.Toast.LENGTH_SHORT);
    }));
  }

  private void confirmDeleteCollection (TdApi.GiftCollection collection) {
    showOptions(Lang.getString(R.string.GiftCollectionDeleteConfirm, collection.name),
      new int[] {R.id.btn_giftCollectionDelete, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.GiftCollectionDelete), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_giftCollectionDelete) {
          deleteCollection(collection.id);
        }
        return true;
      });
  }

  private void deleteCollection (int collectionId) {
    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    tdlib.send(new TdApi.DeleteGiftCollection(ownerId, collectionId), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      int index = indexOfCollection(collectionId);
      if (index != -1) {
        collections.remove(index);
      }
      if (selectedCollectionId == collectionId) {
        selectedCollectionId = 0;
      }
      rebuildStrip();
      reloadGifts();
      UI.showToast(R.string.GiftCollectionDeleted, android.widget.Toast.LENGTH_SHORT);
    }));
  }

  // Add/remove a single gift to/from a collection from the per-gift detail sheet.

  private void pickCollectionForGift (TdApi.ReceivedGift gift) {
    if (gift == null || StringUtils.isEmpty(gift.receivedGiftId) || collections.isEmpty()) {
      return;
    }
    final List<TdApi.GiftCollection> snapshot = new ArrayList<>(collections);
    final int[] idArray = new int[snapshot.size()];
    final String[] titles = new String[snapshot.size()];
    final int[] icons = new int[snapshot.size()];
    for (int i = 0; i < snapshot.size(); i++) {
      idArray[i] = R.id.btn_giftCollectionItem;
      titles[i] = snapshot.get(i).name;
      icons[i] = R.drawable.baseline_folder_24;
    }
    // All rows share btn_giftCollectionItem; the per-row collection id is carried
    // as the option view tag (getTagForItem) so the tap maps to the right one.
    final OptionDelegate delegate = new OptionDelegate() {
      @Override
      public boolean onOptionItemPressed (View optionItemView, int id) {
        Object tag = optionItemView.getTag();
        if (tag instanceof Integer) {
          addGiftToCollection(gift, (Integer) tag);
        }
        return true;
      }

      @Override
      public Object getTagForItem (int position) {
        return position >= 0 && position < snapshot.size() ? snapshot.get(position).id : null;
      }
    };
    showOptions(Lang.getString(R.string.GiftCollectionPickTitle), idArray, titles, null, icons, delegate);
  }

  private void addGiftToCollection (TdApi.ReceivedGift gift, int collectionId) {
    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    tdlib.send(new TdApi.AddGiftCollectionGifts(ownerId, collectionId, new String[] {gift.receivedGiftId}), (collection, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      int index = indexOfCollection(collectionId);
      if (index != -1 && collection != null) {
        collections.set(index, collection);
        rebuildStrip();
      }
      UI.showToast(R.string.GiftCollectionGiftAdded, android.widget.Toast.LENGTH_SHORT);
    }));
  }

  private void removeGiftFromCollection (TdApi.ReceivedGift gift, int collectionId) {
    if (gift == null || StringUtils.isEmpty(gift.receivedGiftId) || collectionId == 0) {
      return;
    }
    final TdApi.MessageSender ownerId = getArgumentsStrict().ownerId;
    final String giftId = gift.receivedGiftId;
    tdlib.send(new TdApi.RemoveGiftCollectionGifts(ownerId, collectionId, new String[] {giftId}), (collection, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      int index = indexOfCollection(collectionId);
      if (index != -1 && collection != null) {
        collections.set(index, collection);
        rebuildStrip();
      }
      // If we're viewing this collection, drop the gift from the visible grid.
      if (selectedCollectionId == collectionId) {
        int giftIndex = indexOfGift(giftId);
        if (giftIndex != -1) {
          gifts.remove(giftIndex);
          adapter.notifyItemRemoved(giftIndex + headerOffset());
        }
      }
      UI.showToast(R.string.GiftCollectionGiftRemoved, android.widget.Toast.LENGTH_SHORT);
    }));
  }

  // TODO(slice-7): ReorderGiftCollections / ReorderGiftCollectionGifts are left
  // unwired. Drag-reorder UI is out of scope for this slice; the functions exist
  // and can be hooked behind move-up/move-down actions in a follow-up.

  // Adapter

  private class GiftHolder extends RecyclerView.ViewHolder {
    public GiftHolder (View itemView) {
      super(itemView);
    }
  }

  private class GiftsAdapter extends RecyclerView.Adapter<GiftHolder> {
    @Override
    public int getItemViewType (int position) {
      return (hasHeader() && position == 0) ? VIEW_TYPE_HEADER : VIEW_TYPE_GIFT;
    }

    @NonNull
    @Override
    public GiftHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == VIEW_TYPE_HEADER) {
        CollectionStripView strip = new CollectionStripView(context());
        strip.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stripView = strip;
        strip.build(collections, selectedCollectionId, getArgumentsStrict().isSelf);
        return new GiftHolder(strip);
      }
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
      if (getItemViewType(position) == VIEW_TYPE_HEADER) {
        ((CollectionStripView) holder.itemView).build(collections, selectedCollectionId, getArgumentsStrict().isSelf);
        return;
      }
      ((GiftView) holder.itemView).setGift(tdlib, gifts.get(position - headerOffset()));
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
      return gifts.size() + headerOffset();
    }
  }

  // Lightweight horizontal "Collections" chip strip rendered as the grid header.
  // "All" resets the filter; tapping a chip filters the grid; for the self-owner
  // a trailing "+ New" chip opens the management menu (create/rename/delete).
  private class CollectionStripView extends HorizontalScrollView {
    private final LinearLayout container;

    public CollectionStripView (Context context) {
      super(context);
      setHorizontalScrollBarEnabled(false);
      setFillViewport(false);
      int padH = Screen.dp(8);
      int padV = Screen.dp(8);
      setPadding(padH, padV, padH, padV);
      container = new LinearLayout(context);
      container.setOrientation(LinearLayout.HORIZONTAL);
      addView(container, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void build (List<TdApi.GiftCollection> collections, int selectedId, boolean isSelf) {
      container.removeAllViews();
      // "All" chip
      container.addView(makeChip(Lang.getString(R.string.GiftCollectionAll), selectedId == 0, v -> selectCollection(0)));
      for (TdApi.GiftCollection collection : collections) {
        final int id = collection.id;
        container.addView(makeChip(collection.name, selectedId == id, v -> selectCollection(id)));
      }
      if (isSelf) {
        container.addView(makeChip("+ " + Lang.getString(R.string.GiftCollectionNew), false, v -> showManageCollectionsMenu()));
      }
    }

    private TextView makeChip (CharSequence text, boolean active, View.OnClickListener onClick) {
      TextView chip = new TextView(getContext());
      chip.setText(text);
      chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      chip.setSingleLine(true);
      chip.setGravity(Gravity.CENTER);
      int padH = Screen.dp(14);
      int padV = Screen.dp(7);
      chip.setPadding(padH, padV, padH, padV);
      chip.setTextColor(Theme.getColor(active ? ColorId.textNeutral : ColorId.text));
      // Rounded pill background; tinted when active.
      android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
      bg.setCornerRadius(Screen.dp(16));
      bg.setColor(active ? Theme.getColor(ColorId.fillingPositive) : Theme.getColor(ColorId.filling));
      if (!active) {
        bg.setStroke(Math.max(1, Screen.dp(1)), Theme.getColor(ColorId.separator));
      }
      chip.setBackground(bg);
      chip.setOnClickListener(onClick);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.rightMargin = Screen.dp(8);
      chip.setLayoutParams(lp);
      return chip;
    }
  }
}
