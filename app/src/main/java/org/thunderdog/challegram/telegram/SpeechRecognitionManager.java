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
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manager for speech recognition providers.
 *
 * This singleton manages:
 * - Available speech recognition providers
 * - Active provider selection
 * - Trial usage tracking for TDLib provider (non-Premium users)
 * - Persistent storage of provider configurations
 */
public class SpeechRecognitionManager {

  private static final String TAG = "SpeechRecognitionMgr";

  // Settings keys
  private static final String KEY_PROVIDERS = "speech_providers";
  private static final String KEY_ACTIVE_PROVIDER = "speech_active_provider";
  private static final String KEY_AUTO_TRANSCRIBE = "speech_auto_transcribe";
  private static final String KEY_PREFERRED_LANGUAGE = "speech_preferred_language";

  // Default TDLib provider ID
  public static final String TDLIB_PROVIDER_ID = "tdlib";

  private static volatile SpeechRecognitionManager instance;

  public static SpeechRecognitionManager instance () {
    if (instance == null) {
      synchronized (SpeechRecognitionManager.class) {
        if (instance == null) {
          instance = new SpeechRecognitionManager();
        }
      }
    }
    return instance;
  }

  // Built-in TDLib provider
  private final TdlibSpeechRecognitionProvider tdlibProvider;

  // Currently active provider
  private SpeechRecognitionProvider activeProvider;

  // Trial tracking for non-Premium users (from UpdateSpeechRecognitionTrial)
  private int maxMediaDuration = 0;  // Max voice/video note duration in seconds
  private int weeklyCount = 0;       // Total allowed per week
  private int leftCount = 0;         // Remaining attempts this week
  private int nextResetDate = 0;     // Unix timestamp when trials reset

  // All registered providers
  private final Map<String, SpeechRecognitionProvider> providers = new HashMap<>();

  // Provider configurations (for persistence)
  private final Map<String, SpeechProviderConfig> providerConfigs = new HashMap<>();

  // Settings
  private boolean autoTranscribe = false;
  private String preferredLanguage = null;

  private SpeechRecognitionManager () {
    tdlibProvider = new TdlibSpeechRecognitionProvider();
    registerProvider(tdlibProvider);
    activeProvider = tdlibProvider;

    // Load saved configurations
    loadProviderConfigs();
    loadSettings();
  }

  // --- Provider Registration ---

  /**
   * Register a speech recognition provider.
   * @param provider The provider to register
   */
  public void registerProvider (@NonNull SpeechRecognitionProvider provider) {
    providers.put(provider.getId(), provider);
  }

  /**
   * Unregister a provider.
   * @param providerId The provider ID to unregister
   */
  public void unregisterProvider (@NonNull String providerId) {
    if (!providerId.equals(TDLIB_PROVIDER_ID)) {
      providers.remove(providerId);
      providerConfigs.remove(providerId);
      saveProviderConfigs();

      // If active provider was removed, switch to TDLib
      if (activeProvider != null && activeProvider.getId().equals(providerId)) {
        setActiveProvider(TDLIB_PROVIDER_ID);
      }
    }
  }

  /**
   * Get the currently active provider.
   */
  @NonNull
  public SpeechRecognitionProvider getActiveProvider () {
    return activeProvider;
  }

  /**
   * Get the TDLib provider specifically (for handling message updates).
   */
  @NonNull
  public TdlibSpeechRecognitionProvider getTdlibProvider () {
    return tdlibProvider;
  }

  /**
   * Set the active provider by ID.
   * @param providerId The provider ID to activate
   * @return true if provider was found and set
   */
  public boolean setActiveProvider (@NonNull String providerId) {
    SpeechRecognitionProvider provider = providers.get(providerId);
    if (provider != null) {
      activeProvider = provider;
      Settings.instance().putString(KEY_ACTIVE_PROVIDER, providerId);
      return true;
    }
    return false;
  }

  /**
   * Get a provider by ID.
   */
  @Nullable
  public SpeechRecognitionProvider getProvider (@NonNull String providerId) {
    return providers.get(providerId);
  }

  /**
   * Get all registered providers.
   */
  @NonNull
  public Collection<SpeechRecognitionProvider> getAllProviders () {
    return providers.values();
  }

  /**
   * Get all provider configurations (including those not yet instantiated).
   */
  @NonNull
  public Collection<SpeechProviderConfig> getAllProviderConfigs () {
    return providerConfigs.values();
  }

