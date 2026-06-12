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
- **Slice 5 — Resale + purchase offers** ✅ DONE (`e9f97db92`). `SearchGiftsForResale`/`SendResoldGift`/`SetGiftResalePrice`/`SendGiftPurchaseOffer`/`ProcessGiftPurchaseOffer`; `GiftResaleController` browser, resale actions on `UpgradedGiftController`, wired the slice-1 offer card accept/reject. Made `TGMessageGiveawayBase` button optional (`hasButton()`). (Stars top-up still stubbed; offer duration fixed 1 day; attribute filter UI not built.)
- **Slice 6 — Auctions** ✅ DONE (`353e065a9`). `GetGiftAuctionState`/`PlaceGiftAuctionBid`/`IncreaseGiftAuctionBid`; live `UpdateGiftAuctionState`/`UpdateActiveGiftAuctions` via a feature-scoped `Tdlib.GiftAuctionListener` + cached snapshot; `GiftAuctionsController` list + `GiftAuctionController` detail/bidding + `GiftAuctionHeaderView`; `InternalLinkTypeGiftAuction`. (Bid-for-self only; Stars top-up stubbed.)
- **Slice 7 — Collections** ✅ DONE (`727a64b0d`). `GetGiftCollections` + `Create`/`SetName`/`Delete`/`Add`/`Remove` CRUD; collections chip strip on `GiftsController` with `collectionId` filtering; `InternalLinkTypeGiftCollection` via `SearchPublicChat`. (Reorder funcs + multi-select seeding left as commented TODO.)
- **Slice 8 — Crafting** ✅ DONE (`f829d7c00`). `GetGiftsForCrafting`/`CraftGift` (all 4 results); `GiftCraftController` multi-select picker with primary-number ordering + honest odds hint; `GiftView.setCheckedState`; "Craft Gift" action on `UpgradedGiftController`. (No fabricated per-attribute percentages.)
- **Slice 9 — Transaction history glue** ✅ DONE (`c4479411b`). The Stars screen already labelled every gift `StarTransactionType*`; added `TonTransactionsController` (`GetTonTransactions`, all nine `TonTransactionType*` incl. gift offer/purchase/sale) + `GiftRarityUtil.formatTonNano`, reachable from `SettingsStarsController`.

---

## Status: all 9 slices complete ✅
Full Stars/TON gift economy shipped on `feature/rich-messages`: render (1) → received-gifts surface (2) → send + settings (3) → upgrade/transfer (4) → resale + offers (5) → auctions (6) → collections (7) → crafting (8) → TON history (9). Each slice is its own compiling, pushed commit.

### Known stubs / follow-ups (all clearly commented in code)
- ~~**Stars top-up on insufficient balance**~~ ✅ DONE. `SettingsStarsController.purchaseStars` now opens a real Telegram payment form (`InputInvoiceTelegram(TelegramPaymentPurposeStars)` → `GetPaymentForm` → `PaymentFormController` card flow). A reusable `TdlibUi.showStarsBalanceLowPrompt(...)` detects `BALANCE_TOO_LOW` and offers an in-app top-up; wired into send-gift, upgrade, resale buy, and auction place/raise bid. (Card completion still depends on the fork's pre-existing `PaymentFormController` / payment-provider setup.)
- **Export-to-TON** (upgraded gift → NFT) — needs the 2FA password flow; stubbed in `UpgradedGiftController`.
- **Auction bids** are bid-for-self only (no recipient/text picker); fixed 1-day offer duration; `paidMessageStarCount=0`.
- **Collections**: reorder ✅ DONE — `ReorderGiftCollections` (Move left/right in the manage menu) and `ReorderGiftCollectionGifts` (Move left/right in the per-gift sheet, gated on `endReached` so the full order is known); both optimistic with revert-on-error. Multi-select seeding of a new collection is still unwired.
- **Crafting**: no per-attribute persistence percentages (API doesn't label backdrop vs symbol probabilities) — generic hint shown.
- **Resale attribute filter UI** ✅ DONE — overflow menu on `GiftResaleController` with sort orders (price / number / listing date) and model/symbol/backdrop filters, populated from the facet counts in the first unfiltered `SearchGiftsForResale` response; picking a facet sets the matching `UpgradedGiftAttributeId` and reloads.

## Dependency order
Rendering foundation (Gift/UpgradedGift sub-objects) → message contents → received gifts (produces `receivedGiftId`, the handle every mutating op needs) → upgrade/transfer → resale → auctions/crafting/collections. Payment glue is render-only.
