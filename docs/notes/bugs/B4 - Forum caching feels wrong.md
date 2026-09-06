---
mantis: 
area: Topics
severity: P2
status: open — needs specifics
build: all-features-combined e33134ba
device: user's phone
---
# Forum caching feels wrong

## Symptom
"Issues with caching in the forums, it works a bit weird." Reported 2026-09-06. No concrete scenario yet.

## Questions for the reporter
- Stale topic list (topics missing / order wrong / unread badges stuck) or stale messages inside a topic?
- After what: app restart, switching topics, coming back from another chat, a new message arriving?
- List layout or tabs layout?

## Where to look
`Tdlib.updateForumTopicsCache` / `getCachedForumTopics` / `removeCachedForumTopic` (≈3567–3597), `copyForumTopics` (deep copies `lastMessage`), `ForumTopicsController.loadTopics` (shows cached topics first, then `GetForumTopics`), `resortAndRefresh` (`notifyDataSetChanged`, known flicker), `applyNewTopicMessage`, `fetchAndInsertTopic`. Possibly also B3's chat-level read state in `MessagesLoader.processMessages`. See [[Forum Topics]] regression list.

## Fix
—
