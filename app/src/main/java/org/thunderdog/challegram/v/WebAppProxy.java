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

        case "web_app_close":
          controller.onWebAppClose();
          break;

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
            if (!url.isEmpty()) {
              controller.onWebAppOpenLink(url);
            }
          }
          break;

        case "web_app_open_tg_link":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String pathFull = data.optString("path_full", "");
            if (!pathFull.isEmpty()) {
              controller.onWebAppOpenLink("https://t.me" + pathFull);
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
            int color = parseColor(colorStr);
            int textColor = parseColor(textColorStr);
            controller.onWebAppSetMainButton(visible, text, color, textColor, isActive, isProgressVisible);
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
          // TODO: Implement fullscreen (requires more significant UI changes)
          controller.onWebAppExpand();
          break;

        case "web_app_exit_fullscreen":
          // TODO: Implement exit fullscreen
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
          // Theme is provided via URL parameters, no specific action needed
          // The WebApp JS SDK handles this automatically
          break;

        // ==================== Invoice Events ====================
        case "web_app_open_invoice":
          if (eventData != null) {
            JSONObject data = new JSONObject(eventData);
            String slug = data.optString("slug", "");
            if (!slug.isEmpty()) {
              // Open invoice link
              controller.onWebAppOpenLink("https://t.me/$" + slug);
            }
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
          // Token storage not implemented - would need secure storage
          Log.w("WebApp biometry token storage not implemented");
          break;

        case "web_app_biometry_open_settings":
          // Open system biometric settings
          Log.w("WebApp biometry settings not implemented");
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

        // ==================== Location Events ====================
        case "web_app_request_location":
          // TODO: Implement location access
          Log.w("WebApp location request not yet implemented");
          break;

        // ==================== Share Events ====================
        case "web_app_share_to_story":
          // TODO: Implement story sharing
          Log.w("WebApp share to story not yet implemented");
          break;

        // ==================== Emoji Status Events ====================
        case "web_app_request_emoji_status_access":
        case "web_app_set_emoji_status":
          // TODO: Implement emoji status
          Log.w("WebApp emoji status events not yet implemented: %s", eventName);
          break;

        // ==================== Home Screen Events ====================
        case "web_app_check_home_screen":
        case "web_app_add_to_home_screen":
          // TODO: Implement home screen shortcuts
          Log.w("WebApp home screen events not yet implemented: %s", eventName);
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
