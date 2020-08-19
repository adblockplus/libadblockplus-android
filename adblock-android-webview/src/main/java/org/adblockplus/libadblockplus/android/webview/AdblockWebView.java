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
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.SingleInstanceEngineProvider;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.webview.content_type.ContentTypeDetector;
import org.adblockplus.libadblockplus.android.webview.content_type.HeadersContentTypeDetector;
import org.adblockplus.libadblockplus.android.webview.content_type.OrderedContentTypeDetector;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl;
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;
import org.jetbrains.annotations.TestOnly;

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

import timber.log.Timber;

import static org.adblockplus.libadblockplus.android.webview.AdblockWebView.OptionalBoolean.from;

/**
 * WebView with ad blocking
 */
public class AdblockWebView extends WebView
{
  private static final String ASSETS_CHARSET_NAME = "UTF-8";
  private static final String BRIDGE_TOKEN = "{{BRIDGE}}";
  private static final String DEBUG_TOKEN = "{{DEBUG}}";
  private static final String HIDE_TOKEN = "{{HIDE}}";
  private static final String HIDDEN_TOKEN = "{{HIDDEN_FLAG}}";
  private static final String SITEKEY_EXTRACTED_TOKEN = "{{SITEKEY_EXTRACTED_FLAG}}";
  private static final String BRIDGE = "jsBridge";
  private static final String EMPTY_ELEMHIDE_STRING = "";
  private static final String EMPTY_ELEMHIDE_ARRAY_STRING = "[]";

  private OrderedContentTypeDetector contentTypeDetector;
  private final AtomicReference<AdblockEngineProvider> providerReference = new AtomicReference<>();
  private Integer loadError;
  private ProxyWebChromeClient intWebChromeClient;
  private ProxyWebViewClient intWebViewClient;
  private final Map<String, String> url2Referrer
          = Collections.synchronizedMap(new HashMap<String, String>());
  private final AtomicReference<String> navigationUrl = new AtomicReference<>();
  private String injectJs;
  private String elemhideBlockedJs;
  private CountDownLatch elemHideLatch;
  private final AtomicReference<OptionalBoolean> adblockEnabled =
      new AtomicReference<>(OptionalBoolean.UNDEFINED);
  private String elemHideSelectorsString;
  private String elemHideEmuSelectorsString;
  private final Object elemHideThreadLockObject = new Object();
  private ElemHideThread elemHideThread;
  private boolean loading;
  private String elementsHiddenFlag;
  private String sitekeyExtractedFlag;
  private final AtomicBoolean redirectInProgress = new AtomicBoolean(false);
  private SiteKeyExtractor siteKeyExtractor;
  private final AtomicBoolean acceptCookie = new AtomicBoolean(true);
  private final AtomicBoolean acceptThirdPartyCookies = new AtomicBoolean(false);

  /**
   * Optional boolean value.
   * Puts 2 dimensions (having value/no value + true/false) into 1 dimension
   * to achieve atomic comparisons and null-safety.
   */
  public enum OptionalBoolean
  {
    /**
     * No value (equal to "null")
     */
    UNDEFINED,

    /**
     * Having a value and it's True
     */
    TRUE,

    /**
     * Having a value and it's False
     */
    FALSE;

    /**
     * Convenience method to get enum value from boolean value
     * @param value boolean value
     * @return enum value
     */
    public static OptionalBoolean from(final boolean value)
    {
      return (value ? TRUE : FALSE);
    }
  }

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
      private final WhitelistReason reason;

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

  public void setRedirectInProgress(final boolean redirectInProgress)
  {
    this.redirectInProgress.set(redirectInProgress);
  }

