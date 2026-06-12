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
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram Stars monetization for a channel/bot: shows the available / current /
 * total Star revenue (GetStarRevenueStatistics) with the USD estimate. The chart
 * and the password-gated withdrawal flow are follow-ups.
 */
public class StarRevenueController extends RecyclerViewController<StarRevenueController.Args> implements View.OnClickListener {

  public static class Args {
    public final TdApi.MessageSender ownerId;
    public Args (TdApi.MessageSender ownerId) {
      this.ownerId = ownerId;
    }
  }

  private SettingsAdapter adapter;
  private TdApi.MessageSender ownerId;
  private @Nullable TdApi.StarRevenueStatistics statistics;

  public StarRevenueController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Monetization);
  }

  @Override
  public int getId () {
    return R.id.controller_starRevenue;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.ownerId = args.ownerId;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    recyclerView.setAdapter(adapter);
    buildLoadingCells();
    // isDark only affects the (not-yet-rendered) revenue chart's colors.
    tdlib.send(new TdApi.GetStarRevenueStatistics(ownerId, false), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        showError(TD.toErrorString(error));
        return;
      }
      statistics = result;
      buildCells();
    }));
  }

  private void buildLoadingCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.Monetization));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void showError (String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.Monetization));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private String usd (TdApi.StarAmount amount) {
    if (statistics == null || amount == null) {
      return "";
    }
    double dollars = amount.starCount * statistics.usdRate;
    return String.format(java.util.Locale.US, " · ≈ $%.2f", dollars);
  }

  private void addAmountRow (List<ListItem> items, int titleRes, TdApi.StarAmount amount, boolean first) {
    if (!first) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    long stars = amount != null ? amount.starCount : 0;
    ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, R.drawable.baseline_premium_star_24, titleRes);
    item.setData(Lang.plural(R.string.xStars, stars) + usd(amount));
    items.add(item);
  }

  private void buildCells () {
    if (statistics == null) {
      return;
    }
    TdApi.StarRevenueStatus status = statistics.status;
    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.MonetizationBalance));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    addAmountRow(items, R.string.MonetizationAvailable, status.availableAmount, true);
    addAmountRow(items, R.string.MonetizationCurrent, status.currentAmount, false);
    addAmountRow(items, R.string.MonetizationTotal, status.totalAmount, false);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    ListItem withdraw = new ListItem(ListItem.TYPE_SETTING, R.id.btn_withdrawStars, R.drawable.baseline_payment_24,
      R.string.MonetizationWithdraw);
    if (status.withdrawalEnabled && status.availableAmount != null && status.availableAmount.starCount > 0) {
      withdraw.setTextColorId(ColorId.textNeutral);
    }
    items.add(withdraw);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.MonetizationHint));

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_withdrawStars) {
      if (statistics == null || statistics.status == null || !statistics.status.withdrawalEnabled
        || statistics.status.availableAmount == null || statistics.status.availableAmount.starCount <= 0) {
        UI.showToast(R.string.MonetizationWithdrawUnavailable, android.widget.Toast.LENGTH_SHORT);
        return;
      }
      startWithdrawal();
    }
  }

  private void startWithdrawal () {
    // Withdrawal requires confirming the account's 2-step verification password to
    // obtain a one-time withdrawal URL, which is then opened in the browser.
    tdlib.send(new TdApi.GetPasswordState(), (state, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showToast(TD.toErrorString(error), android.widget.Toast.LENGTH_SHORT);
        return;
      }
      if (!state.hasPassword) {
        UI.showToast(R.string.MonetizationWithdraw2FARequired, android.widget.Toast.LENGTH_LONG);
        return;
      }
      PasswordController c = new PasswordController(context, tdlib);
      c.setArguments(new PasswordController.Args(PasswordController.MODE_CONFIRM, state)
        .setSuccessListener(this::completeWithdrawal));
      navigateTo(c);
    }));
  }

  private void completeWithdrawal (String password) {
    final long starCount = statistics != null && statistics.status != null && statistics.status.availableAmount != null
      ? statistics.status.availableAmount.starCount : 0;
    if (starCount <= 0) {
      return;
    }
    tdlib.send(new TdApi.GetStarWithdrawalUrl(ownerId, starCount, password), (httpUrl, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showToast(TD.toErrorString(error), android.widget.Toast.LENGTH_SHORT);
        return;
      }
      if (httpUrl != null && httpUrl.url != null) {
        tdlib.ui().openUrl(this, httpUrl.url, null);
      }
    }));
  }
}
