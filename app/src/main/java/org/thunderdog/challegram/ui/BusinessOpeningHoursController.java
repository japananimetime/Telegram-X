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
import java.util.TimeZone;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

/**
 * Editor for the Telegram Business opening hours. The user picks a time zone and,
 * per week day, marks the business open and edits an open/close time. The per-day
 * intervals are converted to minute-of-week {@link TdApi.BusinessOpeningHoursInterval}
 * entries and saved via {@link TdApi.SetBusinessOpeningHours}; turning every day off
 * sends {@code null} to remove the opening hours entirely.
 *
 * <p>Per-day editing here is limited to a single contiguous interval (the common
 * case). Multiple intervals per day from the server are preserved by collapsing to
 * the day's earliest start / latest end when first loaded; richer multi-interval
 * editing is a follow-up.
 */
public class BusinessOpeningHoursController extends EditBaseController<TdApi.BusinessOpeningHours> implements View.OnClickListener {

  private static final int MINUTES_PER_DAY = 24 * 60;
  private static final int DEFAULT_OPEN_MINUTE = 9 * 60;  // 09:00
  private static final int DEFAULT_CLOSE_MINUTE = 17 * 60; // 17:00

  // Per-day state: open flag and a within-day interval [openMinute, closeMinute).
  private final boolean[] dayOpen = new boolean[7];
  private final int[] dayOpenMinute = new int[7];
  private final int[] dayCloseMinute = new int[7];
  // Tracks which days the user actually touched. Days the user did NOT edit keep
  // their original server intervals verbatim on save (see onDoneClick), so that
  // overnight intervals the simplified single-interval-per-day editor cannot
  // represent (e.g. Fri 18:00 -> Sat 02:00) are preserved rather than truncated.
  private final boolean[] dayEdited = new boolean[7];
  // Original server intervals, grouped by the day they START in (0=Mon..6=Sun).
  // Used to re-emit unedited days exactly, including overnight spill-over.
  @Nullable private List<TdApi.BusinessOpeningHoursInterval>[] originalIntervalsByDay;
  private String timeZoneId;

  private SettingsAdapter adapter;

