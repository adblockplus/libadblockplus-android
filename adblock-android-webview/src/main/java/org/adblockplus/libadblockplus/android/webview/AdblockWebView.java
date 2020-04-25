/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.adblockplus.libadblockplus.android.webview;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ClientCertRequest;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;  // makes android min version to be 21
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.SingleInstanceEngineProvider;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl;
import org.adblockplus.libadblockplus.sitekey.SiteKeyException;
import org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier;
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.adblockplus.libadblockplus.HttpClient.STATUS_CODE_OK;
import static org.adblockplus.libadblockplus.android.Utils.convertHeaderEntriesToMap;
import static org.adblockplus.libadblockplus.android.Utils.convertMapToHeaderEntries;

import timber.log.Timber;

/**
 * WebView with ad blocking
 */
public class AdblockWebView extends WebView
{
  protected static final String HEADER_REFERRER = "Referer";
  protected static final String HEADER_REQUESTED_WITH = "X-Requested-With";
  protected static final String HEADER_REQUESTED_WITH_XMLHTTPREQUEST = "XMLHttpRequest";
  protected static final String HEADER_REQUESTED_RANGE = "Range";
  protected static final String HEADER_LOCATION = "Location";
  protected static final String HEADER_SET_COOKIE = "Set-Cookie";
  protected static final String HEADER_COOKIE = "Cookie";
  protected static final String HEADER_USER_AGENT = "User-Agent";
  protected static final String HEADER_ACCEPT = "Accept";
  protected static final String HEADER_REFRESH = "Refresh";

  // use low-case strings as in WebResponse all header keys are lowered-case
  protected static final String HEADER_SITEKEY = "x-adblock-key";
  protected static final String HEADER_CONTENT_TYPE = "content-type";

  private static final String ASSETS_CHARSET_NAME = "UTF-8";
  private static final String BRIDGE_TOKEN = "{{BRIDGE}}";
  private static final String DEBUG_TOKEN = "{{DEBUG}}";
  private static final String HIDE_TOKEN = "{{HIDE}}";
  private static final String HIDDEN_TOKEN = "{{HIDDEN_FLAG}}";
  private static final String BRIDGE = "jsBridge";
  private static final String EMPTY_ELEMHIDE_STRING = "";
  private static final String EMPTY_ELEMHIDE_ARRAY_STRING = "[]";

  // decisions
  private final static String RESPONSE_CHARSET_NAME = "UTF-8";
  private final static String RESPONSE_MIME_TYPE = "text/plain";

  private RegexContentTypeDetector contentTypeDetector = new RegexContentTypeDetector();
  private AtomicReference<AdblockEngineProvider> providerReference = new AtomicReference<>();
  private Integer loadError;
  private WebChromeClient extWebChromeClient;
  private WebViewClient extWebViewClient;
  private WebViewClient intWebViewClient;
  private Map<String, String> url2Referrer = Collections.synchronizedMap(new HashMap<String, String>());
  private String url;
  private String injectJs;
  private String elemhideBlockedJs;
  private CountDownLatch elemHideLatch;
  private AtomicBoolean adblockEnabled;
  private String elemHideSelectorsString;
  private String elemHideEmuSelectorsString;
  private Object elemHideThreadLockObject = new Object();
  private ElemHideThread elemHideThread;
  private boolean loading;
  private String elementsHiddenFlag;
  private AtomicBoolean redirectInProgress = new AtomicBoolean(false);

  /**
   * Listener for ad blocking related events.
   * However, this interface may not be in use if Adblock Plus is disabled.
   */
  public interface EventsListener
  {
    /**
     * Immutable data-class containing an auxiliary information about resource event.
     */
    class ResourceInfo
    {
      private final String requestUrl;
      private final List<String> parentFrameUrls;

      ResourceInfo(final String requestUrl, final List<String> parentFrameUrls)
      {
        this.requestUrl = requestUrl;
        this.parentFrameUrls = new ArrayList<>(parentFrameUrls);
      }

      public String getRequestUrl()
      {
        return requestUrl;
      }

      public List<String> getParentFrameUrls()
      {
        return parentFrameUrls;
      }
    }

    /**
     * Immutable data-class containing an auxiliary information about blocked resource.
     */
    final class BlockedResourceInfo extends ResourceInfo
    {
      private final FilterEngine.ContentType contentType;

      BlockedResourceInfo(final String requestUrl,
                          final List<String> parentFrameUrls,
                          final FilterEngine.ContentType contentType)
      {
        super(requestUrl, parentFrameUrls);
        this.contentType = contentType;
      }

      public FilterEngine.ContentType getContentType()
      {
        return contentType;
      }
    }

    /**
     * Whitelisting reason:
     */
    enum WhitelistReason
    {
      /**
       * Document is whitelisted
       */
      DOCUMENT,

      /**
       * Domain is whitelisted by user
       */
      DOMAIN,

      /**
       * Exception filter
       */
      FILTER
    }

    /**
     * Immutable data-class containing an auxiliary information about whitelisted resource.
     */
    final class WhitelistedResourceInfo extends ResourceInfo
    {
      private WhitelistReason reason;

      public WhitelistedResourceInfo(final String requestUrl,
                                     final List<String> parentFrameUrls,
                                     final WhitelistReason reasons)
      {
        super(requestUrl, parentFrameUrls);
        this.reason = reasons;
      }

      public WhitelistReason getReason()
      {
        return reason;
      }
    }

    /**
     * "Navigation" event.
     *
     * This method is called when the current instance of WebView begins loading of a new page.
     * It corresponds to `onPageStarted` of `WebViewClient` and is called on the UI thread.
     */
    void onNavigation();

    /**
     * "Resource loading blocked" event.
     *
     * This method can be called on a background thread.
     * It should not block the thread for too long as it slows down resource loading.
     * @param info contains auxiliary information about a blocked resource.
     */
    void onResourceLoadingBlocked(final BlockedResourceInfo info);

    /**
     * "Resource loading whitelisted" event.
     *
     * This method can be called on a background thread.
     * It should not block the thread for too long as it slows down resource loading.
     * @param info contains auxiliary information about a blocked resource.
     */
    void onResourceLoadingWhitelisted(final WhitelistedResourceInfo info);
  }

