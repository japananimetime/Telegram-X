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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Hub for the Telegram Business suite. Reads the current user's
 * {@link TdApi.BusinessInfo} (from UserFullInfo) and surfaces every business
 * feature. The fully-editable sub-features (start page, location, chat links)
 * navigate to dedicated editors; the remaining toggles (opening hours, greeting,
 * away message, connected bot) display their current state and can be turned off
 * here — rich editors for them are follow-ups.
 */
public class SettingsBusinessController extends RecyclerViewController<Void> implements View.OnClickListener {

  private SettingsAdapter adapter;
  private @Nullable TdApi.BusinessInfo businessInfo;

  public SettingsBusinessController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.TelegramBusiness);
  }

  @Override
  public int getId () {
    return R.id.controller_business;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    recyclerView.setAdapter(adapter);
    buildCells();
    loadBusinessInfo();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    // Re-read after returning from a sub-editor so the value rows reflect changes.
    loadBusinessInfo();
  }

  private void loadBusinessInfo () {
    tdlib.cache().userFull(tdlib.myUserId(), userFull -> runOnUiThreadOptional(() -> {
      this.businessInfo = userFull != null ? userFull.businessInfo : null;
      buildCells();
    }));
  }

  private static String onOff (boolean on) {
    return Lang.getString(on ? R.string.BusinessValueOn : R.string.BusinessValueOff);
  }

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    addValueRow(items, R.id.btn_businessStartPage, R.drawable.baseline_home_24, R.string.BusinessStartPage,
      businessInfo != null && businessInfo.startPage != null && !StringUtils.isEmpty(businessInfo.startPage.title)
        ? businessInfo.startPage.title : onOff(businessInfo != null && businessInfo.startPage != null), true);
    addValueRow(items, R.id.btn_businessLocation, R.drawable.baseline_location_on_24, R.string.BusinessLocation,
      businessInfo != null && businessInfo.location != null && !StringUtils.isEmpty(businessInfo.location.address)
        ? businessInfo.location.address : onOff(businessInfo != null && businessInfo.location != null), false);
    addValueRow(items, R.id.btn_businessOpeningHours, R.drawable.baseline_access_time_24, R.string.BusinessOpeningHours,
      onOff(businessInfo != null && businessInfo.openingHours != null), false);
    addValueRow(items, R.id.btn_businessGreeting, R.drawable.baseline_chat_bubble_24, R.string.BusinessGreetingMessage,
      onOff(businessInfo != null && businessInfo.greetingMessageSettings != null), false);
    addValueRow(items, R.id.btn_businessAway, R.drawable.baseline_schedule_24, R.string.BusinessAwayMessage,
      onOff(businessInfo != null && businessInfo.awayMessageSettings != null), false);
    addValueRow(items, R.id.btn_businessChatLinks, R.drawable.baseline_link_24, R.string.BusinessChatLinks, null, false);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, false);
  }

  private void addValueRow (List<ListItem> items, int id, int icon, int titleRes, @Nullable String value, boolean first) {
    if (!first) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    ListItem item = new ListItem(value != null ? ListItem.TYPE_VALUED_SETTING_COMPACT : ListItem.TYPE_SETTING, id, icon, titleRes);
    if (value != null) {
      item.setData(value);
    }
    items.add(item);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_businessStartPage) {
      BusinessStartPageController c = new BusinessStartPageController(context, tdlib);
      c.setArguments(businessInfo != null ? businessInfo.startPage : null);
      navigateTo(c);
    } else if (id == R.id.btn_businessLocation) {
      BusinessLocationController c = new BusinessLocationController(context, tdlib);
      c.setArguments(businessInfo != null ? businessInfo.location : null);
      navigateTo(c);
    } else if (id == R.id.btn_businessChatLinks) {
      navigateTo(new BusinessChatLinksController(context, tdlib));
    } else if (id == R.id.btn_businessOpeningHours) {
      showTurnOffOptions(businessInfo != null && businessInfo.openingHours != null,
        () -> tdlib.send(new TdApi.SetBusinessOpeningHours(null), okHandler()));
    } else if (id == R.id.btn_businessGreeting) {
      showTurnOffOptions(businessInfo != null && businessInfo.greetingMessageSettings != null,
        () -> tdlib.send(new TdApi.SetBusinessGreetingMessageSettings(null), okHandler()));
    } else if (id == R.id.btn_businessAway) {
      showTurnOffOptions(businessInfo != null && businessInfo.awayMessageSettings != null,
        () -> tdlib.send(new TdApi.SetBusinessAwayMessageSettings(null), okHandler()));
    }
  }

  private void showTurnOffOptions (boolean isOn, Runnable turnOff) {
    if (!isOn) {
      UI.showToast(R.string.BusinessEditUnavailable, Toast.LENGTH_SHORT);
      return;
    }
    showOptions(Lang.getString(R.string.BusinessEditUnavailable),
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.BusinessTurnOff), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_done) {
          turnOff.run();
        }
        return true;
      });
  }

  private Tdlib.ResultHandler<TdApi.Ok> okHandler () {
    return (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
      } else {
        loadBusinessInfo();
      }
    });
  }
}
