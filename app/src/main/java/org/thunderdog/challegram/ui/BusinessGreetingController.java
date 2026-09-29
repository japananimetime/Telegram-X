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
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Editor for the Telegram Business greeting message settings. The user picks the
 * quick-reply shortcut used as the greeting, the inactivity period after which a
 * chat is considered idle (7/14/21/28 days), and the recipients (shared
 * {@link BusinessRecipientsController}). Saved via
 * {@link TdApi.SetBusinessGreetingMessageSettings}; choosing "no shortcut" disables
 * the greeting (sends {@code null}).
 */
public class BusinessGreetingController extends EditBaseController<TdApi.BusinessGreetingMessageSettings> implements View.OnClickListener, BusinessRecipientsController.Delegate {

  private static final int[] INACTIVITY_DAY_OPTIONS = {7, 14, 21, 28};

  private SettingsAdapter adapter;
  private int shortcutId; // 0 = none
  private int inactivityDays = 7;
  private TdApi.BusinessRecipients recipients;

  public BusinessGreetingController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_businessGreeting;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BusinessGreetingMessage);
  }

  @Override
  public void setArguments (@Nullable TdApi.BusinessGreetingMessageSettings args) {
    super.setArguments(args);
    if (args != null) {
      this.shortcutId = args.shortcutId;
      this.inactivityDays = args.inactivityDays != 0 ? args.inactivityDays : 7;
      this.recipients = args.recipients;
    }
    if (this.recipients == null) {
      this.recipients = BusinessRecipientsController.emptyRecipients();
    }
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int id = item.getId();
        if (id == R.id.btn_businessGreetingShortcut) {
          view.setData(BusinessAwayController.shortcutName(tdlib, shortcutId));
        } else if (id == R.id.btn_businessInactivity) {
          view.setData(Lang.pluralBold(R.string.BusinessGreetingInactivityValue, inactivityDays));
        } else if (id == R.id.btn_businessRecipients) {
          view.setData(BusinessAwayController.recipientsSummary(recipients));
        }
      }
    };
    buildCells();
    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    setDoneVisible(true);
  }

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessGreetingMessage));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessGreetingShortcut, 0, R.string.BusinessQuickReplyShortcut));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessGreetingShortcutHint).setTextColorId(ColorId.textLight));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessRecipients, 0, R.string.BusinessRecipients));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessGreetingInactivity));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessInactivity, 0, R.string.BusinessGreetingInactivity));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessGreetingInactivityHint).setTextColorId(ColorId.textLight));

    if (adapter != null) {
      adapter.setItems(items, false);
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_businessGreetingShortcut) {
      BusinessAwayController.pickShortcut(this, tdlib, shortcutId, newId -> {
        shortcutId = newId;
        adapter.updateValuedSettingById(R.id.btn_businessGreetingShortcut);
      });
    } else if (id == R.id.btn_businessInactivity) {
      pickInactivity();
    } else if (id == R.id.btn_businessRecipients) {
      BusinessRecipientsController c = new BusinessRecipientsController(context, tdlib);
      c.setArguments(new BusinessRecipientsController.Args(recipients, this));
      navigateTo(c);
    }
  }

  private void pickInactivity () {
    ListItem[] items = new ListItem[INACTIVITY_DAY_OPTIONS.length];
    for (int i = 0; i < INACTIVITY_DAY_OPTIONS.length; i++) {
      int days = INACTIVITY_DAY_OPTIONS[i];
      items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_businessInactivityOption, 0,
        Lang.plural(R.string.BusinessGreetingInactivityValue, days), R.id.btn_businessInactivity, days == inactivityDays);
      items[i].setIntValue(days);
    }
    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_businessInactivity)
      .setRawItems(items)
      .setNeedSeparators(true)
      .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter, window) -> {
        if (item != null && item.getIntValue() != 0) {
          inactivityDays = item.getIntValue();
          adapter.updateValuedSettingById(R.id.btn_businessInactivity);
        }
        if (window != null) {
          window.hideWindow(true);
        }
      });
    showSettings(b);
  }

  @Override
  public void onRecipientsChanged (TdApi.BusinessRecipients recipients) {
    this.recipients = recipients;
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_businessRecipients);
    }
  }

  @Override
  protected boolean onDoneClick () {
    if (isInProgress()) {
      return true;
    }
    final TdApi.BusinessGreetingMessageSettings settings;
    if (shortcutId == 0) {
      settings = null; // disables the greeting message
    } else {
      settings = new TdApi.BusinessGreetingMessageSettings(shortcutId, recipients, inactivityDays);
    }
    setInProgress(true);
    tdlib.send(new TdApi.SetBusinessGreetingMessageSettings(settings), (result, error) -> runOnUiThreadOptional(() -> {
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
