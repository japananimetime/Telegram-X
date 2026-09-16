---
mantis: 955
area: Interface / notifications
severity: P1
status: fixed, commit e55d1d096 (fix/crashes-2026-09-17), device-tested OK 2026-09-17
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
Not yet triggered on device (no Replies notification arrived during the 2026-09-06 test session).

## Related observation (2026-09-06, not a client bug)
A fresh non-member comment in t.me/yobangelion (post 19555, comment 150733) got a reply from a friend, and **no Replies message was created on the server**: neither this app nor nchat on the laptop (same account) shows anything in the Replies chat, and no notification arrived. Candidates on Telegram's side: the account had *left* the discussion group the day before (may differ from never joined), the reply may have targeted the post rather than the comment, or delayed delivery. Retest in a channel whose discussion group was never joined before filing anything.

## Root cause found 2026-09-17
Hypothesis 1 was right. The device log for a real tap (09/13 15:50:59, local chat 61) shows `handleIntent OPEN_CHAT` followed by **`Received error: #400: The chat is not a forum`** twice around the fallback line `Empty initial chunk around 2005549449216, retrying from the end`. TDLib fills `message.topicId` with a `MessageTopicForum` for Replies-chat messages (the original comment thread), `TdlibNotification.findForumTopicId()` passed it through, the intent carried `message_thread_id`, and the loader requested forum-topic history from a private chat. The 09-06 fallback retried **with the same topic**, so it failed too.

Reproduced on demand with a crafted intent (`--el chat_id 1271266957 --el message_id 2005549449216 --el message_thread_id 12345` after a force-stop): empty chat, same two errors.

## Fix (final)
Commit `e55d1d096`: `TdlibUi.openChat` drops a `MessageTopicForum` when the target chat is not a forum (covers every caller), and `TdlibNotification.findForumTopicId()` only reports a topic for forum chats. Mantis #955.

## Verification
Device-tested OK 2026-09-17 02:10 on build 0.29.0.1785: the same crafted intent opens the Replies chat positioned at the Sept 13 message, no loader errors in the app log.
