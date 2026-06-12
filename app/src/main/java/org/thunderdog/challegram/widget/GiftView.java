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
 * File created for received gifts surface (Slice 2)
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import tgx.td.Td;

/**
 * Grid cell that renders a single {@link TdApi.ReceivedGift}: the gift sticker
 * (animated when available, static otherwise), the gift's star value, and a
 * pinned indicator. Lifecycle-aware: attach()/detach() forward to the receiver.
 *
 * Drawing state is hoisted - never allocate in onDraw().
 */
public class GiftView extends View implements AttachDelegate {
  private static final int STICKER_SIZE_DP = 72;
  private static final int RADIUS_DP = 12;

  private final ComplexReceiver receiver;
  private final RectF backgroundRect = new RectF();
  private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint pinDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private @Nullable Tdlib tdlib;
  private @Nullable TdApi.ReceivedGift gift;
  private @Nullable TdApi.AvailableGift availableGift;
  private @Nullable TdApi.GiftForResale resaleGift;

  private boolean animated;
  private @Nullable GifFile gifFile;
  private @Nullable ImageFile imageFile;

  private @Nullable String starText;
  private boolean isPinned;
  private boolean isDimmed;

  public GiftView (Context context) {
    super(context);
    this.receiver = new ComplexReceiver(this);
  }

  public void setGift (@Nullable Tdlib tdlib, @Nullable TdApi.ReceivedGift gift) {
    this.tdlib = tdlib;
    this.gift = gift;
    this.availableGift = null;
    this.resaleGift = null;
    this.animated = false;
    this.gifFile = null;
    this.imageFile = null;
    this.starText = null;
    this.isPinned = false;
    this.isDimmed = false;

    if (gift != null && tdlib != null) {
      this.isPinned = gift.isPinned;
      TdApi.Sticker sticker = stickerOf(gift.gift);
      long starCount = starCountOf(gift.gift);
      if (starCount > 0) {
        this.starText = Long.toString(starCount);
      }
      if (sticker != null) {
        this.animated = Td.isAnimated(sticker.format);
        if (animated) {
          GifFile gif = new GifFile(tdlib, sticker);
          gif.setScaleType(GifFile.FIT_CENTER);
          gif.setPlayOnce();
          this.gifFile = gif;
        } else {
          ImageFile img = new ImageFile(tdlib, sticker.sticker);
          img.setScaleType(ImageFile.FIT_CENTER);
          this.imageFile = img;
        }
      }
    }
    requestFiles();
    invalidate();
  }

  public @Nullable TdApi.ReceivedGift getGift () {
    return gift;
  }

  public void setAvailableGift (@Nullable Tdlib tdlib, @Nullable TdApi.AvailableGift availableGift, boolean dimmed) {
    this.tdlib = tdlib;
    this.availableGift = availableGift;
    this.gift = null;
    this.resaleGift = null;
    this.animated = false;
    this.gifFile = null;
    this.imageFile = null;
    this.starText = null;
    this.isPinned = false;
    this.isDimmed = dimmed;

    if (availableGift != null && availableGift.gift != null && tdlib != null) {
      TdApi.Gift g = availableGift.gift;
      if (g.starCount > 0) {
        this.starText = Long.toString(g.starCount);
      }
      TdApi.Sticker sticker = g.sticker;
      if (sticker != null) {
        this.animated = Td.isAnimated(sticker.format);
        if (animated) {
          GifFile gif = new GifFile(tdlib, sticker);
          gif.setScaleType(GifFile.FIT_CENTER);
          gif.setPlayOnce();
          this.gifFile = gif;
        } else {
          ImageFile img = new ImageFile(tdlib, sticker.sticker);
          img.setScaleType(ImageFile.FIT_CENTER);
          this.imageFile = img;
        }
      }
    }
    requestFiles();
    invalidate();
  }

  public @Nullable TdApi.AvailableGift getAvailableGift () {
    return availableGift;
  }

