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
 * File created on 17/01/2026
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.content.pm.ActivityInfo;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.Manifest;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.util.Locale;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.BiometricAuthentication;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.CameraController;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.v.WebAppProxy;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class WebAppController extends WebkitController<WebAppController.Args> implements Menu, MoreDelegate, FactorAnimator.Target, SensorEventListener, ThemeChangeListener {

  public static class Args {
    public final long chatId;
    public final long botUserId;
    public final String botUsername;
    public final String url;
    public final long launchId;
    public final TdApi.WebAppOpenMode openMode;
    public @Nullable String buttonText;
    public @Nullable MessagesController ownerController;

    public Args (long chatId, long botUserId, String botUsername, String url, long launchId, @Nullable TdApi.WebAppOpenMode openMode) {
      this.chatId = chatId;
      this.botUserId = botUserId;
      this.botUsername = botUsername;
      this.url = url;
      this.launchId = launchId;
      this.openMode = openMode;
    }

    public Args setButtonText (String buttonText) {
      this.buttonText = buttonText;
      return this;
    }

    public Args setOwnerController (MessagesController controller) {
      this.ownerController = controller;
      return this;
    }
  }

  // UI components
  private WebView webView;
  private WebAppProxy webAppProxy;
  private FrameLayoutFix contentView;
  private DoubleHeaderView headerCell;
  private MainButtonView mainButtonView;
  private MainButtonView secondaryButtonView;

  // Main button state
  private boolean mainButtonVisible = false;
  private boolean mainButtonActive = true;
  private boolean mainButtonProgressVisible = false;
  private String mainButtonText = "";
  private int mainButtonColor = 0;
  private int mainButtonTextColor = 0;

  // Secondary button state
  private boolean secondaryButtonVisible = false;
  private boolean secondaryButtonActive = true;
  private boolean secondaryButtonProgressVisible = false;
  private String secondaryButtonText = "";
  private int secondaryButtonColor = 0;
  private int secondaryButtonTextColor = 0;
  private String secondaryButtonPosition = "left";

  // Fullscreen state
  private boolean isFullscreen = false;
  private boolean orientationLocked = false;
  private int savedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

  // Back button state
  private boolean backButtonVisible = false;

  // Settings button state
  private boolean settingsButtonVisible = false;

  // Expansion state
  private boolean isExpanded = false;

  // Closing confirmation
  private boolean closingConfirmationEnabled = false;

  // Swipe behavior
  private boolean verticalSwipeAllowed = true;

  // Header/background colors (null means use theme default)
  private Integer customHeaderColor = null;
  private Integer customBackgroundColor = null;
  private Integer customBottomBarColor = null;

  // Animation IDs
  private static final int ANIMATOR_MAIN_BUTTON = 0;
  private BoolAnimator mainButtonAnimator;

  public WebAppController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_webApp;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_webApp;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_webApp) {
      header.addMoreButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      int[] ids;
      String[] titles;
      if (settingsButtonVisible) {
        ids = new int[] {R.id.btn_settings, R.id.btn_openLink, R.id.btn_reload};
        titles = new String[] {Lang.getString(R.string.BotSettings), Lang.getString(R.string.OpenInExternalApp), Lang.getString(R.string.Reload)};
      } else {
        ids = new int[] {R.id.btn_openLink, R.id.btn_reload};
        titles = new String[] {Lang.getString(R.string.OpenInExternalApp), Lang.getString(R.string.Reload)};
      }
      showMore(ids, titles, 0);
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_openLink) {
      if (getArguments() != null) {
        Intents.openUri(getArguments().url);
      }
    } else if (id == R.id.btn_reload) {
      if (webView != null) {
        webView.reload();
      }
    } else if (id == R.id.btn_settings) {
      onWebAppSettingsButtonPressed();
    }
  }

  @SuppressLint("AddJavascriptInterface")
  @Override
  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    this.webView = webView;
    this.headerCell = headerCell;

    // Get the content view (parent of webView)
    this.contentView = (FrameLayoutFix) webView.getParent();

    Args args = getArguments();
    if (args != null) {
      headerCell.setTitle(args.botUsername != null ? args.botUsername : Lang.getString(R.string.WebApp));
      headerCell.setSubtitle(Lang.getString(R.string.WebApp));

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        webAppProxy = new WebAppProxy(this);
        webView.addJavascriptInterface(webAppProxy, "TelegramWebviewProxy");
      }

      // Create and add secondary button (above main button)
      secondaryButtonView = new MainButtonView(context());
      secondaryButtonView.setVisibility(View.GONE);
      secondaryButtonView.setOnClickListener(v -> onSecondaryButtonClicked());
      contentView.addView(secondaryButtonView, FrameLayoutFix.newParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f),
        Gravity.BOTTOM
      ));

      // Create and add main button
      mainButtonView = new MainButtonView(context());
      mainButtonView.setVisibility(View.GONE);
      mainButtonView.setOnClickListener(v -> onMainButtonClicked());
      contentView.addView(mainButtonView, FrameLayoutFix.newParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f),
        Gravity.BOTTOM
      ));

      // Initialize animator
      mainButtonAnimator = new BoolAnimator(ANIMATOR_MAIN_BUTTON, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);

      // Set up layout change listener for viewport reporting
      webView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        if (bottom - top != oldBottom - oldTop) {
          sendViewportData();
        }
      });

      // Register for theme changes
      ThemeManager.instance().addThemeListener(this);

      // Register for WebApp message sent updates
      if (args.launchId != 0) {
        tdlib.addWebAppMessageSentListener(args.launchId, this);
      }

      // Try to load placeholder while webview loads
      loadPlaceholder();

      webView.loadUrl(args.url);
    }
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    if (!verticalSwipeAllowed && y >= Size.getHeaderPortraitSize()) {
      return false;
    }
    return y < Size.getHeaderPortraitSize() || x <= Screen.dp(15f);
  }

  @Override
  protected int getBackButton () {
    return backButtonVisible ? BackHeaderButton.TYPE_BACK : BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (backButtonVisible) {
      onWebAppBackButtonPressed();
      return true;
    }
    if (closingConfirmationEnabled) {
      showCloseConfirmation();
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  private void showCloseConfirmation () {
    showOptions(
      Lang.getString(R.string.WebAppCloseConfirmation),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Close), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      null,
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          closingConfirmationEnabled = false;
          navigateBack();
        }
        return true;
      }
    );
  }

  @Override
  public void destroy () {
    stopAllSensors();
    ThemeManager.instance().removeThemeListener(this);
    tdlib.removeWebAppMessageSentListener(this);
    if (isFullscreen) {
      restoreFullscreenState();
    }
    if (orientationLocked) {
      onWebAppToggleOrientationLock(false);
    }
    super.destroy();
    if (getArguments() != null && getArguments().launchId != 0) {
      tdlib.send(new TdApi.CloseWebApp(getArguments().launchId), (result, error) -> {
        if (error != null) {
          Log.e("Failed to close web app: %s", error.message);
        }
      });
    }
  }

  // FactorAnimator.Target implementation
  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ANIMATOR_MAIN_BUTTON) {
      updateMainButtonLayout(factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_MAIN_BUTTON && finalFactor == 0f) {
      mainButtonView.setVisibility(View.GONE);
    }
  }

  private void updateMainButtonLayout (float factor) {
    if (mainButtonView == null) return;

    int buttonHeight = Screen.dp(48f);
    int translationY = (int) ((1f - factor) * buttonHeight);
    mainButtonView.setTranslationY(translationY);
    mainButtonView.setAlpha(factor);

    // Adjust webView bottom padding
    int bottomPadding = (int) (buttonHeight * factor);
    webView.setPadding(0, 0, 0, bottomPadding);
  }

  // ==================== Web App JavaScript Interface Callbacks ====================

  public void onWebAppReady () {
    Log.i("WebApp ready");
    // Send initial viewport data
    sendViewportData();
    // Send initial safe area data
    sendSafeAreaData();
  }

  public void onWebAppClose () {
    UI.post(() -> {
      if (closingConfirmationEnabled) {
        showCloseConfirmation();
      } else {
        navigateBack();
      }
    });
  }

  public void onWebAppSendData (String data) {
    Args args = getArguments();
    if (args != null && args.botUserId != 0) {
      String buttonText = args.buttonText != null ? args.buttonText : "";
      tdlib.send(new TdApi.SendWebAppData(args.botUserId, buttonText, data), (result, error) -> {
        if (error != null) {
          Log.e("Failed to send web app data: %s", error.message);
        }
        UI.post(this::navigateBack);
      });
    }
  }

  public void onWebAppOpenLink (String url) {
    onWebAppOpenLink(url, false);
  }

  public void onWebAppOpenLink (String url, boolean tryInstantView) {
    if (tryInstantView) {
      TdlibUi.UrlOpenParameters params = new TdlibUi.UrlOpenParameters().forceInstantView();
      tdlib.ui().openUrl(this, url, params);
    } else {
      tdlib.ui().openUrl(this, url, null);
    }
  }

  public void onWebAppRequestPhone () {
    TdApi.User user = tdlib.myUser();
    if (user == null) {
      sendEventToWebApp("phone_requested", "{\"status\":\"cancelled\"}");
      return;
    }

    showOptions(
      Lang.getString(R.string.ShareYourPhoneNumberBot),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Share), Lang.getString(R.string.Cancel)},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          String phone = user.phoneNumber;
          sendEventToWebApp("phone_requested", "{\"status\":\"sent\",\"phone_number\":\"" + escapeJsonString(phone) + "\"}");
        } else {
          sendEventToWebApp("phone_requested", "{\"status\":\"cancelled\"}");
        }
        return true;
      }
    );
  }

  public void onWebAppRequestWriteAccess () {
    Args args = getArguments();
    if (args == null) {
      sendEventToWebApp("write_access_requested", "{\"status\":\"cancelled\"}");
      return;
    }

    showOptions(
      Lang.getString(R.string.AllowWriteAccess, tdlib.chatTitle(args.chatId)),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Allow), Lang.getString(R.string.Cancel)},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          tdlib.send(new TdApi.AllowBotToSendMessages(args.botUserId), (result, error) -> {
            UI.post(() -> {
              if (error != null) {
                sendEventToWebApp("write_access_requested", "{\"status\":\"cancelled\"}");
              } else {
                sendEventToWebApp("write_access_requested", "{\"status\":\"allowed\"}");
              }
            });
          });
        } else {
          sendEventToWebApp("write_access_requested", "{\"status\":\"cancelled\"}");
        }
        return true;
      }
    );
  }

  // ==================== Main Button ====================

  public void onWebAppSetMainButton (boolean visible, String text, int color, int textColor, boolean isActive, boolean isProgressVisible) {
    onWebAppSetMainButton(visible, text, color, textColor, isActive, isProgressVisible, false);
  }

  public void onWebAppSetMainButton (boolean visible, String text, int color, int textColor, boolean isActive, boolean isProgressVisible, boolean hasShineEffect) {
    this.mainButtonVisible = visible;
    this.mainButtonText = text;
    this.mainButtonColor = color;
    this.mainButtonTextColor = textColor;
    this.mainButtonActive = isActive;
    this.mainButtonProgressVisible = isProgressVisible;
    UI.post(this::updateMainButton);
  }

  private void updateMainButton () {
    if (mainButtonView == null) return;

    mainButtonView.setText(mainButtonText);
    mainButtonView.setButtonColors(mainButtonColor, mainButtonTextColor);
    mainButtonView.setActive(mainButtonActive);
    mainButtonView.setProgressVisible(mainButtonProgressVisible);

    if (mainButtonVisible) {
      mainButtonView.setVisibility(View.VISIBLE);
      mainButtonAnimator.setValue(true, true);
    } else {
      mainButtonAnimator.setValue(false, true);
    }
  }

  private void onMainButtonClicked () {
    if (!mainButtonActive || mainButtonProgressVisible) return;
    sendEventToWebApp("main_button_pressed", "{}");
  }

  // ==================== Secondary Button ====================

  public void onWebAppSetSecondaryButton (boolean visible, String text, int color, int textColor, boolean isActive, boolean isProgressVisible, boolean hasShineEffect, String position) {
    this.secondaryButtonVisible = visible;
    this.secondaryButtonText = text;
    this.secondaryButtonColor = color;
    this.secondaryButtonTextColor = textColor;
    this.secondaryButtonActive = isActive;
    this.secondaryButtonProgressVisible = isProgressVisible;
    this.secondaryButtonPosition = position != null ? position : "left";
    UI.post(this::updateSecondaryButton);
  }

  private void updateSecondaryButton () {
    if (secondaryButtonView == null) return;

    secondaryButtonView.setText(secondaryButtonText);
    secondaryButtonView.setButtonColors(secondaryButtonColor, secondaryButtonTextColor);
    secondaryButtonView.setActive(secondaryButtonActive);
    secondaryButtonView.setProgressVisible(secondaryButtonProgressVisible);

    if (secondaryButtonVisible) {
      secondaryButtonView.setVisibility(View.VISIBLE);
      // Position secondary button above main button when both visible
      updateButtonPositions();
    } else {
      secondaryButtonView.setVisibility(View.GONE);
      updateButtonPositions();
    }
  }

  private void updateButtonPositions () {
    int buttonHeight = Screen.dp(48f);

    if (secondaryButtonVisible && mainButtonVisible) {
      FrameLayout.LayoutParams secondaryParams = (FrameLayout.LayoutParams) secondaryButtonView.getLayoutParams();
      FrameLayout.LayoutParams mainParams = (FrameLayout.LayoutParams) mainButtonView.getLayoutParams();

      switch (secondaryButtonPosition) {
        case "top":
          // Secondary above main — both full width, stacked
          secondaryParams.bottomMargin = buttonHeight;
          secondaryParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          mainParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          mainParams.gravity = Gravity.BOTTOM;
          secondaryParams.gravity = Gravity.BOTTOM;
          break;
        case "bottom":
          // Secondary below main — secondary at very bottom, main above it
          secondaryParams.bottomMargin = 0;
          mainParams.bottomMargin = buttonHeight;
          secondaryParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          mainParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          mainParams.gravity = Gravity.BOTTOM;
          secondaryParams.gravity = Gravity.BOTTOM;
          break;
        case "right":
          // Secondary to the right of main — side by side
          secondaryParams.bottomMargin = 0;
          mainParams.bottomMargin = 0;
          secondaryParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          mainParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          mainParams.gravity = Gravity.BOTTOM | Gravity.START;
          secondaryParams.gravity = Gravity.BOTTOM | Gravity.END;
          break;
        case "left":
        default:
          // Secondary to the left of main — side by side (default)
          secondaryParams.bottomMargin = 0;
          mainParams.bottomMargin = 0;
          secondaryParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          mainParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
          mainParams.gravity = Gravity.BOTTOM | Gravity.END;
          secondaryParams.gravity = Gravity.BOTTOM | Gravity.START;
          break;
      }
      secondaryButtonView.setLayoutParams(secondaryParams);
      mainButtonView.setLayoutParams(mainParams);
    } else if (secondaryButtonVisible) {
      FrameLayout.LayoutParams secondaryParams = (FrameLayout.LayoutParams) secondaryButtonView.getLayoutParams();
      secondaryParams.bottomMargin = 0;
      secondaryParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
      secondaryParams.gravity = Gravity.BOTTOM;
      secondaryButtonView.setLayoutParams(secondaryParams);
    } else if (mainButtonVisible) {
      FrameLayout.LayoutParams mainParams = (FrameLayout.LayoutParams) mainButtonView.getLayoutParams();
      mainParams.bottomMargin = 0;
      mainParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
      mainParams.gravity = Gravity.BOTTOM;
      mainButtonView.setLayoutParams(mainParams);
    }

    // Adjust webView bottom padding
    int totalHeight = 0;
    if (mainButtonVisible && secondaryButtonVisible) {
      boolean stacked = "top".equals(secondaryButtonPosition) || "bottom".equals(secondaryButtonPosition);
      totalHeight = stacked ? buttonHeight * 2 : buttonHeight;
    } else if (mainButtonVisible || secondaryButtonVisible) {
      totalHeight = buttonHeight;
    }
    if (webView != null) {
      webView.setPadding(0, 0, 0, totalHeight);
    }
  }

  private void onSecondaryButtonClicked () {
    if (!secondaryButtonActive || secondaryButtonProgressVisible) return;
    sendEventToWebApp("secondary_button_pressed", "{}");
  }

  // ==================== Back Button ====================

  public void onWebAppSetBackButton (boolean visible) {
    if (this.backButtonVisible != visible) {
      this.backButtonVisible = visible;
      UI.post(() -> {
        // Update the header button
        if (headerView() != null) {
          headerView().updateButton(getMenuId(), getBackButton(), 0);
        }
      });
    }
  }

  public void onWebAppBackButtonPressed () {
    sendEventToWebApp("back_button_pressed", "{}");
  }

  // ==================== Settings Button ====================

  public void onWebAppSetSettingsButton (boolean visible) {
    this.settingsButtonVisible = visible;
  }

  private void onWebAppSettingsButtonPressed () {
    sendEventToWebApp("settings_button_pressed", "{}");
  }

  // ==================== Popups & Dialogs ====================

  public void onWebAppShowAlert (String message, @Nullable Runnable callback) {
    showOptions(
      message,
      new int[] {R.id.btn_done},
      new String[] {Lang.getString(R.string.OK)},
      (itemView, id) -> {
        if (callback != null) {
          callback.run();
        }
        sendEventToWebApp("popup_closed", "{\"button_id\":\"\"}");
        return true;
      }
    );
  }

  public void onWebAppShowConfirm (String message, @Nullable java.util.function.Consumer<Boolean> callback) {
    showOptions(
      message,
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.OK), Lang.getString(R.string.Cancel)},
      (itemView, id) -> {
        boolean confirmed = id == R.id.btn_done;
        if (callback != null) {
          callback.accept(confirmed);
        }
        sendEventToWebApp("popup_closed", "{\"button_id\":\"" + (confirmed ? "ok" : "") + "\"}");
        return true;
      }
    );
  }

  public void onWebAppShowPopup (String title, String message, JSONArray buttons) {
    if (buttons == null || buttons.length() == 0) {
      onWebAppShowAlert(message, null);
      return;
    }

    int buttonCount = Math.min(buttons.length(), 3);
    int[] ids = new int[buttonCount];
    String[] titles = new String[buttonCount];
    int[] colors = new int[buttonCount];
    String[] buttonIds = new String[buttonCount];

    for (int i = 0; i < buttonCount; i++) {
      JSONObject btn = buttons.optJSONObject(i);
      if (btn != null) {
        buttonIds[i] = btn.optString("id", "");
        String type = btn.optString("type", "default");
        String text = btn.optString("text", "");

        ids[i] = R.id.btn_done + i;

        switch (type) {
          case "ok":
            titles[i] = text.isEmpty() ? Lang.getString(R.string.OK) : text;
            colors[i] = OptionColor.NORMAL;
            break;
          case "close":
            titles[i] = text.isEmpty() ? Lang.getString(R.string.Close) : text;
            colors[i] = OptionColor.NORMAL;
            break;
          case "cancel":
            titles[i] = text.isEmpty() ? Lang.getString(R.string.Cancel) : text;
            colors[i] = OptionColor.NORMAL;
            break;
          case "destructive":
            titles[i] = text;
            colors[i] = OptionColor.RED;
            break;
          default:
            titles[i] = text;
            colors[i] = OptionColor.NORMAL;
            break;
        }
      }
    }

    final String[] finalButtonIds = buttonIds;
    CharSequence popupMessage = title != null && !title.isEmpty()
      ? title + "\n\n" + message
      : message;

    showOptions(
      popupMessage,
      ids,
      titles,
      colors,
      null,
      (itemView, id) -> {
        int index = id - R.id.btn_done;
        String selectedId = index >= 0 && index < finalButtonIds.length ? finalButtonIds[index] : "";
        sendEventToWebApp("popup_closed", "{\"button_id\":\"" + escapeJsonString(selectedId) + "\"}");
        return true;
      }
    );
  }

  // ==================== Haptic Feedback ====================

  public void onWebAppHapticFeedback (String type, String style) {
    View view = getViewForApplyingOffsets();
    if (view == null) return;

    switch (type) {
      case "impact":
        switch (style) {
          case "light":
            performHapticFeedback(view, 10);
            break;
          case "medium":
            performHapticFeedback(view, 20);
            break;
          case "heavy":
            performHapticFeedback(view, 50);
            break;
          case "rigid":
            performHapticFeedback(view, 30);
            break;
          case "soft":
            performHapticFeedback(view, 15);
            break;
        }
        break;
      case "notification":
        switch (style) {
          case "error":
            performHapticFeedback(view, 40);
            break;
          case "success":
            performHapticFeedback(view, 25);
            break;
          case "warning":
            performHapticFeedback(view, 35);
            break;
        }
        break;
      case "selection_change":
        performHapticFeedback(view, 5);
        break;
    }
  }

  private void performHapticFeedback (View view, int duration) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Vibrator vibrator = (Vibrator) context().getSystemService(Context.VIBRATOR_SERVICE);
      if (vibrator != null && vibrator.hasVibrator()) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
      }
    } else {
      UI.hapticVibrate(view, duration > 20);
    }
  }

  // ==================== Closing & Swipe Behavior ====================

  public void onWebAppSetClosingBehavior (boolean needConfirmation) {
    this.closingConfirmationEnabled = needConfirmation;
  }

  public void onWebAppSetSwipeBehavior (boolean allowVerticalSwipe) {
    this.verticalSwipeAllowed = allowVerticalSwipe;
  }

  // ==================== Hide Keyboard ====================

  public void onWebAppHideKeyboard () {
    if (webView != null) {
      Keyboard.hide(webView);
    }
  }

  // ==================== Fullscreen ====================

  public void onWebAppRequestFullscreen () {
    if (isFullscreen) return;
    isFullscreen = true;
    UI.post(() -> {
      BaseActivity activity = context();
      if (activity != null && activity.getWindow() != null) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          android.view.WindowInsetsController insetsController = activity.getWindow().getInsetsController();
          if (insetsController != null) {
            insetsController.hide(android.view.WindowInsets.Type.systemBars());
            insetsController.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
          }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          activity.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          );
        }
        if (headerView() != null) {
          headerView().setVisibility(View.GONE);
        }
        sendEventToWebApp("fullscreen_changed", "{\"is_fullscreen\":true}");
      } else {
        isFullscreen = false;
        sendEventToWebApp("fullscreen_failed", "{\"error\":\"UNSUPPORTED\"}");
      }
    });
  }

  public void onWebAppExitFullscreen () {
    if (!isFullscreen) return;
    isFullscreen = false;
    UI.post(() -> {
      restoreFullscreenState();
      sendEventToWebApp("fullscreen_changed", "{\"is_fullscreen\":false}");
    });
  }

  private void restoreFullscreenState () {
    BaseActivity activity = context();
    if (activity != null && activity.getWindow() != null) {
      activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.view.WindowInsetsController insetsController = activity.getWindow().getInsetsController();
        if (insetsController != null) {
          insetsController.show(android.view.WindowInsets.Type.systemBars());
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
      }
      if (headerView() != null) {
        headerView().setVisibility(View.VISIBLE);
      }
    }
  }

  // ==================== Orientation Lock ====================

  public void onWebAppToggleOrientationLock (boolean locked) {
    BaseActivity activity = context();
    if (activity == null) return;

    if (locked && !orientationLocked) {
      savedOrientation = activity.getRequestedOrientation();
      activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
      orientationLocked = true;
    } else if (!locked && orientationLocked) {
      activity.setRequestedOrientation(savedOrientation);
      orientationLocked = false;
    }
  }

  // ==================== Invoice Handling ====================

  public void onWebAppOpenInvoice (String slug) {
    TdApi.InputInvoiceName inputInvoice = new TdApi.InputInvoiceName(slug);
    tdlib.send(new TdApi.GetPaymentForm(inputInvoice, getThemeParameters()), (result, error) -> {
      UI.post(() -> {
        if (error != null) {
          sendEventToWebApp("invoice_closed", "{\"slug\":\"" + escapeJsonString(slug) + "\",\"status\":\"failed\"}");
          return;
        }
        TdApi.PaymentForm paymentForm = (TdApi.PaymentForm) result;
        openPaymentFormForInvoice(slug, paymentForm, inputInvoice);
      });
    });
  }

  private void openPaymentFormForInvoice (String slug, TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice) {
    if (paymentForm.type instanceof TdApi.PaymentFormTypeStars) {
      TdApi.PaymentFormTypeStars starsType = (TdApi.PaymentFormTypeStars) paymentForm.type;
      String message = Lang.plural(R.string.StarsPayConfirmMessage, (int) starsType.starCount);
      showOptions(
        message,
        new int[] { R.id.btn_done, R.id.btn_cancel },
        new String[] { Lang.getString(R.string.StarsPayConfirm, starsType.starCount), Lang.getString(R.string.Cancel) },
        new int[] { OptionColor.BLUE, OptionColor.NORMAL },
        new int[] { R.drawable.baseline_star_24, R.drawable.baseline_cancel_24 },
        (view, optionId) -> {
          if (optionId == R.id.btn_done) {
            tdlib.send(new TdApi.SendPaymentForm(inputInvoice, paymentForm.id, "", "", null, 0), (result, error) -> {
              UI.post(() -> {
                if (error != null) {
                  sendEventToWebApp("invoice_closed", "{\"slug\":\"" + escapeJsonString(slug) + "\",\"status\":\"failed\"}");
                } else {
                  sendEventToWebApp("invoice_closed", "{\"slug\":\"" + escapeJsonString(slug) + "\",\"status\":\"paid\"}");
                }
              });
            });
          } else {
            sendEventToWebApp("invoice_closed", "{\"slug\":\"" + escapeJsonString(slug) + "\",\"status\":\"cancelled\"}");
          }
          return true;
        }
      );
    } else if (paymentForm.type instanceof TdApi.PaymentFormTypeRegular) {
      PaymentFormController formController = new PaymentFormController(context(), tdlib);
      formController.setArguments(new PaymentFormController.Args(paymentForm, inputInvoice, 0));
      formController.setPaymentResultListener(success -> {
        String status = success ? "paid" : "cancelled";
        sendEventToWebApp("invoice_closed", "{\"slug\":\"" + escapeJsonString(slug) + "\",\"status\":\"" + status + "\"}");
      });
      navigateTo(formController);
    } else {
      sendEventToWebApp("invoice_closed", "{\"slug\":\"" + escapeJsonString(slug) + "\",\"status\":\"failed\"}");
    }
  }

  // ==================== Switch Inline Query ====================

  public void onWebAppSwitchInlineQuery (String query, JSONArray chatTypes) {
    Args args = getArguments();
    if (args == null || args.botUsername == null) return;

    // Open chat picker with inline query, or switch to current chat
    tdlib.ui().switchInline(this, args.botUsername, query, false);
    navigateBack();
  }

  // ==================== Viewport & Expansion ====================

  public void onWebAppExpand () {
    isExpanded = true;
    sendViewportData();
  }

  public void onWebAppRequestViewport () {
    sendViewportData();
  }

  private void sendViewportData () {
    if (webView == null) return;

    int buttonHeight = Screen.dp(48f);
    int height = webView.getHeight();
    if (mainButtonVisible && secondaryButtonVisible) {
      boolean stacked = "top".equals(secondaryButtonPosition) || "bottom".equals(secondaryButtonPosition);
      height -= stacked ? buttonHeight * 2 : buttonHeight;
    } else if (mainButtonVisible || secondaryButtonVisible) {
      height -= buttonHeight;
    }
    int stableHeight = height;
    boolean expanded = isExpanded;

    String js = String.format(
      "if (window.Telegram && window.Telegram.WebApp) {" +
      "  Telegram.WebApp.viewportHeight = %d;" +
      "  Telegram.WebApp.viewportStableHeight = %d;" +
      "  Telegram.WebApp.isExpanded = %b;" +
      "  if (Telegram.WebApp.onEvent) {" +
      "    Telegram.WebApp.onEvent('viewportChanged', {isStateStable: true});" +
      "  }" +
      "}",
      height, stableHeight, expanded
    );
    webView.evaluateJavascript(js, null);
  }

  // ==================== Dynamic Theme Colors ====================

  public void onWebAppSetHeaderColor (String color) {
    int parsedColor = parseColor(color);
    if (parsedColor != 0) {
      this.customHeaderColor = parsedColor;
      UI.post(() -> {
        if (headerCell != null) {
          // Note: Full header color change requires more complex implementation
          // For now we can adjust the header view background if exposed
        }
      });
    }
  }

  public void onWebAppSetBackgroundColor (String color) {
    int parsedColor = parseColor(color);
    if (parsedColor != 0) {
      this.customBackgroundColor = parsedColor;
      UI.post(() -> {
        if (contentView != null) {
          contentView.setBackgroundColor(parsedColor);
        }
      });
    }
  }

  public void onWebAppSetBottomBarColor (String color) {
    int parsedColor = parseColor(color);
    if (parsedColor != 0) {
      this.customBottomBarColor = parsedColor;
      // Note: Bottom bar is the main button background, already handled by mainButtonColor
    }
  }

  private int parseColor (String colorStr) {
    if (colorStr == null || colorStr.isEmpty()) return 0;
    try {
      if (colorStr.startsWith("#")) {
        return (int) Long.parseLong(colorStr.substring(1), 16) | 0xFF000000;
      } else if (colorStr.equals("bg_color")) {
        return Theme.getColor(ColorId.filling);
      } else if (colorStr.equals("secondary_bg_color")) {
        return Theme.getColor(ColorId.fillingPositive);
      }
      return 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  // ==================== Cloud Storage (Custom Methods) ====================

  public void onWebAppInvokeCustomMethod (String requestId, String method, String parameters) {
    Args args = getArguments();
    if (args == null) {
      sendCustomMethodError(requestId, "No bot context");
      return;
    }

    tdlib.send(new TdApi.SendWebAppCustomRequest(args.botUserId, method, parameters), (result, error) -> {
      UI.post(() -> {
        if (error != null) {
          sendCustomMethodError(requestId, error.message);
        } else {
          TdApi.CustomRequestResult customResult = (TdApi.CustomRequestResult) result;
          sendCustomMethodResult(requestId, customResult.result);
        }
      });
    });
  }

  private void sendCustomMethodResult (String requestId, String result) {
    String js = String.format(
      "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
      "  Telegram.WebApp.onEvent('custom_method_invoked', {req_id: '%s', result: %s});" +
      "}",
      escapeJsonString(requestId), result != null ? result : "null"
    );
    if (webView != null) {
      webView.evaluateJavascript(js, null);
    }
  }

  private void sendCustomMethodError (String requestId, String error) {
    String js = String.format(
      "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
      "  Telegram.WebApp.onEvent('custom_method_invoked', {req_id: '%s', error: '%s'});" +
      "}",
      escapeJsonString(requestId), escapeJsonString(error)
    );
    if (webView != null) {
      webView.evaluateJavascript(js, null);
    }
  }

  // ==================== Clipboard Access ====================

  public void onWebAppReadClipboard (String requestId) {
    CharSequence clipText = U.getPasteText(context());
    String data = clipText != null ? clipText.toString() : null;

    String js;
    if (data != null) {
      js = String.format(
        "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
        "  Telegram.WebApp.onEvent('clipboard_text_received', {req_id: '%s', data: '%s'});" +
        "}",
        escapeJsonString(requestId), escapeJsonString(data)
      );
    } else {
      js = String.format(
        "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
        "  Telegram.WebApp.onEvent('clipboard_text_received', {req_id: '%s'});" +
        "}",
        escapeJsonString(requestId)
      );
    }
    if (webView != null) {
      webView.evaluateJavascript(js, null);
    }
  }

  // ==================== Biometric Authentication ====================

  public void onWebAppBiometryGetInfo () {
    boolean available = BiometricAuthentication.isAvailable();
    String type = available ? (BiometricAuthentication.ONLY_FINGERPRINT ? "finger" : "face") : "";

    String js = String.format(
      "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
      "  Telegram.WebApp.onEvent('biometry_info_received', {" +
      "    available: %b," +
      "    type: '%s'," +
      "    access_requested: %b," +
      "    access_granted: %b," +
      "    device_id: ''," +
      "    token_saved: %b" +
      "  });" +
      "}",
      available, type, biometryAccessRequested, biometryAccessGranted, biometricToken != null
    );
    if (webView != null) {
      webView.evaluateJavascript(js, null);
    }
  }

  private boolean biometryAccessRequested = false;
  private boolean biometryAccessGranted = false;

  public void onWebAppBiometryRequestAccess (String reason) {
    if (!BiometricAuthentication.isAvailable()) {
      sendBiometryAccessResult(false);
      return;
    }

    biometryAccessRequested = true;

    showOptions(
      reason != null && !reason.isEmpty() ? reason : Lang.getString(R.string.ConfirmYourBiometrics),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Allow), Lang.getString(R.string.Cancel)},
      (itemView, id) -> {
        biometryAccessGranted = (id == R.id.btn_done);
        sendBiometryAccessResult(biometryAccessGranted);
        return true;
      }
    );
  }

  private void sendBiometryAccessResult (boolean granted) {
    String js = String.format(
      "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
      "  Telegram.WebApp.onEvent('biometry_auth_requested', {status: '%s'});" +
      "}",
      granted ? "authorized" : "failed"
    );
    if (webView != null) {
      webView.evaluateJavascript(js, null);
    }
  }

  public void onWebAppBiometryAuthenticate (String reason) {
    if (!BiometricAuthentication.isAvailable() || !biometryAccessGranted) {
      sendBiometryAuthResult(false, null);
      return;
    }

    BaseActivity activity = context();
    if (activity == null) {
      sendBiometryAuthResult(false, null);
      return;
    }

    BiometricAuthentication.authenticate(
      activity,
      reason != null && !reason.isEmpty() ? reason : Lang.getString(R.string.ConfirmYourBiometrics),
      Lang.getString(R.string.Cancel),
      false,
      new BiometricAuthentication.Callback() {
        @Override
        public void onAuthenticated (androidx.biometric.BiometricPrompt.AuthenticationResult result, boolean strong) {
          UI.post(() -> sendBiometryAuthResult(true, biometricToken != null ? biometricToken : ""));
        }

        @Override
        public void onAuthenticationError (CharSequence message, boolean isFatal) {
          UI.post(() -> sendBiometryAuthResult(false, null));
        }
      }
    );
  }

  private void sendBiometryAuthResult (boolean success, String token) {
    String js;
    if (success) {
      js = String.format(
        "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
        "  Telegram.WebApp.onEvent('biometry_auth_requested', {status: 'authorized', token: '%s'});" +
        "}",
        token != null ? escapeJsonString(token) : ""
      );
    } else {
      js = "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
           "  Telegram.WebApp.onEvent('biometry_auth_requested', {status: 'failed'});" +
           "}";
    }
    if (webView != null) {
      webView.evaluateJavascript(js, null);
    }
  }

  // ==================== Home Screen Shortcuts ====================

  public void onWebAppCheckHomeScreen () {
    boolean supported = ShortcutManagerCompat.isRequestPinShortcutSupported(context());
    String status = supported ? "allowed" : "missed";
    sendEventToWebApp("home_screen_checked", "{\"status\":\"" + status + "\"}");
  }

  public void onWebAppAddToHomeScreen () {
    Args args = getArguments();
    if (args == null) {
      sendEventToWebApp("home_screen_added", "{\"status\":\"failed\"}");
      return;
    }

    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context())) {
      sendEventToWebApp("home_screen_added", "{\"status\":\"missed\"}");
      return;
    }

    String label = args.botUsername != null ? args.botUsername : Lang.getString(R.string.WebApp);
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + args.botUsername));
    intent.setPackage(context().getPackageName());

    ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context(), "webapp_" + args.botUserId)
      .setShortLabel(label)
      .setIntent(intent)
      .setIcon(IconCompat.createWithResource(context(), R.drawable.baseline_star_24))
      .build();

    boolean result = ShortcutManagerCompat.requestPinShortcut(context(), shortcut, null);
    String status = result ? "added" : "failed";
    sendEventToWebApp("home_screen_added", "{\"status\":\"" + status + "\"}");
  }

  // ==================== File Download ====================

  public void onWebAppRequestFileDownload (String url, String fileName) {
    Args args = getArguments();
    if (args == null) {
      sendEventToWebApp("file_download_requested", "{\"status\":\"cancelled\"}");
      return;
    }

    // Validate with TDLib
    tdlib.send(new TdApi.CheckWebAppFileDownload(args.botUserId, fileName, url), (result, error) -> {
      UI.post(() -> {
        if (error != null) {
          sendEventToWebApp("file_download_requested", "{\"status\":\"cancelled\"}");
          return;
        }
        // Show confirmation dialog
        showOptions(
          Lang.getString(R.string.WebAppDownloadFile, fileName),
          new int[] {R.id.btn_done, R.id.btn_cancel},
          new String[] {Lang.getString(R.string.Download), Lang.getString(R.string.Cancel)},
          (itemView, id) -> {
            if (id == R.id.btn_done) {
              startFileDownload(url, fileName);
              sendEventToWebApp("file_download_requested", "{\"status\":\"downloading\"}");
            } else {
              sendEventToWebApp("file_download_requested", "{\"status\":\"cancelled\"}");
            }
            return true;
          }
        );
      });
    });
  }

  private void startFileDownload (String url, String fileName) {
    try {
      DownloadManager downloadManager = (DownloadManager) context().getSystemService(Context.DOWNLOAD_SERVICE);
      if (downloadManager != null) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
        downloadManager.enqueue(request);
      }
    } catch (Exception e) {
      Log.e("Failed to start download: %s", e.getMessage());
    }
  }

  // ==================== Emoji Status ====================

  public void onWebAppRequestEmojiStatusAccess () {
    TdApi.User me = tdlib.myUser();
    if (me == null || !me.isPremium) {
      sendEventToWebApp("emoji_status_access_requested", "{\"status\":\"cancelled\"}");
      return;
    }

    showOptions(
      Lang.getString(R.string.WebAppEmojiStatusAccess),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Allow), Lang.getString(R.string.Cancel)},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          sendEventToWebApp("emoji_status_access_requested", "{\"status\":\"allowed\"}");
        } else {
          sendEventToWebApp("emoji_status_access_requested", "{\"status\":\"cancelled\"}");
        }
        return true;
      }
    );
  }

  public void onWebAppSetEmojiStatus (long customEmojiId, int duration) {
    int expirationDate = duration > 0 ? (int) (System.currentTimeMillis() / 1000) + duration : 0;
    TdApi.EmojiStatusTypeCustomEmoji emojiType = new TdApi.EmojiStatusTypeCustomEmoji(customEmojiId);
    TdApi.EmojiStatus emojiStatus = new TdApi.EmojiStatus(emojiType, expirationDate);
    tdlib.send(new TdApi.SetEmojiStatus(emojiStatus), (result, error) -> {
      UI.post(() -> {
        if (error != null) {
          sendEventToWebApp("emoji_status_set", "{\"status\":\"failed\"}");
        } else {
          sendEventToWebApp("emoji_status_set", "{\"status\":\"set\"}");
        }
      });
    });
  }

  // ==================== Location Services ====================

  private static final int LOCATION_PERMISSION_REQUEST = 1001;

  public void onWebAppCheckLocation () {
    BaseActivity activity = context();
    if (activity == null) {
      sendEventToWebApp("location_checked", "{\"available\":false,\"access_requested\":false,\"access_granted\":false}");
      return;
    }
    boolean granted = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    sendEventToWebApp("location_checked",
      String.format("{\"available\":true,\"access_requested\":%b,\"access_granted\":%b}", granted, granted));
  }

  public void onWebAppOpenLocationSettings () {
    try {
      context().startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    } catch (Exception e) {
      Log.e("Failed to open location settings: %s", e.getMessage());
    }
  }

  public void onWebAppRequestLocation () {
    BaseActivity activity = context();
    if (activity == null) {
      sendLocationResult(false, null);
      return;
    }

    // Check permission
    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      // Show confirmation dialog first
      showOptions(
        Lang.getString(R.string.WebAppLocationAccess),
        new int[] {R.id.btn_done, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.Allow), Lang.getString(R.string.Cancel)},
        (itemView, id) -> {
          if (id == R.id.btn_done) {
            ActivityCompat.requestPermissions(activity, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            // Location will be sent after permission result
          } else {
            sendLocationResult(false, null);
          }
          return true;
        }
      );
      return;
    }

    getAndSendLocation();
  }

  @SuppressLint("MissingPermission")
  private void getAndSendLocation () {
    try {
      LocationManager locationManager = (LocationManager) context().getSystemService(Context.LOCATION_SERVICE);
      if (locationManager != null) {
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
          location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        sendLocationResult(location != null, location);
      } else {
        sendLocationResult(false, null);
      }
    } catch (Exception e) {
      Log.e("Failed to get location: %s", e.getMessage());
      sendLocationResult(false, null);
    }
  }

  private void sendLocationResult (boolean available, @Nullable Location location) {
    if (location != null && available) {
      String data = String.format(Locale.US,
        "{\"available\":true,\"latitude\":%f,\"longitude\":%f,\"altitude\":%f," +
        "\"course\":%f,\"speed\":%f,\"horizontal_accuracy\":%f,\"vertical_accuracy\":%f," +
        "\"course_accuracy\":0,\"speed_accuracy\":0}",
        location.getLatitude(), location.getLongitude(), location.getAltitude(),
        location.getBearing(), location.getSpeed(), location.getAccuracy(),
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? location.getVerticalAccuracyMeters() : 0f
      );
      sendEventToWebApp("location_requested", data);
    } else {
      sendEventToWebApp("location_requested", "{\"available\":false}");
    }
  }

  public void onRequestPermissionsResult (int requestCode, int[] grantResults) {
    if (requestCode == LOCATION_PERMISSION_REQUEST) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        getAndSendLocation();
      } else {
        sendLocationResult(false, null);
      }
    }
  }

  // ==================== Story Sharing ====================

  public void onWebAppShareToStory (String mediaUrl, @Nullable String text, @Nullable JSONObject widgetLink) {
    // Use Android share intent as fallback since full story editor is complex
    String shareText = text != null ? text : "";
    if (widgetLink != null) {
      String url = widgetLink.optString("url", "");
      String linkName = widgetLink.optString("name", "");
      if (!url.isEmpty()) {
        shareText = shareText.isEmpty() ? url : shareText + "\n" + url;
      }
    }
    Intents.shareText(shareText.isEmpty() ? mediaUrl : shareText);
  }

  // ==================== Device Storage (Bot API 9.0) ====================

  private SharedPreferences getDeviceStoragePrefs () {
    Args args = getArguments();
    long botId = args != null ? args.botUserId : 0;
    return context().getSharedPreferences("webapp_device_storage_" + botId, Context.MODE_PRIVATE);
  }

  public void onDeviceStorageSaveKey (String reqId, String key, @Nullable String value) {
    try {
      SharedPreferences prefs = getDeviceStoragePrefs();
      if (value == null) {
        prefs.edit().remove(key).apply();
      } else {
        prefs.edit().putString(key, value).apply();
      }
      sendEventToWebApp("device_storage_key_saved", "{\"req_id\":\"" + escapeJsonString(reqId) + "\"}");
    } catch (Exception e) {
      sendEventToWebApp("device_storage_failed", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"error\":\"" + escapeJsonString(e.getMessage()) + "\"}");
    }
  }

  public void onDeviceStorageGetKey (String reqId, String key) {
    try {
      SharedPreferences prefs = getDeviceStoragePrefs();
      String value = prefs.getString(key, null);
      if (value != null) {
        sendEventToWebApp("device_storage_key_received", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"value\":\"" + escapeJsonString(value) + "\"}");
      } else {
        sendEventToWebApp("device_storage_key_received", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"value\":null}");
      }
    } catch (Exception e) {
      sendEventToWebApp("device_storage_failed", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"error\":\"" + escapeJsonString(e.getMessage()) + "\"}");
    }
  }

  public void onDeviceStorageClear (String reqId) {
    try {
      getDeviceStoragePrefs().edit().clear().apply();
      sendEventToWebApp("device_storage_cleared", "{\"req_id\":\"" + escapeJsonString(reqId) + "\"}");
    } catch (Exception e) {
      sendEventToWebApp("device_storage_failed", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"error\":\"" + escapeJsonString(e.getMessage()) + "\"}");
    }
  }

  // ==================== Secure Storage (Bot API 9.0) ====================

  private SharedPreferences getSecureStoragePrefs () {
    Args args = getArguments();
    long botId = args != null ? args.botUserId : 0;
    return context().getSharedPreferences("webapp_secure_storage_" + botId, Context.MODE_PRIVATE);
  }

  public void onSecureStorageSaveKey (String reqId, String key, @Nullable String value) {
    try {
      SharedPreferences prefs = getSecureStoragePrefs();
      if (value == null) {
        prefs.edit().remove(key).apply();
      } else {
        prefs.edit().putString(key, value).apply();
      }
      sendEventToWebApp("secure_storage_key_saved", "{\"req_id\":\"" + escapeJsonString(reqId) + "\"}");
    } catch (Exception e) {
      sendEventToWebApp("secure_storage_failed", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"error\":\"" + escapeJsonString(e.getMessage()) + "\"}");
    }
  }

  public void onSecureStorageGetKey (String reqId, String key) {
    try {
      SharedPreferences prefs = getSecureStoragePrefs();
      String value = prefs.getString(key, null);
      if (value != null) {
        sendEventToWebApp("secure_storage_key_received", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"value\":\"" + escapeJsonString(value) + "\"}");
      } else {
        sendEventToWebApp("secure_storage_key_received", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"value\":null}");
      }
    } catch (Exception e) {
      sendEventToWebApp("secure_storage_failed", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"error\":\"" + escapeJsonString(e.getMessage()) + "\"}");
    }
  }

  public void onSecureStorageClear (String reqId) {
    try {
      getSecureStoragePrefs().edit().clear().apply();
      sendEventToWebApp("secure_storage_cleared", "{\"req_id\":\"" + escapeJsonString(reqId) + "\"}");
    } catch (Exception e) {
      sendEventToWebApp("secure_storage_failed", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"error\":\"" + escapeJsonString(e.getMessage()) + "\"}");
    }
  }

  public void onSecureStorageRestoreKey (String reqId, String key) {
    // Restore key requires biometric authentication — for now, same as get
    if (!biometryAccessGranted) {
      sendEventToWebApp("secure_storage_failed", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"error\":\"AUTH_REQUIRED\"}");
      return;
    }
    try {
      SharedPreferences prefs = getSecureStoragePrefs();
      String value = prefs.getString(key, null);
      if (value != null) {
        sendEventToWebApp("secure_storage_key_restored", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"value\":\"" + escapeJsonString(value) + "\"}");
      } else {
        sendEventToWebApp("secure_storage_key_restored", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"value\":null}");
      }
    } catch (Exception e) {
      sendEventToWebApp("secure_storage_failed", "{\"req_id\":\"" + escapeJsonString(reqId) + "\",\"error\":\"" + escapeJsonString(e.getMessage()) + "\"}");
    }
  }

  // ==================== QR Code Scanner ====================

  private boolean qrScannerOpen = false;

  public void onWebAppOpenQrScanner (String text) {
    if (qrScannerOpen) return;
    qrScannerOpen = true;

    String subtitle = (text != null && !text.isEmpty()) ? text : Lang.getString(R.string.ScanQRFullSubtitle);

    openInAppCamera(new CameraOpenOptions()
      .mode(CameraController.MODE_QR)
      .allowSystem(false)
      .ignoreAnchor(true)
      .qrModeSubtitleText(subtitle)
      .allowAnyQrCode(true)
      .qrCodeListener(qrCode -> {
        qrScannerOpen = false;
        sendEventToWebApp("qr_text_received", "{\"data\":\"" + escapeJsonString(qrCode) + "\"}");
      })
    );
  }

  public void onWebAppCloseQrScanner () {
    qrScannerOpen = false;
    // Camera will close automatically when result received or user backs out
  }

  // ==================== Device Sensors ====================

  private SensorManager sensorManager;
  private Sensor accelerometer;
  private Sensor gyroscope;
  private Sensor rotationVector;
  private boolean accelerometerStarted = false;
  private boolean gyroscopeStarted = false;
  private boolean deviceOrientationStarted = false;
  private boolean deviceOrientationAbsolute = false;

  private void initSensors () {
    sensorManager = (SensorManager) context().getSystemService(Context.SENSOR_SERVICE);
    if (sensorManager != null) {
      accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
      gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
      rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }
  }

  // Accelerometer
  public void onWebAppStartAccelerometer (int refreshRate) {
    if (sensorManager == null) initSensors();
    if (sensorManager == null || accelerometer == null) {
      sendEventToWebApp("accelerometer_failed", "{\"error\":\"UNSUPPORTED\"}");
      return;
    }
    if (accelerometerStarted) return;

    int delay = refreshRateToDelay(refreshRate);
    sensorManager.registerListener(this, accelerometer, delay);
    accelerometerStarted = true;
    sendEventToWebApp("accelerometer_started", "{}");
  }

  public void onWebAppStopAccelerometer () {
    if (!accelerometerStarted || sensorManager == null) return;
    sensorManager.unregisterListener(this, accelerometer);
    accelerometerStarted = false;
    sendEventToWebApp("accelerometer_stopped", "{}");
  }

  // Gyroscope
  public void onWebAppStartGyroscope (int refreshRate) {
    if (sensorManager == null) initSensors();
    if (sensorManager == null || gyroscope == null) {
      sendEventToWebApp("gyroscope_failed", "{\"error\":\"UNSUPPORTED\"}");
      return;
    }
    if (gyroscopeStarted) return;

    int delay = refreshRateToDelay(refreshRate);
    sensorManager.registerListener(this, gyroscope, delay);
    gyroscopeStarted = true;
    sendEventToWebApp("gyroscope_started", "{}");
  }

  public void onWebAppStopGyroscope () {
    if (!gyroscopeStarted || sensorManager == null) return;
    sensorManager.unregisterListener(this, gyroscope);
    gyroscopeStarted = false;
    sendEventToWebApp("gyroscope_stopped", "{}");
  }

  // Device Orientation
  public void onWebAppStartDeviceOrientation (boolean absolute, int refreshRate) {
    if (sensorManager == null) initSensors();
    if (sensorManager == null || rotationVector == null) {
      sendEventToWebApp("device_orientation_failed", "{\"error\":\"UNSUPPORTED\"}");
      return;
    }
    if (deviceOrientationStarted) return;

    deviceOrientationAbsolute = absolute;
    int delay = refreshRateToDelay(refreshRate);
    sensorManager.registerListener(this, rotationVector, delay);
    deviceOrientationStarted = true;
    sendEventToWebApp("device_orientation_started", "{}");
  }

  public void onWebAppStopDeviceOrientation () {
    if (!deviceOrientationStarted || sensorManager == null) return;
    sensorManager.unregisterListener(this, rotationVector);
    deviceOrientationStarted = false;
    sendEventToWebApp("device_orientation_stopped", "{}");
  }

  private int refreshRateToDelay (int refreshRate) {
    if (refreshRate <= 20) return SensorManager.SENSOR_DELAY_FASTEST;
    if (refreshRate <= 60) return SensorManager.SENSOR_DELAY_GAME;
    if (refreshRate <= 100) return SensorManager.SENSOR_DELAY_UI;
    return SensorManager.SENSOR_DELAY_NORMAL;
  }

  private void stopAllSensors () {
    if (accelerometerStarted) onWebAppStopAccelerometer();
    if (gyroscopeStarted) onWebAppStopGyroscope();
    if (deviceOrientationStarted) onWebAppStopDeviceOrientation();
  }

  // SensorEventListener implementation
  @Override
  public void onSensorChanged (SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      float x = event.values[0];
      float y = event.values[1];
      float z = event.values[2];
      sendEventToWebApp("accelerometer_changed", String.format(Locale.US, "{\"x\":%.6f,\"y\":%.6f,\"z\":%.6f}", x, y, z));
    } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
      float x = event.values[0];
      float y = event.values[1];
      float z = event.values[2];
      sendEventToWebApp("gyroscope_changed", String.format(Locale.US, "{\"x\":%.6f,\"y\":%.6f,\"z\":%.6f}", x, y, z));
    } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
      float[] rotationMatrix = new float[9];
      float[] orientation = new float[3];
      SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
      SensorManager.getOrientation(rotationMatrix, orientation);

      // Convert to degrees
      float alpha = (float) Math.toDegrees(orientation[0]); // Azimuth (compass)
      float beta = (float) Math.toDegrees(orientation[1]);  // Pitch
      float gamma = (float) Math.toDegrees(orientation[2]); // Roll

      sendEventToWebApp("device_orientation_changed",
        String.format(Locale.US, "{\"absolute\":%b,\"alpha\":%.2f,\"beta\":%.2f,\"gamma\":%.2f}",
          deviceOrientationAbsolute, alpha, beta, gamma));
    }
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int accuracy) {
    // Not needed for web apps
  }

  // ==================== Visibility Events (3.3) ====================

  @Override
  public void onFocus () {
    super.onFocus();
    sendEventToWebApp("visibility_changed", "{\"is_visible\":true}");
    sendEventToWebApp("activated", "{}");
  }

  @Override
  public void onBlur () {
    super.onBlur();
    sendEventToWebApp("visibility_changed", "{\"is_visible\":false}");
    sendEventToWebApp("deactivated", "{}");
  }

  // ==================== Safe Area Events (3.2) ====================

  public void onWebAppRequestSafeArea () {
    sendSafeAreaEvent();
  }

  public void onWebAppRequestContentSafeArea () {
    sendContentSafeAreaEvent();
  }

  private void sendSafeAreaData () {
    sendSafeAreaEvent();
    sendContentSafeAreaEvent();
  }

  private void sendSafeAreaEvent () {
    if (webView == null) return;
    BaseActivity activity = context();
    if (activity == null || activity.getWindow() == null) return;

    int top = 0, bottom = 0, left = 0, right = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      android.view.DisplayCutout cutout = activity.getWindow().getDecorView().getRootWindowInsets() != null
        ? activity.getWindow().getDecorView().getRootWindowInsets().getDisplayCutout()
        : null;
      if (cutout != null) {
        top = cutout.getSafeInsetTop();
        bottom = cutout.getSafeInsetBottom();
        left = cutout.getSafeInsetLeft();
        right = cutout.getSafeInsetRight();
      }
    }

    sendEventToWebApp("safe_area_changed",
      String.format(Locale.US, "{\"top\":%d,\"bottom\":%d,\"left\":%d,\"right\":%d}", top, bottom, left, right));
  }

  private void sendContentSafeAreaEvent () {
    if (webView == null) return;
    BaseActivity activity = context();
    if (activity == null || activity.getWindow() == null) return;

    int top = 0, bottom = 0, left = 0, right = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      android.view.DisplayCutout cutout = activity.getWindow().getDecorView().getRootWindowInsets() != null
        ? activity.getWindow().getDecorView().getRootWindowInsets().getDisplayCutout()
        : null;
      if (cutout != null) {
        top = cutout.getSafeInsetTop();
        bottom = cutout.getSafeInsetBottom();
        left = cutout.getSafeInsetLeft();
        right = cutout.getSafeInsetRight();
      }
    }

    // Content safe area includes the header when not fullscreen
    int contentTop = isFullscreen ? top : top + Size.getHeaderPortraitSize();
    sendEventToWebApp("content_safe_area_changed",
      String.format(Locale.US, "{\"top\":%d,\"bottom\":%d,\"left\":%d,\"right\":%d}", contentTop, bottom, left, right));
  }

  // ==================== Biometric Token & Settings (3.1) ====================

  private String biometricToken = null;

  public void onWebAppBiometryUpdateToken (String token) {
    // Store token in memory (secure storage would require Android Keystore integration)
    this.biometricToken = token;
    sendEventToWebApp("biometry_token_updated", "{\"status\":\"updated\"}");
  }

  public void onWebAppBiometryOpenSettings () {
    try {
      Intent intent;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        intent = new Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL);
      } else {
        intent = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
      }
      context().startActivity(intent);
    } catch (Exception e) {
      Log.e("Failed to open biometric settings: %s", e.getMessage());
    }
  }

  // ==================== Loading Placeholder (3.6) ====================

  public void loadPlaceholder () {
    Args args = getArguments();
    if (args == null || args.botUserId == 0) return;

    tdlib.send(new TdApi.GetWebAppPlaceholder(args.botUserId), (result, error) -> {
      // Placeholder is displayed while webview loads - on web_app_ready we would hide it
      // Since rendering SVG paths from Outline requires complex path drawing, we skip the visual
      // and just use the loading spinner that WebkitController already provides
      if (error == null) {
        Log.i("WebApp placeholder available for bot %d", args.botUserId);
      }
    });
  }

  // ==================== Prepared Message (3.5) ====================

  public void sendPreparedMessageResult (String status) {
    sendEventToWebApp("prepared_message_sent", "{\"status\":\"" + status + "\"}");
  }

  // ==================== ThemeChangeListener ====================

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, @Nullable ColorState state) {
    sendThemeChangedEvent();
  }

  public void onWebAppRequestTheme () {
    sendThemeChangedEvent();
  }

  private void sendThemeChangedEvent () {
    if (webView == null) return;
    String themeParamsJson = buildThemeParamsJson();
    sendEventToWebApp("theme_changed", themeParamsJson);
  }

  private String buildThemeParamsJson () {
    TdApi.ThemeParameters params = getThemeParameters();
    return String.format(Locale.US,
      "{\"bg_color\":\"#%06X\"," +
      "\"secondary_bg_color\":\"#%06X\"," +
      "\"header_bg_color\":\"#%06X\"," +
      "\"bottom_bar_bg_color\":\"#%06X\"," +
      "\"section_bg_color\":\"#%06X\"," +
      "\"section_separator_color\":\"#%06X\"," +
      "\"text_color\":\"#%06X\"," +
      "\"accent_text_color\":\"#%06X\"," +
      "\"section_header_text_color\":\"#%06X\"," +
      "\"subtitle_text_color\":\"#%06X\"," +
      "\"destructive_text_color\":\"#%06X\"," +
      "\"hint_color\":\"#%06X\"," +
      "\"link_color\":\"#%06X\"," +
      "\"button_color\":\"#%06X\"," +
      "\"button_text_color\":\"#%06X\"}",
      params.backgroundColor & 0xFFFFFF,
      params.secondaryBackgroundColor & 0xFFFFFF,
      params.headerBackgroundColor & 0xFFFFFF,
      params.bottomBarBackgroundColor & 0xFFFFFF,
      params.sectionBackgroundColor & 0xFFFFFF,
      params.sectionSeparatorColor & 0xFFFFFF,
      params.textColor & 0xFFFFFF,
      params.accentTextColor & 0xFFFFFF,
      params.sectionHeaderTextColor & 0xFFFFFF,
      params.subtitleTextColor & 0xFFFFFF,
      params.destructiveTextColor & 0xFFFFFF,
      params.hintColor & 0xFFFFFF,
      params.linkColor & 0xFFFFFF,
      params.buttonColor & 0xFFFFFF,
      params.buttonTextColor & 0xFFFFFF
    );
  }

  // ==================== WebAppMessageSent ====================

  public void onWebAppMessageSent (long launchId) {
    Args args = getArguments();
    if (args != null && args.launchId == launchId) {
      UI.post(() -> {
        sendEventToWebApp("web_app_data_sent", "{}");
        navigateBack();
      });
    }
  }

  // ==================== Helper Methods ====================

  private void sendEventToWebApp (String eventName, String eventData) {
    if (webView == null) return;
    String js = String.format(
      "if (window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.onEvent) {" +
      "  Telegram.WebApp.onEvent('%s', %s);" +
      "}",
      eventName, eventData
    );
    webView.evaluateJavascript(js, null);
  }

  private String escapeJsonString (String str) {
    if (str == null) return "";
    return str.replace("\\", "\\\\")
              .replace("\"", "\\\"")
              .replace("\n", "\\n")
              .replace("\r", "\\r")
              .replace("\t", "\\t");
  }

  public TdApi.ThemeParameters getThemeParameters () {
    TdApi.ThemeParameters params = new TdApi.ThemeParameters();
    params.backgroundColor = Theme.getColor(ColorId.filling) & 0xFFFFFF;
    params.secondaryBackgroundColor = Theme.getColor(ColorId.fillingPositive) & 0xFFFFFF;
    params.headerBackgroundColor = Theme.getColor(ColorId.headerBackground) & 0xFFFFFF;
    params.bottomBarBackgroundColor = Theme.getColor(ColorId.headerBackground) & 0xFFFFFF;
    params.sectionBackgroundColor = Theme.getColor(ColorId.filling) & 0xFFFFFF;
    params.sectionSeparatorColor = Theme.getColor(ColorId.separator) & 0xFFFFFF;
    params.textColor = Theme.getColor(ColorId.text) & 0xFFFFFF;
    params.accentTextColor = Theme.getColor(ColorId.textLink) & 0xFFFFFF;
    params.sectionHeaderTextColor = Theme.getColor(ColorId.textLight) & 0xFFFFFF;
    params.subtitleTextColor = Theme.getColor(ColorId.textLight) & 0xFFFFFF;
    params.destructiveTextColor = Theme.getColor(ColorId.textNegative) & 0xFFFFFF;
    params.hintColor = Theme.getColor(ColorId.textPlaceholder) & 0xFFFFFF;
    params.linkColor = Theme.getColor(ColorId.textLink) & 0xFFFFFF;
    params.buttonColor = Theme.getColor(ColorId.fillingPositive) & 0xFFFFFF;
    params.buttonTextColor = Theme.getColor(ColorId.fillingPositiveContent) & 0xFFFFFF;
    return params;
  }

  // ==================== Main Button View ====================

  private class MainButtonView extends FrameLayout {
    private final Paint backgroundPaint;
    private final Paint textPaint;
    private final ProgressComponent progress;

    private String text = "";
    private int backgroundColor = 0xFF007AFF;
    private int textColor = 0xFFFFFFFF;
    private boolean active = true;
    private boolean progressVisible = false;

    public MainButtonView (Context context) {
      super(context);
      setWillNotDraw(false);

      backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      backgroundPaint.setColor(backgroundColor);

      textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      textPaint.setTypeface(Fonts.getRobotoMedium());
      textPaint.setTextSize(Screen.dp(15f));
      textPaint.setColor(textColor);
      textPaint.setTextAlign(Paint.Align.CENTER);

      progress = new ProgressComponent(UI.getContext(context), Screen.dp(8f));
      progress.setUseLargerPaint(Screen.dp(2f));
      progress.forceColor(textColor);
    }

    public void setText (String text) {
      this.text = text != null ? text : "";
      invalidate();
    }

    public void setButtonColors (int bgColor, int txtColor) {
      this.backgroundColor = bgColor;
      this.textColor = txtColor;
      backgroundPaint.setColor(bgColor);
      textPaint.setColor(txtColor);
      progress.forceColor(txtColor);
      invalidate();
    }

    public void setActive (boolean active) {
      this.active = active;
      setAlpha(active ? 1f : 0.6f);
    }

    public void setProgressVisible (boolean visible) {
      if (this.progressVisible != visible) {
        this.progressVisible = visible;
        if (visible) {
          progress.attachToView(this);
        } else {
          progress.detachFromView(this);
        }
        invalidate();
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      int progressSize = Screen.dp(20f);
      int centerX = getMeasuredWidth() / 2;
      int centerY = getMeasuredHeight() / 2;
      progress.setBounds(
        centerX - progressSize / 2,
        centerY - progressSize / 2,
        centerX + progressSize / 2,
        centerY + progressSize / 2
      );
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);

      // Draw background
      canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

      if (progressVisible) {
        // Draw progress spinner
        progress.draw(canvas);
      } else {
        // Draw text
        float textY = getHeight() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(text.toUpperCase(), getWidth() / 2f, textY, textPaint);
      }
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      if (progressVisible) {
        progress.attachToView(this);
      }
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      progress.detachFromView(this);
    }
  }

}
