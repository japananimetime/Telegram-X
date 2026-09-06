# Architecture Map

Everything under `app/src/main/java/org/thunderdog/challegram/` (≈1,150 Java files, 10 Kotlin). Upstream's [[GUIDE]] explains the frameworks in depth; this is the orientation map.

## Packages
| Package | Purpose | Fork hotspots |
|---|---|---|
| `telegram/` | TDLib wrapper `Tdlib.java` (12.7k lines), `TdlibManager` (accounts), `TdlibUi` (8k, "open X" helpers + deep links), listeners, caches, `TdlibNotificationHelper`, `TdlibSettingsManager` | forum topic cache, `GroupCallManager`, speech recognition providers, `SavedMessagesListener`, `QuickReplyListener` |
| `ui/` | one `*Controller` per screen | 45 new controllers (list below) |
| `navigation/` | `NavigationController`, `ViewController`, `HeaderView` — custom stack, not Fragments | |
| `data/` | message models `TGMessage*`, `TGChat`, `TGReaction*`, `TD.java` helpers | `TGMessageRich`, `TGMessageService` (service texts), `TGMessageInvoice` |
| `component/chat/` | the chat screen guts: `MessagesLoader`, `MessagesSearchManager`, `ChatHeaderView`, `InputView`, `MessageView` | topic-aware loading/search/header |
| `component/dialogs/`, `widget/` | chat list rows (`ChatView`, `BetterChatView`), `AvatarView`, `StoryBarView`, `GiftView`, `voip/` video views | |
| `voip/` | 1:1 and group call engine bridge (`TgCallsController`, `GroupCallInstance`, `VideoCameraCapturer`, package `org.telegram.messenger.voip` required by the native ABI) | all fork-added |
| `billing/` | `BillingManager` (Play Billing for Premium / Stars) | fork-added |
| `theme/`, `tool/`, `util/` | colors, `Screen.dp`, `Lang`, text rendering `util/text/Text.java` | `EmojiBidiLegacy.kt`, `Emojis.kt`, `TopicIconModifier` |
| `app/jni/` | native glue: `tgvoip.cpp`, `group_call.cpp`, `video_capture_context.h`, `BuildTgCalls.cmake`, `patches/` | |

## Navigation in one paragraph
Single `BaseActivity` hosts a `NavigationController`. Every screen is a `ViewController<Args>` created with `(context, tdlib)`, given `setArguments(...)`, pushed with `navigationController.navigateTo(...)`. Lifecycle: `onCreateView()` once, `onFocus()/onBlur()`, `needAsynchronousAnimation()` + `executeScheduledAnimation()` to hold the transition until data is loaded (forgetting the second call = screens that "hang" for the framework timeout), `destroy()` for cleanup and listener removal. Screen ids live in `res/values/ids.xml` as `controller_*`. List screens extend `RecyclerViewController`/`SettingsAdapter` with `ListItem`s; tabs use `ViewPagerController`.

## TDLib access
- `tdlib.client().send(new TdApi.X(...), result -> ...)` or `tdlib.send(fn, (result, error) -> {})`.
- Updates fan out through `TdlibListeners` to interfaces in `telegram/` (`ChatListener`, `MessageListener`, `ForumTopicInfoListener`, `StoryListener`, ...). A screen implements the interface and subscribes in `onCreateView`, unsubscribes in `destroy`.
- `TdApi` is the **rich-messages** custom build (see [[Repositories & Branches]]); message topics are `TdApi.MessageTopic` (forum/thread/saved/direct), matched with `Td.matchesTopic`.
- Unhandled update constructors fall into a `default`/cast branch in `Tdlib.java`: a new TDLib update type once caused a `ClassCastException` crash (REAUDIT item 1). When bumping TDLib, grep the update switch first.

## What the fork added, by feature (files under `ui/` unless noted)
- **Forum topics**: `ForumTopicsController`, `ForumTopicTabsController`, `ForumTopicView`, `util/TopicIconModifier`, `telegram/ForumTopicInfoListener` — details in [[Forum Topics]]
- **Stories**: `StoryViewController` (2.5k), `StoryPreviewController`, `StoryColorPickerController`, `SettingsStoriesController`, `widget/StoryBarView`, `AvatarView` rings
- **Mini apps**: `WebAppController` (2.9k) + `WebAppProxy` JS bridge, `WebAppSecureStorage`
- **Gifts / Stars / TON**: `GiftsController`, `GiftPickerController`, `GiftAuction(s)Controller`, `GiftCraftController`, `GiftResaleController`, `GiftSettingsController`, `UpgradedGiftController`, `SettingsStarsController`, `StarTransactionsController`, `TonTransactionsController`, `StarRevenueController`, `widget/GiftView`
- **Payments / Premium**: `PaymentFormController`, `PaymentReceiptController`, `SettingsPremiumController`, `billing/BillingManager`
- **Calls**: `GroupCallController`, `RtmpUrlController`, `telegram/GroupCallManager`, `voip/*`, `widget/voip/*`, `app/jni/*`
- **Business**: `SettingsBusinessController` + `Business{Away,Greeting,OpeningHours,Location,StartPage,ChatLinks,ConnectedBot,Recipients}Controller`
- **Parity extras**: `ChatBoostController`, `DownloadsController`, `QuickRepliesController`, `SavedMessagesTopicsController`, `SavedMessagesTagEditController`, `StickerSetsController`, `SettingsCommunityController`, `SettingsMessagesFilter*Controller`
- **Voice transcription**: `SettingsVoiceTranscriptionController`, `EditTranscriptionProviderController`, `telegram/SpeechRecognition*` (TDLib, local, HTTP providers)
- **Rich messages**: `data/TGMessageRich`, `PageBlock`, jlatexmath for math — tracker in [[RICH_TEXT_AUDIT_2026-06-15]]

Most-modified upstream files (diff vs upstream): `Tdlib.java` (+1.6k), `TdlibUi.java` (+1.3k), `MessagesController.java` (+1k), `TGMessageService.java`. Expect merge conflicts there when touching upstream.

## Conventions that bite
- Two-space indent, space before the parameter paren: `void method () {`.
- Kotlin only in `me.vkryl.*`.
- `Theme.getColor(R.id.theme_color_*)`, never hardcoded colors (the stories viewer violates this, see [[Known Issues & Open Items]]).
- `Lang.getString(R.string.X)` for all text; the launcher label `AppName` is generated from `app.name`, do not add it to `strings.xml`.
- Never allocate in `onDraw`; hoist `Paint`s (`GiftView` is the model to copy).
- Use `tdlib.currentTimeMillis()` for server-relative times, not the device clock.
