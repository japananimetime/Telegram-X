---
mantis: 
area: Interface / notifications
severity: P1
status: open — needs facts
build: all-features-combined e33134ba
device: user's phone
---
# Replies chat opens empty from a notification

## Symptom
Tapping a notification from the "Replies" chat (Telegram's service chat that collects replies/mentions to your comments) opens the chat with no messages; leaving and re-entering shows them. Reported 2026-09-06.

## Facts needed
- Exact chat: the "Replies" service chat (user id 1271266957) or the discussion group where the mention happened?
- Does it also happen when opening that chat from the chat list, or only from the notification tap?
- Log lines from `TAG_MESSAGES_LOADER` at that moment (Settings → Bug reports → Log tags), especially `Received error:`.

## Where in the code
`MainActivity.openMessagesController` (≈1388): notification intent extras `message_id` → `params.highlightMessage(...)`, and `message_thread_id` → **`params.messageTopic(new TdApi.MessageTopicForum((int) messageThreadId))`** for *any* chat. The thread id is filled by `TdlibNotificationStyle` (≈354 forum topic views, ≈737 `group.findForumTopicId()`), which only yields forum topic ids, so a non-forum chat should get 0 — verify with a log. If a non-zero id ever reaches a non-forum chat, the loader filters every message by a bogus forum topic and the chat is empty until reopened normally: exactly the symptom.
Otherwise the highlight path: `TdlibUi.openChat` → `MessagesManager.loadFromMessage(force=false)` → `GetChatHistory(from = message, offset = -19, limit = 33)`; an error/empty result is displayed as an empty chat (`MessagesLoader.newHandler` ≈338, `displayMessages`).

## Hypotheses
1. Bogus forum topic from the notification intent (see above).
2. Highlighted message not loadable at that moment (message id from the push, TDLib has no history yet) → empty positioned load; the B3 fallback only covers forum topics. A general fallback (retry from the end when a positioned initial load returns nothing) would cover this too.

## Fix
—
