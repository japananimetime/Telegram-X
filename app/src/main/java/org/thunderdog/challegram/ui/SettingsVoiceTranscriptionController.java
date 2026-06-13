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
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.SpeechProviderConfig;
import org.thunderdog.challegram.telegram.SpeechRecognitionManager;
import org.thunderdog.challegram.telegram.SpeechRecognitionProvider;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.RadioView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.vkryl.core.collection.IntList;

/**
 * Settings controller for voice transcription configuration.
 *
 * Allows users to:
 * - Select active transcription provider
 * - Add/configure custom providers (OpenAI Whisper, Groq, custom HTTP, etc.)
 * - Toggle auto-transcribe
 * - Set preferred language
 */
public class SettingsVoiceTranscriptionController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener {

  public SettingsVoiceTranscriptionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_voiceTranscription;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.VoiceTranscription);
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_transcriptionProvider) {
          SpeechRecognitionProvider provider = (SpeechRecognitionProvider) item.getData();
          if (provider != null) {
            view.setName(provider.getDisplayName());
            SpeechRecognitionProvider active = SpeechRecognitionManager.instance().getActiveProvider();
            boolean isActive = active != null && active.getId().equals(provider.getId());
            view.setData(isActive ? Lang.getString(R.string.TranscriptionProviderActive) : getProviderTypeString(provider.getType()));
            RadioView radioView = view.findRadioView();
            if (radioView != null) {
              radioView.setChecked(isActive, isUpdate);
            }
          }
        } else if (itemId == R.id.btn_autoTranscribe) {
          view.getToggler().setRadioEnabled(SpeechRecognitionManager.instance().isAutoTranscribeEnabled(), isUpdate);
        }
      }
    };

    adapter.setOnLongClickListener(this);

    List<ListItem> items = buildItems();
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @NonNull
  private String getProviderTypeString (@NonNull SpeechRecognitionProvider.ProviderType type) {
    switch (type) {
      case TDLIB:
        return Lang.getString(R.string.TranscriptionTypeTelegram);
      case HTTP_API:
        return Lang.getString(R.string.TranscriptionTypeHttpApi);
      case LOCAL:
        return Lang.getString(R.string.TranscriptionTypeLocal);
      default:
        return "";
    }
  }

  private List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();

    // Info header
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.VoiceTranscriptionInfo));

    // Providers section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TranscriptionProviders));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    // List all providers
    Collection<SpeechRecognitionProvider> providers = SpeechRecognitionManager.instance().getAllProviders();
    boolean first = true;
    for (SpeechRecognitionProvider provider : providers) {
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      first = false;
      items.add(new ListItem(
        ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO,
        R.id.btn_transcriptionProvider,
        0,
        provider.getDisplayName(),
        false
      ).setData(provider));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Add provider button
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_addTranscriptionProvider, R.drawable.baseline_add_24, R.string.TranscriptionAddProvider));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TranscriptionAddProviderInfo));

    // Options section
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Options));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_autoTranscribe, 0, R.string.TranscriptionAutoTranscribe));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TranscriptionAutoTranscribeInfo));

    // Trial info for TDLib provider (non-Premium users)
    if (!tdlib.hasPremium()) {
      SpeechRecognitionManager manager = SpeechRecognitionManager.instance();
      int remaining = manager.getRemainingTrialAttempts();
      int weekly = manager.getWeeklyTrialCount();
      if (weekly > 0) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TranscriptionTrialInfo));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(
          ListItem.TYPE_DESCRIPTION,
          0, 0,
          Lang.plural(R.string.TranscriptionTrialRemaining, remaining, remaining, weekly),
          false
        ));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    }

    return items;
  }

  private void rebuildProviderList () {
    List<ListItem> items = buildItems();
    adapter.setItems(items, true);
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_transcriptionProvider) {
      ListItem item = (ListItem) v.getTag();
      SpeechRecognitionProvider provider = (SpeechRecognitionProvider) item.getData();
      if (provider != null) {
        SpeechRecognitionManager.instance().setActiveProvider(provider.getId());
        rebuildProviderList();
      }
    } else if (viewId == R.id.btn_addTranscriptionProvider) {
      showAddProviderOptions();
    } else if (viewId == R.id.btn_autoTranscribe) {
      boolean newValue = adapter.toggleView(v);
      SpeechRecognitionManager.instance().setAutoTranscribe(newValue);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_transcriptionProvider) {
      ListItem item = (ListItem) v.getTag();
      SpeechRecognitionProvider provider = (SpeechRecognitionProvider) item.getData();
      if (provider != null && !provider.getId().equals(SpeechRecognitionManager.TDLIB_PROVIDER_ID)) {
        showProviderOptions(provider);
        return true;
      }
    }
    return false;
  }

  private void showAddProviderOptions () {
    List<SpeechProviderConfig> templates = SpeechRecognitionManager.instance().getProviderTemplates();

    IntList ids = new IntList(templates.size());
    ArrayList<String> names = new ArrayList<>(templates.size());

    int idCounter = 0;
    for (SpeechProviderConfig template : templates) {
      ids.append(idCounter++);
      names.add(template.getDisplayName());
    }

    showOptions(
      Lang.getString(R.string.TranscriptionAddProvider),
      ids.get(),
      names.toArray(new String[0]),
      null,
      null,
      (itemView, id) -> {
        if (id >= 0 && id < templates.size()) {
          SpeechProviderConfig template = templates.get(id);
          showConfigureProviderDialog(template);
        }
        return true;
      }
    );
  }

  private void showProviderOptions (@NonNull SpeechRecognitionProvider provider) {
    showOptions(
      provider.getDisplayName(),
      new int[] {R.id.btn_edit, R.id.btn_delete},
      new String[] {Lang.getString(R.string.Edit), Lang.getString(R.string.Delete)},
      new int[] {OptionColor.NORMAL, OptionColor.RED},
      new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_delete_24},
      (itemView, id) -> {
        if (id == R.id.btn_edit) {
          SpeechProviderConfig config = SpeechRecognitionManager.instance().getProviderConfig(provider.getId());
          if (config != null) {
            showConfigureProviderDialog(config);
          }
        } else if (id == R.id.btn_delete) {
          showDeleteProviderConfirmation(provider);
        }
        return true;
      }
    );
  }

  private void showDeleteProviderConfirmation (@NonNull SpeechRecognitionProvider provider) {
    showOptions(
      Lang.getString(R.string.TranscriptionDeleteProviderConfirm),
      new int[] {R.id.btn_delete, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_delete) {
          SpeechRecognitionManager.instance().removeProviderConfig(provider.getId());
          rebuildProviderList();
        }
        return true;
      }
    );
  }

  private void showConfigureProviderDialog (@NonNull SpeechProviderConfig template) {
    EditTranscriptionProviderController controller = new EditTranscriptionProviderController(context, tdlib);
    controller.setArguments(new EditTranscriptionProviderController.Args(template, this::rebuildProviderList));
    navigateTo(controller);
  }
}
