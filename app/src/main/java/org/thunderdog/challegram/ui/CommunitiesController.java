/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

/**
 * Lists the communities joined by the current user.
 *
 * FORK-TDLIB: getJoinedCommunities comes from tdlib/source/patches/td-communities.patch;
 * switch to the official method once TDLib exposes it.
 */
public class CommunitiesController extends RecyclerViewController<Void> implements View.OnClickListener, TdlibCache.CommunityDataChangeListener {
  private SettingsAdapter adapter;
  private long[] communityIds;

  public CommunitiesController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_communities;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Communities);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    buildCells();
    recyclerView.setAdapter(adapter);
    tdlib.send(new TdApi.GetJoinedCommunities(), (communities, error) -> {
      if (error != null) {
        UI.showError(error);
        communities = new TdApi.Communities(new long[0]);
      }
      final long[] ids = communities.communityIds;
      runOnUiThreadOptional(() -> setCommunityIds(ids));
    });
  }

  private void setCommunityIds (long[] communityIds) {
    if (this.communityIds != null) {
      for (long communityId : this.communityIds) {
        tdlib.cache().unsubscribeFromCommunityUpdates(communityId, this);
      }
    }
    this.communityIds = communityIds;
    for (long communityId : communityIds) {
      tdlib.cache().subscribeToCommunityUpdates(communityId, this);
    }
    buildCells();
  }

  @Override
  public void destroy () {
    super.destroy();
    if (communityIds != null) {
      for (long communityId : communityIds) {
        tdlib.cache().unsubscribeFromCommunityUpdates(communityId, this);
      }
    }
  }

  @Override
  public void onCommunityUpdated (TdApi.Community community) {
    runOnUiThreadOptional(this::buildCells);
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (communityIds == null) {
      items.add(new ListItem(ListItem.TYPE_PROGRESS));
    } else if (communityIds.length == 0) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CommunitiesEmpty));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (long communityId : communityIds) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_community)
          .setData(CommunityController.newCommunityWrapper(tdlib, communityId, tdlib.cache().community(communityId)))
          .setLongId(communityId));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_community) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getLongId() != 0) {
        CommunityController c = new CommunityController(context, tdlib);
        c.setArguments(new CommunityController.Args(item.getLongId()));
        navigateTo(c);
      }
    }
  }
}
