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
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;

/**
 * Controller for Telegram Stars - buy stars and view balance.
 */
public class SettingsStarsController extends RecyclerViewController<SettingsStarsController.Args> implements View.OnClickListener {

  public static class Args {
    public final long requiredStarCount;
    public final String purpose;

    public Args() {
      this.requiredStarCount = 0;
      this.purpose = null;
    }

    public Args(long requiredStarCount, String purpose) {
      this.requiredStarCount = requiredStarCount;
      this.purpose = purpose;
    }
  }

  private SettingsAdapter adapter;
  private TdApi.StarPaymentOptions paymentOptions;
  private long starBalance = 0;
  private boolean focusedBefore;

  public SettingsStarsController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName() {
    return Lang.getString(R.string.TelegramStars);
  }

  @Override
  public int getId() {
    return R.id.controller_stars;
  }

  @Override
  protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_starOption) {
          TdApi.StarPaymentOption option = (TdApi.StarPaymentOption) item.getData();
          if (option != null) {
            String price = CurrencyUtils.buildAmount(option.currency, option.amount);
            view.setData(price);
          }
        }
      }
    };

    recyclerView.setAdapter(adapter);

    // Show loading state
    buildLoadingCells();

    // Fetch data
    fetchData();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    // Refresh the balance when returning to this screen (e.g. after a regular
    // card purchase handed off to PaymentFormController). The first focus is
    // skipped because onCreateView() already kicks off fetchData().
    if (focusedBefore) {
      fetchData();
    } else {
      focusedBefore = true;
    }
  }

  private void buildLoadingCells() {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramStars));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void fetchData() {
    // Fetch star balance
    tdlib.send(new TdApi.GetStarTransactions(new TdApi.MessageSenderUser(tdlib.myUserId()), null, null, null, 1), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error == null && result != null) {
          TdApi.StarTransactions transactions = (TdApi.StarTransactions) result;
          starBalance = transactions.starAmount.starCount;
        }
        fetchPaymentOptions();
      });
    });
  }

  private void fetchPaymentOptions() {
    tdlib.send(new TdApi.GetStarPaymentOptions(), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          showError(TD.toErrorString(error));
        } else {
          paymentOptions = (TdApi.StarPaymentOptions) result;
          buildCells();
        }
      });
    });
  }

  private void showError(String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramStars));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private void buildCells() {
    List<ListItem> items = new ArrayList<>();

    // Header and description
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramStars));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.StarsDescription));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Balance
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.StarsBalance));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    String balanceText = Lang.plural(R.string.xStars, starBalance);
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, balanceText));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Transaction history link
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_starTransactions, R.drawable.baseline_history_24, R.string.StarTransactions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Payment options
    if (paymentOptions != null && paymentOptions.options != null && paymentOptions.options.length > 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BuyStars));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      boolean first = true;
      for (TdApi.StarPaymentOption option : paymentOptions.options) {
        if (option.isAdditional) continue; // Skip additional options for now
        
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;

        String title = Lang.plural(R.string.xStars, option.starCount);
        ListItem item = new ListItem(
          ListItem.TYPE_VALUED_SETTING_COMPACT,
          R.id.btn_starOption,
          R.drawable.baseline_star_24,
          title,
          false
        );
        item.setData(option);
        items.add(item);
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, true);
  }

  @Override
  public void onClick(View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_starOption) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.StarPaymentOption) {
        TdApi.StarPaymentOption option = (TdApi.StarPaymentOption) item.getData();
        purchaseStars(option);
      }
    } else if (viewId == R.id.btn_starTransactions) {
      openTransactionHistory();
    }
  }

  private void purchaseStars(TdApi.StarPaymentOption option) {
    if (!StringUtils.isEmpty(option.storeProductId)) {
      // Store (Google Play) purchase of Telegram Stars.
      // Mirrors SettingsPremiumController#startPaymentFlow: when only a store
      // product id is available and there is no out-of-store payment route,
      // surface the store-unavailable notice. The shared BillingManager is
      // currently wired exclusively for the Premium SUBS product and exposes
      // no generic INAPP entry point for StorePaymentPurposeStars, so an
      // honest notice is shown instead of a fake purchase. See report.
      UI.showToast(R.string.PremiumStorePaymentNotAvailable, Toast.LENGTH_SHORT);
    } else {
      // Out-of-store purchase: build a Telegram invoice for the Stars purpose
      // and route it through the regular GetPaymentForm flow, mirroring
      // SettingsPremiumController#openPaymentForm / #handlePaymentForm.
      purchaseStarsOutOfStore(option);
    }
  }

  private void purchaseStarsOutOfStore(TdApi.StarPaymentOption option) {
    UI.showToast(R.string.LoadingPaymentForm, Toast.LENGTH_SHORT);

    TdApi.TelegramPaymentPurposeStars purpose = new TdApi.TelegramPaymentPurposeStars(
      option.currency,
      option.amount,
      option.starCount,
      0 /* chatId: buying for self */
    );
    TdApi.InputInvoiceTelegram inputInvoice = new TdApi.InputInvoiceTelegram(purpose);

    tdlib.send(new TdApi.GetPaymentForm(inputInvoice, null), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          // Wrap consistently with sendStarsPayment so the user sees a localized
          // "Payment failed: <reason>" rather than the raw TDLib error string.
          UI.showToast(Lang.getString(R.string.StarsPaymentFailed, TD.toErrorString(error)), Toast.LENGTH_SHORT);
        } else {
          TdApi.PaymentForm paymentForm = (TdApi.PaymentForm) result;
          handlePaymentForm(paymentForm, inputInvoice, option.starCount);
        }
      });
    });
  }

  private void handlePaymentForm(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice, long starCount) {
    if (paymentForm.type instanceof TdApi.PaymentFormTypeRegular) {
      // Regular (card / external provider) payment form: hand off to the
      // shared payment form controller, as SettingsPremiumController does.
      PaymentFormController controller = new PaymentFormController(context(), tdlib);
      controller.setArguments(new PaymentFormController.Args(paymentForm, inputInvoice, 0));
      navigateTo(controller);
    } else if (paymentForm.type instanceof TdApi.PaymentFormTypeStars) {
      TdApi.PaymentFormTypeStars starsType = (TdApi.PaymentFormTypeStars) paymentForm.type;
      showStarsPaymentConfirmation(paymentForm, inputInvoice, starsType.starCount);
    } else {
      UI.showToast(R.string.PaymentUnknownType, Toast.LENGTH_SHORT);
    }
  }

  private void showStarsPaymentConfirmation(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice, long starCount) {
    String message = Lang.getString(R.string.StarsPayConfirmMessage, starCount);
    showOptions(
      message,
      new int[] { R.id.btn_done, R.id.btn_cancel },
      new String[] { Lang.getString(R.string.StarsPayConfirm, starCount), Lang.getString(R.string.Cancel) },
      new int[] { OptionColor.BLUE, OptionColor.NORMAL },
      new int[] { R.drawable.baseline_star_24, R.drawable.baseline_cancel_24 },
      (view, optionId) -> {
        if (optionId == R.id.btn_done) {
          sendStarsPayment(paymentForm, inputInvoice);
        }
        return true;
      }
    );
  }

  private void sendStarsPayment(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice) {
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);
    tdlib.send(new TdApi.SendPaymentForm(inputInvoice, paymentForm.id, "", "", null, 0), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showToast(Lang.getString(R.string.StarsPaymentFailed, TD.toErrorString(error)), Toast.LENGTH_SHORT);
        } else if (result instanceof TdApi.PaymentResult) {
          TdApi.PaymentResult paymentResult = (TdApi.PaymentResult) result;
          if (paymentResult.success) {
            UI.showToast(R.string.StarsPaymentSuccess, Toast.LENGTH_SHORT);
            // Refresh balance and options after a successful purchase.
            fetchData();
          } else if (!StringUtils.isEmpty(paymentResult.verificationUrl)) {
            // Some payments require a final verification step (e.g. 3D Secure).
            tdlib.ui().openUrl(this, paymentResult.verificationUrl, null);
          } else {
            UI.showToast(R.string.PaymentVerificationNeeded, Toast.LENGTH_SHORT);
          }
        }
      });
    });
  }

  private void openTransactionHistory() {
    StarTransactionsController controller = new StarTransactionsController(context(), tdlib);
    navigateTo(controller);
  }
}
