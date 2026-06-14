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
 * File created on 15/11/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

public class WebkitController<T> extends ViewController<T> {
  private WebView webView;
  private DoubleHeaderView headerCell;

  public WebkitController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_webkit;
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected final View onCreateView (Context context) {
    headerCell = new DoubleHeaderView(context());
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);

    FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return true;
      }
    };
    ViewSupport.setThemedBackground(contentView, ColorId.filling, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // FIXME android.webkit.WebViewFactory$MissingWebViewPackageException
    webView = new WebView(context);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    configureWebViewSecurity(webView);

    if (hasSpecialProcessing()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        webView.setWebViewClient(new WebViewClient() {
          @Override
          public void onPageFinished (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            if (uri == null || !processSpecial(uri))
              super.onPageFinished(view, url);
          }

          @Override
          public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
            return processSpecial(request.getUrl()) || super.shouldOverrideUrlLoading(view, request);
          }
        });
      } else {
        webView.setWebViewClient(new WebViewClient() {
          @Override
          public void onPageFinished (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            if (uri == null || !processSpecial(uri))
              super.onPageFinished(view, url);
          }

          @Override
          public boolean shouldOverrideUrlLoading (WebView view, String url) {
            Uri uri;
            try {
              uri = Uri.parse(url);
            } catch (Throwable t) {
              uri = null;
            }
            return (uri != null && processSpecial(uri)) || super.shouldOverrideUrlLoading(view, url);
          }
        });
      }
    } else {
      webView.setWebViewClient(new WebViewClient());
    }
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged (WebView view, int newProgress) {
        onPageProgress((float) newProgress / 100f);
      }
    });
    onCreateWebView(headerCell, webView);

    contentView.addView(webView);

    return contentView;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return webView;
  }

  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    if (getArguments() != null && getArguments() instanceof String) {
      headerCell.setSubtitle((String) getArguments());
      loadUrl((String) getArguments());
    }
  }

  protected final void loadUrl (String url) {
    webView.loadUrl(url);
  }

  protected void onPageProgress (float progress) {
    if (headerCell != null) {
      headerCell.animateProgress(progress);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    webView.destroy();
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  /**
   * Applies the security-sensitive WebView settings. The base implementation keeps the
   * historically permissive behavior used by the generic in-app browser, the Telegram FAQ
   * page and HTML5 games (these load only remote http(s) URLs and never need file access).
   *
   * Subclasses that expose a privileged surface — notably {@link WebAppController}, which
   * injects the {@code TelegramWebviewProxy} JS bridge into attacker-influenced bot pages —
   * MUST NOT relax these defaults and should override {@link #allowsMixedContent()},
   * {@link #allowsThirdPartyCookies()} and {@link #allowsFileAccess()} to harden them.
   */
  protected void configureWebViewSecurity (WebView webView) {
    final WebSettings settings = webView.getSettings();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // FIXME maybe better to remove?
      settings.setMixedContentMode(allowsMixedContent() ? WebSettings.MIXED_CONTENT_ALWAYS_ALLOW : WebSettings.MIXED_CONTENT_NEVER_ALLOW);
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, allowsThirdPartyCookies());
    }

    // setAllowFileAccess defaults to true on API < 30; explicitly pin the file/content
    // access flags so they reflect allowsFileAccess() on every API level. Instant View is
    // rendered natively by InstantViewController (no WebView), so this never affects it.
    final boolean allowFileAccess = allowsFileAccess();
    settings.setAllowFileAccess(allowFileAccess);
    settings.setAllowContentAccess(allowFileAccess);
    settings.setAllowFileAccessFromFileURLs(allowFileAccess);
    settings.setAllowUniversalAccessFromFileURLs(allowFileAccess);
  }

  /**
   * @return whether active mixed content (https pages loading http scripts) is allowed.
   * Defaults to {@code true} to preserve the legacy browser behavior; overridden to
   * {@code false} on privileged surfaces.
   */
  protected boolean allowsMixedContent () {
    return true;
  }

  /**
   * @return whether the WebView may set/read third-party cookies. Defaults to {@code true}
   * for the legacy browser behavior; overridden to {@code false} on privileged surfaces.
   */
  protected boolean allowsThirdPartyCookies () {
    return true;
  }

  /**
   * @return whether the WebView may access {@code file://}/{@code content://} resources.
   * Defaults to {@code true} to keep the browser/FAQ/game behavior unchanged; overridden to
   * {@code false} on privileged surfaces so file:// is unreachable from bot-controlled pages.
   */
  protected boolean allowsFileAccess () {
    return true;
  }

  protected boolean hasSpecialProcessing () {
    return false;
  }

  protected boolean processSpecial (Uri url) {
    return false; // override
  }
}
