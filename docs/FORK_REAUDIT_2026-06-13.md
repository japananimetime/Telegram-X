# Telegram X Fork — Second-Pass Audit (2026-06-13)

Re-run of the 7-way fan-out audit after this session's P0/cheap-wins/bubble-renderer
work. Goal unchanged: **as functional as the official app, with the current (better)
design**. Severity: **P0** crash/security/data-loss/fake-working · **P1** correctness ·
**P2** quality/polish · **gap** missing vs official · **design** UX shortfall.

The original audit (`FORK_QUALITY_AUDIT.md`) is fully resolved. This pass focuses on
the code added since, plus a fresh parity sweep.

---

## Fixed during this re-audit pass

| # | Sev | Issue | Commit |
|---|-----|-------|--------|
| 1 | **P0** | **ClassCastException regression** — the poll-vote-count change left `UpdatePendingMessage`/`UpdateLiveStoryTopDonors`/`UpdateChatJoinResult` falling into the `UpdateChatUnreadPollVoteCount` cast. Hard crash on emit. | `34774f97b` |
| 2 | P1 | Paid-media bubble didn't re-render after unlock (text updated in place, never re-running `buildPaidMediaText`). Force `MESSAGE_REPLACE_REQUIRED`. | `ec8e7e10c` |
| 3 | gap | Message-effect picker offered in groups/secret chats where TDLib ignores `effectId`. Gated to private non-secret chats. | `ec8e7e10c` |
| 4 | P2 | `MessageGroupCall` ignored `isVideo` (audio call said "video chat"); fixed voice/video + call semantics. | `ec8e7e10c` |
| 5 | P2 | `speechFooterShown` not cleared when a fact-check claims the footer slot. | `ec8e7e10c` |
| 6 | P1 | **Story viewer hang on `StoryContentLive`** (fell through switch → black screen). Default branch now skips. | `c78bf4aee` |
| 7 | P1 | **Story video hang** — no `onPlayerError` (failed video never advanced). Now skips. | `c78bf4aee` |
| 8 | P1 | Stale story video-download callback played the old video over a new story. Guarded by chat/story id + destroyed. | `c78bf4aee` |
| 9 | P1 | `formatPrice` wrong for JPY/3-decimal/locale (`amount/100.0`). → `CurrencyUtils.buildAmount`. | `45d7ab928` |

---

## P1 cluster — fixed (follow-up pass)

| Issue | Commit |
|-------|--------|
| Effects silently dropped on attach-menu/media/sticker sends + pendingId leak — stamped in both send wrappers | `ac145e7ea` |
| Stars payment ignored `PaymentResult.success`/`verificationUrl` | `b3ded68ac` |
| Star/TON transaction history capped at 50 — added accumulation + "Show more" pagination | `b3ded68ac` |
| `formatPrice` wrong for JPY/3-decimal/locale → `CurrencyUtils.buildAmount` | `45d7ab928` |
| Mini-app `location_checked` on settings-return ignored per-bot grant (denied bot saw granted) | `aae3e2bd8` |
| Mini-app `set_emoji_status` without access check — gated on persisted per-bot grant | `aae3e2bd8` |
| Forum pagination terminator (`length >= 100` not `nextOffset != 0`) | `987fe1fd9` |
| Forum `unreadCount++` double-count — gated on lastMessage advancing | `987fe1fd9` |
| Forum row showed stale draft over newer message | `987fe1fd9` |
| Forum new-message-in-unknown-topic full reload → targeted `GetForumTopic` insert | `987fe1fd9` |
| Story caption entities dropped (display/compose/edit) | `94c887197` |
| `StarAmount` aliased Tdlib's cached object → copy | `069c9b00a` |
| Payment tips never collected (`tipAmount=0`) — tip section + total + `SendPaymentForm` | `e16722a74` |
| Order info never collected (`orderInfoId=""`) — contact-info form + `ValidateOrderInfo` | `e16722a74` |
| Paid-reaction amount hardcoded to 1 — preset + custom amount picker | `614c4ccf1` |
| Shipping address + shipping-option selection never collected — full address form + `ValidateOrderInfo`→options→total | `0e0168549` |

## P1 — correctness, still open

