---
mantis: 954
area: Interface / mini apps
severity: P1
status: fixed, commit c84851515 (fix/crashes-2026-09-17), device-tested OK 2026-09-17
build: all-features-combined 994cb6573 (0.28.11.1785)
device: user's phone
---
# Mini App crashes on close with confirmation

## Symptom
In a Mini App that enabled closing confirmation (DurgerKing after adding an item), pressing the header X or system back crashes the app instantly. Found during the 2026-09-17 on-device Mini App check, not in the historical crash logs.

## Reproduce
1. @DurgerKingBot → /start → "Order Food".
2. ADD any item.
3. Tap X.
Frequency: always.

## Evidence
`StackOverflowError`; the repeating frames are `WebAppController.performOnBackPressed:435 → showCloseConfirmation:442 → ViewController.showOptions → PopupLayout.showSimplePopupView:521 → BaseActivity.handleOnBackPress → NavigationController.performOnBackPressed → …`.

## Where in the code
`WebAppController.performOnBackPressed` / `showCloseConfirmation`; the query semantics of `ViewController.performOnBackPressed(fromTop, commit)`. See [[Feature Areas]] (mini apps).

## Cause
`performOnBackPressed` is also called with `commit == false` as a query (BaseActivity does that while a popup is being shown). The controller ignored the flag and opened the sheet on every call, re-entering itself through the popup it was opening. The web-app back-button branch had the same flaw.

## Fix
Report "handled" on a query and act only on commit, the same pattern MessagesController uses for text selection. `WebAppController.java`, commit `c84851515`, Mantis #954.

## Verification
Device-tested OK 2026-09-17 01:52: confirmation sheet appears, Close closes the app, Cancel keeps it. The Mini App flow itself works end to end (launch from keyboard button and from a `t.me/DurgerKingBot/menu` link, theme, main button, back button, haptics, order page).

## Open polish
Header title differs by launch path: "Durger King" from the keyboard button, "DurgerKingBot" (username) from the direct link. Cosmetic.
