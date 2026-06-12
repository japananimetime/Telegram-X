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
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;

/**
 * Recently/actively downloaded files (SearchFileDownloads). Lets the user pause/
 * resume an active download or remove a file from the downloads list.
 */
public class DownloadsController extends RecyclerViewController<Void> implements View.OnClickListener {

  private SettingsAdapter adapter;
  private final List<TdApi.FileDownload> downloads = new ArrayList<>();
  private String nextOffset = "";
  private boolean isLoading;

  public DownloadsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Downloads);
  }

  @Override
  public int getId () {
    return R.id.controller_downloads;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_download) {
          TdApi.FileDownload download = (TdApi.FileDownload) item.getData();
          if (download != null) {
            view.setData(downloadStatus(download));
          }
        }
      }
    };
    recyclerView.setAdapter(adapter);
    buildLoadingCells();
    fetch("");
  }

  private void buildLoadingCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.Downloads));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void fetch (String offset) {
    if (isLoading) {
      return;
    }
    final boolean isFirstPage = StringUtils.isEmpty(offset);
    isLoading = true;
    tdlib.send(new TdApi.SearchFileDownloads("", false, false, offset, 50), (result, error) -> runOnUiThreadOptional(() -> {
      isLoading = false;
      if (error != null) {
        if (isFirstPage) {
          showError(TD.toErrorString(error));
        } else {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
        }
        return;
      }
      if (isFirstPage) {
        downloads.clear();
      }
      if (result.files != null) {
        Collections.addAll(downloads, result.files);
      }
      nextOffset = result.nextOffset;
      buildCells();
    }));
  }

  private void showError (String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.Downloads));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private String downloadName (TdApi.Message message) {
    if (message == null) {
      return Lang.getString(R.string.File);
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessageDocument.CONSTRUCTOR: {
        String name = ((TdApi.MessageDocument) message.content).document.fileName;
        return StringUtils.isEmpty(name) ? Lang.getString(R.string.File) : name;
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
        if (!StringUtils.isEmpty(audio.fileName)) return audio.fileName;
        return StringUtils.isEmpty(audio.title) ? Lang.getString(R.string.File) : audio.title;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        String name = ((TdApi.MessageVideo) message.content).video.fileName;
        return StringUtils.isEmpty(name) ? Lang.getString(R.string.Video) : name;
      }
      default: {
        ContentPreview preview = ContentPreview.getChatListPreview(tdlib, message.chatId, message, false);
        return preview != null ? preview.buildText(false) : Lang.getString(R.string.File);
      }
    }
  }

  private String downloadStatus (TdApi.FileDownload download) {
    int dateRes;
    String state;
    if (download.completeDate != 0) {
      state = Lang.getString(R.string.DownloadComplete);
      dateRes = download.completeDate;
    } else if (download.isPaused) {
      state = Lang.getString(R.string.DownloadPaused);
      dateRes = download.addDate;
    } else {
      state = Lang.getString(R.string.Downloading);
      dateRes = download.addDate;
    }
    return state + " · " + Lang.dateYearShortTime(dateRes, TimeUnit.SECONDS);
  }

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();

    if (downloads.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DownloadsEmpty));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.setItems(items, true);
      return;
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (TdApi.FileDownload download : downloads) {
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      first = false;
      ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_download,
        R.drawable.baseline_file_download_24, downloadName(download.message));
      item.setData(download);
      items.add(item);
    }
    if (!StringUtils.isEmpty(nextOffset)) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showMore, 0, R.string.ShowMore).setTextColorId(ColorId.textNeutral));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Clear-all action
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_clearDownloads, R.drawable.baseline_delete_sweep_24, R.string.DownloadsClearAll).setTextColorId(ColorId.textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_download) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.FileDownload) {
        showDownloadOptions((TdApi.FileDownload) item.getData());
      }
    } else if (id == R.id.btn_showMore) {
      fetch(nextOffset);
    } else if (id == R.id.btn_clearDownloads) {
      tdlib.send(new TdApi.RemoveAllFilesFromDownloads(false, false, false), (ok, error) -> runOnUiThreadOptional(() -> {
        downloads.clear();
        nextOffset = "";
        buildCells();
      }));
    }
  }

  private void showDownloadOptions (TdApi.FileDownload download) {
    boolean active = download.completeDate == 0;
    List<Integer> ids = new ArrayList<>();
    List<String> strings = new ArrayList<>();
    List<Integer> colors = new ArrayList<>();
    List<Integer> icons = new ArrayList<>();
    if (active) {
      ids.add(R.id.btn_pauseDownload);
      strings.add(Lang.getString(download.isPaused ? R.string.DownloadResume : R.string.DownloadPause));
      colors.add(ViewController.OptionColor.NORMAL);
      icons.add(download.isPaused ? R.drawable.baseline_file_download_24 : R.drawable.baseline_pause_24);
    }
    ids.add(R.id.btn_removeDownload);
    strings.add(Lang.getString(R.string.DownloadRemove));
    colors.add(ViewController.OptionColor.RED);
    icons.add(R.drawable.baseline_delete_24);

    showOptions(downloadName(download.message), toIntArray(ids), strings.toArray(new String[0]), toIntArray(colors), toIntArray(icons), (itemView, id) -> {
      if (id == R.id.btn_pauseDownload) {
        tdlib.send(new TdApi.ToggleDownloadIsPaused(download.fileId, !download.isPaused), tdlib.typedOkHandler());
        download.isPaused = !download.isPaused;
        buildCells();
      } else if (id == R.id.btn_removeDownload) {
        tdlib.send(new TdApi.RemoveFileFromDownloads(download.fileId, false), tdlib.typedOkHandler());
        downloads.remove(download);
        buildCells();
      }
      return true;
    });
  }

  private static int[] toIntArray (List<Integer> list) {
    int[] result = new int[list.size()];
    for (int i = 0; i < list.size(); i++) {
      result[i] = list.get(i);
    }
    return result;
  }
}
