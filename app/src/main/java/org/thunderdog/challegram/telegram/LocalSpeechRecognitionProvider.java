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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local (on-device) speech recognition provider.
 *
 * This is a framework for future integration with:
 * - whisper.cpp (native library)
 * - Vosk (Java-compatible)
 * - Other local speech-to-text engines
 *
 * Currently implements the interface with placeholder functionality.
 * Actual model loading and inference would need native integration.
 */
public class LocalSpeechRecognitionProvider implements SpeechRecognitionProvider {

  private static final String TAG = "LocalSpeechRecognition";

  private final SpeechProviderConfig config;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Map<String, Boolean> pendingRequests = new HashMap<>();

  // Model state
  private boolean isModelLoaded = false;
  private String loadError = null;

  public LocalSpeechRecognitionProvider (@NonNull SpeechProviderConfig config) {
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
    return ProviderType.LOCAL;
  }

  @Override
  public boolean isAvailable () {
    if (!config.isEnabled() || !config.isValid()) {
      return false;
    }

    // Check if model file exists
    String modelPath = config.getModelPath();
    if (modelPath != null && !modelPath.isEmpty()) {
      File modelFile = new File(modelPath);
      if (!modelFile.exists()) {
        loadError = "Model file not found: " + modelPath;
        return false;
      }
    }

    // Check if executable exists (for external process providers)
    String execPath = config.getExecutablePath();
    if (execPath != null && !execPath.isEmpty()) {
      File execFile = new File(execPath);
      if (!execFile.exists() || !execFile.canExecute()) {
        loadError = "Executable not found or not executable: " + execPath;
        return false;
      }
    }

    return true;
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
    return true; // Local provider needs the audio file downloaded
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
        // TODO: Implement actual local transcription
        // This would involve:
        // 1. Loading the model (if not already loaded)
        // 2. Converting audio to the required format
        // 3. Running inference
        // 4. Returning the transcribed text

        // For now, return an error indicating local transcription is not yet implemented
        String result = performLocalTranscription(filePath);

        // Check if cancelled
        synchronized (pendingRequests) {
          if (!pendingRequests.containsKey(key)) {
            return;
          }
          pendingRequests.remove(key);
        }

        if (result != null) {
          notifySuccess(callback, result);
        } else {
          notifyError(callback, "TRANSCRIPTION_FAILED", "Local transcription returned no result");
        }
      } catch (Exception e) {
        Log.e(TAG, "Local transcription failed", e);
        synchronized (pendingRequests) {
          pendingRequests.remove(key);
        }
        notifyError(callback, "LOCAL_ERROR", e.getMessage());
      }
    });
  }

  /**
   * Perform local transcription.
   *
   * TODO: Implement actual local model inference.
   * Options for implementation:
   *
   * 1. whisper.cpp via JNI:
   *    - Compile whisper.cpp as a native library
   *    - Create JNI bindings
   *    - Load model and run inference
   *
   * 2. Vosk:
   *    - Add Vosk dependency (vosk-android)
   *    - Load model from modelPath
   *    - Create recognizer and process audio
   *
   * 3. External process:
   *    - Use executablePath to run external binary
   *    - Pass audio file as argument
   *    - Capture stdout as result
   *
   * @param filePath Path to the audio file
   * @return Transcribed text, or null on failure
   */
  @Nullable
  private String performLocalTranscription (@NonNull String filePath) {
    String execPath = config.getExecutablePath();

    if (execPath != null && !execPath.isEmpty()) {
      // Try external process approach
      return runExternalTranscription(execPath, filePath);
    }

    // No local implementation available yet
    throw new UnsupportedOperationException(
      "Local transcription not yet implemented. " +
      "Configure an external executable or use HTTP API provider instead."
    );
  }

  /**
   * Run transcription via external process.
   *
   * Expected behavior:
   * - Execute: {executablePath} {audioFilePath}
   * - Read stdout as the transcription result
   *
   * @param executablePath Path to the transcription binary
   * @param audioFilePath Path to the audio file
   * @return Transcribed text from stdout
   */
  @Nullable
  private String runExternalTranscription (@NonNull String executablePath, @NonNull String audioFilePath) {
    try {
      ProcessBuilder pb = new ProcessBuilder(executablePath, audioFilePath);
      pb.redirectErrorStream(true);

      Process process = pb.start();

      // Read output
      StringBuilder output = new StringBuilder();
      try (java.io.BufferedReader reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      int exitCode = process.waitFor();
      if (exitCode == 0) {
        return output.toString().trim();
      } else {
        Log.e(TAG, "External process exited with code " + exitCode + ": " + output);
        return null;
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to run external transcription", e);
      return null;
    }
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
   * Get the last load error, if any.
   */
  @Nullable
  public String getLoadError () {
    return loadError;
  }

  /**
   * Check if the model is currently loaded.
   */
  public boolean isModelLoaded () {
    return isModelLoaded;
  }

  /**
   * Attempt to load the model.
   * This is a placeholder for actual model loading.
   *
   * @return true if model loaded successfully
   */
  public boolean loadModel () {
    // TODO: Implement actual model loading
    // For now, just check if files exist
    if (!isAvailable()) {
      return false;
    }

    isModelLoaded = true;
    return true;
  }

  /**
   * Unload the model to free memory.
   */
  public void unloadModel () {
    // TODO: Implement actual model unloading
    isModelLoaded = false;
  }
}