  private final AtomicReference<EventsListener> eventsListenerAtomicReference
          = new AtomicReference<>();
  private final AtomicReference<SiteKeysConfiguration> siteKeysConfiguration =
      new AtomicReference<>();
  private final AdblockEngine.SettingsChangedListener engineSettingsChangedCb =
      new AdblockEngine.SettingsChangedListener()
  {
    @Override
    public void onEnableStateChanged(final boolean enabled)
    {
      final OptionalBoolean newValue = from(enabled);
      final OptionalBoolean oldValue = adblockEnabled.getAndSet(newValue);
      if (oldValue != OptionalBoolean.UNDEFINED && oldValue != newValue)
      {
        Timber.d("Filter Engine status changed, enable status is %s", newValue);
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
  private final AdblockEngineProvider.EngineCreatedListener engineCreatedCb = new AdblockEngineProvider.EngineCreatedListener()
  {
    @Override
    public void onAdblockEngineCreated(final AdblockEngine engine)
    {
      adblockEnabled.set(from(engine.isEnabled()));
      Timber.d("Filter Engine created, enable status is %s", adblockEnabled.get());
      engine.addSettingsChangedListener(engineSettingsChangedCb);
    }
  };
  private final AdblockEngineProvider.EngineDisposedListener engineDisposedCb
          = new AdblockEngineProvider.EngineDisposedListener()
  {
    @Override
    public void onAdblockEngineDisposed()
    {
      adblockEnabled.set(OptionalBoolean.UNDEFINED);
    }
  };

  public AdblockWebView(final Context context)
  {
    super(context);
    initAbp();
  }

  public AdblockWebView(final Context context, final AttributeSet attrs)
  {
    super(context, attrs);
    initAbp();
  }

  public AdblockWebView(final Context context, final AttributeSet attrs, final int defStyle)
  {
    super(context, attrs, defStyle);
    initAbp();
  }

  private EventsListener getEventsListener()
  {
    return eventsListenerAtomicReference.get();
  }

  public void setSiteKeysConfiguration(final SiteKeysConfiguration siteKeysConfiguration)
  {
    this.siteKeysConfiguration.set(siteKeysConfiguration);
    siteKeyExtractor.setSiteKeysConfiguration(siteKeysConfiguration);
  }

  @TestOnly
  public SiteKeysConfiguration getSiteKeysConfiguration()
  {
    return siteKeysConfiguration.get();
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
    intWebChromeClient.setExtWebChromeClient(client);
  }

  @Override
  public void setWebViewClient(final WebViewClient client)
  {
    intWebViewClient.setExtWebViewClient(client);
  }

  @TestOnly
  public SiteKeyExtractor getSiteKeyExtractor()
  {
    return this.siteKeyExtractor;
  }

  @TestOnly
  public void setSiteKeyExtractor(final SiteKeyExtractor extractor)
  {
    this.siteKeyExtractor = extractor;
  }

  @SuppressLint("SetJavaScriptEnabled")
  private void initAbp()
  {
    addJavascriptInterface(this, BRIDGE);
    initRandom();
    buildInjectJs();
    getSettings().setJavaScriptEnabled(true);

    siteKeyExtractor = new CombinedSiteKeyExtractor(this);
    intWebChromeClient = new AdblockWebWebChromeClient(null);
    intWebViewClient = new AdblockWebViewClient(null);

    super.setWebChromeClient(intWebChromeClient);
    super.setWebViewClient(intWebViewClient);
  }

  private AdblockEngineProvider getProvider()
  {
    return providerReference.get();
  }

  private String readScriptFile(final String filename) throws IOException
  {
    return Utils
      .readAssetAsString(getContext(), filename, ASSETS_CHARSET_NAME)
      .replace(BRIDGE_TOKEN, BRIDGE)
      .replace(DEBUG_TOKEN, (BuildConfig.DEBUG ? "" : "//"))
      .replace(HIDDEN_TOKEN, elementsHiddenFlag)
      .replace(SITEKEY_EXTRACTED_TOKEN, sitekeyExtractedFlag);
  }

  private void runScript(final String script)
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
            adblockEnabled.set(from(getProvider().getEngine().isEnabled()));
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

  private class AdblockWebWebChromeClient extends ProxyWebChromeClient
  {
    AdblockWebWebChromeClient(final WebChromeClient extWebChromeClient)
    {
      super(extWebChromeClient);
    }

    @Override
    public boolean onConsoleMessage(final ConsoleMessage consoleMessage)
    {
      Timber.d("JS: level=%s, message=\"%s\", sourceId=\"%s\", line=%d",
              consoleMessage.messageLevel(),
              consoleMessage.message(),
              consoleMessage.sourceId(),
              consoleMessage.lineNumber());

      return super.onConsoleMessage(consoleMessage);
    }

    @Override
    public void onProgressChanged(final WebView view, final int newProgress)
    {
      if (redirectInProgress.get())
      {
        Timber.d("Skipping onProgressChanged to %d%% for url: %s", newProgress, view.getUrl());
        return;
      }
      Timber.d("onProgressChanged to %d%% for url: %s", newProgress, view.getUrl());
      tryInjectJs();

      super.onProgressChanged(view, newProgress);
    }
  }

  private void tryInjectJs()
  {
    if (adblockEnabled.get() != OptionalBoolean.TRUE)
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

    // Allow loading
    ALLOW_LOAD_NO_SITEKEY_CHECK,

    // Block loading
    BLOCK_LOAD,
  }

  static class WebResponseResult
  {
    // decisions
    static String RESPONSE_CHARSET_NAME = "UTF-8";
    static String RESPONSE_MIME_TYPE = "text/plain";

    static WebResourceResponse ALLOW_LOAD = null;
    static WebResourceResponse BLOCK_LOAD =
            new WebResourceResponse(RESPONSE_MIME_TYPE, RESPONSE_CHARSET_NAME, null);
  }

  /**
   * WebViewClient for API 21 and newer
   * (has Referrer since it overrides `shouldInterceptRequest(..., request)` with referrer)
   */
  private class AdblockWebViewClient extends ProxyWebViewClient
  {
    AdblockWebViewClient(final WebViewClient extWebViewClient)
    {
      super(extWebViewClient);
    }

    @Override
    public void onPageStarted(final WebView view, final String url, final Bitmap favicon)
    {
      Timber.d("onPageStarted called for url %s", url);
      if (loading)
      {
        stopAbpLoading();
      }

      startAbpLoading(url);

      notifyNavigation();

      super.onPageStarted(view, url, favicon);
    }

    @Override
    public void onPageFinished(final WebView view, final String url)
    {
      if (redirectInProgress.get())
      {
        Timber.d("Skipping onPageFinished for url: %s", url);
        redirectInProgress.set(false);
        return;
      }
      Timber.d("onPageFinished called for url %s", url);
      loading = false;

      super.onPageFinished(view, url);
    }

    @Override
    public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl)
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

      super.onReceivedError(view, errorCode, description, failingUrl);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onReceivedError(final WebView view, final WebResourceRequest request, final WebResourceError error)
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

      super.onReceivedError(view, request, error);
    }

    private AbpShouldBlockResult shouldAbpBlockRequest(final WebResourceRequest request)
    {
      // here we just trying to fill url -> referrer map
      final String url = request.getUrl().toString();

      final boolean isMainFrame = request.isForMainFrame();
      boolean isWhitelisted = false;
      boolean canContainSitekey = false;
      boolean isAcceptableAdsEnabled = true;

      final String referrer = request.getRequestHeaders().get(HttpClient.HEADER_REFERRER);

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

        final AdblockEngine engine = getProvider().getEngine();

        // Apart from checking counter (getProvider().getCounter()) we also need to make sure
        // that getProvider().getEngine() is already set.
        // We check that under getProvider().getReadEngineLock(); so we are sure it will not be
        // changed after this check.
        if (isDisposed || engine == null)
        {
          Timber.e("FilterEngine already disposed");
          return AbpShouldBlockResult.NOT_ENABLED;
        }

        if (adblockEnabled.get() == OptionalBoolean.UNDEFINED)
        {
          Timber.e("No adblockEnabled value");
          return AbpShouldBlockResult.NOT_ENABLED;
        }
        else
        {
          // check the real enable status and update adblockEnabled flag which is used
          // later on to check if we should execute element hiding JS
          final OptionalBoolean newValue = from(engine.isEnabled());
          adblockEnabled.set(newValue);
          if (newValue == OptionalBoolean.FALSE)
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
        final List<String> referrerChain = new ArrayList<>();
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

        isAcceptableAdsEnabled = engine.isAcceptableAdsEnabled();
        if (isMainFrame)
        {
          // never blocking main frame requests, just subrequests
          Timber.w("%s is main frame, allow loading", url);

          siteKeyExtractor.setEnabled(isAcceptableAdsEnabled);
        }
        else
        {
          final SiteKeysConfiguration siteKeysConfiguration = getSiteKeysConfiguration();
          final String siteKey = (siteKeysConfiguration != null
            ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
              .getAny(referrerChain, ""))
            : null);

          // whitelisted
          if (engine.isDomainWhitelisted(url, referrerChain))
          {
            isWhitelisted = true;
            Timber.w("%s domain is whitelisted, allow loading", url);
            notifyResourceWhitelisted(new EventsListener.WhitelistedResourceInfo(
                url, referrerChain, EventsListener.WhitelistReason.DOMAIN));
          }
          else if (engine.isDocumentWhitelisted(url, referrerChain, siteKey))
          {
            isWhitelisted = true;
            Timber.w("%s document is whitelisted, allow loading", url);
            notifyResourceWhitelisted(new EventsListener.WhitelistedResourceInfo(
                url, referrerChain, EventsListener.WhitelistReason.DOCUMENT));
          }
          else
          {
            // determine the content
            FilterEngine.ContentType contentType =
                    ensureContentTypeDetectorCreatedAndGet().detect(request);
            if (contentType == null)
            {
              Timber.w("contentTypeDetector didn't recognize content type");
              contentType = FilterEngine.ContentType.OTHER;
            }

            if (contentType == FilterEngine.ContentType.SUBDOCUMENT ||
                contentType == FilterEngine.ContentType.OTHER)
            {
              canContainSitekey = true;
            }

            boolean specificOnly = false;
            if (!referrerChain.isEmpty())
            {
              final String parentUrl = referrerChain.get(0);
              final List<String> referrerChainForGenericblock = referrerChain.subList(1, referrerChain.size());
              specificOnly = engine.isGenericblockWhitelisted(parentUrl,
                      referrerChainForGenericblock, siteKey);
              if (specificOnly)
              {
                Timber.w("Found genericblock filter for url %s which parent is %s", url, parentUrl);
              }
            }

            // check if we should block
            final AdblockEngine.MatchesResult result = engine.matches(
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
              isWhitelisted = true;
              Timber.w("%s is whitelisted in matches()", url);
              notifyResourceWhitelisted(new EventsListener.WhitelistedResourceInfo(
                  url, referrerChain, EventsListener.WhitelistReason.FILTER));
            }
            Timber.d("Allowed loading %s", url);
          }
        } // !MainFrame
      }
      finally
      {
        lock.unlock();
      }

      // we rely on calling `fetchUrlAndCheckSiteKey` later in `shouldInterceptRequest`, now we
      // just reply that it's fine to load the resource
      final SiteKeysConfiguration siteKeysConfiguration = getSiteKeysConfiguration();
      if ((
            isAcceptableAdsEnabled
            ||
            (siteKeysConfiguration != null && siteKeysConfiguration.getForceChecks())
          )
          &&
          (
            isMainFrame
            ||
            (canContainSitekey && !isWhitelisted)
          ))
      {
        // if url is a main frame (whitelisted by default) or can contain by design a site key header
        // (it content type is SUBDOCUMENT or OTHER) and it is not yet whitelisted then we need to
        // make custom HTTP get request to try to obtain a site key header.
        return AbpShouldBlockResult.ALLOW_LOAD;
      }

      return AbpShouldBlockResult.ALLOW_LOAD_NO_SITEKEY_CHECK;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(final WebView view, final WebResourceRequest request)
    {
      if (request.isForMainFrame())
      {
        Timber.d("Updating navigationUrl to `%s`", request.getUrl().toString());
        navigationUrl.set(request.getUrl().toString());
      }
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

      final WebViewClient extWebViewClient = getExtWebViewClient();
      if (extWebViewClient != null)
      {
        // allow external WebViewClient to perform and intercept requests
        // its fine to block shouldAbpBlockRequest and wait
        final WebResourceResponse externalResponse
                = extWebViewClient.shouldInterceptRequest(view, request);

        // if we are having an external WebResourceResponse provided by external WebViewClient,
        // we will do the sitekey verification and just return the Response
        if (externalResponse != null)
        {
          if (!AbpShouldBlockResult.ALLOW_LOAD_NO_SITEKEY_CHECK.equals(abpBlockResult))
          {
            Timber.d("Verifying site keys with external shouldInterceptRequest response");
            getSiteKeysConfiguration().getSiteKeyVerifier().verifyInHeaders(url,
                  requestHeaders,
                  externalResponse.getResponseHeaders());
            Timber.d("Finished verifying, returning external response and stop");
          }
          else
          {
            Timber.d("Skipped verifying of the site keys with " +
                    "external shouldInterceptRequest response");
          }
          return externalResponse;
        }
      }

      // we don't need to make a HTTP GET request to check a site key header
      if (AbpShouldBlockResult.ALLOW_LOAD_NO_SITEKEY_CHECK.equals(abpBlockResult))
      {
        return WebResponseResult.ALLOW_LOAD;
      }

      if (requestHeaders.containsKey(HttpClient.HEADER_REQUESTED_RANGE))
      {
        Timber.d("Skipping site key check for the request with a Range header");
        return WebResponseResult.ALLOW_LOAD;
      }

      return siteKeyExtractor.obtainAndCheckSiteKey(AdblockWebView.this, request);
    }
  }

  public boolean canAcceptCookie(final String requestUrl, final String cookieString)
  {
    final String documentUrl = navigationUrl.get();
    if (documentUrl == null || requestUrl == null || cookieString == null)
    {
      return false;
    }
    if (!acceptCookie.get())
    {
      return false;
    }
    if (!acceptThirdPartyCookies.get())
    {
      return Utils.isFirstPartyCookie(documentUrl, requestUrl, cookieString);
    }
    return true;
  }

  // not a huge saving, but still nice to lazy init
  // since `contentTypeDetector` might not be used ever
  private ContentTypeDetector ensureContentTypeDetectorCreatedAndGet()
  {
    if (contentTypeDetector == null)
    {
      final HeadersContentTypeDetector headersContentTypeDetector =
          new HeadersContentTypeDetector();
      final UrlFileExtensionTypeDetector urlFileExtensionTypeDetector =
          new UrlFileExtensionTypeDetector();
      contentTypeDetector = new OrderedContentTypeDetector(headersContentTypeDetector,
          urlFileExtensionTypeDetector);
    }
    return contentTypeDetector;
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
    String filenameWithQuery;
    try
    {
      filenameWithQuery = Utils.extractPathWithQuery(url);
      if (filenameWithQuery.startsWith("/"))
      {
        filenameWithQuery = filenameWithQuery.substring(1);
      }
    }
    catch (final MalformedURLException e)
    {
      Timber.e("Failed to parse URI for blocked resource:" + url + ". Skipping element hiding");
      return;
    }
    Timber.d("Trying to elemhide visible blocked resource with url `%s` and path `%s`",
        url, filenameWithQuery);

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

  private void initRandom()
  {
    final Random random = new Random();
    elementsHiddenFlag = "abp" + Math.abs(random.nextLong());
    sitekeyExtractedFlag = "abp" + Math.abs(random.nextLong());
  }

  private class ElemHideThread extends Thread
  {
    private String stylesheetString;
    private String emuSelectorsString;
    private final CountDownLatch finishedLatch;
    private final AtomicBoolean isFinished;
    private final AtomicBoolean isCancelled;

    public ElemHideThread(final CountDownLatch finishedLatch)
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

        // Apart from checking counter (getProvider().getCounter()) we also need to make sure
        // that getProvider().getEngine() is already set.
        // We check that under getProvider().getReadEngineLock(); so we are sure it will not be
        // changed after this check.
        if (isDisposed || getProvider().getEngine() == null)
        {
          Timber.w("FilterEngine already disposed");
          stylesheetString = EMPTY_ELEMHIDE_STRING;
          emuSelectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
        }
        else
        {
          final List<String> referrerChain = new ArrayList<>(1);
          String parentUrl = navigationUrl.get();
          referrerChain.add(parentUrl);
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

          final List<Subscription> subscriptions = filterEngine.getListedSubscriptions();

          try
          {
            Timber.d("Listed subscriptions: %d", subscriptions.size());
            if (BuildConfig.DEBUG)
            {
              for (final Subscription eachSubscription : subscriptions)
              {
                Timber.d("Subscribed to "
                  + (eachSubscription.isDisabled() ? "disabled" : "enabled")
                  + " " + eachSubscription);
              }
            }
          }
          finally
          {
            for (final Subscription eachSubscription : subscriptions)
            {
              eachSubscription.dispose();
            }
          }

          final String navigationUrlLocalRef = navigationUrl.get();
          final String domain = filterEngine.getHostFromURL(navigationUrlLocalRef);
          if (domain == null)
          {
            Timber.e("Failed to extract domain from %s", navigationUrlLocalRef);
            stylesheetString = EMPTY_ELEMHIDE_STRING;
            emuSelectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
          }
          else
          {
            // elemhide
            Timber.d("Requesting elemhide selectors from AdblockEngine for %s in %s",
                    navigationUrlLocalRef, this);

            final SiteKeysConfiguration siteKeysConfiguration = getSiteKeysConfiguration();
            final String siteKey = (siteKeysConfiguration != null
              ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
                .getAny(referrerChain, ""))
              : null);

            final boolean specificOnly = filterEngine.matches(navigationUrlLocalRef,
              FilterEngine.ContentType.maskOf(FilterEngine.ContentType.GENERICHIDE),
              Collections.<String>emptyList(), null) != null;

            if (specificOnly)
            {
              Timber.d("elemhide - specificOnly selectors");
            }

            stylesheetString = getProvider()
              .getEngine()
              .getElementHidingStyleSheet(navigationUrlLocalRef, domain, referrerChain, siteKey, specificOnly);

            Timber.d("Finished requesting elemhide stylesheet, got %d symbols in %s",
                    stylesheetString.length(), this);

            // elemhideemu
            Timber.d("Requesting elemhideemu selectors from AdblockEngine for %s in %s",
                    navigationUrlLocalRef, this);
            final List<FilterEngine.EmulationSelector> emuSelectors = getProvider()
              .getEngine()
              .getElementHidingEmulationSelectors(navigationUrlLocalRef, domain, referrerChain, siteKey);

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

    private void finish(final String selectorsString, final String emuSelectorsString)
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

    public void setFinishedRunnable(final Runnable runnable)
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

  private final Runnable elemHideThreadFinishedRunnable = new Runnable()
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

  private void startAbpLoading(final String newUrl)
  {
    Timber.d("Start loading %s", newUrl);

    loading = true;
    loadError = null;

    if (newUrl != null)
    {
      navigationUrl.compareAndSet(null, newUrl);

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
        final StringBuffer sb = new StringBuffer();
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

    if (AdblockWebView.this.canGoBack())
    {
      navigationUrl.set(null);
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

    if (AdblockWebView.this.canGoForward())
    {
      navigationUrl.set(null);
    }
    super.goForward();
  }

  @Override
  public void reload()
  {
    checkCookieSettings();
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.reload();
  }

  private void checkCookieSettings()
  {
    final boolean acceptCookies = CookieManager.getInstance().acceptCookie();
    acceptCookie.set(acceptCookies);
    // If cookies are disabled no need to check more
    if (acceptCookies)
    {
      // acceptThirdPartyCookies() needs to be called from UI thread
      acceptThirdPartyCookies.set(CookieManager.getInstance().acceptThirdPartyCookies(this));
    }
  }

  @Override
  public void loadUrl(final String url)
  {
    checkCookieSettings();
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadUrl(url);
  }

  @Override
  public void loadUrl(final String url, final Map<String, String> additionalHttpHeaders)
  {
    checkCookieSettings();
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadUrl(url, additionalHttpHeaders);
  }

  @Override
  public void loadData(final String data, final String mimeType, final String encoding)
  {
    checkCookieSettings();
    ensureProvider();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadData(data, mimeType, encoding);
  }

  @Override
  public void loadDataWithBaseURL(final String baseUrl, final String data, final String mimeType, final String encoding,
                                  final String historyUrl)
  {
    checkCookieSettings();
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
    private final Runnable disposeFinished;

    private DisposeRunnable(final Runnable disposeFinished)
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
      final AdblockEngine engine = getProvider().getEngine();
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

    final DisposeRunnable disposeRunnable = new DisposeRunnable(disposeFinished);
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
