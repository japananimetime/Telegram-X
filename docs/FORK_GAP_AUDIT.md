# Telegram X Fork — Gap & Correctness Audit

Date: 2026-06-12 · Branch: `feature/rich-messages` · TDLib: master `tdlib/td@d6debbb` (1.8.65, layer 227)

Compiled from four parallel audits (API coverage, forums, mini apps, features). Items are tagged with effort **S** (≤1 day) / **M** (days) / **L** (weeks) and reference `file:line` evidence. "Done" items below were fixed during the TDLib upgrade work; everything else is open.

---

## P0 — Regressions & crashes from our own upgrade (fix first)

- [x] **9 new message types → red error bubble + possible chat-list crash.** `MessageStakeDice`, `MessageChatOwnerChanged/Left`, `MessageManagedBotCreated`, `MessageChatHasProtectedContentToggled/DisableRequested`, `MessagePollOptionAdded/Deleted`, `MessageUpgradedGiftPurchaseOfferRejected` were missing from `TGMessage.valueOf` and threw in `ContentPreview` (reachable from chat-list rendering). **Fixed** in commit `7926f673d` — routed to the unsupported placeholder. (S)
- [x] **Mini app phone-number privacy leak.** Now shares the contact via `SharePhoneNumber` and emits only the status. **Fixed** `fdb4677de`. (S) — security
- [x] **Mini app clipboard read is unconditional.** Gated on `Tdlib.isTrustedMiniAppBot`. **Fixed** `fdb4677de`. (S) — security
- [x] **Mini app "secure" storage plaintext + cross-account.** AES-256-GCM Keystore encryption (`WebAppSecureStorage`) + account-namespaced prefs. **Fixed** `fdb4677de`. (M) — security
- [x] **`requireSameOrigin` not enforced.** Plumbed into `Args` from all 5 launch sites; `WebAppProxy` drops cross-origin `postEvent`. **Fixed** `fdb4677de`. (S+M) — security
  - Note: top-level origin check only; iframe-level isolation (androidx `WebViewCompat.addWebMessageListener`) still open as a hardening follow-up.

## P1 — High-visibility correctness / parity

- [x] **`updateForumTopic` drops the new `unreadPollVoteCount` field.** Cache copy now carries it. **Fixed** (listener-param plumbing deferred until a poll-vote badge consumes it). (S)
- [x] **Mini app viewport/safe-area emitted in device px, not CSS px.** Now converted via `toCssPx` in viewport + both safe-area senders. **Fixed**. (S)
- [x] **Mini app `WebAppOpenMode` fullscreen ignored.** Fullscreen-on-open honored in `onWebAppReady`. **Fixed** (compact half-sheet still open). (M)
- [x] **Mini app secondary button left/right overlap** (fixed: half-width side-by-side) (both MATCH_PARENT). `WebAppController.java:676`. (S)
- [ ] **`web_app_send_prepared_message` / `web_app_share_message` hard-fail on a stale premise.** TDLib now has `GetPreparedInlineMessage`; implement fetch → filtered chat picker → `SendInlineQueryResultMessage`. `WebAppProxy.java:569`. (M)
- [x] **Inline keyboard `ButtonStyle` (Default/Primary/Danger/Success) + `iconCustomEmojiId` not rendered.** Styled bot buttons show as plain. `TGInlineKeyboard.java`. (M)
- [x] **No mention ("@") badge in forum topic rows.** Added `mentionCounter` with the `@` icon. **Fixed**. (S)
- [x] **Story deep links dead.** `InternalLinkTypeStory` now resolves the poster and opens the viewer. **Fixed** (LiveStory/StoryAlbum still need their own viewers). (M)
- [x] **Open story viewer never live-refreshes.** `StoryListener` (updateStory/Deleted/StealthMode/PostSucceeded/Failed) has zero implementors. (M)
- [x] **Shared story in chat (`MessageStory`) renders as a service text line**, not a tappable preview card. (M)
- [x] **Forum topic sort ignores draft-date influence in `order`** (TDLib contract: sort by `order` desc). `ForumTopicsController.java:1870`. (S)
- [~] **Peer-set chat themes/wallpapers/accent colors never render or live-update.** (background full+live; named-theme/accent TODO) `updateChatTheme/ChatBackground/ChatAccentColors/ChatEmojiStatus/ChatVideoChat/ChatViewAsTopics` dispatched to zero consumers. (M)

