# Gift Economy — Implementation Plan

Deep build of the Stars gift economy (chosen from the fork gap audit). TDLib master is integrated; all gift TdApi types/functions exist. Built in compiling, independently-committable slices.

## Reusable infrastructure (already in the fork)
- `data/TGMessageGiveawayBase.java` — gift-card scaffold: `GiftParticlesDrawable` background, `Content`/`ContentPart` builder (text, avatar chips, centered icon), inline ripple button.
- `data/TGMessageGiftRegular.java`, `data/TGMessageGift.java` — existing card renderers to mirror.
- `component/popups/GiftCodeController.java`, `widget/GiftHeaderView.java` — bottom-sheet / header templates.
- `component/sticker/TGStickerObj.java` + `ComplexReceiver`/`GifReceiver` — animated sticker (model/symbol) drawing.
- `UpgradedGiftBackdropColors{centerColor,edgeColor,symbolColor,textColor}` — backdrop gradient source.
- `ui/StarTransactionsController.java`, `ui/SettingsStarsController.java` — Stars screens (purchase is stubbed).

## Slices
- **Slice 1 — Render gift message types** (IN PROGRESS). `MessageUpgradedGift`, `MessageRefundedUpgradedGift`, `MessageUpgradedGiftPurchaseOffer`, `…Rejected`, `MessageGiftedTon` → cards in `TGMessage.valueOf`; real `ContentPreview`. Foundation: `UpgradedGift` + model/symbol/backdrop/rarity/origin.
- **Slice 2 — Received-gifts profile tab.** `GetReceivedGifts`/`GetReceivedGift`/`ToggleGiftIsSaved`/`SetPinnedGifts`; read `UserFullInfo.giftCount/giftSettings`, `SupergroupFullInfo.giftCount`; new `SharedBaseController` tab in `ProfileController.getControllers()`.
- **Slice 3 — Send gift + settings.** `GetAvailableGifts`/`SendGift`/`CanSendGift`/`SetGiftSettings`/`SellGift`; gift-picker controller; "Send a gift" entry in `ProfileController.showPrivateMore()`. Needs real star purchase (`SettingsStarsController.purchaseStars` is stubbed).
- **Slice 4 — Upgrade / transfer.** `GetGiftUpgradePreview`/`GetUpgradedGiftVariants`/`UpgradeGift`/`BuyGiftUpgrade`/`TransferGift`/`GetUpgradedGift`/`SetUpgradedGiftColors`/`DropGiftOriginalDetails`; upgraded-gift detail screen; `InternalLinkTypeUpgradedGift`.
- **Slice 5 — Resale + purchase offers.** `SearchGiftsForResale`/`SendResoldGift`/`SetGiftResalePrice`/`SendGiftPurchaseOffer`/`ProcessGiftPurchaseOffer`.
- **Slice 6 — Auctions.** `GiftAuctionState`/`PlaceGiftAuctionBid`/`IncreaseGiftAuctionBid`/`GetGiftAuctionState`; `UpdateGiftAuctionState`/`UpdateActiveGiftAuctions`; `InternalLinkTypeGiftAuction`.
- **Slice 7 — Collections.** `GetGiftCollections` + collection CRUD; `InternalLinkTypeGiftCollection`.
- **Slice 8 — Crafting.** `GetGiftsForCrafting`/`CraftGift`.
- **Slice 9 — Transaction history glue** (render-only). Fold gift `StarTransactionType*`/`TonTransactionType*` into the Stars/TON transaction screens.

## Dependency order
Rendering foundation (Gift/UpgradedGift sub-objects) → message contents → received gifts (produces `receivedGiftId`, the handle every mutating op needs) → upgrade/transfer → resale → auctions/crafting/collections. Payment glue is render-only.
