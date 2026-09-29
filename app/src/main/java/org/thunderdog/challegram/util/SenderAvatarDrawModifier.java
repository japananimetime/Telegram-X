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

import android.graphics.Canvas;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ComplexReceiverProvider;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

/**
 * Draws a {@link TdApi.MessageSender}'s avatar on the LEFT of a {@link org.thunderdog.challegram.component.base.SettingView}
 * row, vertically centered. Pair with {@code view.forcePadding(LEFT_PADDING, 0)} so the row text is
 * offset to the right of the avatar. {@link #getWidth()} returns 0 (the avatar sits in the left slot,
 * not reserved from the right text width).
 */
public class SenderAvatarDrawModifier implements DrawModifier {
  public static final int AVATAR_SIZE_DP = 40;
  // Text padding to pair with this modifier (avatar left margin + size + gap).
  public static final int LEFT_PADDING_DP = 64;

  private final TdApi.MessageSender sender;

  public SenderAvatarDrawModifier (TdApi.MessageSender sender) {
    this.sender = sender;
  }

  public SenderAvatarDrawModifier requestFiles (ComplexReceiver complexReceiver, Tdlib tdlib) {
    AvatarReceiver avatarReceiver = complexReceiver.getAvatarReceiver(0);
    avatarReceiver.requestMessageSender(tdlib, sender, AvatarReceiver.Options.NONE);
    return this;
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    ComplexReceiver complexReceiver = view instanceof ComplexReceiverProvider ? ((ComplexReceiverProvider) view).getComplexReceiver() : null;
    if (complexReceiver == null) {
      return;
    }
    AvatarReceiver avatarReceiver = complexReceiver.getAvatarReceiver(0);
    if (avatarReceiver.isEmpty()) {
      return;
    }
    int size = Screen.dp(AVATAR_SIZE_DP);
    int x = Screen.dp(13f);
    int y = (view.getMeasuredHeight() - size) / 2;
    avatarReceiver.setBounds(x, y, x + size, y + size);
    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.draw(c);
  }

  @Override
  public int getWidth () {
    return 0;
  }
}
