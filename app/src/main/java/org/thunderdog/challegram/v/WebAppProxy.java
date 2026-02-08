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
package org.thunderdog.challegram.v;

import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.WebAppController;

/**
 * JavaScript interface for Telegram Web Apps.
 * This is injected into WebView as "TelegramWebviewProxy" and handles
 * postEvent calls from the Web App JavaScript API.
 *
 * Events reference: https://core.telegram.org/bots/webapps
 */
@SuppressWarnings("unused")
public final class WebAppProxy {
  private final WebAppController controller;

  public WebAppProxy (WebAppController controller) {
    this.controller = controller;
  }

  @JavascriptInterface
  public void postEvent (final String eventName, final String eventData) {
    Log.i("WebApp postEvent: %s, data: %s", eventName, eventData);
    UI.post(() -> handleEvent(eventName, eventData));
  }

  private void handleEvent (String eventName, String eventData) {
    try {
      switch (eventName) {
        // ==================== Core Events ====================
        case "web_app_ready":
          controller.onWebAppReady();
          break;

        case "web_app_close": {
          boolean returnBack = false;
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            returnBack = data.optBoolean("return_back", false);
          }
          controller.onWebAppClose(returnBack);
          break;
        }

        case "web_app_data_send":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String sendData = data.optString("data", "");
            controller.onWebAppSendData(sendData);
          }
          break;

        // ==================== Link Events ====================
        case "web_app_open_link":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String url = data.optString("url", "");
            boolean tryInstantView = data.optBoolean("try_instant_view", false);
            String tryBrowser = data.optString("try_browser", null);
            if (!url.isEmpty()) {
              controller.onWebAppOpenLink(url, tryInstantView, tryBrowser);
            }
          }
          break;

