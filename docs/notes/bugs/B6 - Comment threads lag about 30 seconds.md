---
mantis: 
area: General / messaging (comment threads)
severity: P1
status: fixed, device-tested OK 2026-09-06 (build 0.28.11.1785)
build: all-features-combined e33134ba
device: user's phone
---
# Comment threads lag about 30 seconds

## Symptom
While chatting in a channel post's comments (message thread) **without being in the discussion chat itself**, everything arrives with roughly 30 s delay: other people's new messages, the user's own sent messages, and especially mentions that land in the "Replies" chat. Reported 2026-09-06.

## Facts (2026-09-06)
The user comments **through the channel without being a member** of the discussion group. As a member everything is instant. That matches TDLib: the server pushes supergroup updates only to members; TDLib runs one `getChannelDifference` when a non-member opens the chat and nothing periodic afterwards.

## Where in the code
- Thread screen: `MessagesController` with `ThreadInfo` (`data/ThreadInfo.java`); `MessagesManager.openChat` → `tdlib.openChat(chat.id)` (discussion chat) — unchanged vs upstream.
- Incoming filter: `MessagesManager.updateNewMessage` → `Td.matchesTopic(message.topicId, viewingTopic)` (`vkryl/td/.../TdUtils.kt` ≈2055): a message with `topicId == null` is dropped while viewing any topic/thread. The fork widened the viewing topic to forum topics; thread behaviour is as upstream.
- TDLib side (`MessagesManager.cpp` upstream master): `open_dialog` runs one `get_channel_difference`; updates for a channel are processed only `if (d->was_opened || is_member || sponsored)`. There is no periodic re-fetch for opened non-member channels — that alone would explain a lag that ends only when something else (e.g. `GetMessageThread` refresh on reply-count change) triggers a fetch.

## Hypotheses
1. Non-member discussion group + no push from server → new comments only appear when the thread info is refreshed (interaction-info update ≈ 30 s cadence). Mitigation: while a thread is on screen and the user is not a member, poll `GetMessageThreadHistory(chatId, root, 0, 0, N)` every few seconds (TDLib emits `updateNewMessage` for messages newer than the last known one) or call `GetMessageThread` periodically.
2. Pending own messages carry `topicId == null` and are filtered out by `matchesTopic` until `updateMessageSendSucceeded` delivers the server copy — would show as "appear late", not "sending". Check with `TAG_MESSAGES_LOADER` logs.

## Fix
Branch `fix/giveaway-typing-topics`, `MessagesController`: while a comment thread is focused and `tdlib.chatStatus(discussionChatId)` is not a member status, poll `GetMessageThreadHistory(chatId, root, 0, 0, 5)` every 4 s (`scheduleThreadRefresh` / `cancelThreadRefresh` on focus/blur/destroy). TDLib's `need_channel_difference_to_add_message` sees a newer message in the result and schedules its own channel difference, which delivers the normal `updateNewMessage` stream, so nothing is injected into the UI by hand. Members are unaffected.

## Verification
Not device-tested. Test as a non-member: post from another account, expect the comment within ~5 s; own replies should stop lagging too. Watch data usage: one small request every 4 s only while a non-member thread is on screen.
