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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableInt;

/**
 * Editor for the Telegram Business away message settings. The user picks the
 * quick-reply shortcut used as the away message, when it should be sent
 * (always / outside opening hours / a custom date range), the recipients (shared
 * {@link BusinessRecipientsController}), and whether to suppress sending while
 * recently online. Saved via {@link TdApi.SetBusinessAwayMessageSettings}; choosing
 * "no shortcut" disables the away message (sends {@code null}).
 *
 * <p>This class also hosts the shared shortcut-picker / recipients-summary helpers
 * reused by {@link BusinessGreetingController}.
 */
public class BusinessAwayController extends EditBaseController<TdApi.BusinessAwayMessageSettings> implements View.OnClickListener, BusinessRecipientsController.Delegate {

  // Schedule selection.
  private static final int SCHEDULE_ALWAYS = 0;
  private static final int SCHEDULE_OUTSIDE_HOURS = 1;
  private static final int SCHEDULE_CUSTOM = 2;

  private SettingsAdapter adapter;
  private int shortcutId; // 0 = none
  private int scheduleType = SCHEDULE_ALWAYS;
  private int customStartDate, customEndDate; // Unix seconds, for SCHEDULE_CUSTOM
  private boolean offlineOnly;
  private TdApi.BusinessRecipients recipients;

