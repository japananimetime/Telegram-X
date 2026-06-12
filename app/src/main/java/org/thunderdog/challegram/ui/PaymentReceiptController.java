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
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;

/**
 * Read-only view of a completed payment (GetPaymentReceipt). Opened from the
 * "payment successful" service message. Renders product info, the itemised price
 * (regular receipts) or the Stars amount + transaction id (Stars receipts), the
 * tip, chosen shipping option, payment method, and contact/shipping details.
 */
public class PaymentReceiptController extends RecyclerViewController<PaymentReceiptController.Args> implements View.OnClickListener {

  public static class Args {
    public final long chatId;
    public final long messageId;
    public Args (long chatId, long messageId) {
      this.chatId = chatId;
      this.messageId = messageId;
    }
  }

  private SettingsAdapter adapter;
  private @Nullable TdApi.PaymentReceipt receipt;

  public PaymentReceiptController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.PaymentReceipt);
  }

  @Override
  public int getId () {
    return R.id.controller_paymentReceipt;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    recyclerView.setAdapter(adapter);
    buildLoadingCells();
    Args args = getArgumentsStrict();
    tdlib.send(new TdApi.GetPaymentReceipt(args.chatId, args.messageId), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        showError(TD.toErrorString(error));
        return;
      }
      receipt = result;
      buildCells();
    }));
  }

  private void buildLoadingCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void showError (String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private static void addRow (List<ListItem> items, CharSequence title, CharSequence value, boolean first) {
    if (!first) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, 0, title, false);
    item.setData(value);
    items.add(item);
  }

  private void buildCells () {
    if (receipt == null) {
      return;
    }
    List<ListItem> items = new ArrayList<>();

    // Product
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentReceiptProduct));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    if (receipt.productInfo != null) {
      addRow(items, Lang.getString(R.string.PaymentReceiptTitle), receipt.productInfo.title, first);
      first = false;
      if (receipt.productInfo.description != null && !StringUtils.isEmpty(receipt.productInfo.description.text)) {
        addRow(items, Lang.getString(R.string.PaymentReceiptDescription), receipt.productInfo.description.text, false);
      }
    }
    TdApi.User seller = tdlib.cache().user(receipt.sellerBotUserId);
    if (seller != null) {
      addRow(items, Lang.getString(R.string.PaymentReceiptSeller), TD.getUserName(seller), first);
      first = false;
    }
    addRow(items, Lang.getString(R.string.PaymentReceiptDate),
      Lang.getMessageTimestamp(receipt.date, TimeUnit.SECONDS), first);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Amount
    if (receipt.type != null) {
      switch (receipt.type.getConstructor()) {
        case TdApi.PaymentReceiptTypeRegular.CONSTRUCTOR: {
          TdApi.PaymentReceiptTypeRegular regular = (TdApi.PaymentReceiptTypeRegular) receipt.type;
          buildRegular(items, regular);
          break;
        }
        case TdApi.PaymentReceiptTypeStars.CONSTRUCTOR: {
          TdApi.PaymentReceiptTypeStars stars = (TdApi.PaymentReceiptTypeStars) receipt.type;
          buildStars(items, stars);
          break;
        }
      }
    }

    adapter.setItems(items, false);
  }

  private void buildRegular (List<ListItem> items, TdApi.PaymentReceiptTypeRegular regular) {
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentReceiptPrice));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    final String currency = regular.invoice != null ? regular.invoice.currency : "";
    long total = 0;
    boolean first = true;
    if (regular.invoice != null && regular.invoice.priceParts != null) {
      for (TdApi.LabeledPricePart part : regular.invoice.priceParts) {
        addRow(items, part.label, CurrencyUtils.buildAmount(currency, part.amount), first);
        first = false;
        total += part.amount;
      }
    }
    if (regular.shippingOption != null && regular.shippingOption.priceParts != null) {
      for (TdApi.LabeledPricePart part : regular.shippingOption.priceParts) {
        addRow(items, part.label, CurrencyUtils.buildAmount(currency, part.amount), first);
        first = false;
        total += part.amount;
      }
    }
    if (regular.tipAmount > 0) {
      addRow(items, Lang.getString(R.string.PaymentReceiptTip), CurrencyUtils.buildAmount(currency, regular.tipAmount), first);
      first = false;
      total += regular.tipAmount;
    }
    addRow(items, Lang.getString(R.string.PaymentReceiptTotal), CurrencyUtils.buildAmount(currency, total), first);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Payment method / contact
    List<ListItem> meta = new ArrayList<>();
    boolean metaFirst = true;
    if (!StringUtils.isEmpty(regular.credentialsTitle)) {
      addRow(meta, Lang.getString(R.string.PaymentReceiptMethod), regular.credentialsTitle, metaFirst);
      metaFirst = false;
    }
    if (regular.orderInfo != null) {
      TdApi.OrderInfo info = regular.orderInfo;
      if (!StringUtils.isEmpty(info.name)) {
        addRow(meta, Lang.getString(R.string.PaymentName), info.name, metaFirst);
        metaFirst = false;
      }
      if (!StringUtils.isEmpty(info.phoneNumber)) {
        addRow(meta, Lang.getString(R.string.PaymentPhone), info.phoneNumber, metaFirst);
        metaFirst = false;
      }
      if (!StringUtils.isEmpty(info.emailAddress)) {
        addRow(meta, Lang.getString(R.string.PaymentEmail), info.emailAddress, metaFirst);
        metaFirst = false;
      }
    }
    if (!meta.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentReceiptMethodSection));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.addAll(meta);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
  }

  private void buildStars (List<ListItem> items, TdApi.PaymentReceiptTypeStars stars) {
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentReceiptPrice));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    addRow(items, Lang.getString(R.string.PaymentReceiptTotal), Lang.plural(R.string.xStars, stars.starCount), true);
    if (!StringUtils.isEmpty(stars.transactionId)) {
      addRow(items, Lang.getString(R.string.PaymentReceiptTransaction), stars.transactionId, false);
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  @Override
  public void onClick (View v) {
    // No interactive rows.
  }
}
