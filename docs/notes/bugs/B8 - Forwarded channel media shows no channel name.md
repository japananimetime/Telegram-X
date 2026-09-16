---
mantis: 950
area: Interface / messages
severity: P2
status: fixed, commit 5ad39af9d (fix/crashes-2026-09-17), device-tested OK 2026-09-17
build: all-features-combined 6a9a2a4bb (0.28.11.1785)
device: user's phone
---
# Forwarded channel media shows no channel name

## Symptom
A photo/video forwarded from a channel into a private chat renders the forward line (vertical bar, "yesterday 6:12 PM", 159,900 views, 4,098 shares) but the channel name before the time is empty. Reported 2026-09-17. Screenshot: `B8 - forward line without channel name.png`.

## Reproduce
1. Find a channel post with a six-digit view count and a four-digit share count (photo or video).
2. Forward it to a private chat, bubble mode.
3. Wait until the forward date renders as "yesterday HH:MM", or use a narrow screen.
Frequency: always for that combination. Text forwards are unaffected (they may extend horizontally).

## Evidence
Static only. `TGMessage.buildForward()`: `max = totalMax - fTimeWidth - xTimePadding - viewCounter - shareCounter`; for a media bubble `totalMax` is the content width (`allowBubbleHorizontalExtend()` is false), so `max <= 0` and `makeName()` returns `null`. The time is then drawn at `forwardTextLeft + 0 + 6dp`, exactly what the screenshot shows.

## Where in the code
Chat → `TGMessage.buildForward()` (name), `getViewCountMode()` (which line the counters live on), draw block under `// forward` in `drawBubble`, `getBubbleTimePartWidth()` / `drawBubbleTimePart()` (main time pill). `TGSourceChat` resolves the title correctly; the name was built and discarded.

## Cause
Inherited from upstream Telegram X (code identical at merge base 1e1a4dd39): the forward line gives the counters priority over the author name and never checks whether anything is left for the name.

## Fix
`feature/dpi-desync` (working branch), `TGMessage.java` only. The name is laid out first against everything left after the time; if the view/share counters no longer fit beside it, `fCountersOverflow` is set and `getViewCountMode()` returns `VIEW_COUNT_MAIN`, so the counters move to the message's own time part (bubble time pill / header line). `setForwardCountersOverflow()` re-runs `layoutInfo()` so that part makes room. Flat mode without header keeps the old squeeze (nowhere else to draw them). commit `5ad39af9d` on `fix/crashes-2026-09-17` `[#950]`

## Verification
Device-tested OK 2026-09-17 01:43: the same message now shows "Алексей Ше…" + "yesterday 6:12 PM" on the forward line, counters no longer on that line. Original plan: install the arm64 debug build, open the chat from the screenshot, expect "Соболев LIVE" on the forward line and views/shares in the time pill on the photo. Watch: PSA forwards (unchanged path), counters animating on live view-count updates (`onCounterAppearanceChanged` → `buildForward` → may flip the flag → `layoutInfo`).
