# Communities

Telegram **communities** group supergroups, channels and bots under one umbrella (a community is a channel-type object on the server). Branch `feature/communities`, on top of the [[Upstream Sync 2026-09]] core.

## TDLib support (and the fork patch)
Official TDLib `tdlib/td@42e6a52` exposes only `loadCommunityFullInfo`, `createCommunity`, `setCommunityName`, the `community`/`communityFullInfo` objects + updates, `supergroupFullInfo.community_id` / `userFullInfo.community_id` (bots), 3 service messages and `searchMessagesChatTypeFilterCommunity`. The server API (`telegram_api.tl`) already has everything else.

**Temporary patch** `tdlib/source/patches/td-communities.patch` (applied by `tdlib/source/build-patched.sh`, built into `japananimetime/tdlib`) adds, following TDLib's own `CommunityManager` style:

| td_api function | server method |
|---|---|
| `getJoinedCommunities` → `communities` | `communities.getJoinedCommunities` |
| `addCommunityChat`, `setCommunityChatIsHidden`, `removeCommunityChat` | `communities.togglePeerLink` (visible / hidden / deleted) |
| `getCommunityAddChatRequests` → `communityAddChatRequests` | `communities.getPeerLinkRequests` |
| `processCommunityAddChatRequest(s)` | `communities.toggle(All)PeerLinkRequestApproval` |
| `setCommunityMemberIsBanned` | `communities.toggleParticipantBanned` |

Every mutating call reloads the community full info, so `updateCommunityFullInfo` keeps `TdlibCache` current.

### Removing the patch when TDLib ships the API
1. Delete `tdlib/source/patches/td-communities.patch` (and `build-patched.sh` if nothing else needs it); upgrade TDLib normally.
2. `grep -rn "FORK-TDLIB" app/src` lists every app call site; rename to the official functions/objects (the compiler will flag all of them once `TdApi.java` no longer has ours).
3. Not in the patch (still unsupported): community photo/admins editing, `communities.getParticipantJoinedChats`, collapsing a community in the chat list (`dialogCommunity`), leaving a community.

## App code
| File | Role |
|---|---|
| `ui/CommunityController.java` | community screen: photo/name/role, rename, admin counters, chat list (tap = open, long-press = hide/show/remove for chat-list editors), entry to requests |
| `ui/CommunitiesController.java` | "My communities" (drawer → Communities), `getJoinedCommunities` |
| `ui/CommunityAddChatRequestsController.java` | pending add-chat requests: approve/decline, all at once |
| `ui/ProfileController.java` | "Community" row (supergroups, channels, bots in a community); ⋮ Create community / Add to community / Remove from community for owned chats & editable bots |
| `ui/SharedMembersController.java` | member long-press → "Ban from community" when the chat is in a community and you have `can_ban_members` |
| `telegram/TdlibCache.java` | upstream cache + fork `subscribeToCommunityUpdates` / `unsubscribeFromCommunityUpdates` |
| `data/DoubleTextWrapper.java` | `setPhoto(ChatPhotoInfo)` for non-chat avatars (community photo) |

Gotchas: `createCommunity` on a **basic group upgrades it to a supergroup** (the create dialog warns). Adding a chat you can't approve yourself becomes a request to the community admins. There is no server call to list community members/banned users, so bans are issued from a chat's member list.
