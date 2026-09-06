# Known Issues & Open Items

Consolidated from [[FORK_REAUDIT_2026-06-13]], [[FORK_QUALITY_AUDIT]], [[FORK_GAP_AUDIT]] and [[HANDOFF_2026-06-15_calls-parity-push]], filtered to what those documents still mark open. Your own bug reports go in [[bugs/Bug Index]]; move an item there when you start on it.

## User-reported bugs (2026-09-06)
See [[bugs/Bug Index]]: giveaway card crash (fixed), quote long-press crash (needs log), empty large topic (fixed, probable), forum caching (needs specifics), typing status (fixed).

## Unverified on device (highest risk)
- Native calls: 1:1 video, group video tiles, switch camera, accept-with-video, screen share (projection permission, FGS type, group presentation). Two devices needed.
- Background push on Android 12/13/14 with the real `google-services.json` and a keystore-signed build.
- Fresh-clone build of `all-features-combined` on a new machine (HANDOFF 06-14 item 7 — never done; the Linux box is the chance).

## Correctness (P1/P2 still open)
| Area | Item | Where |
|---|---|---|
| Forum | `resortAndRefresh` → `notifyDataSetChanged` flicker | `ForumTopicsController` ≈1626 |
| Mini apps | `postEvent` origin TOCTOU; `WebMessageListener` migration deferred | `WebAppProxy.java:42` |
| Mini apps | `web_app_send_prepared_message` / `share_message` not implemented (`GetPreparedInlineMessage`) | `WebAppProxy.java` ≈569 |
| Chat | `appendFormatted` / `ContentPreview.java:660` lack null guards | `data/ContentPreview` |
| Payments | order-info fields lack email/phone keyboard input types | `PaymentFormController` |
| Payments | payment-open logic duplicated in `TGMessageInvoice` and `TdlibUi.openPaymentForm` | |
| Chat theme | named peer-set chat theme / accent color not applied (`onChatThemeChanged` no-op; wallpaper works) | `MessagesController.java` ≈554 |
| Stars | balance not pushed live to an open screen (correct on open) | `SettingsStarsController` |

## Design / UX debt
- Stories viewer: viewers list as Toast (`StoryViewController` ≈1478), statistics as AlertDialog text (≈2036), four raw `AlertDialog`s (≈1760, 1882, 2057, 2161), quick reactions hardcoded `"✕"` + `0xFFFF6666`, hardcoded white/black chrome.
- `needAsynchronousAnimation()` never resolved in the 5 gift controllers and others → wait the full framework timeout before appearing. Fix: call `executeScheduledAnimation()` in the data-loaded callback.
- Toasts used for primary feedback and empty states (gifts, stories) instead of tooltips / in-list empty cells.
- `notifyDataSetChanged()` for incremental updates in story bar, forum list, tag bar.
- Gift details and forum icon picker are text-blob sheets; should be visual.
- Transaction rows without counterparty avatars, not tappable.
- Scattered hardcoded strings (`amount + " • " + date`).

## Feature gaps (not bugs)
See the bottom of [[Feature Areas]] and the ranked table in [[FORK_REAUDIT_2026-06-13]]. Biggest: live stories, conference calls, direct-message topics, passkeys, checklists, AI features.

## Migration markers still in code
`TODO(td)` in `data/TD.java:1881` (customTitle → tag), `TODO(rich-media)` in `PageBlock.java`, `TGMessageRich.java`, `MediaItem.java`, `MediaPreview.java:511`; `TODO(rich-theming)` / `TODO(rich-rtl)` in `TGMessageRich.java`. `grep -rn "TODO(td)\|TODO(rich" app/src/main/java` to refresh this list.
