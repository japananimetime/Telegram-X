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

## P1 — correctness, still open

**Message effects**
- **Effect silently dropped on attach-menu / media / sticker sends.** The choke layer only covers `send()`; `sendPhotosAndVideosCompressed` (MediaLayout/CameraController/MediaViewController) and `sendContent` (stickers/GIFs/dice/files/contacts/audio) bypass `applyPendingMessageEffect`, and the unused `pendingMessageEffectId` then **leaks into the next text send**. *Fix:* stamp the effect at the lowest common send point (thread it into `sendPhotosAndVideosCompressed`/`sendContent` `MessageSendOptions`, or apply centrally in the chat's `sendMessage` options for albums/photos — `effectId` is valid on `sendMessage`/`sendMessageAlbum`). At minimum clear `pendingMessageEffectId` after any send to stop the leak. `MessagesController.java:~2405`.

**Forum topics** (all carried over from pass 1, re-verified present)
- Unread count inferred locally; `unreadCount++` double-counts and is only ever zeroed, never decremented on partial read. `ForumTopicsController.java:1796`, `Tdlib.java:8624`.
- Pagination terminator should be "full page (`length >= 100`)", not `nextOffset != 0`. `ForumTopicsController.java:820,886`.
- New message in unknown topic → full 100-topic reload instead of one `GetForumTopic`. `:1742`.
- Row shows draft even when `lastMessage.date` is newer. `ForumTopicView.java:225`.
- `onForumTopicFullyUpdated` override is dead code (never dispatched). `:1629`.
- `resortAndRefresh` uses `notifyDataSetChanged()` (flicker); should diff/range-notify. `:1622`.
- `copyForumTopics` shares nested `lastMessage`/`info` refs; `onMessageContentChanged` mutates `lastMessage.content` in place across threads. `Tdlib.java:3599`.

**Payments**
- Order info / shipping never collected (`orderInfoId`/`shippingOptionId` always `""`); `ValidateOrderInfo` unused. `PaymentFormController.java:581`.
- Tips ignored (`tipAmount=0` hardcoded; `suggestedTipAmounts` never rendered). `:584`.
- Star/TON transaction history capped at 50, `nextOffset` never used (no load-more). `StarTransactionsController.java:97`, `TonTransactionsController.java:96`.
- Stars payment treats any non-error as success; ignores `PaymentResult.success`/`verificationUrl`. `TdlibUi.java:8146`.
- Paid-reaction amount hardcoded to 1 Star — no amount slider. `TGMessage.java:9819`.

**Mini Apps**
- `location_checked` on return from settings (`onFocus`) reports `access_granted` from OS permission only, ignoring the per-bot grant — contradicts `onWebAppCheckLocation`; a **denied** bot can see `granted:true`. `WebAppController.java:2143`.
- `onWebAppSetEmojiStatus` sets the status without verifying the `emoji_status_access` prompt was granted. `:1592`.
- Biometry access flags (`biometryAccessRequested/Granted`) are ephemeral per-session while the token is persisted → inconsistent `biometry_info_received`. `:1411`.
- `postEvent` origin check is TOCTOU and `requireSameOrigin` defaults `false`; capture origin synchronously + default `true`. `WebAppProxy.java:42`.

**Stories**
- Caption **entities dropped** on display (`captionView.setText(story.caption.text)`), compose, and edit (`new TextEntity[0]`). Links/bold/mentions/custom-emoji lost. `StoryViewController.java:684`, `StoryPreviewController.java:467`.

---

## P2 — quality

- `StarAmount` from the listener is aliased into Tdlib's cached object (`StarTransactionsController.java:82`) — copy it; balance display ignores `nanostarCount`.
- `ChatsController` story-list listener added from two sites, never removed (soft leak via weak ref); `storyBarView` not nulled in `destroy()`. `ChatsController.java:3253`.
- Gift auctions empty-state **toast spam** on every live update (`GiftAuctionsController.java:96,165`) — use inline empty state.
- "View receipt" is a stub (`GetPaymentReceipt` unused); payment-open logic duplicated between `TGMessageInvoice` and `TdlibUi.openPaymentForm`.
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
| 1 | **Chat boosts** | `GetChatBoostStatus`, `BoostChat`, `GetAvailableChatBoostSlots`, `GetChatBoostLink` | High | M |
| 2 | **Fact-check editing** | `SetMessageFactCheck` (display already done) | Low-Med | S |
| 3 | **Quick replies** | `LoadQuickReplyShortcuts`, `AddQuickReplyShortcutMessage`, `EditQuickReplyMessage` (updates already cached) | Med | M |
| 4 | **Downloads manager** | `SearchFileDownloads`, `GetFileDownloads`, `ToggleDownloadIsPaused` (`UpdateFileDownload` already arrives) | Med | M |
| 5 | **Monetization/revenue dashboard** | `GetChatRevenueStatistics`, `GetStarRevenueStatistics`, `Get*WithdrawalUrl` (updates already handled) | Med | M |
| 6 | **Telegram Business suite** | `SetBusinessOpeningHours/Location/Greeting/AwayMessage/ConnectedBot/StartPage` | Med | L |
| 7 | **Sticker-pack authoring** | `CreateNewStickerSet`, `AddStickerToSet`, `SetStickerEmojis` | Med | L |
| 8 | **Saved Messages topics** | `LoadSavedMessagesTopics`, `GetSavedMessagesTopicHistory`, `ToggleSavedMessagesTopicIsPinned` | Med | L |
| 9 | **Group calls / video chats / live streams** | `JoinGroupCall`, `CreateVideoChat`, `GetGroupCall*` — `GroupCallListener` has zero implementers; gates the call card + `StoryContentLive` | High | XL |
| 10 | **Refunds / receipts / star withdrawal** | `RefundStarPayment`, `GetPaymentReceipt`, `GetStarWithdrawalUrl` | Low-Med | S–M |

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