**Forum topics** (remaining)
- Unread count never decremented on *partial* read (only zeroed on full read) — the double-count is fixed, but partial-read drift needs per-topic `UpdateChatReadInbox` handling. `Tdlib.java:8624`.
- `onForumTopicFullyUpdated` override is dead code (never dispatched). `:1629`.
- `resortAndRefresh` uses `notifyDataSetChanged()` (flicker); should diff/range-notify. `:1622`.
- `copyForumTopics` shares nested `lastMessage`/`info` refs; `onMessageContentChanged` mutates `lastMessage.content` in place across threads. `Tdlib.java:3599`.

**Payments** — ✅ **closed out.** Tips, contact info (name/phone/email), and the full shipping-address + shipping-option flow are all implemented. Minor polish remaining: email/phone keyboard input types on the order-info fields (cosmetic).

**Mini Apps** (remaining)
- Biometry access flags (`biometryAccessRequested/Granted`) are ephemeral per-session while the token is persisted → inconsistent `biometry_info_received`. `:1411`.
- `postEvent` origin check is TOCTOU and `requireSameOrigin` defaults `false`; capture origin synchronously + default `true`. `WebAppProxy.java:42`.

---

## P2 — quality

- `StarAmount` from the listener is aliased into Tdlib's cached object (`StarTransactionsController.java:82`) — copy it; balance display ignores `nanostarCount`.
- `ChatsController` story-list listener added from two sites, never removed (soft leak via weak ref); `storyBarView` not nulled in `destroy()`. `ChatsController.java:3253`.
- Gift auctions empty-state **toast spam** on every live update (`GiftAuctionsController.java:96,165`) — use inline empty state.
- ~~"View receipt" is a stub (`GetPaymentReceipt` unused)~~ — ✅ DONE (`3e04bd96c`): tapping the payment-successful service message opens `PaymentReceiptController`. (Payment-open logic is still duplicated between `TGMessageInvoice` and `TdlibUi.openPaymentForm` — minor.)
- Mini-app `share_to_story` fetches an arbitrary bot-supplied `media_url` over HTTP with redirects, no scheme/host/size limit (SSRF-ish); `startFileDownload` passes raw `fileName` to a public dir. `WebAppController.java:1785,1560`.
- `appendFormatted`/`ContentPreview.java:660` lack the null guards their siblings have (low risk).

---

## design — toward "better than official"

**Stories is still the worst offender** (unchanged from pass 1):
- Viewers list shown as a **Toast** of names (`StoryViewController.java:1478`) — should be an avatars/reactions sheet.
- Statistics = **AlertDialog text dump** printing `jsonData.length()` (`:2036`) — no charts.
- **4 raw `android.app.AlertDialog`s** (report/edit-caption/statistics/create-album) ignore the theme (`:1760,1882,2057,2161`).
- Quick reactions = raw `TextView`s with hardcoded `"✕"` glyph + `0xFFFF6666`, no dark-mode/custom-emoji (`:2314`).
- Hardcoded white/black colors throughout the viewer chrome.

**Cross-cutting:** `needAsynchronousAnimation()` still unresolved in the Gift controllers (full framework timeout before appearing); toasts for primary feedback; text-blob detail sheets; `notifyDataSetChanged` flicker; transaction rows lack counterparty avatars / aren't tappable.

---

## TDLib parity gaps (feature areas absent vs official)

Ranked by visibility ÷ effort. None of these are bugs — they're missing features.

