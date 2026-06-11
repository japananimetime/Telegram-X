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
 * File created for forum topics tabs support
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import android.widget.LinearLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import tgx.td.ChatId;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.collection.IntList;

import org.thunderdog.challegram.util.StringList;

public class ForumTopicTabsController extends ViewPagerController<ForumTopicTabsController.Arguments> implements TdlibCache.SupergroupDataChangeListener, ChatListener, Menu, MoreDelegate {

  public static class Arguments {
    public final TdApi.Chat chat;

    public Arguments (TdApi.Chat chat) {
      this.chat = chat;
    }
  }

  private TdApi.Chat chat;
  private long chatId;
  private final List<TdApi.ForumTopic> topics = new ArrayList<>();
  private boolean isLoading;
  private boolean pendingReload; // A reload was requested while a load was already in flight
  private boolean hasMore;
  // Pagination cursor, taken from the last GetForumTopics response
  private int nextOffsetDate;
  private long nextOffsetMessageId;
  private int nextOffsetForumTopicId;
  private boolean isSubscribedToUpdates;
  private LoadingController loadingController;

  public ForumTopicTabsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.chat = args.chat;
    this.chatId = args.chat.id;
  }

  @Override
  public int getId () {
    return R.id.controller_forumTopicTabs;
  }

  @Override
  public long getChatId () {
    return chatId;
  }

  @Override
  public CharSequence getName () {
    return chat != null ? chat.title : "";
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getTitleStyle () {
    return TITLE_STYLE_COMPACT;
  }

  @Override
  protected int getMenuButtonsWidth () {
    // Width for the more button (3 dots)
    return Screen.dp(56f);
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    loadTopics();
  }

  private void loadTopics () {
    if (isLoading) {
      pendingReload = true;
      return;
    }
    isLoading = true;

    if (!isSubscribedToUpdates) {
      tdlib.listeners().subscribeToChatUpdates(chatId, this);
      tdlib.cache().subscribeToSupergroupUpdates(ChatId.toSupergroupId(chatId), this);
      isSubscribedToUpdates = true;
    }

    // First, try to show cached topics immediately for instant display
    java.util.List<TdApi.ForumTopic> cachedTopics = tdlib.getCachedForumTopics(chatId);
    if (cachedTopics != null && !cachedTopics.isEmpty() && topics.isEmpty()) {
      topics.clear();
      topics.addAll(cachedTopics);
      // Rebuild the pager with cached topics immediately
      notifyPagerItemPositionsChanged();
    }

    // Then refresh from network in background
    tdlib.client().send(new TdApi.GetForumTopics(chatId, "", 0, 0L, 0, 100), result -> {
      if (result.getConstructor() == TdApi.ForumTopics.CONSTRUCTOR) {
        TdApi.ForumTopics forumTopics = (TdApi.ForumTopics) result;
        tdlib.ui().post(() -> {
          if (isDestroyed()) return;
          topics.clear();
          for (TdApi.ForumTopic topic : forumTopics.topics) {
            topics.add(topic);
          }
          nextOffsetDate = forumTopics.nextOffsetDate;
          nextOffsetMessageId = forumTopics.nextOffsetMessageId;
          nextOffsetForumTopicId = forumTopics.nextOffsetForumTopicId;
          hasMore = forumTopics.topics.length > 0 && nextOffsetForumTopicId != 0;
          // Update cache
          tdlib.updateForumTopicsCache(chatId, topics);
          isLoading = false;

          // Rebuild the pager with topics
          if (!topics.isEmpty()) {
            notifyPagerItemPositionsChanged();
          }
          continuePendingReload();
        });
      } else {
        tdlib.ui().post(() -> {
          if (isDestroyed()) return;
          isLoading = false;
          UI.showError(result);
          if (topics.isEmpty() && loadingController != null) {
            loadingController.showError();
          }
          continuePendingReload();
        });
      }
    });
  }

  private void continuePendingReload () {
    if (pendingReload) {
      pendingReload = false;
      loadTopics();
    }
  }

  private static int indexOfTopic (List<TdApi.ForumTopic> list, long forumTopicId) {
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).info.forumTopicId == forumTopicId) {
        return i;
      }
    }
    return -1;
  }

  @Override
  protected int getPagerItemCount () {
    return topics.isEmpty() ? 1 : topics.size();
  }

  @Override
  protected long getPagerItemId (int position) {
    if (position < topics.size()) {
      return topics.get(position).info.forumTopicId;
    }
    return position;
  }

  @Override
  protected @Nullable List<ViewPagerTopView.Item> getPagerSectionItems () {
    List<ViewPagerTopView.Item> items = new ArrayList<>();
    if (topics.isEmpty()) {
      // Return a "Loading..." placeholder to ensure tabs are created
      items.add(new ViewPagerTopView.Item(Lang.getString(R.string.LoadingTopics)));
    } else {
      for (TdApi.ForumTopic topic : topics) {
        items.add(new ViewPagerTopView.Item(topic.info.name));
      }
    }
    return items;
  }

  @Override
  protected String[] getPagerSections () {
    // Using getPagerSectionItems instead
    return null;
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    if (topics.isEmpty()) {
      // Show empty loading placeholder
      return createEmptyController(context);
    }

    TdApi.ForumTopic topic = topics.get(position);
    MessagesController controller = new MessagesController(context, tdlib);

    MessagesController.Arguments args = new MessagesController.Arguments(
      tdlib,
      null, // chatList
      chat,
      null, // threadInfo
      new TdApi.MessageTopicForum(topic.info.forumTopicId),
      null // filter
    );
    args.setForumTopic(topic);
    controller.setArguments(args);

    return controller;
  }

  private ViewController<?> createEmptyController (Context context) {
    // Return a loading controller with centered progress spinner while topics load
    loadingController = new LoadingController(context, tdlib, () -> {
      if (!isDestroyed()) {
        loadTopics();
      }
    });
    return loadingController;
  }

  @Override
  public View getViewForApplyingOffsets () {
    ViewController<?> c = getCachedControllerForPosition(getCurrentPagerItemPosition());
    return c != null ? c.getViewForApplyingOffsets() : null;
  }

  @Override
  public void onPageScrolled (int position, int actualPosition, float positionOffset, int positionOffsetPixels) {
    // Load more topics when scrolling near the end
    if (hasMore && !isLoading && actualPosition >= topics.size() - 3) {
      loadMoreTopics();
    }
  }

  private void loadMoreTopics () {
    if (isLoading || !hasMore || topics.isEmpty()) return;
    isLoading = true;

    tdlib.client().send(new TdApi.GetForumTopics(chatId, "", nextOffsetDate, nextOffsetMessageId, nextOffsetForumTopicId, 50), result -> {
      if (result.getConstructor() == TdApi.ForumTopics.CONSTRUCTOR) {
        TdApi.ForumTopics forumTopics = (TdApi.ForumTopics) result;
        tdlib.ui().post(() -> {
          if (isDestroyed()) return;
          // Dedup by topic id: duplicate pager item ids corrupt the pager's id->position map
          int added = 0;
          for (TdApi.ForumTopic topic : forumTopics.topics) {
            if (indexOfTopic(topics, topic.info.forumTopicId) == -1) {
              topics.add(topic);
              added++;
            }
          }
          nextOffsetDate = forumTopics.nextOffsetDate;
          nextOffsetMessageId = forumTopics.nextOffsetMessageId;
          nextOffsetForumTopicId = forumTopics.nextOffsetForumTopicId;
          hasMore = forumTopics.topics.length > 0 && nextOffsetForumTopicId != 0;
          tdlib.updateForumTopicsCache(chatId, topics);
          isLoading = false;

          // Update the pager
          if (added > 0) {
            notifyPagerItemPositionsChanged();
          }
          continuePendingReload();
        });
      } else {
        tdlib.ui().post(() -> {
          if (isDestroyed()) return;
          isLoading = false;
          continuePendingReload();
        });
      }
    });
  }

  // ChatListener (ForumTopicInfoListener) implementation
  @Override
  public void onForumTopicInfoChanged (TdApi.ForumTopicInfo info) {
    if (info.chatId != chatId) return;
    tdlib.ui().post(() -> {
      if (isDestroyed()) return;
      int index = indexOfTopic(topics, info.forumTopicId);
      if (index != -1) {
        topics.get(index).info = info;
        notifyPagerItemPositionsChanged();
      } else if (!topics.isEmpty()) {
        // Unknown topic - probably created remotely; refresh the set
        loadTopics();
      }
    });
  }

  @Override
  public void onForumTopicUpdated (long chatId, long forumTopicId, boolean isPinned, long lastReadInboxMessageId, long lastReadOutboxMessageId, int unreadMentionCount, int unreadReactionCount, TdApi.ChatNotificationSettings notificationSettings, TdApi.DraftMessage draftMessage) {
    if (chatId != this.chatId) return;
    tdlib.ui().post(() -> {
      if (isDestroyed()) return;
      int index = indexOfTopic(topics, forumTopicId);
      if (index == -1) return;
      // Tabs only render names; keep the local state fresh for the menu actions
      TdApi.ForumTopic topic = topics.get(index);
      topic.isPinned = isPinned;
      topic.lastReadInboxMessageId = lastReadInboxMessageId;
      topic.lastReadOutboxMessageId = lastReadOutboxMessageId;
      topic.unreadMentionCount = unreadMentionCount;
      topic.unreadReactionCount = unreadReactionCount;
      if (notificationSettings != null) {
        topic.notificationSettings = notificationSettings;
      }
      topic.draftMessage = draftMessage;
    });
  }

  // TdlibCache.SupergroupDataChangeListener implementation
  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    tdlib.ui().post(() -> {
      if (ChatId.toSupergroupId(chatId) == supergroup.id) {
        // Check if forum mode was disabled externally or tabs layout changed
        if (!supergroup.isForum && chat != null) {
          // Forum mode was disabled - navigate to regular chat view
          navigateBack();
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              tdlib.ui().openChat(this, chat.id, new TdlibUi.ChatOpenParameters().keepStack());
            }
          });
        } else if (supergroup.isForum && !supergroup.hasForumTabs && chat != null) {
          // Tabs layout was disabled - switch to list controller
          navigateBack();
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              tdlib.ui().openChat(this, chat.id, new TdlibUi.ChatOpenParameters().keepStack());
            }
          });
        }
      }
    });
  }

  @Override
  public void onSupergroupFullUpdated (long supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    // Not used
  }

  // Menu implementation
  @Override
  public int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addMoreButton(menu, this);
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }

  @Nullable
  private TdApi.ForumTopic getCurrentTopic () {
    int position = getCurrentPagerItemPosition();
    if (position >= 0 && position < topics.size()) {
      return topics.get(position);
    }
    return null;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    } else if (id == R.id.menu_btn_more) {
      TdApi.ForumTopic topic = getCurrentTopic();

      IntList ids = new IntList(8);
      IntList icons = new IntList(8);
      StringList strings = new StringList(8);

      // Search option
      ids.append(R.id.menu_btn_search);
      icons.append(R.drawable.baseline_search_24);
      strings.append(R.string.Search);

      boolean canManage = canManageTopics();

      // Topic-specific options (only if we have a valid topic)
      if (topic != null) {
        // Notifications (always available for members)
        boolean isMuted = topic.notificationSettings != null && topic.notificationSettings.muteFor > 0;
        ids.append(R.id.btn_notifications);
        icons.append(isMuted ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24);
        strings.append(isMuted ? R.string.Unmute : R.string.Mute);

        // Admin-only actions
        if (canManage) {
          // Close/Reopen
          if (topic.info.isClosed) {
            ids.append(R.id.btn_reopenTopic);
            icons.append(R.drawable.baseline_lock_24);
            strings.append(R.string.ReopenTopic);
          } else {
            ids.append(R.id.btn_closeTopic);
            icons.append(R.drawable.baseline_lock_24);
            strings.append(R.string.CloseTopic);
          }

          // Pin/Unpin
          if (topic.isPinned) {
            ids.append(R.id.btn_unpinTopic);
            icons.append(R.drawable.deproko_baseline_pin_undo_24);
            strings.append(R.string.UnpinTopic);
          } else {
            ids.append(R.id.btn_pinTopic);
            icons.append(R.drawable.deproko_baseline_pin_24);
            strings.append(R.string.PinTopic);
          }

          // Edit
          ids.append(R.id.btn_editTopic);
          icons.append(R.drawable.baseline_edit_24);
          strings.append(R.string.EditTopic);
        }
      }

      // Create topic option (only if user has permission)
      if (canCreateTopics()) {
        ids.append(R.id.btn_createTopic);
        icons.append(R.drawable.baseline_add_24);
        strings.append(R.string.NewTopic);
      }

      // Group info option (admin only)
      if (canManage) {
        ids.append(R.id.btn_chatSettings);
        icons.append(R.drawable.baseline_info_24);
        strings.append(R.string.TabInfo);
      }

      // View as topics list option (switch to ForumTopicsController)
      ids.append(R.id.btn_viewAsTopics);
      icons.append(R.drawable.baseline_format_list_bulleted_type_24);
      strings.append(R.string.ViewAsTopics);

      // View as chat option (always available)
      ids.append(R.id.btn_viewAsChat);
      icons.append(R.drawable.baseline_chat_bubble_24);
      strings.append(R.string.ViewAsChat);

      showMore(ids.get(), strings.get(), icons.get(), 0);
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    TdApi.ForumTopic topic = getCurrentTopic();

    if (id == R.id.menu_btn_search) {
      // Switch to ForumTopicsController which has full search functionality
      ForumTopicsController listController = new ForumTopicsController(context, tdlib);
      listController.setArguments(new ForumTopicsController.Arguments(chat));
      navigateTo(listController);
    } else if (id == R.id.btn_createTopic) {
      showCreateTopicDialog();
    } else if (id == R.id.btn_chatSettings) {
      // Open chat profile/settings
      ProfileController profileController = new ProfileController(context, tdlib);
      profileController.setArguments(new ProfileController.Args(chat, null, false));
      navigateTo(profileController);
    } else if (id == R.id.btn_viewAsTopics) {
      // Save preference for topics list view
      tdlib.settings().setForumViewPreference(chatId, TdlibSettingsManager.FORUM_VIEW_TOPICS);
      // Switch to topics list view (ForumTopicsController)
      ForumTopicsController listController = new ForumTopicsController(context, tdlib);
      listController.setArguments(new ForumTopicsController.Arguments(chat));
      // Navigate and remove this controller from stack
      listController.addOneShotFocusListener(() -> {
        listController.destroyStackItemAt(listController.stackSize() - 2);
      });
      navigateTo(listController);
    } else if (id == R.id.btn_viewAsChat) {
      // Remember the choice, so the chat keeps opening in unified view
      // even when the forum has tabs enabled
      tdlib.settings().setForumViewPreference(chatId, TdlibSettingsManager.FORUM_VIEW_CHAT);
      // Set viewAsTopics to false and open as unified chat
      tdlib.client().send(new TdApi.ToggleChatViewAsTopics(chatId, false), result -> {
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.post(() -> UI.showError(result));
          return;
        }
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          tdlib.ui().post(() -> {
            // Create the messages controller and replace current controller
            MessagesController c = new MessagesController(context, tdlib);
            c.setArguments(new MessagesController.Arguments(tdlib, null, chat, null, null, null));

            // Navigate to messages controller and remove this controller from stack after navigation
            c.addOneShotFocusListener(() -> {
              // Destroy the ForumTopicTabsController from the stack (it's now at position stackSize - 2)
              c.destroyStackItemAt(c.stackSize() - 2);
            });
            navigateTo(c);
          });
        }
      });
    } else if (topic != null) {
      if (id == R.id.btn_notifications) {
        showTopicMuteOptions(topic);
      } else if (id == R.id.btn_closeTopic) {
        toggleTopicClosed(topic, true);
      } else if (id == R.id.btn_reopenTopic) {
        toggleTopicClosed(topic, false);
      } else if (id == R.id.btn_pinTopic) {
        toggleTopicPinned(topic, true);
      } else if (id == R.id.btn_unpinTopic) {
        toggleTopicPinned(topic, false);
      } else if (id == R.id.btn_editTopic) {
        editTopic(topic);
      }
    }
  }

  private void toggleTopicPinned (TdApi.ForumTopic topic, boolean pinned) {
    tdlib.client().send(new TdApi.ToggleForumTopicIsPinned(topic.info.chatId, topic.info.forumTopicId, pinned), result -> {
      if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
        // Update local state on the UI thread; the authoritative state
        // arrives via updateForumTopic
        UI.post(() -> topic.isPinned = pinned);
      } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.post(() -> UI.showError(result));
      }
    });
  }

  private void toggleTopicClosed (TdApi.ForumTopic topic, boolean closed) {
    tdlib.client().send(new TdApi.ToggleForumTopicIsClosed(topic.info.chatId, topic.info.forumTopicId, closed), result -> {
      if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
        // Update local state on the UI thread; the authoritative state
        // arrives via updateForumTopicInfo
        UI.post(() -> topic.info.isClosed = closed);
      } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.post(() -> UI.showError(result));
      }
    });
  }

  private void editTopic (TdApi.ForumTopic topic) {
    openInputAlert(
      Lang.getString(R.string.EditTopic),
      Lang.getString(R.string.TopicNameHint),
      R.string.Done,
      R.string.Cancel,
      topic.info.name,
      (inputView, result) -> {
        String newName = result.trim();
        if (newName.isEmpty()) {
          inputView.setInErrorState(true);
          return false;
        }
        if (newName.length() > 128) {
          inputView.setInErrorState(true);
          return false;
        }
        // Edit the topic with new name, keep existing icon
        tdlib.client().send(new TdApi.EditForumTopic(
          topic.info.chatId,
          topic.info.forumTopicId,
          newName,
          false, // editIconCustomEmoji
          0 // iconCustomEmojiId (not changing)
        ), result1 -> {
          UI.post(() -> {
            if (result1.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
              // Update local topic name and refresh tabs
              topic.info.name = newName;
              notifyPagerItemPositionsChanged();
            } else if (result1.getConstructor() == TdApi.Error.CONSTRUCTOR) {
              UI.showError(result1);
            }
          });
        });
        return true;
      },
      true
    );
  }

  private void showTopicMuteOptions (TdApi.ForumTopic topic) {
    boolean isMuted = topic.notificationSettings != null && topic.notificationSettings.muteFor > 0;

    IntList ids = new IntList(5);
    IntList icons = new IntList(5);
    ArrayList<String> strings = new ArrayList<>();

    if (isMuted) {
      // Currently muted - show unmute option
      ids.append(R.id.btn_menu_enable);
      icons.append(R.drawable.baseline_notifications_24);
      strings.add(Lang.getString(R.string.EnableNotifications));
    } else {
      // Currently unmuted - show mute options
      ids.append(R.id.btn_menu_1hour);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXHours, 1));

      ids.append(R.id.btn_menu_8hours);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXHours, 8));

      ids.append(R.id.btn_menu_2days);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXDays, 2));

      ids.append(R.id.btn_menu_disable);
      icons.append(R.drawable.baseline_notifications_off_24);
      strings.add(Lang.getString(R.string.MuteForever));
    }

    showOptions(topic.info.name, ids.get(), strings.toArray(new String[0]), null, icons.get(), (itemView, itemId) -> {
      int muteFor = 0;
      if (itemId == R.id.btn_menu_enable) {
        muteFor = 0; // Unmute
      } else if (itemId == R.id.btn_menu_1hour) {
        muteFor = (int) java.util.concurrent.TimeUnit.HOURS.toSeconds(1);
      } else if (itemId == R.id.btn_menu_8hours) {
        muteFor = (int) java.util.concurrent.TimeUnit.HOURS.toSeconds(8);
      } else if (itemId == R.id.btn_menu_2days) {
        muteFor = (int) java.util.concurrent.TimeUnit.DAYS.toSeconds(2);
      } else if (itemId == R.id.btn_menu_disable) {
        muteFor = Integer.MAX_VALUE; // Mute forever
      }

      setForumTopicMuteFor(topic, muteFor);
      return true;
    });
  }

  private void setForumTopicMuteFor (TdApi.ForumTopic topic, int muteFor) {
    TdApi.ChatNotificationSettings settings = new TdApi.ChatNotificationSettings();
    settings.useDefaultMuteFor = (muteFor == 0);
    settings.muteFor = muteFor;
    settings.useDefaultSound = true;
    settings.useDefaultShowPreview = true;
    settings.useDefaultMuteStories = true;
    settings.useDefaultStorySound = true;
    settings.useDefaultDisablePinnedMessageNotifications = true;
    settings.useDefaultDisableMentionNotifications = true;

    tdlib.client().send(new TdApi.SetForumTopicNotificationSettings(
      topic.info.chatId,
      topic.info.forumTopicId,
      settings
    ), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          // Update local state
          if (topic.notificationSettings == null) {
            topic.notificationSettings = new TdApi.ChatNotificationSettings();
          }
          topic.notificationSettings.muteFor = muteFor;
          topic.notificationSettings.useDefaultMuteFor = (muteFor == 0);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
        }
      });
    });
  }

  private void showCreateTopicDialog () {
    openInputAlert(
      Lang.getString(R.string.NewTopic),
      Lang.getString(R.string.TopicNameHint),
      R.string.Done,
      R.string.Cancel,
      null,
      (inputView, result) -> {
        String name = result.trim();
        if (name.isEmpty()) {
          inputView.setInErrorState(true);
          return false;
        }
        if (name.length() > 128) {
          inputView.setInErrorState(true);
          return false;
        }
        createTopic(name);
        return true;
      },
      true
    );
  }

  private void createTopic (String name) {
    // Standard topic colors from Telegram
    int[] topicColors = {
      0x6FB9F0, // Blue
      0xFFD67E, // Yellow
      0xCB86DB, // Purple
      0x8EEE98, // Green
      0xFF93B2, // Pink
      0xFB6F5F  // Red
    };
    // Pick a random color
    int color = topicColors[(int) (Math.random() * topicColors.length)];

    TdApi.ForumTopicIcon icon = new TdApi.ForumTopicIcon(color, 0);

    tdlib.client().send(new TdApi.CreateForumTopic(chatId, name, false, icon), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.ForumTopicInfo.CONSTRUCTOR) {
          // Reload topics to show the new one
          loadTopics();
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
        }
      });
    });
  }

  @Override
  public void destroy () {
    super.destroy();
    if (isSubscribedToUpdates) {
      tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
      tdlib.cache().unsubscribeFromSupergroupUpdates(ChatId.toSupergroupId(chatId), this);
      isSubscribedToUpdates = false;
    }
    loadingController = null;
  }

  // Permission checks for topic actions
  private boolean canCreateTopics () {
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    if (status == null) return false;

    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canManageTopics;
      case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
        // Check chat-level permissions
        return chat != null && chat.permissions != null && chat.permissions.canCreateTopics;
      default:
        return false;
    }
  }

  private boolean canManageTopics () {
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    if (status == null) return false;

    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canManageTopics;
      default:
        return false;
    }
  }

  // Loading placeholder controller shown while topics are being loaded
  private static class LoadingController extends ViewController<Void> {
    private final Runnable onRetry;
    private ProgressComponentView progressView;
    private ListInfoView infoView;
    private boolean inErrorState;

    public LoadingController (Context context, Tdlib tdlib, Runnable onRetry) {
      super(context, tdlib);
      this.onRetry = onRetry;
    }

    @Override
    public int getId () {
      return 0;
    }

    @Override
    protected View onCreateView (Context context) {
      // Create a centered progress spinner
      FrameLayoutFix container = new FrameLayoutFix(context);
      container.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      ));

      progressView = new ProgressComponentView(context);
      progressView.initLarge(1f);
      FrameLayoutFix.LayoutParams lp = new FrameLayoutFix.LayoutParams(
        Screen.dp(48f),
        Screen.dp(48f),
        Gravity.CENTER
      );
      progressView.setLayoutParams(lp);
      container.addView(progressView);

      infoView = new ListInfoView(context);
      infoView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      infoView.setVisibility(View.GONE);
      container.addView(infoView);

      container.setOnClickListener(v -> {
        if (inErrorState && onRetry != null) {
          showProgress();
          onRetry.run();
        }
      });

      if (inErrorState) {
        applyErrorState();
      }
      return container;
    }

    /**
     * Replaces the endless spinner with an error message and tap-to-retry.
     */
    public void showError () {
      inErrorState = true;
      if (progressView != null) {
        applyErrorState();
      }
    }

    private void applyErrorState () {
      progressView.setVisibility(View.GONE);
      infoView.setVisibility(View.VISIBLE);
      infoView.showInfo(Lang.getString(R.string.LoadingTopicsError));
    }

    private void showProgress () {
      inErrorState = false;
      if (progressView != null) {
        progressView.setVisibility(View.VISIBLE);
        infoView.setVisibility(View.GONE);
      }
    }
  }
}
