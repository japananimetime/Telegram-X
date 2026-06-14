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
package org.thunderdog.challegram.billing;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main billing orchestration for Telegram Premium purchases.
 *
 * Design inspired by official Telegram clients (GPL-licensed).
 * Implementation is original code.
 *
 * SECURITY NOTES:
 * - Never log purchase tokens or receipt data
 * - Premium status is determined by server, not local flags
 * - All purchases must be verified server-side before entitlement
 */
public class BillingManager implements PurchasesUpdatedListener, BillingClientStateListener {
  private static final String TAG = "BillingManager";

  private static volatile BillingManager instance;

  private final Context context;
  private final BillingClient billingClient;
  private final BillingPayloadHandler payloadHandler;
  private final Set<String> pendingTokens;
  private final Map<String, Consumer<BillingResult>> resultListeners;
  private final List<Runnable> connectionListeners;

  private ProductDetails premiumProductDetails;
  private boolean isConnected;
  private boolean billingUnavailable;
  private int retryCount;

  // In-memory cache of Stars purchase purposes, keyed by the obfuscated profile id
  // (payload id) handed to Google Play. The shared BillingPayloadHandler only knows
  // how to (de)serialize the Premium subscription purpose to disk, so for the generic
  // INAPP Stars flow we keep the live StorePaymentPurposeStars here and resolve it in
  // the purchase callback before falling back to the on-disk handler. Consumable Stars
  // purchases complete within the same app session, so this in-memory map is the
  // primary resolution path for them. See assignPurchaseToServer() / resolvePayload().
  private final Map<String, Pair<Integer, TdApi.StorePaymentPurpose>> pendingStarsPurposes =
    Collections.synchronizedMap(new HashMap<>());

  // Purchase flow state
  private Tdlib currentTdlib;
  private Runnable onPurchaseCanceled;

  public static BillingManager getInstance() {
    if (instance == null) {
      synchronized (BillingManager.class) {
        if (instance == null) {
          instance = new BillingManager(UI.getAppContext());
        }
      }
    }
    return instance;
  }

  private BillingManager(@NonNull Context context) {
    this.context = context.getApplicationContext();
    this.payloadHandler = new BillingPayloadHandler(context);
    this.pendingTokens = Collections.synchronizedSet(new HashSet<>());
    this.resultListeners = new HashMap<>();
    this.connectionListeners = new ArrayList<>();

    this.billingClient = BillingClient.newBuilder(context)
      .enablePendingPurchases()
      .setListener(this)
      .build();
  }

  // ==================== Connection Management ====================

  /**
   * Initializes billing connection.
   * Should be called on app startup.
   */
  public void initialize() {
    if (!BillingConfig.BILLING_ENABLED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Billing disabled by config");
      }
      return;
    }

