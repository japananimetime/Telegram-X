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

import java.util.HashMap;
import java.util.Map;

import me.vkryl.core.lambda.CancellableRunnable;

/**
 * TDLib-based speech recognition provider.
 *
 * Uses Telegram's built-in speech recognition via TdApi.RecognizeSpeech.
 * Results are delivered through message content updates (VoiceNote/VideoNote).
 *
 * For non-Premium users, this is limited by trial counts tracked via
 * UpdateSpeechRecognitionTrial.
 */
public class TdlibSpeechRecognitionProvider implements SpeechRecognitionProvider {

  private static final String TAG = "TdlibSpeechRecognition";
  private static final String PROVIDER_ID = "tdlib";
  private static final String PROVIDER_NAME = "Telegram";

  // Pending transcription callbacks, keyed by "chatId_messageId"
  private final Map<String, Callback> pendingCallbacks = new HashMap<>();

  @NonNull
  @Override
  public String getId () {
    return PROVIDER_ID;
  }

  @NonNull
  @Override
  public String getDisplayName () {
    return PROVIDER_NAME;
  }

  @NonNull
  @Override
  public ProviderType getType () {
    return ProviderType.TDLIB;
  }

  @Override
  public boolean isAvailable () {
    // TDLib provider is always available when logged in
    return true;
  }

  @Override
  public boolean canTranscribe (@NonNull Tdlib tdlib, @NonNull TdApi.Message message, @Nullable TdApi.MessageProperties properties) {
    // Check message properties for TDLib capability
    if (properties != null && properties.canRecognizeSpeech) {
      return true;
    }

    // If properties are null, check if this is a voice/video note message type
    // The actual capability will be checked by TDLib when we call RecognizeSpeech
    if (message.content != null) {
      int constructor = message.content.getConstructor();
      return constructor == TdApi.MessageVoiceNote.CONSTRUCTOR ||
             constructor == TdApi.MessageVideoNote.CONSTRUCTOR;
    }

    return false;
  }

  @Override
  public void transcribe (@NonNull Tdlib tdlib, long chatId, long messageId, @NonNull Callback callback) {
    String key = makeKey(chatId, messageId);

    // Store callback for when we receive the result via message update
    synchronized (pendingCallbacks) {
      pendingCallbacks.put(key, callback);
    }

    // Send the recognition request
    tdlib.client().send(new TdApi.RecognizeSpeech(chatId, messageId), result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        TdApi.Error error = (TdApi.Error) result;
        Log.e(TAG, "RecognizeSpeech failed: %s", error.message);

        // Remove callback and notify error
        synchronized (pendingCallbacks) {
          pendingCallbacks.remove(key);
        }

        callback.onError(String.valueOf(error.code), error.message);
      }
      // On success (TdApi.Ok), we wait for the message content update
    });
  }

  @Override
  public void cancel (long chatId, long messageId) {
    String key = makeKey(chatId, messageId);
    synchronized (pendingCallbacks) {
      pendingCallbacks.remove(key);
    }
  }

  /**
   * Called when a message content update is received that may contain speech recognition results.
   * This is invoked from TGMessageFile when the VoiceNote/VideoNote content is updated.
   *
   * @param chatId Chat ID
   * @param messageId Message ID
   * @param result The speech recognition result
   */
  public void onSpeechRecognitionResult (long chatId, long messageId, @Nullable TdApi.SpeechRecognitionResult result) {
    if (result == null) {
      return;
    }

    String key = makeKey(chatId, messageId);
    Callback callback;

    synchronized (pendingCallbacks) {
      callback = pendingCallbacks.get(key);
    }

    if (callback == null) {
      return;
    }

    switch (result.getConstructor()) {
      case TdApi.SpeechRecognitionResultPending.CONSTRUCTOR: {
        TdApi.SpeechRecognitionResultPending pending = (TdApi.SpeechRecognitionResultPending) result;
        if (pending.partialText != null && !pending.partialText.isEmpty()) {
          callback.onProgress(pending.partialText);
        }
        break;
      }
      case TdApi.SpeechRecognitionResultText.CONSTRUCTOR: {
        TdApi.SpeechRecognitionResultText text = (TdApi.SpeechRecognitionResultText) result;
        synchronized (pendingCallbacks) {
          pendingCallbacks.remove(key);
        }
        callback.onSuccess(text.text);
        break;
      }
      case TdApi.SpeechRecognitionResultError.CONSTRUCTOR: {
        TdApi.SpeechRecognitionResultError error = (TdApi.SpeechRecognitionResultError) result;
        synchronized (pendingCallbacks) {
          pendingCallbacks.remove(key);
        }
        callback.onError(String.valueOf(error.error.code), error.error.message);
        break;
      }
    }
  }

  /**
   * Check if there's a pending transcription for the given message.
   */
  public boolean hasPendingTranscription (long chatId, long messageId) {
    String key = makeKey(chatId, messageId);
    synchronized (pendingCallbacks) {
      return pendingCallbacks.containsKey(key);
    }
  }

  private static String makeKey (long chatId, long messageId) {
    return chatId + "_" + messageId;
  }
}