        case "web_app_open_tg_link":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String pathFull = data.optString("path_full", "");
            boolean forceRequest = data.optBoolean("force_request", false);
            if (!pathFull.isEmpty()) {
              controller.onWebAppOpenTgLink("https://t.me" + pathFull, forceRequest);
            }
          }
          break;

        // ==================== Permission Events ====================
        case "web_app_request_phone":
          controller.onWebAppRequestPhone();
          break;

        case "web_app_request_write_access":
          controller.onWebAppRequestWriteAccess();
          break;

        // ==================== Main Button Events ====================
        case "web_app_setup_main_button":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            boolean visible = data.optBoolean("is_visible", false);
            String text = data.optString("text", "");
            String colorStr = data.optString("color", "#007AFF");
            String textColorStr = data.optString("text_color", "#FFFFFF");
            boolean isActive = data.optBoolean("is_active", true);
            boolean isProgressVisible = data.optBoolean("is_progress_visible", false);
            boolean hasShineEffect = data.optBoolean("has_shine_effect", false);
            int color = parseColor(colorStr);
            int textColor = parseColor(textColorStr);
            controller.onWebAppSetMainButton(visible, text, color, textColor, isActive, isProgressVisible, hasShineEffect);
          }
          break;

        // ==================== Secondary Button Events ====================
        case "web_app_setup_secondary_button":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            boolean visible = data.optBoolean("is_visible", false);
            String text = data.optString("text", "");
            String colorStr = data.optString("color", "#007AFF");
            String textColorStr = data.optString("text_color", "#FFFFFF");
            boolean isActive = data.optBoolean("is_active", true);
            boolean isProgressVisible = data.optBoolean("is_progress_visible", false);
            boolean hasShineEffect = data.optBoolean("has_shine_effect", false);
            String position = data.optString("position", "left");
            int color = parseColor(colorStr);
            int textColor = parseColor(textColorStr);
            controller.onWebAppSetSecondaryButton(visible, text, color, textColor, isActive, isProgressVisible, hasShineEffect, position);
          }
          break;

        // ==================== Back Button Events ====================
        case "web_app_setup_back_button":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            boolean visible = data.optBoolean("is_visible", false);
            controller.onWebAppSetBackButton(visible);
          }
          break;

        // ==================== Settings Button Events ====================
        case "web_app_setup_settings_button":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            boolean visible = data.optBoolean("is_visible", false);
            controller.onWebAppSetSettingsButton(visible);
          }
          break;

        // ==================== Closing & Swipe Behavior ====================
        case "web_app_setup_closing_behavior":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            boolean needConfirmation = data.optBoolean("need_confirmation", false);
            controller.onWebAppSetClosingBehavior(needConfirmation);
          }
          break;

        case "web_app_setup_swipe_behavior":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            boolean allowVerticalSwipe = data.optBoolean("allow_vertical_swipe", true);
            controller.onWebAppSetSwipeBehavior(allowVerticalSwipe);
          }
          break;

        // ==================== Haptic Feedback ====================
        case "web_app_trigger_haptic_feedback":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String type = data.optString("type", "");
            String style = "";
            if (type.equals("impact")) {
              style = data.optString("impact_style", "medium");
            } else if (type.equals("notification")) {
              style = data.optString("notification_type", "success");
            }
            controller.onWebAppHapticFeedback(type, style);
          }
          break;

        // ==================== Popup Events ====================
        case "web_app_open_popup":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String title = data.optString("title", "");
            String message = data.optString("message", "");
            JSONArray buttons = data.optJSONArray("buttons");
            controller.onWebAppShowPopup(title, message, buttons);
          }
          break;

        case "web_app_show_alert":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String message = data.optString("message", "");
            controller.onWebAppShowAlert(message, null);
          }
          break;

        case "web_app_show_confirm":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String message = data.optString("message", "");
            controller.onWebAppShowConfirm(message, null);
          }
          break;

        // ==================== Viewport Events ====================
        case "web_app_request_viewport":
          controller.onWebAppRequestViewport();
          break;

        case "web_app_expand":
          controller.onWebAppExpand();
          break;

        case "web_app_request_fullscreen":
          controller.onWebAppRequestFullscreen();
          break;

        case "web_app_exit_fullscreen":
          controller.onWebAppExitFullscreen();
          break;

        case "web_app_toggle_orientation_lock":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            boolean locked = data.optBoolean("locked", false);
            controller.onWebAppToggleOrientationLock(locked);
          }
          break;

        // ==================== Theme Color Events ====================
        case "web_app_set_header_color":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String color = data.optString("color", data.optString("color_key", ""));
            controller.onWebAppSetHeaderColor(color);
          }
          break;

        case "web_app_set_background_color":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String color = data.optString("color", "");
            controller.onWebAppSetBackgroundColor(color);
          }
          break;

        case "web_app_set_bottom_bar_color":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String color = data.optString("color", "");
            controller.onWebAppSetBottomBarColor(color);
          }
          break;

        // ==================== Theme Request ====================
        case "web_app_request_theme":
          controller.onWebAppRequestTheme();
          break;

        // ==================== Invoice Events ====================
        case "web_app_open_invoice":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String slug = data.optString("slug", "");
            if (!slug.isEmpty()) {
              controller.onWebAppOpenInvoice(slug);
            }
          }
          break;

        // ==================== Switch Inline Query ====================
        case "web_app_switch_inline_query":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String query = data.optString("query", "");
            JSONArray chatTypes = data.optJSONArray("chat_types");
            controller.onWebAppSwitchInlineQuery(query, chatTypes);
          }
          break;

        // ==================== Clipboard Events ====================
        case "web_app_read_text_from_clipboard":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            controller.onWebAppReadClipboard(reqId);
          }
          break;

        // ==================== QR Scanner Events ====================
        case "web_app_open_scan_qr_popup":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String text = data.optString("text", "");
            controller.onWebAppOpenQrScanner(text);
          } else {
            controller.onWebAppOpenQrScanner(null);
          }
          break;

        case "web_app_close_scan_qr_popup":
          controller.onWebAppCloseQrScanner();
          break;

        // ==================== Biometric Events ====================
        case "web_app_biometry_get_info":
          controller.onWebAppBiometryGetInfo();
          break;

        case "web_app_biometry_request_access":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reason = data.optString("reason", "");
            controller.onWebAppBiometryRequestAccess(reason);
          } else {
            controller.onWebAppBiometryRequestAccess(null);
          }
          break;

        case "web_app_biometry_request_auth":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reason = data.optString("reason", "");
            controller.onWebAppBiometryAuthenticate(reason);
          } else {
            controller.onWebAppBiometryAuthenticate(null);
          }
          break;

        case "web_app_biometry_update_token":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String token = data.optString("token", "");
            controller.onWebAppBiometryUpdateToken(token);
          }
          break;

        case "web_app_biometry_open_settings":
          controller.onWebAppBiometryOpenSettings();
          break;

        // ==================== Keyboard Events ====================
        case "web_app_hide_keyboard":
          controller.onWebAppHideKeyboard();
          break;

        // ==================== Cloud Storage Events ====================
        case "web_app_invoke_custom_method":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            String method = data.optString("method", "");
            String params = data.optString("params", "{}");
            controller.onWebAppInvokeCustomMethod(reqId, method, params);
          }
          break;

        // ==================== Accelerometer/Gyroscope Events ====================
        case "web_app_start_accelerometer":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            int refreshRate = data.optInt("refresh_rate", 200);
            controller.onWebAppStartAccelerometer(refreshRate);
          } else {
            controller.onWebAppStartAccelerometer(200);
          }
          break;

        case "web_app_stop_accelerometer":
          controller.onWebAppStopAccelerometer();
          break;

        case "web_app_start_gyroscope":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            int refreshRate = data.optInt("refresh_rate", 200);
            controller.onWebAppStartGyroscope(refreshRate);
          } else {
            controller.onWebAppStartGyroscope(200);
          }
          break;

        case "web_app_stop_gyroscope":
          controller.onWebAppStopGyroscope();
          break;

        case "web_app_start_device_orientation":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            int refreshRate = data.optInt("refresh_rate", 200);
            boolean needAbsolute = data.optBoolean("need_absolute", false);
            controller.onWebAppStartDeviceOrientation(needAbsolute, refreshRate);
          } else {
            controller.onWebAppStartDeviceOrientation(false, 200);
          }
          break;

        case "web_app_stop_device_orientation":
          controller.onWebAppStopDeviceOrientation();
          break;

        // ==================== Safe Area Events ====================
        case "web_app_request_safe_area":
          controller.onWebAppRequestSafeArea();
          break;

        case "web_app_request_content_safe_area":
          controller.onWebAppRequestContentSafeArea();
          break;

        // ==================== Location Events ====================
        case "web_app_check_location":
          controller.onWebAppCheckLocation();
          break;

        case "web_app_request_location":
          controller.onWebAppRequestLocation();
          break;

        case "web_app_open_location_settings":
          controller.onWebAppOpenLocationSettings();
          break;

        // ==================== Share Events ====================
        case "web_app_share_to_story":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String mediaUrl = data.optString("media_url", "");
            String storyText = data.optString("text", null);
            JSONObject widgetLink = data.optJSONObject("widget_link");
            if (!mediaUrl.isEmpty()) {
              controller.onWebAppShareToStory(mediaUrl, storyText, widgetLink);
            }
          }
          break;

        // ==================== Emoji Status Events ====================
        case "web_app_request_emoji_status_access":
          controller.onWebAppRequestEmojiStatusAccess();
          break;

        case "web_app_set_emoji_status":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            // Spec says custom_emoji_id is a string; parse robustly
            long customEmojiId = 0;
            try {
              customEmojiId = Long.parseLong(data.optString("custom_emoji_id", "0"));
            } catch (NumberFormatException ignored) {
              customEmojiId = data.optLong("custom_emoji_id", 0);
            }
            int duration = data.optInt("duration", 0);
            controller.onWebAppSetEmojiStatus(customEmojiId, duration);
          }
          break;

        // ==================== Home Screen Events ====================
        case "web_app_check_home_screen":
          controller.onWebAppCheckHomeScreen();
          break;

        case "web_app_add_to_home_screen":
          controller.onWebAppAddToHomeScreen();
          break;

        // ==================== File Download Events ====================
        case "web_app_request_file_download":
        case "web_app_download_file":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String url = data.optString("url", "");
            String fileName = data.has("filename") ? data.optString("filename", "") : data.optString("file_name", "");
            if (!url.isEmpty() && !fileName.isEmpty()) {
              controller.onWebAppRequestFileDownload(url, fileName);
            }
          }
          break;

        // ==================== Device Storage Events ====================
        case "web_app_device_storage_save_key":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            String key = data.optString("key", "");
            String value = data.isNull("value") ? null : data.optString("value", null);
            controller.onDeviceStorageSaveKey(reqId, key, value);
          }
          break;

        case "web_app_device_storage_get_key":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            String key = data.optString("key", "");
            controller.onDeviceStorageGetKey(reqId, key);
          }
          break;

        case "web_app_device_storage_clear":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            controller.onDeviceStorageClear(reqId);
          }
          break;

        // ==================== Secure Storage Events ====================
        case "web_app_secure_storage_save_key":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            String key = data.optString("key", "");
            String value = data.isNull("value") ? null : data.optString("value", null);
            controller.onSecureStorageSaveKey(reqId, key, value);
          }
          break;

        case "web_app_secure_storage_get_key":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            String key = data.optString("key", "");
            controller.onSecureStorageGetKey(reqId, key);
          }
          break;

        case "web_app_secure_storage_clear":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            controller.onSecureStorageClear(reqId);
          }
          break;

        case "web_app_secure_storage_restore_key":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String reqId = data.optString("req_id", "");
            String key = data.optString("key", "");
            controller.onSecureStorageRestoreKey(reqId, key);
          }
          break;

        // ==================== Share Message Events ====================
        case "web_app_share_message":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String msgId = data.optString("msg_id", "");
            controller.onWebAppShareMessage(msgId);
          }
          break;

        // ==================== Prepared Message Events ====================
        case "web_app_send_prepared_message":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String preparedMessageId = data.optString("id", "");
            // TDLib does not yet have SendPreparedMessage API
            Log.w("WebApp send prepared message not yet supported by TDLib: %s", preparedMessageId);
            controller.sendPreparedMessageResult(null, "UNSUPPORTED");
          }
          break;

        // ==================== Unknown Events ====================
        default:
          Log.w("Unknown WebApp event: %s", eventName);
          break;
      }
    } catch (JSONException e) {
      Log.e("Failed to parse WebApp event data for %s: %s", eventName, e.getMessage());
    }
  }

  private int parseColor (String colorStr) {
    try {
      if (colorStr == null || colorStr.isEmpty()) {
        return 0xFF007AFF; // Default button color
      }
      if (colorStr.startsWith("#")) {
        String hex = colorStr.substring(1);
        // Handle both #RGB and #RRGGBB formats
        if (hex.length() == 3) {
          char r = hex.charAt(0);
          char g = hex.charAt(1);
          char b = hex.charAt(2);
          hex = "" + r + r + g + g + b + b;
        }
        return (int) Long.parseLong(hex, 16) | 0xFF000000;
      }
      return 0xFF007AFF;
    } catch (NumberFormatException e) {
      return 0xFF007AFF;
    }
  }
}
