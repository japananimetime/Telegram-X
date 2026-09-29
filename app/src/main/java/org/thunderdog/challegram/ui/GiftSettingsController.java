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
 * File created for gift-receiving settings (Slice 3)
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Gift-receiving settings for the current user. Reads the current value from
 * {@link TdApi.UserFullInfo#giftSettings} (passed in {@link Args}) and persists
 * changes via {@link TdApi.SetGiftSettings}.
 */
public class GiftSettingsController extends RecyclerViewController<GiftSettingsController.Args> implements View.OnClickListener {

  public static class Args {
    public final TdApi.GiftSettings settings;

    public Args (TdApi.GiftSettings settings) {
      this.settings = settings;
    }
  }

  public GiftSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_giftSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.GiftSettings);
  }

  private SettingsAdapter adapter;

  // Local mutable state mirrored into a GiftSettings on save.
  private boolean showGiftButton;
  private boolean unlimitedGifts;
  private boolean limitedGifts;
  private boolean upgradedGifts;
  private boolean giftsFromChannels;
  private boolean premiumSubscription;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    TdApi.GiftSettings settings = getArgumentsStrict().settings;
    if (settings != null) {
      showGiftButton = settings.showGiftButton;
      TdApi.AcceptedGiftTypes types = settings.acceptedGiftTypes;
      if (types != null) {
        unlimitedGifts = types.unlimitedGifts;
        limitedGifts = types.limitedGifts;
        upgradedGifts = types.upgradedGifts;
        giftsFromChannels = types.giftsFromChannels;
        premiumSubscription = types.premiumSubscription;
      }
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        boolean value = false;
        if (itemId == R.id.btn_giftShowButton) {
          value = showGiftButton;
        } else if (itemId == R.id.btn_giftAcceptUnlimited) {
          value = unlimitedGifts;
        } else if (itemId == R.id.btn_giftAcceptLimited) {
          value = limitedGifts;
        } else if (itemId == R.id.btn_giftAcceptUpgraded) {
          value = upgradedGifts;
        } else if (itemId == R.id.btn_giftAcceptChannels) {
          value = giftsFromChannels;
        } else if (itemId == R.id.btn_giftAcceptPremium) {
          value = premiumSubscription;
        }
        view.getToggler().setRadioEnabled(value, isUpdate);
      }
    };

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_giftShowButton, 0, R.string.GiftShowButton, showGiftButton));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.GiftShowButtonDesc));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.GiftAcceptedTypes));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_giftAcceptUnlimited, 0, R.string.GiftAcceptUnlimited, unlimitedGifts));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_giftAcceptLimited, 0, R.string.GiftAcceptLimited, limitedGifts));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_giftAcceptUpgraded, 0, R.string.GiftAcceptUpgraded, upgradedGifts));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_giftAcceptChannels, 0, R.string.GiftAcceptChannels, giftsFromChannels));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_giftAcceptPremium, 0, R.string.GiftAcceptPremium, premiumSubscription));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.GiftAcceptedTypesDesc));

    adapter.setItems(items.toArray(new ListItem[0]), false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    boolean newValue = adapter.toggleView(v);
    if (id == R.id.btn_giftShowButton) {
      showGiftButton = newValue;
    } else if (id == R.id.btn_giftAcceptUnlimited) {
      unlimitedGifts = newValue;
    } else if (id == R.id.btn_giftAcceptLimited) {
      limitedGifts = newValue;
    } else if (id == R.id.btn_giftAcceptUpgraded) {
      upgradedGifts = newValue;
    } else if (id == R.id.btn_giftAcceptChannels) {
      giftsFromChannels = newValue;
    } else if (id == R.id.btn_giftAcceptPremium) {
      premiumSubscription = newValue;
    } else {
      return;
    }
    save();
  }

  private boolean isSaving;

  private void save () {
    if (isSaving) {
      return;
    }
    isSaving = true;
    TdApi.AcceptedGiftTypes types = new TdApi.AcceptedGiftTypes(unlimitedGifts, limitedGifts, upgradedGifts, giftsFromChannels, premiumSubscription);
    TdApi.GiftSettings settings = new TdApi.GiftSettings(showGiftButton, types);
    tdlib.send(new TdApi.SetGiftSettings(settings), (ok, error) -> runOnUiThreadOptional(() -> {
      isSaving = false;
      if (error != null) {
        UI.showError(error);
      }
    }));
  }
}