  /**
   * Get count of custom (non-TDLib) providers.
   */
  public int getCustomProviderCount () {
    return providers.size() - 1; // Subtract TDLib
  }

  // --- Provider Configuration ---

  /**
   * Add or update a custom provider configuration.
   * Creates the appropriate provider instance based on config type.
   *
   * @param config The provider configuration
   * @return The created/updated provider, or null on error
   */
  @Nullable
  public SpeechRecognitionProvider addProviderConfig (@NonNull SpeechProviderConfig config) {
    if (!config.isValid()) {
      Log.e(TAG, "Invalid provider config: " + config.getId());
      return null;
    }

    // Create provider instance based on type
    SpeechRecognitionProvider provider = createProviderFromConfig(config);
    if (provider == null) {
      return null;
    }

    // Store config and register provider
    providerConfigs.put(config.getId(), config);
    registerProvider(provider);
    saveProviderConfigs();

    return provider;
  }

  /**
   * Remove a custom provider configuration.
   *
   * @param providerId The provider ID to remove
   * @return true if removed
   */
  public boolean removeProviderConfig (@NonNull String providerId) {
    if (providerId.equals(TDLIB_PROVIDER_ID)) {
      return false; // Cannot remove built-in provider
    }

    unregisterProvider(providerId);
    return true;
  }

  /**
   * Get a provider configuration by ID.
   */
  @Nullable
  public SpeechProviderConfig getProviderConfig (@NonNull String providerId) {
    return providerConfigs.get(providerId);
  }

  /**
   * Create a provider instance from configuration.
   */
  @Nullable
  private SpeechRecognitionProvider createProviderFromConfig (@NonNull SpeechProviderConfig config) {
    switch (config.getType()) {
      case TDLIB:
        // TDLib provider is built-in, return existing
        return tdlibProvider;

      case HTTP_API:
        return new HttpSpeechRecognitionProvider(config);

      case LOCAL:
        return new LocalSpeechRecognitionProvider(config);

      default:
        Log.e(TAG, "Unknown provider type: " + config.getType());
        return null;
    }
  }

  // --- Settings ---

  /**
   * Get whether auto-transcribe is enabled.
   */
  public boolean isAutoTranscribeEnabled () {
    return autoTranscribe;
  }

  /**
   * Set auto-transcribe setting.
   */
  public void setAutoTranscribe (boolean enabled) {
    this.autoTranscribe = enabled;
    Settings.instance().putBoolean(KEY_AUTO_TRANSCRIBE, enabled);
  }

  /**
   * Get preferred language for transcription.
   */
  @Nullable
  public String getPreferredLanguage () {
    return preferredLanguage;
  }

  /**
   * Set preferred language for transcription.
   * @param language Language code (e.g., "en", "ru") or null for auto-detect
   */
  public void setPreferredLanguage (@Nullable String language) {
    this.preferredLanguage = language;
    if (language != null) {
      Settings.instance().putString(KEY_PREFERRED_LANGUAGE, language);
    } else {
      Settings.instance().remove(KEY_PREFERRED_LANGUAGE);
    }
  }

  // --- Persistence ---

