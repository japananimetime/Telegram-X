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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.util.ArrayList;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.lambda.Destroyable;

/**
 * Renders {@link TdApi.MessageRichMessage} inside a chat bubble
 * by reusing the Instant View {@link PageBlock} renderers.
 *
 * TODO(rich-theming): blocks use {@code TextColorSets.InstantView.*} palettes; they adapt
 * to the current theme, but do not follow bubbleIn/bubbleOut-specific text colors.
 * TODO(rich-rtl): {@code richMessage.isRtl} is forwarded to the parser, but per-paragraph
 * direction is currently handled by {@code Text} auto-detection only, same as Instant View.
 */
public class TGMessageRich extends TGMessage implements ClickHelper.Delegate {
  private static final int MEDIA_MODE_NONE = 0;
  private static final int MEDIA_MODE_IMAGE = 1;
  private static final int MEDIA_MODE_GIF = 2;

  private TdApi.RichMessage richMessage;
  private final ArrayList<PageBlock> blocks = new ArrayList<>();
  private final ClickHelper clickHelper = new ClickHelper(this);

  private int contentWidth, contentHeight;

  // "Show more" affordance for truncated (isFull == false) messages
  private @Nullable Text showMoreText;
  private boolean showMoreLoading;

  public TGMessageRich (MessagesManager context, TdApi.Message msg, @NonNull TdApi.RichMessage richMessage) {
    super(context, msg);
    this.richMessage = richMessage;
    parseBlocks();
  }

  // == Parsing ==

  private void parseBlocks () {
    destroyBlocks();
    ArrayList<PageBlock> parsedBlocks;
    try {
      parsedBlocks = PageBlock.parseRichMessage(controller(), richMessage, openParameters());
    } catch (Throwable t) {
      Log.e("Cannot parse rich message blocks", t);
      parsedBlocks = new ArrayList<>(1);
      parsedBlocks.add(new PageBlockRichText(controller(), new TdApi.PageBlockParagraph(new TdApi.RichTextItalic(new TdApi.RichTextPlain(Lang.getString(R.string.UnsupportedMessageType)))), openParameters()));
    }
    blocks.addAll(parsedBlocks);
    // Re-attach freshly parsed blocks to the views this message is already displayed in
    for (View view : currentViews) {
      for (PageBlock block : blocks) {
        block.attachToView(view);
      }
    }
  }

  private void destroyBlocks () {
    for (PageBlock block : blocks) {
      for (View view : currentViews) {
        block.detachFromView(view);
      }
      if (block instanceof Destroyable) {
        ((Destroyable) block).performDestroy();
      }
    }
    blocks.clear();
  }

  private boolean needShowMore () {
    return !richMessage.isFull;
  }

  // == Layout ==

  @Override
  protected void buildContent (int maxWidth) {
    this.contentWidth = maxWidth;
    View view = findCurrentView();
    int totalHeight = 0;
    for (PageBlock block : blocks) {
      block.setViewWidthOverride(maxWidth);
      totalHeight += block.getHeight(view, maxWidth);
    }
    if (needShowMore()) {
      this.showMoreText = new Text.Builder(Lang.getString(R.string.RichMessageShowMore), maxWidth - getShowMorePaddingLeft(), PageBlockRichText.getParagraphProvider(), getShowMoreColorSet())
        .singleLine()
        .build();
      totalHeight += getShowMoreHeight();
    } else {
      this.showMoreText = null;
    }
    this.contentHeight = totalHeight;
  }

  @Override
  protected int getContentWidth () {
    // PageBlocks are width-takers: occupy the maximum available bubble width
    return contentWidth;
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }

  private int getBlocksHeight () {
    return contentHeight - (showMoreText != null ? getShowMoreHeight() : 0);
  }

