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
- **Slice 1 — Render gift message types** ✅ DONE (`f025079a3`). `MessageUpgradedGift`, `MessageRefundedUpgradedGift`, `MessageUpgradedGiftPurchaseOffer`, `…Rejected`, `MessageGiftedTon` → cards in `TGMessage.valueOf`; real `ContentPreview`. `TGMessageGiveawayBase.ContentGiftCard` (cached gradient + receiver sticker), `GiftRarityUtil`.
- **Slice 2 — Received-gifts profile surface** ✅ DONE (`66cb41a87`). `GiftsController` (grid) + `GiftView` cell; profile "Gifts" row when `giftCount>0`; detail sheet with Save/Unsave + Pin/Unpin; closes the slice-1 View-Gift TODO via `GetReceivedGift`. (Used a profile row, not a `SharedBaseController` tab — the tab system is media-coupled.)
- **Slice 3 — Send gift + settings** ✅ DONE (`91634872e`). `GetAvailableGifts`/`SendGift`/`CanSendGift`/`SetGiftSettings`/`SellGift`; `GiftPickerController` + `GiftSettingsController`; "Send a gift" entry in profile overflow. (Real star purchase on insufficient balance still stubbed — `SettingsStarsController.purchaseStars`.)
- **Slice 4 — Upgrade / transfer** ✅ DONE (`9bd2a8d06`). `GetGiftUpgradePreview`/`UpgradeGift`/`TransferGift`/`GetUpgradedGift`/`SetUpgradedGiftColors`/`DropGiftOriginalDetails`; `UpgradedGiftHeaderView` hero card + `UpgradedGiftController` detail screen; `InternalLinkTypeUpgradedGift` routing. (Export-to-TON stubbed — needs 2FA password flow.)
- **Slice 5 — Resale + purchase offers.** `SearchGiftsForResale`/`SendResoldGift`/`SetGiftResalePrice`/`SendGiftPurchaseOffer`/`ProcessGiftPurchaseOffer`.
- **Slice 6 — Auctions.** `GiftAuctionState`/`PlaceGiftAuctionBid`/`IncreaseGiftAuctionBid`/`GetGiftAuctionState`; `UpdateGiftAuctionState`/`UpdateActiveGiftAuctions`; `InternalLinkTypeGiftAuction`.
- **Slice 7 — Collections.** `GetGiftCollections` + collection CRUD; `InternalLinkTypeGiftCollection`.
- **Slice 8 — Crafting.** `GetGiftsForCrafting`/`CraftGift`.
- **Slice 9 — Transaction history glue** (render-only). Fold gift `StarTransactionType*`/`TonTransactionType*` into the Stars/TON transaction screens.

## Dependency order
Rendering foundation (Gift/UpgradedGift sub-objects) → message contents → received gifts (produces `receivedGiftId`, the handle every mutating op needs) → upgrade/transfer → resale → auctions/crafting/collections. Payment glue is render-only.
