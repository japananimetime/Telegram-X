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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.vkryl.core.StringUtils;

/**
 * Configuration model for speech recognition providers.
 *
 * Supports different provider types:
 * - TDLIB: Telegram's built-in (no config needed)
 * - HTTP_API: External HTTP APIs (OpenAI Whisper, Google Cloud, custom endpoints)
 * - LOCAL: Local models (whisper.cpp, etc.)
 */
public class SpeechProviderConfig {

  // Provider identification
  private String id;
  private String displayName;
  private SpeechRecognitionProvider.ProviderType type;
  private boolean isEnabled;
  private boolean isBuiltIn;

  // HTTP API configuration
  private String apiEndpoint;
  private String apiKey;
  private Map<String, String> headers;
  private String requestBodyTemplate;
  private String responseJsonPath;
  private String audioFieldName;
  private String audioEncoding; // "base64", "multipart"

  // Local provider configuration
  private String modelPath;
  private String executablePath;

  // Common settings
  private String[] supportedLanguages;
  private int maxDurationSeconds;
  private String preferredLanguage;

  public SpeechProviderConfig () {
    this.id = UUID.randomUUID().toString();
    this.headers = new HashMap<>();
    this.isEnabled = true;
    this.isBuiltIn = false;
    this.audioEncoding = "base64";
    this.audioFieldName = "audio";
    this.maxDurationSeconds = 0; // 0 = no limit
  }

  // --- Builder-style setters ---

  public SpeechProviderConfig setId (@NonNull String id) {
    this.id = id;
    return this;
  }

  public SpeechProviderConfig setDisplayName (@NonNull String displayName) {
    this.displayName = displayName;
    return this;
  }

  public SpeechProviderConfig setType (@NonNull SpeechRecognitionProvider.ProviderType type) {
    this.type = type;
    return this;
  }

  public SpeechProviderConfig setEnabled (boolean enabled) {
    this.isEnabled = enabled;
    return this;
  }

  public SpeechProviderConfig setBuiltIn (boolean builtIn) {
    this.isBuiltIn = builtIn;
    return this;
  }

  public SpeechProviderConfig setApiEndpoint (@Nullable String apiEndpoint) {
    this.apiEndpoint = apiEndpoint;
    return this;
  }

