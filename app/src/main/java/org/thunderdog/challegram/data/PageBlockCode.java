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
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.CodeSyntaxHighlighter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.core.StringUtils;

/**
 * Renders a {@link TdApi.PageBlockPreformatted} as a proper code block in a rich message: a rounded
 * filled box with the language name as a header line, then the monospace code. Replaces the earlier
 * "plain highlight + floating language label" look.
 */
public class PageBlockCode extends PageBlock {
  private final TextWrapper code;
  private final @Nullable String language;
  private @Nullable Text languageText;

  public PageBlockCode (ViewController<?> context, TdApi.PageBlockPreformatted block) {
    super(context, block);
    String codeText = TD.getText(block.text);
    TextEntity[] entities = CodeSyntaxHighlighter.highlight(codeText, block.language);
    this.code = new TextWrapper(codeText, PageBlockRichText.getPreformattedProvider(), TextColorSets.InstantView.NORMAL, entities, null);
    this.code.setViewProvider(currentViews);
    this.language = !StringUtils.isEmpty(block.language) ? block.language : null;
  }

  private static int horizontalPadding () {
    return Screen.dp(12f);
  }

  private static int verticalPadding () {
    return Screen.dp(10f);
  }

  private int headerHeight () {
    return language != null ? Screen.dp(20f) : 0;
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
    return verticalPadding() * 2 + headerHeight() + code.getHeight();
  }

  @Override
  protected int computeHeight (View view, int width) {
    int boxWidth = width - getMinimumContentPadding(true) - getMinimumContentPadding(false);
    int innerWidth = Math.max(1, boxWidth - horizontalPadding() * 2);
    code.prepare(innerWidth);
    languageText = language != null
      ? new Text.Builder(language, innerWidth, PageBlockRichText.getCaptionProvider(), TextColorSets.InstantView.CAPTION).singleLine().build()
      : null;
    return getContentTop() * 2 + getContentHeight();
  }

  @Override
  protected boolean handleTouchEvent (View view, MotionEvent e) {
    return false;
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver iconReceiver) {
    final int left = getMinimumContentPadding(true);
    final int right = getViewWidth(view) - getMinimumContentPadding(false);
    final int top = getContentTop();
    final int bottom = top + getContentHeight();

    RectF rect = Paints.getRectF();
    rect.set(left, top, right, bottom);
    final int radius = Screen.dp(6f);
    c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(Theme.getColor(ColorId.iv_preBlockBackground)));

    final int contentLeft = left + horizontalPadding();
    final int contentRight = right - horizontalPadding();
    int y = top + verticalPadding();
    if (languageText != null) {
      languageText.draw(c, contentLeft, y, null, 0.5f);
      y += headerHeight();
    }
    code.draw(c, contentLeft, contentRight, 0, y, null, 1f, iconReceiver);
  }
}
