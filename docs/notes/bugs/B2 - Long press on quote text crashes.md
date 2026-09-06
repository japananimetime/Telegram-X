---
mantis: 
area: Interface / messages
severity: P0
status: not reproducible on 0.28.11.1785 (2026-09-06)
build: all-features-combined e33134ba
device: user's phone
---
# Long press on quote ("citation") text crashes the app

## Symptom
Tap-and-hold on quoted text in a message crashes the app. Reported 2026-09-06.

## Reproduce
Unclear which "quote": (a) a blockquote block inside a message body (`TextEntityTypeBlockQuote` / expandable), or (b) the quoted excerpt shown in a reply header. Ask / try both.

## Evidence
None yet. **Needed:** the crash text. Two ways: after the crash relaunch the app → Settings → Help → Bug reports (`SettingsBugController`) → "View error" (`btn_showError`, shown after a crash); or `adb logcat -s AndroidRuntime:E` while reproducing. See [[Debugging Playbook]].

## Where in the code
Long-press chain for blockquotes: `util/text/Text.performLongPress` (≈3329) → first the message callback `TGMessage.onLongPress` (≈7805) → `TGMessage.performLongPress(view, 0, 0)`; then the `pressedQuote` branch → `QuoteBackground.performLongPress` + `TextEntityMessage.performLongPress` (≈575) which builds `copyText = sb.subSequence(entity.offset, entity.offset + entity.length)` from `text.getText()` and shows a Copy option via `context.showOptions(...)`. Quote *selection* (creating a reply quote) lives in `TGMessageText.processTextSelection` / `showQuoteActionMode` + `util/text/TextSelectionHelper`.

## Hypotheses (unverified)
1. `subSequence` with TDLib entity offsets against a `Text` that holds a shorter/transformed string (e.g. an expandable quote rendered collapsed, or rich-message paragraph splitting) → `StringIndexOutOfBoundsException`.
2. `clickableEntity` and `quoteEntity` both null in `TextEntityMessage.performLongPress` → NPE at `clickableEntity.type`.
3. Reply-header quote: different path entirely (`ReplyComponent` has no long-press handling; would come from `MessageView`).

## Fix
No change made. On the 2026-09-06 build (0.28.11.1785, branch `fix/giveaway-typing-topics`) the user long-pressed quote text on device with a logcat crash watcher attached: no crash. Keep this note; if it recurs, grab the trace as described above.

## Verification
—
