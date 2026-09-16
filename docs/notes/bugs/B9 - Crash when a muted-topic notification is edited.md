---
mantis: 952
area: Topics / notifications
severity: P1
status: fixed, commit c979604d6 (fix/crashes-2026-09-17), awaiting device test
build: all-features-combined 6a9a2a4bb (0.28.11.1785)
device: user's phone
---
# Crash when a muted-topic notification is edited

## Symptom
App dies in the background or while open, on the `NotificationQueue` thread. Six crash files (`crash.0.28.9.*.{1,3,7,25}`, `crash.0.28.11.1785.6a9a2a4b.{1,2}`: Sep 10 20:38, Sep 15 21:52) and one system dropbox entry.

## Reproduce
1. Mute one topic in a forum whose chat itself is not muted.
2. Receive a message in that topic while the app is not in the foreground.
3. That message gets edited or deleted before the notification group is dismissed.
Frequency: whenever the sequence happens.

## Evidence
`java.lang.IllegalStateException: Notification not found in the global list` at `TdlibNotificationHelper.editNotification:313` ← `TdlibNotificationManager.processNotification:2341`.

## Where in the code
`TdlibNotificationHelper.processNotificationGroup` (fork muted-topic filter) and `editNotification`; `TdlibNotificationGroup.updateNotification`; `TdlibNotification.isFromMutedForumTopic`. See [[Forum Topics]].

## Cause
The fork's muted-forum-topic filter keeps such notifications out of the helper's global `notifications` list but leaves them inside the `TdlibNotificationGroup`. On `updateNotification` the group finds and updates it, then the upstream assertion that both lists are in sync throws.

## Fix
`editNotification`: when the notification is not in the global list, keep it out if it is still from a muted topic, otherwise adopt it (add + sort) and continue. `TdlibNotificationHelper.java`, commit `c979604d6` on `fix/crashes-2026-09-17`, Mantis #952.

## Verification
Pending on device. Watch: notification count/summary for forums with a mix of muted and unmuted topics.
