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
 * File created for the upgraded-gift detail screen (Slice 4)
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
import org.thunderdog.challegram.data.GiftRarityUtil;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.UpgradedGiftHeaderView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

/**
 * Detail screen for an upgraded (collectible / NFT) gift. Renders a hero card
 * (backdrop gradient + model sticker + symbol accent), the gift's attributes
 * (model / symbol / backdrop rarity), owner, original details and estimated
 * value. When the current user owns the gift (a {@code receivedGiftId} plus the
 * owner flags are supplied via {@link Args}), exposes Transfer / Wear / Export /
 * Drop-details actions; otherwise the screen is read-only.
 */
public class UpgradedGiftController extends RecyclerViewController<UpgradedGiftController.Args> implements View.OnClickListener {
  private static final int CUSTOM_HEADER = 0;

  public static class Args {
    public final TdApi.UpgradedGift gift;
    // Owner context; null/empty receivedGiftId -> read-only.
    public final @Nullable String receivedGiftId;
    public final boolean canBeTransferred;
    public final long transferStarCount;
    public final long dropOriginalDetailsStarCount;
    public final int exportDate;

    public Args (TdApi.UpgradedGift gift) {
      this(gift, null, false, 0, 0, 0);
    }

    public Args (TdApi.UpgradedGift gift, @Nullable String receivedGiftId, boolean canBeTransferred, long transferStarCount, long dropOriginalDetailsStarCount, int exportDate) {
      this.gift = gift;
      this.receivedGiftId = receivedGiftId;
      this.canBeTransferred = canBeTransferred;
      this.transferStarCount = transferStarCount;
      this.dropOriginalDetailsStarCount = dropOriginalDetailsStarCount;
      this.exportDate = exportDate;
    }
  }

