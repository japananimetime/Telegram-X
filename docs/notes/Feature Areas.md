# Feature Areas

One row per fork feature. "Open" lists the items still flagged by the audits ([[FORK_REAUDIT_2026-06-13]], [[FORK_QUALITY_AUDIT]], [[FORK_GAP_AUDIT]]) or the handoffs. Everything on afc is compile-verified; the native-calls and push work of 2026-06-15 was **not device-tested** at handoff time.

| Feature | Branch | Key code | State / open |
|---|---|---|---|
| Forum topics | `forum-topics-implementation` | see [[Forum Topics]] | done; flicker on refresh, icon picker, bot-DM topics open |
| Stories | `feature/stories`, `stories-implementation` | `StoryViewController`, `StoryBarView`, `AvatarView`, `SettingsStoriesController`, `StoryColorPickerController` | works; design debt: viewers list is a Toast, statistics is a text dump, 4 raw `AlertDialog`s, hardcoded colors, quick reactions as raw `TextView`s; live stories not supported |
| Mini apps | `feature/mini-apps`, `mini-apps-hardened` | `WebAppController`, `WebAppProxy`, `WebAppSecureStorage` | hardened (same-origin, AES storage, biometry persistence, SSRF/path fixes); open: `postEvent` origin TOCTOU (needs `WebMessageListener`), `web_app_send_prepared_message`, compact half-sheet mode, `web_app_verify_age` |
| Gifts / Stars / TON | `feature/gifts`, `feature/stars` | `Gifts*Controller`, `GiftView`, `Star*Controller`, `Ton*Controller`, `Tdlib` caches | auctions, crafting, resale, wear, revenue dashboard done; `needAsynchronousAnimation` never resolved in gift controllers (perceived hang); transaction rows lack avatars/taps |
| Payments / Premium | `feature/premium-billing` | `PaymentFormController`, `PaymentReceiptController`, `BillingManager`, `SettingsPremiumController` | tips, order info, shipping, receipts, Stars store via Play done; cosmetic: keyboard types on order-info fields |
| Calls | `feature/native-video-calls` | `voip/*`, `GroupCallController`, `GroupCallManager`, `app/jni/*` | 1:1 video, group video, screen share (server-side presentation) implemented; **needs two-device verification**; live-stream listening, conference links, participant audio levels remaining |
| Rich messages | `feature/rich-messages` | `TGMessageRich`, `PageBlock`, jlatexmath | tracker [[RICH_TEXT_AUDIT_2026-06-15]]; phases 1–4 mostly done |
| Quotes | `feature/quotes` | `TGMessage` quote path, `TGMessageText` | cross-chat quote highlight fixed; watch the highlight animator |
| Saved tags | `feature/saved-tags` | `SavedMessagesTagsBarView`, `TGReactions`, `MessagesController` | fixed receiver lifecycle / topic-scoped updates in audit pass |
| Saved Messages topics | parity | `SavedMessagesTopicsController`, `SavedMessagesListener` | list, pin, delete; opened chat scoped to topic |
| Profile notes | `feature/profile-notes` | profile UI | P0 crash fixed in quality audit |
| Playback speed | `feature/playback-speed` | `player/TGPlayerController` | listener add/remove swap fixed |
| Disposable voices | `feature/disposable-voices` | compose/send path | |
| Voice transcription | `feature/voice-transcription` | `SpeechRecognitionManager` + TDLib/local/HTTP providers, settings screens | |
| Reactions improvements | `feature/reactions-improvements` | `TGReactions`, paid reactions (`TdExt.kt`) | paid-reaction amount picker done |
| Business suite | parity | `SettingsBusinessController` + editors | editors for hours/greeting/away/start page/location/links/connected bot done |
| Quick replies / Downloads / Boosts / Sticker packs | parity | `QuickRepliesController`, `DownloadsController`, `ChatBoostController`, `StickerSetsController` | quick-reply create + "/" picker, boost chart, sticker emoji edit/reorder are follow-ups |
| Push (FCM) | `parity-fixes` | `PushProcessor`, `ForegroundService`, `PushHandler`, `app/src/google/*` | Android 12+ FGS-from-background fix ported; real `google-services.json`; **device-test pending** |
| Community / filters | `feature/community-features` | `SettingsCommunityController`, `SettingsMessagesFilter*` | |

## Stubbed update handlers (silently ignored)
`updateSpeechRecognitionTrial`, `updateDefaultBackground`, `UpdateNewOauthRequest` (third-party Telegram login fails), `UpdateStakeDiceState`, `UpdateTextCompositionStyles`, `UpdateWebBrowserSettings`, `UpdatePendingMessage`, `UpdateLiveStoryTopDonors`, `UpdateChatJoinResult`, `UpdateUnconfirmedSession` (security warning never shown), `UpdateContactCloseBirthdays`.

## Absent vs official (large)
Live stories viewer, conference calls, channel direct-message topics, passkeys login, managed/guard bots, AI summaries / AI compose, checklists, polls v2 extras, suggested posts, web-browser settings sync.
