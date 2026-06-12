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
import org.thunderdog.challegram.telegram.SavedMessagesListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/**
 * Lists the Saved Messages topics (My Notes, Author Hidden, and per-chat topics).
 * Topics are loaded via LoadSavedMessagesTopics and cached in Tdlib (refreshed
 * live via SavedMessagesListener). Tapping a topic opens the Saved Messages chat
 * scoped to it; long-press offers pin/unpin (ToggleSavedMessagesTopicIsPinned)
 * and delete (DeleteSavedMessagesTopicHistory).
 */
public class SavedMessagesTopicsController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener, SavedMessagesListener {

  private SettingsAdapter adapter;
  private boolean loadedOnce;
  private boolean endReached;
  private boolean loading;

  public SavedMessagesTopicsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.SavedMessagesTopics);
  }

  @Override
  public int getId () {
    return R.id.controller_savedMessagesTopics;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_savedMessagesTopic) {
          TdApi.SavedMessagesTopic topic = (TdApi.SavedMessagesTopic) item.getData();
          view.setData(topic != null ? lastMessagePreview(topic) : "");
        }
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.listeners().subscribeToSavedMessagesUpdates(this);
    buildCells();
    loadMore();
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromSavedMessagesUpdates(this);
  }

  private void loadMore () {
    if (loading || endReached) {
      return;
    }
    loading = true;
    tdlib.send(new TdApi.LoadSavedMessagesTopics(20), (ok, error) -> runOnUiThreadOptional(() -> {
      loading = false;
      loadedOnce = true;
      if (error != null) {
        // 404 means all topics have been loaded.
        endReached = true;
      }
      buildCells();
    }));
  }

  @Override
  public void onSavedMessagesTopicsChanged () {
    runOnUiThreadOptional(this::buildCells);
  }

  private String topicName (TdApi.SavedMessagesTopic topic) {
    if (topic.type == null) {
      return Lang.getString(R.string.SavedMessages);
    }
    switch (topic.type.getConstructor()) {
      case TdApi.SavedMessagesTopicTypeMyNotes.CONSTRUCTOR:
        return Lang.getString(R.string.MyNotes);
      case TdApi.SavedMessagesTopicTypeAuthorHidden.CONSTRUCTOR:
        return Lang.getString(R.string.AuthorHidden);
      case TdApi.SavedMessagesTopicTypeSavedFromChat.CONSTRUCTOR: {
        long chatId = ((TdApi.SavedMessagesTopicTypeSavedFromChat) topic.type).chatId;
        String title = tdlib.chatTitle(chatId);
        return StringUtils.isEmpty(title) ? Lang.getString(R.string.SavedMessages) : title;
      }
    }
    return Lang.getString(R.string.SavedMessages);
  }

  private String lastMessagePreview (TdApi.SavedMessagesTopic topic) {
    if (topic.lastMessage == null) {
      return "";
    }
    ContentPreview preview = ContentPreview.getChatListPreview(tdlib, tdlib.selfChatId(), topic.lastMessage, false);
    return preview != null ? preview.buildText(false).toString() : "";
  }

  private void buildCells () {
    List<TdApi.SavedMessagesTopic> topics = tdlib.getSavedMessagesTopics();
    List<ListItem> items = new ArrayList<>();

    if (topics.isEmpty()) {
      if (loadedOnce && !loading) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SavedMessagesTopicsEmpty));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (TdApi.SavedMessagesTopic topic : topics) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        int icon = topic.isPinned ? R.drawable.deproko_baseline_pin_24 : R.drawable.baseline_bookmark_24;
        ListItem item = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_savedMessagesTopic, icon, topicName(topic), false);
        item.setData(topic);
        item.setLongId(topic.id);
        items.add(item);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      if (!endReached) {
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showMore, 0, R.string.ShowMore));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    }

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_showMore) {
      loadMore();
    } else if (id == R.id.btn_savedMessagesTopic) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.SavedMessagesTopic) {
        openTopic((TdApi.SavedMessagesTopic) item.getData());
      }
    }
  }

  private void openTopic (TdApi.SavedMessagesTopic topic) {
    tdlib.ui().openChat(this, tdlib.selfChatId(), new TdlibUi.ChatOpenParameters()
      .keepStack()
      .messageTopic(new TdApi.MessageTopicSavedMessages(topic.id)));
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.btn_savedMessagesTopic) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.SavedMessagesTopic) {
        showTopicOptions((TdApi.SavedMessagesTopic) item.getData());
        return true;
      }
    }
    return false;
  }

  private void showTopicOptions (TdApi.SavedMessagesTopic topic) {
    showOptions(topicName(topic),
      new int[] {topic.isPinned ? R.id.btn_unpinTopic : R.id.btn_pinTopic, R.id.btn_delete},
      new String[] {
        Lang.getString(topic.isPinned ? R.string.UnpinTopic : R.string.PinTopic),
        Lang.getString(R.string.Delete)
      },
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED},
      new int[] {
        topic.isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24,
        R.drawable.baseline_delete_24
      },
      (itemView, optionId) -> {
        if (optionId == R.id.btn_pinTopic || optionId == R.id.btn_unpinTopic) {
          tdlib.send(new TdApi.ToggleSavedMessagesTopicIsPinned(topic.id, !topic.isPinned), tdlib.typedOkHandler());
        } else if (optionId == R.id.btn_delete) {
          showConfirm(Lang.getString(R.string.DeleteSavedMessagesTopicConfirm), Lang.getString(R.string.Delete), R.drawable.baseline_delete_24, ViewController.OptionColor.RED, () -> {
            tdlib.send(new TdApi.DeleteSavedMessagesTopicHistory(topic.id), tdlib.typedOkHandler());
          });
        }
        return true;
      });
  }
}
