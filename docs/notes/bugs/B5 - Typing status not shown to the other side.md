---
mantis: 
area: General / messaging
severity: P1
status: fixed-untested
build: all-features-combined e33134ba
device: user's phone
---
# Typing status not shown to the other side

## Symptom
Other people do not see "typing…" while the user types. Reported second-hand, 2026-09-06.

## Reproduce
Open a chat that has a saved draft (or where the input text differs from the stored draft), type. Only the *first* visit after the draft was applied matters: from then on that screen never reports typing.

## Evidence
Static. `InputView.setDraft()` sets `ignoreDraft = true` before the programmatic `setInput(...)`, and nothing in the codebase (fork or upstream) ever resets it. `processTextChange` reports `controller.onInputTextChange(s, !ignoreDraft && byUserAction)`, and `MessagesController.onInputTextChange` only calls `setTyping(...)` when that flag is true. So once a draft has been applied to the screen, every later user edit is reported as non-user, and `SendChatAction(ChatActionTyping)` is never sent for that chat. Same latent bug exists upstream; the fork also silently swallows `sendChatAction` errors "Chat doesn't have threads" / "Chat is not a forum" (added with the quotes feature, `2812b4d9`), which hides a second possible cause (a topic id sent for a chat that has none).

## Where in the code
`component/chat/InputView.java` (`setDraft`, `processTextChange`), `ui/MessagesController.java` (`onInputTextChange`, `setChatAction` ≈11100). Receiving side is `telegram/TdlibStatusManager`.

## Fix
Branch `fix/giveaway-typing-topics`:
- `InputView.setDraft`: `ignoreDraft` now only wraps the synchronous `setInput()` call (try/finally).
- `MessagesController.setChatAction`: the swallowed topic errors are logged (`Log.w`) so a rejected typing action leaves a trace.

## Verification
Not built yet. Test: chat with a draft → type → the other device shows "typing…"; also a forum topic and a comment thread.