  private int getShowMorePaddingLeft () {
    return Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET);
  }

  private int getShowMoreHeight () {
    return showMoreText != null ? showMoreText.getHeight() + Screen.dp(12f) : 0;
  }

  private TextColorSet getShowMoreColorSet () {
    if (!useBubbles()) {
      return TextColorSets.Regular.LINK;
    }
    return isOutgoingBubble() ? TextColorSets.BubbleOut.LINK : TextColorSets.BubbleIn.LINK;
  }

  // == Media ==

  private static int getMediaMode (PageBlock block) {
    switch (block.getRelatedViewType()) {
      case ListItem.TYPE_PAGE_BLOCK_MEDIA:
      case ListItem.TYPE_PAGE_BLOCK_AVATAR:
        return MEDIA_MODE_IMAGE;
      case ListItem.TYPE_PAGE_BLOCK_GIF:
        return MEDIA_MODE_GIF;
    }
    return MEDIA_MODE_NONE;
  }

  private static long previewKey (int blockIndex) {
    return blockIndex * 2L;
  }

  private static long contentKey (int blockIndex) {
    return blockIndex * 2L + 1L;
  }

  @Override
  public boolean needComplexReceiver () {
    return true;
  }

  @Override
  public void requestMediaContent (ComplexReceiver receiver, boolean invalidate, int invalidateArg) {
    final int blockCount = blocks.size();
    for (int i = 0; i < blockCount; i++) {
      PageBlock block = blocks.get(i);
      switch (getMediaMode(block)) {
        case MEDIA_MODE_IMAGE: {
          if (!invalidate) {
            block.requestPreview(receiver.getPreviewReceiver(previewKey(i)));
          }
          block.requestImage(receiver.getImageReceiver(contentKey(i)));
          break;
        }
        case MEDIA_MODE_GIF: {
          if (!invalidate) {
            block.requestPreview(receiver.getPreviewReceiver(previewKey(i)));
          }
          block.requestGif(receiver.getGifReceiver(contentKey(i)));
          break;
        }
      }
    }
    receiver.clearReceiversWithHigherKey(blockCount * 2L);
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    // TODO(rich-media): PageBlock.requestIcons does not support key offsets, so RichTextIcon
    // keys may collide if multiple blocks contain inline icons (last block wins)
    boolean requested = false;
    for (PageBlock block : blocks) {
      if (block instanceof PageBlockRichText || block instanceof PageBlockTable) {
        block.requestIcons(textMediaReceiver);
        requested = true;
      }
    }
    if (!requested) {
      textMediaReceiver.clear();
    }
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    for (PageBlock block : blocks) {
      block.autoDownloadContent();
    }
  }

  // == Drawing ==

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    drawContent(view, c, startX, startY, maxWidth, null);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, @Nullable ComplexReceiver receiver) {
    final ComplexReceiver iconReceiver = view.getTextMediaReceiver();
    final int blockCount = blocks.size();
    int y = startY;
    for (int i = 0; i < blockCount; i++) {
      PageBlock block = blocks.get(i);
      final int blockHeight = block.getHeight(view, contentWidth);
      final int mediaMode = getMediaMode(block);
      Receiver preview = null, content = null;
      if (mediaMode != MEDIA_MODE_NONE) {
        if (receiver == null) {
          // Media blocks cannot be drawn without their receivers
          y += blockHeight;
          continue;
        }
        preview = receiver.getPreviewReceiver(previewKey(i));
        content = mediaMode == MEDIA_MODE_GIF ? receiver.getGifReceiver(contentKey(i)) : receiver.getImageReceiver(contentKey(i));
      }
      final int saveCount = Views.save(c);
      c.translate(startX, y);
      block.draw(view, c, preview, content, iconReceiver);
      Views.restore(c, saveCount);
      y += blockHeight;
    }
    if (showMoreText != null) {
      showMoreText.draw(c, startX + getShowMorePaddingLeft(), startX + maxWidth, 0, y + Screen.dp(6f), null, showMoreLoading ? 0.6f : 1f);
    }
  }

  // == Touch ==

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) {
      return true;
    }
    if (clickHelper.onTouchEvent(view, e)) {
      return true;
    }
    final int startX = getContentX();
    int y = getContentY();
    for (PageBlock block : blocks) {
      final int blockHeight = block.getHeight(view, contentWidth);
      e.offsetLocation(-startX, -y);
      boolean result = block.onTouchEvent(view, e);
      e.offsetLocation(startX, y);
      if (result) {
        return true;
      }
      y += blockHeight;
    }
    return false;
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    clickHelper.cancel(view, x, y);
    return super.performLongPress(view, x, y);
  }

  private static final int CLICK_TARGET_NONE = 0;
  private static final int CLICK_TARGET_SHOW_MORE = 1;
  private static final int CLICK_TARGET_DETAILS = 2;

  private int findClickTarget (float x, float y) {
    final float localX = x - getContentX();
    final float localY = y - getContentY();
    if (localX < 0 || localX >= contentWidth || localY < 0 || localY >= contentHeight) {
      return CLICK_TARGET_NONE;
    }
    if (showMoreText != null && !showMoreLoading && localY >= getBlocksHeight()) {
      return CLICK_TARGET_SHOW_MORE;
    }
    PageBlock block = findBlockAt(localY);
    if (block instanceof PageBlockRichText && block.isClickable() && block.getOriginalBlock() != null &&
      block.getOriginalBlock().getConstructor() == TdApi.PageBlockDetails.CONSTRUCTOR) {
      return CLICK_TARGET_DETAILS;
    }
    return CLICK_TARGET_NONE;
  }

  private @Nullable PageBlock findBlockAt (float localY) {
    int y = 0;
    View view = findCurrentView();
    for (PageBlock block : blocks) {
      int blockHeight = block.getHeight(view, contentWidth);
      if (localY >= y && localY < y + blockHeight) {
        return block;
      }
      y += blockHeight;
    }
    return null;
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return findClickTarget(x, y) != CLICK_TARGET_NONE;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    switch (findClickTarget(x, y)) {
      case CLICK_TARGET_SHOW_MORE: {
        loadFullMessage();
        break;
      }
      case CLICK_TARGET_DETAILS: {
        PageBlock block = findBlockAt(y - getContentY());
        if (block instanceof PageBlockRichText) {
          TdApi.PageBlockDetails details = (TdApi.PageBlockDetails) block.getOriginalBlock();
          details.isOpen = !details.isOpen;
          parseBlocks();
          rebuildAndUpdateContent();
          invalidateContentReceiver();
          invalidateTextMediaReceiver();
        }
        break;
      }
    }
  }

  // == "Show more" (truncated messages) ==

  private void loadFullMessage () {
    if (showMoreLoading || richMessage.isFull) {
      return;
    }
    showMoreLoading = true;
    invalidate();
    tdlib.send(new TdApi.GetFullRichMessage(msg.chatId, msg.id), (fullRichMessage, error) -> runOnUiThreadOptional(() -> {
      showMoreLoading = false;
      if (error != null) {
        UI.showError(error);
        invalidate();
      } else {
        setRichMessage(fullRichMessage);
      }
    }));
  }

  private void setRichMessage (@NonNull TdApi.RichMessage newRichMessage) {
    this.richMessage = newRichMessage;
    if (msg.content != null && msg.content.getConstructor() == TdApi.MessageRichMessage.CONSTRUCTOR) {
      ((TdApi.MessageRichMessage) msg.content).message = newRichMessage;
    }
    parseBlocks();
    rebuildAndUpdateContent();
    invalidateContentReceiver();
    invalidateTextMediaReceiver();
  }

  // == Updates & lifecycle ==

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (newContent.getConstructor() != TdApi.MessageRichMessage.CONSTRUCTOR) {
      return false;
    }
    this.msg.content = newContent;
    setRichMessage(((TdApi.MessageRichMessage) newContent).message);
    return true;
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    for (PageBlock block : blocks) {
      if (attached) {
        block.attachToView(view);
      } else {
        block.detachFromView(view);
      }
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    destroyBlocks();
  }
}