  public SpeechProviderConfig setApiKey (@Nullable String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  public SpeechProviderConfig setHeader (@NonNull String key, @NonNull String value) {
    this.headers.put(key, value);
    return this;
  }

  public SpeechProviderConfig setHeaders (@Nullable Map<String, String> headers) {
    this.headers = headers != null ? headers : new HashMap<>();
    return this;
  }

  public SpeechProviderConfig setRequestBodyTemplate (@Nullable String template) {
    this.requestBodyTemplate = template;
    return this;
  }

  public SpeechProviderConfig setResponseJsonPath (@Nullable String jsonPath) {
    this.responseJsonPath = jsonPath;
    return this;
  }

  public SpeechProviderConfig setAudioFieldName (@Nullable String fieldName) {
    this.audioFieldName = fieldName;
    return this;
  }

  public SpeechProviderConfig setAudioEncoding (@Nullable String encoding) {
    this.audioEncoding = encoding;
    return this;
  }

  public SpeechProviderConfig setModelPath (@Nullable String modelPath) {
    this.modelPath = modelPath;
    return this;
  }

  public SpeechProviderConfig setExecutablePath (@Nullable String executablePath) {
    this.executablePath = executablePath;
    return this;
  }

  public SpeechProviderConfig setSupportedLanguages (@Nullable String[] languages) {
    this.supportedLanguages = languages;
    return this;
  }

  public SpeechProviderConfig setMaxDurationSeconds (int maxDuration) {
    this.maxDurationSeconds = maxDuration;
    return this;
  }

  public SpeechProviderConfig setPreferredLanguage (@Nullable String language) {
    this.preferredLanguage = language;
    return this;
  }

  // --- Getters ---

  @NonNull
  public String getId () {
    return id;
  }

  @NonNull
  public String getDisplayName () {
    return displayName != null ? displayName : id;
  }

  @NonNull
  public SpeechRecognitionProvider.ProviderType getType () {
    return type != null ? type : SpeechRecognitionProvider.ProviderType.HTTP_API;
  }

  public boolean isEnabled () {
    return isEnabled;
  }

  public boolean isBuiltIn () {
    return isBuiltIn;
  }

  @Nullable
  public String getApiEndpoint () {
    return apiEndpoint;
  }

  @Nullable
  public String getApiKey () {
    return apiKey;
  }

  @NonNull
  public Map<String, String> getHeaders () {
    return headers;
  }

  @Nullable
  public String getRequestBodyTemplate () {
    return requestBodyTemplate;
  }

  @Nullable
  public String getResponseJsonPath () {
    return responseJsonPath;
  }

  @NonNull
  public String getAudioFieldName () {
    return audioFieldName != null ? audioFieldName : "audio";
  }

  @NonNull
  public String getAudioEncoding () {
    return audioEncoding != null ? audioEncoding : "base64";
  }

  @Nullable
  public String getModelPath () {
    return modelPath;
  }

  @Nullable
  public String getExecutablePath () {
    return executablePath;
  }

  @Nullable
  public String[] getSupportedLanguages () {
    return supportedLanguages;
  }

  public int getMaxDurationSeconds () {
    return maxDurationSeconds;
  }

  @Nullable
  public String getPreferredLanguage () {
    return preferredLanguage;
  }

  // --- Validation ---

  public boolean isValid () {
    if (StringUtils.isEmpty(id) || StringUtils.isEmpty(displayName) || type == null) {
      return false;
    }

    switch (type) {
      case TDLIB:
        return true; // No additional config needed
      case HTTP_API:
        return !StringUtils.isEmpty(apiEndpoint);
      case LOCAL:
        return !StringUtils.isEmpty(modelPath) || !StringUtils.isEmpty(executablePath);
    }
    return false;
  }

  // --- JSON Serialization ---

  @NonNull
  public JSONObject toJson () throws JSONException {
    JSONObject json = new JSONObject();
    json.put("id", id);
    json.put("displayName", displayName);
    json.put("type", type != null ? type.name() : null);
    json.put("isEnabled", isEnabled);
    json.put("isBuiltIn", isBuiltIn);

    // HTTP API
    if (apiEndpoint != null) json.put("apiEndpoint", apiEndpoint);
    if (apiKey != null) json.put("apiKey", apiKey);
    if (!headers.isEmpty()) {
      JSONObject headersJson = new JSONObject();
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        headersJson.put(entry.getKey(), entry.getValue());
      }
      json.put("headers", headersJson);
    }
    if (requestBodyTemplate != null) json.put("requestBodyTemplate", requestBodyTemplate);
    if (responseJsonPath != null) json.put("responseJsonPath", responseJsonPath);
    if (audioFieldName != null) json.put("audioFieldName", audioFieldName);
    if (audioEncoding != null) json.put("audioEncoding", audioEncoding);

    // Local
    if (modelPath != null) json.put("modelPath", modelPath);
    if (executablePath != null) json.put("executablePath", executablePath);

    // Common
    if (supportedLanguages != null && supportedLanguages.length > 0) {
      JSONArray langsArray = new JSONArray();
      for (String lang : supportedLanguages) {
        langsArray.put(lang);
      }
      json.put("supportedLanguages", langsArray);
    }
    json.put("maxDurationSeconds", maxDurationSeconds);
    if (preferredLanguage != null) json.put("preferredLanguage", preferredLanguage);

    return json;
  }

