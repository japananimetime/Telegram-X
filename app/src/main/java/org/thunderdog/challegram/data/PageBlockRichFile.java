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
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;

import me.vkryl.core.lambda.Destroyable;

/**
 * Renders an audio track or voice note inside a rich message ({@link TGMessageRich}) using the
 * interactive, canvas-drawable {@link FileComponent} (play/pause button + progress + seek), wired
 * to the audio player. Unlike {@link PageBlockFile} (which renders via a RecyclerView inline view
 * in Instant View), this draws itself on the message canvas.
 *
 * The audio is a sub-block of the message rather than a standalone audio message, so playback goes
 * through {@link FileComponent}'s synthetic-message fallback (its {@code playPauseFile == null}).
 */
public class PageBlockRichFile extends PageBlock implements Destroyable {
  private final FileComponent component;

  public PageBlockRichFile (ViewController<?> context, TGMessage message, TdApi.PageBlockAudio audioBlock) {
    super(context, audioBlock);
    this.component = new FileComponent(message, message.getMessage(), audioBlock.audio, null, message.manager);
    this.component.setViewProvider(message.currentViews);
  }

  public PageBlockRichFile (ViewController<?> context, TGMessage message, TdApi.PageBlockVoiceNote voiceBlock) {
    super(context, voiceBlock);
    this.component = new FileComponent(message, message.getMessage(), voiceBlock.voiceNote, null, message.manager);
    this.component.setViewProvider(message.currentViews);
  }

  @Override
  public int getRelatedViewType () {
    // Reuse the media view type so TGMessageRich allocates preview + image receivers for this block.
    return ListItem.TYPE_PAGE_BLOCK_MEDIA;
  }

  @Override
  protected int getContentTop () {
    return Screen.dp(8f);
  }

  @Override
  protected int getContentHeight () {
    return component.getHeight();
  }

  @Override
  protected int computeHeight (View view, int width) {
    component.buildLayout(width - getMinimumContentPadding(true) - getMinimumContentPadding(false));
    return component.getHeight() + getContentTop() * 2;
  }

  @Override
  protected boolean handleTouchEvent (View view, MotionEvent e) {
    return component.onTouchEvent(view, e);
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver iconReceiver) {
    component.draw(view, c, getMinimumContentPadding(true), getContentTop(), preview, receiver, 0, 0, 1f, 0f);
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    component.requestPreview(receiver);
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    receiver.clear();
  }

  @Override
  public void performDestroy () {
    component.performDestroy();
  }
}