| Rank | Feature area | Key unused TDLib funcs | Vis | Effort |
|------|--------------|------------------------|-----|--------|
| 1 | ~~**Chat boosts**~~ — ✅ DONE (`850be94ed`): `ChatBoostController` (status/level/progress + `BoostChat` + share link), channel profile menu | High | M |
| 2 | ~~**Fact-check editing**~~ — ✅ DONE (`57c3476c0`): admin context-menu action → `SetMessageFactCheck` (add/edit/remove) | Low-Med | S |
| 3 | ~~**Quick replies**~~ — ✅ DONE (`5db0dc8bf`): cache + `QuickRepliesController` (list/delete) under Settings; create + "/" send-picker are follow-ups | Med | M |
| 4 | ~~**Downloads manager**~~ — ✅ DONE (`61016ec13`): `DownloadsController` (`SearchFileDownloads` + pause/resume/remove/clear-all) under Settings | Med | M |
| 5 | ~~**Monetization/revenue dashboard**~~ — ✅ DONE (`67f79c175`): `StarRevenueController` (`GetStarRevenueStatistics` balances + USD); chart + withdrawal are follow-ups | Med | M |
| 6 | ~~**Telegram Business suite**~~ — ✅ DONE (`c362b772d`): `SettingsBusinessController` hub + full editors for start page (`SetBusinessStartPage`), location (`SetBusinessLocation`), and chat links (`GetBusinessChatLinks`/`Create`/`Delete`); opening-hours/greeting/away show state + turn-off (`Set*(null)`). Rich editors for hours/greeting/away/connected-bot are follow-ups. | Med | L |
| 7 | ~~**Sticker-pack authoring**~~ — ✅ DONE (`c5e333be0`): `StickerSetsController` — owned-set list (`GetOwnedStickerSets`), create pack (`CreateNewStickerSet`), add sticker (`AddStickerToSet`), rename (`SetStickerSetTitle`), open/share. Image picked via `ACTION_GET_CONTENT`. Emoji-edit/reorder/remove are follow-ups. | Med | L |
| 8 | ~~**Saved Messages topics**~~ — ✅ DONE (`1a3aed2fa`): Tdlib topic cache + `SavedMessagesListener`; `SavedMessagesTopicsController` (Settings → Saved Messages) lists topics with live updates + paging, tap opens the chat scoped to the topic, pin/unpin (`ToggleSavedMessagesTopicIsPinned`) + delete (`DeleteSavedMessagesTopicHistory`). | Med | L |
| 9 | **Group calls / video chats / live streams** (XL — being sliced). **slice 1 ✅** (`e89f5f8af`): read-only `GroupCallController` + `openVoiceChat`. **slice 1.5 ✅** (`072705b93`): in-chat call bar. **slice 2 ✅ (joinable, Path A — raw in-tree tgcalls bridge, no ntgcalls):** 2a native JNI bridge to `GroupInstanceCustomImpl` + join handshake (`60ceaa801`,`3753d9c01`); 2b `GroupCallManager` TDLib `JoinVideoChat` orchestration + `GroupCallService` foreground/audio-focus (`fb7dd8e7f`,`7c7ee61af`); 2c interactive Join/Leave/Mute UI (`9a64f835f`). **slice 3 (tractable subset) ✅** (`400d13918`): create/start/end video chats (`CreateVideoChat`/`StartScheduledVideoChat`/`EndGroupCall`/`SetVideoChatTitle`) from the profile overflow + `GroupCallController` admin section, RTMP-out (`RtmpUrlController`: `GetVideoChatRtmpUrl`/`ReplaceVideoChatRtmpUrl`), invite link (`GetVideoChatInviteLink`). **Remaining:** live audio needs device verification (2 accounts); participant roster + speaking rings + audio-levels callback; live-stream *listening* (broadcast mode) + screen-share (own native sub-projects); conference (`JoinGroupCall`/InputGroupCall link) path. | High | XL |
| 10 | ~~**Refunds / receipts / star withdrawal**~~ — ✅ DONE (`3e04bd96c`): `PaymentReceiptController` (`GetPaymentReceipt`) opened from the payment-successful service message; real Star withdrawal in `StarRevenueController` (2FA confirm → `GetStarWithdrawalUrl` → open URL). `RefundStarPayment` left out — bots-only, no user-client entry point. | Low-Med | S–M |

**Empty/stubbed update handlers** (no-op `// TODO`): `updateSpeechRecognitionTrial` (voice-to-text trial quota), `updateDefaultBackground`, `UpdateNewOauthRequest` (login approval), `UpdateStakeDiceState`, `UpdateTextCompositionStyles`, `UpdateWebBrowserSettings`, `UpdatePendingMessage`, `UpdateLiveStoryTopDonors`, `UpdateChatJoinResult`.

---

## Recommended next order

1. **Effects media-send threading + leak fix** (P1, the one this-session feature that's not fully wired).
2. **Payment P1 cluster** (formatPrice ✓ done; tips, order-info, tx pagination, Stars-success check) — these mishandle real money.
3. **Mini-app consent correctness** (location-on-settings-return, emoji-status gate, biometry persistence, postEvent origin default).
4. **Forum count/pagination correctness** (double-count, terminator, targeted topic fetch).
5. **Story caption entities** + the cheap design wins (viewers sheet, themed dialogs).
6. **Parity features by ROI**: chat boosts → fact-check editing → quick replies → downloads manager.
7. **Group calls** as a standalone epic (unblocks the call card + live stories).
