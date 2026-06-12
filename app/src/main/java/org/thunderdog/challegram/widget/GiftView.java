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
  private @Nullable TdApi.GiftAuctionState auctionGift;

  private boolean animated;
  private @Nullable GifFile gifFile;
  private @Nullable ImageFile imageFile;

  private @Nullable String starText;
  private boolean isPinned;
  private boolean isDimmed;

  // Multi-select state (used by the craft picker, Slice 8). When checked, a
  // tinted border + a check badge are drawn over the cell; the "primary" flag
  // additionally marks the gift whose number is kept by a craft.
  private boolean isChecked;
  private boolean isPrimary;
  private final Paint selectionBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint checkBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public GiftView (Context context) {
    super(context);
    this.receiver = new ComplexReceiver(this);
  }

  /**
   * Marks this cell as selected/primary for the multi-select craft picker
   * (Slice 8). {@code primary} implies {@code checked}.
   */
  public void setCheckedState (boolean checked, boolean primary) {
    if (this.isChecked != checked || this.isPrimary != primary) {
      this.isChecked = checked;
      this.isPrimary = primary;
      invalidate();
    }
  }

  public void setGift (@Nullable Tdlib tdlib, @Nullable TdApi.ReceivedGift gift) {
    this.tdlib = tdlib;
    this.gift = gift;
    this.availableGift = null;
    this.resaleGift = null;
    this.auctionGift = null;
    this.animated = false;
    this.gifFile = null;
    this.imageFile = null;
    this.starText = null;
    this.isPinned = false;
    this.isDimmed = false;
    this.isChecked = false;
    this.isPrimary = false;

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
    this.auctionGift = null;
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
    this.auctionGift = null;
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

  /**
   * Renders a gift currently on auction ({@link TdApi.GiftAuctionState}). The
   * sticker is the gift's own sticker; {@code bidText} (if non-null) is drawn
   * below in place of the star value (e.g. the current min/your bid).
   */
  public void setAuctionGift (@Nullable Tdlib tdlib, @Nullable TdApi.GiftAuctionState auctionGift, @Nullable String bidText) {
    this.tdlib = tdlib;
    this.auctionGift = auctionGift;
    this.gift = null;
    this.availableGift = null;
    this.resaleGift = null;
    this.animated = false;
    this.gifFile = null;
    this.imageFile = null;
    this.starText = bidText;
    this.isPinned = false;
    this.isDimmed = false;

    if (auctionGift != null && auctionGift.gift != null && tdlib != null) {
      TdApi.Sticker sticker = auctionGift.gift.sticker;
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

  public @Nullable TdApi.GiftAuctionState getAuctionGift () {
    return auctionGift;
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

    // Selection overlay (craft picker, Slice 8): a tinted border ring around the
    // background plus a check/star badge in the top-left corner.
    if (isChecked) {
      final int accent = Theme.getColor(isPrimary ? ColorId.iconActive : ColorId.fillingPositive);
      selectionBorderPaint.setStyle(Paint.Style.STROKE);
      selectionBorderPaint.setStrokeWidth(Math.max(2, Screen.dp(2)));
      selectionBorderPaint.setColor(accent);
      final float br = Screen.dp(RADIUS_DP);
      c.drawRoundRect(backgroundRect, br, br, selectionBorderPaint);

      final float badgeRadius = Screen.dp(9);
      final float badgeCx = backgroundRect.left + Screen.dp(2);
      final float badgeCy = backgroundRect.top + Screen.dp(2);
      checkBadgePaint.setStyle(Paint.Style.FILL);
      checkBadgePaint.setColor(accent);
      c.drawCircle(badgeCx, badgeCy, badgeRadius, checkBadgePaint);

      if (isPrimary) {
        // Primary marker: a star glyph (the crafted gift keeps this gift's number).
        TextPaint badgeText = Paints.getBoldPaint13(false);
        badgeText.setColor(0xFFFFFFFF);
        final String star = "★";
        final float tw = badgeText.measureText(star);
        c.drawText(star, badgeCx - tw / 2f, badgeCy + Screen.dp(4.5f), badgeText);
      } else {
        // Checkmark, drawn with two cached line strokes (no allocation).
        checkBadgePaint.setColor(0xFFFFFFFF);
        checkBadgePaint.setStyle(Paint.Style.STROKE);
        checkBadgePaint.setStrokeWidth(Math.max(1.5f, Screen.dp(1.5f)));
        final float l = badgeCx - Screen.dp(4);
        final float m = badgeCx - Screen.dp(1);
        final float rr = badgeCx + Screen.dp(4);
        c.drawLine(l, badgeCy + Screen.dp(0.5f), m, badgeCy + Screen.dp(3.5f), checkBadgePaint);
        c.drawLine(m, badgeCy + Screen.dp(3.5f), rr, badgeCy - Screen.dp(3.5f), checkBadgePaint);
      }
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
