---
mantis: 994
area: Interface
severity: minor
status: fixed, awaiting device test
build: core/upstream-2026-10
device: 
---
# B12 — "Unread messages" separator 1–2 messages too high; saved scroll position lost

## Symptom
(A) Read a chat to the very end, leave, come back: the "Unread messages" bar sits 1–2 messages above the end, although the chat list shows no unread badge.
(B) Scroll a few messages up, leave, come back later (after other actions): the chat opens at the bottom, the mid-chat position is gone.

## Reproduce
(A) Sit at the bottom of an active group without scrolling, let 1–2 messages (or album parts) arrive, press back, reopen.
(B) Scroll up, leave, then open the same chat from a notification / link / reply and leave again; reopen from the chat list.

## Where in the code
`component/chat/MessagesManager.java`: `updateNewMessage`, `setFocused`, `saveScrollPosition`. Related: [[Forum Topics]].

## Cause
(A) Upstream: messages are reported read only from `onScrolled`, which RecyclerView doesn't fire when a new bottom message keeps the visible position range unchanged (full screen; merged album parts change no positions). Nothing re-checked on blur, and on blur view requests were already disallowed (`allowViewRequest` → `isFocused`).
(B) Upstream: any visit that opened elsewhere (bottom, notification, link) overwrote the saved position on its first layout pass. Fork: forum topic tabs (no `messageThread`) saved under the chat-level key, clobbering the plain chat view's position.
Also `TGMessage.isOld()` compared seconds to `MINUTES.toMillis(5)` (≈3.5 days instead of 5 minutes).

## Fix
Branch `fix/unread-marker-scroll`: `scheduleViewMessages()` after a new bottom message; `viewMessages(false)` before losing focus; saving a scroll position no longer replaces a saved mid-chat position until the user scrolls or sends (unless the visit itself restored that position); scroll key uses the forum topic when there's no message thread; `isOld()` unit fix.
