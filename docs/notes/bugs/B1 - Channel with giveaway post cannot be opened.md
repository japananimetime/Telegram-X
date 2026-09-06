---
mantis: 
area: Interface / messages
severity: P0
status: fixed-untested
build: all-features-combined e33134ba
device: user's phone
---
# Channel with a giveaway post cannot be opened

## Symptom
A channel that posted a Premium giveaway ("lottery") could not be opened at all; it became accessible again only after enough newer posts pushed the giveaway off the first page. Reported 2026-09-06, seen for months.

## Reproduce
Open any chat whose first loaded page contains a `MessageGiveaway` bubble. The app crashes while laying out the card.

## Evidence
Static: `TGInlineKeyboard.firstButton()` is `buttons.get(0)` → `IndexOutOfBoundsException` on an empty keyboard.

## Where in the code
`data/TGMessageGiveaway.java` (card), `data/TGMessageGiveawayBase.java` (`buildContent`, `loadGiveawayInfo`, `onGiveawayInfoLoaded`), `data/TGInlineKeyboard.java`. See [[Feature Areas]].

## Cause
The gift-economy work (commit `ea063202`, "Gift economy (rebased onto core)") made the card's action button optional: `TGMessageGiveawayBase.buildContent` only calls `onBuildButton()` when `hasButton()` is true, and the default `hasButton()` is `getButtonText() != null`. `TGMessageGiveaway` builds its label dynamically in `onBuildButton()` and overrides neither method, so its ripple keyboard stays empty. `onBuildContent()` then schedules `loadGiveawayInfo()`, which calls `rippleButton.firstButton().showProgressDelayed()` → crash on the UI thread every time the card is built. The base class even carries a comment saying the giveaway card should override `hasButton()`; it never did. `TGMessageGiveawayWinners` overrides `getButtonText()` and is unaffected.

## Fix
Branch `fix/giveaway-typing-topics`:
- `TGMessageGiveaway`: `hasButton()` returns true.
- `TGMessageGiveawayBase`: `loadGiveawayInfo()` / `onGiveawayInfoLoaded()` only touch the ripple button when the keyboard has one (`hasRippleButton()`), so no card variant can crash this way again.

## Verification
Not yet built (no toolchain on the Linux box). Test: open a channel with an active giveaway on the first page; tap "Learn more"; check the participating state renders; also a completed giveaway (winners card).
