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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

/**
 * Editor for the Telegram Business "Chatbots" feature: a single bot connected to
 * the current business account that can manage chats on the user's behalf. On open
 * it loads any existing connection via {@link TdApi.GetBusinessConnectedBot}. The
 * user resolves a bot by username ({@link TdApi.SearchPublicChat}, must be a bot),
 * chooses the recipients (shared {@link BusinessRecipientsController}) and toggles
 * the bot's {@link TdApi.BusinessBotRights}. Saving sends
 * {@link TdApi.SetBusinessConnectedBot}; the "Remove bot" action sends
 * {@link TdApi.DeleteBusinessConnectedBot}.
 */
public class BusinessConnectedBotController extends EditBaseController<Void> implements View.OnClickListener, BusinessRecipientsController.Delegate {

  private SettingsAdapter adapter;

  private long botUserId; // 0 = none selected yet
  private TdApi.BusinessRecipients recipients;
  private TdApi.BusinessBotRights rights;
  private boolean hadExistingBot;
  // Set once the user edits recipients or any right toggle. Guards the late
  // GetBusinessConnectedBot seed from clobbering in-progress edits if the
  // network round-trip lands after the user has already started changing things.
  private boolean userTouched;

  public BusinessConnectedBotController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_businessConnectedBot;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BusinessChatbots);
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    this.recipients = BusinessRecipientsController.emptyRecipients();
    this.rights = defaultRights();

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int id = item.getId();
        if (id == R.id.btn_businessBot) {
          view.setData(botSummary());
        } else if (id == R.id.btn_businessRecipients) {
          view.setData(BusinessAwayController.recipientsSummary(recipients));
        }
      }
    };
    buildCells();
    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    setDoneVisible(true);

    loadConnectedBot();
  }

  private static TdApi.BusinessBotRights defaultRights () {
    // Default to the common "can reply" right enabled, everything else off, mirroring
    // the official client where a freshly-connected bot can manage chats by default.
    TdApi.BusinessBotRights r = new TdApi.BusinessBotRights();
    r.canReply = true;
    return r;
  }

  private void loadConnectedBot () {
    tdlib.send(new TdApi.GetBusinessConnectedBot(), (info, error) -> runOnUiThreadOptional(() -> {
      if (isDestroyed()) {
        return;
      }
      if (error != null) {
        // A 404 simply means no bot is connected yet — that is not an error to surface.
        return;
      }
      if (info != null && info.bot != null) {
        this.hadExistingBot = true;
        this.botUserId = info.bot.botUserId;
        if (userTouched) {
          // The user already started editing recipients/rights before this late
          // seed landed; do not clobber their in-progress changes. Only refresh
          // the bot summary (which they have not touched here) and the now-visible
          // "Remove bot" row, preserving the current toggle/recipient state.
          buildCells();
          adapter.updateValuedSettingById(R.id.btn_businessBot);
          return;
        }
        if (info.bot.recipients != null) {
          this.recipients = info.bot.recipients;
        }
        if (info.bot.rights != null) {
          this.rights = info.bot.rights;
        }
        buildCells();
      }
    }));
  }

  private CharSequence botSummary () {
    if (botUserId == 0) {
      return Lang.getString(R.string.BusinessChatbotsNone);
    }
    return tdlib.cache().userName(botUserId);
  }

  // Rights presented as toggles. Reflection-free explicit list keeps the mapping obvious.
  private static final class RightRow {
    final int id;
    final int titleRes;
    RightRow (int id, int titleRes) {
      this.id = id;
      this.titleRes = titleRes;
    }
  }

  private boolean rightValue (int id) {
    if (id == R.id.btn_businessBotCanReply) return rights.canReply;
    if (id == R.id.btn_businessBotCanReadMessages) return rights.canReadMessages;
    if (id == R.id.btn_businessBotCanDeleteSentMessages) return rights.canDeleteSentMessages;
    if (id == R.id.btn_businessBotCanDeleteAllMessages) return rights.canDeleteAllMessages;
    if (id == R.id.btn_businessBotCanEditName) return rights.canEditName;
    if (id == R.id.btn_businessBotCanEditBio) return rights.canEditBio;
    if (id == R.id.btn_businessBotCanEditProfilePhoto) return rights.canEditProfilePhoto;
    if (id == R.id.btn_businessBotCanEditUsername) return rights.canEditUsername;
    if (id == R.id.btn_businessBotCanViewGiftsAndStars) return rights.canViewGiftsAndStars;
    if (id == R.id.btn_businessBotCanSellGifts) return rights.canSellGifts;
    if (id == R.id.btn_businessBotCanChangeGiftSettings) return rights.canChangeGiftSettings;
    if (id == R.id.btn_businessBotCanTransferAndUpgradeGifts) return rights.canTransferAndUpgradeGifts;
    if (id == R.id.btn_businessBotCanTransferStars) return rights.canTransferStars;
    if (id == R.id.btn_businessBotCanManageStories) return rights.canManageStories;
    return false;
  }

  private void setRightValue (int id, boolean value) {
    if (id == R.id.btn_businessBotCanReply) rights.canReply = value;
    else if (id == R.id.btn_businessBotCanReadMessages) rights.canReadMessages = value;
    else if (id == R.id.btn_businessBotCanDeleteSentMessages) rights.canDeleteSentMessages = value;
    else if (id == R.id.btn_businessBotCanDeleteAllMessages) rights.canDeleteAllMessages = value;
    else if (id == R.id.btn_businessBotCanEditName) rights.canEditName = value;
    else if (id == R.id.btn_businessBotCanEditBio) rights.canEditBio = value;
    else if (id == R.id.btn_businessBotCanEditProfilePhoto) rights.canEditProfilePhoto = value;
    else if (id == R.id.btn_businessBotCanEditUsername) rights.canEditUsername = value;
    else if (id == R.id.btn_businessBotCanViewGiftsAndStars) rights.canViewGiftsAndStars = value;
    else if (id == R.id.btn_businessBotCanSellGifts) rights.canSellGifts = value;
    else if (id == R.id.btn_businessBotCanChangeGiftSettings) rights.canChangeGiftSettings = value;
    else if (id == R.id.btn_businessBotCanTransferAndUpgradeGifts) rights.canTransferAndUpgradeGifts = value;
    else if (id == R.id.btn_businessBotCanTransferStars) rights.canTransferStars = value;
    else if (id == R.id.btn_businessBotCanManageStories) rights.canManageStories = value;
  }

  private static final RightRow[] RIGHT_ROWS = new RightRow[] {
    new RightRow(R.id.btn_businessBotCanReply, R.string.BusinessChatbotsRightReply),
    new RightRow(R.id.btn_businessBotCanReadMessages, R.string.BusinessChatbotsRightReadMessages),
    new RightRow(R.id.btn_businessBotCanDeleteSentMessages, R.string.BusinessChatbotsRightDeleteSent),
    new RightRow(R.id.btn_businessBotCanDeleteAllMessages, R.string.BusinessChatbotsRightDeleteAll),
    new RightRow(R.id.btn_businessBotCanEditName, R.string.BusinessChatbotsRightEditName),
    new RightRow(R.id.btn_businessBotCanEditBio, R.string.BusinessChatbotsRightEditBio),
    new RightRow(R.id.btn_businessBotCanEditProfilePhoto, R.string.BusinessChatbotsRightEditProfilePhoto),
    new RightRow(R.id.btn_businessBotCanEditUsername, R.string.BusinessChatbotsRightEditUsername),
    new RightRow(R.id.btn_businessBotCanViewGiftsAndStars, R.string.BusinessChatbotsRightViewGifts),
    new RightRow(R.id.btn_businessBotCanSellGifts, R.string.BusinessChatbotsRightSellGifts),
    new RightRow(R.id.btn_businessBotCanChangeGiftSettings, R.string.BusinessChatbotsRightChangeGiftSettings),
    new RightRow(R.id.btn_businessBotCanTransferAndUpgradeGifts, R.string.BusinessChatbotsRightTransferGifts),
    new RightRow(R.id.btn_businessBotCanTransferStars, R.string.BusinessChatbotsRightTransferStars),
    new RightRow(R.id.btn_businessBotCanManageStories, R.string.BusinessChatbotsRightManageStories)
  };

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessChatbots));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessBot, 0, R.string.BusinessChatbotsBot));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessChatbotsHint).setTextColorId(ColorId.textLight));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessRecipients, 0, R.string.BusinessRecipients));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessChatbotsRights));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < RIGHT_ROWS.length; i++) {
      RightRow row = RIGHT_ROWS[i];
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, row.id, 0, row.titleRes, rightValue(row.id)));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (hadExistingBot) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_businessBotRemove, 0, R.string.BusinessChatbotsRemove).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (adapter != null) {
      adapter.setItems(items, false);
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_businessBot) {
      promptBotUsername();
    } else if (id == R.id.btn_businessRecipients) {
      BusinessRecipientsController c = new BusinessRecipientsController(context, tdlib);
      c.setArguments(new BusinessRecipientsController.Args(recipients, this));
      navigateTo(c);
    } else if (id == R.id.btn_businessBotRemove) {
      confirmRemove();
    } else {
      // Rights toggles.
      for (RightRow row : RIGHT_ROWS) {
        if (id == row.id) {
          boolean value = adapter.toggleView(v);
          userTouched = true;
          setRightValue(id, value);
          return;
        }
      }
    }
  }

  private void promptBotUsername () {
    String current = botUserId != 0 ? tdlib.cache().userUsername(botUserId) : null;
    openInputAlert(Lang.getString(R.string.BusinessChatbotsBot), Lang.getString(R.string.BusinessChatbotsBotHint), R.string.Done, R.string.Cancel,
      !StringUtils.isEmpty(current) ? "@" + current : null,
      (inputView, result) -> {
        resolveBot(result);
        return true;
      }, true);
  }

  private void resolveBot (String username) {
    if (username == null) {
      return;
    }
    String clean = username.trim();
    if (clean.startsWith("@")) {
      clean = clean.substring(1);
    }
    if (StringUtils.isEmpty(clean)) {
      // Clearing the username unselects the bot.
      botUserId = 0;
      adapter.updateValuedSettingById(R.id.btn_businessBot);
      return;
    }
    tdlib.send(new TdApi.SearchPublicChat(clean), (chat, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      TdApi.User user = tdlib.chatUser(chat);
      if (!TD.isBot(user)) {
        UI.showToast(R.string.BusinessChatbotsNotABot, Toast.LENGTH_SHORT);
        return;
      }
      botUserId = user.id;
      adapter.updateValuedSettingById(R.id.btn_businessBot);
    }));
  }

  @Override
  public void onRecipientsChanged (TdApi.BusinessRecipients recipients) {
    this.recipients = recipients;
    userTouched = true;
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_businessRecipients);
    }
  }

  private void confirmRemove () {
    showOptions(Lang.getString(R.string.BusinessChatbotsRemoveConfirm),
      new int[] {R.id.btn_businessBotRemove, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.BusinessChatbotsRemove), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_businessBotRemove) {
          removeBot();
        }
        return true;
      });
  }

  private void removeBot () {
    if (isInProgress() || botUserId == 0) {
      return;
    }
    setInProgress(true);
    tdlib.send(new TdApi.DeleteBusinessConnectedBot(botUserId), (result, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);
      if (error != null) {
        UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
      } else {
        onSaveCompleted();
      }
    }));
  }

  @Override
  protected boolean onDoneClick () {
    if (isInProgress()) {
      return true;
    }
    if (botUserId == 0) {
      UI.showToast(R.string.BusinessChatbotsNone, Toast.LENGTH_SHORT);
      return true;
    }
    setInProgress(true);
    TdApi.BusinessConnectedBot bot = new TdApi.BusinessConnectedBot(botUserId, recipients, rights);
    tdlib.send(new TdApi.SetBusinessConnectedBot(bot), (result, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);
      if (error != null) {
        UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
      } else {
        onSaveCompleted();
      }
    }));
    return true;
  }
}