## P2 — Missing features officially supported (medium)

- [ ] **Checklists** render as "unsupported"; no create / mark-done. `TGMessage.java:8455`, functions `AddChecklistTasks`/`MarkChecklistTasksAsDone` unused. (M-L)
- [ ] **Paid media (`MessagePaidMedia`)** — common in channels — renders as unsupported placeholder. (M)
- [ ] **AI message summaries** (`SummarizeMessage`, `message.summaryLanguageCode`) absent — official June 2026 headline feature. (M)
- [ ] **Polls v2:** voter-added options (`AddPollOption`/`DeletePollOption`), option/poll media rendering, `author`/`additionDate`, vote-restriction UI, unread-poll-vote badges (`updateChatUnreadPollVoteCount`), `getPollVoteStatistics` graphs. `CreatePollController.java:687-691`, `TGMessagePoll.java`. (M total)
- [ ] **`messageGroupCall` invite card** — renders as unsupported instead of a Join card. `TGMessage.java:8452`. (S for the card; joining is XL, see P3)
- [ ] **Forum create-topic: no icon/color picker** (random color, name-only). `ForumTopicsController.java:1474-1524`. (M)
- [ ] **Forum edit-icon: 12-entry text list, no custom emoji, no color change.** `ForumTopicsController.java:1144`. (M-L)
- [ ] **Hide/unhide General topic** (`ToggleGeneralForumTopicIsHidden` unused); hidden General still shown in list. (S)
- [ ] **Per-topic notification settings** (custom sound/preview/mute-until) + exceptions UI — only mute presets today. `SettingsNotificationController` has zero topic references. (M)
- [ ] **Forum topic context menu** lacks Mark-as-read, Copy/Share topic link (`GetForumTopicLink`), Delete-from-tabs. (S each)
- [ ] **Bot-DM topics** — the topic family now extends to bot chats (`UserTypeBot.hasTopics/allowsUsersToCreateTopics`); `isForum()` is supergroup-only, so bot DMs with topics open flat. (L)
- [ ] **ShareController topic picker** silently targets General on dismissal; 100-topic cap; plain rows. `ShareController.java:2087`. (S/M)
- [ ] **Server-side topic name search** — `GetforumTopics` query param always `""`; >100-topic forums get incomplete client-side results. (S/M)
- [ ] **Mini app gaps:** `web_app_verify_age` unhandled, home-screen shortcut callback/avatar/startapp param, location per-bot consent + active fix, emoji-status confirm dialog + persistence, biometry grant persistence, fullscreen safe-area resend, visibility on activity pause, spec error codes, U+2028/2029 JSON escaping. (S-M each — see mini-apps audit)
- [ ] **OAuth requests** (`UpdateNewOauthRequest`, `AcceptOauthRequest`, `InternalLinkTypeOauth`) silently dropped — third-party Telegram login fails. (M)
- [ ] **Fact checks** (`UpdateMessageFactCheck` TODO, `SetMessageFactCheck` unused). (M)
- [ ] **Voice-to-text** (`RecognizeSpeech`, `RateSpeechRecognition`) absent. (M)
- [ ] **Star/TON balance & wallet** — `UpdateOwnedStarCount/TonCount` are TODO; revenue updates dispatched to zero consumers; `MessageGiftedTon` unsupported. (M)
- [ ] **Premium features page / in-app purchase** — `PremiumFeaturesPage`/`RestorePurchases` links → tooltip; `GetPremiumFeatures`/store functions unused. (M)
- [ ] **Suggested posts (channel paid posts)** — 5 message types unsupported, approve/decline unused. (M)
- [ ] **Premium AI composing** (`ComposeTextWithAi`/`FixTextWithAi`/text composition styles). (M-L)
- [ ] **Downloads manager screen** — update plumbing exists, zero functions called. (M)
- [ ] **Message effects** (`UpdateAvailableMessageEffects` TODO, `GetMessageEffect` unused) — effects invisible. (M)
- [ ] **Unconfirmed session security warning** (`UpdateUnconfirmedSession` TODO) never surfaced. (S)
- [ ] **Birthday banners** (`UpdateContactCloseBirthdays` dispatched to zero consumers). (S)

