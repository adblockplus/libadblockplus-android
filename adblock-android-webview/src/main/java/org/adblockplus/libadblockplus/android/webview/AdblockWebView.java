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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

import static org.adblockplus.libadblockplus.android.Utils.convertMapToHeaderEntries;
import static org.adblockplus.libadblockplus.android.Utils.convertHeaderEntriesToMap;

/**
 * WebView with ad blocking
 */
public class AdblockWebView extends WebView
{
  private static final String TAG = Utils.getTag(AdblockWebView.class);

  protected static final String HEADER_REFERRER = "Referer";
  protected static final String HEADER_REQUESTED_WITH = "X-Requested-With";
  protected static final String HEADER_REQUESTED_WITH_XMLHTTPREQUEST = "XMLHttpRequest";
  protected static final String HEADER_REQUESTED_RANGE = "Range";
  protected static final String HEADER_LOCATION = "Location";
  protected static final String HEADER_SET_COOKIE = "Set-Cookie";
  protected static final String HEADER_COOKIE = "Cookie";
  protected static final String HEADER_USER_AGENT = "User-Agent";
  protected static final String HEADER_ACCEPT = "Accept";

  // use low-case strings as in WebResponse all header keys are lowered-case
  protected static final String HEADER_SITEKEY = "x-adblock-key";
  protected static final String HEADER_CONTENT_TYPE = "content-type";
  protected static final String HEADER_CONTENT_ENCODING = "content-encoding";

  private static final String ASSETS_CHARSET_NAME = "UTF-8";
  private static final String BRIDGE_TOKEN = "{{BRIDGE}}";
  private static final String DEBUG_TOKEN = "{{DEBUG}}";
  private static final String HIDE_TOKEN = "{{HIDE}}";
  private static final String HIDDEN_TOKEN = "{{HIDDEN_FLAG}}";
  private static final String BRIDGE = "jsBridge";
  private static final String EMPTY_ELEMHIDE_ARRAY_STRING = "[]";
  private static final String ELEMHIDEEMU_ARRAY_DEF_TOKEN = "[{{elemHidingEmulatedPatternsDef}}]";