  /**
   * Load provider configurations from storage.
   */
  private void loadProviderConfigs () {
    String json = Settings.instance().getString(KEY_PROVIDERS, null);
    if (json == null || json.isEmpty()) {
      return;
    }

    try {
      JSONArray array = new JSONArray(json);
      for (int i = 0; i < array.length(); i++) {
        JSONObject obj = array.getJSONObject(i);
        SpeechProviderConfig config = SpeechProviderConfig.fromJson(obj);
        if (config.isValid() && !config.getId().equals(TDLIB_PROVIDER_ID)) {
          providerConfigs.put(config.getId(), config);

          // Create and register provider
          SpeechRecognitionProvider provider = createProviderFromConfig(config);
          if (provider != null) {
            registerProvider(provider);
          }
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "Failed to load provider configs", e);
    }
  }

  /**
   * Save provider configurations to storage.
   */
  private void saveProviderConfigs () {
    try {
      JSONArray array = new JSONArray();
      for (SpeechProviderConfig config : providerConfigs.values()) {
        if (!config.getId().equals(TDLIB_PROVIDER_ID)) {
          array.put(config.toJson());
        }
      }
      Settings.instance().putString(KEY_PROVIDERS, array.toString());
    } catch (JSONException e) {
      Log.e(TAG, "Failed to save provider configs", e);
    }
  }

  /**
   * Load general settings.
   */
  private void loadSettings () {
    autoTranscribe = Settings.instance().getBoolean(KEY_AUTO_TRANSCRIBE, false);
    preferredLanguage = Settings.instance().getString(KEY_PREFERRED_LANGUAGE, null);

    // Load active provider
    String activeProviderId = Settings.instance().getString(KEY_ACTIVE_PROVIDER, TDLIB_PROVIDER_ID);
    if (providers.containsKey(activeProviderId)) {
      activeProvider = providers.get(activeProviderId);
    }
  }

  // --- Trial tracking (for TDLib non-Premium users) ---

  /**
   * Update trial info from TDLib UpdateSpeechRecognitionTrial.
   * Called from Tdlib.java when the update is received.
   */
  public void updateTrialInfo (int maxMediaDuration, int weeklyCount, int leftCount, int nextResetDate) {
    this.maxMediaDuration = maxMediaDuration;
    this.weeklyCount = weeklyCount;
    this.leftCount = leftCount;
    this.nextResetDate = nextResetDate;
  }

  /**
   * @return Maximum allowed media duration for trial transcription (seconds)
   */
  public int getMaxMediaDuration () {
    return maxMediaDuration;
  }

  /**
   * @return Total number of trial transcriptions allowed per week
   */
  public int getWeeklyTrialCount () {
    return weeklyCount;
  }

  /**
   * @return Number of trial transcriptions remaining this week
   */
  public int getRemainingTrialAttempts () {
    return leftCount;
  }

  /**
   * @return true if there are trial attempts remaining
   */
  public boolean hasTrialAttemptsRemaining () {
    return leftCount > 0;
  }

  /**
   * @return Unix timestamp when trial attempts reset, or 0 if unknown
   */
  public int getNextResetDate () {
    return nextResetDate;
  }

  /**
   * Get human-readable time until trials reset.
   * @return Formatted time string (e.g., "in 3 days")
   */
  @Nullable
  public String getNextResetTimeFormatted () {
    if (nextResetDate <= 0) {
      return null;
    }

    long nowSeconds = System.currentTimeMillis() / 1000;
    long diffSeconds = nextResetDate - nowSeconds;

    if (diffSeconds <= 0) {
      return null;
    }

    // Use Lang for localized relative time
    return Lang.getRelativeTimestamp(nextResetDate, TimeUnit.SECONDS);
  }

  /**
   * Check if transcription should be available for the current user and provider.
   *
   * For TDLib provider:
   * - Premium users: always available
   * - Non-Premium: available if trial attempts remain
   *
   * For other providers: check if provider is configured and available
   *
   * @param tdlib The Tdlib instance (for Premium check)
   * @return true if transcription feature should be shown
   */
  public boolean isTranscriptionAvailable (@NonNull Tdlib tdlib) {
    if (activeProvider.getType() != SpeechRecognitionProvider.ProviderType.TDLIB) {
      // Custom providers - always available if configured
      return activeProvider.isAvailable();
    }

    // TDLib provider - check Premium or trial
    if (tdlib.hasPremium()) {
      return true;
    }

    // Non-Premium: check trial availability
    return hasTrialAttemptsRemaining();
  }

  /**
   * Check if the given voice duration is within trial limits.
   * Only relevant for non-Premium users with TDLib provider.
   *
   * @param durationSeconds Voice note duration in seconds
   * @param tdlib Tdlib instance for Premium check
   * @return true if duration is acceptable
   */
  public boolean isDurationWithinTrialLimit (int durationSeconds, @NonNull Tdlib tdlib) {
    if (activeProvider.getType() != SpeechRecognitionProvider.ProviderType.TDLIB) {
      return true; // Custom providers have their own limits
    }

    if (tdlib.hasPremium()) {
      return true; // Premium has no duration limit
    }

    return maxMediaDuration <= 0 || durationSeconds <= maxMediaDuration;
  }

  // --- Templates ---

  /**
   * Get all available provider templates.
   */
  @NonNull
  public List<SpeechProviderConfig> getProviderTemplates () {
    return SpeechProviderConfig.getAllTemplates();
  }
}
