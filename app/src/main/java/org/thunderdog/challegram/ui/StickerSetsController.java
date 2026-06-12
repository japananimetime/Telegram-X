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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Sticker-pack authoring: lists the user's owned sticker sets (GetOwnedStickerSets),
 * lets them create a new pack (CreateNewStickerSet) and add stickers to an existing
 * one (AddStickerToSet) by picking an image, rename a pack (SetStickerSetTitle), and
 * open/share/copy a pack link.
 *
 * Stickers are added by picking a still image (PNG/WEBP — TDLib converts PNG to WEBP
 * server-side) or an animated WEBM/TGS file; the format is inferred from the file
 * extension. Per-sticker emoji are collected via a prompt. Editing emoji of existing
 * stickers and reordering are follow-ups.
 */
public class StickerSetsController extends RecyclerViewController<Void> implements View.OnClickListener, ActivityResultHandler {
  private static final int REQUEST_PICK_STICKER = 0x57000001;

  private SettingsAdapter adapter;
  private final List<TdApi.StickerSetInfo> sets = new ArrayList<>();
  private boolean loaded;

  // Pending creation state, captured before the image picker is launched.
  private @Nullable String pendingTitle;       // non-null => creating a new pack
  private @Nullable String pendingAddToName;   // non-null => adding to an existing pack

