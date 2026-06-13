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

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.vkryl.core.StringUtils;

/**
 * HTTP API-based speech recognition provider.
 *
 * Supports various HTTP APIs for speech-to-text:
 * - OpenAI Whisper API
 * - Groq Whisper API
 * - Google Cloud Speech-to-Text
 * - Custom HTTP endpoints (self-hosted Ollama, etc.)
 *
 * Configuration is provided via SpeechProviderConfig.
 */
public class HttpSpeechRecognitionProvider implements SpeechRecognitionProvider {

  private static final String TAG = "HttpSpeechRecognition";

  private final SpeechProviderConfig config;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Map<String, Boolean> pendingRequests = new HashMap<>();

  public HttpSpeechRecognitionProvider (@NonNull SpeechProviderConfig config) {
    this.config = config;
  }

  @NonNull
  @Override
  public String getId () {
    return config.getId();
  }

  @NonNull
  @Override
  public String getDisplayName () {
    return config.getDisplayName();
  }

  @NonNull
  @Override
  public ProviderType getType () {
    return ProviderType.HTTP_API;
  }

  @Override
  public boolean isAvailable () {
    return config.isEnabled() && config.isValid();
  }

  @Override
  public boolean canTranscribe (@NonNull Tdlib tdlib, @NonNull TdApi.Message message, @Nullable TdApi.MessageProperties properties) {
    if (!isAvailable()) {
      return false;
    }

    // Check message type
    if (message.content == null) {
      return false;
    }
    int constructor = message.content.getConstructor();
    if (constructor != TdApi.MessageVoiceNote.CONSTRUCTOR &&
        constructor != TdApi.MessageVideoNote.CONSTRUCTOR) {
      return false;
    }

    // Check duration limit
    int maxDuration = config.getMaxDurationSeconds();
    if (maxDuration > 0) {
      int duration = 0;
      if (constructor == TdApi.MessageVoiceNote.CONSTRUCTOR) {
        duration = ((TdApi.MessageVoiceNote) message.content).voiceNote.duration;
      } else {
        duration = ((TdApi.MessageVideoNote) message.content).videoNote.duration;
      }
      if (duration > maxDuration) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean needsAudioFile () {
    return true; // HTTP provider needs the audio file downloaded
  }

  @Override
  public void transcribe (@NonNull Tdlib tdlib, long chatId, long messageId, @NonNull Callback callback) {
    String key = makeKey(chatId, messageId);
    synchronized (pendingRequests) {
      pendingRequests.put(key, true);
    }

    // First, get the message to access the file
    tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        TdApi.Error error = (TdApi.Error) result;
        notifyError(callback, "GET_MESSAGE_FAILED", error.message);
        return;
      }

      TdApi.Message message = (TdApi.Message) result;
      TdApi.File file = getAudioFile(message);

      if (file == null) {
        notifyError(callback, "NO_AUDIO_FILE", "Message does not contain audio");
        return;
      }

      // Download file if needed
      if (!file.local.isDownloadingCompleted) {
        tdlib.files().downloadFile(file, 1, (downloaded, error) -> {
          if (downloaded != null && downloaded.local.isDownloadingCompleted) {
            performTranscription(downloaded.local.path, chatId, messageId, callback);
          } else {
            String errorMsg = error != null ? error.message : "Failed to download audio file";
            notifyError(callback, "DOWNLOAD_FAILED", errorMsg);
          }
        });
      } else {
        performTranscription(file.local.path, chatId, messageId, callback);
      }
    });
  }

  @Nullable
  private TdApi.File getAudioFile (@NonNull TdApi.Message message) {
    if (message.content == null) return null;

    switch (message.content.getConstructor()) {
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return ((TdApi.MessageVoiceNote) message.content).voiceNote.voice;
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return ((TdApi.MessageVideoNote) message.content).videoNote.video;
      default:
        return null;
    }
  }

  private void performTranscription (@NonNull String filePath, long chatId, long messageId, @NonNull Callback callback) {
    String key = makeKey(chatId, messageId);

    executor.execute(() -> {
      // Check if cancelled
      synchronized (pendingRequests) {
        if (!pendingRequests.containsKey(key)) {
          return;
        }
      }

      try {
        String result = callApi(filePath);
        String text = extractText(result);

        // Check if cancelled
        synchronized (pendingRequests) {
          if (!pendingRequests.containsKey(key)) {
            return;
          }
          pendingRequests.remove(key);
        }

        if (text != null) {
          notifySuccess(callback, text);
        } else {
          notifyError(callback, "PARSE_ERROR", "Failed to parse transcription response");
        }
      } catch (IOException e) {
        Log.e(TAG, "HTTP request failed", e);
        synchronized (pendingRequests) {
          pendingRequests.remove(key);
        }
        notifyError(callback, "HTTP_ERROR", e.getMessage());
      } catch (Exception e) {
        Log.e(TAG, "Transcription failed", e);
        synchronized (pendingRequests) {
          pendingRequests.remove(key);
        }
        notifyError(callback, "UNKNOWN_ERROR", e.getMessage());
      }
    });
  }

  @NonNull
  private String callApi (@NonNull String filePath) throws IOException {
    String endpoint = config.getApiEndpoint();
    if (StringUtils.isEmpty(endpoint)) {
      throw new IOException("No API endpoint configured");
    }

    URL url = new URL(endpoint);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

    try {
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setConnectTimeout(30000);
      connection.setReadTimeout(120000);

      // Set headers
      for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
        String value = substituteVariables(header.getValue());
        connection.setRequestProperty(header.getKey(), value);
      }

      // Build and send request body
      String encoding = config.getAudioEncoding();
      if ("multipart".equals(encoding)) {
        sendMultipartRequest(connection, filePath);
      } else if ("binary".equals(encoding)) {
        sendBinaryRequest(connection, filePath);
      } else {
        sendJsonRequest(connection, filePath);
      }

      // Read response
      int responseCode = connection.getResponseCode();
      if (responseCode >= 200 && responseCode < 300) {
        return readResponse(connection);
      } else {
        String errorBody = readErrorResponse(connection);
        throw new IOException("HTTP " + responseCode + ": " + errorBody);
      }
    } finally {
      connection.disconnect();
    }
  }

  private void sendJsonRequest (@NonNull HttpURLConnection connection, @NonNull String filePath) throws IOException {
    connection.setRequestProperty("Content-Type", "application/json");

    // Read and encode audio file
    byte[] audioBytes = readFileBytes(filePath);
    String base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP);

    // Build request body
    String template = config.getRequestBodyTemplate();
    String body;
    if (!StringUtils.isEmpty(template)) {
      body = substituteVariables(template);
      body = body.replace("${base64_audio}", base64Audio);
    } else {
      // Default JSON format
      JSONObject json = new JSONObject();
      try {
        json.put(config.getAudioFieldName(), base64Audio);
        if (!StringUtils.isEmpty(config.getPreferredLanguage())) {
          json.put("language", config.getPreferredLanguage());
        }
      } catch (JSONException e) {
        throw new IOException("Failed to build JSON request", e);
      }
      body = json.toString();
    }

    try (OutputStream os = connection.getOutputStream()) {
      os.write(body.getBytes(StandardCharsets.UTF_8));
    }
  }

  private void sendBinaryRequest (@NonNull HttpURLConnection connection, @NonNull String filePath) throws IOException {
    // Content-Type should already be set via headers in config
    byte[] audioBytes = readFileBytes(filePath);
    try (OutputStream os = connection.getOutputStream()) {
      os.write(audioBytes);
    }
  }

  private void sendMultipartRequest (@NonNull HttpURLConnection connection, @NonNull String filePath) throws IOException {
    String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

    File audioFile = new File(filePath);
    String fileName = audioFile.getName();
    String mimeType = getMimeType(fileName);

    try (DataOutputStream dos = new DataOutputStream(connection.getOutputStream())) {
      // Audio file part
      dos.writeBytes("--" + boundary + "\r\n");
      dos.writeBytes("Content-Disposition: form-data; name=\"" + config.getAudioFieldName() + "\"; filename=\"" + fileName + "\"\r\n");
      dos.writeBytes("Content-Type: " + mimeType + "\r\n\r\n");

      // Write file content
      try (FileInputStream fis = new FileInputStream(audioFile)) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
          dos.write(buffer, 0, bytesRead);
        }
      }
      dos.writeBytes("\r\n");

      // Additional form fields from template
      String template = config.getRequestBodyTemplate();
      if (!StringUtils.isEmpty(template)) {
        try {
          JSONObject json = new JSONObject(template);
          java.util.Iterator<String> keys = json.keys();
          while (keys.hasNext()) {
            String key = keys.next();
            String value = json.optString(key, "");
            value = substituteVariables(value);

            dos.writeBytes("--" + boundary + "\r\n");
            dos.writeBytes("Content-Disposition: form-data; name=\"" + key + "\"\r\n\r\n");
            dos.writeBytes(value + "\r\n");
          }
        } catch (JSONException e) {
          Log.w(TAG, "Failed to parse template JSON", e);
        }
      }

      // Closing boundary
      dos.writeBytes("--" + boundary + "--\r\n");
      dos.flush();
    }
  }

  @NonNull
  private String substituteVariables (@NonNull String input) {
    String result = input;
    result = result.replace("${api_key}", config.getApiKey() != null ? config.getApiKey() : "");
    result = result.replace("${language}", config.getPreferredLanguage() != null ? config.getPreferredLanguage() : "auto");
    return result;
  }

  @NonNull
  private String readResponse (@NonNull HttpURLConnection connection) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      return response.toString();
    }
  }

  @NonNull
  private String readErrorResponse (@NonNull HttpURLConnection connection) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      return response.toString();
    } catch (Exception e) {
      return "Unknown error";
    }
  }

  @Nullable
  private String extractText (@NonNull String response) {
    String jsonPath = config.getResponseJsonPath();

    // Try to parse as JSON
    try {
      JSONObject json = new JSONObject(response);

      if (!StringUtils.isEmpty(jsonPath)) {
        // Simple JSON path extraction (supports $.field or $.field.subfield)
        return extractJsonPath(json, jsonPath);
      }

      // Try common response field names
      if (json.has("text")) return json.getString("text");
      if (json.has("transcription")) return json.getString("transcription");
      if (json.has("transcript")) return json.getString("transcript");
      if (json.has("result")) return json.getString("result");

    } catch (JSONException e) {
      // Response might be plain text
      return response.trim();
    }

    return null;
  }

  @Nullable
  private String extractJsonPath (@NonNull JSONObject json, @NonNull String path) {
    // Remove leading $. if present
    if (path.startsWith("$.")) {
      path = path.substring(2);
    } else if (path.startsWith("$")) {
      path = path.substring(1);
    }

    String[] parts = path.split("\\.");
    Object current = json;

    try {
      for (String part : parts) {
        if (current instanceof JSONObject) {
          current = ((JSONObject) current).get(part);
        } else {
          return null;
        }
      }

      if (current instanceof String) {
        return (String) current;
      } else if (current != null) {
        return current.toString();
      }
    } catch (JSONException e) {
      Log.w(TAG, "Failed to extract JSON path: " + path, e);
    }

    return null;
  }

  @NonNull
  private byte[] readFileBytes (@NonNull String filePath) throws IOException {
    File file = new File(filePath);
    byte[] bytes = new byte[(int) file.length()];
    try (FileInputStream fis = new FileInputStream(file)) {
      int offset = 0;
      int numRead;
      while (offset < bytes.length && (numRead = fis.read(bytes, offset, bytes.length - offset)) >= 0) {
        offset += numRead;
      }
    }
    return bytes;
  }

  @NonNull
  private String getMimeType (@NonNull String fileName) {
    String lower = fileName.toLowerCase();
    if (lower.endsWith(".ogg") || lower.endsWith(".oga")) {
      return "audio/ogg";
    } else if (lower.endsWith(".mp3")) {
      return "audio/mpeg";
    } else if (lower.endsWith(".m4a")) {
      return "audio/mp4";
    } else if (lower.endsWith(".wav")) {
      return "audio/wav";
    } else if (lower.endsWith(".webm")) {
      return "audio/webm";
    } else if (lower.endsWith(".mp4")) {
      return "video/mp4";
    }
    return "application/octet-stream";
  }

  @Override
  public void cancel (long chatId, long messageId) {
    String key = makeKey(chatId, messageId);
    synchronized (pendingRequests) {
      pendingRequests.remove(key);
    }
  }

  private static String makeKey (long chatId, long messageId) {
    return chatId + "_" + messageId;
  }

  private void notifySuccess (@NonNull Callback callback, @NonNull String text) {
    UI.post(() -> callback.onSuccess(text));
  }

  private void notifyError (@NonNull Callback callback, @NonNull String code, @NonNull String message) {
    UI.post(() -> callback.onError(code, message));
  }

  /**
   * Get the configuration for this provider.
   */
  @NonNull
  public SpeechProviderConfig getConfig () {
    return config;
  }

  /**
   * Test the provider configuration by making a simple request.
   * Returns null on success, or error message on failure.
   */
  @Nullable
  public String testConnection () {
    if (!config.isValid()) {
      return "Invalid configuration";
    }

    // For HTTP providers, we just validate the endpoint is reachable
    try {
      URL url = new URL(config.getApiEndpoint());
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("HEAD");
      connection.setConnectTimeout(10000);
      connection.connect();

      int code = connection.getResponseCode();
      connection.disconnect();

      // Accept any response (even 401/403 means the server is reachable)
      if (code > 0) {
        return null; // Success
      }
    } catch (Exception e) {
      return e.getMessage();
    }

    return "Unknown error";
  }
}
