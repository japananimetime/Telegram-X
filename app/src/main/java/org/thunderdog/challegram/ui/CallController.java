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
 * File created on 06/04/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.RateLimiter;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.voip.annotation.VideoState;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.TextView;
import org.thunderdog.challegram.widget.voip.CallControlsLayout;
import org.thunderdog.challegram.widget.voip.CallVideoView;

import android.Manifest;
import android.content.pm.PackageManager;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ScrimUtil;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewHandler;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class CallController extends ViewController<CallController.Arguments> implements TdlibCache.UserDataChangeListener, TdlibCache.CallStateChangeListener, View.OnClickListener, FactorAnimator.Target, Runnable, CallControlsLayout.CallControlCallback, Screen.StatusBarHeightChangeListener, TGCallService.VideoStateListener {
  private static final boolean DEBUG_FADE_BRANDING = true;

  private static class ButtonView extends View implements FactorAnimator.Target {
    private Drawable icon;
    private float factor;
    private boolean needCross;

    public ButtonView (Context context) {
      super(context);
    }

    public void setIcon (@DrawableRes int icon) {
      this.icon = Drawables.get(icon);
    }

    public void setNeedCross (boolean needCross) {
      this.needCross = needCross;
    }

    public boolean toggleActive () {
      setIsActive(!isActive, true);
      return isActive;
    }

    private boolean isActive;

    public void setIsActive (boolean isActive, boolean animated) {
      if (this.isActive != isActive) {
        this.isActive = isActive;
        if (animated) {
          animateFactor(isActive ? 1f : 0f);
        } else {
          forceFactor(isActive ? 1f : 0f);
        }
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      return getParent() != null && ((View) getParent()).getAlpha() == 1f && super.onTouchEvent(event);
    }

    private FactorAnimator animator;

    private void animateFactor (float toFactor) {
      if (animator == null) {
        animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
      }
      animator.animateTo(toFactor);
    }

    private void forceFactor (float toFactor) {
      if (animator != null) {
        animator.forceFactor(toFactor);
      }
      setFactor(toFactor);
    }

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    @Override
    protected void onDraw (Canvas c) {
      if (icon == null) {
        return;
      }
      float cx = getMeasuredWidth() / 2;
      float cy = getMeasuredHeight() / 2;
      int backgroundColor = ColorUtils.fromToArgb(0x00ffffff, 0xffffffff, factor);
      if (factor != 0f) {
        c.drawCircle(cx, cy, Screen.dp(18f), Paints.fillingPaint(backgroundColor));
      }
      int iconColor = ColorUtils.fromToArgb(0xffffffff, 0xff000000, factor);
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, Paints.getPorterDuffPaint(iconColor));
      if (factor != 0f && needCross) {
        DrawAlgorithms.drawCross(c, cx, cy, factor, iconColor, backgroundColor);
      }
    }
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  public static class Arguments {
    private TdApi.Call call;

    public Arguments (TdApi.Call call) {
      this.call = call;
    }
  }

  public CallController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.Call call;
  private @Nullable TdApi.User user;
  private CallSettings callSettings;
  private boolean hadEmojiSinceStart;

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.call = args.call;
    setCallBarsCount(tdlib.context().calls().getCallBarsCount(tdlib, call.id));
    this.hadEmojiSinceStart = call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
    this.user = tdlib.cache().user(call.userId);
    // For a video call (outgoing video, or an incoming call the user picks up as video), start the
    // local camera automatically once the call is established.
    this.autoEnableVideoOnReady = call.isVideo;
  }

  @Override
  public int getId () {
    return R.id.controller_call;
  }

  private AvatarView avatarView;
  private TextView nameView, stateView;
  private EmojiStatusHelper emojiStatusHelper;
  private float nameTextWidth;
  private TextPaint nameTextPaint;
  private LinearLayout brandWrap;
  private TextView debugView;
  private CallStrengthView strengthView;

  private static class CallStrengthView extends View {
    private final ViewHandler handler;

    public CallStrengthView (Context context) {
      super(context);
      handler = new ViewHandler();
    }

    private int barCount = -1;
    private long lastUpdateTime;

    private static final long MAX_FREQUENCY_MS = 1000l;

    public void setBarsCount (int callStrength) {
      boolean changed = Math.max(this.barCount, 0) != Math.max(callStrength, 0);
      this.barCount = callStrength;
      if (changed) {
        long now = SystemClock.elapsedRealtime();

        if (lastUpdateTime == 0 || now - lastUpdateTime >= MAX_FREQUENCY_MS) {
          lastUpdateTime = now;
          handler.cancelInvalidate(this);
          invalidate();
        } else {
          handler.invalidate(this, MAX_FREQUENCY_MS - (now - lastUpdateTime));
        }
      }
    }

    private static final int MAX_BARS_COUNT = 4;

    @Override
    protected void onDraw (Canvas c) {
      int size = Screen.dp(3f);
      int spacing = Screen.dp(1f);
      int totalSize = size * MAX_BARS_COUNT + spacing * (MAX_BARS_COUNT - 1);
      int startX = getMeasuredWidth() / 2 - totalSize / 2;
      int startY = getMeasuredHeight() / 2 + size * 2;
      int cx = startX;
      for (int i = 0; i < 4; i++) {
        RectF rectF = Paints.getRectF();
        rectF.set(cx, startY - size * (i + 1), cx + size, startY);
        c.drawRoundRect(rectF, spacing, spacing, Paints.fillingPaint(barCount > i ? 0xffffffff : 0x7fffffff));
        cx += size + spacing;
      }
    }
  }

  private TextView emojiViewSmall, emojiViewBig, emojiViewHint;
  private CallControlsLayout callControlsLayout;

  private FrameLayoutFix buttonWrap;
  private ButtonView muteButtonView, speakerButtonView, videoButtonView, switchCameraButtonView, screenShareButtonView;

  private CallVideoView callVideoView;
  private boolean videoSinksAttached;
  private boolean useFrontCamera = true;
  private @VideoState int remoteVideoState = VideoState.INACTIVE;
  private boolean outgoingVideoEnabled;
  private boolean screenSharing;
  private boolean videoStateListenerAttached;
  private boolean autoEnableVideoOnReady;

  private float lastHeaderFactor;

  @Override
  protected void applyCustomHeaderAnimations (float factor) {
    if (lastHeaderFactor != factor) {
      lastHeaderFactor = factor;
      updateControlsAlpha();
      updateEmojiFactors();
      if (DEBUG_FADE_BRANDING) {
        brandWrap.setAlpha(factor);
      }
      avatarView.invalidate();
    }
  }

  private BoolAnimator strengthAnimator;
  private static final int ANIMATOR_STRENGTH = 6;

  private void updateCallStrength () {
    boolean isVisible = callStrength >= 0 && call != null && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR && callDuration >= 0;
    if (!isVisible == (strengthAnimator != null && strengthAnimator.getValue())) {
      if (strengthAnimator == null) {
        strengthAnimator = new BoolAnimator(ANIMATOR_STRENGTH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      strengthAnimator.setValue(isVisible, strengthView != null && lastHeaderFactor > 0f);
    }
  }

  private int callStrength = -1;

  private void setCallBarsCount (int count) {
    if (this.callStrength != count) {
      this.callStrength = count;
      if (strengthView != null) {
        strengthView.setBarsCount(count);
      }
      updateCallStrength();
    }
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    updateButtonWrapHeight(extraBottomInset);
    Views.setPaddingBottom(buttonWrap, extraBottomInset);
    Views.setPaddingBottom(callControlsLayout, extraBottomInset);
  }

  // buttonWrap is a fixed-height bottom strip whose bottom padding reserves the gesture-nav inset.
  // The strip must be tall enough to hold the 72dp buttons ON TOP of that inset, otherwise the inset
  // padding squeezes the buttons up out of the strip (clipped → invisible). Grow the height by the inset.
  private void updateButtonWrapHeight (int extraBottomInset) {
    if (buttonWrap == null) {
      return;
    }
    int targetHeight = Screen.dp(76f) + extraBottomInset;
    ViewGroup.LayoutParams params = buttonWrap.getLayoutParams();
    if (params != null && params.height != targetHeight) {
      params.height = targetHeight;
      buttonWrap.setLayoutParams(params);
    }
  }

  @Override
  public void onStatusBarHeightChanged (int newHeight) {
    int startMargin = Math.max(Screen.dp(18f) + newHeight, Screen.dp(42f));
    Views.setTopMargin(brandWrap, startMargin);
    Views.setTopMargin(nameView, startMargin + Screen.dp(34f));
    Views.setTopMargin(stateView, startMargin + Screen.dp(94f));
  }

  @Override
  protected View onCreateView (final Context context) {
    final FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateEmojiPosition();
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateEmojiPosition();
      }
    };
    ViewSupport.setThemedBackground(contentView, ColorId.headerBackground, this);

    avatarView = new AvatarView(context) {
      private final Drawable topShadow = ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.TOP, false);

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int shadowSize = Screen.dp(212f);
        if (topShadow.getBounds().right != width || topShadow.getBounds().bottom != shadowSize) {
          topShadow.setBounds(0, 0, width, shadowSize);
        }
      }

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        super.onTouchEvent(event);
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            return true;
          case MotionEvent.ACTION_UP:
            if (emojiExpandFactor == 1f && isEmojiExpanded) {
              setEmojiExpanded(false);
            }
            return true;
        }
        return false;
      }

      @Override
      protected void onDraw(Canvas c){
        super.onDraw(c);
        int alpha = (int) (255f * lastHeaderFactor * .5f);
        Drawables.setAlpha(topShadow, alpha);
        topShadow.draw(c);
      }
    };
    avatarView.setNoRound(true);
    avatarView.setNoPlaceholders(true);
    avatarView.setNeedFull(true);
    avatarView.setUser(tdlib, user, false);
    avatarView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(avatarView);

    // Video renderers sit above the avatar background but below the name/state/controls overlays.
    callVideoView = new CallVideoView(context);
    callVideoView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(callVideoView);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    // Top-left corner

    int startMargin = Math.max(Screen.dp(18f) + Screen.getStatusBarHeight(), Screen.dp(42f));

    params.topMargin = startMargin + Screen.dp(34f);
    params.leftMargin = params.rightMargin = Screen.dp(18f);

    nameView = new EmojiTextView(context) {
      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        nameTextWidth = U.measureText(TD.getUserName(user), nameTextPaint);
        if (nameTextWidth > getMeasuredWidth() - getPaddingRight()) {
          CharSequence text = getText().subSequence(0, getLayout().getEllipsisStart(0)) + "...";
          nameTextWidth = U.measureText(text, nameTextPaint);
        }
      }

      @Override
      protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
        emojiStatusHelper.draw(canvas, (int) Math.min(getMeasuredWidth() - emojiStatusHelper.getWidth(0), nameTextWidth + Screen.dp(7)), Screen.dp(9));
      }
    };
    nameView.setScrollDisabled(true);
    nameView.setSingleLine(true);
    nameView.setTextColor(0xffffffff);
    nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
    nameView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    Views.setSimpleShadow(nameView);
    nameView.setEllipsize(TextUtils.TruncateAt.END);
    nameView.setLayoutParams(params);
    contentView.addView(nameView);

    nameTextPaint = new TextPaint();
    nameTextPaint.setTextSize(Screen.dp(40));
    nameTextPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    emojiStatusHelper = new EmojiStatusHelper(tdlib, nameView, null);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = startMargin + Screen.dp(94f);
    params.leftMargin = params.rightMargin = Screen.dp(18f);

    stateView = new TextView(context);
    stateView.setScrollDisabled(true);
    // stateView.setSingleLine(true);
    stateView.setMaxLines(2);
    stateView.setLineSpacing(Screen.dp(3f), 1f);
    stateView.setTextColor(0xffffffff);
    stateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    stateView.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(stateView);
    // stateView.setEllipsize(TextUtils.TruncateAt.END);
    stateView.setLayoutParams(params);
    contentView.addView(stateView);

    Screen.addStatusBarHeightListener(this);
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = startMargin;
    params.leftMargin = params.rightMargin = Screen.dp(18f);
    brandWrap = new LinearLayout(context);
    if (DEBUG_FADE_BRANDING) {
      brandWrap.setAlpha(0f);
    }
    brandWrap.setOrientation(LinearLayout.HORIZONTAL);
    brandWrap.setLayoutParams(params);
    contentView.addView(brandWrap);

    LinearLayout.LayoutParams lp;

    lp = new LinearLayout.LayoutParams(Screen.dp(14f), Screen.dp(14f));
    lp.topMargin = Screen.dp(2f);

    ImageView brandIcon = new ImageView(context);
    brandIcon.setScaleType(ImageView.ScaleType.CENTER);
    brandIcon.setImageResource(R.drawable.deproko_logo_telegram_18);
    brandIcon.setLayoutParams(lp);
    brandWrap.addView(brandIcon);

    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = Screen.dp(9f);

    TextView brandView = new TextView(context);
    brandView.setScrollDisabled(true);
    brandView.setSingleLine(true);
    brandView.setTextColor(0xffffffff);
    brandView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    brandView.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(brandView);
    brandView.setEllipsize(TextUtils.TruncateAt.END);
    brandView.setLayoutParams(lp);
    brandView.setText(Lang.getString(R.string.VoipBranding).toUpperCase());
    if (Log.checkLogLevel(Log.LEVEL_INFO) || BuildConfig.EXPERIMENTAL) {
      brandView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick (View v) {
          TGCallService.markLogViewed();
          if (debugView != null) {
            contentView.removeView(debugView);
            debugView = null;
          } else {
            final TextView view = new TextView(context);
            view.setScrollDisabled(true);
            view.setBackgroundColor(0xaaffffff);
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setTextColor(0xff000000);
            view.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
            view.post(new Runnable() {
              @Override
              public void run () {
                TGCallService service = TGCallService.currentInstance();

                SpannableStringBuilder b = new SpannableStringBuilder();
                if (service != null) {
                  b.append(service.getLibraryNameAndVersion());
                } else {
                  b.append("service unavailable");
                }
                b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoBold(), 0), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (service != null) {
                  CharSequence log = service.getDebugString();
                  if (!StringUtils.isEmpty(log)) {
                    b.append("\n\n");
                    b.append(log);
                  }
                }
                view.setText(b);
                if (view.getParent() != null) {
                  view.postDelayed(this, 500l);
                }
              }
            });
            debugView = view;
            contentView.addView(debugView);
          }
        }
      });
    }
    brandWrap.addView(brandView);

    lp = new LinearLayout.LayoutParams(Screen.dp(18f), Screen.dp(18f));
    // lp.topMargin = Screen.dp(2f);
    lp.leftMargin = Screen.dp(8f);
    strengthView = new CallStrengthView(context);
    strengthView.setLayoutParams(lp);
    if (callStrength < 0) {
      strengthView.setAlpha(0f);
    }
    strengthView.setBarsCount(callStrength);
    brandWrap.addView(strengthView);

    // Emoji corner

    emojiViewSmall = new EmojiTextView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return (event.getAction() != MotionEvent.ACTION_DOWN || emojiExpandFactor == 0f) && super.onTouchEvent(event);
      }
    };
    emojiViewSmall.setScrollDisabled(true);
    emojiViewSmall.setSingleLine(true);
    emojiViewSmall.setTextColor(0xffffffff);
    emojiViewSmall.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
    emojiViewSmall.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewSmall);
    emojiViewSmall.setEllipsize(TextUtils.TruncateAt.END);
    emojiViewSmall.setPadding(Screen.dp(18f), Screen.dp(18f), Screen.dp(18f), Screen.dp(18f));
    emojiViewSmall.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    emojiViewSmall.setOnClickListener(this);
    emojiViewSmall.setId(R.id.btn_emoji);
    contentView.addView(emojiViewSmall);

    emojiViewBig = new EmojiTextView(context);
    emojiViewBig.setScrollDisabled(true);
    emojiViewBig.setSingleLine(true);
    emojiViewBig.setScaleX(1f / EMOJI_EXPAND_FACTOR);
    emojiViewBig.setScaleY(1f / EMOJI_EXPAND_FACTOR);
    emojiViewBig.setAlpha(0f);
    emojiViewBig.setTextColor(0xffffffff);
    int emojiBigSize = (int) (16f * DESIRED_EMOJI_EXPAND_FACTOR);
    EMOJI_EXPAND_FACTOR = (float) emojiBigSize / 16f;
    emojiViewBig.setTextSize(TypedValue.COMPLEX_UNIT_DIP, emojiBigSize);
    emojiViewBig.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewBig);
    emojiViewBig.setEllipsize(TextUtils.TruncateAt.END);
    emojiViewBig.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    contentView.addView(emojiViewBig);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.topMargin = Screen.dp(24f) * 2;
    params.rightMargin = params.leftMargin = Screen.dp(48f);

    emojiViewHint = new EmojiTextView(context);
    emojiViewHint.setScrollDisabled(true);
    emojiViewHint.setAlpha(0f);
    emojiViewHint.setTextColor(0xffffffff);
    emojiViewHint.setGravity(Gravity.CENTER);
    emojiViewHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
    emojiViewHint.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewHint);
    emojiViewHint.setLayoutParams(params);
    contentView.addView(emojiViewHint);

    // Call settings buttons

    muteButtonView = new ButtonView(context);
    muteButtonView.setId(R.id.btn_mute);
    muteButtonView.setOnClickListener(this);
    muteButtonView.setIcon(R.drawable.baseline_mic_24);
    muteButtonView.setNeedCross(true);
    muteButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.LEFT | Gravity.BOTTOM));

    ButtonView messageButtonView = new ButtonView(context);
    messageButtonView.setId(R.id.btn_openChat);
    messageButtonView.setOnClickListener(this);
    messageButtonView.setIcon(R.drawable.baseline_chat_bubble_24);
    messageButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));

    speakerButtonView = new ButtonView(context);
    speakerButtonView.setId(R.id.btn_speaker);
    speakerButtonView.setOnClickListener(this);
    speakerButtonView.setIcon(R.drawable.baseline_volume_up_24);
    speakerButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.RIGHT | Gravity.BOTTOM));

    // Video toggle (own camera on/off). Sits left of centre in the bottom row; shown when video
    // calling is available for this call.
    videoButtonView = new ButtonView(context);
    videoButtonView.setId(R.id.btn_video);
    videoButtonView.setOnClickListener(this);
    videoButtonView.setIcon(R.drawable.baseline_videocam_24);
    videoButtonView.setNeedCross(true);
    FrameLayoutFix.LayoutParams videoParams = FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
    videoParams.rightMargin = Screen.dp(96f);
    videoButtonView.setLayoutParams(videoParams);
    videoButtonView.setVisibility(View.GONE);

    buttonWrap = new FrameLayoutFix(context);
    buttonWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(76f) + extraBottomInset, Gravity.BOTTOM));
    buttonWrap.addView(muteButtonView);
    buttonWrap.addView(videoButtonView);
    buttonWrap.addView(messageButtonView);
    buttonWrap.addView(speakerButtonView);
    Views.setPaddingBottom(buttonWrap, extraBottomInset);
    Drawable drawable = ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.BOTTOM, false);
    drawable.setAlpha((int) (255f * .3f));
    ViewUtils.setBackground(buttonWrap, drawable);
    contentView.addView(buttonWrap);

    // Switch camera (front/back). Top-right overlay, shown only while sending video.
    switchCameraButtonView = new ButtonView(context);
    switchCameraButtonView.setId(R.id.btn_camera_switch);
    switchCameraButtonView.setOnClickListener(this);
    switchCameraButtonView.setIcon(R.drawable.baseline_camera_front_24);
    FrameLayoutFix.LayoutParams switchParams = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.TOP | Gravity.RIGHT);
    switchParams.topMargin = Math.max(Screen.dp(18f) + Screen.getStatusBarHeight(), Screen.dp(42f));
    switchParams.rightMargin = Screen.dp(8f);
    switchCameraButtonView.setLayoutParams(switchParams);
    switchCameraButtonView.setVisibility(View.GONE);
    contentView.addView(switchCameraButtonView);

    // Screen share toggle. Sits right of centre in the bottom row (mirroring the video
    // toggle on the left); shown once the call is established. Replaces the camera as the
    // outgoing source while active.
    screenShareButtonView = new ButtonView(context);
    screenShareButtonView.setId(R.id.btn_screenShare);
    screenShareButtonView.setOnClickListener(this);
    screenShareButtonView.setIcon(R.drawable.baseline_devices_other_24);
    screenShareButtonView.setNeedCross(true);
    FrameLayoutFix.LayoutParams screenShareParams = FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
    screenShareParams.leftMargin = Screen.dp(96f);
    screenShareButtonView.setLayoutParams(screenShareParams);
    screenShareButtonView.setVisibility(View.GONE);
    buttonWrap.addView(screenShareButtonView);

    // Answer controls

    callControlsLayout = new CallControlsLayout(context, this);
    Views.setPaddingBottom(callControlsLayout, extraBottomInset);
    callControlsLayout.setCallback(this);
    callControlsLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(callControlsLayout);
    callControlsLayout.setCall(tdlib, call, false);

    // Data

    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.cache().addUserDataListener(call.userId, this);

    this.callSettings = tdlib.cache().getCallSettings(call.id);

    setTexts();
    updateCallState();

    if (callSettings != null) {
      muteButtonView.setIsActive(callSettings.isMicMuted(), false);
      speakerButtonView.setIsActive(callSettings.isSpeakerModeEnabled(), false);
    }

    return contentView;
  }


  private void setTexts () {
    if (emojiStatusHelper != null) {
      this.emojiStatusHelper.updateEmoji(tdlib, user, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
        @Override
        public long mediaTextComplexColor () {
          return Theme.newComplexColor(true, ColorId.white);
        }
      }, R.drawable.baseline_premium_star_28, 32);
    }
    if (nameView != null) {
      this.nameView.setText(TD.getUserName(user));
      this.nameView.setPadding(0, 0, user != null && user.isPremium ? emojiStatusHelper.getWidth(Screen.dp(7)) : 0, 0);
      this.nameView.requestLayout();
    }
    if (emojiViewHint != null)
      this.emojiViewHint.setText(Lang.getString(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
  }

  @Override
  public void onCallAccept (TdApi.Call call) {
    tdlib.context().calls().acceptCall(context(), tdlib, call.id);
  }

  @Override
  public void onCallDecline (TdApi.Call call, boolean isHangUp) {
    tdlib.context().calls().hangUp(tdlib, call.id);
  }

  @Override
  public void onCallRestart (TdApi.Call call) {
    tdlib.context().calls().makeCall(this, call.userId, null);
  }

  public boolean compareUserId (long userId) {
    return call.userId == userId;
  }

  @Override
  public void onCallClose (TdApi.Call call) {
    closeCall();
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (!UI.isTablet()) {
      context().setOrientation(BaseActivity.getAndroidOrientationPortrait());
    }
  }

  @Override
  public void onCleanAfterHide () {
    super.onCleanAfterHide();
    if (!UI.isTablet()) {
      context().setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
  }

  private void updateLoop () {
    setIsLooping(!isDestroyed() && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
  }

  private boolean isLooping;

  private void setIsLooping (boolean isLooping) {
    if (this.isLooping != isLooping) {
      this.isLooping = isLooping;
      if (isLooping) {
        UI.post(this);
      } else {
        UI.removePendingRunnable(this);
      }
    }
  }

  @Override
  public void run () {
    if (!isDestroyed()) {
      updateCallState();
      if (isLooping) {
        UI.post(this, tdlib.context().calls().getTimeTillNextCallDurationUpdate(tdlib, call.id));
      }
    }
  }

  private boolean buttonsVisible;
  private FactorAnimator buttonsAnimator;
  private static final int ANIMATOR_BUTTONS_ID = 0;
  private float buttonsFactor;

  private void setButtonsVisible (boolean areVisible, boolean animated) {
    if (this.buttonsVisible != areVisible) {
      this.buttonsVisible = areVisible;
      if (animated) {
        if (buttonsAnimator == null) {
          buttonsAnimator = new FactorAnimator(ANIMATOR_BUTTONS_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.buttonsFactor);
        }
        buttonsAnimator.animateTo(areVisible ? 1f : 0f);
      } else {
        if (buttonsAnimator != null) {
          buttonsAnimator.forceFactor(areVisible ? 1f : 0f);
        }
        setButtonsFactor(areVisible ? 1f : 0f);
      }
    }
  }

  private void setButtonsFactor (float factor) {
    this.buttonsFactor = factor;
    updateControlsAlpha();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BUTTONS_ID: {
        setButtonsFactor(factor);
        break;
      }
      case ANIMATOR_FLASH_ID: {
        stateView.setAlpha(factor <= .5f ? 1f - (factor / .5f) : (factor - .5f) / .5f);
        break;
      }
      case ANIMATOR_EMOJI_VISIBILITY_ID: {
        setEmojiVisibilityFactor(factor);
        break;
      }
      case ANIMATOR_EMOJI_EXPAND_ID: {
        setEmojiExpandFactor(Math.max(0f, factor));
        break;
      }
      case ANIMATOR_STRENGTH: {
        if (strengthView != null) {
          strengthView.setAlpha(factor);
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FLASH_ID: {
        if (finalFactor == 1f) {
          flashLimiter.run();
        }
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_emoji) {
      if (isEmojiVisible) {
        setEmojiExpanded(true);
      }
    } else if (viewId == R.id.btn_mute) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        callSettings.setMicMuted(((ButtonView) v).toggleActive());
      }
    } else if (viewId == R.id.btn_video) {
      if (!TD.isFinished(call)) {
        toggleOutgoingVideo();
      }
    } else if (viewId == R.id.btn_screenShare) {
      if (!TD.isFinished(call)) {
        toggleScreenShare();
      }
    } else if (viewId == R.id.btn_camera_switch) {
      if (!TD.isFinished(call) && outgoingVideoEnabled) {
        useFrontCamera = !useFrontCamera;
        TGCallService service = TGCallService.currentInstance();
        if (service != null && service.compareCall(tdlib, call.id)) {
          service.switchCamera(useFrontCamera);
        }
        if (callVideoView != null) {
          callVideoView.setLocalMirror(useFrontCamera);
        }
      }
    } else if (viewId == R.id.btn_openChat) {
      tdlib.ui().openPrivateChat(this, call.userId, null);
    } else if (viewId == R.id.btn_speaker) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        if (callSettings.isSpeakerModeEnabled()) {
          callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_EARPIECE);
        } else {
          callSettings.toggleSpeakerMode(this);
        }
      }
    }
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setTexts();
      }
    });
  }

  @Override
  public void onCallUpdated (final TdApi.Call call) {
    if (!isDestroyed()) {
      updateCall(call);
      updateCallState();
    }
  }

  private boolean isClosed;

  private void closeCall () {
    isClosed = true;
    tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    navigateBack();
  }

  private TdApi.CallState previousCallState;

  private void updateCall (TdApi.Call call) {
    if (isClosed) {
      return;
    }
    this.previousCallState = this.call.state;
    boolean prevIsActive = this.call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
    boolean callEnded = call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR || (call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && ((TdApi.CallStateDiscarded) call.state).reason.getConstructor() == TdApi.CallDiscardReasonHungUp.CONSTRUCTOR);

    this.call = call;
    this.callDuration = 0;
    setCallBarsCount(tdlib.context().calls().getCallBarsCount(tdlib, call.id));
    updateCallStrength();
    if (TD.isCancelled(call) || TD.isAcceptedOnOtherDevice(call) || TD.isDeclined(call) || (prevIsActive && callEnded) || TD.isMissed(call) || call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      closeCall();
    } else {
      callControlsLayout.setCall(tdlib, call, navigationController != null);
    }
  }

  @Override
  public void onCallStateChanged (final int callId, final int newState) {
    if (!isDestroyed()) {
      updateCallState();
    }
  }

  @Override
  public void onCallBarsCountChanged (int callId, int barsCount) {
    if (!isDestroyed() && this.call != null && this.call.id == callId) {
      setCallBarsCount(barsCount);
    }
  }

  @Override
  public void onCallSettingsChanged (final int callId, final CallSettings settings) {
    if (!isDestroyed()) {
      callSettings = settings;
      updateCallButtons();
    }
  }

  private boolean isFlashing;
  private FactorAnimator flashAnimator;
  private static final int ANIMATOR_FLASH_ID = 1;

  public static final long CALL_FLASH_DURATION = 1100;
  public static final long CALL_FLASH_DELAY = 650l;

  private final RateLimiter flashLimiter = new RateLimiter(() -> {
    if (isFlashing) {
      flashAnimator.forceFactor(0f);
      if (isFlashing) {
        flashAnimator.animateTo(1f);
      }
    }
  }, 100l, null);

  private void setFlashing (boolean isFlashing) {
    if (this.isFlashing != isFlashing) {
      this.isFlashing = isFlashing;
      if (isFlashing) {
        if (flashAnimator == null) {
          flashAnimator = new FactorAnimator(ANIMATOR_FLASH_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, CALL_FLASH_DURATION);
          flashAnimator.setStartDelay(CALL_FLASH_DELAY);
        }
        if (!flashAnimator.isAnimating()) {
          flashAnimator.forceFactor(0f);
          flashAnimator.animateTo(1f);
        }
      } else {
        if (flashAnimator != null && flashAnimator.getFactor() == 0f) {
          flashAnimator.forceFactor(0f);
        }
      }
    }
  }

  private long callDuration;

  private void updateCallState () {
    updateLoop();
    String str;
    callDuration = tdlib.context().calls().getCallDuration(tdlib, call.id);
    if (previousCallState != null && call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      str = TD.getCallState2(call, previousCallState, callDuration, false);
    } else {
      str = TD.getCallState(call, callDuration, false);
      if (!call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && tdlib.context().isMultiUser()) {
        String longName = tdlib.accountLongName();
        if (longName != null) {
          str = str + "\n" + Lang.getString(R.string.VoipAnsweringAsAccount, longName);
        }
      }
    }
    stateView.setText(str.toUpperCase());
    setButtonsVisible(!TD.isFinished(call) && !(call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing), isFocused());
    updateEmoji();
    updateFlashing();
    updateCallStrength();
    if (isCallActive()) {
      attachVideoServiceListener();
    }
    updateVideoVisibility();
  }

  private boolean hadFocus;

  private void updateFlashing () {
    hadFocus = isFocused() || hadFocus;
    setFlashing(TD.getCallNeedsFlashing(call) && hadFocus);
  }

  @Override
  protected void onFocusStateChanged () {
    updateFlashing();
  }

  private void updateControlsAlpha () {
    // The in-call control row (mute / video / speaker) must follow the call-active state only, NOT the
    // navigation header reveal factor: lastHeaderFactor legitimately drops to 0 (e.g. when the call
    // screen is presented without nav chrome), which previously zeroed these controls mid-call and
    // left only the hang-up button visible.
    buttonWrap.setAlpha(buttonsFactor);
    buttonWrap.setTranslationY((1f - buttonsFactor) * buttonWrap.getMeasuredHeight() * .2f);
  }

  private void updateCallButtons () {
    if (buttonWrap != null) {
      muteButtonView.setIsActive(callSettings != null && callSettings.isMicMuted(), isFocused());
      speakerButtonView.setIsActive(callSettings != null && callSettings.isSpeakerModeEnabled(), isFocused());
    }
  }

  private void updateEmoji () {
    boolean emojiVisible = (call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
    if (emojiVisible && StringUtils.isEmpty(emojiViewSmall.getText())) {

      TdApi.CallStateReady ready = (TdApi.CallStateReady) call.state;

      StringBuilder b = new StringBuilder();
      for (String emoji : ready.emojis) {
        if (b.length() > 0) {
          b.append("  ");
        }
        b.append(emoji);
      }

      CharSequence result = Emoji.instance().replaceEmoji(b.toString());

      emojiViewSmall.setText(result);
      emojiViewBig.setText(result);

      if (!hadEmojiSinceStart) {
        showEmojiTooltip();
      }
    }
    updateEmojiFactors();
    setEmojiVisible(emojiVisible, isFocused());
  }

  private void showEmojiTooltip () {
    context().tooltipManager().builder(emojiViewSmall).controller(this).show(tdlib, Lang.getStringBold(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
  }

  private boolean isEmojiVisible;
  private FactorAnimator emojiVisibilityAnimator;

  private static final int ANIMATOR_EMOJI_VISIBILITY_ID = 3;
  private static final int ANIMATOR_EMOJI_EXPAND_ID = 4;

  private void setEmojiVisible (boolean isVisible, boolean animated) {
    if (this.isEmojiVisible != isVisible) {
      this.isEmojiVisible = isVisible;
      float toFactor = isVisible ? 1f : 0f;

      if (animated) {
        if (emojiVisibilityAnimator == null) {
          emojiVisibilityAnimator = new FactorAnimator(ANIMATOR_EMOJI_VISIBILITY_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, DURATION_EMOJI_VISIBILITY, this.emojiVisibilityFactor);
        }
        emojiVisibilityAnimator.animateTo(toFactor);
      } else {
        if (emojiVisibilityAnimator != null) {
          emojiVisibilityAnimator.forceFactor(toFactor);
        }
        setEmojiVisibilityFactor(toFactor);
      }
    }
  }

  private static final long DURATION_EMOJI_VISIBILITY = 180l;

  private boolean isEmojiExpanded;
  private FactorAnimator emojiExpandAnimator;

  private void setEmojiExpanded (boolean isExpanded) {
    if (this.isEmojiExpanded != isExpanded) {
      this.isEmojiExpanded = isExpanded;
      if (emojiExpandAnimator == null) {
        emojiExpandAnimator = new FactorAnimator(ANIMATOR_EMOJI_EXPAND_ID, this, new OvershootInterpolator(1.02f), 310l, this.emojiExpandFactor);
      }
      emojiExpandAnimator.animateTo(isExpanded ? 1f : 0f);
    }
  }

  private float emojiVisibilityFactor;
  private float emojiExpandFactor;

  private void setEmojiVisibilityFactor (float factor) {
    if (this.emojiVisibilityFactor != factor) {
      this.emojiVisibilityFactor = factor;
      updateEmojiFactors();
    }
  }

  private void setEmojiExpandFactor (float factor) {
    if (this.emojiExpandFactor != factor) {
      this.emojiExpandFactor = factor;
      updateEmojiFactors();
    }
  }

  private static final float DESIRED_EMOJI_EXPAND_FACTOR = 2.25f;
  private static float EMOJI_EXPAND_FACTOR = DESIRED_EMOJI_EXPAND_FACTOR;

  private void updateEmojiFactors () {
    emojiViewSmall.setAlpha(MathUtils.clamp(emojiVisibilityFactor * (1f - Math.max(1f - lastHeaderFactor, emojiExpandFactor >= .5f ? (emojiExpandFactor - .5f) / .5f : 0f))));
    emojiViewSmall.setScaleX(1f + emojiExpandFactor * (EMOJI_EXPAND_FACTOR - 1f));
    emojiViewSmall.setScaleY(1f + emojiExpandFactor * (EMOJI_EXPAND_FACTOR - 1f));

    float bigAlpha = MathUtils.clamp(emojiVisibilityFactor * emojiExpandFactor);
    emojiViewBig.setAlpha(bigAlpha);
    emojiViewHint.setAlpha(bigAlpha);
    float factor = 1f / EMOJI_EXPAND_FACTOR;
    emojiViewBig.setScaleX(factor + (1f - factor) * emojiExpandFactor);
    emojiViewBig.setScaleY(factor + (1f - factor) * emojiExpandFactor);
    avatarView.setMainAlpha(1f - MathUtils.clamp(emojiVisibilityFactor * emojiExpandFactor));
    updateEmojiPosition();
  }

  private void updateEmojiPosition () {
    final int parentWidth = ((View) emojiViewBig.getParent()).getMeasuredWidth();
    final int parentHeight = ((View) emojiViewBig.getParent()).getMeasuredHeight();

    final int viewWidth = emojiViewBig.getMeasuredWidth();
    final int viewHeight = emojiViewBig.getMeasuredHeight();

    final int viewWidthSmall = emojiViewSmall.getMeasuredWidth();
    final int viewHeightSmall = emojiViewSmall.getMeasuredHeight();

    final int startLeft = parentWidth - viewWidthSmall;
    final int startTop = Screen.dp(42f) - emojiViewSmall.getPaddingTop();

    final int fromCenterX = startLeft + viewWidthSmall / 2;
    final int fromCenterY = startTop + viewHeightSmall / 2;

    final int toCenterX = parentWidth / 2;
    final int toCenterY = parentHeight / 2 - Screen.dp(24f);

    final int centerX = (int) (fromCenterX + (float) (toCenterX - fromCenterX) * emojiExpandFactor);
    final int centerY = (int) (fromCenterY + (float) (toCenterY - fromCenterY) * emojiExpandFactor);

    int x = centerX - viewWidth / 2;
    int y = centerY - viewHeight / 2;

    emojiViewBig.setTranslationX(x);
    emojiViewBig.setTranslationY(y);

    int xSmall = centerX - viewWidthSmall / 2;
    int ySmall = centerY - viewHeightSmall / 2;

    emojiViewSmall.setTranslationX(xSmall);
    emojiViewSmall.setTranslationY(ySmall);
  }

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    if (!oneShot) {
      destroyStackItemByIdExcludingLast(R.id.controller_call);
      ViewController<?> c = previousStackItem();
      if (c != null && c.getId() == R.id.controller_contacts) {
        destroyStackItemById(R.id.controller_contacts);
      }
      oneShot = true;
    }
    tdlib.context().calls().acknowledgeCurrentCall(call.id);
    requestBluetoothConnectForCall();
  }

  private boolean requestedBluetoothConnect;

  // Android 12+ needs the runtime BLUETOOTH_CONNECT grant before getAvailableCommunicationDevices()
  // will expose Bluetooth headsets — without it, call audio can't route to BT. Request it once,
  // non-blocking (the call proceeds regardless); on grant, re-apply call settings so the audio
  // service re-evaluates devices and the BT route becomes selectable.
  private void requestBluetoothConnectForCall () {
    if (requestedBluetoothConnect || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
      return;
    }
    if (context().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
      requestedBluetoothConnect = true;
      return;
    }
    requestedBluetoothConnect = true;
    context().requestCustomPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, (code, permissions, grantResults, grantCount) -> {
      if (grantCount > 0 && call != null) {
        CallSettings settings = tdlib.cache().getCallSettings(call.id);
        if (settings != null) {
          tdlib.cache().onUpdateCallSettings(call.id, settings);
        }
      }
    });
  }

  @Override
  protected int getPopupRestoreColor () {
    return 0xff000000;
  }

  @Override
  protected boolean forceFadeMode () {
    ViewController<?> c = previousStackItem();
    return c != null && c.getId() == R.id.controller_call;
  }

  public void replaceCall (TdApi.Call call) {
    tdlib.cache().unsubscribeFromCallUpdates(this.call.id, this);
    previousCallState = null;
    updateCall(call);
    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.context().calls().acknowledgeCurrentCall(call.id);
    updateCallState();
  }

  // Video

  private @Nullable TGCallService boundVideoService;

  private @Nullable TGCallService currentCallService () {
    TGCallService service = TGCallService.currentInstance();
    return service != null && service.compareCall(tdlib, call.id) ? service : null;
  }

  private void attachVideoServiceListener () {
    if (isDestroyed()) {
      return;
    }
    TGCallService service = currentCallService();
    if (service == null) {
      return;
    }
    if (boundVideoService != service) {
      if (boundVideoService != null) {
        // Detach sinks/listener from the old service before rebinding so the new instance gets
        // fresh sinks (otherwise remote video stays black after a service swap).
        if (videoSinksAttached) {
          boundVideoService.setIncomingVideoOutput(null);
          boundVideoService.setLocalVideoOutput(null);
        }
        boundVideoService.removeVideoStateListener(this);
        videoSinksAttached = false;
      }
      boundVideoService = service;
      videoStateListenerAttached = true;
      service.setVideoStateListener(this);
    }
    // Attach the remote/local renderer sinks to the live VoIP instance once.
    if (!videoSinksAttached && callVideoView != null && service.getVoip() != null) {
      service.setIncomingVideoOutput(callVideoView.getRemoteSink());
      service.setLocalVideoOutput(callVideoView.getLocalSink());
      videoSinksAttached = true;
    }
    this.remoteVideoState = service.getRemoteVideoState();
    this.outgoingVideoEnabled = service.isVideoOutgoing();
    if (autoEnableVideoOnReady && !outgoingVideoEnabled && service.getVoip() != null) {
      autoEnableVideoOnReady = false;
      requestCameraPermissionThen(() -> setOutgoingVideoEnabled(true));
    }
    updateVideoVisibility();
  }

  private void toggleOutgoingVideo () {
    // The camera button toggles the CAMERA source specifically: if a screen-share is currently
    // active, switch the source to the camera (mutually exclusive) rather than just stopping
    // the share; if the camera is already the source, turn it off; otherwise turn it on.
    if (screenSharing) {
      requestCameraPermissionThen(() -> setOutgoingVideoEnabled(true));
    } else if (outgoingVideoEnabled) {
      setOutgoingVideoEnabled(false);
    } else {
      requestCameraPermissionThen(() -> setOutgoingVideoEnabled(true));
    }
  }

  private void requestCameraPermissionThen (Runnable onGranted) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
        context().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      context().requestCustomPermissions(new String[] {Manifest.permission.CAMERA}, (code, permissions, grantResults, grantCount) -> {
        // Ignore a grant that arrives after the controller was destroyed or the call ended /
        // unbound — enabling video at that point would target a dead service.
        if (grantCount == permissions.length && !isDestroyed() && isCallActive() && currentCallService() != null) {
          onGranted.run();
        }
      });
    } else {
      onGranted.run();
    }
  }

  private void setOutgoingVideoEnabled (boolean enabled) {
    if (isDestroyed()) {
      return;
    }
    TGCallService service = currentCallService();
    if (service == null) {
      return;
    }
    attachVideoServiceListener();
    service.setOutgoingVideoEnabled(enabled, useFrontCamera);
    this.outgoingVideoEnabled = service.isVideoOutgoing();
    this.screenSharing = service.isScreenSharing();
    updateVideoVisibility();
  }

  private void toggleScreenShare () {
    if (screenSharing) {
      setScreenShareEnabled(false);
    } else {
      // Request MediaProjection permission; on grant the result is stored in
      // VoIPScreenCapture and we start the screencast capturer.
      context().requestScreenCapturePermission(() -> {
        if (!isDestroyed() && isCallActive() && currentCallService() != null) {
          setScreenShareEnabled(true);
        }
      }, null);
    }
  }

  private void setScreenShareEnabled (boolean enabled) {
    if (isDestroyed()) {
      return;
    }
    TGCallService service = currentCallService();
    if (service == null) {
      return;
    }
    attachVideoServiceListener();
    boolean success = service.setScreenSharingEnabled(enabled);
    this.outgoingVideoEnabled = service.isVideoOutgoing();
    this.screenSharing = service.isScreenSharing();
    updateVideoVisibility();
    if (enabled && !success) {
      // FGS re-assert or screencast creation failed: surface an error so the user can retry
      // (the toggle has already reset above to reflect that screen sharing isn't live).
      UI.showToast(R.string.VoipScreenShareFailed, android.widget.Toast.LENGTH_SHORT);
    }
  }

  @Override
  public void onCallVideoStateChanged (@VideoState int remoteVideoState, boolean isVideoOutgoing) {
    if (isDestroyed()) {
      return;
    }
    this.remoteVideoState = remoteVideoState;
    this.outgoingVideoEnabled = isVideoOutgoing;
    TGCallService service = currentCallService();
    this.screenSharing = service != null && service.isScreenSharing();
    updateVideoVisibility();
  }

  private boolean isCallActive () {
    return call != null && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
  }

  private void updateVideoVisibility () {
    if (callVideoView == null) {
      return;
    }
    boolean active = isCallActive();
    boolean remoteVisible = active && remoteVideoState == VideoState.ACTIVE;
    boolean localVisible = active && outgoingVideoEnabled;

    callVideoView.setRemoteVisible(remoteVisible);
    callVideoView.setLocalVisible(localVisible);

    // Camera video is "on" only when the outgoing source is the camera (not the screen).
    boolean cameraOutgoing = outgoingVideoEnabled && !screenSharing;
    if (videoButtonView != null) {
      // Show the video toggle once the call is established (video can be enabled on demand).
      videoButtonView.setVisibility(active ? View.VISIBLE : View.GONE);
      videoButtonView.setIsActive(cameraOutgoing, isFocused());
    }
    if (screenShareButtonView != null) {
      // Show the screen-share toggle once the call is established.
      screenShareButtonView.setVisibility(active ? View.VISIBLE : View.GONE);
      screenShareButtonView.setIsActive(screenSharing, isFocused());
    }
    if (switchCameraButtonView != null) {
      // Camera switch only makes sense while the camera (not screen) is the source.
      switchCameraButtonView.setVisibility(localVisible && cameraOutgoing ? View.VISIBLE : View.GONE);
    }
    // Dim the avatar background behind an active remote video so the stream reads clearly.
    avatarView.setAlpha(remoteVisible ? 0f : 1f);
  }

  @Override
  public void destroy () {
    super.destroy();
    Screen.removeStatusBarHeightListener(this);
    tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    tdlib.cache().removeUserDataListener(call.userId, this);
    if (boundVideoService != null) {
      if (videoSinksAttached) {
        boundVideoService.setIncomingVideoOutput(null);
        boundVideoService.setLocalVideoOutput(null);
        videoSinksAttached = false;
      }
      // Compare-and-clear: only unregister if WE are still the bound listener, so a newer
      // CallController that took over the service isn't accidentally unhooked.
      boundVideoService.removeVideoStateListener(this);
      boundVideoService = null;
    }
    if (callVideoView != null) {
      callVideoView.release();
    }
    avatarView.performDestroy();
  }

  @Override
  protected boolean usePopupMode () {
    return true;
  }
}
