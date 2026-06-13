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

/**
 * Interface for speech recognition providers.
 *
 * This abstraction allows different backends for voice transcription:
 * - TDLib (Telegram's built-in, default)
 * - OpenAI Whisper API (future)
 * - Local models like whisper.cpp (future)
 * - Custom HTTP endpoints (future)
 */
public interface SpeechRecognitionProvider {

  /**
   * Provider type identifiers
   */
  enum ProviderType {
    TDLIB,      // Telegram's built-in provider
    HTTP_API,   // External HTTP API (OpenAI, etc.)
    LOCAL       // Local model (whisper.cpp, etc.)
  }

  /**
   * @return Unique identifier for this provider
   */
  @NonNull
  String getId ();

  /**
   * @return Human-readable display name
   */
  @NonNull
  String getDisplayName ();

  /**
   * @return The type of this provider
   */
  @NonNull
  ProviderType getType ();

  /**
   * Check if this provider is currently available for use.
   * For TDLib: always true when logged in
   * For HTTP API: check if configured with valid endpoint
   * For Local: check if model file exists
   */
  boolean isAvailable ();

  /**
   * Check if this provider can transcribe the given message.
   *
   * @param tdlib The Tdlib instance
   * @param message The voice/video note message
   * @param properties Message properties (contains canRecognizeSpeech for TDLib)
   * @return true if transcription is possible
   */
  boolean canTranscribe (@NonNull Tdlib tdlib, @NonNull TdApi.Message message, @Nullable TdApi.MessageProperties properties);

  /**
   * Start transcription of a voice/video note message.
   * Results are delivered asynchronously via the callback.
   *
   * @param tdlib The Tdlib instance
   * @param chatId Chat containing the message
   * @param messageId Message to transcribe
   * @param callback Callback for progress and results
   */
  void transcribe (@NonNull Tdlib tdlib, long chatId, long messageId, @NonNull Callback callback);

  /**
   * Cancel an ongoing transcription.
   *
   * @param chatId Chat containing the message
   * @param messageId Message being transcribed
   */
  void cancel (long chatId, long messageId);

  /**
   * Whether this provider requires the audio file to be downloaded first.
   * TDLib: false (handles download internally)
   * HTTP/Local: true (need file path)
   */
  default boolean needsAudioFile () {
    return false;
  }

  /**
   * Callback interface for transcription results
   */
  interface Callback {
    /**
     * Called when partial transcription is available.
     * Not all providers support this.
     *
     * @param partialText The partial transcription text
     */
    void onProgress (@NonNull String partialText);

    /**
     * Called when transcription completes successfully.
     *
     * @param text The final transcription text
     */
    void onSuccess (@NonNull String text);

    /**
     * Called when transcription fails.
     *
     * @param errorCode Error code (e.g., "MSG_VOICE_TOO_LONG")
     * @param errorMessage Human-readable error description
     */
    void onError (@NonNull String errorCode, @NonNull String errorMessage);
  }
}
