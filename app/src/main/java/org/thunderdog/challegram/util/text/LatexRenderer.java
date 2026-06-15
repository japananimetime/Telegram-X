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
package org.thunderdog.challegram.util.text;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.StringUtils;
import ru.noties.jlatexmath.JLatexMathDrawable;

/**
 * Renders a LaTeX math expression to a white-on-transparent bitmap using the vendored jlatexmath
 * engine. The bitmap is intentionally drawn in pure white so callers can tint it to any theme text
 * color via a {@code SRC_IN} color filter at draw time (so inline math tracks theme changes without
 * being re-rendered). Returns {@code null} when the expression is empty or jlatexmath cannot parse
 * it, letting callers fall back to a plain-text rendering.
 */
public final class LatexRenderer {
  private LatexRenderer () { }

  // Rendered larger than display size for crispness; the inline layout scales the bitmap down to the
  // surrounding line height.
  private static int renderTextSize () {
    return Screen.dp(24f);
  }

  public static @Nullable Bitmap render (@Nullable String expression) {
    if (StringUtils.isEmpty(expression)) {
      return null;
    }
    try {
      JLatexMathDrawable drawable = JLatexMathDrawable.builder(expression)
        .textSize(renderTextSize())
        .color(Color.WHITE)
        .align(JLatexMathDrawable.ALIGN_LEFT)
        .build();
      int width = drawable.getIntrinsicWidth();
      int height = drawable.getIntrinsicHeight();
      if (width <= 0 || height <= 0) {
        return null;
      }
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(bitmap);
      drawable.setBounds(0, 0, width, height);
      drawable.draw(c);
      return bitmap;
    } catch (Throwable t) {
      Log.i("Cannot render inline LaTeX expression: %s", t, expression);
      return null;
    }
  }
}
