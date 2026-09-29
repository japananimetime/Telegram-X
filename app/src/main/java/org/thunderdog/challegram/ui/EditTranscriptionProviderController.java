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
 *
 * File created on 17/01/2026
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.SpeechProviderConfig;
import org.thunderdog.challegram.telegram.SpeechRecognitionManager;
import org.thunderdog.challegram.telegram.SpeechRecognitionProvider;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

/**
 * Controller for editing/adding a transcription provider configuration.
 */
public class EditTranscriptionProviderController extends EditBaseController<EditTranscriptionProviderController.Args> implements
  SettingsAdapter.TextChangeListener {

  public static class Args {
    public final SpeechProviderConfig template;
    public final Runnable onComplete;

    public Args (@NonNull SpeechProviderConfig template, @Nullable Runnable onComplete) {
      this.template = template;
      this.onComplete = onComplete;
    }
  }

  public EditTranscriptionProviderController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private SpeechProviderConfig config;
  private Runnable onComplete;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    // Create a copy for editing
    try {
      this.config = SpeechProviderConfig.fromJson(args.template.toJson());
    } catch (Exception e) {
      this.config = args.template;
    }
    this.onComplete = args.onComplete;

    // Generate unique ID for new providers if needed
    SpeechProviderConfig existingConfig = SpeechRecognitionManager.instance().getProviderConfig(config.getId());
    if (existingConfig != null && !config.equals(existingConfig)) {
      config.setId(config.getId() + "_" + System.currentTimeMillis());
    }
  }

  @Override
  public int getId () {
    return R.id.controller_editTranscriptionProvider;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.TranscriptionConfigureProvider);
  }

  private SettingsAdapter adapter;

  @Override
  protected int getRecyclerBackgroundColorId () {
    return ColorId.background;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        final int itemId = item.getId();
        if (itemId == R.id.input_providerName) {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        } else if (itemId == R.id.input_apiEndpoint) {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        } else if (itemId == R.id.input_apiKey) {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        }
      }
    };
    adapter.setTextChangeListener(this);

    // Build items list
    ListItem nameItem = new ListItem(ListItem.TYPE_EDITTEXT, R.id.input_providerName, 0, R.string.TranscriptionProviderName)
      .setStringValue(config.getDisplayName());

    if (config.getType() == SpeechRecognitionProvider.ProviderType.HTTP_API) {
      ListItem endpointItem = new ListItem(ListItem.TYPE_EDITTEXT, R.id.input_apiEndpoint, 0, R.string.TranscriptionApiEndpoint)
        .setStringValue(config.getApiEndpoint());

      ListItem apiKeyItem = new ListItem(ListItem.TYPE_EDITTEXT, R.id.input_apiKey, 0, R.string.TranscriptionApiKey)
        .setStringValue(config.getApiKey());

      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        nameItem,
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        endpointItem,
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        apiKeyItem,
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TranscriptionEnterApiKey)
      }, false);
    } else {
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        nameItem,
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
      }, false);
    }

    recyclerView.setAdapter(adapter);

    setDoneVisible(true);
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v) {
    String text = v.getText().toString();
    if (id == R.id.input_providerName) {
      config.setDisplayName(text);
    } else if (id == R.id.input_apiEndpoint) {
      config.setApiEndpoint(text);
    } else if (id == R.id.input_apiKey) {
      config.setApiKey(text);
    }
    checkDone();
  }

  private void checkDone () {
    boolean canSave = !StringUtils.isEmpty(config.getDisplayName());
    if (config.getType() == SpeechRecognitionProvider.ProviderType.HTTP_API) {
      canSave = canSave && !StringUtils.isEmpty(config.getApiEndpoint());
    }
    setDoneVisible(canSave);
  }

  @Override
  protected boolean onDoneClick () {
    // Validate
    String name = config.getDisplayName();
    if (StringUtils.isEmpty(name)) {
      UI.showToast(R.string.ErrorFieldEmpty, Toast.LENGTH_SHORT);
      return false;
    }

    if (config.getType() == SpeechRecognitionProvider.ProviderType.HTTP_API) {
      String endpoint = config.getApiEndpoint();
      if (StringUtils.isEmpty(endpoint)) {
        UI.showToast(R.string.ErrorFieldEmpty, Toast.LENGTH_SHORT);
        return false;
      }
    }

    // Save provider
    SpeechRecognitionProvider provider = SpeechRecognitionManager.instance().addProviderConfig(config);
    if (provider == null) {
      UI.showToast(R.string.Error, Toast.LENGTH_SHORT);
      return false;
    }

    UI.showToast(R.string.TranscriptionProviderAdded, Toast.LENGTH_SHORT);

    if (onComplete != null) {
      onComplete.run();
    }

    onSaveCompleted();
    return true;
  }
}
