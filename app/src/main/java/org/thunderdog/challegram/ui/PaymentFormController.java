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
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;

/**
 * Controller for payment form - handles card input and payment processing.
 */
public class PaymentFormController extends RecyclerViewController<PaymentFormController.Args>
    implements View.OnClickListener, SettingsAdapter.TextChangeListener {

  public static class Args {
    public final TdApi.PaymentForm paymentForm;
    public final TdApi.InputInvoice inputInvoice;
    public final int monthCount;

    public Args(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice, int monthCount) {
      this.paymentForm = paymentForm;
      this.inputInvoice = inputInvoice;
      this.monthCount = monthCount;
    }
  }

  private SettingsAdapter adapter;
  private TdApi.PaymentForm paymentForm;
  private TdApi.InputInvoice inputInvoice;

  // Card input fields
  private String cardNumber = "";
  private String cardExpiry = "";
  private String cardCvc = "";
  private String cardHolder = "";

  // Tip + order info
  private long tipAmount = 0;
  private String orderName = "";
  private String orderPhone = "";
  private String orderEmail = "";
  private String validatedOrderInfoId = "";
  private ListItem orderNameItem, orderPhoneItem, orderEmailItem;

  // Shipping address + options
  private String orderCountry = "", orderState = "", orderCity = "", orderStreet1 = "", orderStreet2 = "", orderPostcode = "";
  private ListItem orderCountryItem, orderStateItem, orderCityItem, orderStreet1Item, orderStreet2Item, orderPostcodeItem;
  private TdApi.ShippingOption[] shippingOptions;
  private String selectedShippingOptionId = "";

  // ListItem references for text fields
  private ListItem cardNumberItem;
  private ListItem cardExpiryItem;
  private ListItem cardCvcItem;
  private ListItem cardHolderItem;

  private boolean isProcessing = false;

  public interface PaymentResultListener {
    void onPaymentResult (boolean success);
  }

  private @Nullable PaymentResultListener paymentResultListener;

  public void setPaymentResultListener (@Nullable PaymentResultListener listener) {
    this.paymentResultListener = listener;
  }

  public PaymentFormController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName() {
    return Lang.getString(R.string.PaymentForm);
  }

  @Override
  public int getId() {
    return R.id.controller_paymentForm;
  }

  @Override
  protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
    Args args = getArgumentsStrict();
    this.paymentForm = args.paymentForm;
    this.inputInvoice = args.inputInvoice;
    if (paymentForm.type instanceof TdApi.PaymentFormTypeRegular) {
      // Prefill contact/shipping fields with the order info the user saved earlier
      TdApi.OrderInfo saved = ((TdApi.PaymentFormTypeRegular) paymentForm.type).savedOrderInfo;
      if (saved != null) {
        orderName = saved.name;
        orderPhone = saved.phoneNumber;
        orderEmail = saved.emailAddress;
        if (saved.shippingAddress != null) {
          orderCountry = saved.shippingAddress.countryCode;
          orderState = saved.shippingAddress.state;
          orderCity = saved.shippingAddress.city;
          orderStreet1 = saved.shippingAddress.streetLine1;
          orderStreet2 = saved.shippingAddress.streetLine2;
          orderPostcode = saved.shippingAddress.postalCode;
        }
      }
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_paymentSavedCard) {
          TdApi.SavedCredentials creds = (TdApi.SavedCredentials) item.getData();
          if (creds != null) {
            view.setData(creds.title);
          }
        } else if (itemId == R.id.btn_paymentShippingOption) {
          TdApi.ShippingOption option = (TdApi.ShippingOption) item.getData();
          TdApi.Invoice invoice = invoice();
          view.setData(option != null && invoice != null ? formatPrice(invoice.currency, calculateTotalAmount(option.priceParts)) : null);
        } else if (itemId == R.id.btn_paymentTip) {
          view.setData(null);
        }
      }

      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        // Edit text views are recycled between fields, so always reset both the
        // input type and the filters for every field this screen shows.
        int inputType;
        InputFilter[] filters;
        if (item == cardNumberItem) {
          inputType = InputType.TYPE_CLASS_NUMBER;
          filters = new InputFilter[] {new InputFilter.LengthFilter(19)};
        } else if (item == cardExpiryItem) {
          // Phone keyboard: digits plus '/' for MM/YY
          inputType = InputType.TYPE_CLASS_PHONE;
          filters = new InputFilter[] {new InputFilter.LengthFilter(7)};
        } else if (item == cardCvcItem) {
          inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
          filters = new InputFilter[] {new InputFilter.LengthFilter(4)};
        } else if (item == cardHolderItem) {
          inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
          filters = new InputFilter[0];
        } else if (item == orderNameItem) {
          inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
          filters = new InputFilter[0];
        } else if (item == orderPhoneItem) {
          inputType = InputType.TYPE_CLASS_PHONE;
          filters = new InputFilter[0];
        } else if (item == orderEmailItem) {
          inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
          filters = new InputFilter[0];
        } else if (item == orderCountryItem) {
          // ISO 3166-1 alpha-2 country code
          inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
          filters = new InputFilter[] {new InputFilter.LengthFilter(2), new InputFilter.AllCaps()};
        } else if (item == orderStreet1Item || item == orderStreet2Item || item == orderCityItem || item == orderStateItem) {
          inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
          filters = new InputFilter[0];
        } else if (item == orderPostcodeItem) {
          inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
          filters = new InputFilter[0];
        } else {
          return;
        }
        editText.getEditText().setInputType(inputType);
        editText.getEditText().setFilters(filters);
      }
    };
    adapter.setTextChangeListener(this);

    recyclerView.setAdapter(adapter);
    buildCells();
  }

  @Override
  public void onTextChanged(int id, ListItem item, MaterialEditTextGroup v) {
    String text = v.getText().toString();
    if (item == cardNumberItem) {
      cardNumber = text.replaceAll("\\s", "");
    } else if (item == cardExpiryItem) {
      cardExpiry = text;
    } else if (item == cardCvcItem) {
      cardCvc = text;
    } else if (item == cardHolderItem) {
      cardHolder = text;
    } else if (item == orderNameItem) {
      if (!text.equals(orderName)) {
        orderName = text;
        validatedOrderInfoId = ""; // contact info changed: re-validate before paying
      }
    } else if (item == orderPhoneItem) {
      if (!text.equals(orderPhone)) {
        orderPhone = text;
        validatedOrderInfoId = "";
      }
    } else if (item == orderEmailItem) {
      if (!text.equals(orderEmail)) {
        orderEmail = text;
        validatedOrderInfoId = "";
      }
    } else if (item == orderStreet1Item) {
      if (!text.equals(orderStreet1)) {
        orderStreet1 = text;
        invalidateShippingAddress();
      }
    } else if (item == orderStreet2Item) {
      if (!text.equals(orderStreet2)) {
        orderStreet2 = text;
        invalidateShippingAddress();
      }
    } else if (item == orderCityItem) {
      if (!text.equals(orderCity)) {
        orderCity = text;
        invalidateShippingAddress();
      }
    } else if (item == orderStateItem) {
      if (!text.equals(orderState)) {
        orderState = text;
        invalidateShippingAddress();
      }
    } else if (item == orderPostcodeItem) {
      if (!text.equals(orderPostcode)) {
        orderPostcode = text;
        invalidateShippingAddress();
      }
    } else if (item == orderCountryItem) {
      if (!text.equals(orderCountry)) {
        orderCountry = text;
        invalidateShippingAddress();
      }
    }
  }

  // Address changed: the previous validation (and its shipping options) is stale.
  // The options list stays visible until the next validation replaces it, so the
  // focused field is not unbound by a rebuild while the user is typing.
  private void invalidateShippingAddress () {
    validatedOrderInfoId = "";
    selectedShippingOptionId = "";
  }

  private long selectedShippingPrice () {
    if (shippingOptions == null || StringUtils.isEmpty(selectedShippingOptionId)) {
      return 0;
    }
    for (TdApi.ShippingOption option : shippingOptions) {
      if (option.id.equals(selectedShippingOptionId)) {
        return calculateTotalAmount(option.priceParts);
      }
    }
    return 0;
  }

  private void buildCells() {
    List<ListItem> items = new ArrayList<>();

    // Product info
    if (paymentForm.productInfo != null) {
      items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, paymentForm.productInfo.title));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      if (paymentForm.productInfo.description != null && !StringUtils.isEmpty(paymentForm.productInfo.description.text)) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, paymentForm.productInfo.description.text));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (paymentForm.type instanceof TdApi.PaymentFormTypeRegular) {
      TdApi.PaymentFormTypeRegular regular = (TdApi.PaymentFormTypeRegular) paymentForm.type;

      // Contact information (collected before payment via ValidateOrderInfo)
      if (regular.invoice.needName || regular.invoice.needPhoneNumber || regular.invoice.needEmailAddress) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentContactInfo));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        boolean firstField = true;
        if (regular.invoice.needName) {
          orderNameItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentName, 0, R.string.PaymentName).setStringValue(orderName);
          items.add(orderNameItem);
          firstField = false;
        }
        if (regular.invoice.needPhoneNumber) {
          if (!firstField) items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          orderPhoneItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentPhone, 0, R.string.PaymentPhone).setStringValue(orderPhone);
          items.add(orderPhoneItem);
          firstField = false;
        }
        if (regular.invoice.needEmailAddress) {
          if (!firstField) items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          orderEmailItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentEmail, 0, R.string.PaymentEmail).setStringValue(orderEmail);
          items.add(orderEmailItem);
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }

      // Shipping address
      if (regular.invoice.needShippingAddress) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentShippingAddress));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        orderStreet1Item = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentStreet1, 0, R.string.PaymentStreet1).setStringValue(orderStreet1);
        items.add(orderStreet1Item);
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        orderStreet2Item = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentStreet2, 0, R.string.PaymentStreet2).setStringValue(orderStreet2);
        items.add(orderStreet2Item);
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        orderCityItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCity, 0, R.string.PaymentCity).setStringValue(orderCity);
        items.add(orderCityItem);
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        orderStateItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentState, 0, R.string.PaymentState).setStringValue(orderState);
        items.add(orderStateItem);
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        orderPostcodeItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentPostcode, 0, R.string.PaymentPostcode).setStringValue(orderPostcode);
        items.add(orderPostcodeItem);
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        orderCountryItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCountry, 0, R.string.PaymentCountry).setStringValue(orderCountry);
        items.add(orderCountryItem);
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PaymentCountryHint));
      }

      // Shipping options (populated after ValidateOrderInfo)
      if (shippingOptions != null && shippingOptions.length > 0) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentShippingMethod));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        boolean firstOpt = true;
        for (TdApi.ShippingOption option : shippingOptions) {
          if (!firstOpt) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
          firstOpt = false;
          ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_paymentShippingOption, 0, option.title);
          item.setData(option);
          if (option.id.equals(selectedShippingOptionId)) {
            item.setTextColorId(ColorId.textNeutral);
          }
          items.add(item);
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }

      // Saved cards
      if (regular.savedCredentials != null && regular.savedCredentials.length > 0) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentSavedCards));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        boolean first = true;
        for (TdApi.SavedCredentials creds : regular.savedCredentials) {
          if (!first) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
          first = false;
          ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_paymentSavedCard, R.drawable.baseline_credit_card_24, creds.title);
          item.setData(creds);
          items.add(item);
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }

      // Tip (optional)
      if (regular.invoice.maxTipAmount > 0 && regular.invoice.suggestedTipAmounts != null && regular.invoice.suggestedTipAmounts.length > 0) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentTipOptional));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        boolean firstTip = true;
        for (long tip : regular.invoice.suggestedTipAmounts) {
          if (!firstTip) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
          firstTip = false;
          ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_paymentTip, 0,
            formatPrice(regular.invoice.currency, tip));
          item.setLongValue(tip);
          if (tip == tipAmount) {
            item.setTextColorId(ColorId.textNeutral);
          }
          items.add(item);
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PaymentTipHint));
      }

      // New card input
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentNewCard));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      // Card number
      cardNumberItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCard, 0, R.string.PaymentCardNumber).setStringValue(cardNumber);
      items.add(cardNumberItem);
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));

      // Expiry
      cardExpiryItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCard, 0, R.string.PaymentCardExpiry).setStringValue(cardExpiry);
      items.add(cardExpiryItem);
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));

      // CVC
      cardCvcItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCard, 0, R.string.PaymentCardCVC).setStringValue(cardCvc);
      items.add(cardCvcItem);

      // Cardholder name (if required by provider)
      if (regular.paymentProvider instanceof TdApi.PaymentProviderStripe) {
        TdApi.PaymentProviderStripe stripe = (TdApi.PaymentProviderStripe) regular.paymentProvider;
        if (stripe.needCardholderName) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          cardHolderItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_paymentCard, 0, R.string.PaymentCardHolder).setStringValue(cardHolder);
          items.add(cardHolderItem);
        }
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      // Pay button (total includes the chosen tip + selected shipping option)
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      long totalAmount = calculateTotalAmount(regular.invoice.priceParts) + tipAmount + selectedShippingPrice();
      String priceText = formatPrice(regular.invoice.currency, totalAmount);
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_paymentSubmit, R.drawable.baseline_payment_24, Lang.getString(R.string.PaymentPay, priceText)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, false);
  }

  @Override
  public void onClick(View v) {
    if (isProcessing) return;

    final int viewId = v.getId();
    if (viewId == R.id.btn_paymentSavedCard) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.SavedCredentials) {
        TdApi.SavedCredentials creds = (TdApi.SavedCredentials) item.getData();
        ensureOrderInfoThenPay(() -> payWithSavedCredentials(creds));
      }
    } else if (viewId == R.id.btn_paymentTip) {
      ListItem item = (ListItem) v.getTag();
      if (item != null) {
        long tip = item.getLongValue();
        // Tapping the selected tip clears it.
        tipAmount = (tip == tipAmount) ? 0 : tip;
        buildCells();
      }
    } else if (viewId == R.id.btn_paymentShippingOption) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.ShippingOption) {
        selectedShippingOptionId = ((TdApi.ShippingOption) item.getData()).id;
        buildCells();
      }
    } else if (viewId == R.id.btn_paymentSubmit) {
      if (validateCard()) {
        ensureOrderInfoThenPay(this::processNewCardPayment);
      }
    }
  }

  private boolean validateCard() {
    if (cardNumber.length() < 13 || cardNumber.length() > 19) {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      return false;
    }
    if (cardExpiry.length() < 4) {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      return false;
    }
    if (cardCvc.length() < 3) {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      return false;
    }
    return true;
  }

  private void payWithSavedCredentials(TdApi.SavedCredentials creds) {
    isProcessing = true;
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);

    TdApi.InputCredentialsSaved credentials = new TdApi.InputCredentialsSaved(creds.id);
    sendPaymentForm(credentials);
  }

  private void processNewCardPayment() {
    if (!(paymentForm.type instanceof TdApi.PaymentFormTypeRegular)) {
      UI.showToast(R.string.PaymentUnsupportedType, Toast.LENGTH_SHORT);
      return;
    }

    TdApi.PaymentFormTypeRegular regular = (TdApi.PaymentFormTypeRegular) paymentForm.type;

    if (regular.paymentProvider instanceof TdApi.PaymentProviderStripe) {
      TdApi.PaymentProviderStripe stripe = (TdApi.PaymentProviderStripe) regular.paymentProvider;
      tokenizeCardWithStripe(stripe.publishableKey);
    } else if (regular.paymentProvider instanceof TdApi.PaymentProviderSmartGlocal) {
      TdApi.PaymentProviderSmartGlocal smartGlocal = (TdApi.PaymentProviderSmartGlocal) regular.paymentProvider;
      tokenizeCardWithSmartGlocal(smartGlocal.publicToken, smartGlocal.tokenizeUrl);
    } else if (regular.paymentProvider instanceof TdApi.PaymentProviderOther) {
      TdApi.PaymentProviderOther other = (TdApi.PaymentProviderOther) regular.paymentProvider;
      // Open web form
      tdlib.ui().openUrl(this, other.url, null);
    } else {
      UI.showToast(R.string.PaymentUnknownProvider, Toast.LENGTH_SHORT);
    }
  }

  private void tokenizeCardWithStripe(String publishableKey) {
    isProcessing = true;
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);

    // Parse expiry
    String expMonth, expYear;
    if (cardExpiry.contains("/")) {
      String[] parts = cardExpiry.split("/");
      expMonth = parts[0].trim();
      expYear = parts.length > 1 ? parts[1].trim() : "";
    } else if (cardExpiry.length() >= 4) {
      expMonth = cardExpiry.substring(0, 2);
      expYear = cardExpiry.substring(2);
    } else {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      isProcessing = false;
      return;
    }

    // Ensure 4-digit year
    if (expYear.length() == 2) {
      expYear = "20" + expYear;
    }

    final String finalExpMonth = expMonth;
    final String finalExpYear = expYear;

    // Make Stripe API call in background
    new Thread(() -> {
      try {
        String token = createStripeToken(publishableKey, cardNumber, finalExpMonth, finalExpYear, cardCvc, cardHolder);
        runOnUiThreadOptional(() -> {
          if (token != null) {
            JSONObject tokenData = new JSONObject();
            try {
              tokenData.put("type", "card");
              tokenData.put("id", token);
            } catch (JSONException e) {
              Log.e("Payment", "Failed to create token JSON", e);
            }

            TdApi.InputCredentialsNew credentials = new TdApi.InputCredentialsNew(
              tokenData.toString(),
              true // allowSave
            );
            sendPaymentForm(credentials);
          } else {
            isProcessing = false;
            UI.showToast(R.string.PaymentCardFailed, Toast.LENGTH_SHORT);
          }
        });
      } catch (Exception e) {
        Log.e("Payment", "Stripe tokenization failed", e);
        runOnUiThreadOptional(() -> {
          isProcessing = false;
          UI.showToast(Lang.getString(R.string.PaymentFailed, e.getMessage()), Toast.LENGTH_SHORT);
        });
      }
    }).start();
  }

  private String createStripeToken(String publishableKey, String cardNumber, String expMonth, String expYear, String cvc, String name) throws Exception {
    URL url = new URL("https://api.stripe.com/v1/tokens");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Authorization", "Bearer " + publishableKey);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    conn.setDoOutput(true);

    StringBuilder params = new StringBuilder();
    params.append("card[number]=").append(cardNumber);
    params.append("&card[exp_month]=").append(expMonth);
    params.append("&card[exp_year]=").append(expYear);
    params.append("&card[cvc]=").append(cvc);
    if (!StringUtils.isEmpty(name)) {
      params.append("&card[name]=").append(java.net.URLEncoder.encode(name, "UTF-8"));
    }

    try (OutputStream os = conn.getOutputStream()) {
      os.write(params.toString().getBytes("UTF-8"));
    }

    int responseCode = conn.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();

      JSONObject jsonResponse = new JSONObject(response.toString());
      return jsonResponse.getString("id");
    } else {
      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();
      Log.e("Payment", "Stripe error: " + response);
      throw new Exception("Card processing failed");
    }
  }

  private void tokenizeCardWithSmartGlocal(String publicToken, String tokenizeUrl) {
    isProcessing = true;
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);

    // Parse expiry
    String expMonth, expYear;
    if (cardExpiry.contains("/")) {
      String[] parts = cardExpiry.split("/");
      expMonth = parts[0].trim();
      expYear = parts.length > 1 ? parts[1].trim() : "";
    } else if (cardExpiry.length() >= 4) {
      expMonth = cardExpiry.substring(0, 2);
      expYear = cardExpiry.substring(2);
    } else {
      UI.showToast(R.string.PaymentInvalidCard, Toast.LENGTH_SHORT);
      isProcessing = false;
      return;
    }

    final String finalExpMonth = expMonth;
    final String finalExpYear = expYear;

    new Thread(() -> {
      try {
        String token = createSmartGlocalToken(publicToken, tokenizeUrl, cardNumber, finalExpMonth, finalExpYear, cardCvc);
        runOnUiThreadOptional(() -> {
          if (token != null) {
            TdApi.InputCredentialsNew credentials = new TdApi.InputCredentialsNew(token, true);
            sendPaymentForm(credentials);
          } else {
            isProcessing = false;
            UI.showToast(R.string.PaymentCardFailed, Toast.LENGTH_SHORT);
          }
        });
      } catch (Exception e) {
        Log.e("Payment", "SmartGlocal tokenization failed", e);
        runOnUiThreadOptional(() -> {
          isProcessing = false;
          UI.showToast(Lang.getString(R.string.PaymentFailed, e.getMessage()), Toast.LENGTH_SHORT);
        });
      }
    }).start();
  }

  private String createSmartGlocalToken(String publicToken, String tokenizeUrl, String cardNumber, String expMonth, String expYear, String cvc) throws Exception {
    URL url = new URL(tokenizeUrl);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("X-Public-Token", publicToken);
    conn.setDoOutput(true);

    JSONObject card = new JSONObject();
    card.put("number", cardNumber);
    card.put("expiration_month", expMonth);
    card.put("expiration_year", expYear);
    card.put("security_code", cvc);

    JSONObject body = new JSONObject();
    body.put("card", card);

    try (OutputStream os = conn.getOutputStream()) {
      os.write(body.toString().getBytes("UTF-8"));
    }

    int responseCode = conn.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();
      return response.toString();
    } else {
      throw new Exception("Card processing failed");
    }
  }

  private TdApi.Invoice invoice () {
    return paymentForm.type instanceof TdApi.PaymentFormTypeRegular
      ? ((TdApi.PaymentFormTypeRegular) paymentForm.type).invoice : null;
  }

  private boolean needsOrderInfo () {
    TdApi.Invoice inv = invoice();
    return inv != null && (inv.needName || inv.needPhoneNumber || inv.needEmailAddress || inv.needShippingAddress);
  }

  /**
   * Validates collected contact/shipping info (if required) before paying. When the
   * invoice needs shipping, ValidateOrderInfo returns shipping options the user must
   * pick from: in that case they are shown and payment happens on the next tap.
   */
  private void ensureOrderInfoThenPay (Runnable pay) {
    TdApi.Invoice inv = invoice();
    if (!needsOrderInfo()) {
      pay.run();
      return;
    }
    if (!StringUtils.isEmpty(validatedOrderInfoId)) {
      if (inv != null && inv.needShippingAddress && StringUtils.isEmpty(selectedShippingOptionId)
        && shippingOptions != null && shippingOptions.length > 0) {
        UI.showToast(R.string.PaymentSelectShipping, Toast.LENGTH_SHORT);
        return;
      }
      pay.run();
      return;
    }
    isProcessing = true;
    TdApi.Address address = (inv != null && inv.needShippingAddress)
      ? new TdApi.Address(orderCountry, orderState, orderCity, orderStreet1, orderStreet2, orderPostcode) : null;
    TdApi.OrderInfo orderInfo = new TdApi.OrderInfo(orderName, orderPhone, orderEmail, address);
    tdlib.send(new TdApi.ValidateOrderInfo(inputInvoice, orderInfo, false), (result, error) -> runOnUiThreadOptional(() -> {
      isProcessing = false;
      if (error != null) {
        UI.showToast(Lang.getString(R.string.PaymentOrderInfoError, TD.toErrorString(error)), Toast.LENGTH_SHORT);
      } else if (result != null) {
        validatedOrderInfoId = result.orderInfoId;
        shippingOptions = result.shippingOptions;
        if (shippingOptions != null && shippingOptions.length > 0) {
          // Auto-select a single option, otherwise show the list and wait for a choice.
          if (shippingOptions.length == 1) {
            selectedShippingOptionId = shippingOptions[0].id;
            buildCells();
            pay.run();
          } else {
            selectedShippingOptionId = "";
            buildCells();
            UI.showToast(R.string.PaymentSelectShipping, Toast.LENGTH_SHORT);
          }
        } else {
          shippingOptions = null;
          selectedShippingOptionId = "";
          buildCells();
          pay.run();
        }
      }
    }));
  }

  private void sendPaymentForm(TdApi.InputCredentials credentials) {
    TdApi.SendPaymentForm request = new TdApi.SendPaymentForm(
      inputInvoice,
      paymentForm.id,
      validatedOrderInfoId,
      selectedShippingOptionId,
      credentials,
      tipAmount
    );

    tdlib.send(request, (result, error) -> {
      runOnUiThreadOptional(() -> {
        isProcessing = false;
        if (error != null) {
          UI.showToast(Lang.getString(R.string.PaymentFailed, TD.toErrorString(error)), Toast.LENGTH_SHORT);
          if (paymentResultListener != null) {
            paymentResultListener.onPaymentResult(false);
          }
        } else {
          TdApi.PaymentResult paymentResult = (TdApi.PaymentResult) result;
          if (paymentResult.success) {
            UI.showToast(R.string.PaymentSuccess, Toast.LENGTH_SHORT);
            if (paymentResultListener != null) {
              paymentResultListener.onPaymentResult(true);
            }
            navigateBack();
          } else if (!StringUtils.isEmpty(paymentResult.verificationUrl)) {
            // Need 3D Secure verification
            tdlib.ui().openUrl(PaymentFormController.this, paymentResult.verificationUrl, null);
          } else {
            UI.showToast(R.string.PaymentVerificationNeeded, Toast.LENGTH_SHORT);
          }
        }
      });
    });
  }

  private long calculateTotalAmount(TdApi.LabeledPricePart[] priceParts) {
    if (priceParts == null) return 0;
    long total = 0;
    for (TdApi.LabeledPricePart part : priceParts) {
      total += part.amount;
    }
    return total;
  }

  private String formatPrice(String currency, long amount) {
    // amount/100.0 is wrong for zero-decimal (JPY) and 3-decimal currencies and
    // ignores locale/symbol. CurrencyUtils handles per-currency exponent + format.
    return CurrencyUtils.buildAmount(currency, amount);
  }
}
