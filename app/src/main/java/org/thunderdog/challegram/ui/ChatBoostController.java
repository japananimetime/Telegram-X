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
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Boost a channel/supergroup: shows the current boost level/progress and lets the
 * user apply their available boost slots (GetChatBoostStatus + GetAvailableChatBoostSlots
 * + BoostChat).
 */
public class ChatBoostController extends RecyclerViewController<ChatBoostController.Args> implements View.OnClickListener {

  public static class Args {
    public final long chatId;
    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  private SettingsAdapter adapter;
  private long chatId;
  private @Nullable TdApi.ChatBoostStatus status;
  private @Nullable TdApi.ChatBoostSlot[] slots;
  private boolean isBoosting;

  public ChatBoostController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Boost);
  }

  @Override
  public int getId () {
    return R.id.controller_chatBoost;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.chatId = args.chatId;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_boostLink && status != null) {
          view.setData(status.boostUrl);
        }
      }
    };
    recyclerView.setAdapter(adapter);
    buildLoadingCells();
    loadStatus();
  }

  private void buildLoadingCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.Boost));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void loadStatus () {
    tdlib.send(new TdApi.GetChatBoostStatus(chatId), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        showError(TD.toErrorString(error));
        return;
      }
      status = result;
      // Load the user's boost slots (best-effort; the status alone is enough to render).
      tdlib.send(new TdApi.GetAvailableChatBoostSlots(), (slotsResult, slotsError) -> runOnUiThreadOptional(() -> {
        slots = slotsError == null && slotsResult != null ? slotsResult.slots : new TdApi.ChatBoostSlot[0];
        buildCells();
      }));
    }));
  }

  private void showError (String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.Boost));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  // Slots the user can apply to this chat (not already boosting it).
  private List<TdApi.ChatBoostSlot> applicableSlots () {
    List<TdApi.ChatBoostSlot> result = new ArrayList<>();
    if (slots != null) {
      for (TdApi.ChatBoostSlot slot : slots) {
        if (slot.currentlyBoostedChatId != chatId) {
          result.add(slot);
        }
      }
    }
    return result;
  }

  private void buildCells () {
    if (status == null) {
      return;
    }
    List<ListItem> items = new ArrayList<>();

    // Level + progress
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BoostLevel));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0,
      Lang.getString(R.string.BoostLevelStatus, status.level, status.boostCount)));
    String progress;
    if (status.nextLevelBoostCount > 0) {
      int remaining = Math.max(0, status.nextLevelBoostCount - status.boostCount);
      progress = Lang.plural(R.string.BoostsToNextLevel, remaining);
    } else {
      progress = Lang.getString(R.string.BoostMaxLevel);
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, progress));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Premium subscribers info
    if (status.premiumMemberCount > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0,
        Lang.getString(R.string.BoostPremiumMembers, status.premiumMemberCount,
          (int) Math.round(status.premiumMemberPercentage))));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    // Boost action
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean canBoost = !applicableSlots().isEmpty();
    ListItem boostItem = new ListItem(ListItem.TYPE_SETTING, R.id.btn_boost, R.drawable.baseline_flash_on_24,
      canBoost ? R.string.BoostThisChat : R.string.BoostAlreadyBoosted);
    if (canBoost) {
      boostItem.setTextColorId(ColorId.textNeutral);
    }
    items.add(boostItem);
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_boostLink, R.drawable.baseline_link_24, R.string.BoostShareLink));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BoostHint));

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_boost) {
      boostChat();
    } else if (id == R.id.btn_boostLink) {
      if (status != null && !StringUtils.isEmpty(status.boostUrl)) {
        UI.copyText(status.boostUrl, R.string.CopiedLink);
      }
    }
  }

  private void boostChat () {
    if (isBoosting) {
      return;
    }
    List<TdApi.ChatBoostSlot> applicable = applicableSlots();
    if (applicable.isEmpty()) {
      UI.showToast(R.string.BoostNoSlots, Toast.LENGTH_SHORT);
      return;
    }
    // Apply one slot per tap (the official UX), so the user can boost incrementally.
    final int slotId = applicable.get(0).slotId;
    isBoosting = true;
    tdlib.send(new TdApi.BoostChat(chatId, new int[] {slotId}), (result, error) -> runOnUiThreadOptional(() -> {
      isBoosting = false;
      if (error != null) {
        UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
        return;
      }
      slots = result != null ? result.slots : slots;
      UI.showToast(R.string.BoostApplied, Toast.LENGTH_SHORT);
      // Refresh the status so the level/progress reflects the new boost.
      tdlib.send(new TdApi.GetChatBoostStatus(chatId), (statusResult, statusError) -> runOnUiThreadOptional(() -> {
        if (statusError == null) {
          status = statusResult;
        }
        buildCells();
      }));
    }));
  }
}