  public BusinessOpeningHoursController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_businessOpeningHours;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BusinessOpeningHours);
  }

  @Override
  public void setArguments (@Nullable TdApi.BusinessOpeningHours args) {
    super.setArguments(args);
    for (int i = 0; i < 7; i++) {
      dayOpen[i] = false;
      dayOpenMinute[i] = DEFAULT_OPEN_MINUTE;
      dayCloseMinute[i] = DEFAULT_CLOSE_MINUTE;
      dayEdited[i] = false;
    }
    //noinspection unchecked
    originalIntervalsByDay = new List[7];
    if (args != null) {
      this.timeZoneId = args.timeZoneId;
      if (args.openingHours != null) {
        for (TdApi.BusinessOpeningHoursInterval interval : args.openingHours) {
          // Map the week-minute interval back onto the day it starts in.
          int day = Math.min(6, Math.max(0, interval.startMinute / MINUTES_PER_DAY));
          // Remember the original interval verbatim so an unedited day can be
          // re-emitted exactly on save (TDLib's endMinute spills into the next
          // day for overnight hours; we must not lose that on save).
          if (originalIntervalsByDay[day] == null) {
            originalIntervalsByDay[day] = new ArrayList<>();
          }
          originalIntervalsByDay[day].add(interval);

          int startInDay = interval.startMinute - day * MINUTES_PER_DAY;
          int endInDay = interval.endMinute - day * MINUTES_PER_DAY;
          // For DISPLAY only, the single-interval editor clamps overnight spill to
          // end-of-day. The original interval above is preserved for save, so the
          // overnight portion is not actually destroyed unless the user edits this day.
          if (endInDay > MINUTES_PER_DAY) {
            endInDay = MINUTES_PER_DAY;
          }
          if (!dayOpen[day]) {
            dayOpen[day] = true;
            dayOpenMinute[day] = startInDay;
            dayCloseMinute[day] = endInDay;
          } else {
            // Collapse multiple intervals to earliest start / latest end.
            dayOpenMinute[day] = Math.min(dayOpenMinute[day], startInDay);
            dayCloseMinute[day] = Math.max(dayCloseMinute[day], endInDay);
          }
        }
      }
    }
    if (StringUtils.isEmpty(timeZoneId)) {
      timeZoneId = TimeZone.getDefault().getID();
    }
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int id = item.getId();
        if (id == R.id.btn_businessTimeZone) {
          view.setData(StringUtils.isEmpty(timeZoneId) ? Lang.getString(R.string.BusinessTimeZoneDefault) : timeZoneId);
        } else if (id == R.id.btn_businessDay) {
          int day = item.getIntValue();
          if (day >= 0 && day < 7) {
            view.setData(dayOpen[day]
              ? formatRange(dayOpenMinute[day], dayCloseMinute[day])
              : Lang.getString(R.string.BusinessHoursClosed));
          }
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

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessTimeZone));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessTimeZone, 0, R.string.BusinessTimeZone));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessHoursDays));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int day = 0; day < 7; day++) {
      if (day > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessDay, 0, (CharSequence) weekdayName(day));
      item.setIntValue(day);
      items.add(item);
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessHoursHint).setTextColorId(ColorId.textLight));

    if (adapter != null) {
      adapter.setItems(items, false);
    }
  }

  /** Localized weekday name for our 0=Monday .. 6=Sunday index. */
  private static String weekdayName (int day) {
    // DateFormatSymbols.getWeekdays() is 1-indexed by Calendar.SUNDAY..SATURDAY (index 0 is empty).
    final int[] calendarDays = {
      java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY, java.util.Calendar.WEDNESDAY,
      java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY, java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY
    };
    String[] names = new java.text.DateFormatSymbols(Lang.locale()).getWeekdays();
    int idx = (day >= 0 && day < 7) ? calendarDays[day] : java.util.Calendar.MONDAY;
    if (names != null && idx < names.length && !StringUtils.isEmpty(names[idx])) {
      return names[idx];
    }
    return String.valueOf(day);
  }

  private static String formatRange (int openMinute, int closeMinute) {
    return formatMinute(openMinute) + " – " + formatMinute(closeMinute);
  }

  private static String formatMinute (int minuteOfDay) {
    int h = (minuteOfDay / 60) % 24;
    int m = minuteOfDay % 60;
    if (minuteOfDay >= MINUTES_PER_DAY) { // end-of-day represented as 24:00
      h = 24;
      m = 0;
    }
    return String.format(java.util.Locale.US, "%02d:%02d", h, m);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_businessTimeZone) {
      pickTimeZone();
    } else if (id == R.id.btn_businessDay) {
      ListItem item = (ListItem) v.getTag();
      if (item != null) {
        editDay(item.getIntValue());
      }
    }
  }

  private void pickTimeZone () {
    tdlib.send(new TdApi.GetTimeZones(), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null || result == null || result.timeZones == null || result.timeZones.length == 0) {
        // Fall back to a manual identifier entry so the feature still works offline.
        promptManualTimeZone();
        return;
      }
      final TdApi.TimeZone[] zones = result.timeZones;
      ListItem[] items = new ListItem[zones.length];
      for (int i = 0; i < zones.length; i++) {
        items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_businessTimeZoneOption, 0, zones[i].name, R.id.btn_businessTimeZone, zones[i].id.equals(timeZoneId));
        items[i].setStringValue(zones[i].id);
      }
      SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_businessTimeZone)
        .setRawItems(items)
        .setNeedSeparators(true)
        .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter, window) -> {
          if (item != null && !StringUtils.isEmpty(item.getStringValue())) {
            timeZoneId = item.getStringValue();
            adapter.updateValuedSettingById(R.id.btn_businessTimeZone);
          }
          if (window != null) {
            window.hideWindow(true);
          }
        });
      showSettings(b);
    }));
  }

  private void promptManualTimeZone () {
    openInputAlert(Lang.getString(R.string.BusinessTimeZone), Lang.getString(R.string.BusinessTimeZone),
      R.string.Save, R.string.Cancel, timeZoneId, (inputView, text) -> {
        if (StringUtils.isEmpty(text != null ? text.trim() : null)) {
          return false;
        }
        timeZoneId = text.trim();
        adapter.updateValuedSettingById(R.id.btn_businessTimeZone);
        return true;
      }, true);
  }

  private void editDay (int day) {
    if (day < 0 || day >= 7) {
      return;
    }
    showOptions(weekdayName(day),
      new int[] {R.id.btn_businessDayOpen, R.id.btn_businessDayHours, R.id.btn_businessDayClosed},
      new String[] {Lang.getString(R.string.BusinessHoursMarkOpen), Lang.getString(R.string.BusinessHoursSetTime), Lang.getString(R.string.BusinessHoursMarkClosed)},
      null, null,
      (itemView, optionId) -> {
        if (optionId == R.id.btn_businessDayOpen) {
          dayOpen[day] = true;
          dayEdited[day] = true;
          adapter.updateValuedSettingByPosition(findDayPosition(day));
        } else if (optionId == R.id.btn_businessDayClosed) {
          dayOpen[day] = false;
          dayEdited[day] = true;
          adapter.updateValuedSettingByPosition(findDayPosition(day));
        } else if (optionId == R.id.btn_businessDayHours) {
          promptTime(day, true);
        }
        return true;
      });
  }

  private void promptTime (int day, boolean openTime) {
    int current = openTime ? dayOpenMinute[day] : dayCloseMinute[day];
    openInputAlert(
      Lang.getString(openTime ? R.string.BusinessHoursOpenTime : R.string.BusinessHoursCloseTime),
      Lang.getString(R.string.BusinessHoursTimeFormat),
      R.string.Continue, R.string.Cancel, formatMinute(current), (inputView, text) -> {
        int parsed = parseMinute(text);
        if (parsed < 0) {
          UI.showToast(R.string.BusinessHoursInvalidTime, Toast.LENGTH_SHORT);
          return false;
        }
        if (openTime) {
          dayOpenMinute[day] = parsed;
          // Chain to the close-time prompt.
          tdlib.ui().post(() -> promptTime(day, false));
        } else {
          if (parsed <= dayOpenMinute[day]) {
            UI.showToast(R.string.BusinessHoursInvalidTime, Toast.LENGTH_SHORT);
            return false;
          }
          dayCloseMinute[day] = parsed;
          dayOpen[day] = true;
          dayEdited[day] = true;
          adapter.updateValuedSettingByPosition(findDayPosition(day));
        }
        return true;
      }, true);
  }

  private int findDayPosition (int day) {
    List<ListItem> items = adapter.getItems();
    for (int i = 0; i < items.size(); i++) {
      ListItem item = items.get(i);
      if (item.getId() == R.id.btn_businessDay && item.getIntValue() == day) {
        return i;
      }
    }
    return -1;
  }

  /** Parses an "HH:MM" string into a minute-of-day, or -1 if invalid. Accepts 24:00 as end-of-day. */
  private static int parseMinute (@Nullable String text) {
    if (StringUtils.isEmpty(text)) {
      return -1;
    }
    String s = text.trim();
    int colon = s.indexOf(':');
    if (colon <= 0) {
      return -1;
    }
    try {
      int h = Integer.parseInt(s.substring(0, colon).trim());
      int m = Integer.parseInt(s.substring(colon + 1).trim());
      if (h < 0 || m < 0 || m > 59) {
        return -1;
      }
      if (h == 24 && m == 0) {
        return MINUTES_PER_DAY;
      }
      if (h > 23) {
        return -1;
      }
      return h * 60 + m;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  @Override
  protected boolean onDoneClick () {
    if (isInProgress()) {
      return true;
    }

    List<TdApi.BusinessOpeningHoursInterval> intervals = new ArrayList<>();
    for (int day = 0; day < 7; day++) {
      boolean hasOriginal = originalIntervalsByDay != null && originalIntervalsByDay[day] != null && !originalIntervalsByDay[day].isEmpty();
      if (!dayEdited[day] && hasOriginal) {
        // The user did not touch this day: re-emit the original server intervals
        // verbatim. This preserves multi-interval days AND overnight spill-over
        // (endMinute > end-of-day) that the simplified per-day editor cannot
        // represent. LIMITATION: once the user edits a day, its overnight portion
        // is collapsed to the single within-day [open, close) interval below; a
        // full overnight/multi-interval editor is a follow-up.
        intervals.addAll(originalIntervalsByDay[day]);
      } else if (dayOpen[day] && dayCloseMinute[day] > dayOpenMinute[day]) {
        int base = day * MINUTES_PER_DAY;
        intervals.add(new TdApi.BusinessOpeningHoursInterval(base + dayOpenMinute[day], base + dayCloseMinute[day]));
      }
    }

    final TdApi.BusinessOpeningHours openingHours;
    if (intervals.isEmpty()) {
      openingHours = null; // removes the opening hours
    } else if (StringUtils.isEmpty(timeZoneId)) {
      UI.showToast(R.string.BusinessTimeZoneRequired, Toast.LENGTH_SHORT);
      return true;
    } else {
      openingHours = new TdApi.BusinessOpeningHours(timeZoneId, intervals.toArray(new TdApi.BusinessOpeningHoursInterval[0]));
    }

    setInProgress(true);
    tdlib.send(new TdApi.SetBusinessOpeningHours(openingHours), (result, error) -> runOnUiThreadOptional(() -> {
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