  public UpgradedGiftController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_upgradedGift;
  }

  @Override
  public CharSequence getName () {
    TdApi.UpgradedGift gift = getArguments() != null ? getArgumentsStrict().gift : null;
    if (gift != null && !StringUtils.isEmpty(gift.title)) {
      return gift.title;
    }
    return Lang.getString(R.string.UpgradedGift);
  }

  private SettingsAdapter adapter;
  private UpgradedGiftHeaderView headerView;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    final TdApi.UpgradedGift gift = getArgumentsStrict().gift;

    adapter = new SettingsAdapter(this) {
      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        UpgradedGiftHeaderView view = new UpgradedGiftHeaderView(context());
        view.setLayoutParams(new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setGift(tdlib, gift);
        UpgradedGiftController.this.headerView = view;
        return new SettingHolder(view);
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setData(item.getStringValue());
      }
    };

    adapter.setItems(buildItems(gift).toArray(new ListItem[0]), false);
    recyclerView.setAdapter(adapter);
  }

  private List<ListItem> buildItems (TdApi.UpgradedGift gift) {
    final Args args = getArgumentsStrict();
    final List<ListItem> items = new ArrayList<>();

    // Hero header (custom view).
    items.add(new ListItem(ListItem.TYPE_CUSTOM - CUSTOM_HEADER));

    // Attributes: Model / Symbol / Backdrop.
    final List<ListItem> attrs = new ArrayList<>();
    addAttribute(attrs, R.string.UpgradedGiftModel,
      gift.model != null ? gift.model.name : null,
      gift.model != null ? gift.model.rarity : null);
    addAttribute(attrs, R.string.UpgradedGiftSymbol,
      gift.symbol != null ? gift.symbol.name : null,
      gift.symbol != null ? gift.symbol.rarity : null);
    addAttribute(attrs, R.string.UpgradedGiftBackdrop,
      gift.backdrop != null ? gift.backdrop.name : null,
      gift.backdrop != null ? gift.backdrop.rarity : null);
    if (!attrs.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      appendWithSeparators(items, attrs);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    // Owner.
    String ownerName = ownerName(gift);
    if (!StringUtils.isEmpty(ownerName)) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, 0, R.string.UpgradedGiftOwner)
        .setStringValue(ownerName));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    // Original details.
    if (gift.originalDetails != null) {
      String details = buildOriginalDetails(gift.originalDetails);
      if (!StringUtils.isEmpty(details)) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.UpgradedGiftOriginalDetails));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, 0, 0, R.string.UpgradedGiftOriginalDetails)
          .setStringValue(details));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    }

    // Estimated value.
    if (!StringUtils.isEmpty(gift.valueCurrency) && gift.valueAmount > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, 0, R.string.UpgradedGiftValue)
        .setStringValue(Lang.getString(R.string.UpgradedGiftValueAmount, formatAmount(gift.valueAmount), gift.valueCurrency)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    // Owner actions.
    final boolean isOwner = !StringUtils.isEmpty(args.receivedGiftId);
    final List<ListItem> actions = new ArrayList<>();
    if (isOwner) {
      if (args.canBeTransferred) {
        actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftTransfer, R.drawable.baseline_share_arrow_24, R.string.UpgradedGiftTransfer));
      }
      if (gift.colors != null) {
        actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftWear, R.drawable.baseline_palette_24, R.string.UpgradedGiftWear));
      }
      // Resale (Slice 5): list / relist / unlist this gift.
      if (gift.resaleParameters != null) {
        actions.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_giftResaleChangePrice, R.drawable.baseline_star_24, R.string.UpgradedGiftChangePrice)
          .setStringValue(Lang.getString(R.string.UpgradedGiftListedPrice, listedPriceLabel(gift.resaleParameters))));
        actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftResaleUnlist, R.drawable.baseline_remove_circle_24, R.string.UpgradedGiftUnlist));
      } else {
        actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftResaleList, R.drawable.baseline_star_24, R.string.UpgradedGiftSell));
      }
      if (args.exportDate != 0) {
        actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftExport, R.drawable.baseline_open_in_browser_24, R.string.UpgradedGiftExport));
      }
      if (gift.originalDetails != null && args.dropOriginalDetailsStarCount >= 0) {
        actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftDropDetails, R.drawable.baseline_delete_24, R.string.UpgradedGiftDropDetails));
      }
      // Craft / combine (Slice 8): combine several of the user's own upgraded
      // gifts of this family into a new one. Only meaningful when we know the
      // regular-gift family id.
      if (gift.regularGiftId != 0) {
        actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftCraft, R.drawable.baseline_premium_star_24, R.string.GiftCraft));
      }
    } else {
      // Non-owner actions (Slice 5): make a purchase offer on someone else's gift.
      if (gift.canSendPurchaseOffer && gift.ownerId != null && !StringUtils.isEmpty(gift.name)) {
        actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftMakeOffer, R.drawable.baseline_star_24, R.string.UpgradedGiftMakeOffer));
      }
    }
    // Resale market browser (Slice 5): visible to anyone when the gift can be
    // resold, so users can buy other copies of this collectible family.
    if (gift.regularGiftId != 0 && (gift.resaleParameters != null || gift.canSendPurchaseOffer)) {
      actions.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftResaleBrowse, R.drawable.baseline_galaxy_store_24, R.string.GiftResaleBrowse));
    }
    if (!actions.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.UpgradedGiftActions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      appendWithSeparators(items, actions);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    return items;
  }

  private static void appendWithSeparators (List<ListItem> dst, List<ListItem> rows) {
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) {
        dst.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      dst.add(rows.get(i));
    }
  }

  private static void addAttribute (List<ListItem> dst, int labelRes, @Nullable String name, @Nullable TdApi.UpgradedGiftAttributeRarity rarity) {
    if (StringUtils.isEmpty(name)) {
      return;
    }
    String rarityLabel = GiftRarityUtil.rarityLabel(rarity);
    String value = StringUtils.isEmpty(rarityLabel) ? name : Lang.getString(R.string.UpgradedGiftAttribute, name, rarityLabel);
    dst.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, 0, labelRes).setStringValue(value));
  }

  private @Nullable String ownerName (TdApi.UpgradedGift gift) {
    if (gift.ownerId != null) {
      return tdlib.chatTitle(Td.getSenderId(gift.ownerId));
    }
    if (!StringUtils.isEmpty(gift.ownerName)) {
      return gift.ownerName;
    }
    return null;
  }

  private String buildOriginalDetails (TdApi.UpgradedGiftOriginalDetails details) {
    StringBuilder sb = new StringBuilder();
    String sender = details.senderId != null ? tdlib.chatTitle(Td.getSenderId(details.senderId)) : Lang.getString(R.string.GiftFromUnknown);
    String receiver = details.receiverId != null ? tdlib.chatTitle(Td.getSenderId(details.receiverId)) : "";
    sb.append(Lang.getString(R.string.UpgradedGiftOriginalFromTo, sender, receiver));
    sb.append("\n").append(Lang.getString(R.string.format_GiveawayDateTime,
      Lang.dateYearFull(details.date, TimeUnit.SECONDS), Lang.time(details.date, TimeUnit.SECONDS)));
    if (details.text != null && !StringUtils.isEmpty(details.text.text)) {
      sb.append("\n\n").append(details.text.text);
    }
    return sb.toString();
  }

  private static String formatAmount (long amount) {
    // value is in the smallest currency units (cents); show with 2 decimals.
    return GiftRarityUtil.formatTon(amount, 2);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_giftTransfer) {
      startTransfer();
    } else if (id == R.id.btn_giftWear) {
      wearGift();
    } else if (id == R.id.btn_giftExport) {
      exportToTon();
    } else if (id == R.id.btn_giftDropDetails) {
      confirmDropDetails();
    } else if (id == R.id.btn_giftResaleList || id == R.id.btn_giftResaleChangePrice) {
      startSetResalePrice();
    } else if (id == R.id.btn_giftResaleUnlist) {
      confirmUnlist();
    } else if (id == R.id.btn_giftMakeOffer) {
      startMakeOffer();
    } else if (id == R.id.btn_giftResaleBrowse) {
      openResaleMarket();
    } else if (id == R.id.btn_giftCraft) {
      openCraft();
    }
  }

  // Craft / combine (Slice 8)

  private void openCraft () {
    final TdApi.UpgradedGift gift = getArgumentsStrict().gift;
    if (gift.regularGiftId == 0) {
      return;
    }
    GiftCraftController.open(this, tdlib, gift.regularGiftId, gift.title);
  }

  // Resale: list / relist / unlist (SetGiftResalePrice)

  private static String listedPriceLabel (@NonNull TdApi.GiftResaleParameters params) {
    if (params.toncoinOnly || (params.starCount <= 0 && params.toncoinCentCount > 0)) {
      return Lang.getString(R.string.GiftedTonAmount, GiftRarityUtil.formatTon(params.toncoinCentCount, 2));
    }
    return Lang.plural(R.string.xStars, params.starCount);
  }

  private void startSetResalePrice () {
    // Pick the currency first, then the amount. A custom dual-field widget would
    // be over-building the filter UI for this slice.
    showOptions(Lang.getString(R.string.GiftResalePriceTitle),
      new int[] {R.id.btn_giftPriceStars, R.id.btn_giftPriceTon, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.GiftPriceStars), Lang.getString(R.string.GiftPriceTon), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_star_24, R.drawable.baseline_galaxy_store_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftPriceStars) {
          promptResaleAmount(false);
        } else if (optionId == R.id.btn_giftPriceTon) {
          promptResaleAmount(true);
        }
        return true;
      });
  }

  private void promptResaleAmount (boolean ton) {
    openInputAlert(
      Lang.getString(R.string.GiftResalePriceTitle),
      Lang.getString(ton ? R.string.GiftResalePriceHintTon : R.string.GiftResalePriceHintStars),
      R.string.Save, R.string.Cancel, null,
      (inputView, result) -> {
        TdApi.GiftResalePrice price = parsePrice(result, ton);
        if (price == null) {
          UI.showToast(R.string.GiftResalePriceInvalid, Toast.LENGTH_SHORT);
          return false;
        }
        applyResalePrice(price);
        return true;
      }, true);
  }

  private static @Nullable TdApi.GiftResalePrice parsePrice (@Nullable String input, boolean ton) {
    if (StringUtils.isEmpty(input)) {
      return null;
    }
    String clean = input.trim();
    try {
      if (ton) {
        // Accept a decimal TON amount; store as 1/100-of-Toncoin cents.
        double value = Double.parseDouble(clean);
        long cents = Math.round(value * 100.0);
        if (cents <= 0) {
          return null;
        }
        return new TdApi.GiftResalePriceTon(cents);
      } else {
        long stars = Long.parseLong(clean);
        if (stars <= 0) {
          return null;
        }
        return new TdApi.GiftResalePriceStar(stars);
      }
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void applyResalePrice (@Nullable TdApi.GiftResalePrice price) {
    final Args args = getArgumentsStrict();
    tdlib.send(new TdApi.SetGiftResalePrice(args.receivedGiftId, price), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      UI.showToast(price == null ? R.string.UpgradedGiftUnlisted : R.string.UpgradedGiftListed_done, Toast.LENGTH_SHORT);
      navigateBack();
    }));
  }

  private void confirmUnlist () {
    showOptions(Lang.getString(R.string.UpgradedGiftUnlist),
      new int[] {R.id.btn_giftResaleUnlist, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.UpgradedGiftUnlist), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftResaleUnlist) {
          applyResalePrice(null);
        }
        return true;
      });
  }

  // Make a purchase offer (SendGiftPurchaseOffer)

  private void startMakeOffer () {
    showOptions(Lang.getString(R.string.UpgradedGiftMakeOffer),
      new int[] {R.id.btn_giftPriceStars, R.id.btn_giftPriceTon, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.GiftPriceStars), Lang.getString(R.string.GiftPriceTon), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_star_24, R.drawable.baseline_galaxy_store_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftPriceStars) {
          promptOfferAmount(false);
        } else if (optionId == R.id.btn_giftPriceTon) {
          promptOfferAmount(true);
        }
        return true;
      });
  }

  private void promptOfferAmount (boolean ton) {
    openInputAlert(
      Lang.getString(R.string.UpgradedGiftMakeOffer),
      Lang.getString(ton ? R.string.GiftResalePriceHintTon : R.string.GiftResalePriceHintStars),
      R.string.Continue, R.string.Cancel, null,
      (inputView, result) -> {
        TdApi.GiftResalePrice price = parsePrice(result, ton);
        if (price == null) {
          UI.showToast(R.string.GiftResalePriceInvalid, Toast.LENGTH_SHORT);
          return false;
        }
        promptOfferDuration(price);
        return true;
      }, true);
  }

  // Lets the user pick how long the purchase offer stays open before sending it.
  private void promptOfferDuration (@NonNull TdApi.GiftResalePrice price) {
    final int[] durations = {86400, 259200, 604800, 2592000};
    final String[] labels = {
      Lang.getString(R.string.GiftOfferDuration1Day),
      Lang.getString(R.string.GiftOfferDuration3Days),
      Lang.getString(R.string.GiftOfferDuration1Week),
      Lang.getString(R.string.GiftOfferDuration1Month)
    };
    final int[] ids = new int[durations.length];
    final int[] icons = new int[durations.length];
    for (int i = 0; i < durations.length; i++) {
      ids[i] = R.id.btn_giftOfferDurationItem;
      icons[i] = R.drawable.baseline_watch_later_24;
    }
    final OptionDelegate delegate = new OptionDelegate() {
      @Override
      public boolean onOptionItemPressed (View optionItemView, int id) {
        Object tag = optionItemView.getTag();
        if (tag instanceof Integer) {
          sendOffer(price, (Integer) tag);
        }
        return true;
      }

      @Override
      public Object getTagForItem (int position) {
        return position >= 0 && position < durations.length ? durations[position] : null;
      }
    };
    showOptions(Lang.getString(R.string.GiftOfferDurationTitle), ids, labels, null, icons, delegate);
  }

  private void sendOffer (@NonNull TdApi.GiftResalePrice price, int durationSeconds) {
    final TdApi.UpgradedGift gift = getArgumentsStrict().gift;
    if (gift.ownerId == null || StringUtils.isEmpty(gift.name)) {
      return;
    }
    // paidMessageStarCount should be the recipient's outgoingPaidMessageStarCount;
    // for the common (user, no paid messages) case 0 is correct. A full fetch of
    // userFullInfo to honor paid DMs is out of scope here.
    tdlib.send(new TdApi.SendGiftPurchaseOffer(gift.ownerId, gift.name, price, durationSeconds, 0), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      UI.showToast(R.string.UpgradedGiftOfferSent, Toast.LENGTH_SHORT);
      navigateBack();
    }));
  }

  // Resale market browser

  private void openResaleMarket () {
    final TdApi.UpgradedGift gift = getArgumentsStrict().gift;
    if (gift.regularGiftId == 0) {
      return;
    }
    GiftResaleController.open(this, tdlib, gift.regularGiftId, gift.title);
  }

  // Transfer

  private void startTransfer () {
    // Username-based picker fallback. A full people-picker (ShareController /
    // ContactsController) could be wired here later; the username entry path is
    // a complete, working flow on its own.
    openInputAlert(
      Lang.getString(R.string.UpgradedGiftTransferTitle),
      Lang.getString(R.string.UpgradedGiftTransferHint),
      R.string.Continue, R.string.Cancel, null,
      (inputView, result) -> {
        resolveAndConfirmTransfer(result);
        return true;
      }, true);
  }

  private void resolveAndConfirmTransfer (String username) {
    if (StringUtils.isEmpty(username)) {
      return;
    }
    String clean = username.trim();
    if (clean.startsWith("@")) {
      clean = clean.substring(1);
    }
    if (StringUtils.isEmpty(clean)) {
      return;
    }
    tdlib.send(new TdApi.SearchPublicChat(clean), (chat, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      TdApi.User user = tdlib.chatUser(chat);
      if (user == null) {
        UI.showToast(R.string.UpgradedGiftTransferNoUser, Toast.LENGTH_SHORT);
        return;
      }
      confirmTransfer(user);
    }));
  }

  private void confirmTransfer (TdApi.User user) {
    final Args args = getArgumentsStrict();
    final long userId = user.id;
    final String name = tdlib.cache().userName(userId);
    CharSequence message;
    if (args.transferStarCount > 0) {
      message = Lang.getString(R.string.UpgradedGiftTransferConfirmStars, name, Lang.plural(R.string.xStars, args.transferStarCount));
    } else {
      message = Lang.getString(R.string.UpgradedGiftTransferConfirm, name);
    }
    showOptions(message,
      new int[] {R.id.btn_giftTransfer, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.UpgradedGiftTransfer), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_share_arrow_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftTransfer) {
          performTransfer(userId);
        }
        return true;
      });
  }

  private void performTransfer (long userId) {
    final Args args = getArgumentsStrict();
    tdlib.send(new TdApi.TransferGift(null, args.receivedGiftId, new TdApi.MessageSenderUser(userId), args.transferStarCount),
      (ok, error) -> runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showError(error);
          return;
        }
        UI.showToast(R.string.UpgradedGiftTransferred, Toast.LENGTH_SHORT);
        navigateBack();
      }));
  }

  // Wear (set color scheme)

  private void wearGift () {
    final TdApi.UpgradedGift gift = getArgumentsStrict().gift;
    if (gift.colors == null) {
      return;
    }
    tdlib.send(new TdApi.SetUpgradedGiftColors(gift.colors.id), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      UI.showToast(R.string.UpgradedGiftWearDone, Toast.LENGTH_SHORT);
    }));
  }

  // Export to TON
  // Withdrawing the gift to the TON blockchain (as an NFT on Fragment) requires the
  // user's 2-step verification password: fetch the password state, confirm the
  // password via PasswordController, then GetUpgradedGiftWithdrawalUrl and open it.
  private void exportToTon () {
    final Args args = getArgumentsStrict();
    if (StringUtils.isEmpty(args.receivedGiftId) || isDestroyed()) {
      return;
    }
    tdlib.send(new TdApi.GetPasswordState(), (state, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (state == null || !state.hasPassword) {
        // Withdrawal needs 2-step verification enabled on the account.
        UI.showToast(R.string.UpgradedGiftExportNeeds2FA, Toast.LENGTH_LONG);
        return;
      }
      PasswordController controller = new PasswordController(context(), tdlib);
      controller.setArguments(new PasswordController.Args(PasswordController.MODE_CONFIRM, state)
        .setSuccessListener(password -> requestWithdrawalUrl(args.receivedGiftId, password)));
      navigateTo(controller);
    }));
  }

  private void requestWithdrawalUrl (String receivedGiftId, String password) {
    tdlib.send(new TdApi.GetUpgradedGiftWithdrawalUrl(receivedGiftId, password), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (result != null && !StringUtils.isEmpty(result.url)) {
        tdlib.ui().openUrl(this, result.url, null);
      }
    }));
  }

  // Drop original details

  private void confirmDropDetails () {
    final Args args = getArgumentsStrict();
    showOptions(Lang.getString(R.string.UpgradedGiftDropDetailsConfirm),
      new int[] {R.id.btn_giftDropDetails, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.UpgradedGiftDropDetails), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_giftDropDetails) {
          tdlib.send(new TdApi.DropGiftOriginalDetails(args.receivedGiftId, args.dropOriginalDetailsStarCount),
            (ok, error) -> runOnUiThreadOptional(() -> {
              if (error != null) {
                UI.showError(error);
                return;
              }
              UI.showToast(R.string.UpgradedGiftDropDetailsDone, Toast.LENGTH_SHORT);
              navigateBack();
            }));
        }
        return true;
      });
  }

  @Override
  public void destroy () {
    super.destroy();
    if (headerView != null) {
      headerView.performDestroy();
    }
  }

  // Static openers

  /**
   * Opens the detail screen for an already-loaded upgraded gift owned by the
   * current user (e.g. from a gift message bubble or the received-gifts list).
   */
  public static void open (ViewController<?> context, Tdlib tdlib, Args args) {
    if (context == null || tdlib == null || args == null || args.gift == null) {
      return;
    }
    UpgradedGiftController c = new UpgradedGiftController(context.context(), tdlib);
    c.setArguments(args);
    context.context().navigation().navigateTo(c);
  }

  /**
   * Resolves an upgraded gift by its unique name (internalLinkTypeUpgradedGift)
   * and opens a read-only detail screen.
   */
  public static void openByName (ViewController<?> context, Tdlib tdlib, String name) {
    if (context == null || tdlib == null || StringUtils.isEmpty(name)) {
      return;
    }
    tdlib.send(new TdApi.GetUpgradedGift(name), (gift, error) -> context.runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      open(context, tdlib, new Args(gift));
    }));
  }
}
