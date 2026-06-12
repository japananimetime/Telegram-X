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
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Lists Telegram Business chat links (GetBusinessChatLinks), lets the user create
 * a new one (CreateBusinessChatLink) with a ready-to-send message, copy/share an
 * existing link, and delete it (DeleteBusinessChatLink).
 */
public class BusinessChatLinksController extends RecyclerViewController<Void> implements View.OnClickListener {

  private SettingsAdapter adapter;
  private final List<TdApi.BusinessChatLink> links = new ArrayList<>();
  private boolean loaded;

  public BusinessChatLinksController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BusinessChatLinks);
  }

  @Override
  public int getId () {
    return R.id.controller_businessChatLinks;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_businessChatLink) {
          TdApi.BusinessChatLink link = (TdApi.BusinessChatLink) item.getData();
          if (link != null) {
            view.setData(Lang.plural(R.string.BusinessChatLinkViews, link.viewCount));
          }
        }
      }
    };
    recyclerView.setAdapter(adapter);
    buildCells();
    tdlib.send(new TdApi.GetBusinessChatLinks(), (result, error) -> runOnUiThreadOptional(() -> {
      loaded = true;
      if (error == null && result != null && result.links != null) {
        links.clear();
        for (TdApi.BusinessChatLink link : result.links) {
          links.add(link);
        }
      }
      buildCells();
    }));
  }

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessChatLinksHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_businessChatLinkCreate, R.drawable.baseline_add_link_24, R.string.BusinessChatLinkCreate)
      .setTextColorId(org.thunderdog.challegram.theme.ColorId.textNeutral));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (links.isEmpty()) {
      if (loaded) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessChatLinksEmpty));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (TdApi.BusinessChatLink link : links) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        String title = !StringUtils.isEmpty(link.title) ? link.title :
          (link.text != null && !StringUtils.isEmpty(link.text.text) ? link.text.text : link.link);
        ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_businessChatLink, R.drawable.baseline_link_24, title, false);
        item.setData(link);
        items.add(item);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_businessChatLinkCreate) {
      promptCreate();
    } else if (id == R.id.btn_businessChatLink) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.BusinessChatLink) {
        showLinkOptions((TdApi.BusinessChatLink) item.getData());
      }
    }
  }

  private void promptCreate () {
    openInputAlert(Lang.getString(R.string.BusinessChatLinkCreate), Lang.getString(R.string.BusinessChatLinkText),
      R.string.Create, R.string.Cancel, null, (inputView, text) -> {
        if (StringUtils.isEmpty(text != null ? text.trim() : null)) {
          return false;
        }
        TdApi.InputBusinessChatLink linkInfo = new TdApi.InputBusinessChatLink(new TdApi.FormattedText(text.trim(), null), "");
        tdlib.send(new TdApi.CreateBusinessChatLink(linkInfo), (result, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
          } else if (result != null) {
            links.add(result);
            buildCells();
          }
        }));
        return true;
      }, true);
  }

  private void showLinkOptions (TdApi.BusinessChatLink link) {
    showOptions(link.link,
      new int[] {R.id.btn_copyLink, R.id.btn_share, R.id.btn_delete},
      new String[] {Lang.getString(R.string.CopyLink), Lang.getString(R.string.Share), Lang.getString(R.string.BusinessChatLinkDelete)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED},
      new int[] {R.drawable.baseline_content_copy_24, R.drawable.baseline_forward_24, R.drawable.baseline_delete_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_copyLink) {
          UI.copyText(link.link, R.string.CopiedLink);
        } else if (optionId == R.id.btn_share) {
          tdlib.ui().shareUrl(this, link.link);
        } else if (optionId == R.id.btn_delete) {
          deleteLink(link);
        }
        return true;
      });
  }

  private void deleteLink (TdApi.BusinessChatLink link) {
    tdlib.send(new TdApi.DeleteBusinessChatLink(link.link), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
      } else {
        links.remove(link);
        buildCells();
      }
    }));
  }
}
