# Telegram X Fork — Correctness, Quality & Parity Audit

Date: 2026-06-12 · Branch: `main` (full integrated build) · TDLib master `d6debbb` (layer 227)

Compiled from a 7-way parallel audit (TDLib coverage, forum, stories, gifts, mini-apps, other features, design/UX). Every finding was verified against current code; `file:line` evidence cited. Goal: make Telegram X **as functional as the official app, with better-but-consistent design**.

Severity: **P0** = crash / security / data-loss / "looks done but isn't". **P1** = correctness. **P2** = quality/polish. **gap** = missing vs official. **design** = UX shortfall vs the TGX design language.

---

## P0 — Fix first (crashes, security, fake-working features)

> **Status: all 8 RESOLVED** (2026-06-12) — compiles green. Commits: profile notes
> (198ab6a9b), paid reactions (078404e7b), stories caption+progress (8c999bde0),
> mini-app JS injection (9f768b98b), location consent + plaintext storage (46fb585ae).


| # | Issue | Evidence | Action |
|---|---|---|---|
| 1 | **Profile note crashes any profile that has one.** `calculateHeight()` throws `UnsupportedOperationException` for `btn_profileNote` (`TYPE_INFO_MULTILINE`). | `ui/ProfileController.java:5868-5891` | Add a `btn_profileNote` branch mirroring `btn_description`. |
| 2 | **Profile notes are display-only + read-only.** `SetUserNote` is called nowhere; `btn_profileNote` has no onClick (not even copy). Note only appears if set on another client — and then crashes (#1). | `ui/ProfileController.java:5171,6696` | Add edit/delete UI + live-update via `checkProfileNote()` in the `isUpdate` path. |
| 3 | **Paid/star reactions are dead code that never spends the star.** Only `AddPendingPaidMessageReaction(...,1,null)` is called — no `CommitPendingPaidMessageReaction`/`Remove`/`ToggleAnonymous` anywhere, no UI entry point. | `data/TGReactions.java:619-634` | Either build the real flow (confirm → pending → undo timer → commit; anonymity from `defaultPaidReaction()`) or delete it so it doesn't masquerade as working. |
| 4 | **Posted stories are always caption-less.** `postStory(...)` passes `null` for caption though `captionText` is collected. (No caption input UI in composer either.) | `ui/StoryPreviewController.java:443` | Build `FormattedText` from `captionText`; add caption input to composer. |
| 5 | **Open story restarts progress/video on every benign `UpdateStory`** (view-count tick, seen echo). The live-refresh listener calls full `displayStory` → resets timer + re-prepares ExoPlayer. | `ui/StoryViewController.java:514-523` | Split a `refreshChrome(story)` (caption/reactions/header) out of `displayStory`; only reload media if `story.content` changed. |
| 6 | **Mini-app JS injection.** `sendEventToWebApp` builds JS by string concat; `escapeJsonString` doesn't escape U+2028/U+2029; event name is unescaped; `sendCustomMethodResult` interpolates raw `result` JSON. Attacker-influenced values (clipboard, QR, custom-method, device/secure storage) can break out and run JS in the page. | `ui/WebAppController.java:2316-2338` | Escape U+2028/2029; build payloads with `JSONObject`/`JSONStringer`; never interpolate raw `result`/event name. |
| 7 | **Mini-app location has no per-bot consent.** Any app with OS location permission gets precise GPS with zero per-bot prompt. | `ui/WebAppController.java:1592,1655-1678` | Persist per-bot location grant (like biometry tokens); prompt on first request. |
| 8 | **Mini-app "secure storage" silently falls back to plaintext** (API<23 or any cipher error) and still reports success, with no prefix to distinguish. | `util/WebAppSecureStorage.java:88-105` | Gate on `isEncryptionAvailable()`; reject or clearly mark plaintext; never hand back unprefixed plaintext as trusted. |

---

## P1 — Correctness bugs

**Messaging core / shared**
- **Attach-while-typing silently loses typed text** — input cleared unconditionally after `sendPhotosAndVideos` even when the text wasn't consumed as caption. `ui/MessagesController.java:11081-11084`.
- **Cross-chat reply drops the quote highlight** — `quoteInfo` only threaded on the same-chat path; cross-chat open has no fragment highlight. `data/TGMessage.java:2978-2983`.
- **Quote-highlight animator leaks** — raw `ValueAnimator`, never stored/cancelled, captures `this` for 2s; repeated taps race. `data/TGMessageText.java:903-936`; per-frame `new Paint()` at `util/text/Text.java:2452`.
- **`removeTrackChangeListener` calls `.add()` not `.remove()`** — playback listeners never unsubscribe (stale callbacks). `player/TGPlayerController.java:607-611`.
- **Rich-message inline-icon receiver key collision** — `requestIcons` uses base key 0 with no per-block offset; ≥2 text blocks overwrite each other's icons. `data/TGMessageRich.java:215-228`.

**Forum**
- **Unread counts inferred locally and only ever zeroed, never partially decremented** — reading some-but-not-all leaves stale counts; client-side `unreadCount++` per new message double-counts. `telegram/Tdlib.java:8594`, `ui/ForumTopicsController.java:1796`. Rely on `GetForumTopic`/`UpdateChatReadInbox` for authoritative counts.
- **Pagination terminator wrong** — `canLoadMore` keyed on `nextOffset != 0` (always set), should be "full page returned" (`length >= 100`); fires wasteful tail requests. `ui/ForumTopicsController.java:820,886`.
- **New-message-in-unknown-topic triggers a full 100-topic reload** instead of one `GetForumTopic`. `ui/ForumTopicsController.java:1722`.
- **Draft vs last-message** — row shows draft and ignores a newer incoming message; pick the newer by date. `ui/ForumTopicView.java:225`.
- `onForumTopicFullyUpdated` override is **dead code** (never dispatched). `ui/ForumTopicsController.java:1629`.

**Stories**
- **ChatsController story-list listener + `storyBarView` never cleaned up in `destroy()`** — soft leak (weak-ref saves it); re-toggling "hide stories" stacks listeners. `ui/ChatsController.java:3251,~2779`.
- Viewer subscribes to the **global** story list instead of the per-story API (works, but defeats the design). `ui/StoryViewController.java:496`.
- Video download callback has **no destroyed/stale-story guard** and **no `onPlayerError`** (failed video hangs on thumbnail forever). `ui/StoryViewController.java:731-769`.
- Caption/edit render **plain text, dropping entities** (links not clickable; edit strips entities). `ui/StoryViewController.java:638,1871`.

**Gifts**
- **Auction snapshot in-place mutation race** — `activeGiftAuctions()` returns the shared array; `updateGiftAuctionState` swaps elements in place under lock while the UI iterates it. Return a defensive copy or only replace the whole array. `telegram/Tdlib.java:9060,9077`.
- Auction detail renders **permanently empty on initial-state error** (no state set, no `executeScheduledAnimation`). `ui/GiftAuctionController.java:124`.
- Auctions-list **empty-state toast spam** on every live update. `ui/GiftAuctionsController.java:96,161`.
- `GiftPickerController` uses **device clock** (`System.currentTimeMillis`) not `tdlib.currentTimeMillis()` for `nextSendDate`. `ui/GiftPickerController.java:138`.
- `GiftView` badge **mutates a shared `Paints.getBoldPaint13` to STROKE without restoring** — use a private paint. `widget/GiftView.java:373,393`.

**Saved tags**
- `TagChipView`'s `StickerSmallView` receiver **never attached/detached/destroyed** (icons may not render + leak). `widget/SavedMessagesTagsBarView.java:204`.
- Tag labels on bubbles **never refresh on `UpdateSavedMessagesTags`** (stale after rename). `data/TGReactions.java:153`.
- `onSavedMessagesTagsUpdated` **ignores `savedMessagesTopicId`** — topic-scoped update clobbers the global bar. `ui/MessagesController.java:11453`.

**Payments**
- **Order info / shipping never collected or sent** (`orderInfoId`/`shippingOptionId` always `""`); any invoice needing name/email/address is rejected/mischarged. `ui/PaymentFormController.java:577`.
- **Tips ignored & `formatPrice` wrong** (`amount/100.0` breaks JPY/XTR/locale). Use `CurrencyUtils.buildAmount`. `ui/PaymentFormController.java:316,614`.
- **Premium store purchase dead-ends** — Play `storeProductId` path shows "not available" though a working `BillingManager.launchPremiumPurchase()` exists. `ui/SettingsPremiumController.java:203`.
- **Star transaction history capped at 50** — `nextOffset` never read, no load-more. `ui/StarTransactionsController.java:79`.

---

## TDLib parity gaps (functional, prioritized by visibility ÷ effort)

> **Cheap-wins progress (2026-06-13):** DONE — live Stars/TON balance (58bfd4471);
> 16 service-message types now render as grey pills instead of red "unsupported"
> (c47c13f82); chat-list previews for story/gift/service messages (76cdb97bc);
> fact-check display (9d1fe85f6); AI message summaries (1e03e781b); voice/video-note
> transcription (425b5849a).
> REMAINING named — message effects only (needs available-effects store +
> compose-time picker + send-effect playback animation; the largest of the batch).
> Also still open: unread poll-vote badge, unconfirmed-session warning, live
> accent-color/emoji-status redraw, paid-media / checklist / group-call bubble renderers.

**Cheap, high-value (S):**
- **Stars/TON balance live** — `UpdateOwnedStarCount`/`UpdateOwnedTonCount` empty stubs; gifts/payments shipped but balance is stale. `Tdlib.java:9837,9841`.
- **`MessageStory` / `MessageGiftedTon` chat-list previews** still return `UnsupportedMessage` though bubbles render. `ContentPreview.java:1614,1637`.
- **Live accent-color / emoji-status redraw** — data stored, `UpdateChatAccentColors`/`UpdateChatEmojiStatus` not consumed. `MessagesController.java:564`, `ChatListener.java:35`.
- **Unread poll-vote badges** — `UpdateChatUnreadPollVoteCount` → `break`; `ForumTopic.unreadPollVoteCount` plumbed, no badge.
- **Unconfirmed-session security warning** — `UpdateUnconfirmedSession` stub. `Tdlib.java:9200`.

**Medium (M):**
- **Message effects** (animated send effects + picker) — `UpdateAvailableMessageEffects`/`GetMessageEffect` unused.
- **Fact checks** — `message.factCheck` ignored, `SetMessageFactCheck` unused.
- **Voice-to-text** — `RecognizeSpeech`/`RateSpeechRecognition` unused (high-demand Premium).
- **AI message summaries** — `summaryLanguageCode` ignored, `SummarizeMessage` unused.
- **Named peer-set chat theme** — `Chat.theme` stored, `onChatThemeChanged` TODO no-op (wallpaper already live). `MessagesController.java:554`.
- **Mini-app prepared/shared message** — `web_app_send_prepared_message`/`web_app_share_message` stubbed though `GetPreparedInlineMessage`+`SendInlineQueryResultMessage` now exist. `WebAppProxy.java:565`.
- **OAuth login approval**, **Downloads manager**, **Polls v2** (added-option metadata, vote-restriction reason), **Quick replies**, **Group-call invite card** (needs group calls).

**Service-message placeholders → red "unsupported" bubbles (each S):** `MessageChatSetBackground`, `MessageSuggestProfilePhoto`/`Birthdate`, `MessagePaymentSuccessfulBot`, `MessagePaidMessagesRefunded`/`PriceChanged`, `MessageGiveawayPrizeStars`, `MessageChecklistTasksAdded`/`Done`, `MessagePollOptionAdded`/`Deleted`, `MessagePassportDataSent`, owner-changed/managed-bot/protected-content toggles. All at `TGMessage.java:8456-8510`. Knock out in one pass.

**Bubble renders as placeholder (M/L):** `MessagePaidMedia` (blurred + unlock-for-Stars), `MessageChecklist` (interactive tasks), `MessageGroupCall` (join card), suggested-post cards, `MessageStakeDice`.

**Large absent subsystems (weeks; lower priority):** **Group calls / video chats / live streams** (XL — `GroupCallListener` has zero implementers; gates the call-invite card + live-story playback + chat-in-calls). **Live stories** (`StoryContentLive` falls through the viewer switch). **Telegram Business suite**, **Chat boosts**, **Channel/Star/TON monetization stats**, **Saved-Messages topics**, **channel direct-message topics**, **sticker-pack authoring**, **managed/guard bots**, **web-browser-settings sync**.

---

## Design / UX — toward "better than official"

**Stories is the worst offender** for non-native UX:
- Viewers list shown as a **Toast** of "• Name" lines (`StoryViewController.java:1431`) — should be a viewers sheet with avatars/reactions.
- Statistics shown as an **AlertDialog text dump** that literally prints `jsonData.length()` (`:1990`) — route to a real chart controller.
- **4 raw `android.app.AlertDialog`s** (report, edit-caption, statistics, create-album) ignore the theme (`:1713,1835,2010,2114`) — use TGX input popups / controllers.
- Quick-reactions row is raw `TextView`s with a hardcoded `"✕"` glyph and `0xFFFF6666` color, no dark-mode (`:2279`).
- Nearly all feedback is **toasts**, not anchored tooltips (`context().tooltipManager()`).

**Cross-cutting:**
- **`needAsynchronousAnimation()` is never resolved** in the 5 Gift controllers + others — they wait the full framework timeout (0.5–2s) before appearing, feeling like a hang. Call `executeScheduledAnimation()` in the data-loaded callback (the standard pattern). Highest perceived-perf win.
- **Toasts for primary feedback & empty states** across Gifts + Stories — convert to tooltips / in-list empty cells.
- **Text-blob "detail" sheets** — gift details (`GiftsController.java:401`) and forum icon picker (`ForumTopicsController.java:1158`, emoji-char list) should be visual (GiftView-backed sheet; rendered custom-emoji grid).
- **`notifyDataSetChanged()`** for incremental updates (story bar, forum list, tag bar) causes flicker — diff/range-notify instead.
- Transaction rows lack counterparty **avatars** and aren't **tappable** (`StarTransactionsController.java:133`).
- Scattered hardcoded strings (`amount + " • " + date`, `+ ": "`) and a few non-`Lang` English literals.

**Already well-designed (the model to follow):** `widget/GiftView.java` (hoisted paints, ComplexReceiver, Screen.dp, theme colors — exemplary); the gift header views (cached RadialGradient); `Star/TonTransactionsController` structure (ListItem/SettingsAdapter, typed `Lang` switches); forum thread-safety (`copyForumTopics`, dataLock discipline); the Google Play `BillingManager` (well-engineered, just not wired to the Premium screen); mini-app same-origin + secure-storage IV handling.

---

## Recommended execution order

1. **P0 batch** — profile-note crash (#1) + read-only (#2); decide paid-reactions (#3 build-or-delete); story caption (#4) + progress-restart (#5); mini-app security trio (#6 escaping, #7 location consent, #8 plaintext).
2. **P1 correctness sweep** — attach-while-typing text loss; cross-chat quote highlight + animator leak; playback add/remove swap; forum unread-count authority; auction snapshot race; saved-tags trio; payment order-info/price + Premium store wiring + star-tx pagination.
3. **Cheap TDLib wins** — Stars/TON balance live; story/gifted-TON previews; live accent/emoji-status; the service-message placeholder batch.
4. **Design pass** — resolve `needAsynchronousAnimation` everywhere; stories toasts/AlertDialogs → native; gift/forum visual pickers; transaction avatars.
5. **Medium features** — voice-to-text, message effects, fact checks, AI summaries, mini-app prepared messages, paid-media + checklist bubbles.
6. **Strategic (later)** — group calls (XL, unblocks several), business suite, boosts, monetization.