  @NonNull
  public static SpeechProviderConfig fromJson (@NonNull JSONObject json) throws JSONException {
    SpeechProviderConfig config = new SpeechProviderConfig();

    config.id = json.optString("id", UUID.randomUUID().toString());
    config.displayName = json.optString("displayName", null);
    String typeStr = json.optString("type", null);
    if (typeStr != null) {
      try {
        config.type = SpeechRecognitionProvider.ProviderType.valueOf(typeStr);
      } catch (IllegalArgumentException e) {
        config.type = SpeechRecognitionProvider.ProviderType.HTTP_API;
      }
    }
    config.isEnabled = json.optBoolean("isEnabled", true);
    config.isBuiltIn = json.optBoolean("isBuiltIn", false);

    // HTTP API
    config.apiEndpoint = json.optString("apiEndpoint", null);
    config.apiKey = json.optString("apiKey", null);
    JSONObject headersJson = json.optJSONObject("headers");
    if (headersJson != null) {
      Iterator<String> keys = headersJson.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        config.headers.put(key, headersJson.getString(key));
      }
    }
    config.requestBodyTemplate = json.optString("requestBodyTemplate", null);
    config.responseJsonPath = json.optString("responseJsonPath", null);
    config.audioFieldName = json.optString("audioFieldName", "audio");
    config.audioEncoding = json.optString("audioEncoding", "base64");

    // Local
    config.modelPath = json.optString("modelPath", null);
    config.executablePath = json.optString("executablePath", null);

    // Common
    JSONArray langsArray = json.optJSONArray("supportedLanguages");
    if (langsArray != null && langsArray.length() > 0) {
      config.supportedLanguages = new String[langsArray.length()];
      for (int i = 0; i < langsArray.length(); i++) {
        config.supportedLanguages[i] = langsArray.getString(i);
      }
    }
    config.maxDurationSeconds = json.optInt("maxDurationSeconds", 0);
    config.preferredLanguage = json.optString("preferredLanguage", null);