  public BusinessAwayController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_businessAway;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BusinessAwayMessage);
  }

  @Override
  public void setArguments (@Nullable TdApi.BusinessAwayMessageSettings args) {
    super.setArguments(args);
    if (args != null) {
      this.shortcutId = args.shortcutId;
      this.recipients = args.recipients;
      this.offlineOnly = args.offlineOnly;
      if (args.schedule != null) {
        switch (args.schedule.getConstructor()) {
          case TdApi.BusinessAwayMessageScheduleAlways.CONSTRUCTOR:
            scheduleType = SCHEDULE_ALWAYS;
            break;
          case TdApi.BusinessAwayMessageScheduleOutsideOfOpeningHours.CONSTRUCTOR:
            scheduleType = SCHEDULE_OUTSIDE_HOURS;
            break;
          case TdApi.BusinessAwayMessageScheduleCustom.CONSTRUCTOR:
            scheduleType = SCHEDULE_CUSTOM;
            TdApi.BusinessAwayMessageScheduleCustom custom = (TdApi.BusinessAwayMessageScheduleCustom) args.schedule;
            customStartDate = custom.startDate;
            customEndDate = custom.endDate;
            break;
        }
      }
    }
    if (this.recipients == null) {
      this.recipients = BusinessRecipientsController.emptyRecipients();
    }
    if (customStartDate == 0) {
      customStartDate = (int) (System.currentTimeMillis() / 1000L);
    }
    if (customEndDate <= customStartDate) {
      customEndDate = customStartDate + 24 * 60 * 60;
    }
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int id = item.getId();
        if (id == R.id.btn_businessAwayShortcut) {
          view.setData(shortcutName(tdlib, shortcutId));
        } else if (id == R.id.btn_businessSchedule) {
          view.setData(scheduleSummary());
        } else if (id == R.id.btn_businessRecipients) {
          view.setData(recipientsSummary(recipients));
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

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessAwayMessage));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessAwayShortcut, 0, R.string.BusinessQuickReplyShortcut));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessAwayShortcutHint).setTextColorId(ColorId.textLight));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessAwaySchedule));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessSchedule, 0, R.string.BusinessAwaySchedule));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessRecipients, 0, R.string.BusinessRecipients));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_businessOfflineOnly, 0, R.string.BusinessAwayOfflineOnly, offlineOnly));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessAwayOfflineOnlyHint).setTextColorId(ColorId.textLight));

    if (adapter != null) {
      adapter.setItems(items, false);
    }
  }

  private CharSequence scheduleSummary () {
    switch (scheduleType) {
      case SCHEDULE_OUTSIDE_HOURS:
        return Lang.getString(R.string.BusinessAwayScheduleOutside);
      case SCHEDULE_CUSTOM:
        return Lang.getString(R.string.BusinessAwayScheduleCustomRange,
          Lang.getDate(customStartDate, java.util.concurrent.TimeUnit.SECONDS),
          Lang.getDate(customEndDate, java.util.concurrent.TimeUnit.SECONDS));
      case SCHEDULE_ALWAYS:
      default:
        return Lang.getString(R.string.BusinessAwayScheduleAlways);
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_businessAwayShortcut) {
      pickShortcut(this, tdlib, shortcutId, newId -> {
        shortcutId = newId;
        adapter.updateValuedSettingById(R.id.btn_businessAwayShortcut);
      });
    } else if (id == R.id.btn_businessSchedule) {
      pickSchedule();
    } else if (id == R.id.btn_businessRecipients) {
      BusinessRecipientsController c = new BusinessRecipientsController(context, tdlib);
      c.setArguments(new BusinessRecipientsController.Args(recipients, this));
      navigateTo(c);
    } else if (id == R.id.btn_businessOfflineOnly) {
      offlineOnly = adapter.toggleView(v);
    }
  }

  private void pickSchedule () {
    ListItem[] items = new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_businessScheduleAlways, 0, R.string.BusinessAwayScheduleAlways, R.id.btn_businessSchedule, scheduleType == SCHEDULE_ALWAYS),
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_businessScheduleOutside, 0, R.string.BusinessAwayScheduleOutside, R.id.btn_businessSchedule, scheduleType == SCHEDULE_OUTSIDE_HOURS),
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_businessScheduleCustom, 0, R.string.BusinessAwayScheduleCustom, R.id.btn_businessSchedule, scheduleType == SCHEDULE_CUSTOM)
    };
    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_businessSchedule)
      .setRawItems(items)
      .setNeedSeparators(false)
      .setIntDelegate((settingsId, result) -> {
        int selection = result.get(R.id.btn_businessSchedule);
        if (selection == R.id.btn_businessScheduleOutside) {
          scheduleType = SCHEDULE_OUTSIDE_HOURS;
          adapter.updateValuedSettingById(R.id.btn_businessSchedule);
        } else if (selection == R.id.btn_businessScheduleCustom) {
          scheduleType = SCHEDULE_CUSTOM;
          pickCustomStart();
        } else {
          scheduleType = SCHEDULE_ALWAYS;
          adapter.updateValuedSettingById(R.id.btn_businessSchedule);
        }
      });
    showSettings(b);
  }

  private void pickCustomStart () {
    showDateTimePicker(Lang.getString(R.string.BusinessAwayScheduleStart), R.string.Today, R.string.Tomorrow, R.string.BusinessScheduleFutureDate, startMillis -> {
      customStartDate = (int) (startMillis / 1000L);
      if (customEndDate <= customStartDate) {
        customEndDate = customStartDate + 24 * 60 * 60;
      }
      pickCustomEnd();
    }, null);
  }

  private void pickCustomEnd () {
    showDateTimePicker(Lang.getString(R.string.BusinessAwayScheduleEnd), R.string.Today, R.string.Tomorrow, R.string.BusinessScheduleFutureDate, endMillis -> {
      int end = (int) (endMillis / 1000L);
      if (end <= customStartDate) {
        UI.showToast(R.string.BusinessAwayScheduleInvalid, Toast.LENGTH_SHORT);
        end = customStartDate + 24 * 60 * 60;
      }
      customEndDate = end;
      adapter.updateValuedSettingById(R.id.btn_businessSchedule);
    }, null);
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
    final TdApi.BusinessAwayMessageSettings settings;
    if (shortcutId == 0) {
      settings = null; // disables the away message
    } else {
      TdApi.BusinessAwayMessageSchedule schedule;
      switch (scheduleType) {
        case SCHEDULE_OUTSIDE_HOURS:
          schedule = new TdApi.BusinessAwayMessageScheduleOutsideOfOpeningHours();
          break;
        case SCHEDULE_CUSTOM:
          if (customEndDate <= customStartDate) {
            UI.showToast(R.string.BusinessAwayScheduleInvalid, Toast.LENGTH_SHORT);
            return true;
          }
          schedule = new TdApi.BusinessAwayMessageScheduleCustom(customStartDate, customEndDate);
          break;
        case SCHEDULE_ALWAYS:
        default:
          schedule = new TdApi.BusinessAwayMessageScheduleAlways();
          break;
      }
      settings = new TdApi.BusinessAwayMessageSettings(shortcutId, recipients, schedule, offlineOnly);
    }
    setInProgress(true);
    tdlib.send(new TdApi.SetBusinessAwayMessageSettings(settings), (result, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);
      if (error != null) {
        UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
      } else {
        onSaveCompleted();
      }
    }));
    return true;
  }

  // Shared helpers (reused by BusinessGreetingController)

  /** Human-readable name of a quick-reply shortcut id, or a "none" placeholder when 0/unknown. */
  public static CharSequence shortcutName (Tdlib tdlib, int shortcutId) {
    if (shortcutId == 0) {
      return Lang.getString(R.string.BusinessShortcutNone);
    }
    for (TdApi.QuickReplyShortcut shortcut : tdlib.getQuickReplyShortcuts()) {
      if (shortcut.id == shortcutId && !StringUtils.isEmpty(shortcut.name)) {
        return shortcut.name;
      }
    }
    return Lang.getString(R.string.BusinessShortcutNone);
  }

  /** Short description of the chosen recipients categories. */
  public static CharSequence recipientsSummary (@Nullable TdApi.BusinessRecipients recipients) {
    if (recipients == null) {
      return Lang.getString(R.string.BusinessRecipientsNone);
    }
    List<String> parts = new ArrayList<>();
    if (recipients.selectExistingChats) {
      parts.add(Lang.getString(R.string.BusinessRecipientsExistingChats));
    }
    if (recipients.selectNewChats) {
      parts.add(Lang.getString(R.string.BusinessRecipientsNewChats));
    }
    if (recipients.selectContacts) {
      parts.add(Lang.getString(R.string.BusinessRecipientsContacts));
    }
    if (recipients.selectNonContacts) {
      parts.add(Lang.getString(R.string.BusinessRecipientsNonContacts));
    }
    if (recipients.chatIds != null && recipients.chatIds.length > 0) {
      parts.add(Lang.plural(R.string.xChats, recipients.chatIds.length));
    }
    if (parts.isEmpty()) {
      return Lang.getString(R.string.BusinessRecipientsNone);
    }
    String joined = TextUtils_join(parts);
    if (recipients.excludeSelected) {
      return Lang.getString(R.string.BusinessRecipientsExcept, joined);
    }
    return joined;
  }

  private static String TextUtils_join (List<String> parts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        sb.append(Lang.getConcatSeparator());
      }
      sb.append(parts.get(i));
    }
    return sb.toString();
  }

  /**
   * Shows a popup to pick a quick-reply shortcut (or "none"). Reads the already
   * synced shortcut cache; if it is empty, triggers a load and tells the user to
   * create shortcuts first. Calls {@code onPicked} with the chosen id (0 = none).
   */
  public static void pickShortcut (ViewController<?> context, Tdlib tdlib, int currentId, @NonNull RunnableInt onPicked) {
    List<TdApi.QuickReplyShortcut> shortcuts = tdlib.getQuickReplyShortcuts();
    if (shortcuts.isEmpty()) {
      // Kick a load so a subsequent open finds them, and guide the user meanwhile.
      tdlib.send(new TdApi.LoadQuickReplyShortcuts(), (result, error) -> {});
      UI.showToast(R.string.BusinessShortcutEmpty, Toast.LENGTH_LONG);
      return;
    }
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_businessShortcutOption, 0, Lang.getString(R.string.BusinessShortcutNone), R.id.btn_businessShortcut, currentId == 0));
    for (TdApi.QuickReplyShortcut shortcut : shortcuts) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      ListItem item = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_businessShortcutOption, 0,
        !StringUtils.isEmpty(shortcut.name) ? shortcut.name : String.valueOf(shortcut.id), R.id.btn_businessShortcut, shortcut.id == currentId);
      item.setIntValue(shortcut.id);
      items.add(item);
    }
    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_businessShortcut)
      .setRawItems(items)
      .setNeedSeparators(false)
      .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter, window) -> {
        if (item != null) {
          onPicked.runWithInt(item.getIntValue());
        }
        if (window != null) {
          window.hideWindow(true);
        }
      });
    context.showSettings(b);
  }
}