  private AtomicReference<EventsListener> eventsListenerAtomicReference = new AtomicReference<EventsListener>();
  private SiteKeysConfiguration siteKeysConfiguration;
  private AdblockEngine.SettingsChangedListener engineSettingsChangedCb = new AdblockEngine.SettingsChangedListener()
  {
    @Override
    public void onEnableStateChanged(final boolean enabled)
    {
      if (adblockEnabled == null)
      {
        return;
      }
      boolean oldValue = adblockEnabled.getAndSet(enabled);
      if (oldValue != enabled)
      {
        Timber.d("Filter Engine status changed, enable status is %s", adblockEnabled.get());
        AdblockWebView.this.post(new Runnable()
        {
          @Override
          public void run()
          {
            clearCache(true);
          }
        });
      }
    }
  };
  private AdblockEngineProvider.EngineCreatedListener engineCreatedCb = new AdblockEngineProvider.EngineCreatedListener()
  {
    @Override
    public void onAdblockEngineCreated(final AdblockEngine engine)
    {
      adblockEnabled = new AtomicBoolean(engine.isEnabled());
      Timber.d("Filter Engine created, enable status is %s", adblockEnabled.get());
      engine.addSettingsChangedListener(engineSettingsChangedCb);
    }
  };
  private AdblockEngineProvider.EngineDisposedListener engineDisposedCb = new AdblockEngineProvider.EngineDisposedListener()
  {
    @Override
    public void onAdblockEngineDisposed()
    {
      adblockEnabled = null;
    }
  };

  public AdblockWebView(Context context)
  {
    super(context);
    initAbp();
  }