    return config;
  }

  // --- Templates for popular services ---

  /**
   * Template for OpenAI Whisper API
   */
  public static SpeechProviderConfig templateOpenAiWhisper () {
    return new SpeechProviderConfig()
      .setId("openai-whisper")
      .setDisplayName("OpenAI Whisper")
      .setType(SpeechRecognitionProvider.ProviderType.HTTP_API)
      .setApiEndpoint("https://api.openai.com/v1/audio/transcriptions")
      .setHeader("Authorization", "Bearer ${api_key}")
      .setAudioEncoding("multipart")
      .setAudioFieldName("file")
      .setRequestBodyTemplate("{\"model\": \"whisper-1\"}")
      .setResponseJsonPath("$.text");
  }

  /**
   * Template for Groq Whisper API (faster, cheaper)
   */
  public static SpeechProviderConfig templateGroqWhisper () {
    return new SpeechProviderConfig()
      .setId("groq-whisper")
      .setDisplayName("Groq Whisper")
      .setType(SpeechRecognitionProvider.ProviderType.HTTP_API)
      .setApiEndpoint("https://api.groq.com/openai/v1/audio/transcriptions")
      .setHeader("Authorization", "Bearer ${api_key}")
      .setAudioEncoding("multipart")
      .setAudioFieldName("file")
      .setRequestBodyTemplate("{\"model\": \"whisper-large-v3\"}")
      .setResponseJsonPath("$.text");
  }

  /**
   * Template for local Ollama server
   */
  public static SpeechProviderConfig templateOllama () {
    return new SpeechProviderConfig()
      .setId("ollama-local")
      .setDisplayName("Ollama (Local)")
      .setType(SpeechRecognitionProvider.ProviderType.HTTP_API)
      .setApiEndpoint("http://localhost:11434/api/transcribe")
      .setAudioEncoding("base64")
      .setAudioFieldName("audio")
      .setRequestBodyTemplate("{\"model\": \"whisper\", \"audio\": \"${base64_audio}\"}")
      .setResponseJsonPath("$.text");
  }

  /**
   * Template for Google Cloud Speech-to-Text
   */
  public static SpeechProviderConfig templateGoogleCloud () {
    return new SpeechProviderConfig()
      .setId("google-cloud-stt")
      .setDisplayName("Google Cloud Speech")
      .setType(SpeechRecognitionProvider.ProviderType.HTTP_API)
      .setApiEndpoint("https://speech.googleapis.com/v1/speech:recognize?key=${api_key}")
      .setHeader("Content-Type", "application/json")
      .setAudioEncoding("base64")
      .setAudioFieldName("content")
      .setRequestBodyTemplate("{\"config\": {\"encoding\": \"OGG_OPUS\", \"sampleRateHertz\": 48000, \"languageCode\": \"${language}\"}, \"audio\": {\"content\": \"${base64_audio}\"}}")
      .setResponseJsonPath("$.results[0].alternatives[0].transcript");
  }

  /**
   * Template for Azure Cognitive Services Speech
   */
  public static SpeechProviderConfig templateAzureSpeech () {
    return new SpeechProviderConfig()
      .setId("azure-speech")
      .setDisplayName("Azure Speech Services")
      .setType(SpeechRecognitionProvider.ProviderType.HTTP_API)
      .setApiEndpoint("https://<region>.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=${language}")
      .setHeader("Ocp-Apim-Subscription-Key", "${api_key}")
      .setHeader("Content-Type", "audio/ogg; codecs=opus")
      .setAudioEncoding("binary")
      .setAudioFieldName("audio")
      .setResponseJsonPath("$.DisplayText");
  }

  /**
   * Template for AssemblyAI
   */
  public static SpeechProviderConfig templateAssemblyAI () {
    return new SpeechProviderConfig()
      .setId("assemblyai")
      .setDisplayName("AssemblyAI")
      .setType(SpeechRecognitionProvider.ProviderType.HTTP_API)
      .setApiEndpoint("https://api.assemblyai.com/v2/transcript")
      .setHeader("Authorization", "${api_key}")
      .setHeader("Content-Type", "application/json")
      .setAudioEncoding("base64")
      .setAudioFieldName("audio_data")
      .setRequestBodyTemplate("{\"audio_data\": \"${base64_audio}\", \"language_code\": \"${language}\"}")
      .setResponseJsonPath("$.text");
  }

  /**
   * Template for Deepgram
   */
  public static SpeechProviderConfig templateDeepgram () {
    return new SpeechProviderConfig()
      .setId("deepgram")
      .setDisplayName("Deepgram")
      .setType(SpeechRecognitionProvider.ProviderType.HTTP_API)
      .setApiEndpoint("https://api.deepgram.com/v1/listen?model=nova-2&language=${language}")
      .setHeader("Authorization", "Token ${api_key}")
      .setHeader("Content-Type", "audio/ogg")
      .setAudioEncoding("binary")
      .setAudioFieldName("audio")
      .setResponseJsonPath("$.results.channels[0].alternatives[0].transcript");
  }

  /**
   * Template for custom HTTP endpoint
   */
  public static SpeechProviderConfig templateCustomHttp () {
    return new SpeechProviderConfig()
      .setId("custom-" + UUID.randomUUID().toString().substring(0, 8))
      .setDisplayName("Custom API")
      .setType(SpeechRecognitionProvider.ProviderType.HTTP_API)
      .setAudioEncoding("base64")
      .setAudioFieldName("audio")
      .setRequestBodyTemplate("{\"audio\": \"${base64_audio}\", \"language\": \"${language}\"}")
      .setResponseJsonPath("$.transcription");
  }

  /**
   * Get all available templates
   */
  public static List<SpeechProviderConfig> getAllTemplates () {
    List<SpeechProviderConfig> templates = new ArrayList<>();
    templates.add(templateOpenAiWhisper());
    templates.add(templateGroqWhisper());
    templates.add(templateGoogleCloud());
    templates.add(templateAzureSpeech());
    templates.add(templateAssemblyAI());
    templates.add(templateDeepgram());
    templates.add(templateOllama());
    templates.add(templateCustomHttp());
    return templates;
  }
}
