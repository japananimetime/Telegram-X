---
mantis: 
area: Interface / notifications
severity: P1
status: mitigated-untested
build: all-features-combined e33134ba
device: user's phone
---
# Replies chat opens empty from a notification

## Symptom
Tapping a notification from the "Replies" chat (Telegram's service chat that collects replies/mentions to your comments) opens the chat with no messages; leaving and re-entering shows them. Reported 2026-09-06.

## Facts (2026-09-06)
It is Telegram's "Replies" service chat, and it happens **only** from the notification tap; opening from the chat list is fine. So the difference is the positioned load at the notification's message id (`highlightMessage`), typically during a cold start when TDLib has not loaded that chat yet.

## Where in the code
`MainActivity.openMessagesController` (≈1388): notification intent extras `message_id` → `params.highlightMessage(...)`, and `message_thread_id` → **`params.messageTopic(new TdApi.MessageTopicForum((int) messageThreadId))`** for *any* chat. The thread id is filled by `TdlibNotificationStyle` (≈354 forum topic views, ≈737 `group.findForumTopicId()`), which only yields forum topic ids, so a non-forum chat should get 0 — verify with a log. If a non-zero id ever reaches a non-forum chat, the loader filters every message by a bogus forum topic and the chat is empty until reopened normally: exactly the symptom.
Otherwise the highlight path: `TdlibUi.openChat` → `MessagesManager.loadFromMessage(force=false)` → `GetChatHistory(from = message, offset = -19, limit = 33)`; an error/empty result is displayed as an empty chat (`MessagesLoader.newHandler` ≈338, `displayMessages`).

## Hypotheses
1. Bogus forum topic from the notification intent (see above).
2. Highlighted message not loadable at that moment (message id from the push, TDLib has no history yet) → empty positioned load; the B3 fallback only covers forum topics. A general fallback (retry from the end when a positioned initial load returns nothing) would cover this too.

## Fix
Branch `fix/giveaway-typing-topics`, `MessagesLoader`: the one-shot "empty positioned initial load → reload from the end" fallback from B3 now applies to every plain chat, not only forum topics (`specialMode == NONE`, no search filter). The chat can no longer stay empty; at worst it opens at the bottom instead of at the highlighted message. The `TAG_MESSAGES_LOADER` warning names the message id that failed, so the root cause can still be chased from a log.

## Verification
Not device-tested. Kill the app, get a Replies notification, tap it: chat must show messages.
