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
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.QuickReplyListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Lists the account's quick-reply shortcuts (Business feature) and lets the user
 * create, rename and delete them. Loads via LoadQuickReplyShortcuts; the list is
 * cached in Tdlib and refreshed live via QuickReplyListener.
 *
 * <p>NOTE: TDLib has no standalone "create empty shortcut" function. A shortcut is
 * materialised implicitly by adding its first message (AddQuickReplyShortcutMessage
 * creates the shortcut if it doesn't already exist). The create flow below therefore
 * collects a shortcut name plus a first message text and persists both in one step,
 * which is the closest correct flow TDLib supports. Renaming uses
 * SetQuickReplyShortcutName.</p>
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

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_quickReplyCreate, R.drawable.baseline_add_24, R.string.QuickReplyCreate)
      .setTextColorId(org.thunderdog.challegram.theme.ColorId.textNeutral));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

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
    final int id = v.getId();
    if (id == R.id.btn_quickReplyCreate) {
      promptCreate();
    } else if (id == R.id.btn_quickReply) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.QuickReplyShortcut) {
        TdApi.QuickReplyShortcut shortcut = (TdApi.QuickReplyShortcut) item.getData();
        showShortcutOptions(shortcut);
      }
    }
  }

  private void showShortcutOptions (TdApi.QuickReplyShortcut shortcut) {
    showOptions("/" + shortcut.name,
      new int[] {R.id.btn_rename, R.id.btn_delete},
      new String[] {Lang.getString(R.string.QuickReplyRename), Lang.getString(R.string.QuickReplyDelete)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED},
      new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_delete_24},
      (itemView, id) -> {
        if (id == R.id.btn_rename) {
          promptRename(shortcut);
        } else if (id == R.id.btn_delete) {
          tdlib.send(new TdApi.DeleteQuickReplyShortcut(shortcut.id), (ok, error) -> runOnUiThreadOptional(() -> {
            if (error != null) {
              UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
            }
          }));
        }
        return true;
      });
  }

  // Step 1: collect the shortcut name. The name is validated by CheckQuickReplyShortcutName
  // and then the first message text is collected (step 2), because TDLib only persists a
  // shortcut once it has at least one message (AddQuickReplyShortcutMessage).
  private void promptCreate () {
    openInputAlert(Lang.getString(R.string.QuickReplyCreate), Lang.getString(R.string.QuickReplyName),
      R.string.Continue, R.string.Cancel, null, (inputView, name) -> {
        final String trimmedName = name != null ? name.trim() : null;
        if (StringUtils.isEmpty(trimmedName)) {
          return false;
        }
        tdlib.send(new TdApi.CheckQuickReplyShortcutName(trimmedName), (ok, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
          } else {
            promptCreateMessage(trimmedName);
          }
        }));
        return true;
      }, true);
  }

  // Step 2: collect the first message text and create the shortcut by adding it.
  private void promptCreateMessage (String shortcutName) {
    openInputAlert("/" + shortcutName, Lang.getString(R.string.QuickReplyMessageHint),
      R.string.Create, R.string.Cancel, null, (inputView, text) -> {
        final String trimmedText = text != null ? text.trim() : null;
        if (StringUtils.isEmpty(trimmedText)) {
          return false;
        }
        TdApi.InputMessageContent content = new TdApi.InputMessageText(new TdApi.FormattedText(trimmedText, null), null, false);
        // Adding the first message implicitly creates the shortcut (and persists it).
        tdlib.send(new TdApi.AddQuickReplyShortcutMessage(shortcutName, 0, content), (message, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
          }
          // On success the shortcut list is refreshed live via QuickReplyListener.
        }));
        return true;
      }, true);
  }

  private void promptRename (TdApi.QuickReplyShortcut shortcut) {
    openInputAlert(Lang.getString(R.string.QuickReplyRename), Lang.getString(R.string.QuickReplyName),
      R.string.Save, R.string.Cancel, shortcut.name, (inputView, name) -> {
        final String trimmedName = name != null ? name.trim() : null;
        if (StringUtils.isEmpty(trimmedName)) {
          return false;
        }
        if (trimmedName.equals(shortcut.name)) {
          return true;
        }
        tdlib.send(new TdApi.CheckQuickReplyShortcutName(trimmedName), (ok, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
          } else {
            tdlib.send(new TdApi.SetQuickReplyShortcutName(shortcut.id, trimmedName), (ok2, error2) -> runOnUiThreadOptional(() -> {
              if (error2 != null) {
                UI.showToast(TD.toErrorString(error2), Toast.LENGTH_SHORT);
              }
            }));
          }
        }));
        return true;
      }, true);
  }
}
