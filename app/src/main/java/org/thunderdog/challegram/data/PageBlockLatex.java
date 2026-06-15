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
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;

import me.vkryl.core.StringUtils;
import ru.noties.jlatexmath.JLatexMathDrawable;

/**
 * Renders a {@link TdApi.PageBlockMathematicalExpression} as true typeset math using the vendored
 * jlatexmath engine ({@link JLatexMathDrawable}). The drawable is rebuilt when the theme text color
 * changes, and scaled down to fit the bubble width when the formula is too wide.
 */
public class PageBlockLatex extends PageBlock {
  private final String expression;
  private @Nullable JLatexMathDrawable drawable;
  private boolean built;
  private int builtColor;
  private float scale = 1f;

  public PageBlockLatex (ViewController<?> context, TdApi.PageBlockMathematicalExpression block) {
    super(context, block);
    this.expression = !StringUtils.isEmpty(block.expression) ? block.expression : "";
  }

  private void ensureDrawable () {
    int color = Theme.getColor(ColorId.iv_text);
    if (built && builtColor == color) {
      return;
    }
    built = true;
    builtColor = color;
    try {
      drawable = JLatexMathDrawable.builder(expression)
        .textSize(Screen.dp(17f))
        .color(color)
        .align(JLatexMathDrawable.ALIGN_LEFT)
        .build();
    } catch (Throwable t) {
      Log.e("Cannot render LaTeX expression: %s", t, expression);
      drawable = null;
    }
  }

  @Override
  public int getRelatedViewType () {
    return ListItem.TYPE_PAGE_BLOCK;
  }

  @Override
  protected int getContentTop () {
    return Screen.dp(8f);
  }

  @Override
  protected int getContentHeight () {
    return drawable != null ? Math.round(drawable.getIntrinsicHeight() * scale) : 0;
  }

  @Override
  protected int computeHeight (View view, int width) {
    ensureDrawable();
    if (drawable == null) {
      return getContentTop() * 2;
    }
    int avail = width - getMinimumContentPadding(true) - getMinimumContentPadding(false);
    int intrinsicWidth = drawable.getIntrinsicWidth();
    scale = (intrinsicWidth > avail && intrinsicWidth > 0) ? (float) avail / intrinsicWidth : 1f;
    return Math.round(drawable.getIntrinsicHeight() * scale) + getContentTop() * 2;
  }

  @Override
  protected boolean handleTouchEvent (View view, MotionEvent e) {
    return false;
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver iconReceiver) {
    ensureDrawable();
    if (drawable == null) {
      return;
    }
    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    final int saveCount = Views.save(c);
    c.translate(getMinimumContentPadding(true), getContentTop());
    if (scale != 1f) {
      c.scale(scale, scale);
    }
    drawable.draw(c);
    Views.restore(c, saveCount);
  }
}
