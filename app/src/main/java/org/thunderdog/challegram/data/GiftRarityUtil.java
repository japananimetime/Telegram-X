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
 * File created for upgraded gift attribute formatting
 */
package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

/**
 * Helpers for formatting upgraded-gift attributes (rarity, resale price)
 * shared by the upgraded-gift message renderers.
 */
public final class GiftRarityUtil {
  private GiftRarityUtil () { }

  /**
   * Human-readable rarity label, or {@code null} if unknown.
   * PerMille is rendered as a percentage ("0.5%"), the named rarities use their
   * localized labels.
   */
  public static @Nullable String rarityLabel (@Nullable TdApi.UpgradedGiftAttributeRarity rarity) {
    if (rarity == null) {
      return null;
    }
    switch (rarity.getConstructor()) {
      case TdApi.UpgradedGiftAttributeRarityPerMille.CONSTRUCTOR: {
        int perMille = ((TdApi.UpgradedGiftAttributeRarityPerMille) rarity).perMille;
        if (perMille <= 0) {
          return "<0.1%";
        }
        // perMille is per-1000; convert to percent with up to one decimal place.
        if (perMille % 10 == 0) {
          return (perMille / 10) + "%";
        }
        return String.format(java.util.Locale.US, "%.1f%%", perMille / 10f);
      }
      case TdApi.UpgradedGiftAttributeRarityUncommon.CONSTRUCTOR:
        return Lang.getString(R.string.UpgradedGiftRarityUncommon);
      case TdApi.UpgradedGiftAttributeRarityRare.CONSTRUCTOR:
        return Lang.getString(R.string.UpgradedGiftRarityRare);
      case TdApi.UpgradedGiftAttributeRarityEpic.CONSTRUCTOR:
        return Lang.getString(R.string.UpgradedGiftRarityEpic);
      case TdApi.UpgradedGiftAttributeRarityLegendary.CONSTRUCTOR:
        return Lang.getString(R.string.UpgradedGiftRarityLegendary);
      default:
        return null;
    }
  }

  /**
   * Formats a {@link TdApi.GiftResalePrice} as a user-facing string.
   * Star -> "N Stars" (plural), Ton -> "N.NN TON".
   */
  public static @Nullable String priceLabel (@Nullable TdApi.GiftResalePrice price) {
    if (price == null) {
      return null;
    }
    switch (price.getConstructor()) {
      case TdApi.GiftResalePriceStar.CONSTRUCTOR: {
        long starCount = ((TdApi.GiftResalePriceStar) price).starCount;
        return Lang.plural(R.string.xStars, starCount);
      }
      case TdApi.GiftResalePriceTon.CONSTRUCTOR: {
        long cents = ((TdApi.GiftResalePriceTon) price).toncoinCentCount;
        return Lang.getString(R.string.GiftedTonAmount, formatTon(cents, 2));
      }
      default:
        return null;
    }
  }

  /**
   * Formats a TON amount given in {@code 1/divisorScale} units.
   * For toncoin cents pass scale {@code 2} (1/100 TON); for nanoton-style amounts
   * pass a higher scale. Trims trailing zeros down to at most {@code maxFractionDigits}.
   */
  public static String formatTon (long amount, int maxFractionDigits) {
    double value = amount / Math.pow(10, maxFractionDigits);
    String formatted = String.format(java.util.Locale.US, "%." + maxFractionDigits + "f", value);
    return trimFraction(formatted);
  }

  /**
   * Formats a nanoton amount (the "smallest units" used by {@code TonTransaction.tonAmount},
   * 1 TON = 1e9 nanotons) as trimmed decimal digits with up to 4 fraction digits.
   * Sign is dropped; callers add their own +/- prefix.
   */
  public static String formatTonNano (long nanotons) {
    double value = Math.abs(nanotons) / 1_000_000_000.0;
    String formatted = String.format(java.util.Locale.US, "%.4f", value);
    return trimFraction(formatted);
  }

  // Trim trailing zeros and a trailing dot for cleaner display.
  private static String trimFraction (String formatted) {
    if (formatted.contains(".")) {
      int end = formatted.length();
      while (end > 0 && formatted.charAt(end - 1) == '0') {
        end--;
      }
      if (end > 0 && formatted.charAt(end - 1) == '.') {
        end--;
      }
      formatted = formatted.substring(0, end);
    }
    return formatted;
  }
}
