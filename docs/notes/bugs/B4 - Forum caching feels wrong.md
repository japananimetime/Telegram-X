---
mantis: 
area: Topics
severity: P2
status: fixed-untested
build: all-features-combined e33134ba
device: user's phone
---
# Forum caching feels wrong

## Symptom
Reported 2026-09-06: (1) entering a forum shows a stale topic list (and stale last-message previews) that then visibly, uncomfortably rewrites itself; (2) messages inside a topic seem to load from scratch every time, even for topics opened before.

## Cause
(1) `ForumTopicsController.loadTopics` paints `Tdlib.getCachedForumTopics` first, then replaces the whole list with the `GetForumTopics` result via `adapter.setTopics` → `notifyDataSetChanged()`. Nothing kept that cache current while the screen was closed: `Tdlib` only wrote it from `GetForumTopics` / `fetchForumUnreadTopicCount`, so every new message, read, pin or rename since the last visit was missing from the first paint and then flashed in. Same pattern in `onNewMessage` when a topic moved (`notifyDataSetChanged`).
(2) `MessagesLoader` forces `loadingLocal = false` for `GetForumTopicHistory` (TDLib has no only-local variant for topic history), so unlike normal chats there is no instant local first paint; and unread topics open with a positioned network request (`CHUNK_SIZE_SEARCH`). TDLib itself serves thread history from its database when it has it, so this is latency, not lost data. Not changed yet; see "Remaining".

## Fix
Branch `fix/giveaway-typing-topics`:
- `Tdlib`: the topics cache is now maintained from updates while no screen is open — `updateForumTopicInfo` (name/icon/closed/hidden), `updateForumTopic` (read positions, pin, mute, draft, "fully read" → unread 0), `updateNewMessage` / `updateMessageSendSucceeded` (last message, order bump, unread++ for incoming), with the unread-topic badge recomputed.
- `ForumTopicsController`: the network refresh is applied with `DiffUtil` against the on-screen snapshot (only rows that changed redraw/move); a single re-sorted row uses `notifyItemMoved`.

## Remaining
- Instant first paint for topic history would need a local-only source: e.g. try `GetChatHistory(onlyLocal=true)` filtered by `Td.matchesTopic` before the topic request, or keep recently visited topic controllers alive. Measure on device first.
- `MessagesLoader.processMessages` still uses chat-level read ids for the unread divider in topic mode.

## Where to look
`Tdlib.updateForumTopicsCache` / `getCachedForumTopics` / `removeCachedForumTopic` (≈3567–3597), `copyForumTopics` (deep copies `lastMessage`), `ForumTopicsController.loadTopics` (shows cached topics first, then `GetForumTopics`), `resortAndRefresh` (`notifyDataSetChanged`, known flicker), `applyNewTopicMessage`, `fetchAndInsertTopic`. Possibly also B3's chat-level read state in `MessagesLoader.processMessages`. See [[Forum Topics]] regression list.

## Fix
—