  public StickerSetsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.StickerPacks);
  }

  @Override
  public int getId () {
    return R.id.controller_stickerSets;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_stickerSet) {
          TdApi.StickerSetInfo info = (TdApi.StickerSetInfo) item.getData();
          if (info != null) {
            view.setData(Lang.plural(R.string.xStickers, info.size));
          }
        }
      }
    };
    recyclerView.setAdapter(adapter);
    buildCells();
    loadSets();
  }

  private void loadSets () {
    tdlib.send(new TdApi.GetOwnedStickerSets(0, 100), (result, error) -> runOnUiThreadOptional(() -> {
      loaded = true;
      if (error == null && result != null && result.sets != null) {
        sets.clear();
        for (TdApi.StickerSetInfo info : result.sets) {
          sets.add(info);
        }
      }
      buildCells();
    }));
  }

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_stickerSetCreate, R.drawable.baseline_create_24, R.string.StickerPackCreate)
      .setTextColorId(ColorId.textNeutral));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.StickerPacksHint));

    if (sets.isEmpty()) {
      if (loaded) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.StickerPacksEmpty));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    } else {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.StickerPacksMine));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (TdApi.StickerSetInfo info : sets) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_stickerSet, R.drawable.deproko_baseline_stickers_24, info.title, false);
        item.setData(info);
        item.setLongId(info.id);
        items.add(item);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_stickerSetCreate) {
      promptCreate();
    } else if (id == R.id.btn_stickerSet) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.StickerSetInfo) {
        showSetOptions((TdApi.StickerSetInfo) item.getData());
      }
    }
  }

  private void promptCreate () {
    openInputAlert(Lang.getString(R.string.StickerPackCreate), Lang.getString(R.string.StickerPackTitleHint),
      R.string.Continue, R.string.Cancel, null, (inputView, title) -> {
        if (StringUtils.isEmpty(title != null ? title.trim() : null)) {
          return false;
        }
        pendingTitle = title.trim();
        pendingAddToName = null;
        pickStickerImage();
        return true;
      }, true);
  }

  private void showSetOptions (TdApi.StickerSetInfo info) {
    showOptions(info.title,
      new int[] {R.id.btn_open, R.id.btn_stickerSetAdd, R.id.btn_edit, R.id.btn_copyLink, R.id.btn_share},
      new String[] {
        Lang.getString(R.string.Open),
        Lang.getString(R.string.StickerPackAddSticker),
        Lang.getString(R.string.StickerPackRename),
        Lang.getString(R.string.CopyLink),
        Lang.getString(R.string.Share)
      },
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_visibility_24, R.drawable.baseline_create_24, R.drawable.baseline_edit_24, R.drawable.baseline_content_copy_24, R.drawable.baseline_forward_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_open) {
          tdlib.ui().showStickerSet(this, info.id, null);
        } else if (optionId == R.id.btn_stickerSetAdd) {
          pendingTitle = null;
          pendingAddToName = info.name;
          pickStickerImage();
        } else if (optionId == R.id.btn_edit) {
          promptRename(info);
        } else if (optionId == R.id.btn_copyLink) {
          UI.copyText(tdlib.tMeUrl("addstickers/" + info.name), R.string.CopiedLink);
        } else if (optionId == R.id.btn_share) {
          tdlib.ui().shareUrl(this, tdlib.tMeUrl("addstickers/" + info.name));
        }
        return true;
      });
  }

  private void promptRename (TdApi.StickerSetInfo info) {
    openInputAlert(Lang.getString(R.string.StickerPackRename), Lang.getString(R.string.StickerPackTitleHint),
      R.string.Save, R.string.Cancel, info.title, (inputView, title) -> {
        final String newTitle = title != null ? title.trim() : "";
        if (StringUtils.isEmpty(newTitle)) {
          return false;
        }
        tdlib.send(new TdApi.SetStickerSetTitle(info.name, newTitle), (result, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
          } else {
            info.title = newTitle;
            buildCells();
          }
        }));
        return true;
      }, true);
  }

  private void pickStickerImage () {
    if (context.permissions().requestReadExternalStorage(Permissions.ReadType.EXTERNAL_IMAGES, grantResult -> {
      if (grantResult == Permissions.GrantResult.ALL) {
        pickStickerImage();
      }
    })) {
      return;
    }
    try {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("image/*");
      context.putActivityResultHandler(REQUEST_PICK_STICKER, this);
      context.startActivityForResult(intent, REQUEST_PICK_STICKER);
    } catch (Throwable t) {
      UI.showToast(R.string.AccessError, Toast.LENGTH_SHORT);
    }
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (requestCode != REQUEST_PICK_STICKER) {
      return;
    }
    if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
      pendingTitle = null;
      pendingAddToName = null;
      return;
    }
    final String path = resolveToLocalPath(data.getData());
    if (StringUtils.isEmpty(path)) {
      UI.showToast(R.string.AccessError, Toast.LENGTH_SHORT);
      pendingTitle = null;
      pendingAddToName = null;
      return;
    }
    promptEmojiThenSubmit(path);
  }

  private void promptEmojiThenSubmit (String path) {
    openInputAlert(Lang.getString(R.string.StickerEmojiPrompt), Lang.getString(R.string.StickerEmojiHint),
      R.string.Done, R.string.Cancel, null, (inputView, emojis) -> {
        final String value = emojis != null ? emojis.trim() : "";
        if (StringUtils.isEmpty(value)) {
          return false;
        }
        submitSticker(path, value);
        return true;
      }, true);
  }

  private static TdApi.StickerFormat formatForPath (String path) {
    String lower = path.toLowerCase();
    if (lower.endsWith(".webm")) {
      return new TdApi.StickerFormatWebm();
    }
    if (lower.endsWith(".tgs")) {
      return new TdApi.StickerFormatTgs();
    }
    return new TdApi.StickerFormatWebp(); // PNG/WEBP; PNG is converted server-side
  }

  private void submitSticker (String path, String emojis) {
    final TdApi.InputSticker sticker = new TdApi.InputSticker(
      new TdApi.InputFileLocal(path), formatForPath(path), emojis, null, null);
    if (!StringUtils.isEmpty(pendingTitle)) {
      final String title = pendingTitle;
      pendingTitle = null;
      tdlib.send(new TdApi.CreateNewStickerSet(tdlib.myUserId(), title, "", new TdApi.StickerTypeRegular(), false,
        new TdApi.InputSticker[] {sticker}, ""), (result, error) -> runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
        } else {
          UI.showToast(R.string.StickerPackCreated, Toast.LENGTH_SHORT);
          loadSets();
          if (result != null) {
            tdlib.ui().showStickerSet(this, result.id, null);
          }
        }
      }));
    } else if (!StringUtils.isEmpty(pendingAddToName)) {
      final String name = pendingAddToName;
      pendingAddToName = null;
      tdlib.send(new TdApi.AddStickerToSet(tdlib.myUserId(), name, sticker), (result, error) -> runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
        } else {
          UI.showToast(R.string.StickerAdded, Toast.LENGTH_SHORT);
          loadSets();
        }
      }));
    }
  }

  /**
   * Resolve a picked content/file Uri to a local filesystem path TDLib can read.
   * Falls back to copying the stream into the app cache when the Uri can't be
   * resolved to a real readable file.
   */
  private @Nullable String resolveToLocalPath (Uri uri) {
    String resolved = U.tryResolveFilePath(uri);
    if (!StringUtils.isEmpty(resolved)) {
      File f = new File(resolved);
      if (f.exists() && f.canRead()) {
        return resolved;
      }
    }
    // Copy the content stream into cache.
    try {
      String name = uri.getLastPathSegment();
      String ext = ".png";
      if (!StringUtils.isEmpty(name) && name.contains(".")) {
        ext = name.substring(name.lastIndexOf('.'));
      }
      File outDir = new File(context.getCacheDir(), "stickers");
      //noinspection ResultOfMethodCallIgnored
      outDir.mkdirs();
      File outFile = new File(outDir, "sticker_" + Math.abs(uri.hashCode()) + ext);
      try (InputStream in = context.getContentResolver().openInputStream(uri);
           OutputStream out = new FileOutputStream(outFile)) {
        if (in == null) {
          return null;
        }
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) {
          out.write(buf, 0, read);
        }
      }
      return outFile.getAbsolutePath();
    } catch (Throwable t) {
      return null;
    }
  }
}