  private RegexContentTypeDetector contentTypeDetector = new RegexContentTypeDetector();
  private boolean debugMode;
  private AtomicReference<AdblockEngineProvider> providerReference = new AtomicReference();
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
        Log.d(TAG, "Filter Engine status changed, enable status is " + adblockEnabled.get());
        AdblockWebView.this.post(new Runnable()
        {
          @Override
          public void run()
          {
            applyClients();
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
      Log.d(TAG, "Filter Engine created, enable status is " + adblockEnabled.get());
      AdblockWebView.this.post(new Runnable()
      {
        @Override
        public void run()
        {
          applyClients();
        }
      });
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

  public SiteKeysConfiguration getSiteKeysConfiguration()
  {
    return siteKeysConfiguration;
  }

  public void setSiteKeysConfiguration(final SiteKeysConfiguration siteKeysConfiguration)
  {
    this.siteKeysConfiguration = siteKeysConfiguration;
  }

  private void applyClients()
  {
    if (adblockEnabled == null)
    {
      return;
    }
    super.setWebChromeClient(adblockEnabled.get() || extWebChromeClient == null ?
            intWebChromeClient : extWebChromeClient);
    super.setWebViewClient(adblockEnabled.get() || extWebViewClient == null ?
            intWebViewClient : extWebViewClient);
  }

  @Override
  public void setWebChromeClient(final WebChromeClient client)
  {
    extWebChromeClient = client;
    applyClients();
  }

  @Override
  public void setWebViewClient(final WebViewClient client)
  {
    extWebViewClient = client;
    applyClients();
  }

  private void initAbp()
  {
    addJavascriptInterface(this, BRIDGE);
    initRandom();
    buildInjectJs();
    getSettings().setJavaScriptEnabled(true);
    intWebViewClient = new AdblockWebViewClient();
  }

  private AdblockEngineProvider getProvider()
  {
    return providerReference.get();
  }

  public boolean isDebugMode()
  {
    return debugMode;
  }

  /**
   * Set to true to see debug log output int AdblockWebView and JS console
   * Should be set before first URL loading if using internal AdblockEngineProvider
   * @param debugMode is debug mode
   */
  public void setDebugMode(boolean debugMode)
  {
    this.debugMode = debugMode;
  }

  private void d(String message)
  {
    if (debugMode)
    {
      Log.d(TAG, message);
    }
  }

  private void w(String message)
  {
    if (debugMode)
    {
      Log.w(TAG, message);
    }
  }

  private void e(String message, Throwable t)
  {
    Log.e(TAG, message, t);
  }

  private void e(String message)
  {
    Log.e(TAG, message);
  }

  private String readScriptFile(String filename) throws IOException
  {
    return Utils
      .readAssetAsString(getContext(), filename, ASSETS_CHARSET_NAME)
      .replace(BRIDGE_TOKEN, BRIDGE)
      .replace(DEBUG_TOKEN, (debugMode ? "" : "//"))
      .replace(HIDDEN_TOKEN, elementsHiddenFlag);
  }

  private String readEmuScriptFile(String filename) throws IOException
  {
    return Utils
      .readAssetAsString(getContext(), filename, ASSETS_CHARSET_NAME)
      .replace(ELEMHIDEEMU_ARRAY_DEF_TOKEN, "JSON.parse(jsBridge.getElemhideEmulationSelectors())");
  }

  private void runScript(String script)
  {
    d("runScript started");
    evaluateJavascript(script, null);
    d("runScript finished");
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
        final Lock lock = provider.getReadEngineLock();
        lock.lock();

        try
        {
          // Note that if retain() needs to create a FilterEngine it will wait (in bg thread)
          // until we finish this synchronized block and release the engine lock.
          getProvider().retain(true); // asynchronously
          if (getProvider().getEngine() != null)
          {
            adblockEnabled = new AtomicBoolean(getProvider().getEngine().isEnabled());
            Log.d(TAG, "Filter Engine already created, enable status is " + adblockEnabled);
            applyClients();
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
          lock.unlock();
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
    public void onReceivedTitle(WebView view, String title)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedTitle(view, title);
      }
    }

    @Override
    public void onReceivedIcon(WebView view, Bitmap icon)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedIcon(view, icon);
      }
    }

    @Override
    public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedTouchIconUrl(view, url, precomposed);
      }
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onShowCustomView(view, callback);
      }
    }

    @Override
    public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onShowCustomView(view, requestedOrientation, callback);
      }
    }

    @Override
    public void onHideCustomView()
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onHideCustomView();
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
    }

    @Override
    public void onCloseWindow(WebView window)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onCloseWindow(window);
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
      d("JS: level=" + consoleMessage.messageLevel()
        + ", message=\"" + consoleMessage.message() + "\""
        + ", sourceId=\"" + consoleMessage.sourceId() + "\""
        + ", line=" + consoleMessage.lineNumber());

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
      d("Loading progress=" + newProgress + "%");
      tryInjectJs();

      if (extWebChromeClient != null)
      {
        extWebChromeClient.onProgressChanged(view, newProgress);
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
      d("Injecting script");
      runScript(injectJs);
    }
  }

  private void clearReferrers()
  {
    d("Clearing referrers");
    url2Referrer.clear();
  }

  /**
   * WebViewClient for API 21 and newer
   * (has Referrer since it overrides `shouldInterceptRequest(..., request)` with referrer)
   */
  private class AdblockWebViewClient extends WebViewClient
  {
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
      if (loading)
      {
        stopAbpLoading();
      }

      startAbpLoading(url);

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
      e("Load error:" +
        " code=" + errorCode +
        " with description=" + description +
        " for url=" + failingUrl);
      loadError = errorCode;

      stopAbpLoading();

      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedError(view, errorCode, description, failingUrl);
      }
      else
      {
        super.onReceivedError(view, errorCode, description, failingUrl);
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

    protected WebResourceResponse shouldInterceptRequest(
      final WebView webview, final String url,
      final boolean isMainFrame, final boolean isXmlHttpRequest,
      final String requestMethod, final String referrer,
      final Map<String, String> requestHeadersMap)
    {
      final Lock lock = getProvider().getReadEngineLock();
      lock.lock();

      try
      {
        // if dispose() was invoke, but the page is still loading then just let it go
        if (getProvider().getCounter() == 0)
        {
          e("FilterEngine already disposed, allow loading");

          // allow loading by returning null
          return null;
        }
        else
        {
          getProvider().waitForReady();
        }

        if (adblockEnabled == null)
        {
          return null;
        }
        else
        {
          // check the real enable status and update adblockEnabled flag which is used
          // later on to check if we should execute element hiding JS
          adblockEnabled.set(getProvider().getEngine().isEnabled());
          if (!adblockEnabled.get())
          {
            return null;
          }
        }

        d("Loading url " + url);

        if (referrer != null)
        {
          d("Header referrer for " + url + " is " + referrer);
          if (!url.equals(referrer))
          {
            url2Referrer.put(url, referrer);
          }
          else
          {
            w("Header referrer value is the same as url, skipping url2Referrer.put()");
          }
        }
        else
        {
          w("No referrer header for " + url);
        }

        // reconstruct frames hierarchy
        List<String> referrerChain = new ArrayList<>();
        String parent = url;
        while ((parent = url2Referrer.get(parent)) != null)
        {
          if (referrerChain.contains(parent))
          {
            w("Detected referrer loop, finished creating referrers list");
            break;
          }
          referrerChain.add(0, parent);
        }

        if (isMainFrame)
        {
          // never blocking main frame requests, just subrequests
          w(url + " is main frame, allow loading");
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
            w(url + " domain is whitelisted, allow loading");
          }
          else if (getProvider().getEngine().isDocumentWhitelisted(url, referrerChain, siteKey))
          {
            w(url + " document is whitelisted, allow loading");
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
                w("contentTypeDetector didn't recognize content type");
                final String acceptType = requestHeadersMap.get(HEADER_ACCEPT);
                if (acceptType != null && acceptType.contains("text/html"))
                {
                  w("using subdocument content type");
                  contentType = FilterEngine.ContentType.SUBDOCUMENT;
                }
                else
                {
                  w("using other content type");
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
                w("Found genericblock filter for url " + url + " which parent is " + parentUrl);
              }
            }
            // check if we should block
            if (getProvider().getEngine().matches(url, FilterEngine.ContentType.maskOf(contentType),
                    referrerChain, siteKey, specificOnly))
            {
              w("Blocked loading " + url);

              if (isVisibleResource(contentType))
              {
                elemhideBlockedResource(url);
              }

              // if we should block, return empty response which results in 'errorLoading' callback
              return new WebResourceResponse("text/plain", "UTF-8", null);
            }
            d("Allowed loading " + url);
          }
        }
      }
      finally
      {
        lock.unlock();
      }

      if (requestHeadersMap.containsKey(HEADER_REQUESTED_RANGE))
      {
        Log.d(TAG, "Skipping site key check for the request with a Range header");
        return null;
      }

      return fetchUrlAndCheckSiteKey(isMainFrame ? webview : null, url, requestHeadersMap, requestMethod);
    }

    private WebResourceResponse fetchUrlAndCheckSiteKey(final WebView webview, String url,
                                                        final Map<String, String> requestHeadersMap,
                                                        final String requestMethod)
    {
      if (siteKeysConfiguration == null ||
          !requestMethod.equalsIgnoreCase(HttpClient.REQUEST_METHOD_GET))
      {
        // for now we handle site key only for GET requests
        return null;
      }

      Log.d(TAG, "fetchUrlAndCheckSiteKey() called from Thread " + Thread.currentThread().getId());
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
          headersList.add(new HeaderEntry(HEADER_COOKIE, cookieValue));
        }
        final HttpRequest request = new HttpRequest(url, requestMethod, headersList, autoFollowRedirect);
        siteKeysConfiguration.getHttpClient().request(request, callback);
      }
      catch (final AdblockPlusException e)
      {
        Log.e(TAG, "WebRequest failed", e);
        // allow WebView to continue, repeating the request and handling the response
        return null;
      }

      try
      {
        latch.await();
      }
      catch (final InterruptedException e)
      {
        // error waiting for the response, continue by returning null
        return null;
      }

      final ServerResponse response = responseHolder.response;
      final ServerResponse.NsStatus status = response.getStatus();
      int statusCode = response.getResponseStatus();

      if (HttpClient.isRedirectCode(statusCode))
      {
        if (webview != null)
        {
          reloadWebViewUrl(webview, url, responseHolder);
        }
        return null;
      }

      if (response.getFinalUrl() != null)
      {
        d("Updating url to " + response.getFinalUrl() + ", was (" + url + ")");
        url = response.getFinalUrl();
      }

      for (HeaderEntry header : responseHolder.response.getResponseHeaders())
      {
        if (header.getKey().equals(HEADER_SITEKEY))
        {
          // verify signature and save public key to be used as sitekey for next requests
          try
          {
            if (siteKeysConfiguration.getSiteKeyVerifier().verify(
                Utils.getUrlWithoutAnchor(url), requestHeadersMap.get(HEADER_USER_AGENT), header.getValue()))
            {
              d("Url " + url + " public key verified successfully");
            }
            else
            {
              e("Url " + url + " public key is not verified");
            }
          }
          catch (final SiteKeyException e)
          {
            e("Failed to verify sitekey header", e);
          }
          break;
        }
      }

      final Map<String, String> responseHeadersMap = convertHeaderEntriesToMap(response.getResponseHeaders());
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
      if (responseEncoding == null)
      {
        responseEncoding = responseHeadersMap.get(HEADER_CONTENT_ENCODING);
      }

      if (response.getResponse() != null)
      {
        final byte[] buffer = Utils.byteBufferToByteArray(response.getResponse());
        final InputStream byteBufferInputStream = new ByteArrayInputStream(buffer);
        return new WebResourceResponse(
                responseMimeType, responseEncoding,
                statusCode, getReasonPhrase(status),
                responseHeadersMap, byteBufferInputStream);
      }
      return null;
    }

    private void reloadWebViewUrl(final WebView webview,
                                  final String url,
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
            Log.e(TAG, "Failed to build absolute redirect URL", e);
            redirectedUrl = null;
          }
        }
        if (header.getKey().equalsIgnoreCase(HEADER_SET_COOKIE) &&
            header.getValue() != null && !header.getValue().isEmpty())
        {
          CookieManager.getInstance().setCookie(url, header.getValue());
        }
      }

      if (redirectedUrl != null)
      {
        Log.d(TAG, "redirecting webview from " + url + " to " + redirectedUrl);
        final String finalUrl = redirectedUrl;
        // we need to reload webview url to make it aware of new new url after redirection
        webview.post(new Runnable()
        {
          @Override
          public void run()
          {
            webview.stopLoading();
            webview.loadUrl(finalUrl);
          }
        });
      }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)
    {
      // here we just trying to fill url -> referrer map
      // blocking/allowing loading will happen in `shouldInterceptRequest(WebView,String)`
      String url = request.getUrl().toString();

      boolean isXmlHttpRequest =
        request.getRequestHeaders().containsKey(HEADER_REQUESTED_WITH) &&
          HEADER_REQUESTED_WITH_XMLHTTPREQUEST.equals(
            request.getRequestHeaders().get(HEADER_REQUESTED_WITH));

      return shouldInterceptRequest(view, url, request.isForMainFrame(),
              isXmlHttpRequest, request.getMethod(),
              request.getRequestHeaders().get(HEADER_REFERRER),
              request.getRequestHeaders());
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
    d("Trying to elemhide visible blocked resource with url: " + url);
    final String filenameWithQuery;
    try
    {
      filenameWithQuery = Utils.extractPathWithQuery(url);
    }
    catch (final MalformedURLException e)
    {
      e("Failed to parse URI for blocked resource:" + url + ". Skipping element hiding");
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
    private String selectorsString;
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
        if (getProvider().getCounter() == 0)
        {
          w("FilterEngine already disposed");
          selectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
          emuSelectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
        }
        else
        {
          getProvider().waitForReady();
          List<String> referrerChain = new ArrayList<String>(1);
          referrerChain.add(url);
          String parentUrl = url;
          while ((parentUrl = url2Referrer.get(parentUrl)) != null)
          {
            if (referrerChain.contains(parentUrl))
            {
              w("Detected referrer loop, finished creating referrers list");
              break;
            }
            referrerChain.add(0, parentUrl);
          }

          final FilterEngine filterEngine = getProvider().getEngine().getFilterEngine();

          List<Subscription> subscriptions = filterEngine.getListedSubscriptions();

          try
          {
            d("Listed subscriptions: " + subscriptions.size());
            if (debugMode)
            {
              for (Subscription eachSubscription : subscriptions)
              {
                d("Subscribed to "
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
            e("Failed to extract domain from " + url);
            selectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
            emuSelectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
          }
          else
          {
            // elemhide
            d("Requesting elemhide selectors from AdblockEngine for " + url + " in " + this);

            final String siteKey = (siteKeysConfiguration != null
              ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
                .getAny(referrerChain, ""))
              : null);

            final boolean specificOnly = filterEngine.matches(url,
              FilterEngine.ContentType.maskOf(FilterEngine.ContentType.GENERICHIDE),
              Collections.<String>emptyList(), null) != null;

            if (specificOnly)
            {
              d("elemhide - specificOnly selectors");
            }

            List<String> selectors = getProvider()
              .getEngine()
              .getElementHidingSelectors(url, domain, referrerChain, siteKey, specificOnly);

            d("Finished requesting elemhide selectors, got " + selectors.size() + " in " + this);
            selectorsString = Utils.stringListToJsonArray(selectors);

            // elemhideemu
            d("Requesting elemhideemu selectors from AdblockEngine for " + url + " in " + this);
            List<FilterEngine.EmulationSelector> emuSelectors = getProvider()
              .getEngine()
              .getElementHidingEmulationSelectors(url, domain, referrerChain, siteKey);

            d("Finished requesting elemhideemu selectors, got " + emuSelectors.size() + " in " + this);
            emuSelectorsString = Utils.emulationSelectorListToJsonArray(emuSelectors);
          }
        }
      }
      finally
      {
        lock.unlock();
        if (isCancelled.get())
        {
          w("This thread is cancelled, exiting silently " + this);
        }
        else
        {
          finish(selectorsString, emuSelectorsString);
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
      d("Setting elemhide string " + selectorsString.length() + " bytes");
      elemHideSelectorsString = selectorsString;

      d("Setting elemhideemu string " + emuSelectorsString.length() + " bytes");
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
      w("Cancelling elemhide thread " + this);
      if (isFinished.get())
      {
        w("This thread is finished, exiting silently " + this);
      }
      else
      {
        isCancelled.set(true);
        finish(EMPTY_ELEMHIDE_ARRAY_STRING, EMPTY_ELEMHIDE_ARRAY_STRING);
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
        w("elemHideThread set to null");
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
        getContext(), AdblockEngine.BASE_PATH_DIRECTORY, debugMode));
    }
  }

  private void startAbpLoading(String newUrl)
  {
    d("Start loading " + newUrl);

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
        sb.append(readEmuScriptFile("elemhideemu.jst"));
        injectJs = sb.toString();
      }

      if (elemhideBlockedJs == null)
      {
        elemhideBlockedJs = readScriptFile("elemhideblocked.js");
      }
    }
    catch (final IOException e)
    {
      e("Failed to read script", e);
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
    d("Stop abp loading");

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
  public String getElemhideSelectors()
  {
    if (elemHideLatch == null)
    {
      return EMPTY_ELEMHIDE_ARRAY_STRING;
    }
    else
    {
      try
      {
        // elemhide selectors list getting is started in startAbpLoad() in background thread
        d("Waiting for elemhide selectors to be ready");
        elemHideLatch.await();
        d("Elemhide selectors ready, " + elemHideSelectorsString.length() + " bytes");

        return elemHideSelectorsString;
      }
      catch (final InterruptedException e)
      {
        w("Interrupted, returning empty selectors list");
        return EMPTY_ELEMHIDE_ARRAY_STRING;
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
        d("Waiting for elemhideemu selectors to be ready");
        elemHideLatch.await();
        d("Elemhideemu selectors ready, " + elemHideEmuSelectorsString.length() + " bytes");

        return elemHideEmuSelectorsString;
      }
      catch (final InterruptedException e)
      {
        w("Interrupted, returning empty elemhideemu selectors list");
        return EMPTY_ELEMHIDE_ARRAY_STRING;
      }
    }
  }

  private void doDispose()
  {
    w("Disposing AdblockEngine");
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
    d("Dispose invoked");

    if (getProvider() == null)
    {
      d("No internal AdblockEngineProvider created");
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
        w("Busy with elemhide selectors, delayed disposing scheduled");
        elemHideThread.setFinishedRunnable(disposeRunnable);
      }
      else
      {
        disposeRunnable.run();
      }
    }
  }
}