## P3 — Large absent subsystems (weeks each)

- [ ] **Group calls / video chats / live streams** (XL) — `voip/` is 1:1 only; ~45 functions unused, all 9 group-call updates dead-end, blocks live stories + chat-in-calls + call-invite cards.
- [ ] **Live stories** (L) — depends on group-call infra. `StoryContentLive` not cased; start/join/RTMP/donor functions unused.
- [ ] **Gift economy** (L) — auctions, crafting, send/upgrade/resale/wear, received-gifts profile tab, collections; ~70 functions unused; `MessageUpgradedGift`/`RefundedUpgradedGift` unsupported.
- [ ] **Telegram Business suite** (L) — 38 functions unused; away/greeting, hours, location, start page, connected bot, business messages, quick replies (handlers empty).
- [ ] **Channel direct-messages topics** (L) — `updateDirectMessagesChatTopic` stubbed; admin per-sender topic list absent.
- [ ] **Saved Messages topics** (L) — empty update handlers (no STUB comment — should be labeled); per-chat Saved Messages organization absent (tags exist).
- [ ] **Chat boosts** (L) — boost links tooltip, 10 functions unused.
- [ ] **Passkeys login** (L) — 6 functions unused; needs Android Credential Manager.
- [ ] **Sticker pack authoring** (L) — ~15 functions unused.
- [ ] **Channel/star/TON monetization & affiliate programs** (L) — revenue stats, withdrawals, subscriptions, affiliate CRUD.
- [ ] **Quick replies** (M) — 11 functions unused, 4 empty update handlers.
- [ ] **Managed bots & guard bots** (M-L) — `CreateBot`, access settings, `KeyboardButtonTypeRequestManagedBot` (CommandKeyboardLayout silently no-ops it), guard-bot join flow (`ChatJoinResultGuardBotApprovalRequired` → request-sent fallback).
- [ ] **Web browser settings sync** (M) — `ChangeWebBrowserSettings`/`GetLinkWebBrowserType` unused; in-app vs external choice not server-synced.

---

## Migration TODO markers left in code (from the upgrade)

| Marker | Location | Note |
|---|---|---|
| `TODO(td)` customTitle→tag | `data/TD.java:1881` | `needUpgradeToSupergroup` can't check title from bare status |
| `TODO(td)` requireSameOrigin | `TGInlineKeyboard.java:1163`, `MessagesController.java:7778/8160` | see P0 same-origin |
| `TODO(rich-media)` | `PageBlock.java:517/534/547`, `TGMessageRich.java:216`, `MediaItem.java:986` | collage grid, audio/voice inline view, embedded WebView, RichTextIcon key offsets, media-viewer support |
| `TODO(rich-theming)` / `TODO(rich-rtl)` | `TGMessageRich.java:48/50` | bubble-specific palettes; per-paragraph RTL |
| `TODO(td)` rich preview | `MediaPreview.java:511` | dedicated rich-message media preview |

## Notes

- Both submodules (`tdlib`, `vkryl/td`) carry the upgrade as local working-tree modifications — they can't be pushed to the upstream TGX-Android repos. Same as before, now on TDLib master.
- `:app:generateResourcesAndThemes` regenerates `TdCompileAssert.kt` / `TdUnsupported.kt` / `TdEqualsTo.kt` in `vkryl/td` from `TdApi.java` on every build — never hand-edit those; add types to `buildSrc/.../config/TdlibEqualTypes.kt` instead.
