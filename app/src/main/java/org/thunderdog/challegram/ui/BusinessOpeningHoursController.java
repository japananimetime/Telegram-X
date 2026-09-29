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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

/**
 * Editor for the Telegram Business opening hours. The user picks a time zone and,
 * per week day, marks the business open and edits one or more open/close
 * intervals. Each interval may run past midnight (an "overnight" interval, e.g.
 * Fri 18:00 → 02:00). The per-day intervals are converted to minute-of-week
 * {@link TdApi.BusinessOpeningHoursInterval} entries and saved via
 * {@link TdApi.SetBusinessOpeningHours}; turning every day off sends {@code null}
 * to remove the opening hours entirely.
 *
 * <p>Intervals are stored relative to the day they start in: {@code start} is a
 * minute-of-day in {@code [0, 24*60)} and {@code end} is a minute-of-day in
 * {@code (start, 48*60]} — values above {@code 24*60} represent an overnight
 * spill into the following day. On save each interval is offset by the day's
 * base minute-of-week, which reproduces TDLib's representation exactly (including
 * overnight spill-over) and supports arbitrarily many intervals per day.
 */
public class BusinessOpeningHoursController extends EditBaseController<TdApi.BusinessOpeningHours> implements View.OnClickListener {

  private static final int MINUTES_PER_DAY = 24 * 60;
  private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;
  private static final int DEFAULT_OPEN_MINUTE = 9 * 60;  // 09:00
  private static final int DEFAULT_CLOSE_MINUTE = 17 * 60; // 17:00

  /** A within-day interval. start in [0, MINUTES_PER_DAY); end in (start, 2*MINUTES_PER_DAY]. */
  private static final class Interval {
    int start;
    int end;

    Interval (int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  // Per-day intervals, relative to the day they START in (0=Mon..6=Sun).
  // An empty list means the day is closed.
  @SuppressWarnings({"unchecked", "rawtypes"})
  private final List<Interval>[] dayIntervals = new List[7];
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
      dayIntervals[i] = new ArrayList<>();
    }
    if (args != null) {
      this.timeZoneId = args.timeZoneId;
      if (args.openingHours != null) {
        for (TdApi.BusinessOpeningHoursInterval interval : args.openingHours) {
          int start = interval.startMinute;
          int end = interval.endMinute;
          if (end <= start) {
            continue;
          }
          // Clamp to the valid week range defensively.
          start = Math.max(0, Math.min(start, MINUTES_PER_WEEK - 1));
          end = Math.max(start + 1, Math.min(end, MINUTES_PER_WEEK + MINUTES_PER_DAY));
          int day = Math.min(6, start / MINUTES_PER_DAY);
          int base = day * MINUTES_PER_DAY;
          // Keep relative to the start day, preserving overnight spill (end may
          // exceed MINUTES_PER_DAY, meaning the interval continues into the next day).
          dayIntervals[day].add(new Interval(start - base, end - base));
        }
        for (int day = 0; day < 7; day++) {
          sortIntervals(dayIntervals[day]);
        }
      }
    }
    if (StringUtils.isEmpty(timeZoneId)) {
      timeZoneId = TimeZone.getDefault().getID();
    }
  }

