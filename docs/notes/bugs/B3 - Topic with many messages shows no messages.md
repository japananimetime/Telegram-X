---
mantis: 
area: Topics
severity: P1
status: fixed, device-tested OK 2026-09-06 (build 0.28.11.1785)
build: all-features-combined e33134ba
device: user's phone
---
# Topic with many messages shows no messages

## Symptom
Opening a large topic from the topic list shows an empty chat and nothing ever loads. Reported 2026-09-06.

## Reproduce
Best guess: a topic the user has **never read** (`lastReadInboxMessageId == 0`, `unreadCount > 0`) opened from the **list** layout. Tabs layout is unaffected (it opens every topic from the end with no highlight id).

## Evidence
Static only. `ForumTopicsController.openTopic` (≈937) handles the never-read case with `new MessageId(chatId, MessageId.MIN_VALID_ID)` where `MIN_VALID_ID == 1L` — not a real message id — and `HIGHLIGHT_MODE_UNREAD`. That becomes `GetForumTopicHistory(chat, topic, fromMessageId = 1, offset = -19, limit = 33)` in `MessagesLoader.load` (≈1196). If TDLib answers with an error or zero messages, `newHandler` turns it into an empty chunk (≈338), and for `MODE_INITIAL` the loader sets `canLoadTop = false` (`scrollMessageId.isHistoryStart()`) and `canLoadBottom = false` (no suitable message) (≈1656) — exactly "no messages, cannot load at all". Enable log tag `TAG_MESSAGES_LOADER` in Settings → Bug reports → Log tags to see the `Received error:` line and confirm.

## Where in the code
`ui/ForumTopicsController.openTopic`, `component/chat/MessagesLoader` (`load`, `newHandler`, `processMessages` tail), see [[Forum Topics]].

## Fix
Branch `fix/giveaway-typing-topics`:
- `openTopic`: never-read topics start from the topic root message, `MessageId.fromServerMessageId(topic.info.forumTopicId)` (for General that is the chat's first message), instead of id 1.
- `MessagesLoader`: one-shot safety net — if a positioned initial load of a forum topic returns nothing, retry `loadFromStart` from the end so the user always sees the latest messages (logged under `TAG_MESSAGES_LOADER`).

## Verification
Not built yet. Test with a never-opened topic of a few thousand messages in list layout, then the same topic in tabs layout, then a topic with a few unread messages (should still open at the first unread).

## Related
`processMessages` uses the *chat-level* `lastReadInboxMessageId` / `unreadCount` for the unread divider even in topic mode (≈1386–1398); the topic's own values from `TdApi.ForumTopic` would be correct. Not changed yet.
