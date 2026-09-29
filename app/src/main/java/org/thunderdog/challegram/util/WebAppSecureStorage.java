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
 */
package org.thunderdog.challegram.util;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts Mini App secure-storage values with an AES-256-GCM key held in the
 * Android Keystore (hardware-backed where available), so values are not stored
 * in plaintext at rest.
 *
 * On API < 23 the Keystore AES API is unavailable; {@link #encrypt(String)} then
 * returns {@code null} so the caller can reject the write rather than persist
 * plaintext that masquerades as secure. The stored format is
 * {@code 1:base64(iv):base64(ciphertext)}; any value without that prefix is
 * treated as untrusted and never handed back.
 */
public final class WebAppSecureStorage {
  private static final String KEYSTORE = "AndroidKeyStore";
  private static final String KEY_ALIAS = "tgx_webapp_secure_storage";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_LENGTH = 12;
  private static final String PREFIX = "1:";

  private WebAppSecureStorage () { }

  public static boolean isEncryptionAvailable () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
  }

  @Nullable
  private static SecretKey obtainKey () {
    if (!isEncryptionAvailable()) {
      return null;
    }
    try {
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
      keyStore.load(null);
      KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
      if (entry instanceof KeyStore.SecretKeyEntry) {
        return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
      }
      KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
      KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build();
      generator.init(spec);
      return generator.generateKey();
    } catch (Throwable t) {
      Log.e("Cannot obtain WebApp secure-storage key", t);
      return null;
    }
  }

  /**
   * Encrypts a value for storage. Returns {@code null} when the value cannot be
   * encrypted (Keystore unavailable on API &lt; 23, or any key/cipher failure) so
   * the caller can reject the write instead of persisting unprotected plaintext.
   */
  @Nullable
  public static String encrypt (@NonNull String value) {
    SecretKey key = obtainKey();
    if (key == null) {
      return null;
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key);
      byte[] iv = cipher.getIV();
      byte[] ciphertext = cipher.doFinal(value.getBytes("UTF-8"));
      return PREFIX
        + Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
        + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    } catch (Throwable t) {
      Log.e("Cannot encrypt WebApp secure-storage value", t);
      return null;
    }
  }

  /**
   * Decrypts a stored value. Anything without the encryption prefix is considered
   * untrusted (corrupt or never properly encrypted) and yields {@code null} — we
   * never return plaintext as if it were a trusted secure-storage value.
   */
  @Nullable
  public static String decrypt (@Nullable String stored) {
    if (stored == null || !stored.startsWith(PREFIX)) {
      return null;
    }
    SecretKey key = obtainKey();
    if (key == null) {
      return null;
    }
    try {
      String[] parts = stored.substring(PREFIX.length()).split(":", 2);
      if (parts.length != 2) {
        return null;
      }
      byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
      byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
      if (iv.length != IV_LENGTH) {
        return null;
      }
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(ciphertext), "UTF-8");
    } catch (Throwable t) {
      Log.e("Cannot decrypt WebApp secure-storage value", t);
      return null;
    }
  }
}