    startConnection();
  }

  private void startConnection() {
    if (isConnected || billingClient.isReady()) {
      return;
    }

    try {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Starting billing connection...");
      }
      billingClient.startConnection(this);
    } catch (Exception e) {
      Log.e(TAG, "Failed to start billing connection", e);
    }
  }

  @Override
  public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
    if (BillingConfig.DEBUG_BILLING) {
      Log.d(TAG, "Billing setup finished: %s", getResponseCodeString(billingResult.getResponseCode()));
    }

    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
      isConnected = true;
      billingUnavailable = false;
      retryCount = 0;

      // Query premium product details
      queryPremiumProductDetails();

      // Check for any pending purchases
      queryExistingPurchases();

      // Notify listeners
      notifyConnectionListeners();
    } else {
      billingUnavailable = true;
      isConnected = false;
    }
  }

  @Override
  public void onBillingServiceDisconnected() {
    if (BillingConfig.DEBUG_BILLING) {
      Log.d(TAG, "Billing service disconnected");
    }

    isConnected = false;

    // Retry with exponential backoff
    if (retryCount < BillingConfig.MAX_RETRY_ATTEMPTS) {
      long delay = Math.min(
        BillingConfig.INITIAL_RETRY_DELAY_MS * (1L << retryCount),
        BillingConfig.MAX_RETRY_DELAY_MS
      );
      retryCount++;

      UI.post(this::startConnection, delay);
    }
  }

  /**
   * Adds a listener to be called when billing is connected.
   * If already connected, listener is called immediately.
   */
  public void whenConnected(@NonNull Runnable listener) {
    if (isConnected) {
      UI.post(listener);
    } else {
      synchronized (connectionListeners) {
        connectionListeners.add(listener);
      }
    }
  }

  private void notifyConnectionListeners() {
    synchronized (connectionListeners) {
      for (Runnable listener : connectionListeners) {
        UI.post(listener);
      }
      connectionListeners.clear();
    }
  }

  // ==================== Product Details ====================

  private void queryPremiumProductDetails() {
    if (!billingClient.isReady()) {
      return;
    }

    QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
      .setProductId(BillingConfig.PREMIUM_PRODUCT_ID)
      .setProductType(BillingClient.ProductType.SUBS)
      .build();

    QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
      .setProductList(Collections.singletonList(product))
      .build();

    billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        for (ProductDetails details : productDetailsList) {
          if (BillingConfig.PREMIUM_PRODUCT_ID.equals(details.getProductId())) {
            premiumProductDetails = details;
            if (BillingConfig.DEBUG_BILLING) {
              Log.d(TAG, "Premium product details loaded");
            }
            break;
          }
        }

        if (premiumProductDetails == null) {
          billingUnavailable = true;
          if (BillingConfig.DEBUG_BILLING) {
            Log.w(TAG, "Premium product not found in Play Store");
          }
        }
      } else {
        if (BillingConfig.DEBUG_BILLING) {
          Log.w(TAG, "Failed to query product details: %s",
            getResponseCodeString(billingResult.getResponseCode()));
        }
      }
    });
  }

  // ==================== Purchase Flow ====================

  /**
   * Launches the Premium purchase flow.
   *
   * @param activity   The activity to launch the flow from
   * @param tdlib      The TDLib instance for the current account
   * @param onCanceled Callback when purchase is canceled
   */
  public void launchPremiumPurchase(
    @NonNull Activity activity,
    @NonNull Tdlib tdlib,
    @Nullable Runnable onCanceled
  ) {
    if (!BillingConfig.BILLING_ENABLED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Billing disabled");
      }
      return;
    }

    if (premiumProductDetails == null || !billingClient.isReady()) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Billing not ready or product not available");
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
      return;
    }

    List<ProductDetails.SubscriptionOfferDetails> offerDetails =
      premiumProductDetails.getSubscriptionOfferDetails();

    if (offerDetails == null || offerDetails.isEmpty()) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "No subscription offers available");
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
      return;
    }

    this.currentTdlib = tdlib;
    this.onPurchaseCanceled = onCanceled;

    // Create payment purpose
    TdApi.StorePaymentPurposePremiumSubscription purpose =
      new TdApi.StorePaymentPurposePremiumSubscription();

    // Create obfuscated payload
    Pair<String, String> payload = payloadHandler.createPayload(purpose, tdlib.id());

    // Build billing flow params
    BillingFlowParams.ProductDetailsParams productParams =
      BillingFlowParams.ProductDetailsParams.newBuilder()
        .setProductDetails(premiumProductDetails)
        .setOfferToken(offerDetails.get(0).getOfferToken())
        .build();

    BillingFlowParams flowParams = BillingFlowParams.newBuilder()
      .setProductDetailsParamsList(Collections.singletonList(productParams))
      .setObfuscatedAccountId(payload.first)
      .setObfuscatedProfileId(payload.second)
      .build();

    // Launch the flow
    BillingResult result = billingClient.launchBillingFlow(activity, flowParams);

    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Failed to launch billing flow: %s",
          getResponseCodeString(result.getResponseCode()));
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
    }
  }

  /**
   * Launches a generic in-app (consumable) purchase of Telegram Stars through Google Play.
   *
   * Unlike {@link #launchPremiumPurchase}, the Stars store products are one-time INAPP
   * products (not SUBS), so there is no subscription offer token. The product details are
   * queried on demand from the option's {@code storeProductId} and the resulting transaction
   * is assigned to TDLib with a {@link TdApi.StorePaymentPurposeStars} purpose via the shared
   * {@link #onPurchasesUpdated} / {@link #assignPurchaseToServer} callback path.
   *
   * @param activity   The activity to launch the flow from
   * @param tdlib      The TDLib instance for the current account
   * @param option     The store-purchasable Stars option (must have a non-empty storeProductId)
   * @param onCanceled Callback when the purchase is canceled or cannot be started
   */
  public void launchStarsPurchase(
    @NonNull Activity activity,
    @NonNull Tdlib tdlib,
    @NonNull TdApi.StarPaymentOption option,
    @Nullable Runnable onCanceled
  ) {
    if (!BillingConfig.BILLING_ENABLED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Billing disabled");
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
      return;
    }

    if (option.storeProductId == null || option.storeProductId.isEmpty()) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Stars option has no store product id");
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
      return;
    }

    if (!billingClient.isReady()) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Billing not ready for Stars purchase");
      }
      if (onCanceled != null) {
        onCanceled.run();
      }
      return;
    }

    // Build the Stars purpose up-front. TDLib requires CanPurchaseFromStore to be called
    // before any in-store purchase; mirror that contract here.
    final TdApi.StorePaymentPurposeStars purpose = new TdApi.StorePaymentPurposeStars(
      option.currency,
      option.amount,
      option.starCount,
      0 /* chatId: buying for self */
    );

    tdlib.client().send(new TdApi.CanPurchaseFromStore(purpose), canPurchaseResult -> UI.post(() -> {
      if (canPurchaseResult.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        TdApi.Error error = (TdApi.Error) canPurchaseResult;
        if (BillingConfig.DEBUG_BILLING) {
          Log.w(TAG, "CanPurchaseFromStore failed: %d %s", error.code, error.message);
        }
        // Terminal failure before any purchase exists: drop the controller's listener so it
        // isn't leaked through this singleton.
        notifyResultListener(option.storeProductId, BillingClient.BillingResponseCode.ERROR);
        if (onCanceled != null) {
          onCanceled.run();
        }
        return;
      }
      queryAndLaunchStarsProduct(activity, tdlib, option, purpose, onCanceled);
    }));
  }

  private void queryAndLaunchStarsProduct(
    @NonNull Activity activity,
    @NonNull Tdlib tdlib,
    @NonNull TdApi.StarPaymentOption option,
    @NonNull TdApi.StorePaymentPurposeStars purpose,
    @Nullable Runnable onCanceled
  ) {
    QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
      .setProductId(option.storeProductId)
      .setProductType(BillingClient.ProductType.INAPP)
      .build();

    QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
      .setProductList(Collections.singletonList(product))
      .build();

    billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> UI.post(() -> {
      if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
        if (BillingConfig.DEBUG_BILLING) {
          Log.w(TAG, "Failed to query Stars product details: %s",
            getResponseCodeString(billingResult.getResponseCode()));
        }
        notifyResultListener(option.storeProductId, BillingClient.BillingResponseCode.ERROR);
        if (onCanceled != null) {
          onCanceled.run();
        }
        return;
      }

      ProductDetails starsDetails = null;
      for (ProductDetails details : productDetailsList) {
        if (option.storeProductId.equals(details.getProductId())) {
          starsDetails = details;
          break;
        }
      }

      if (starsDetails == null) {
        if (BillingConfig.DEBUG_BILLING) {
          Log.w(TAG, "Stars product not found in Play Store");
        }
        notifyResultListener(option.storeProductId, BillingClient.BillingResponseCode.ITEM_UNAVAILABLE);
        if (onCanceled != null) {
          onCanceled.run();
        }
        return;
      }

      launchStarsBillingFlow(activity, tdlib, starsDetails, purpose, onCanceled);
    }));
  }

  private void launchStarsBillingFlow(
    @NonNull Activity activity,
    @NonNull Tdlib tdlib,
    @NonNull ProductDetails starsDetails,
    @NonNull TdApi.StorePaymentPurposeStars purpose,
    @Nullable Runnable onCanceled
  ) {
    this.currentTdlib = tdlib;
    this.onPurchaseCanceled = onCanceled;

    // Create obfuscated payload (account id + payload id). The handler persists what it
    // can; for Stars we additionally keep the live purpose in memory keyed by payload id.
    Pair<String, String> payload = payloadHandler.createPayload(purpose, tdlib.id());
    pendingStarsPurposes.put(payload.second, Pair.create(tdlib.id(), (TdApi.StorePaymentPurpose) purpose));

    // INAPP products have no subscription offer token; set the product details directly.
    BillingFlowParams.ProductDetailsParams productParams =
      BillingFlowParams.ProductDetailsParams.newBuilder()
        .setProductDetails(starsDetails)
        .build();

    BillingFlowParams flowParams = BillingFlowParams.newBuilder()
      .setProductDetailsParamsList(Collections.singletonList(productParams))
      .setObfuscatedAccountId(payload.first)
      .setObfuscatedProfileId(payload.second)
      .build();

    BillingResult result = billingClient.launchBillingFlow(activity, flowParams);

    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Failed to launch Stars billing flow: %s",
          getResponseCodeString(result.getResponseCode()));
      }
      pendingStarsPurposes.remove(payload.second);
      notifyResultListener(starsDetails.getProductId(), result.getResponseCode());
      if (onCanceled != null) {
        onCanceled.run();
      }
    }
  }

  // ==================== Purchase Callbacks ====================

  @Override
  public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
    if (BillingConfig.DEBUG_BILLING) {
      Log.d(TAG, "Purchases updated: %s, count: %d",
        getResponseCodeString(billingResult.getResponseCode()),
        purchases != null ? purchases.size() : 0);
    }

    if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
      handlePurchaseError(billingResult);
      return;
    }

    if (purchases == null || purchases.isEmpty()) {
      return;
    }

    for (Purchase purchase : purchases) {
      handlePurchase(purchase);
    }
  }

  private void handlePurchaseError(BillingResult billingResult) {
    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Purchase canceled by user");
      }
    }

    if (onPurchaseCanceled != null) {
      UI.post(onPurchaseCanceled);
      onPurchaseCanceled = null;
    }
  }

  private void handlePurchase(Purchase purchase) {
    if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Purchase not in PURCHASED state: %d", purchase.getPurchaseState());
      }
      return;
    }

    String token = purchase.getPurchaseToken();
    if (pendingTokens.contains(token)) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Purchase already being processed");
      }
      return;
    }

    if (!purchase.isAcknowledged()) {
      assignPurchaseToServer(purchase);
    }
  }

  /**
   * Resolves the (accountId, purpose) for a completed purchase. Stars purposes are looked
   * up first from the in-memory map populated by {@link #launchStarsBillingFlow}; everything
   * else (and any miss) falls back to the on-disk {@link BillingPayloadHandler}, which is the
   * Premium subscription path.
   */
  @Nullable
  private Pair<Integer, TdApi.StorePaymentPurpose> resolvePayload(@NonNull Purchase purchase) {
    com.android.billingclient.api.AccountIdentifiers identifiers = purchase.getAccountIdentifiers();
    if (identifiers != null) {
      String obfuscatedData = identifiers.getObfuscatedProfileId();
      if (obfuscatedData != null && !obfuscatedData.isEmpty()) {
        Pair<Integer, TdApi.StorePaymentPurpose> starsPayload = pendingStarsPurposes.get(obfuscatedData);
        if (starsPayload != null) {
          return starsPayload;
        }
      }
    }
    return payloadHandler.extractPayload(purchase);
  }

  private void clearResolvedPayload(@NonNull Purchase purchase) {
    com.android.billingclient.api.AccountIdentifiers identifiers = purchase.getAccountIdentifiers();
    if (identifiers != null) {
      String obfuscatedData = identifiers.getObfuscatedProfileId();
      if (obfuscatedData != null && !obfuscatedData.isEmpty()) {
        pendingStarsPurposes.remove(obfuscatedData);
      }
    }
    payloadHandler.clearPayload(purchase);
  }

  /**
   * Attempts to assign a completed purchase to the server. Returns {@code false} when the
   * purchase's payload could not be resolved (so the caller may decide to consume an
   * otherwise-orphaned recovered purchase directly); {@code true} once the assign request
   * has been dispatched.
   */
  private boolean assignPurchaseToServer(Purchase purchase) {
    Pair<Integer, TdApi.StorePaymentPurpose> payload = resolvePayload(purchase);

    if (payload == null) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Failed to extract payload from purchase");
      }
      return false;
    }

    // Prefer the account resolved from the payload over the (possibly stale) currentTdlib,
    // so a purchase is always assigned to the account that initiated it — important when
    // recovering a purchase across an app restart, where currentTdlib reflects whichever
    // account happens to be active now, not the buyer. Fall back to currentTdlib only if
    // the resolved account has no live Tdlib instance.
    Tdlib tdlib = null;
    int resolvedAccountId = payload.first != null ? payload.first : TdlibAccount.NO_ID;
    if (resolvedAccountId != TdlibAccount.NO_ID && TdlibManager.instance().hasAccount(resolvedAccountId)) {
      tdlib = TdlibManager.instance().tdlib(resolvedAccountId);
    }
    if (tdlib == null) {
      tdlib = currentTdlib;
    }
    if (tdlib == null) {
      if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "No TDLib instance available for purchase assignment");
      }
      return false;
    }

    String token = purchase.getPurchaseToken();
    pendingTokens.add(token);

    // Get the product ID from the purchase
    String productId = purchase.getProducts().isEmpty() ? "" : purchase.getProducts().get(0);

    // Create the transaction and assign request
    TdApi.StoreTransactionGooglePlay transaction = new TdApi.StoreTransactionGooglePlay(
      purchase.getPackageName(),
      productId,
      purchase.getPurchaseToken()  // NOTE: Token sent to server only, never logged
    );
    TdApi.AssignStoreTransaction request = new TdApi.AssignStoreTransaction(
      transaction,
      payload.second
    );

    tdlib.client().send(request, result -> {
      pendingTokens.remove(token);

      UI.post(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          if (BillingConfig.DEBUG_BILLING) {
            Log.d(TAG, "Purchase assigned successfully");
          }

          // Determine consumability BEFORE clearing the in-memory Stars purpose.
          // Stars are sold as one-time consumable INAPP products. After the server has
          // credited them, the Google Play purchase must be consumed so it can be bought
          // again (and is not auto-refunded). Premium SUBS purchases are never consumable.
          boolean consumable = isConsumablePurchase(purchase, payload.second);

          // Clear stored payload (on-disk + in-memory Stars purpose)
          clearResolvedPayload(purchase);

          if (consumable) {
            consumePurchase(purchase);
          }

          // Notify success listeners
          for (String product : purchase.getProducts()) {
            notifyResultListener(product, BillingClient.BillingResponseCode.OK);
          }
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          if (BillingConfig.DEBUG_BILLING) {
            Log.w(TAG, "Failed to assign purchase: %d %s", error.code, error.message);
          }

          // Terminal failure: notify + drop any listeners so the destroyed controller is not
          // retained, then fire the cancel callback.
          for (String product : purchase.getProducts()) {
            notifyResultListener(product, BillingClient.BillingResponseCode.ERROR);
          }

          if (onPurchaseCanceled != null) {
            onPurchaseCanceled.run();
            onPurchaseCanceled = null;
          }
        }
      });
    });
    return true;
  }

  /**
   * Returns whether a completed purchase is a consumable Stars (INAPP) product that must be
   * consumed after the server credits it. Consumability is decided EXPLICITLY from the purpose
   * that was actually assigned (a {@link TdApi.StorePaymentPurposeStars}), or from the in-memory
   * Stars tracking map — never defaulted on for arbitrary non-Premium products, so an unrelated
   * future store product is not wrongly consumed (and thereby lost) here.
   *
   * @param resolvedPurpose the purpose resolved/assigned for this purchase, if known
   */
  private boolean isConsumablePurchase(@NonNull Purchase purchase, @Nullable TdApi.StorePaymentPurpose resolvedPurpose) {
    if (purchase.getProducts().contains(BillingConfig.PREMIUM_PRODUCT_ID)) {
      return false;
    }
    // Primary signal: the purpose we just assigned to the server is a Stars purchase.
    if (resolvedPurpose instanceof TdApi.StorePaymentPurposeStars) {
      return true;
    }
    // Secondary signal: we still have the purpose tracked in memory as Stars.
    com.android.billingclient.api.AccountIdentifiers identifiers = purchase.getAccountIdentifiers();
    if (identifiers != null) {
      String obfuscatedData = identifiers.getObfuscatedProfileId();
      if (obfuscatedData != null && !obfuscatedData.isEmpty()) {
        Pair<Integer, TdApi.StorePaymentPurpose> tracked = pendingStarsPurposes.get(obfuscatedData);
        if (tracked != null && tracked.second instanceof TdApi.StorePaymentPurposeStars) {
          return true;
        }
      }
    }
    // Not provably a Stars consumable: leave it unconsumed rather than risk consuming an
    // unrelated product. (Premium is already excluded above.)
    return false;
  }

  private void consumePurchase(@NonNull Purchase purchase) {
    consumePurchase(purchase, 0);
  }

  /**
   * Consumes a completed (Stars) purchase so Google Play does not auto-refund it and it can be
   * bought again. Made robust against transient failures: a non-OK consume is retried with
   * backoff up to {@link BillingConfig#MAX_RETRY_ATTEMPTS} times. Leaving a credited Stars
   * purchase unconsumed risks an auto-refund that reverses Stars already added to the account.
   */
  private void consumePurchase(@NonNull Purchase purchase, int attempt) {
    if (!billingClient.isReady()) {
      // Retry once billing reconnects so the purchase is not left unconsumed.
      if (attempt < BillingConfig.MAX_RETRY_ATTEMPTS) {
        whenConnected(() -> consumePurchase(purchase, attempt + 1));
      } else if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Billing not ready; giving up consuming purchase after %d attempts", attempt);
      }
      return;
    }
    ConsumeParams consumeParams = ConsumeParams.newBuilder()
      .setPurchaseToken(purchase.getPurchaseToken())
      .build();
    billingClient.consumeAsync(consumeParams, (billingResult, purchaseToken) -> {
      int code = billingResult.getResponseCode();
      if (BillingConfig.DEBUG_BILLING) {
        Log.d(TAG, "Consume finished: %s (attempt %d)", getResponseCodeString(code), attempt);
      }
      if (code == BillingClient.BillingResponseCode.OK ||
          code == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
        // Success, or already consumed/owned elsewhere — nothing more to do.
        return;
      }
      if (attempt < BillingConfig.MAX_RETRY_ATTEMPTS) {
        long delay = Math.min(
          BillingConfig.INITIAL_RETRY_DELAY_MS * (1L << attempt),
          BillingConfig.MAX_RETRY_DELAY_MS
        );
        UI.post(() -> consumePurchase(purchase, attempt + 1), delay);
      } else if (BillingConfig.DEBUG_BILLING) {
        Log.w(TAG, "Failed to consume purchase after %d attempts: %s",
          attempt, getResponseCodeString(code));
      }
    });
  }

  // ==================== Purchase Query ====================

  /**
   * Queries existing purchases and processes any unacknowledged ones.
   * Used for purchase restoration and pending purchase recovery.
   */
  public void queryExistingPurchases() {
    if (!billingClient.isReady()) {
      return;
    }

    QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.SUBS)
      .build();

    billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        if (BillingConfig.DEBUG_BILLING) {
          Log.d(TAG, "Found %d existing subscriptions", purchases.size());
        }

        for (Purchase purchase : purchases) {
          if (purchase.getProducts().contains(BillingConfig.PREMIUM_PRODUCT_ID)) {
            if (!purchase.isAcknowledged()) {
              // Pending purchase found, process it
              handlePurchase(purchase);
            }
          }
        }
      }
    });

    // Also recover any in-app (Stars consumable) purchases that were paid for but not yet
    // delivered to the server / consumed — e.g. the billing flow's onPurchasesUpdated was
    // missed because the app was backgrounded. These can only be re-assigned while their
    // Stars purpose is still resolvable (in-memory, same app session); see resolvePayload().
    QueryPurchasesParams inAppParams = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.INAPP)
      .build();

    billingClient.queryPurchasesAsync(inAppParams, (billingResult, purchases) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        if (BillingConfig.DEBUG_BILLING) {
          Log.d(TAG, "Found %d existing in-app purchases", purchases.size());
        }
        for (Purchase purchase : purchases) {
          // Premium is a SUBS product; INAPP results here are Stars (consumables).
          if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            continue;
          }
          String token = purchase.getPurchaseToken();
          if (pendingTokens.contains(token)) {
            continue;
          }
          // Try to (re)assign to the server. If the payload can no longer be resolved — e.g.
          // the in-memory Stars purpose was lost across an app restart AND no disk fallback
          // exists — the purchase would otherwise sit unconsumed until Google Play auto-refunds
          // it (~3 days), reversing any Stars already credited. Consume it directly so it can't
          // linger. With the BillingPayloadHandler now persisting Stars purposes, resolution
          // should normally succeed and this is a defensive last resort.
          if (!assignPurchaseToServer(purchase)) {
            if (BillingConfig.DEBUG_BILLING) {
              Log.w(TAG, "Recovered in-app purchase has unresolvable payload; consuming directly");
            }
            consumePurchase(purchase);
          }
        }
      }
    });
  }

  /**
   * Restores purchases for a TDLib instance.
   * Queries server with existing subscription tokens.
   */
  public void restorePurchases(@NonNull Tdlib tdlib) {
    this.currentTdlib = tdlib;
    queryExistingPurchases();
  }

  // ==================== Utility Methods ====================

  /**
   * Returns whether billing is available.
   */
  public boolean isBillingAvailable() {
    return BillingConfig.BILLING_ENABLED &&
           !billingUnavailable &&
           premiumProductDetails != null;
  }

  /**
   * Returns whether billing client is ready.
   */
  public boolean isReady() {
    return billingClient.isReady();
  }

  /**
   * Returns the Premium product details.
   */
  @Nullable
  public ProductDetails getPremiumProductDetails() {
    return premiumProductDetails;
  }

  /**
   * Formats currency amount for display.
   */
  public String formatPrice(long amountMicros, String currencyCode) {
    try {
      Currency currency = Currency.getInstance(currencyCode);
      NumberFormat format = NumberFormat.getCurrencyInstance();
      format.setCurrency(currency);
      return format.format(amountMicros / 1_000_000.0);
    } catch (Exception e) {
      return String.format("%d %s", amountMicros / 1_000_000, currencyCode);
    }
  }

  /**
   * Adds a result listener for a product purchase. The listener is invoked once with the
   * terminal {@link BillingResult} (OK on success, an error code on any failure / cancel) and
   * then removed, so a destroyed controller is not retained by this singleton.
   */
  public void addResultListener(String productId, Consumer<BillingResult> listener) {
    if (productId == null) {
      return;
    }
    synchronized (resultListeners) {
      resultListeners.put(productId, listener);
    }
  }

  /**
   * Removes a previously registered result listener without invoking it. Safe to call from a
   * controller's onCanceled / destroy path to avoid leaking it through the singleton.
   */
  public void removeResultListener(String productId) {
    if (productId == null) {
      return;
    }
    synchronized (resultListeners) {
      resultListeners.remove(productId);
    }
  }

  /**
   * Removes and invokes (if present) the result listener for a product with the given response
   * code, so the UI can react to both success and failure and the listener is never leaked.
   */
  private void notifyResultListener(String productId, int responseCode) {
    if (productId == null) {
      return;
    }
    Consumer<BillingResult> listener;
    synchronized (resultListeners) {
      listener = resultListeners.remove(productId);
    }
    if (listener != null) {
      listener.accept(BillingResult.newBuilder()
        .setResponseCode(responseCode)
        .build());
    }
  }

  private static String getResponseCodeString(int code) {
    switch (code) {
      case BillingClient.BillingResponseCode.OK: return "OK";
      case BillingClient.BillingResponseCode.USER_CANCELED: return "USER_CANCELED";
      case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE: return "SERVICE_UNAVAILABLE";
      case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE: return "BILLING_UNAVAILABLE";
      case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE: return "ITEM_UNAVAILABLE";
      case BillingClient.BillingResponseCode.DEVELOPER_ERROR: return "DEVELOPER_ERROR";
      case BillingClient.BillingResponseCode.ERROR: return "ERROR";
      case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED: return "ITEM_ALREADY_OWNED";
      case BillingClient.BillingResponseCode.ITEM_NOT_OWNED: return "ITEM_NOT_OWNED";
      case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED: return "SERVICE_DISCONNECTED";
      case BillingClient.BillingResponseCode.SERVICE_TIMEOUT: return "SERVICE_TIMEOUT";
      case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED: return "FEATURE_NOT_SUPPORTED";
      case BillingClient.BillingResponseCode.NETWORK_ERROR: return "NETWORK_ERROR";
      default: return "UNKNOWN(" + code + ")";
    }
  }
}
