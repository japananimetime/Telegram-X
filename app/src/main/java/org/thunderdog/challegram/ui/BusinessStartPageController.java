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
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

/**
 * Editor for the Telegram Business start page (title + message). Sends
 * {@link TdApi.SetBusinessStartPage}; passing an empty title and message removes
 * the custom start page. Attaching a greeting sticker is a follow-up.
 */
public class BusinessStartPageController extends EditBaseController<TdApi.BusinessStartPage> implements SettingsAdapter.TextChangeListener {

  private SettingsAdapter adapter;
  private String title = "";
  private String message = "";

  public BusinessStartPageController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_businessStartPage;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BusinessStartPage);
  }

  @Override
  public void setArguments (@Nullable TdApi.BusinessStartPage args) {
    super.setArguments(args);
    if (args != null) {
      this.title = args.title != null ? args.title : "";
      this.message = args.message != null ? args.message : "";
    }
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        if (item.getId() == R.id.btn_businessStartPage) {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
          Views.setSingleLine(editText.getEditText(), true);
        } else {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
          Views.setSingleLine(editText.getEditText(), false);
        }
      }
    };

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessStartPageTitle));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_EDITTEXT_REUSABLE, R.id.btn_businessStartPage, 0, R.string.BusinessStartPageTitle)
      .setStringValue(title));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessStartPageMessage));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_EDITTEXT_REUSABLE, R.id.input, 0, R.string.BusinessStartPageMessage)
      .setStringValue(message));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessStartPageHint).setTextColorId(ColorId.textLight));

    adapter.setTextChangeListener(this);
    adapter.setItems(items, false);

    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v) {
    if (id == R.id.btn_businessStartPage) {
      title = v.getText().toString();
    } else if (id == R.id.input) {
      message = v.getText().toString();
    }
  }

  @Override
  protected boolean onDoneClick () {
    if (isInProgress()) {
      return true;
    }
    setInProgress(true);
    final String title = this.title.trim();
    final String message = this.message.trim();
    final TdApi.InputBusinessStartPage startPage;
    if (StringUtils.isEmpty(title) && StringUtils.isEmpty(message)) {
      startPage = null; // removes the custom start page
    } else {
      startPage = new TdApi.InputBusinessStartPage(title, message, null);
    }
    tdlib.send(new TdApi.SetBusinessStartPage(startPage), (result, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);
      if (error != null) {
        UI.showToast(org.thunderdog.challegram.data.TD.toErrorString(error), android.widget.Toast.LENGTH_SHORT);
      } else {
        onSaveCompleted();
      }
    }));
    return true;
  }
}