  public AdblockWebView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    initAbp();
  }

  public AdblockWebView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    initAbp();
  }

  private static class ResponseHolder
  {
    ServerResponse response;
  }

  private EventsListener getEventsListener()
  {
    return eventsListenerAtomicReference.get();
  }

  public SiteKeysConfiguration getSiteKeysConfiguration()
  {
    return siteKeysConfiguration;
  }

  public void setSiteKeysConfiguration(final SiteKeysConfiguration siteKeysConfiguration)
  {
    this.siteKeysConfiguration = siteKeysConfiguration;
  }

  /**
   * Sets an implementation of EventsListener which will receive ad blocking related events.
   *
   * @param eventsListener an implementation of EventsListener.
   */
  public void setEventsListener(final EventsListener eventsListener)
  {
    this.eventsListenerAtomicReference.set(eventsListener);
  }

  @Override
  public void setWebChromeClient(final WebChromeClient client)
  {
    extWebChromeClient = client;
  }

  @Override
  public void setWebViewClient(final WebViewClient client)
  {
    extWebViewClient = client;
  }

  @SuppressLint("SetJavaScriptEnabled")
  private void initAbp()
  {
    addJavascriptInterface(this, BRIDGE);
    initRandom();
    buildInjectJs();
    getSettings().setJavaScriptEnabled(true);
    intWebViewClient = new AdblockWebViewClient();
    super.setWebChromeClient(intWebChromeClient);
    super.setWebViewClient(intWebViewClient);
  }

  private AdblockEngineProvider getProvider()
  {
    return providerReference.get();
  }

  private String readScriptFile(String filename) throws IOException
  {
    return Utils
      .readAssetAsString(getContext(), filename, ASSETS_CHARSET_NAME)
      .replace(BRIDGE_TOKEN, BRIDGE)
      .replace(DEBUG_TOKEN, (BuildConfig.DEBUG ? "" : "//"))
      .replace(HIDDEN_TOKEN, elementsHiddenFlag);
  }

  private void runScript(String script)
  {
    Timber.d("runScript started");
    evaluateJavascript(script, null);
    Timber.d("runScript finished");
  }

  public void setProvider(final AdblockEngineProvider provider)
  {
    if (provider == null)
    {
      throw new IllegalArgumentException("Provider cannot be null");
    }

    if (this.getProvider() == provider)
    {
      return;
    }

    final Runnable setRunnable = new Runnable()
    {
      @Override
      public void run()
      {
        AdblockWebView.this.providerReference.set(provider);
        final ReentrantReadWriteLock.ReadLock lock = provider.getReadEngineLock();
        final boolean locked = lock.tryLock();

        try
        {
          // Note that if retain() needs to create a FilterEngine it will wait (in bg thread)
          // until we finish this synchronized block and release the engine lock.
          getProvider().retain(true); // asynchronously
          if (locked && getProvider().getEngine() != null)
          {
            adblockEnabled = new AtomicBoolean(getProvider().getEngine().isEnabled());
            Timber.d("Filter Engine already created, enable status is %s", adblockEnabled);
            getProvider().getEngine().addSettingsChangedListener(engineSettingsChangedCb);
          }
          else
          {
            getProvider().addEngineCreatedListener(engineCreatedCb);
            getProvider().addEngineDisposedListener(engineDisposedCb);
          }
        }
        finally
        {
          if (locked)
          {
            lock.unlock();
          }
        }
      }
    };

    if (this.getProvider() != null)
    {
      // as adblockEngine can be busy with elemhide thread we need to use callback
      this.dispose(setRunnable);
    }
    else
    {
      setRunnable.run();
    }
  }

  private WebChromeClient intWebChromeClient = new WebChromeClient()
  {
    @Override
    public void onPermissionRequest(PermissionRequest request)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onPermissionRequest(request);
      }
      else
      {
        super.onPermissionRequest(request);
      }
    }

    @Override
    public void onPermissionRequestCanceled(PermissionRequest request)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onPermissionRequestCanceled(request);
      }
      else
      {
        super.onPermissionRequestCanceled(request);
      }
    }

    @Override
    public void onReceivedTitle(WebView view, String title)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedTitle(view, title);
      }
      else
      {
        super.onReceivedTitle(view, title);
      }
    }

    @Override
    public void onReceivedIcon(WebView view, Bitmap icon)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedIcon(view, icon);
      }
      else
      {
        super.onReceivedIcon(view, icon);
      }
    }

    @Override
    public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedTouchIconUrl(view, url, precomposed);
      }
      else
      {
        super.onReceivedTouchIconUrl(view, url, precomposed);
      }
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onShowCustomView(view, callback);
      }
      else
      {
        super.onShowCustomView(view, callback);
      }
    }

    @Override
    public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onShowCustomView(view, requestedOrientation, callback);
      }
      else
      {
        super.onShowCustomView(view, requestedOrientation, callback);
      }
    }

    @Override
    public void onHideCustomView()
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onHideCustomView();
      }
      else
      {
        super.onHideCustomView();
      }
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                  Message resultMsg)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
      }
      else
      {
        return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
      }
    }

    @Override
    public void onRequestFocus(WebView view)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onRequestFocus(view);
      }
      else
      {
        super.onRequestFocus(view);
      }
    }

    @Override
    public void onCloseWindow(WebView window)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onCloseWindow(window);
      }
      else
      {
        super.onCloseWindow(window);
      }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsAlert(view, url, message, result);
      }
      else
      {
        return super.onJsAlert(view, url, message, result);
      }
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsConfirm(view, url, message, result);
      }
      else
      {
        return super.onJsConfirm(view, url, message, result);
      }
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                              JsPromptResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsPrompt(view, url, message, defaultValue, result);
      }
      else
      {
        return super.onJsPrompt(view, url, message, defaultValue, result);
      }
    }

    @Override
    public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsBeforeUnload(view, url, message, result);
      }
      else
      {
        return super.onJsBeforeUnload(view, url, message, result);
      }
    }

    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long quota,
                                        long estimatedDatabaseSize, long totalQuota,
                                        WebStorage.QuotaUpdater quotaUpdater)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onExceededDatabaseQuota(url, databaseIdentifier, quota,
          estimatedDatabaseSize, totalQuota, quotaUpdater);
      }
      else
      {
        super.onExceededDatabaseQuota(url, databaseIdentifier, quota,
          estimatedDatabaseSize, totalQuota, quotaUpdater);
      }
    }

    @Override
    public void onReachedMaxAppCacheSize(long requiredStorage, long quota,
                                         WebStorage.QuotaUpdater quotaUpdater)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
      }
      else
      {
        super.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
      }
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin,
                                                   GeolocationPermissions.Callback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onGeolocationPermissionsShowPrompt(origin, callback);
      }
      else
      {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
      }
    }

    @Override
    public void onGeolocationPermissionsHidePrompt()
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onGeolocationPermissionsHidePrompt();
      }
      else
      {
        super.onGeolocationPermissionsHidePrompt();
      }
    }

    @Override
    public boolean onJsTimeout()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsTimeout();
      }
      else
      {
        return super.onJsTimeout();
      }
    }

    @Override
    public void onConsoleMessage(String message, int lineNumber, String sourceID)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onConsoleMessage(message, lineNumber, sourceID);
      }
      else
      {
        super.onConsoleMessage(message, lineNumber, sourceID);
      }
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage)
    {
      Timber.d("JS: level=%s, message=\"%s\", sourceId=\"%s\", line=%d",
              consoleMessage.messageLevel(),
              consoleMessage.message(),
              consoleMessage.sourceId(),
              consoleMessage.lineNumber());

      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onConsoleMessage(consoleMessage);
      }
      else
      {
        return super.onConsoleMessage(consoleMessage);
      }
    }

    @Override
    public Bitmap getDefaultVideoPoster()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.getDefaultVideoPoster();
      }
      else
      {
        return super.getDefaultVideoPoster();
      }
    }

    @Override
    public View getVideoLoadingProgressView()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.getVideoLoadingProgressView();
      }
      else
      {
        return super.getVideoLoadingProgressView();
      }
    }

    @Override
    public void getVisitedHistory(ValueCallback<String[]> callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.getVisitedHistory(callback);
      }
      else
      {
        super.getVisitedHistory(callback);
      }
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress)
    {
      if (redirectInProgress.get())
      {
        Timber.d("Skipping onProgressChanged to %d%% for url: %s", newProgress, view.getUrl());
        return;
      }
      Timber.d("onProgressChanged to %d%% for url: %s", newProgress, view.getUrl());
      tryInjectJs();

      if (extWebChromeClient != null)
      {
        extWebChromeClient.onProgressChanged(view, newProgress);
      }
      else
      {
        super.onProgressChanged(view, newProgress);
      }
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onShowFileChooser(webView, filePathCallback, fileChooserParams);
      }
      else
      {
        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
      }
    }
  };

  private void tryInjectJs()
  {
    if (adblockEnabled == null || !adblockEnabled.get())
    {
      return;
    }
    if (loadError == null && injectJs != null)
    {
      Timber.d("Injecting script");
      runScript(injectJs);
    }
  }

  private void clearReferrers()
  {
    Timber.d("Clearing referrers");
    url2Referrer.clear();
  }

  private enum AbpShouldBlockResult
  {
    // FilterEngine is released or
    // ABP enabled state is unknown or
    // ABP enabled state is "disabled"
    NOT_ENABLED,

    // Allow loading (with further sitekey-related routines)
    ALLOW_LOAD,

    // Block loading
    BLOCK_LOAD,
  }

  private static class WebResponseResult
  {
    static final WebResourceResponse ALLOW_LOAD = null;
    static final WebResourceResponse BLOCK_LOAD =
            new WebResourceResponse(RESPONSE_MIME_TYPE, RESPONSE_CHARSET_NAME, null);
  }

  /**
   * WebViewClient for API 21 and newer
   * (has Referrer since it overrides `shouldInterceptRequest(..., request)` with referrer)
   */
  private class AdblockWebViewClient extends WebViewClient
  {
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
    {
      if (extWebViewClient != null)
      {
        return extWebViewClient.shouldOverrideUrlLoading(view, request);
      }
      else
      {
        return super.shouldOverrideUrlLoading(view, request);
      }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url)
    {
      if (extWebViewClient != null)
      {
        return extWebViewClient.shouldOverrideUrlLoading(view, url);
      }
      else
      {
        return super.shouldOverrideUrlLoading(view, url);
      }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon)
    {
      Timber.d("onPageStarted called for url %s", url);
      if (loading)
      {
        stopAbpLoading();
      }

      startAbpLoading(url);

      notifyNavigation();

      if (extWebViewClient != null)
      {
        extWebViewClient.onPageStarted(view, url, favicon);
      }
      else
      {
        super.onPageStarted(view, url, favicon);
      }
    }

    @Override
    public void onPageFinished(WebView view, String url)
    {
      if (redirectInProgress.get())
      {
        Timber.d("Skipping onPageFinished for url: %s", url);
        redirectInProgress.set(false);
        return;
      }
      Timber.d("onPageFinished called for url %s", url);
      loading = false;
      if (extWebViewClient != null)
      {
        extWebViewClient.onPageFinished(view, url);
      }
      else
      {
        super.onPageFinished(view, url);
      }
    }

    @Override
    public void onLoadResource(WebView view, String url)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onLoadResource(view, url);
      }
      else
      {
        super.onLoadResource(view, url);
      }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onPageCommitVisible(WebView view, String url)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onPageCommitVisible(view, url);
      }
      else
      {
        super.onPageCommitVisible(view, url);
      }
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail)
    {
      if (extWebViewClient != null)
      {
        return extWebViewClient.onRenderProcessGone(view, detail);
      }
      else
      {
        return super.onRenderProcessGone(view, detail);
      }
    }

    @TargetApi(Build.VERSION_CODES.O_MR1)
    @Override
    public void onSafeBrowsingHit(WebView view, WebResourceRequest request,
                                  int threatType, SafeBrowsingResponse callback)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onSafeBrowsingHit(view, request, threatType, callback);
      }
      else
      {
        super.onSafeBrowsingHit(view, request, threatType, callback);
      }
    }

    public void onReceivedClientCertRequest(WebView view, ClientCertRequest request)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedClientCertRequest(view, request);
      }
      else
      {
        super.onReceivedClientCertRequest(view, request);
      }
    }

    @Override
    public void onTooManyRedirects(WebView view, Message cancelMsg, Message continueMsg)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onTooManyRedirects(view, cancelMsg, continueMsg);
      }
      else
      {
        super.onTooManyRedirects(view, cancelMsg, continueMsg);
      }
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
    {
      Timber.e("onReceivedError:" +
        " code=%d" +
        " with description=%s" +
        " for url=%s" +
        " redirectInProgress.get()=%s",
        errorCode, description, failingUrl, redirectInProgress.get());
      loadError = errorCode;

      if (redirectInProgress.get())
      {
        Timber.d("Skipping onReceivedError for redirection");
        return;
      }

      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedError(view, errorCode, description, failingUrl);
      }
      else
      {
        super.onReceivedError(view, errorCode, description, failingUrl);
      }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error)
    {
      Timber.e("onReceivedError:" +
              " code=%d" +
              " with description=%s" +
              " for url=%s" +
              " redirectInProgress.get()=%s" +
              " request.isForMainFrame()=%s",
              error.getErrorCode(), error.getDescription(), request.getUrl(),
              redirectInProgress.get(), request.isForMainFrame());

      if (redirectInProgress.get() && request.isForMainFrame())
      {
        Timber.d("Skipping onReceivedError for redirection");
        return;
      }

      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedError(view, request, error);
      }
      else
      {
        super.onReceivedError(view, request, error);
      }
    }

    @Override
    public void onFormResubmission(WebView view, Message dontResend, Message resend)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onFormResubmission(view, dontResend, resend);
      }
      else
      {
        super.onFormResubmission(view, dontResend, resend);
      }
    }

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.doUpdateVisitedHistory(view, url, isReload);
      }
      else
      {
        super.doUpdateVisitedHistory(view, url, isReload);
      }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedSslError(view, handler, error);
      }
      else
      {
        super.onReceivedSslError(view, handler, error);
      }
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedHttpAuthRequest(view, handler, host, realm);
      }
      else
      {
        super.onReceivedHttpAuthRequest(view, handler, host, realm);
      }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                    WebResourceResponse errorResponse)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedHttpError(view, request, errorResponse);
      }
      else
      {
        super.onReceivedHttpError(view, request, errorResponse);
      }
    }

    @Override
    public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event)
    {
      if (extWebViewClient != null)
      {
        return extWebViewClient.shouldOverrideKeyEvent(view, event);
      }
      else
      {
        return super.shouldOverrideKeyEvent(view, event);
      }
    }

    @Override
    public void onUnhandledKeyEvent(WebView view, KeyEvent event)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onUnhandledKeyEvent(view, event);
      }
      else
      {
        super.onUnhandledKeyEvent(view, event);
      }
    }

    @Override
    public void onScaleChanged(WebView view, float oldScale, float newScale)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onScaleChanged(view, oldScale, newScale);
      }
      else
      {
        super.onScaleChanged(view, oldScale, newScale);
      }
    }

    @Override
    public void onReceivedLoginRequest(WebView view, String realm, String account, String args)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedLoginRequest(view, realm, account, args);
      }
      else
      {
        super.onReceivedLoginRequest(view, realm, account, args);
      }
    }

    private AbpShouldBlockResult shouldAbpBlockRequest(final WebResourceRequest request)
    {
      // here we just trying to fill url -> referrer map
      final String url = request.getUrl().toString();

      final Map<String, String> requestHeadersMap = request.getRequestHeaders();

      final boolean isXmlHttpRequest =
              request.getRequestHeaders().containsKey(HEADER_REQUESTED_WITH) &&
                      HEADER_REQUESTED_WITH_XMLHTTPREQUEST.equals(
                              request.getRequestHeaders().get(HEADER_REQUESTED_WITH));

      final boolean isMainFrame = request.isForMainFrame();

      final String referrer = request.getRequestHeaders().get(HEADER_REFERRER);

      final Lock lock = getProvider().getReadEngineLock();
      lock.lock();

      try
      {
        // if dispose() was invoke, but the page is still loading then just let it go
        boolean isDisposed = false;
        if (getProvider().getCounter() == 0)
        {
          isDisposed = true;
        }
        else
        {
          lock.unlock();
          getProvider().waitForReady();
          lock.lock();
          if (getProvider().getCounter() == 0)
          {
            isDisposed = true;
          }
        }

        if (isDisposed)
        {
          Timber.e("FilterEngine already disposed");
          return AbpShouldBlockResult.NOT_ENABLED;
        }

        if (adblockEnabled == null)
        {
          Timber.e("No adblockEnabled value");
          return AbpShouldBlockResult.NOT_ENABLED;
        }
        else
        {
          // check the real enable status and update adblockEnabled flag which is used
          // later on to check if we should execute element hiding JS
          adblockEnabled.set(getProvider().getEngine().isEnabled());
          if (!adblockEnabled.get())
          {
            Timber.d("adblockEnabled = false");
            return AbpShouldBlockResult.NOT_ENABLED;
          }
        }

        Timber.d("Loading url %s", url);

        if (referrer != null)
        {
          Timber.d("Header referrer for " + url + " is " + referrer);
          if (!url.equals(referrer))
          {
            url2Referrer.put(url, referrer);
          }
          else
          {
            Timber.w("Header referrer value is the same as url, skipping url2Referrer.put()");
          }
        }
        else
        {
          Timber.w("No referrer header for %s", url);
        }

        // reconstruct frames hierarchy
        List<String> referrerChain = new ArrayList<>();
        String parent = url;
        while ((parent = url2Referrer.get(parent)) != null)
        {
          if (referrerChain.contains(parent))
          {
            Timber.w("Detected referrer loop, finished creating referrers list");
            break;
          }
          referrerChain.add(0, parent);
        }

        if (isMainFrame)
        {
          // never blocking main frame requests, just subrequests
          Timber.w("%s is main frame, allow loading", url);
        }
        else
        {
          final String siteKey = (siteKeysConfiguration != null
            ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
              .getAny(referrerChain, ""))
            : null);

          // whitelisted
          if (getProvider().getEngine().isDomainWhitelisted(url, referrerChain))
          {
            Timber.w("%s domain is whitelisted, allow loading", url);
            notifyResourceWhitelisted(new EventsListener.WhitelistedResourceInfo(
                url, referrerChain, EventsListener.WhitelistReason.DOMAIN));
          }
          else if (getProvider().getEngine().isDocumentWhitelisted(url, referrerChain, siteKey))
          {
            Timber.w("%s document is whitelisted, allow loading", url);
            notifyResourceWhitelisted(new EventsListener.WhitelistedResourceInfo(
                url, referrerChain, EventsListener.WhitelistReason.DOCUMENT));
          }
          else
          {
            // determine the content
            FilterEngine.ContentType contentType;
            if (isXmlHttpRequest)
            {
              contentType = FilterEngine.ContentType.XMLHTTPREQUEST;
            }
            else
            {
              contentType = contentTypeDetector.detect(url);
              if (contentType == null)
              {
                Timber.w("contentTypeDetector didn't recognize content type");
                final String acceptType = requestHeadersMap.get(HEADER_ACCEPT);
                if (acceptType != null && acceptType.contains("text/html"))
                {
                  Timber.w("using subdocument content type");
                  contentType = FilterEngine.ContentType.SUBDOCUMENT;
                }
                else
                {
                  Timber.w("using other content type");
                  contentType = FilterEngine.ContentType.OTHER;
                }
              }
            }

            boolean specificOnly = false;
            if (!referrerChain.isEmpty())
            {
              final String parentUrl = referrerChain.get(0);
              final List<String> referrerChainForGenericblock = referrerChain.subList(1, referrerChain.size());
              specificOnly = getProvider().getEngine().isGenericblockWhitelisted(parentUrl,
                      referrerChainForGenericblock, siteKey);
              if (specificOnly)
              {
                Timber.w("Found genericblock filter for url %s which parent is %s", url, parentUrl);
              }
            }

            // check if we should block
            final AdblockEngine.MatchesResult result = getProvider().getEngine().matches(
                url, FilterEngine.ContentType.maskOf(contentType),
                referrerChain, siteKey, specificOnly);

            if (result == AdblockEngine.MatchesResult.NOT_WHITELISTED)
            {
              Timber.w("Blocked loading %s", url);

              if (isVisibleResource(contentType))
              {
                elemhideBlockedResource(url);
              }

              notifyResourceBlocked(new EventsListener.BlockedResourceInfo(
                  url, referrerChain, contentType));
              return AbpShouldBlockResult.BLOCK_LOAD;
            }
            else if (result == AdblockEngine.MatchesResult.WHITELISTED)
            {
              Timber.w("%s is whitelisted in matches()", url);
              notifyResourceWhitelisted(new EventsListener.WhitelistedResourceInfo(
                  url, referrerChain, EventsListener.WhitelistReason.FILTER));
            }
            Timber.d("Allowed loading %s", url);
          }
        }
      }
      finally
      {
        lock.unlock();
      }

      // we rely on calling `fetchUrlAndCheckSiteKey`
      // later in `shouldInterceptRequest`
      // now we just reply that ist fine to load
      // the resource
      return AbpShouldBlockResult.ALLOW_LOAD;
    }

    private WebResourceResponse fetchUrlAndCheckSiteKey(final WebView webview, String url,
                                                        final Map<String, String> requestHeadersMap,
                                                        final String requestMethod)
    {
      if (siteKeysConfiguration == null ||
          !requestMethod.equalsIgnoreCase(HttpClient.REQUEST_METHOD_GET))
      {
        // for now we handle site key only for GET requests
        return WebResponseResult.ALLOW_LOAD;
      }

      Timber.d("fetchUrlAndCheckSiteKey() called from Thread %s", Thread.currentThread().getId());
      final boolean autoFollowRedirect = webview == null;
      final ResponseHolder responseHolder = new ResponseHolder();
      final CountDownLatch latch = new CountDownLatch(1);
      final HttpClient.Callback callback = new HttpClient.Callback()
      {
        @Override
        public void onFinished(final ServerResponse response_)
        {
          responseHolder.response = response_;
          latch.countDown();
        }
      };

      try
      {
        final List<HeaderEntry> headersList = convertMapToHeaderEntries(requestHeadersMap);
        final String cookieValue = CookieManager.getInstance().getCookie(url);
        if (cookieValue != null && !cookieValue.isEmpty())
        {
          Timber.d("Adding %s request header for url %s", HEADER_COOKIE, url);
          headersList.add(new HeaderEntry(HEADER_COOKIE, cookieValue));
        }

        final HttpRequest request = new HttpRequest(url, requestMethod, headersList, autoFollowRedirect, true);
        siteKeysConfiguration.getHttpClient().request(request, callback);
      }
      catch (final AdblockPlusException e)
      {
        Timber.e(e, "WebRequest failed");
        // allow WebView to continue, repeating the request and handling the response
        return WebResponseResult.ALLOW_LOAD;
      }

      try
      {
        latch.await();
      }
      catch (final InterruptedException e)
      {
        // error waiting for the response, continue by returning null
        return WebResponseResult.ALLOW_LOAD;
      }

      final ServerResponse response = responseHolder.response;
      final ServerResponse.NsStatus status = response.getStatus();
      int statusCode = response.getResponseStatus();

      // in some circumstances statusCode gets > 599
      if (!HttpClient.isStatusAllowed(statusCode))
      {
        // looks like the response is just broken
        // let it go
        return WebResponseResult.ALLOW_LOAD;
      }

      final List<HeaderEntry> responseHeaders = response.getResponseHeaders();
      for (final HeaderEntry eachEntry : responseHeaders)
      {
        if (HEADER_SET_COOKIE.equalsIgnoreCase(eachEntry.getKey()))
        {
          Timber.d("Calling setCookie() for url %s", url);
          CookieManager.getInstance().setCookie(url, eachEntry.getValue());
        }
      }

      if (HttpClient.isRedirectCode(statusCode))
      {
        if (webview != null)
        {
          return reloadWebViewUrl(url, responseHolder);
        }
        return WebResponseResult.ALLOW_LOAD;
      }

      if (response.getFinalUrl() != null)
      {
        Timber.d("Updating url to %s, was (%s)", response.getFinalUrl(), url);
        url = response.getFinalUrl();
      }

      final Map<String, String> responseHeadersMap = convertHeaderEntriesToMap(responseHeaders);

      verifySiteKeysInHeaders(siteKeysConfiguration.getSiteKeyVerifier(),
              url,
              requestHeadersMap,
              responseHeadersMap);

      final String responseContentType = responseHeadersMap.get(HEADER_CONTENT_TYPE);
      String responseMimeType = null;
      String responseEncoding = null;
      if (responseContentType != null)
      {
        final int semicolonPos = responseContentType.indexOf(";");
        if (semicolonPos > 0)
        {
          responseMimeType = responseContentType.substring(0, semicolonPos);
          final String charsetKey = "charset=";
          final int charsetPos = responseContentType.indexOf(charsetKey);
          if ((charsetPos >= 0) && (charsetPos < responseContentType.length() - charsetKey.length()))
          {
            responseEncoding = responseContentType.substring(charsetPos + charsetKey.length());
          }
        }
        else if (responseContentType.indexOf("/") > 0)
        {
          responseMimeType = responseContentType;
        }
      }

      /**
       * Quoting https://developer.android.com/reference/android/webkit/WebResourceResponse:
       * Do not use the value of a HTTP Content-Encoding header for encoding, as that header does not
       * specify a character encoding. Content without a defined character encoding (for example image
       * resources) should pass null for encoding.
       * TODO: Include here other contentTypes also, not only "image".
       */
      if ((responseEncoding != null) && (responseMimeType != null) && responseMimeType.startsWith("image"))
      {
        Timber.d("Setting responseEncoding to null for contentType == %s (url == %s)", responseMimeType, url);
        responseEncoding = null;
      }

      if (response.getInputStream() != null)
      {
        Timber.d("Using responseMimeType and responseEncoding: %s => %s (url == %s)", responseMimeType, responseEncoding, url);
        return new WebResourceResponse(
                responseMimeType, responseEncoding,
                statusCode, getReasonPhrase(status),
                responseHeadersMap, response.getInputStream());
      }
      Timber.w("fetchUrlAndCheckSiteKey() passes control to WebView");
      return WebResponseResult.ALLOW_LOAD;
    }

    /**
     * Goes over responseHeaders and does a sitekey verification
     *
     * Passing responseHeaders in Map just not to convert them
     * to HeaderEntries back and forth
     */
    private void verifySiteKeysInHeaders(@NotNull SiteKeyVerifier verifier,
                                         String url,
                                         Map<String, String> requestHeadersMap,
                                         Map<String, String> responseHeaders) {
      for (Map.Entry<String, String> header : responseHeaders.entrySet())
      {
        if (header.getKey().equals(HEADER_SITEKEY))
        {
          // verify signature and save public key to be used as sitekey for next requests
          try
          {
            if (verifier.verify(
                Utils.getUrlWithoutAnchor(url), requestHeadersMap.get(HEADER_USER_AGENT), header.getValue()))
            {
              Timber.d("Url %s public key verified successfully", url);
            }
            else
            {
              Timber.e("Url %s public key is not verified", url);
            }
          }
          catch (final SiteKeyException e)
          {
            Timber.e(e, "Failed to verify sitekey header");
          }
          break;
        }
      }
    }

    private WebResourceResponse reloadWebViewUrl(final String url,
                                                 final ResponseHolder responseHolder)
    {
      String redirectedUrl = null;
      for (final HeaderEntry header : responseHolder.response.getResponseHeaders())
      {
        if (header.getKey().equalsIgnoreCase(HEADER_LOCATION) &&
            header.getValue() != null && !header.getValue().isEmpty())
        {
          redirectedUrl = header.getValue();
          try
          {
            // check and handle relative url redirection
            if (!Utils.isAbsoluteUrl(redirectedUrl))
            {
              redirectedUrl = Utils.getAbsoluteUrl(url, redirectedUrl);
            }
          }
          catch (final Exception e)
          {
            Timber.e(e, "Failed to build absolute redirect URL");
            redirectedUrl = null;
          }
        }
      }

      if (redirectedUrl != null)
      {
        Timber.d("redirecting a webview from %s to %s", url, redirectedUrl);
        // we need to reload webview url to make it aware of new new url after redirection
        redirectInProgress.set(true);
        final Map<String, String> responseHeaders = Collections.singletonMap(HEADER_REFRESH, "0; url=" + redirectedUrl);
        return new WebResourceResponse(RESPONSE_MIME_TYPE, RESPONSE_CHARSET_NAME, STATUS_CODE_OK,
                "OK", responseHeaders, new ByteArrayInputStream(new byte[] {}));
      }
      return WebResponseResult.ALLOW_LOAD;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)
    {
      final AbpShouldBlockResult abpBlockResult = shouldAbpBlockRequest(request);

      // if FilterEngine is unavailable or not enabled, just let it go (and skip sitekey check)
      if (AbpShouldBlockResult.NOT_ENABLED.equals(abpBlockResult))
      {
        return WebResponseResult.ALLOW_LOAD;
      }

      // if url should be blocked, we are not performing any further actions
      if (AbpShouldBlockResult.BLOCK_LOAD.equals(abpBlockResult))
      {
        return WebResponseResult.BLOCK_LOAD;
      }

      final Map<String, String> requestHeaders = request.getRequestHeaders();
      final String url = request.getUrl().toString();

      if (extWebViewClient != null)
      {
        // allow external WebViewClient to perform and intercept requests
        // its fine to block shouldAbpBlockRequest and wait
        final WebResourceResponse externalResponse
                = extWebViewClient.shouldInterceptRequest(view, request);

        // if we are having an external WebResourceResponse
        // provided by external WebViewClient
        // we will do the sitekey verification
        // and just return the Response
        if (externalResponse != null)
        {
          Timber.d("Verifying site keys with external shouldInterceptRequest response");
          verifySiteKeysInHeaders(siteKeysConfiguration.getSiteKeyVerifier(),
                  url,
                  requestHeaders,
                  externalResponse.getResponseHeaders());
          Timber.d("Finished verifying, returning external response and stop");
          return externalResponse;
        }
      }

      if (requestHeaders.containsKey(HEADER_REQUESTED_RANGE))
      {
        Timber.d("Skipping site key check for the request with a Range header");
        return WebResponseResult.ALLOW_LOAD;
      }

      return fetchUrlAndCheckSiteKey(request.isForMainFrame() ? view : null,
              url,
              requestHeaders,
              request.getMethod());
    }
  }

  private void notifyNavigation()
  {
    final EventsListener eventsListener = getEventsListener();
    if (eventsListener != null)
    {
      eventsListener.onNavigation();
    }
  }

  private void notifyResourceBlocked(final EventsListener.BlockedResourceInfo info)
  {
    final EventsListener eventsListener = getEventsListener();
    if (eventsListener != null)
    {
      eventsListener.onResourceLoadingBlocked(info);
    }
  }

  private void notifyResourceWhitelisted(final EventsListener.WhitelistedResourceInfo info)
  {
    final EventsListener eventsListener = getEventsListener();
    if (eventsListener != null)
    {
      eventsListener.onResourceLoadingWhitelisted(info);
    }
  }

  private boolean isVisibleResource(final FilterEngine.ContentType contentType)
  {
    return
        contentType == FilterEngine.ContentType.IMAGE ||
        contentType == FilterEngine.ContentType.MEDIA ||
        contentType == FilterEngine.ContentType.OBJECT ||
        contentType == FilterEngine.ContentType.SUBDOCUMENT;
  }

  private void elemhideBlockedResource(final String url)
  {
    Timber.d("Trying to elemhide visible blocked resource with url: " + url);
    final String filenameWithQuery;
    try
    {
      filenameWithQuery = Utils.extractPathWithQuery(url);
    }
    catch (final MalformedURLException e)
    {
      Timber.e("Failed to parse URI for blocked resource:" + url + ". Skipping element hiding");
      return;
    }

    /*
    It finds all the elements with source URLs ending with ... and then compare full paths.
    We do this trick because the paths in JS (code) can be relative and in DOM tree they are absolute.
     */
    final StringBuilder selectorBuilder = new StringBuilder();
    selectorBuilder.append("[src$='");
    selectorBuilder.append(filenameWithQuery);
    selectorBuilder.append("'], [srcset$='");
    selectorBuilder.append(filenameWithQuery);
    selectorBuilder.append("']");

    // all UI views including AdblockWebView can be touched from UI thread only
    post(new Runnable()
    {
      @Override
      public void run()
      {
        final StringBuilder scriptBuilder = new StringBuilder(elemhideBlockedJs);
        scriptBuilder.append("\n\n");
        scriptBuilder.append("elemhideForSelector(\"");
        scriptBuilder.append(url); // 1st argument
        scriptBuilder.append("\", \"");
        scriptBuilder.append(Utils.escapeJavaScriptString(selectorBuilder.toString())); // 2nd argument
        scriptBuilder.append("\", 0)"); // attempt #0

        AdblockWebView.this.evaluateJavascript(scriptBuilder.toString(), null);
      }
    });
  }

  private String getReasonPhrase(ServerResponse.NsStatus status)
  {
    return status.name().replace("_", "");
  }

  private void initRandom()
  {
    elementsHiddenFlag = "abp" + Math.abs(new Random().nextLong());
  }

  private class ElemHideThread extends Thread
  {
    private String stylesheetString;
    private String emuSelectorsString;
    private CountDownLatch finishedLatch;
    private AtomicBoolean isFinished;
    private AtomicBoolean isCancelled;

    public ElemHideThread(CountDownLatch finishedLatch)
    {
      this.finishedLatch = finishedLatch;
      isFinished = new AtomicBoolean(false);
      isCancelled = new AtomicBoolean(false);
    }

    @Override
    public void run()
    {
      final Lock lock = getProvider().getReadEngineLock();
      lock.lock();

      try
      {
        boolean isDisposed = false;
        if (getProvider().getCounter() == 0)
        {
          isDisposed = true;
        }
        else
        {
          lock.unlock();
          getProvider().waitForReady();
          lock.lock();
          if (getProvider().getCounter() == 0)
          {
            isDisposed = true;
          }
        }

        if (isDisposed)
        {
          Timber.w("FilterEngine already disposed");
          stylesheetString = EMPTY_ELEMHIDE_STRING;
          emuSelectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
        }
        else
        {
          List<String> referrerChain = new ArrayList<String>(1);
          referrerChain.add(url);
          String parentUrl = url;
          while ((parentUrl = url2Referrer.get(parentUrl)) != null)
          {
            if (referrerChain.contains(parentUrl))
            {
              Timber.w("Detected referrer loop, finished creating referrers list");
              break;
            }
            referrerChain.add(0, parentUrl);
          }

          final FilterEngine filterEngine = getProvider().getEngine().getFilterEngine();

          List<Subscription> subscriptions = filterEngine.getListedSubscriptions();

          try
          {
            Timber.d("Listed subscriptions: %d", subscriptions.size());
            if (BuildConfig.DEBUG)
            {
              for (Subscription eachSubscription : subscriptions)
              {
                Timber.d("Subscribed to "
                  + (eachSubscription.isDisabled() ? "disabled" : "enabled")
                  + " " + eachSubscription);
              }
            }
          }
          finally
          {
            for (Subscription eachSubscription : subscriptions)
            {
              eachSubscription.dispose();
            }
          }

          final String domain = filterEngine.getHostFromURL(url);
          if (domain == null)
          {
            Timber.e("Failed to extract domain from %s", url);
            stylesheetString = EMPTY_ELEMHIDE_STRING;
            emuSelectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
          }
          else
          {
            // elemhide
            Timber.d("Requesting elemhide selectors from AdblockEngine for %s in %s",
                    url, this);

            final String siteKey = (siteKeysConfiguration != null
              ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
                .getAny(referrerChain, ""))
              : null);

            final boolean specificOnly = filterEngine.matches(url,
              FilterEngine.ContentType.maskOf(FilterEngine.ContentType.GENERICHIDE),
              Collections.<String>emptyList(), null) != null;

            if (specificOnly)
            {
              Timber.d("elemhide - specificOnly selectors");
            }

            stylesheetString = getProvider()
              .getEngine()
              .getElementHidingStyleSheet(url, domain, referrerChain, siteKey, specificOnly);

            Timber.d("Finished requesting elemhide stylesheet, got %d symbols in %s",
                    stylesheetString.length(), this);

            // elemhideemu
            Timber.d("Requesting elemhideemu selectors from AdblockEngine for %s in %s",
                    url, this);
            List<FilterEngine.EmulationSelector> emuSelectors = getProvider()
              .getEngine()
              .getElementHidingEmulationSelectors(url, domain, referrerChain, siteKey);

            Timber.d("Finished requesting elemhideemu selectors, got  got %d in %s",
                    emuSelectors.size(), this);
            emuSelectorsString = Utils.emulationSelectorListToJsonArray(emuSelectors);
          }
        }
      }
      finally
      {
        lock.unlock();
        if (isCancelled.get())
        {
          Timber.w("This thread is cancelled, exiting silently %s", this);
        }
        else
        {
          finish(stylesheetString, emuSelectorsString);
        }
      }

    }

    private void onFinished()
    {
      finishedLatch.countDown();
      synchronized (finishedRunnableLockObject)
      {
        if (finishedRunnable != null)
        {
          finishedRunnable.run();
        }
      }
    }

    private void finish(String selectorsString, String emuSelectorsString)
    {
      isFinished.set(true);
      Timber.d("Setting elemhide string %d bytes", selectorsString.length());
      elemHideSelectorsString = selectorsString;

      Timber.d("Setting elemhideemu string %d bytes", emuSelectorsString.length());
      elemHideEmuSelectorsString = emuSelectorsString;

      onFinished();
    }

    private final Object finishedRunnableLockObject = new Object();
    private Runnable finishedRunnable;

    public void setFinishedRunnable(Runnable runnable)
    {
      synchronized (finishedRunnableLockObject)
      {
        this.finishedRunnable = runnable;
      }
    }

    public void cancel()
    {
      Timber.w("Cancelling elemhide thread %s", this);
      if (isFinished.get())
      {
        Timber.w("This thread is finished, exiting silently %s", this);
      }
      else
      {
        isCancelled.set(true);
        finish(EMPTY_ELEMHIDE_STRING, EMPTY_ELEMHIDE_ARRAY_STRING);
      }
    }
  }

  private Runnable elemHideThreadFinishedRunnable = new Runnable()
  {
    @Override
    public void run()
    {
      synchronized (elemHideThreadLockObject)
      {
        Timber.w("elemHideThread set to null");
        elemHideThread = null;
      }
    }
  };

  private void ensureProvider()
  {
    // if AdblockWebView works as drop-in replacement for WebView 'provider' is not set.
    // Thus AdblockWebView is using SingleInstanceEngineProvider instance
    if (getProvider() == null)
    {
      setProvider(new SingleInstanceEngineProvider(
        getContext(), AdblockEngine.BASE_PATH_DIRECTORY, BuildConfig.DEBUG));
    }
  }

  private void startAbpLoading(String newUrl)
  {
    Timber.d("Start loading %s", newUrl);

    loading = true;
    loadError = null;
    url = newUrl;

    if (url != null)
    {
      // elemhide and elemhideemu
      elemHideLatch = new CountDownLatch(1);
      synchronized (elemHideThreadLockObject)
      {
        elemHideThread = new ElemHideThread(elemHideLatch);
        elemHideThread.setFinishedRunnable(elemHideThreadFinishedRunnable);
        elemHideThread.start();
      }
    }
    else
    {
      elemHideLatch = null;
    }
  }

  private void buildInjectJs()
  {
    try
    {
      if (injectJs == null)
      {
        StringBuffer sb = new StringBuffer();
        sb.append(readScriptFile("inject.js").replace(HIDE_TOKEN, readScriptFile("css.js")));
        sb.append(readScriptFile("elemhideemu.js"));
        injectJs = sb.toString();
      }

      if (elemhideBlockedJs == null)
      {
        elemhideBlockedJs = readScriptFile("elemhideblocked.js");
      }
    }
    catch (final IOException e)
    {
      Timber.e(e, "Failed to read script");
    }
  }

  @Override
  public void goBack()
  {
    if (loading)
    {
      stopAbpLoading();
    }

    super.goBack();
  }

  @Override
  public void goForward()
  {
    if (loading)
    {
      stopAbpLoading();
    }

    super.goForward();
  }

  @Override
  public void reload()
  {
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.reload();
  }

  @Override
  public void loadUrl(String url)
  {
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadUrl(url);
  }

  @Override
  public void loadUrl(String url, Map<String, String> additionalHttpHeaders)
  {
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadUrl(url, additionalHttpHeaders);
  }

  @Override
  public void loadData(String data, String mimeType, String encoding)
  {
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadData(data, mimeType, encoding);
  }

  @Override
  public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding,
                                  String historyUrl)
  {
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
  }

  @Override
  public void stopLoading()
  {
    stopAbpLoading();
    super.stopLoading();
  }

  private void stopAbpLoading()
  {
    Timber.d("Stop abp loading");

    loading = false;
    clearReferrers();

    synchronized (elemHideThreadLockObject)
    {
      if (elemHideThread != null)
      {
        elemHideThread.cancel();
      }
    }
  }

  // warning: do not rename (used in injected JS by method name)
  @JavascriptInterface
  public String getElemhideStyleSheet()
  {
    if (elemHideLatch == null)
    {
      return EMPTY_ELEMHIDE_STRING;
    }
    else
    {
      try
      {
        // elemhide selectors list getting is started in startAbpLoad() in background thread
        Timber.d("Waiting for elemhide selectors to be ready");
        elemHideLatch.await();
        Timber.d("Elemhide selectors ready, %d bytes", elemHideSelectorsString.length());

        return elemHideSelectorsString;
      }
      catch (final InterruptedException e)
      {
        Timber.w("Interrupted, returning empty selectors list");
        return EMPTY_ELEMHIDE_STRING;
      }
    }
  }

  // warning: do not rename (used in injected JS by method name)
  @JavascriptInterface
  public String getElemhideEmulationSelectors()
  {
    if (elemHideLatch == null)
    {
      return EMPTY_ELEMHIDE_ARRAY_STRING;
    }
    else
    {
      try
      {
        // elemhideemu selectors list getting is started in startAbpLoad() in background thread
        Timber.d("Waiting for elemhideemu selectors to be ready");
        elemHideLatch.await();
        Timber.d("Elemhideemu selectors ready, %d bytes", elemHideEmuSelectorsString.length() );

        return elemHideEmuSelectorsString;
      }
      catch (final InterruptedException e)
      {
        Timber.w("Interrupted, returning empty elemhideemu selectors list");
        return EMPTY_ELEMHIDE_ARRAY_STRING;
      }
    }
  }

  private void doDispose()
  {
    Timber.w("Disposing AdblockEngine");
    getProvider().release();
  }

  private class DisposeRunnable implements Runnable
  {
    private Runnable disposeFinished;

    private DisposeRunnable(Runnable disposeFinished)
    {
      this.disposeFinished = disposeFinished;
    }

    @Override
    public void run()
    {
      doDispose();

      if (disposeFinished != null)
      {
        disposeFinished.run();
      }
    }
  }

  /**
   * Dispose AdblockWebView and internal adblockEngine if it was created
   * If external AdblockEngine was passed using `setAdblockEngine()` it should be disposed explicitly
   * Warning: runnable can be invoked from background thread
   * @param disposeFinished runnable to run when AdblockWebView is disposed
   */
  public void dispose(final Runnable disposeFinished)
  {
    Timber.d("Dispose invoked");

    if (getProvider() == null)
    {
      Timber.d("No internal AdblockEngineProvider created");
      return;
    }

    final Lock lock = getProvider().getReadEngineLock();
    lock.lock();

    try
    {
      AdblockEngine engine = getProvider().getEngine();
      if (engine != null)
      {
        engine.removeSettingsChangedListener(engineSettingsChangedCb);
      }
      getProvider().removeEngineCreatedListener(engineCreatedCb);
      getProvider().removeEngineDisposedListener(engineDisposedCb);
    }
    finally
    {
      lock.unlock();
    }

    stopLoading();

    DisposeRunnable disposeRunnable = new DisposeRunnable(disposeFinished);
    synchronized (elemHideThreadLockObject)
    {
      if (elemHideThread != null)
      {
        Timber.w("Busy with elemhide selectors, delayed disposing scheduled");
        elemHideThread.setFinishedRunnable(disposeRunnable);
      }
      else
      {
        disposeRunnable.run();
      }
    }
  }
}
