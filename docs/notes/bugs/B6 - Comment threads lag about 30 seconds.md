---
mantis: 
area: General / messaging (comment threads)
severity: P1
status: open — needs facts
build: all-features-combined e33134ba
device: user's phone
---
# Comment threads lag about 30 seconds

## Symptom
While chatting in a channel post's comments (message thread) **without being in the discussion chat itself**, everything arrives with roughly 30 s delay: other people's new messages, the user's own sent messages, and especially mentions that land in the "Replies" chat. Reported 2026-09-06.

## Facts needed
- Is the user a **member** of the discussion group, or only commenting through the channel? (Server pushes channel updates only to members; for non-members TDLib fetches on open and otherwise relies on explicit history requests.)
- Does the same lag exist in the official app on the same account/network? (Separates TDLib/server behaviour from fork code.)
- Own messages: do they show as "sending" (clock) for 30 s, or appear only after 30 s?

## Where in the code
- Thread screen: `MessagesController` with `ThreadInfo` (`data/ThreadInfo.java`); `MessagesManager.openChat` → `tdlib.openChat(chat.id)` (discussion chat) — unchanged vs upstream.
- Incoming filter: `MessagesManager.updateNewMessage` → `Td.matchesTopic(message.topicId, viewingTopic)` (`vkryl/td/.../TdUtils.kt` ≈2055): a message with `topicId == null` is dropped while viewing any topic/thread. The fork widened the viewing topic to forum topics; thread behaviour is as upstream.
- TDLib side (`MessagesManager.cpp` upstream master): `open_dialog` runs one `get_channel_difference`; updates for a channel are processed only `if (d->was_opened || is_member || sponsored)`. There is no periodic re-fetch for opened non-member channels — that alone would explain a lag that ends only when something else (e.g. `GetMessageThread` refresh on reply-count change) triggers a fetch.

## Hypotheses
1. Non-member discussion group + no push from server → new comments only appear when the thread info is refreshed (interaction-info update ≈ 30 s cadence). Mitigation: while a thread is on screen and the user is not a member, poll `GetMessageThreadHistory(chatId, root, 0, 0, N)` every few seconds (TDLib emits `updateNewMessage` for messages newer than the last known one) or call `GetMessageThread` periodically.
2. Pending own messages carry `topicId == null` and are filtered out by `matchesTopic` until `updateMessageSendSucceeded` delivers the server copy — would show as "appear late", not "sending". Check with `TAG_MESSAGES_LOADER` logs.

## Fix
—