  /**
   * Renders a gift listed for resale ({@link TdApi.GiftForResale}). The sticker
   * is the upgraded gift's model; {@code priceText} (if non-null) is drawn below
   * in place of the star value.
   */
  public void setResaleGift (@Nullable Tdlib tdlib, @Nullable TdApi.GiftForResale resaleGift, @Nullable String priceText) {
    this.tdlib = tdlib;
    this.resaleGift = resaleGift;
    this.gift = null;
    this.availableGift = null;
    this.animated = false;
    this.gifFile = null;
    this.imageFile = null;
    this.starText = priceText;
    this.isPinned = false;
    this.isDimmed = false;

    if (resaleGift != null && resaleGift.gift != null && resaleGift.gift.model != null && tdlib != null) {
      TdApi.Sticker sticker = resaleGift.gift.model.sticker;
      if (sticker != null) {
        this.animated = Td.isAnimated(sticker.format);
        if (animated) {
          GifFile gif = new GifFile(tdlib, sticker);
          gif.setScaleType(GifFile.FIT_CENTER);
          gif.setPlayOnce();
          this.gifFile = gif;
        } else {
          ImageFile img = new ImageFile(tdlib, sticker.sticker);
          img.setScaleType(ImageFile.FIT_CENTER);
          this.imageFile = img;
        }
      }
    }
    requestFiles();
    invalidate();
  }

  public @Nullable TdApi.GiftForResale getResaleGift () {
    return resaleGift;
  }

  private static @Nullable TdApi.Sticker stickerOf (@Nullable TdApi.SentGift sentGift) {
    if (sentGift == null) {
      return null;
    }
    switch (sentGift.getConstructor()) {
      case TdApi.SentGiftRegular.CONSTRUCTOR: {
        TdApi.Gift g = ((TdApi.SentGiftRegular) sentGift).gift;
        return g != null ? g.sticker : null;
      }
      case TdApi.SentGiftUpgraded.CONSTRUCTOR: {
        TdApi.UpgradedGift g = ((TdApi.SentGiftUpgraded) sentGift).gift;
        return g != null && g.model != null ? g.model.sticker : null;
      }
    }
    return null;
  }

  private static long starCountOf (@Nullable TdApi.SentGift sentGift) {
    if (sentGift != null && sentGift.getConstructor() == TdApi.SentGiftRegular.CONSTRUCTOR) {
      TdApi.Gift g = ((TdApi.SentGiftRegular) sentGift).gift;
      return g != null ? g.starCount : 0;
    }
    return 0;
  }

  private void requestFiles () {
    if (animated) {
      receiver.getGifReceiver(0).requestFile(gifFile);
      receiver.getImageReceiver(0).requestFile(null);
    } else if (imageFile != null) {
      receiver.getGifReceiver(0).requestFile(null);
      receiver.getImageReceiver(0).requestFile(imageFile);
    } else {
      receiver.getGifReceiver(0).requestFile(null);
      receiver.getImageReceiver(0).requestFile(null);
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
  protected void onDraw (Canvas c) {
    final int width = getMeasuredWidth();
    final int height = getMeasuredHeight();
    if (width == 0 || height == 0) {
      return;
    }

    final int stickerSize = Screen.dp(STICKER_SIZE_DP);
    final int cx = width / 2;
    final int top = Screen.dp(8);

    // Rounded background behind the sticker
    final int bgPadding = Screen.dp(6);
    backgroundRect.set(cx - stickerSize / 2f - bgPadding, top - bgPadding,
      cx + stickerSize / 2f + bgPadding, top + stickerSize + bgPadding);
    backgroundPaint.setColor(Theme.getColor(ColorId.filling));
    final float r = Screen.dp(RADIUS_DP);
    c.drawRoundRect(backgroundRect, r, r, backgroundPaint);

    if (animated || imageFile != null) {
      Receiver rec = animated ? receiver.getGifReceiver(0) : receiver.getImageReceiver(0);
      final int sx = cx - stickerSize / 2;
      rec.setBounds(sx, top, sx + stickerSize, top + stickerSize);
      if (isDimmed) {
        rec.setAlpha(0.4f);
      } else {
        rec.setAlpha(1f);
      }
      rec.draw(c);
    }

    // Pinned indicator
    if (isPinned) {
      pinDotPaint.setColor(Theme.getColor(ColorId.iconActive));
      final float dotRadius = Screen.dp(4);
      c.drawCircle(backgroundRect.right - Screen.dp(2), backgroundRect.top + Screen.dp(2), dotRadius, pinDotPaint);
    }

    // Star value below the sticker
    if (starText != null) {
      TextPaint paint = Paints.getBoldPaint13(false);
      paint.setColor(Theme.getColor(ColorId.textLight));
      final float textWidth = paint.measureText(starText);
      final int starY = top + stickerSize + bgPadding + Screen.dp(15);
      c.drawText(starText, cx - textWidth / 2f, starY, paint);
    }
  }
}
