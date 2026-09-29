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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.StickerSuggestionAdapter;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

/**
 * Editor for the Telegram Business start page (title + message + greeting
 * sticker). Sends {@link TdApi.SetBusinessStartPage}; passing an empty title,
 * empty message and no sticker removes the custom start page.
 * <p>
 * Note: pinning a map point is NOT part of the start page — the business
 * location (address + optional {@link TdApi.Location}) is a separate concept
 * edited by {@link BusinessLocationController} via {@code SetBusinessLocation}.
 */
public class BusinessStartPageController extends EditBaseController<TdApi.BusinessStartPage> implements SettingsAdapter.TextChangeListener, View.OnClickListener {

  private SettingsAdapter adapter;
  private String title = "";
  private String message = "";
  private @Nullable TdApi.Sticker sticker;

  private ListItem stickerItem;

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
      this.sticker = args.sticker;
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

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_stickers) {
          view.setData(item.getStringValue());
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

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    stickerItem = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_stickers, R.drawable.deproko_baseline_stickers_24, R.string.Sticker)
      .setStringValue(stickerValue());
    items.add(stickerItem);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setTextChangeListener(this);
    adapter.setItems(items, false);

    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  private String stickerValue () {
    // Reuse the same On/Off value style as the rest of the business settings.
    return Lang.getString(sticker != null ? R.string.BusinessValueOn : R.string.BusinessValueOff);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_stickers) {
      openStickerPicker();
    }
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v) {
    if (id == R.id.btn_businessStartPage) {
      title = v.getText().toString();
    } else if (id == R.id.input) {
      message = v.getText().toString();
    }
  }

  // Sticker picker

  private @Nullable PopupLayout stickerPickerPopup;
  // Transient popup views that have theme-invalidate listeners registered against them; they
  // must be unregistered when the popup is dismissed/destroyed so the listener list doesn't
  // retain the recycled views.
  private @Nullable View stickerPopupShadowView;
  private @Nullable View stickerPopupContentView;

  private void openStickerPicker () {
    if (stickerPickerPopup != null) {
      return;
    }
    // Greeting stickers must belong to a sticker set (not custom emoji);
    // recent stickers are an ideal, lightweight source for the chooser. If the user has no
    // recent stickers, fall back to their installed sticker sets so the chooser still works.
    tdlib.client().send(new TdApi.GetRecentStickers(false), result -> runOnUiThreadOptional(() -> {
      if (result.getConstructor() == TdApi.Stickers.CONSTRUCTOR) {
        TdApi.Sticker[] stickers = ((TdApi.Stickers) result).stickers;
        if (stickers.length > 0) {
          showStickerPickerPopup(stickers);
          return;
        }
      }
      // No recent stickers (or the request failed) — fall back to installed sets.
      loadInstalledStickersFallback();
    }));
  }

  private void loadInstalledStickersFallback () {
    tdlib.client().send(new TdApi.GetInstalledStickerSets(new TdApi.StickerTypeRegular()), result -> runOnUiThreadOptional(() -> {
      if (result.getConstructor() != TdApi.StickerSets.CONSTRUCTOR) {
        UI.showToast(R.string.NoStickerSets, android.widget.Toast.LENGTH_SHORT);
        return;
      }
      TdApi.StickerSetInfo[] sets = ((TdApi.StickerSets) result).sets;
      if (sets.length == 0) {
        UI.showToast(R.string.NoStickerSets, android.widget.Toast.LENGTH_SHORT);
        return;
      }
      // Load the first installed set's stickers to seed the chooser.
      tdlib.client().send(new TdApi.GetStickerSet(sets[0].id), setResult -> runOnUiThreadOptional(() -> {
        if (setResult.getConstructor() != TdApi.StickerSet.CONSTRUCTOR) {
          UI.showToast(R.string.NoStickerSets, android.widget.Toast.LENGTH_SHORT);
          return;
        }
        TdApi.Sticker[] stickers = ((TdApi.StickerSet) setResult).stickers;
        if (stickers.length == 0) {
          UI.showToast(R.string.NoStickerSets, android.widget.Toast.LENGTH_SHORT);
          return;
        }
        showStickerPickerPopup(stickers);
      }));
    }));
  }

  private void showStickerPickerPopup (TdApi.Sticker[] stickers) {
    if (isDestroyed() || stickerPickerPopup != null) {
      return;
    }
    final Context context = context();

    final ArrayList<TGStickerObj> stickerObjs = new ArrayList<>(stickers.length);
    for (TdApi.Sticker s : stickers) {
      // Greeting sticker must not be a custom emoji.
      if (s.fullType != null && s.fullType.getConstructor() == TdApi.StickerFullTypeCustomEmoji.CONSTRUCTOR) {
        continue;
      }
      stickerObjs.add(new TGStickerObj(tdlib, s, s.emoji, s.fullType));
    }
    if (stickerObjs.isEmpty()) {
      UI.showToast(R.string.NoStickerSets, android.widget.Toast.LENGTH_SHORT);
      return;
    }

    final RecyclerView recyclerView = new RecyclerView(context);
    final LinearLayoutManager manager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
    recyclerView.setLayoutManager(manager);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    final StickerSuggestionAdapter stickerAdapter = new StickerSuggestionAdapter(this, manager, this, false);
    stickerAdapter.setCallback(new StickerSuggestionAdapter.Callback() {
      @Override
      public boolean onSendStickerSuggestion (View view, TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
        onStickerPicked(sticker);
        return true;
      }

      @Override
      public int getStickerSuggestionsTop (boolean isEmoji) {
        return 0;
      }

      @Override
      public int getStickerSuggestionPreviewViewportHeight () {
        return -1;
      }

      @Override
      public long getStickerSuggestionsChatId () {
        return 0;
      }
    });
    stickerAdapter.setStickers(stickerObjs);
    recyclerView.setAdapter(stickerAdapter);

    final int popupHeight = Screen.dp(72f) + Screen.dp(7f);

    ShadowView shadowView = new ShadowView(context);
    shadowView.setSimpleTopShadow(true);
    addThemeInvalidateListener(shadowView);

    FrameLayoutFix popupView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(popupView, ColorId.background);
    addThemeInvalidateListener(popupView);
    popupView.addView(shadowView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(7f), Gravity.TOP));
    popupView.addView(recyclerView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(72f), Gravity.TOP, 0, Screen.dp(7f), 0, 0));
    popupView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

    // Remember the views so their theme listeners can be unregistered when the popup goes away.
    stickerPopupShadowView = shadowView;
    stickerPopupContentView = popupView;

    stickerPickerPopup = new PopupLayout(context);
    stickerPickerPopup.init(true);
    stickerPickerPopup.setDismissListener(popup -> {
      removeStickerPopupThemeListeners();
      stickerPickerPopup = null;
    });
    stickerPickerPopup.setNeedRootInsets();
    stickerPickerPopup.showSimplePopupView(popupView, popupHeight);
  }

  private void removeStickerPopupThemeListeners () {
    if (stickerPopupShadowView != null) {
      removeThemeListenerByTarget(stickerPopupShadowView);
      stickerPopupShadowView = null;
    }
    if (stickerPopupContentView != null) {
      removeThemeListenerByTarget(stickerPopupContentView);
      stickerPopupContentView = null;
    }
  }

  private void onStickerPicked (TGStickerObj stickerObj) {
    TdApi.Sticker picked = stickerObj.getSticker();
    if (picked == null) {
      return;
    }
    this.sticker = picked;
    if (stickerItem != null && adapter != null) {
      stickerItem.setStringValue(stickerValue());
      adapter.updateValuedSetting(stickerItem);
    }
    if (stickerPickerPopup != null) {
      // hideWindow(true) fires the dismiss listener, which unregisters the theme listeners
      // and clears stickerPickerPopup.
      stickerPickerPopup.hideWindow(true);
    }
  }

  @Override
  public void destroy () {
    // Tear down the transient popup (and its theme listeners) BEFORE super.destroy(), so the
    // popup window and its views are cleaned up while the controller is still alive.
    if (stickerPickerPopup != null) {
      stickerPickerPopup.hideWindow(false);
      stickerPickerPopup = null;
    }
    removeStickerPopupThemeListeners();
    super.destroy();
  }

  @Override
  protected boolean onDoneClick () {
    if (isInProgress()) {
      return true;
    }
    setInProgress(true);
    final String title = this.title.trim();
    final String message = this.message.trim();
    final TdApi.Sticker sticker = this.sticker;
    final TdApi.InputBusinessStartPage startPage;
    if (StringUtils.isEmpty(title) && StringUtils.isEmpty(message) && sticker == null) {
      startPage = null; // removes the custom start page
    } else {
      // Greeting sticker is referenced by its persistent file id; pass null when none.
      TdApi.InputFile inputSticker = sticker != null ? new TdApi.InputFileId(sticker.sticker.id) : null;
      startPage = new TdApi.InputBusinessStartPage(title, message, inputSticker);
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
