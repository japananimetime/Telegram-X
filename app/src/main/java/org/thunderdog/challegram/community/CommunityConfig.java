/*
 * Community features configuration
 * Adapted from moeGramX (https://github.com/moeCrafters/moeGramX)
 * Licensed under GPLv3
 */
package org.thunderdog.challegram.community;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import org.drinkmore.Tracer;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.leveldb.LevelDB;

/**
 * Configuration storage for community features.
 * All features are opt-in and disabled by default.
 */
public class CommunityConfig {

  private static final int VERSION = 1;
  private static final AtomicBoolean hasInstance = new AtomicBoolean(false);
  private static volatile CommunityConfig instance;
  private final LevelDB config;

  // Settings keys
  private static final String KEY_VERSION = "version";
  public static final String KEY_HIDE_PHONE_NUMBER = "hide_phone_number";
  public static final String KEY_SQUARE_AVATARS = "square_avatars";
  public static final String KEY_DISABLE_REACTIONS = "disable_reactions";
  public static final String KEY_HIDE_STICKER_TIMESTAMP = "hide_sticker_timestamp";
  public static final String KEY_SHOW_ID_IN_PROFILE = "show_id_profile";
  public static final String KEY_BLUR_DRAWER = "blur_drawer";
  public static final String KEY_REMEMBER_SEND_OPTIONS = "remember_send_options";
  public static final String KEY_LAST_SILENT_MODE = "last_silent_mode"; // For remember send options
  public static final String KEY_DISABLE_CAMERA_BUTTON = "disable_camera_button";
  public static final String KEY_DISABLE_RECORD_BUTTON = "disable_record_button";
  public static final String KEY_ROUNDED_STICKERS = "rounded_stickers";

  // Cached values for performance (all default to false = disabled)
  public static boolean hidePhoneNumber = false;
  public static boolean squareAvatars = false;
  public static boolean disableReactions = false;
  public static boolean hideStickerTimestamp = false;
  public static boolean showIdInProfile = false;
  public static boolean blurDrawer = false;
  public static boolean rememberSendOptions = false;
  public static boolean disableCameraButton = false;
  public static boolean disableRecordButton = false;
  public static boolean roundedStickers = false;

  private CommunityConfig () {
    File configDir = new File(UI.getAppContext().getFilesDir(), "community_config");
    if (!configDir.exists() && !configDir.mkdir()) {
      throw new IllegalStateException("Unable to create community config directory");
    }
    long ms = SystemClock.uptimeMillis();
    config = new LevelDB(new File(configDir, "db").getPath(), true, new LevelDB.ErrorHandler() {
      @Override
      public boolean onFatalError (LevelDB levelDB, Throwable error) {
        Tracer.onDatabaseError(error);
        return true;
      }

      @Override
      public void onError (LevelDB levelDB, String message, @Nullable Throwable error) {
        android.util.Log.e(Log.LOG_TAG, message, error);
      }
    });

    int configVersion = 0;
    try {
      configVersion = Math.max(0, config.tryGetInt(KEY_VERSION));
    } catch (FileNotFoundException ignored) {
    }

    if (configVersion > VERSION) {
      Log.e("Downgrading community config version: %d -> %d", configVersion, VERSION);
      config.putInt(KEY_VERSION, VERSION);
    }

    // Load cached values
    loadCachedValues();

    Log.i("Opened community config in %dms", SystemClock.uptimeMillis() - ms);
  }

  private void loadCachedValues () {
    hidePhoneNumber = getBoolean(KEY_HIDE_PHONE_NUMBER, false);
    squareAvatars = getBoolean(KEY_SQUARE_AVATARS, false);
    disableReactions = getBoolean(KEY_DISABLE_REACTIONS, false);
    hideStickerTimestamp = getBoolean(KEY_HIDE_STICKER_TIMESTAMP, false);
    showIdInProfile = getBoolean(KEY_SHOW_ID_IN_PROFILE, false);
    blurDrawer = getBoolean(KEY_BLUR_DRAWER, false);
    rememberSendOptions = getBoolean(KEY_REMEMBER_SEND_OPTIONS, false);
    disableCameraButton = getBoolean(KEY_DISABLE_CAMERA_BUTTON, false);
    disableRecordButton = getBoolean(KEY_DISABLE_RECORD_BUTTON, false);
    roundedStickers = getBoolean(KEY_ROUNDED_STICKERS, false);
  }

  public static CommunityConfig instance () {
    if (instance == null) {
      synchronized (CommunityConfig.class) {
        if (instance == null) {
          if (hasInstance.getAndSet(true)) {
            throw new AssertionError();
          }
          instance = new CommunityConfig();
        }
      }
    }
    return instance;
  }

  public LevelDB edit () {
    return config.edit();
  }

  public void putBoolean (String key, boolean value) {
    config.putBoolean(key, value);
    // Update cached value
    switch (key) {
      case KEY_HIDE_PHONE_NUMBER:
        hidePhoneNumber = value;
        break;
      case KEY_SQUARE_AVATARS:
        squareAvatars = value;
        break;
      case KEY_DISABLE_REACTIONS:
        disableReactions = value;
        break;
      case KEY_HIDE_STICKER_TIMESTAMP:
        hideStickerTimestamp = value;
        break;
      case KEY_SHOW_ID_IN_PROFILE:
        showIdInProfile = value;
        break;
      case KEY_BLUR_DRAWER:
        blurDrawer = value;
        break;
      case KEY_REMEMBER_SEND_OPTIONS:
        rememberSendOptions = value;
        break;
      case KEY_DISABLE_CAMERA_BUTTON:
        disableCameraButton = value;
        break;
      case KEY_DISABLE_RECORD_BUTTON:
        disableRecordButton = value;
        break;
      case KEY_ROUNDED_STICKERS:
        roundedStickers = value;
        break;
    }
  }

  public boolean getBoolean (String key, boolean defValue) {
    return config.getBoolean(key, defValue);
  }

  public void putInt (String key, int value) {
    config.putInt(key, value);
  }

  public int getInt (String key, int defValue) {
    return config.getInt(key, defValue);
  }

  public void putString (String key, String value) {
    config.putString(key, value);
  }

  public String getString (String key, String defValue) {
    return config.getString(key, defValue);
  }

  public void remove (String key) {
    config.remove(key);
  }

  // Helper methods for remember send options
  public static boolean getLastSilentMode () {
    return instance().getBoolean(KEY_LAST_SILENT_MODE, false);
  }

  public static void setLastSilentMode (boolean silent) {
    instance().putBoolean(KEY_LAST_SILENT_MODE, silent);
  }
}
