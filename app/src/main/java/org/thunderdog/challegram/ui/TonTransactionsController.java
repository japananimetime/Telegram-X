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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.GiftRarityUtil;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controller for viewing TON (Toncoin) transaction history. Render-only: pages
 * {@link TdApi.GetTonTransactions} and labels each {@link TdApi.TonTransactionType},
 * including the gift-economy types (gift purchase offer, upgraded gift purchase/sale).
 */
public class TonTransactionsController extends RecyclerViewController<Void> implements View.OnClickListener {

  private SettingsAdapter adapter;
  private TdApi.TonTransactions transactions;

  public TonTransactionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.TonTransactions);
  }

  @Override
  public int getId () {
    return R.id.controller_tonTransactions;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        // Default rendering is sufficient for the info rows used here.
      }
    };
    recyclerView.setAdapter(adapter);

    buildLoadingCells();
    fetchTransactions(null);
  }

  private void buildLoadingCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TonTransactions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void fetchTransactions (String offset) {
    tdlib.send(new TdApi.GetTonTransactions(
      null, // direction - null for all
      offset,
      50
    ), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        showError(TD.toErrorString(error));
      } else {
        transactions = (TdApi.TonTransactions) result;
        buildCells();
      }
    }));
  }

  private void showError (String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TonTransactions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();

    // Balance header
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TonBalance));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    String balanceText = Lang.getString(R.string.GiftedTonAmount, GiftRarityUtil.formatTonNano(transactions.tonAmount));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, balanceText));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (transactions.transactions != null && transactions.transactions.length > 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TonTransactions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      boolean first = true;
      for (TdApi.TonTransaction transaction : transactions.transactions) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;

        String title = getTransactionTitle(transaction);
        String amount = formatTransactionAmount(transaction);
        String date = Lang.dateYearShortTime(transaction.date, TimeUnit.SECONDS);

        ListItem item = new ListItem(
          ListItem.TYPE_INFO_MULTILINE,
          0,
          transaction.tonAmount >= 0 ? R.drawable.baseline_add_24 : R.drawable.baseline_remove_circle_24,
          title,
          false
        );
        item.setString(amount + " • " + date);
        items.add(item);
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.NoTonTransactions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, true);
  }

  private String getTransactionTitle (TdApi.TonTransaction transaction) {
    if (transaction.type == null) {
      return transaction.id;
    }
    switch (transaction.type.getConstructor()) {
      case TdApi.TonTransactionTypeFragmentDeposit.CONSTRUCTOR:
        return Lang.getString(R.string.TonTxFragmentDeposit);
      case TdApi.TonTransactionTypeFragmentWithdrawal.CONSTRUCTOR:
        return Lang.getString(R.string.TonTxFragmentWithdrawal);
      case TdApi.TonTransactionTypeSuggestedPostPayment.CONSTRUCTOR:
        return Lang.getString(R.string.TonTxSuggestedPost);
      case TdApi.TonTransactionTypeGiftPurchaseOffer.CONSTRUCTOR:
        return Lang.getString(R.string.TonTxGiftOffer);
      case TdApi.TonTransactionTypeUpgradedGiftPurchase.CONSTRUCTOR:
        return Lang.getString(R.string.TonTxGiftPurchase);
      case TdApi.TonTransactionTypeUpgradedGiftSale.CONSTRUCTOR:
        return Lang.getString(R.string.TonTxGiftSale);
      case TdApi.TonTransactionTypeStakeDiceStake.CONSTRUCTOR:
        return Lang.getString(R.string.TonTxStakeDice);
      case TdApi.TonTransactionTypeStakeDicePayout.CONSTRUCTOR:
        return Lang.getString(R.string.TonTxStakeDicePayout);
      case TdApi.TonTransactionTypeUnsupported.CONSTRUCTOR:
      default:
        return Lang.getString(R.string.TonTxUnknown);
    }
  }

  private String formatTransactionAmount (TdApi.TonTransaction transaction) {
    String prefix = transaction.tonAmount >= 0 ? "+" : "-";
    return prefix + Lang.getString(R.string.GiftedTonAmount, GiftRarityUtil.formatTonNano(transaction.tonAmount));
  }

  @Override
  public void onClick (View v) {
    // Render-only screen; transaction rows are informational.
  }
}
