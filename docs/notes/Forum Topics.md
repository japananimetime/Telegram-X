# Forum Topics

The fork's forum (topics) support for supergroups. Both layouts exist: **list** (`ForumTopicsController`) and **tabs** (`ForumTopicTabsController`, used when `supergroup.hasForumTabs`). Status per `TASKS.md`: feature-complete, several audit rounds of fixes; remaining open items at the bottom. Original branch `forum-topics-implementation`, now integrated in `all-features-combined`.

## Files (line counts on afc)
| File | Lines | Role |
|---|---|---|
| `ui/ForumTopicsController.java` | 2289 | topic list screen: load/paginate, search (topics + messages, topic filter FAB), create/edit/delete/pin/close/mute, pinned reorder, live updates |
| `ui/ForumTopicTabsController.java` | 735 | tabs layout: one `MessagesController` per topic under a tab strip, 3-dots menu with the same topic actions, "View as chat" |
| `ui/ForumTopicView.java` | 832 | one list row: icon (custom emoji or colored circle), name, last message / draft, time, unread/mention/reaction counters, mute & lock icons; also flat message-search rows |
| `util/TopicIconModifier.java` | 234 | draws topic icons in option dialogs |
| `telegram/ForumTopicInfoListener.java` | | `onForumTopicInfoChanged`, `onForumTopicUpdated(...)`, `onForumTopicFullyUpdated(chatId, topic)` |
| `telegram/Tdlib.java` | | cache + helpers: `forumTopicInfo`, `forumTopic`, `isForumTopicMuted`, `isForum`, `forumUnreadTopicCount`, `fetchForumUnreadTopicCount`, `updateForumTopicUnreadCount`, `removeCachedForumTopic`, `getCachedForumTopics`, `updateForumTopicsCache`, `forumTopicNeedsMuteIcon` (≈ lines 2646–3911) |
| `telegram/TdlibUi.java` ≈2181–2216 | | **entry point**: opening a forum chat decides list vs tabs vs plain chat from `chat.viewAsTopics`, `supergroup.hasForumTabs` and the per-chat preference `TdlibSettingsManager` `FORUM_VIEW_TABS=1 / FORUM_VIEW_TOPICS=2` |
| `ui/MessagesController.java` | 13.6k | topic-scoped chat: fields `messageTopicId` (`TdApi.MessageTopic`) and `forumTopic` (≈2851), `isTopicClosedForUser()` (≈7596) gates the input, `onForumTopicUpdated` (≈11580), "View as topics" / "View forum" menu items |
| `telegram/MessageListManager.java` | | filters loaded messages by `TdApi.MessageTopic` via `Td.matchesTopic` |
| `component/chat/{ChatHeaderView,MessagesLoader,MessagesSearchManager,InputView}.java` | | header shows "Topic · Chat", loader uses `GetForumTopicHistory`, search scoped to topic, typing actions per topic |
| `telegram/TdlibNotificationHelper.java` | | per-topic notification ids (`getNotificationIdForTopicView`), "Chat > Topic" titles, client-side mute filter |
| `widget/BetterChatView`, `VerticalChatView`, `ChatsController`, `data/TGChat` | | unread-topic count badge for forum chats in the chat list |
| `ShareController.java` ≈2087 | | topic picker when sharing into a forum (silently targets General on dismiss — known gap) |

## Data flow
1. `TdlibUi.openChat` → forum → `ForumTopicsController` (or tabs). `loadTopics()` calls `GetForumTopics(chatId, query="", offsets, limit=100)`, caches via `tdlib.updateForumTopicsCache`, `loadMoreTopics()` paginates while a full page came back.
2. Live updates (also mirrored into `Tdlib.forumTopicsCache` since 2026-09-06 so the next list open is fresh): `updateForumTopicInfo` → `onForumTopicInfoChanged`; `updateForumTopic` → `onForumTopicUpdated` (read positions, pin, mute, draft; **no unreadCount**), so partial reads trigger an authoritative `GetForumTopic` and `onForumTopicFullyUpdated`. New messages: `onNewMessage` → `applyNewTopicMessage` (unread++ only when `lastMessage` advances); unknown topic → `fetchAndInsertTopic` (single `GetForumTopic`, not a full reload).
3. Opening a topic: `openTopic` → `MessagesController` with `messageTopicId = MessageTopicForum(id)`; history via `GetForumTopicHistory`; opens at first unread. Send/typing carry the topic. Closed topics disable input unless the user can manage topics.
4. Sorting: `resortTopicList` by pinned then TDLib `order` (draft-aware). Pinned reorder: `startPinnedReorder` → `sendPinnedTopicsOrder` (`SetPinnedForumTopics`).

## TDLib functions in play
`GetForumTopics`, `GetForumTopic`, `GetForumTopicHistory`, `CreateForumTopic`, `EditForumTopic`, `DeleteForumTopic`, `ToggleForumTopicIsPinned`, `SetPinnedForumTopics`, `ToggleForumTopicIsClosed`, `ToggleGeneralForumTopicIsHidden`, `GetForumTopicLink`, `SetForumTopicNotificationSettings`, `GetForumTopicDefaultIcons`, `ToggleSupergroupIsForum` (+ `hasForumTabs`), `ToggleChatViewAsTopics`, `SearchChatMessages` with a `MessageTopic`.

## Known open items (2026-06 audits, still unresolved)
- `resortAndRefresh` uses `notifyDataSetChanged()` → flicker on every update; wants DiffUtil / range notifies (`ForumTopicsController` ≈1626).
- Create-topic dialog has no icon/color picker (random color, name only); edit-icon picker is a text list of 12 entries, no custom emoji, no color change (~`showTopicIconPicker`).
- Per-topic notification settings beyond mute presets (custom sound/preview/mute-until) absent.
- Bot DMs with topics (`UserTypeBot.hasTopics`) open flat: `isForum()` is supergroup-only.
- Server-side topic name search never used (`GetForumTopics` query always `""`), so forums with >100 topics get incomplete client-side search.
- `ShareController` topic picker: General on dismissal, 100-topic cap, plain rows.
- Context menu lacks "mark as read" and "delete from tabs".

## Things that were bugs before (regression watch)
Double-counted unread (`unreadCount++` on every message), wrong pagination terminator (`nextOffset != 0`), full 100-topic reload on unknown topic, draft shown over a newer message, `onForumTopicFullyUpdated` never dispatched, shared nested `lastMessage` refs in `copyForumTopics`, tabs always shown, "Topic created" preview for non-forum service messages (switch fallthrough), new message landing in the wrong tab, muted topics still notifying, closed topic input enabled, `hasForumTabs` forums opening the list view. If a report smells like one of these, start at the corresponding fix commit (`987fe1fd9`, `ee5be0fab`) with `git log -S`.

## Bugs filed 2026-09-06
[[bugs/B3 - Topic with many messages shows no messages]] (never-read topic opened from message id 1) and [[bugs/B4 - Forum caching feels wrong]]. Note for both: `MessagesLoader.processMessages` still uses the *chat-level* `lastReadInboxMessageId` / `unreadCount` for the unread divider in topic mode.

## Debug tips
- `TdApi.Message.topicId` is a `MessageTopic` (forum → `MessageTopicForum.forumTopicId`); the General topic id is 1. `messageThreadId` is the legacy name in listener signatures.
- Topic vs chat mute is layered: `isForumTopicMuted` honors `useDefaultMuteFor` → chat setting.
- View preference is per chat per account: `TdlibSettingsManager` key prefix `forum_view_`.
- Reproduce with a forum you control: create topics, close one, hide General, mute one, then check each row state and the unread badge in the chat list.
