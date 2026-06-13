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
 * File created for the gift-auction detail screen (Slice 6)
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import tgx.td.Td;

/**
 * Hero card for the gift-auction detail screen. Renders the gift's background
 * gradient ({@link TdApi.GiftBackground} centerColor -&gt; edgeColor) when present,
 * the gift sticker centered, and a title + caption beneath.
 *
 * Mirrors {@link UpgradedGiftHeaderView}: all paints/gradients/receivers are
 * hoisted and never allocated in {@link #onDraw(Canvas)}.
 */
public class GiftAuctionHeaderView extends View implements AttachDelegate {
  private static final int CARD_RADIUS_DP = 18;
  private static final int MODEL_SIZE_DP = 130;
  private static final int CARD_VPADDING_DP = 20;
  private static final int CARD_HPADDING_DP = 16;

  private static final long MODEL_KEY = 0;

  private final ComplexReceiver receiver;
  private final RectF cardRect = new RectF();
  private final Paint backdropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private @Nullable Tdlib tdlib;

  private boolean modelAnimated;
  private @Nullable GifFile modelGif;
  private @Nullable ImageFile modelImage;

  private boolean hasBackdrop;
  private int centerColor, edgeColor;
  private @Nullable String title;
  private @Nullable String caption;

  // gradient cache
  private RadialGradient cachedGradient;
  private float cachedCx, cachedCy, cachedRadius;

  public GiftAuctionHeaderView (Context context) {
    super(context);
    this.receiver = new ComplexReceiver(this);
  }

  public void setGift (@Nullable Tdlib tdlib, @Nullable TdApi.Gift gift, @Nullable String title, @Nullable String caption) {
    this.tdlib = tdlib;
    this.modelAnimated = false;
    this.modelGif = null;
    this.modelImage = null;
    this.hasBackdrop = false;
    this.title = title;
    this.caption = caption;
    this.cachedGradient = null;

    if (gift != null && tdlib != null) {
      TdApi.GiftBackground background = gift.background;
      if (background != null) {
        hasBackdrop = true;
        centerColor = 0xFF000000 | background.centerColor;
        edgeColor = 0xFF000000 | background.edgeColor;
      }
      TdApi.Sticker sticker = gift.sticker;
      if (sticker != null) {
        modelAnimated = Td.isAnimated(sticker.format);
        if (modelAnimated) {
          GifFile g = new GifFile(tdlib, sticker);
          g.setScaleType(GifFile.FIT_CENTER);
          g.setPlayOnce();
          modelGif = g;
        } else {
          ImageFile img = new ImageFile(tdlib, sticker.sticker);
          img.setScaleType(ImageFile.FIT_CENTER);
          modelImage = img;
        }
      }
    }
    requestFiles();
    invalidate();
  }

  private void requestFiles () {
    if (modelAnimated) {
      receiver.getGifReceiver(MODEL_KEY).requestFile(modelGif);
      receiver.getImageReceiver(MODEL_KEY).requestFile(null);
    } else {
      receiver.getGifReceiver(MODEL_KEY).requestFile(null);
      receiver.getImageReceiver(MODEL_KEY).requestFile(modelImage);
    }
  }

  @Override
  public void attach () {
    receiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
  }

  public void performDestroy () {
    receiver.performDestroy();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = Screen.dp(MODEL_SIZE_DP) + Screen.dp(CARD_VPADDING_DP) * 2 + Screen.dp(64);
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onDraw (Canvas c) {
    final int width = getMeasuredWidth();
    if (width == 0) {
      return;
    }
    final int hPad = Screen.dp(CARD_HPADDING_DP);
    final int vPad = Screen.dp(CARD_VPADDING_DP);
    final int modelSize = Screen.dp(MODEL_SIZE_DP);
    final int cardHeight = modelSize + vPad * 2;

    cardRect.set(hPad, vPad, width - hPad, vPad + cardHeight);
    final float r = Screen.dp(CARD_RADIUS_DP);

    if (hasBackdrop) {
      final float cx = cardRect.centerX();
      final float cy = cardRect.centerY();
      final float radius = Math.max(cardRect.width(), cardRect.height()) / 2f;
      if (cachedGradient == null || cachedCx != cx || cachedCy != cy || cachedRadius != radius) {
        cachedGradient = new RadialGradient(cx, cy, radius, centerColor, edgeColor, Shader.TileMode.CLAMP);
        cachedCx = cx;
        cachedCy = cy;
        cachedRadius = radius;
      }
      backdropPaint.setShader(cachedGradient);
      c.drawRoundRect(cardRect, r, r, backdropPaint);
    } else {
      backdropPaint.setShader(null);
      backdropPaint.setColor(Theme.getColor(ColorId.filling));
      c.drawRoundRect(cardRect, r, r, backdropPaint);
    }

    // Gift sticker centered
    if (modelAnimated || modelImage != null) {
      Receiver rec = modelAnimated ? receiver.getGifReceiver(MODEL_KEY) : receiver.getImageReceiver(MODEL_KEY);
      final int sx = (int) cardRect.centerX() - modelSize / 2;
      final int sy = (int) cardRect.centerY() - modelSize / 2;
      rec.setBounds(sx, sy, sx + modelSize, sy + modelSize);
      rec.draw(c);
    }

    // Title + caption beneath the card
    float textY = cardRect.bottom + Screen.dp(24);
    if (title != null) {
      TextPaint paint = Paints.getBoldPaint15(false);
      paint.setColor(Theme.getColor(ColorId.text));
      final float tw = paint.measureText(title);
      c.drawText(title, (width - tw) / 2f, textY, paint);
      textY += Screen.dp(22);
    }
    if (caption != null) {
      TextPaint paint = Paints.getRegularTextPaint(14f);
      paint.setColor(Theme.getColor(ColorId.textLight));
      final float tw = paint.measureText(caption);
      c.drawText(caption, (width - tw) / 2f, textY, paint);
    }
  }
}
