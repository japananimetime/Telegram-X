---
mantis: 953
area: Interface / mini apps
severity: P1
status: fixed, commit 009c7ffef (fix/crashes-2026-09-17), device-tested OK 2026-09-17
build: all-features-combined 6a9a2a4bb (0.28.11.1785); crashes seen on 0.28.9 4b48823aa
device: user's phone
---
# Mini App from link crashes on open

## Symptom
Opening a Mini App through a web-app link crashes immediately. Three crash files on 0.28.9 (Jul 27, Jul 30, Aug 10); the code path is unchanged on 0.28.11.

## Reproduce
Open a `t.me/<bot>/<app>` style web-app link so `TdlibUi.openWebAppLink` pushes a `WebAppController` with arguments.

## Evidence
`NullPointerException: FrameLayoutFix.addView on a null object reference` at `WebAppController.onCreateWebView:317` ← `WebkitController.onCreateView:134`.

## Where in the code
`WebkitController.onCreateView` (hook order) and `WebAppController.onCreateWebView` (`contentView = (FrameLayoutFix) webView.getParent()`). See [[Feature Areas]] (mini apps).

## Cause
`WebkitController.onCreateView` called the `onCreateWebView` hook before `contentView.addView(webView)`, so the WebView had no parent yet and `WebAppController` dereferenced null when adding its main/secondary buttons.

## Fix
Attach the WebView to the container first, then call the hook. `WebkitController.java`, commit `009c7ffef` on `fix/crashes-2026-09-17`, Mantis #953. `GameController` and `TelegramFaqController` only configure the WebView in that hook.

## Verification
Device-tested OK 2026-09-17 01:52: `t.me/DurgerKingBot/menu` opens the Mini App through openWebAppLink. Original plan: open a Mini App from a link and from a bot menu button.
