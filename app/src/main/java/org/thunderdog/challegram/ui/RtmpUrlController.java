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
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the RTMP server URL and stream key for a chat's RTMP video chat, so the
 * user can configure streaming software (OBS, etc.). Both fields are copyable;
 * the key can be regenerated (ReplaceVideoChatRtmpUrl). Fetched via
 * GetVideoChatRtmpUrl.
 */
public class RtmpUrlController extends RecyclerViewController<RtmpUrlController.Args> implements View.OnClickListener {

  public static class Args {
    public final long chatId;
    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  private SettingsAdapter adapter;
  private long chatId;
  private @Nullable TdApi.RtmpUrl rtmpUrl;

  public RtmpUrlController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.RtmpStream);
  }

  @Override
  public int getId () {
    return R.id.controller_rtmpUrl;
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
        if (rtmpUrl == null) {
          return;
        }
        if (item.getId() == R.id.btn_rtmpServerUrl) {
          view.setData(rtmpUrl.url);
        } else if (item.getId() == R.id.btn_rtmpStreamKey) {
          view.setData(rtmpUrl.streamKey);
        }
      }
    };
    recyclerView.setAdapter(adapter);
    buildLoadingCells();
    loadUrl(false);
  }

  private void loadUrl (boolean replace) {
    TdApi.Function<TdApi.RtmpUrl> function = replace
      ? new TdApi.ReplaceVideoChatRtmpUrl(chatId)
      : new TdApi.GetVideoChatRtmpUrl(chatId);
    tdlib.send(function, (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        if (replace) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
        } else {
          showError(TD.toErrorString(error));
        }
        return;
      }
      rtmpUrl = result;
      buildCells();
    }));
  }

  private void buildLoadingCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void showError (String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private void buildCells () {
    if (rtmpUrl == null) {
      return;
    }
    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.RtmpStream));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_rtmpServerUrl, 0, R.string.RtmpServerUrl));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_rtmpStreamKey, 0, R.string.RtmpStreamKey));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.RtmpStreamHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_rtmpRevoke, 0, R.string.RtmpRevoke)
      .setTextColorId(ColorId.textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (rtmpUrl == null) {
      return;
    }
    if (id == R.id.btn_rtmpServerUrl) {
      UI.copyText(rtmpUrl.url, R.string.CopiedText);
    } else if (id == R.id.btn_rtmpStreamKey) {
      UI.copyText(rtmpUrl.streamKey, R.string.CopiedText);
    } else if (id == R.id.btn_rtmpRevoke) {
      showConfirm(Lang.getString(R.string.RtmpRevokeConfirm), Lang.getString(R.string.RtmpRevoke), R.drawable.baseline_sync_24, ViewController.OptionColor.RED, () -> loadUrl(true));
    }
  }
}