  private static void sortIntervals (List<Interval> intervals) {
    Collections.sort(intervals, new Comparator<Interval>() {
      @Override
      public int compare (Interval a, Interval b) {
        if (a.start != b.start) {
          return Integer.compare(a.start, b.start);
        }
        return Integer.compare(a.end, b.end);
      }
    });
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
            view.setData(formatDay(day));
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

  /** Subtitle shown on a day row: comma-joined intervals, or "Closed". */
  private String formatDay (int day) {
    List<Interval> intervals = dayIntervals[day];
    if (intervals == null || intervals.isEmpty()) {
      return Lang.getString(R.string.BusinessHoursClosed);
    }
    StringBuilder b = new StringBuilder();
    for (Interval interval : intervals) {
      if (b.length() > 0) {
        b.append(", ");
      }
      b.append(formatRange(interval.start, interval.end));
    }
    return b.toString();
  }

  private static String formatRange (int openMinute, int closeMinute) {
    return formatMinute(openMinute) + " – " + formatMinute(closeMinute);
  }

  private static String formatMinute (int minuteOfDay) {
    if (minuteOfDay >= MINUTES_PER_DAY) {
      // Overnight spill (or exactly 24:00). Render as a 24h+ clock so the user can
      // tell apart "ends at 02:00 next day" from "opens at 02:00".
      int spill = minuteOfDay - MINUTES_PER_DAY;
      if (spill == 0) {
        return "24:00";
      }
      int h = (spill / 60) % 24;
      int m = spill % 60;
      return String.format(java.util.Locale.US, "%02d:%02d⁺¹", h, m);
    }
    int h = (minuteOfDay / 60) % 24;
    int m = minuteOfDay % 60;
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

  /**
   * Opens the per-day options: add a new interval, edit/remove each existing
   * interval, or mark the whole day closed.
   */
  private void editDay (int day) {
    if (day < 0 || day >= 7) {
      return;
    }
    List<Interval> intervals = dayIntervals[day];

    List<Integer> ids = new ArrayList<>();
    List<String> labels = new ArrayList<>();

    // One entry per existing interval to edit/remove it. The label is the
    // interval range itself (e.g. "09:00 – 17:00"); tapping it opens edit/remove.
    for (int i = 0; i < intervals.size(); i++) {
      Interval interval = intervals.get(i);
      ids.add(R.id.btn_businessDayHours);
      labels.add(formatRange(interval.start, interval.end));
    }
    ids.add(R.id.btn_businessDayOpen);
    labels.add(Lang.getString(R.string.BusinessHoursSetTime));
    if (!intervals.isEmpty()) {
      ids.add(R.id.btn_businessDayClosed);
      labels.add(Lang.getString(R.string.BusinessHoursMarkClosed));
    }

    int[] idArray = new int[ids.size()];
    for (int i = 0; i < ids.size(); i++) {
      idArray[i] = ids.get(i);
    }

    // The first intervalCount option rows correspond 1:1 to existing intervals.
    // We tag each option view with its position via getTagForItem(), so an
    // interval-edit tap can recover exactly which interval was pressed (their ids
    // all share btn_businessDayHours and so cannot be told apart by id alone).
    final int intervalCount = intervals.size();
    showOptions(weekdayName(day), idArray, labels.toArray(new String[0]), null, null,
      new org.thunderdog.challegram.util.OptionDelegate() {
        @Override
        public boolean onOptionItemPressed (View optionItemView, int optionId) {
          if (optionId == R.id.btn_businessDayOpen) {
            // Add a brand-new interval.
            promptInterval(day, -1);
          } else if (optionId == R.id.btn_businessDayClosed) {
            dayIntervals[day].clear();
            adapter.updateValuedSettingByPosition(findDayPosition(day));
          } else if (optionId == R.id.btn_businessDayHours) {
            Object tag = optionItemView != null ? optionItemView.getTag() : null;
            int index = (tag instanceof Integer) ? (Integer) tag : -1;
            if (index >= 0 && index < intervalCount) {
              editInterval(day, index);
            }
          }
          return true;
        }

        @Override
        public Object getTagForItem (int position) {
          return position;
        }
      });
  }

  /** Offers to edit or remove an existing interval. */
  private void editInterval (int day, int index) {
    if (index < 0 || index >= dayIntervals[day].size()) {
      return;
    }
    showOptions(formatRange(dayIntervals[day].get(index).start, dayIntervals[day].get(index).end),
      new int[] {R.id.btn_businessDayHours, R.id.btn_delete},
      new String[] {Lang.getString(R.string.BusinessHoursSetTime), Lang.getString(R.string.Remove)},
      null, null,
      (itemView, optionId) -> {
        if (optionId == R.id.btn_businessDayHours) {
          promptInterval(day, index);
        } else if (optionId == R.id.btn_delete) {
          if (index >= 0 && index < dayIntervals[day].size()) {
            dayIntervals[day].remove(index);
            adapter.updateValuedSettingByPosition(findDayPosition(day));
          }
        }
        return true;
      });
  }

  /**
   * Prompts the open time, then chains to the close time, for the interval at
   * {@code index} ({@code -1} to add a new one). The close time may be "after"
   * midnight (i.e. less than the open time), in which case it is treated as an
   * overnight interval and stored as {@code open .. close + 24h}.
   */
  private void promptInterval (int day, int index) {
    boolean isNew = index < 0 || index >= dayIntervals[day].size();
    int currentOpen = isNew ? DEFAULT_OPEN_MINUTE : dayIntervals[day].get(index).start;
    promptOpenTime(day, index, currentOpen);
  }

  private void promptOpenTime (int day, int index, int currentOpen) {
    openInputAlert(
      Lang.getString(R.string.BusinessHoursOpenTime),
      Lang.getString(R.string.BusinessHoursTimeFormat),
      R.string.Continue, R.string.Cancel, formatMinuteForInput(currentOpen), (inputView, text) -> {
        int parsed = parseMinute(text);
        if (parsed < 0 || parsed >= MINUTES_PER_DAY) {
          UI.showToast(R.string.BusinessHoursInvalidTime, Toast.LENGTH_SHORT);
          return false;
        }
        final int openMinute = parsed;
        boolean isNew = index < 0 || index >= dayIntervals[day].size();
        int currentClose = isNew ? DEFAULT_CLOSE_MINUTE : dayIntervals[day].get(index).end;
        // The close-time prompt is posted to the next frame; guard against the controller being
        // destroyed in between (navigated away) so we don't open a dialog on a dead screen.
        tdlib.ui().post(() -> {
          if (isDestroyed()) {
            return;
          }
          promptCloseTime(day, index, openMinute, currentClose);
        });
        return true;
      }, true);
  }

  private void promptCloseTime (int day, int index, int openMinute, int currentClose) {
    openInputAlert(
      Lang.getString(R.string.BusinessHoursCloseTime),
      Lang.getString(R.string.BusinessHoursTimeFormat),
      R.string.Save, R.string.Cancel, formatMinuteForInput(currentClose), (inputView, text) -> {
        int parsed = parseMinute(text);
        if (parsed < 0) {
          UI.showToast(R.string.BusinessHoursInvalidTime, Toast.LENGTH_SHORT);
          return false;
        }
        int closeMinute = parsed;
        // Allow overnight: a close time at/below the open time rolls into the next day.
        if (closeMinute <= openMinute) {
          closeMinute += MINUTES_PER_DAY;
        }
        if (closeMinute <= openMinute || closeMinute > 2 * MINUTES_PER_DAY) {
          UI.showToast(R.string.BusinessHoursInvalidTime, Toast.LENGTH_SHORT);
          return false;
        }
        applyInterval(day, index, openMinute, closeMinute);
        return true;
      }, true);
  }

  private void applyInterval (int day, int index, int openMinute, int closeMinute) {
    // Reached from the close-time dialog callback, which can fire after the controller is gone.
    // Don't mutate state / touch the adapter on a destroyed screen.
    if (isDestroyed()) {
      return;
    }
    if (index < 0 || index >= dayIntervals[day].size()) {
      dayIntervals[day].add(new Interval(openMinute, closeMinute));
    } else {
      Interval interval = dayIntervals[day].get(index);
      interval.start = openMinute;
      interval.end = closeMinute;
    }
    sortIntervals(dayIntervals[day]);
    adapter.updateValuedSettingByPosition(findDayPosition(day));
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

  /** Renders a minute for an input field (overnight spill collapsed to its clock time). */
  private static String formatMinuteForInput (int minute) {
    int clock = minute % MINUTES_PER_DAY;
    if (minute == MINUTES_PER_DAY) {
      return "24:00";
    }
    int h = clock / 60;
    int m = clock % 60;
    return String.format(java.util.Locale.US, "%02d:%02d", h, m);
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

    // Flatten every day's intervals into absolute minute-of-week ranges. An overnight interval
    // keeps its spill (end may exceed the day base + MINUTES_PER_DAY), so adjacent/overlapping
    // ranges across a day boundary are caught by the merge below.
    List<int[]> ranges = new ArrayList<>();
    for (int day = 0; day < 7; day++) {
      List<Interval> dayList = dayIntervals[day];
      if (dayList == null) {
        continue;
      }
      int base = day * MINUTES_PER_DAY;
      for (Interval interval : dayList) {
        if (interval.end > interval.start) {
          ranges.add(new int[] {base + interval.start, base + interval.end});
        }
      }
    }

    // Merge touching/overlapping ranges (range[i].start <= range[i-1].end), accounting for
    // overnight spill (end > a day base + MINUTES_PER_DAY). Sunday's overnight interval may spill
    // past the end of the week (end > MINUTES_PER_WEEK): wrap that tail to the week start and let it
    // merge with Monday's opening range, matching TDLib's circular minute-of-week representation.
    List<int[]> merged = mergeWeeklyRanges(ranges);

    List<TdApi.BusinessOpeningHoursInterval> intervals = new ArrayList<>();
    for (int[] range : merged) {
      if (range[1] > range[0]) {
        intervals.add(new TdApi.BusinessOpeningHoursInterval(range[0], range[1]));
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

  /**
   * Merges overlapping/touching minute-of-week ranges into the minimal disjoint set TDLib expects.
   *
   * <p>Handles three cases the per-day editor can produce: (1) two ranges on the same day that
   * overlap; (2) an overnight range whose spill into the next day overlaps that day's first range;
   * (3) a Sunday overnight range that spills past the end of the week ({@code end > MINUTES_PER_WEEK})
   * — its tail is wrapped to the week start ({@code [0, end - MINUTES_PER_WEEK]}) so it can merge
   * with Monday's opening range, matching TDLib's circular representation. The result is sorted by
   * start and contains no overlaps or touching boundaries.</p>
   */
  private static List<int[]> mergeWeeklyRanges (List<int[]> ranges) {
    if (ranges.isEmpty()) {
      return ranges;
    }
    // 1. Split any range that spills past the end of the week into a head within the week and a
    //    wrapped tail at the week start, so the linear merge below sees everything in [0, week].
    List<int[]> normalized = new ArrayList<>();
    for (int[] range : ranges) {
      int start = range[0];
      int end = range[1];
      if (end > MINUTES_PER_WEEK) {
        normalized.add(new int[] {start, MINUTES_PER_WEEK});
        int wrap = end - MINUTES_PER_WEEK;
        // Clamp the wrapped tail to at most the whole week (a > 1-week interval is nonsensical;
        // collapse it rather than wrapping multiple times).
        normalized.add(new int[] {0, Math.min(wrap, MINUTES_PER_WEEK)});
      } else {
        normalized.add(new int[] {start, end});
      }
    }
    // 2. Sort by start, then by end.
    Collections.sort(normalized, new Comparator<int[]>() {
      @Override
      public int compare (int[] a, int[] b) {
        if (a[0] != b[0]) {
          return Integer.compare(a[0], b[0]);
        }
        return Integer.compare(a[1], b[1]);
      }
    });
    // 3. Linear merge of touching/overlapping ranges.
    List<int[]> merged = new ArrayList<>();
    for (int[] range : normalized) {
      if (merged.isEmpty()) {
        merged.add(new int[] {range[0], range[1]});
        continue;
      }
      int[] last = merged.get(merged.size() - 1);
      if (range[0] <= last[1]) { // overlap or touch
        last[1] = Math.max(last[1], range[1]);
      } else {
        merged.add(new int[] {range[0], range[1]});
      }
    }
    // 4. Circular merge: if the last range reaches the end of the week and the first starts at the
    //    week start, they are contiguous across the wrap — fold the first into the last as a spill.
    if (merged.size() > 1) {
      int[] first = merged.get(0);
      int[] last = merged.get(merged.size() - 1);
      if (last[1] >= MINUTES_PER_WEEK && first[0] == 0) {
        last[1] = MINUTES_PER_WEEK + first[1];
        merged.remove(0);
      }
    }
    return merged;
  }
}
