/*
 * Community features settings controller
 * Adapted from moeGramX (https://github.com/moeCrafters/moeGramX)
 * Licensed under GPLv3
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.community.CommunityConfig;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SettingsCommunityController extends RecyclerViewController<Void> implements View.OnClickListener {

  private SettingsAdapter adapter;

  public SettingsCommunityController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_communitySettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.CommunityFeatures);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setDrawModifier(item.getDrawModifier());
        int itemId = item.getId();
        if (itemId == R.id.btn_hidePhoneNumber) {
          view.getToggler().setRadioEnabled(CommunityConfig.hidePhoneNumber, isUpdate);
        } else if (itemId == R.id.btn_squareAvatars) {
          view.getToggler().setRadioEnabled(CommunityConfig.squareAvatars, isUpdate);
        } else if (itemId == R.id.btn_disableReactions) {
          view.getToggler().setRadioEnabled(CommunityConfig.disableReactions, isUpdate);
        } else if (itemId == R.id.btn_hideStickerTimestamp) {
          view.getToggler().setRadioEnabled(CommunityConfig.hideStickerTimestamp, isUpdate);
        } else if (itemId == R.id.btn_showIdInProfile) {
          view.getToggler().setRadioEnabled(CommunityConfig.showIdInProfile, isUpdate);
        } else if (itemId == R.id.btn_blurDrawer) {
          view.getToggler().setRadioEnabled(CommunityConfig.blurDrawer, isUpdate);
        } else if (itemId == R.id.btn_rememberSendOptions) {
          view.getToggler().setRadioEnabled(CommunityConfig.rememberSendOptions, isUpdate);
        } else if (itemId == R.id.btn_disableCameraButton) {
          view.getToggler().setRadioEnabled(CommunityConfig.disableCameraButton, isUpdate);
        } else if (itemId == R.id.btn_disableRecordButton) {
          view.getToggler().setRadioEnabled(CommunityConfig.disableRecordButton, isUpdate);
        } else if (itemId == R.id.btn_roundedStickers) {
          view.getToggler().setRadioEnabled(CommunityConfig.roundedStickers, isUpdate);
        }
      }
    };

    List<ListItem> items = new ArrayList<>();

    // Header
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunityFeaturesDesc));

    // Appearance section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.CommunityAppearance));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hidePhoneNumber, 0, R.string.HidePhoneNumber));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_squareAvatars, 0, R.string.SquareAvatars));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_blurDrawer, 0, R.string.BlurDrawer));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_roundedStickers, 0, R.string.RoundedStickers));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Chat section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.CommunityChatFeatures));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_disableReactions, 0, R.string.DisableReactions));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideStickerTimestamp, 0, R.string.HideStickerTimestamp));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_rememberSendOptions, 0, R.string.RememberSendOptions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.RememberSendOptionsDesc));

    // Input section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.CommunityInputButtons));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_disableCameraButton, 0, R.string.DisableCameraButton));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_disableRecordButton, 0, R.string.DisableRecordButton));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Profile section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.CommunityProfile));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_showIdInProfile, 0, R.string.ShowIdInProfile));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ShowIdInProfileDesc));

    adapter.setItems(items, true);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    CommunityConfig config = CommunityConfig.instance();

    if (id == R.id.btn_hidePhoneNumber) {
      boolean newValue = !CommunityConfig.hidePhoneNumber;
      config.putBoolean(CommunityConfig.KEY_HIDE_PHONE_NUMBER, newValue);
      adapter.updateValuedSettingById(R.id.btn_hidePhoneNumber);
    } else if (id == R.id.btn_squareAvatars) {
      boolean newValue = !CommunityConfig.squareAvatars;
      config.putBoolean(CommunityConfig.KEY_SQUARE_AVATARS, newValue);
      adapter.updateValuedSettingById(R.id.btn_squareAvatars);
    } else if (id == R.id.btn_disableReactions) {
      boolean newValue = !CommunityConfig.disableReactions;
      config.putBoolean(CommunityConfig.KEY_DISABLE_REACTIONS, newValue);
      adapter.updateValuedSettingById(R.id.btn_disableReactions);
    } else if (id == R.id.btn_hideStickerTimestamp) {
      boolean newValue = !CommunityConfig.hideStickerTimestamp;
      config.putBoolean(CommunityConfig.KEY_HIDE_STICKER_TIMESTAMP, newValue);
      adapter.updateValuedSettingById(R.id.btn_hideStickerTimestamp);
    } else if (id == R.id.btn_showIdInProfile) {
      boolean newValue = !CommunityConfig.showIdInProfile;
      config.putBoolean(CommunityConfig.KEY_SHOW_ID_IN_PROFILE, newValue);
      adapter.updateValuedSettingById(R.id.btn_showIdInProfile);
    } else if (id == R.id.btn_blurDrawer) {
      boolean newValue = !CommunityConfig.blurDrawer;
      config.putBoolean(CommunityConfig.KEY_BLUR_DRAWER, newValue);
      adapter.updateValuedSettingById(R.id.btn_blurDrawer);
    } else if (id == R.id.btn_rememberSendOptions) {
      boolean newValue = !CommunityConfig.rememberSendOptions;
      config.putBoolean(CommunityConfig.KEY_REMEMBER_SEND_OPTIONS, newValue);
      adapter.updateValuedSettingById(R.id.btn_rememberSendOptions);
    } else if (id == R.id.btn_disableCameraButton) {
      boolean newValue = !CommunityConfig.disableCameraButton;
      config.putBoolean(CommunityConfig.KEY_DISABLE_CAMERA_BUTTON, newValue);
      adapter.updateValuedSettingById(R.id.btn_disableCameraButton);
    } else if (id == R.id.btn_disableRecordButton) {
      boolean newValue = !CommunityConfig.disableRecordButton;
      config.putBoolean(CommunityConfig.KEY_DISABLE_RECORD_BUTTON, newValue);
      adapter.updateValuedSettingById(R.id.btn_disableRecordButton);
    } else if (id == R.id.btn_roundedStickers) {
      boolean newValue = !CommunityConfig.roundedStickers;
      config.putBoolean(CommunityConfig.KEY_ROUNDED_STICKERS, newValue);
      adapter.updateValuedSettingById(R.id.btn_roundedStickers);
    }
  }
}
