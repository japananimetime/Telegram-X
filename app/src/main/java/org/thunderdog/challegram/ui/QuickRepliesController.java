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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.QuickReplyListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the account's quick-reply shortcuts (Business feature) and lets the user
 * delete them. Loads via LoadQuickReplyShortcuts; the list is cached in Tdlib and
 * refreshed live via QuickReplyListener.
 */
public class QuickRepliesController extends RecyclerViewController<Void> implements View.OnClickListener, QuickReplyListener {

  private SettingsAdapter adapter;

  public QuickRepliesController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.QuickReplies);
  }

  @Override
  public int getId () {
    return R.id.controller_quickReplies;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_quickReply) {
          TdApi.QuickReplyShortcut shortcut = (TdApi.QuickReplyShortcut) item.getData();
          if (shortcut != null) {
            view.setData(Lang.plural(R.string.xMessages, shortcut.messageCount));
          }
        }
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.listeners().subscribeToQuickReplyUpdates(this);
    buildCells();
    tdlib.send(new TdApi.LoadQuickReplyShortcuts(), (ok, error) -> runOnUiThreadOptional(this::buildCells));
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromQuickReplyUpdates(this);
  }

  @Override
  public void onQuickReplyShortcutsChanged () {
    runOnUiThreadOptional(this::buildCells);
  }

  private void buildCells () {
    List<TdApi.QuickReplyShortcut> shortcuts = tdlib.getQuickReplyShortcuts();
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.QuickRepliesHint));

    if (shortcuts.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.QuickRepliesEmpty));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (TdApi.QuickReplyShortcut shortcut : shortcuts) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_quickReply, R.drawable.baseline_flash_on_24, "/" + shortcut.name);
        item.setData(shortcut);
        item.setLongId(shortcut.id);
        items.add(item);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_quickReply) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.QuickReplyShortcut) {
        TdApi.QuickReplyShortcut shortcut = (TdApi.QuickReplyShortcut) item.getData();
        showShortcutOptions(shortcut);
      }
    }
  }

  private void showShortcutOptions (TdApi.QuickReplyShortcut shortcut) {
    showOptions("/" + shortcut.name,
      new int[] {R.id.btn_delete},
      new String[] {Lang.getString(R.string.QuickReplyDelete)},
      new int[] {ViewController.OptionColor.RED},
      new int[] {R.drawable.baseline_delete_24},
      (itemView, id) -> {
        if (id == R.id.btn_delete) {
          tdlib.send(new TdApi.DeleteQuickReplyShortcut(shortcut.id), (ok, error) -> runOnUiThreadOptional(() -> {
            if (error != null) {
              UI.showToast(error.message, Toast.LENGTH_SHORT);
            }
          }));
        }
        return true;
      });
  }
}
