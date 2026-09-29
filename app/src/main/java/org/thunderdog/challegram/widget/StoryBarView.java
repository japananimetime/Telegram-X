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
 *
 * File created on 21/12/2024
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class StoryBarView extends RecyclerView {

  private static final int ITEM_SIZE_DP = 72;
  private static final int AVATAR_SIZE_DP = 52;
  private static final int RING_WIDTH_DP = 2;
  private static final int RING_GAP_DP = 2;
  private static final int BAR_HEIGHT_DP = 100;

  private static final int VIEW_TYPE_ADD_STORY = 0;
  private static final int VIEW_TYPE_STORY = 1;

  private final Tdlib tdlib;
  private final StoryBarAdapter adapter;
  private final List<TdApi.ChatActiveStories> activeStoriesList = new ArrayList<>();
  private @Nullable StoryClickListener clickListener;
  private @Nullable VisibilityChangeListener visibilityChangeListener;
  private boolean canPostStory = false;

  /**
   * Updates colors when theme changes
   */
  public void updateColors () {
    setBackgroundColor(Theme.fillingColor());
    // Refresh all child views by notifying adapter
    adapter.notifyDataSetChanged();
  }

  public interface StoryClickListener {
    void onStoryClick (long chatId, int storyId, List<TdApi.ChatActiveStories> allStories, int position);
    void onAddStoryClick ();
  }

  public interface VisibilityChangeListener {
    void onStoryBarVisibilityChanged (boolean visible);
  }

  public StoryBarView (@NonNull Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;

    setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    setOverScrollMode(OVER_SCROLL_NEVER);
    setClipToPadding(false);
    setPadding(Screen.dp(8), Screen.dp(8), Screen.dp(8), Screen.dp(8));

    // Set solid background to prevent chat list from showing through
    setBackgroundColor(Theme.fillingColor());

    // Start hidden: updateVisibility() reveals the overlay only once there is content to show,
    // otherwise an empty filled bar flashes over the first chat before stories load.
    setVisibility(GONE);

    adapter = new StoryBarAdapter();
    setAdapter(adapter);
  }

  public void setClickListener (@Nullable StoryClickListener listener) {
    this.clickListener = listener;
  }

  public void setVisibilityChangeListener (@Nullable VisibilityChangeListener listener) {
    this.visibilityChangeListener = listener;
  }

  public void setActiveStories (List<TdApi.ChatActiveStories> stories) {
    final List<TdApi.ChatActiveStories> oldList = new ArrayList<>(activeStoriesList);
    final List<TdApi.ChatActiveStories> newList = stories != null ? stories : new ArrayList<>();

    // This is called on every chat-list update, very often with an identical list. A blanket
    // notifyDataSetChanged() rebinds every avatar and re-loads its image, producing a visible
    // flicker each time. Diff against the previous list so only genuinely changed rows are rebound;
    // when nothing changed we skip the rebind (and the visibility/invalidate churn) entirely.
    if (storiesEqual(oldList, newList)) {
      return;
    }

    activeStoriesList.clear();
    activeStoriesList.addAll(newList);

    DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
      @Override public int getOldListSize () { return oldList.size(); }
      @Override public int getNewListSize () { return newList.size(); }

      @Override public boolean areItemsTheSame (int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).chatId == newList.get(newItemPosition).chatId;
      }

      @Override public boolean areContentsTheSame (int oldItemPosition, int newItemPosition) {
        return storyEntryEqual(oldList.get(oldItemPosition), newList.get(newItemPosition));
      }
    }, false);

    // The "add story" button occupies position 0 when present, so story rows are offset by one.
    final int headerOffset = hasAddButton() ? 1 : 0;
    diff.dispatchUpdatesTo(new ListUpdateCallback() {
      @Override public void onInserted (int position, int count) {
        adapter.notifyItemRangeInserted(position + headerOffset, count);
      }
      @Override public void onRemoved (int position, int count) {
        adapter.notifyItemRangeRemoved(position + headerOffset, count);
      }
      @Override public void onMoved (int fromPosition, int toPosition) {
        adapter.notifyItemMoved(fromPosition + headerOffset, toPosition + headerOffset);
      }
      @Override public void onChanged (int position, int count, @Nullable Object payload) {
        adapter.notifyItemRangeChanged(position + headerOffset, count, payload);
      }
    });

    // Show/hide based on content and settings
    updateVisibility();

    // Force redraw to ensure items render correctly
    post(this::invalidate);
  }

  private static boolean storiesEqual (List<TdApi.ChatActiveStories> a, List<TdApi.ChatActiveStories> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (a.get(i).chatId != b.get(i).chatId || !storyEntryEqual(a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean storyEntryEqual (TdApi.ChatActiveStories a, TdApi.ChatActiveStories b) {
    if (a.chatId != b.chatId || a.maxReadStoryId != b.maxReadStoryId || a.order != b.order || a.stories.length != b.stories.length) {
      return false;
    }
    for (int i = 0; i < a.stories.length; i++) {
      if (a.stories[i].storyId != b.stories[i].storyId) {
        return false;
      }
    }
    return true;
  }

  public void setCanPostStory (boolean canPost) {
    if (this.canPostStory != canPost) {
      this.canPostStory = canPost;
      adapter.notifyDataSetChanged();
      updateVisibility();
      // Force redraw to ensure items render correctly
      post(this::invalidate);
    }
  }

  private void updateVisibility () {
    // The story bar is a floating overlay over the chat list (see ChatsController.ensureStoryBarOverlay),
    // so it must hide itself when there is nothing to show; otherwise an empty bar floats over the
    // first chat. The listener additionally adjusts the list's top padding.
    boolean hasContent = shouldShow();
    setVisibility(hasContent ? VISIBLE : GONE);
    if (visibilityChangeListener != null) {
      visibilityChangeListener.onStoryBarVisibilityChanged(hasContent);
    }
  }

  public boolean shouldShow () {
    if (Settings.instance().hideStories()) {
      return false;
    }
    return canPostStory || !activeStoriesList.isEmpty();
  }

  private boolean hasAddButton () {
    return canPostStory;
  }

  public int getBarHeight () {
    return shouldShow() ? Screen.dp(BAR_HEIGHT_DP) : 0;
  }

  public static int getFixedBarHeight () {
    return Screen.dp(BAR_HEIGHT_DP);
  }

  private class StoryBarAdapter extends RecyclerView.Adapter<ViewHolder> {

    @Override
    public int getItemViewType (int position) {
      if (hasAddButton() && position == 0) {
        return VIEW_TYPE_ADD_STORY;
      }
      return VIEW_TYPE_STORY;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == VIEW_TYPE_ADD_STORY) {
        AddStoryItemView itemView = new AddStoryItemView(getContext(), tdlib);
        itemView.setLayoutParams(new LayoutParams(Screen.dp(ITEM_SIZE_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        return new AddStoryViewHolder(itemView);
      } else {
        StoryAvatarItemView itemView = new StoryAvatarItemView(getContext(), tdlib);
        itemView.setLayoutParams(new LayoutParams(Screen.dp(ITEM_SIZE_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        return new StoryItemViewHolder(itemView);
      }
    }

    @Override
    public void onBindViewHolder (@NonNull ViewHolder holder, int position) {
      if (holder instanceof AddStoryViewHolder) {
        ((AddStoryViewHolder) holder).bind();
      } else if (holder instanceof StoryItemViewHolder) {
        int storyIndex = hasAddButton() ? position - 1 : position;
        if (storyIndex >= 0 && storyIndex < activeStoriesList.size()) {
          TdApi.ChatActiveStories activeStories = activeStoriesList.get(storyIndex);
          ((StoryItemViewHolder) holder).bind(activeStories, storyIndex);
        }
      }
    }

    @Override
    public int getItemCount () {
      int count = activeStoriesList.size();
      if (hasAddButton()) {
        count++;
      }
      return count;
    }
  }

  private class AddStoryViewHolder extends ViewHolder {
    private final AddStoryItemView itemView;

    public AddStoryViewHolder (@NonNull AddStoryItemView itemView) {
      super(itemView);
      this.itemView = itemView;
    }

    public void bind () {
      itemView.updateColors();
      // Re-read the "Show Add Story Border" / ring-color preferences off the draw path on (re)bind,
      // so a setting change made elsewhere is reflected without waiting for re-attach.
      itemView.onStoryRingColorsChanged();
      itemView.setOnClickListener(v -> {
        if (clickListener != null) {
          clickListener.onAddStoryClick();
        }
      });
    }
  }

  private class StoryItemViewHolder extends ViewHolder {
    private final StoryAvatarItemView itemView;

    public StoryItemViewHolder (@NonNull StoryAvatarItemView itemView) {
      super(itemView);
      this.itemView = itemView;
    }

    public void bind (TdApi.ChatActiveStories activeStories, int position) {
      itemView.updateColors();
      itemView.setActiveStories(activeStories);
      itemView.setOnClickListener(v -> {
        if (clickListener != null && activeStories.stories.length > 0) {
          // Find the first unread story (storyId > maxReadStoryId), or use first story if all read
          int storyIndex = 0;
          for (int i = 0; i < activeStories.stories.length; i++) {
            if (activeStories.stories[i].storyId > activeStories.maxReadStoryId) {
              storyIndex = i;
              break;
            }
          }
          clickListener.onStoryClick(
            activeStories.chatId,
            activeStories.stories[storyIndex].storyId,
            activeStoriesList,
            position
          );
        }
      });
    }
  }

  /**
   * Individual story avatar item with ring indicator
   */
  public static class StoryAvatarItemView extends FrameLayoutFix {

    private final Tdlib tdlib;
    private final AvatarView avatarView;
    private final TextView nameView;
    private final Paint ringPaint;
    private final RectF ringRect;

    private @Nullable TdApi.ChatActiveStories activeStories;
    private boolean hasUnread = false;

    // Cached resolved ring colors. Read from Settings off the draw path (attach / size change /
    // explicit refresh) so onDraw -> updateRingGradient() never touches the preference store (pmc).
    private int[] ringColors;

    // Gradient colors for unread ring
    private static final int[] GRADIENT_COLORS = {
      0xFF7B68EE, // Medium slate blue
      0xFF00CED1, // Dark turquoise
      0xFF00FA9A  // Medium spring green
    };

    // Gray for read ring
    private static final int READ_RING_COLOR = 0xFFAAAAAA;

    public StoryAvatarItemView (@NonNull Context context, Tdlib tdlib) {
      super(context);
      this.tdlib = tdlib;

      setWillNotDraw(false);

      ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      ringPaint.setStyle(Paint.Style.STROKE);
      ringPaint.setStrokeWidth(Screen.dp(RING_WIDTH_DP));
      ringRect = new RectF();

      // Avatar view in center
      avatarView = new AvatarView(context);
      int avatarSize = Screen.dp(AVATAR_SIZE_DP);
      LayoutParams avatarParams = new LayoutParams(avatarSize, avatarSize);
      avatarParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
      avatarParams.topMargin = Screen.dp(8);
      avatarView.setLayoutParams(avatarParams);
      addView(avatarView);

      // Name below avatar
      nameView = new TextView(context);
      nameView.setTextSize(11);
      nameView.setTypeface(Fonts.getRobotoRegular());
      nameView.setGravity(Gravity.CENTER);
      nameView.setSingleLine(true);
      nameView.setMaxLines(1);
      nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      nameParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
      nameParams.bottomMargin = Screen.dp(4);
      nameParams.leftMargin = Screen.dp(2);
      nameParams.rightMargin = Screen.dp(2);
      nameView.setLayoutParams(nameParams);
      addView(nameView);

      // Apply current theme colors
      updateColors();
    }

    public void updateColors () {
      nameView.setTextColor(Theme.textAccentColor());
    }

    public void setActiveStories (@Nullable TdApi.ChatActiveStories activeStories) {
      this.activeStories = activeStories;

      if (activeStories != null) {
        TdApi.Chat chat = tdlib.chat(activeStories.chatId);
        if (chat != null) {
          avatarView.setChat(tdlib, chat);
          // Name truncation handled by TextView's ellipsize
          nameView.setText(tdlib.chatTitle(chat));
        }

        // Check if there are unread stories
        hasUnread = activeStories.maxReadStoryId < getMaxStoryId(activeStories);
        updateRingGradient();
      }

      invalidate();
    }

    private int getMaxStoryId (TdApi.ChatActiveStories activeStories) {
      int maxId = 0;
      for (TdApi.StoryInfo info : activeStories.stories) {
        if (info.storyId > maxId) {
          maxId = info.storyId;
        }
      }
      return maxId;
    }

    /**
     * Reads the ring colors from Settings (a preference-store read) and caches them. Must be called
     * off the draw path. Returns true if the resolved colors actually changed.
     */
    private boolean refreshRingColors () {
      int[] resolved = Settings.instance().getStoryRingColors();
      if (resolved == null || resolved.length < 2) {
        resolved = GRADIENT_COLORS;
      }
      if (!java.util.Arrays.equals(ringColors, resolved)) {
        ringColors = resolved;
        return true;
      }
      return false;
    }

    /**
     * Called when the story ring-color setting changes; rebuilds the cached gradient if needed.
     */
    public void onStoryRingColorsChanged () {
      if (refreshRingColors()) {
        updateRingGradient();
        invalidate();
      }
    }

    private void updateRingGradient () {
      if (hasUnread) {
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0) {
          if (ringColors == null) {
            refreshRingColors();
          }
          LinearGradient gradient = new LinearGradient(
            0, 0, width, height,
            ringColors,
            null,
            Shader.TileMode.CLAMP
          );
          ringPaint.setShader(gradient);
        }
      } else {
        ringPaint.setShader(null);
        ringPaint.setColor(READ_RING_COLOR);
      }
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      refreshRingColors();
      updateRingGradient();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      // Re-read the ring-color preference (may have changed while detached), off the draw path.
      if (refreshRingColors()) {
        updateRingGradient();
      }
      // Force redraw after attachment to ensure ring renders
      post(this::invalidate);
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);

      if (activeStories == null || activeStories.stories.length == 0) {
        return;
      }

      // Ensure gradient is initialized (may not be set if view was bound before layout)
      if (hasUnread && ringPaint.getShader() == null) {
        updateRingGradient();
      }

      // Draw ring around avatar
      int centerX = getWidth() / 2;
      int avatarTopMargin = Screen.dp(8);
      int avatarSize = Screen.dp(AVATAR_SIZE_DP);
      int centerY = avatarTopMargin + avatarSize / 2;

      float ringRadius = avatarSize / 2f + Screen.dp(RING_GAP_DP) + Screen.dp(RING_WIDTH_DP) / 2f;

      ringRect.set(
        centerX - ringRadius,
        centerY - ringRadius,
        centerX + ringRadius,
        centerY + ringRadius
      );

      canvas.drawOval(ringRect, ringPaint);
    }
  }

  /**
   * "Add Story" item view with plus icon and gradient ring
   */
  public static class AddStoryItemView extends FrameLayoutFix {

    private final Tdlib tdlib;
    private final ImageView addIcon;
    private final TextView nameView;
    private final Paint ringPaint;
    private final Paint bgPaint;
    private final RectF ringRect;

    // Cached resolved ring colors. Read from Settings off the draw path (attach / size change /
    // explicit refresh) so onDraw -> updateRingGradient() never touches the preference store (pmc).
    private int[] ringColors;

    // Cached "Show Add Story Border" preference. Read from Settings off the draw path (attach /
    // size change / explicit refresh) for the same reason as ringColors: onDraw must never touch
    // the preference store (pmc). When false the add-story gradient ring is not drawn.
    private boolean showBorder = true;

    // Gradient colors for add story ring (same as unread)
    private static final int[] GRADIENT_COLORS = {
      0xFF7B68EE, // Medium slate blue
      0xFF00CED1, // Dark turquoise
      0xFF00FA9A  // Medium spring green
    };

    public AddStoryItemView (@NonNull Context context, Tdlib tdlib) {
      super(context);
      this.tdlib = tdlib;

      setWillNotDraw(false);

      ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      ringPaint.setStyle(Paint.Style.STROKE);
      ringPaint.setStrokeWidth(Screen.dp(RING_WIDTH_DP));
      ringRect = new RectF();

      bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

      // Plus icon in center
      addIcon = new ImageView(context);
      addIcon.setImageResource(R.drawable.baseline_add_24);
      addIcon.setScaleType(ImageView.ScaleType.CENTER);
      int avatarSize = Screen.dp(AVATAR_SIZE_DP);
      LayoutParams iconParams = new LayoutParams(avatarSize, avatarSize);
      iconParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
      iconParams.topMargin = Screen.dp(8);
      addIcon.setLayoutParams(iconParams);
      addView(addIcon);

      // "Add Story" text below
      nameView = new TextView(context);
      nameView.setText(R.string.AddStory);
      nameView.setTextSize(11);
      nameView.setTypeface(Fonts.getRobotoRegular());
      nameView.setGravity(Gravity.CENTER);
      nameView.setSingleLine(true);
      nameView.setMaxLines(1);
      nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      nameParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
      nameParams.bottomMargin = Screen.dp(4);
      nameParams.leftMargin = Screen.dp(2);
      nameParams.rightMargin = Screen.dp(2);
      nameView.setLayoutParams(nameParams);
      addView(nameView);

      // Apply current theme colors
      updateColors();
    }

    public void updateColors () {
      bgPaint.setColor(Theme.fillingColor());
      addIcon.setColorFilter(Theme.iconColor());
      nameView.setTextColor(Theme.textAccentColor());
      invalidate();
    }

    /**
     * Reads the ring colors and the "Show Add Story Border" preference from Settings (preference-store
     * reads) and caches them. Must be called off the draw path. Returns true if either the resolved
     * colors or the border-visibility preference actually changed.
     */
    private boolean refreshRingColors () {
      boolean changed = false;

      int[] resolved = Settings.instance().getStoryRingColors();
      if (resolved == null || resolved.length < 2) {
        resolved = GRADIENT_COLORS;
      }
      if (!java.util.Arrays.equals(ringColors, resolved)) {
        ringColors = resolved;
        changed = true;
      }

      boolean resolvedShowBorder = Settings.instance().showAddStoryBorder();
      if (showBorder != resolvedShowBorder) {
        showBorder = resolvedShowBorder;
        changed = true;
      }

      return changed;
    }

    /**
     * Called when the story ring-color or border-visibility setting changes; rebuilds the cached
     * gradient if needed.
     */
    public void onStoryRingColorsChanged () {
      if (refreshRingColors()) {
        updateRingGradient();
        invalidate();
      }
    }

    private void updateRingGradient () {
      int width = getWidth();
      int height = getHeight();
      if (width > 0 && height > 0) {
        if (ringColors == null) {
          refreshRingColors();
        }
        LinearGradient gradient = new LinearGradient(
          0, 0, width, height,
          ringColors,
          null,
          Shader.TileMode.CLAMP
        );
        ringPaint.setShader(gradient);
      }
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      refreshRingColors();
      updateRingGradient();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      // Re-read the ring-color preference (may have changed while detached), off the draw path.
      if (refreshRingColors()) {
        updateRingGradient();
      }
      // Force redraw after attachment to ensure ring renders
      post(this::invalidate);
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);

      // Draw ring around icon area
      int centerX = getWidth() / 2;
      int avatarTopMargin = Screen.dp(8);
      int avatarSize = Screen.dp(AVATAR_SIZE_DP);
      int centerY = avatarTopMargin + avatarSize / 2;

      // Draw background circle (always drawn — it backs the plus icon)
      float bgRadius = avatarSize / 2f;
      canvas.drawCircle(centerX, centerY, bgRadius, bgPaint);

      // Draw gradient ring only when the "Show Add Story Border" setting is enabled.
      // showBorder is read off the draw path (refreshRingColors), like ringColors.
      if (!showBorder) {
        return;
      }

      // Ensure gradient is initialized (may not be set if view was bound before layout)
      if (ringPaint.getShader() == null) {
        updateRingGradient();
      }

      float ringRadius = avatarSize / 2f + Screen.dp(RING_GAP_DP) + Screen.dp(RING_WIDTH_DP) / 2f;

      ringRect.set(
        centerX - ringRadius,
        centerY - ringRadius,
        centerX + ringRadius,
        centerY + ringRadius
      );

      canvas.drawOval(ringRect, ringPaint);
    }
  }
}
