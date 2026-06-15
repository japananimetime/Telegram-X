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
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
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
 * Editor for the Telegram Business location. Sets a textual address via
 * {@link TdApi.SetBusinessLocation}; clearing the address removes the location.
 * An exact map point ({@link TdApi.Location}) can be pinned via the shared
 * {@link MediaLayout} location picker ({@link MediaLayout#MODE_LOCATION}); when
 * editing an existing location its map point (if any) is preserved.
 */
public class BusinessLocationController extends EditBaseController<TdApi.BusinessLocation> implements SettingsAdapter.TextChangeListener, View.OnClickListener, MediaLayout.LocationPickerCallback {

  private SettingsAdapter adapter;
  private String address = "";
  private @Nullable TdApi.Location existingPoint;

  public BusinessLocationController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_businessLocation;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BusinessLocation);
  }

  @Override
  public void setArguments (@Nullable TdApi.BusinessLocation args) {
    super.setArguments(args);
    if (args != null) {
      this.address = args.address != null ? args.address : "";
      this.existingPoint = args.location;
    }
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Views.setSingleLine(editText.getEditText(), false);
        editText.setMaxLength(96);
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_chooseLocationOnMap) {
          view.setData(existingPoint != null ? R.string.BusinessLocationMapSet : R.string.BusinessLocationMapEmpty);
        }
      }
    };

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BusinessLocationAddress));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_EDITTEXT_REUSABLE, R.id.input, 0, R.string.BusinessLocationAddress)
      .setStringValue(address));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_chooseLocationOnMap, 0, R.string.BusinessLocationMap));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.BusinessLocationHint).setTextColorId(ColorId.textLight));

    adapter.setTextChangeListener(this);
    adapter.setItems(items, false);

    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v) {
    address = v.getText().toString();
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_chooseLocationOnMap) {
      MediaLayout mediaLayout = new MediaLayout(this);
      mediaLayout.initLocationPicker(this);
      mediaLayout.show();
    }
  }

  @Override
  public void onLocationPicked (TdApi.Location location, int heading, TdApi.MessageSendOptions sendOptions) {
    // User committed a point in the shared map picker. Store it and refresh the row.
    this.existingPoint = location;
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_chooseLocationOnMap);
    }
    // SetBusinessLocation requires a 1-96 character address (the point alone is
    // not a valid business location). If we already have one, persist the new
    // point right away; otherwise keep it locally — it will be saved on Done once
    // an address is entered.
    if (!StringUtils.isEmpty(this.address.trim())) {
      saveLocation(false);
    } else {
      UI.showToast(R.string.BusinessLocationAddress, android.widget.Toast.LENGTH_SHORT);
    }
  }

  @Override
  protected boolean onDoneClick () {
    return saveLocation(true);
  }

  private boolean saveLocation (boolean navigateBackOnSuccess) {
    if (isInProgress()) {
      return true;
    }
    setInProgress(true);
    final String address = this.address.trim();
    final TdApi.BusinessLocation location;
    if (StringUtils.isEmpty(address)) {
      location = null; // removes the location
    } else {
      location = new TdApi.BusinessLocation(existingPoint, address);
    }
    tdlib.send(new TdApi.SetBusinessLocation(location), (result, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);
      if (error != null) {
        UI.showToast(TD.toErrorString(error), android.widget.Toast.LENGTH_SHORT);
      } else if (navigateBackOnSuccess) {
        onSaveCompleted();
      }
    }));
    return true;
  }
}
