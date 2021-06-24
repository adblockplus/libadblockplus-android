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
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Pair;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.adblockplus.hermes.AdblockEngine;
import org.adblockplus.hermes.ContentType;
import org.adblockplus.hermes.EmulationSelector;
import org.adblockplus.hermes.MatchesResult;

import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.webview.content_type.ContentTypeDetector;
import org.adblockplus.libadblockplus.android.webview.content_type.HeadersContentTypeDetector;
import org.adblockplus.libadblockplus.android.webview.content_type.OrderedContentTypeDetector;
import org.adblockplus.libadblockplus.android.webview.content_type.UrlFileExtensionTypeDetector;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl;
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;
import org.jetbrains.annotations.TestOnly;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

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
  private static final String EMPTY_PARENT = "";
  private static final String EMPTY_SITEKEY = "";

  private OrderedContentTypeDetector contentTypeDetector;
  private Integer loadError;
  private ProxyWebChromeClient intWebChromeClient;
  private ProxyWebViewClient intWebViewClient;

  /*
   * url2Referrer map stores referrer mappings for the urls: url => its parent (referrer).
   * This data is critical for ABP allowlisting feature (blocked requests don't have children
   * subrequests hence this is logically important only for allowlisting features).
   * Because of a limitations of the WebView API we are populating this collection only based on
   * the "Referer" HTTP request header. But this header can be missing, most often due to the
   * fact that "Referrer-Policy" is set to "no-referrer" value.
   *
   * Lifecycle:
   * url2Referrer data is cleared on the following situations:
   *  - onPageStarted callback is called
   *  - goBack(), goForward(), reload(), load() WebView API methods are called.
   *
   *  We are adding entries to url2Referrer in the shouldAbpBlockRequest() method, and in the same
   *  method url2Referrer is traversed to build frames hierarchy for the request. Here are important
   *  things to remember:
   *  - For requests of type ContentType.SUBDOCUMENT we are adding two mappings:
   *  requestUrl => referrer, Utils.getOrigin(url) => referrer (see DP-1621)
   *  - As mentioned before, when "Referer" HTTP request header was missing for some request, we
   *  will not be able to build a complete frames hierarchy. To mitigate this problem slightly, when
   *  reading url2Referrer to build frames hierarchy we are making sure that root (navigation url)
   *  is added to the frames hierarchy (see DP-1763)
   *  - When reading entries we are making sure that there is no loop (see DP-184).
   */
  private final Map<String, String> url2Referrer
    = Collections.synchronizedMap(new HashMap<String, String>());
  /*
   * Map with data: url => <elemhide selectors, elemhideemu selectors>.
   * This data is collected only for main frame and subframes.
   * Map is cleared when we detect a new page is being loaded or when ABP is disabled.
   */
  private final Map<String, Pair<String, String>> url2Stylesheets
    = Collections.synchronizedMap(new HashMap<String, Pair<String, String>>());
  private final AtomicReference<String> navigationUrl = new AtomicReference<>();
  private String injectJs;
  private String elemhideBlockedJs;
  private boolean loading;
  private String elementsHiddenFlag;
  private String sitekeyExtractedFlag;
  private SiteKeyExtractor siteKeyExtractor;
  private final AtomicReference<AdblockEngine> adblockEngineRef = new AtomicReference<>();

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
     *
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
      private final ContentType contentType;

      BlockedResourceInfo(final String requestUrl,
                          final List<String> parentFrameUrls,
                          final ContentType contentType)
      {
        super(requestUrl, parentFrameUrls);
        this.contentType = contentType;
      }

      public ContentType getContentType()
      {
        return contentType;
      }
    }

    /**
     * Allowlisting reason:
     */
    enum AllowlistReason
    {
      /**
       * Document is allowlisted
       */
      DOCUMENT,

      /**
       * Domain is allowlisted by user
       */
      DOMAIN,

      /**
       * Exception filter
       */
      FILTER
    }

    /**
     * Immutable data-class containing an auxiliary information about allowlisted resource.
     */
    final class AllowlistedResourceInfo extends ResourceInfo
    {
      private final AllowlistReason reason;

      public AllowlistedResourceInfo(final String requestUrl,
                                     final List<String> parentFrameUrls,
                                     final AllowlistReason reasons)
      {
        super(requestUrl, parentFrameUrls);
        this.reason = reasons;
      }

      public AllowlistReason getReason()
      {
        return reason;
      }
    }

    /**
     * "Navigation" event.
     * <p>
     * This method is called when the current instance of WebView begins loading of a new page.
     * It corresponds to `onPageStarted` of `WebViewClient` and is called on the UI thread.
     */
    void onNavigation();

    /**
     * "Resource loading blocked" event.
     * <p>
     * This method can be called on a background thread.
     * It should not block the thread for too long as it slows down resource loading.
     *
     * @param info contains auxiliary information about a blocked resource.
     */
    void onResourceLoadingBlocked(final BlockedResourceInfo info);

    /**
     * "Resource loading allowlisted" event.
     * <p>
     * This method can be called on a background thread.
     * It should not block the thread for too long as it slows down resource loading.
     *
     * @param info contains auxiliary information about a blocked resource.
     */
    void onResourceLoadingAllowlisted(final AllowlistedResourceInfo info);
  }

  private final AtomicReference<EventsListener> eventsListenerAtomicReference
    = new AtomicReference<>();
  private final AtomicReference<SiteKeysConfiguration> siteKeysConfiguration =
    new AtomicReference<>();
  private final AtomicBoolean jsInIframesEnabled = new AtomicBoolean(false);

  public String getNavigationUrl()
  {
    return navigationUrl.get();
  }

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

  public void enableJsInIframes(final boolean jsInIframesEnabled) throws IllegalStateException
  {
    if (jsInIframesEnabled == true && getSiteKeysConfiguration() == null)
    {
      throw new IllegalStateException(
        "Site Keys configuration must be set (enabled) to use this feature!");
    }
    this.jsInIframesEnabled.set(jsInIframesEnabled);
  }

  public boolean getJsInIframesEnabled()
  {
    return jsInIframesEnabled.get() == true && getSiteKeysConfiguration() != null;
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

  public String getInjectJs()
  {
    return injectJs;
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

  public void setEngine(final AdblockEngine adblockEngine)
  {
    adblockEngineRef.set(adblockEngine);
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
      Timber.d("onProgressChanged to %d%% for url: %s", newProgress, view.getUrl());
      tryInjectJs();

      super.onProgressChanged(view, newProgress);
    }
  }

  private void tryInjectJs()
  {
    if (adblockEngineRef.get() == null)
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

  private void clearStylesheets()
  {
    Timber.d("Clearing stylesheet");
    url2Stylesheets.clear();
  }

  private enum AbpShouldBlockResult
  {
    // AdblockEngine is released or
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

  public static class WebResponseResult
  {
    // decisions
    public static final String RESPONSE_CHARSET_NAME = "UTF-8";
    public static final String RESPONSE_MIME_TYPE = "text/plain";

    public static final WebResourceResponse ALLOW_LOAD = null;
    public static final WebResourceResponse BLOCK_LOAD =
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
    public boolean shouldOverrideUrlLoading(final WebView view, final WebResourceRequest request)
    {
      Timber.d("shouldOverrideUrlLoading called for view.getUrl() %s", view.getUrl());
      clearReferrers();
      navigationUrl.set(null);
      return super.shouldOverrideUrlLoading(view, request);
    }

    @Override
    public boolean shouldOverrideUrlLoading(final WebView view, final String url)
    {
      Timber.d("shouldOverrideUrlLoading called for url %s", url);
      clearReferrers();
      navigationUrl.set(null);
      return super.shouldOverrideUrlLoading(view, url);
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
      Timber.d("onPageFinished called for url %s", url);
      loading = false;

      super.onPageFinished(view, url);
    }

    @Override
    public void onReceivedError(final WebView view, final int errorCode, final String description,
                                final String failingUrl)
    {
      Timber.e("onReceivedError:" +
          " code=%d" +
          " with description=%s" +
          " for url=%s",
        errorCode, description, failingUrl);
      loadError = errorCode;

      super.onReceivedError(view, errorCode, description, failingUrl);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onReceivedError(final WebView view, final WebResourceRequest request,
                                final WebResourceError error)
    {
      Timber.e("onReceivedError:" +
          " code=%d" +
          " with description=%s" +
          " for url=%s" +
          " request.isForMainFrame()=%s",
        error.getErrorCode(), error.getDescription(), request.getUrl(),
        request.isForMainFrame());

      super.onReceivedError(view, request, error);
    }

    private AbpShouldBlockResult notifyAndReturnBlockingResponse(final String requestUrl,
                                                                 final List<String> parentFrameUrls,
                                                                 final ContentType contentType)
    {
      if (isVisibleResource(contentType))
      {
        elemhideBlockedResource(requestUrl);
      }
      notifyResourceBlocked(new EventsListener.BlockedResourceInfo(requestUrl,
        parentFrameUrls, contentType));
      return AbpShouldBlockResult.BLOCK_LOAD;
    }

    private String getFirstParent(final List<String> referrerChain)
    {
      return (referrerChain == null || referrerChain.size() == 0) ? EMPTY_PARENT : referrerChain.get(0);
    }

    private AbpShouldBlockResult shouldAbpBlockRequest(final WebResourceRequest request)
    {
      // here we just trying to fill url -> referrer map
      final String url = request.getUrl().toString();
      final String urlWithoutFragment = Utils.getUrlWithoutFragment(url);

      final boolean isMainFrame = request.isForMainFrame();
      boolean isAllowlisted = false;
      boolean canContainSitekey = false;
      final boolean sitekeyCheckEnabled = true;

      final String referrer = request.getRequestHeaders().get(HttpClient.HEADER_REFERRER);

      final AdblockEngine engine = adblockEngineRef.get();
      if (engine == null)
      {
        return AbpShouldBlockResult.NOT_ENABLED;
      }

      Timber.d("Loading url %s", url);

      // We don't want to use a referrer from the previous page, if previous page was allowlisted that would also
      // allowlist current one which is wrong, hence added !isMainFrame
      if (referrer != null && !isMainFrame)
      {
        Timber.d("Header referrer for %s is %s", url, referrer);
        if (!url.equals(referrer))
        {
          url2Referrer.put(urlWithoutFragment, referrer);
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

      if (!sitekeyCheckEnabled && BuildConfig.DEBUG)
      {
          /*final List<Subscription> listedSubscriptions = engine.settings().getListedSubscriptions();
          for (Subscription subscription : listedSubscriptions)
          {
            if (subscription.url.contains("abp-testcase-subscription.txt"))
            {
              sitekeyCheckEnabled = true;
              break;
            }
          }*/
      }
      if (isMainFrame)
      {
        // never blocking main frame requests, just subrequests
        Timber.w("%s is main frame, allow loading", url);
        siteKeyExtractor.setEnabled(sitekeyCheckEnabled);
        // For a main frame we don't need to check result of generateStylesheetForUrl as we still
        // need to inject js for a site key (site key check is disabled in inject.js for subframes).
        clearStylesheets();
        generateStylesheetForUrl(urlWithoutFragment, true);
      }
      else
      {
        // reconstruct frames hierarchy
        final List<String> referrerChain = buildFramesHierarchy(urlWithoutFragment);

        final SiteKeysConfiguration siteKeysConfiguration = getSiteKeysConfiguration();
        String siteKey = (siteKeysConfiguration != null
          ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
          .getAny(referrerChain, EMPTY_SITEKEY))
          : EMPTY_SITEKEY);

        // determine the content
        ContentType contentType =
          ensureContentTypeDetectorCreatedAndGet().detect(request);

        if (contentType == null)
        {
          Timber.w("contentTypeDetector didn't recognize content type");
          contentType = ContentType.OTHER;
        }

        if (contentType == ContentType.SUBDOCUMENT && referrer != null)
        {
          // Due to "strict-origin-when-cross-origin" referrer policy set as default starting
          // Chromium 85 we have to fix the referrers chain with just "origin".
          // See https://jira.eyeo.com/browse/DP-1621
          try
          {
            url2Referrer.put(Utils.getOrigin(url), referrer);
          }
          catch (final MalformedURLException | IllegalArgumentException e)
          {
            Timber.e(e, "Failed to extract origin from %s", url);
          }
        }

        // allowlisted
        if (engine.isContentAllowlisted(url,
          ContentType.maskOf(ContentType.DOCUMENT), referrerChain,
          siteKey))
        {
          isAllowlisted = true;
          Timber.w("%s document is allowlisted, allow loading", url);
          notifyResourceAllowlisted(new EventsListener.AllowlistedResourceInfo(
            url, referrerChain, EventsListener.AllowlistReason.DOCUMENT));
        }
        else
        {
          if (contentType == ContentType.SUBDOCUMENT ||
            contentType == ContentType.OTHER)
          {
            canContainSitekey = true;
          }

          boolean specificOnly = engine.isContentAllowlisted(url,
            ContentType.maskOf(ContentType.GENERICBLOCK),
            referrerChain, siteKey);
          if (specificOnly)
          {
            Timber.w("Found genericblock filter for url %s", url);
          }

          // check if we should block
          MatchesResult result =
            engine.isContentAllowlisted(url, ContentType.maskOf(contentType),
              referrerChain, siteKey) ? MatchesResult.ALLOWLISTED :
              engine.matches(url, ContentType.maskOf(contentType),
                getFirstParent(referrerChain), siteKey, specificOnly);

          if (result == MatchesResult.BLOCKED)
          {
            Timber.i("Attempting to block request with AA on the first try: %s", url);

            // Need to run `waitForSitekeyCheck` to hold the actual check until
            // the sitekey is either obtained or not present
            final boolean waitedForSitekey = siteKeyExtractor.waitForSitekeyCheck(url, isMainFrame);
            if (waitedForSitekey)
            {
              // Request was held, start over to see if it's now allowlisted
              Timber.i("Restarting the check having waited for the sitekey: %s", url);

              siteKey = (siteKeysConfiguration != null
                ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
                .getAny(referrerChain, EMPTY_SITEKEY))
                : EMPTY_SITEKEY);

              if (siteKey.isEmpty())
              {
                Timber.i("SiteKey is not found, blocking the resource %s", url);
                return notifyAndReturnBlockingResponse(url, referrerChain, contentType);
              }

              if (engine.isContentAllowlisted(url,
                ContentType.maskOf(ContentType.DOCUMENT),
                referrerChain, siteKey))
              {
                isAllowlisted = true;
                Timber.w("%s document is allowlisted, allow loading", url);
                notifyResourceAllowlisted(new EventsListener.AllowlistedResourceInfo(
                  url, referrerChain, EventsListener.AllowlistReason.DOCUMENT));
              }
              else
              {
                specificOnly = engine.isContentAllowlisted(url,
                  ContentType.maskOf(ContentType.GENERICBLOCK),
                  referrerChain, siteKey);
                if (specificOnly)
                {
                  Timber.w("Found genericblock filter for url %s", url);
                }

                // check if we should block
                result =
                  engine.isContentAllowlisted(url, ContentType.maskOf(contentType),
                    referrerChain, siteKey) ? MatchesResult.ALLOWLISTED :
                    engine.matches(url, ContentType.maskOf(contentType),
                      getFirstParent(referrerChain), siteKey, specificOnly);

                if (result == MatchesResult.BLOCKED)
                {
                  Timber.i("Blocked loading %s with sitekeyCheckEnabled %s", url,
                    sitekeyCheckEnabled ? "enabled" : "disabled");
                  return notifyAndReturnBlockingResponse(url, referrerChain, contentType);
                }
                if (result == MatchesResult.ALLOWLISTED)
                {
                  isAllowlisted = true;
                  Timber.w("%s is allowlisted in matches()", url);
                  notifyResourceAllowlisted(new EventsListener.AllowlistedResourceInfo(
                    url, referrerChain, EventsListener.AllowlistReason.FILTER));
                }
                Timber.d("Allowed loading %s", url);
              }
            } // if (waitedForSitekey)

            // This check is required because the resource could be allowlisted on the second
            // check after waiting for the sitekey check conclusion
            if (!isAllowlisted)
            {
              Timber.i("Blocked loading %s with sitekeyCheckEnabled %s", url,
                sitekeyCheckEnabled ? "enabled" : "disabled");
              return notifyAndReturnBlockingResponse(url, referrerChain, contentType);
            }
          }
          else if (result == MatchesResult.ALLOWLISTED)
          {
            isAllowlisted = true;
            Timber.w("%s is allowlisted in matches()", url);
            notifyResourceAllowlisted(new EventsListener.AllowlistedResourceInfo(
              url, referrerChain, EventsListener.AllowlistReason.FILTER));
          }
          Timber.d("Allowed loading %s", url);
        }
      } // !MainFrame

      // we rely on calling `fetchUrlAndCheckSiteKey` later in `shouldInterceptRequest`, now we
      // just reply that it's fine to load the resource
      final SiteKeysConfiguration siteKeysConfiguration = getSiteKeysConfiguration();
      if ((sitekeyCheckEnabled || (siteKeysConfiguration != null && siteKeysConfiguration.getForceChecks()))
        && (isMainFrame || (canContainSitekey && !isAllowlisted)))
      {
        // if url is a main frame (allowlisted by default) or can contain by design a site key header
        // (it content type is SUBDOCUMENT or OTHER) and it is not yet allowlisted then we need to
        // make custom HTTP get request to try to obtain a site key header.
        return AbpShouldBlockResult.ALLOW_LOAD;
      }

      return AbpShouldBlockResult.ALLOW_LOAD_NO_SITEKEY_CHECK;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(final WebView view, final WebResourceRequest request)
    {
      final Uri url = request.getUrl();
      final String urlString = url.toString();

      if (request.isForMainFrame())
      {
        Timber.d("Updating navigationUrl to `%s`", urlString);
        navigationUrl.set(Utils.getUrlWithoutFragment(urlString));
        /*if (BuildConfig.DEBUG)
        {
          if (RequestInterceptor.isBlockedByHandlingDebugURLQuery(view, getProvider(), url))
          {
            return WebResponseResult.BLOCK_LOAD;
          }
        }*/
      }
      final AbpShouldBlockResult abpBlockResult = shouldAbpBlockRequest(request);

      // if AdblockEngine is unavailable or not enabled, just let it go (and skip sitekey check)
      if (AbpShouldBlockResult.NOT_ENABLED.equals(abpBlockResult))
      {
        clearStylesheets();
        return WebResponseResult.ALLOW_LOAD;
      }

      // if url should be blocked, we are not performing any further actions
      if (AbpShouldBlockResult.BLOCK_LOAD.equals(abpBlockResult))
      {
        return WebResponseResult.BLOCK_LOAD;
      }

      final Map<String, String> requestHeaders = request.getRequestHeaders();

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
            getSiteKeysConfiguration().getSiteKeyVerifier().verifyInHeaders(urlString,
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

      return siteKeyExtractor.extract(request);
    }
  }

  private List<String> buildFramesHierarchy(final String urlWithoutFragment)
  {
    final List<String> referrerChain = new ArrayList<>();
    String parent = urlWithoutFragment;
    while ((parent = url2Referrer.get(parent)) != null)
    {
      if (referrerChain.contains(parent))
      {
        Timber.w("Detected referrer loop, finished creating referrers list");
        break;
      }
      referrerChain.add(parent);
    }

    // Here we discover if referrerChain is empty or incomplete (i.e. does not contain the
    // navigation url) so we add at least the top referrer which is navigationUrl.
    final String navigationUrlLocal = navigationUrl.get();
    if (TextUtils.isEmpty(navigationUrlLocal))
    {
      return referrerChain; //early exit
    }
    final String navigationUrlDomain = Utils.getDomain(navigationUrlLocal);
    if (TextUtils.isEmpty(navigationUrlDomain))
    {
      Timber.e("buildFramesHierarchy() failed to obtain a domain from url " + navigationUrlLocal);
      return referrerChain; //early exit
    }
    boolean canAddTopLevelParent = false;
    if (!referrerChain.isEmpty())
    {
      // Let's check if we already have a top level domain same as navigationUrlDomain, and if
      // not then add a top level parent.
      final String currentTopLevelDomain = Utils.getDomain(referrerChain.get(referrerChain.size() - 1));
      if (!navigationUrlDomain.equals(currentTopLevelDomain))
      {
        canAddTopLevelParent = true;
      }
    }
    if (referrerChain.isEmpty() || canAddTopLevelParent)
    {
      Timber.d("Adding top level referrer `%s` for `%s`", navigationUrlLocal,
        urlWithoutFragment);
      referrerChain.add(navigationUrlLocal);
    }
    return referrerChain;
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

  private void notifyResourceAllowlisted(final EventsListener.AllowlistedResourceInfo info)
  {
    final EventsListener eventsListener = getEventsListener();
    if (eventsListener != null)
    {
      eventsListener.onResourceLoadingAllowlisted(info);
    }
  }

  private boolean isVisibleResource(final ContentType contentType)
  {
    return
      contentType == ContentType.IMAGE ||
        contentType == ContentType.MEDIA ||
        contentType == ContentType.OBJECT ||
        contentType == ContentType.SUBDOCUMENT;
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

  private void startAbpLoading(final String newUrl)
  {
    Timber.d("Start loading %s", newUrl);

    loading = true;
    loadError = null;

    if (newUrl != null)
    {
      final String urlWithoutFragment = Utils.getUrlWithoutFragment(newUrl);
      if (navigationUrl.compareAndSet(null, urlWithoutFragment))
      {
        // If we get here it usually means that shouldInterceptRequest() was not called for the main
        // frame so let's do it now
        generateStylesheetForUrl(urlWithoutFragment, true);
      }
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
      siteKeyExtractor.startNewPage();
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
      siteKeyExtractor.startNewPage();
    }
    super.goForward();
  }

  @Override
  public void reload()
  {
    if (loading)
    {
      stopAbpLoading();
    }

    super.reload();
  }

  @Override
  public WebBackForwardList restoreState(final Bundle inState)
  {
    siteKeyExtractor.startNewPage();
    return super.restoreState(inState);
  }

  private void loadUrlCommon()
  {
    if (loading)
    {
      stopAbpLoading();
    }

    siteKeyExtractor.startNewPage();
  }

  @Override
  public void loadUrl(final String url)
  {
    loadUrlCommon();
    super.loadUrl(url);
  }

  @Override
  public void loadUrl(final String url, final Map<String, String> additionalHttpHeaders)
  {
    loadUrlCommon();
    super.loadUrl(url, additionalHttpHeaders);
  }

  @Override
  public void loadData(final String data, final String mimeType, final String encoding)
  {
    loadUrlCommon();
    super.loadData(data, mimeType, encoding);
  }

  @Override
  public void loadDataWithBaseURL(final String baseUrl, final String data, final String mimeType,
                                  final String encoding, final String historyUrl)
  {
    loadUrlCommon();
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
  }

  private String emulationSelectorListToJsonArray(final List<EmulationSelector> list)
  {
    final JSONArray array = new JSONArray();

    if (list != null)
    {
      for (final EmulationSelector selector : list)
      {
        if (selector != null)
        {
          try
          {
            final JSONObject selectorObj = new JSONObject();
            selectorObj.put("selector", selector.selector);
            selectorObj.put("text", selector.text);
            array.put(selectorObj);
          }
          catch (final JSONException e)
          {
            Timber.e(e, "Failed to create JSON object");
          }
        }
      }
    }

    return array.toString();
  }

  public boolean generateStylesheetForUrl(final String urlWithoutFragment, final boolean isMainFrame)
  {
    final boolean isJsInIframesEnabled = getJsInIframesEnabled();
    Timber.d("generateStylesheetForUrl() called for url %s, isMainFrame = %b, " +
      "isJsInIframesEnabled == %b", urlWithoutFragment, isMainFrame, isJsInIframesEnabled);
    final AdblockEngine adblockEngine = adblockEngineRef.get();
    if (!isMainFrame && !isJsInIframesEnabled || adblockEngine == null)
    {
      return false;
    }

    final String domain = Utils.getDomain(urlWithoutFragment);
    if (TextUtils.isEmpty(domain))
    {
      Timber.e("Failed to extract domain from %s", urlWithoutFragment);
      return false;
    }

    String stylesheetString = EMPTY_ELEMHIDE_STRING;
    String emuSelectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;

    // Check if css was already generated
    final Pair<String, String> stylesheets = url2Stylesheets.get(urlWithoutFragment);
    if (stylesheets != null)
    {
      return !stylesheets.first.equals(EMPTY_ELEMHIDE_STRING) ||
        !stylesheets.second.equals(EMPTY_ELEMHIDE_ARRAY_STRING);
    }

    final List<String> referrerChain = isMainFrame ?
      new ArrayList<String>() : buildFramesHierarchy(urlWithoutFragment);

    referrerChain.add(0, urlWithoutFragment);
    final SiteKeysConfiguration siteKeysConfiguration = getSiteKeysConfiguration();
    String siteKey = (siteKeysConfiguration != null
      ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
      .getAny(referrerChain, EMPTY_SITEKEY))
      : null);

    if (!isMainFrame && siteKeysConfiguration != null && siteKey.isEmpty())
    {
      Timber.d("Waiting for a site key when handling %s", urlWithoutFragment);
      final boolean waited = siteKeyExtractor.waitForSitekeyCheck(urlWithoutFragment, isMainFrame);
      if (waited)
      {
        siteKey = PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
          .getAny(referrerChain, EMPTY_SITEKEY));
      }
    }

    if (!adblockEngine.isContentAllowlisted(urlWithoutFragment,
      ContentType.maskOf(ContentType.DOCUMENT, ContentType.ELEMHIDE), referrerChain, siteKey))
    {
      final boolean specificOnly = adblockEngine.isContentAllowlisted(urlWithoutFragment,
        ContentType.maskOf(ContentType.GENERICHIDE),
        referrerChain, siteKey);

      // elemhide
      Timber.d("Requesting elemhide stylesheet from AdblockEngine for %s", domain);
      stylesheetString = adblockEngine.getElementHidingStyleSheet(domain, specificOnly);
      Timber.d("Finished requesting elemhide stylesheet, got %d symbols" +
          (specificOnly ? " (specificOnly)" : "") + " for %s", stylesheetString.length(),
        domain);

      // elemhideemu
      Timber.d("Requesting elemhideemu selectors from AdblockEngine for %s", domain);
      final List<EmulationSelector> emuSelectors = adblockEngine.getElementHidingEmulationSelectors(domain);
      Timber.d("Finished requesting elemhideemu selectors, got %d symbols for %s",
        emuSelectors.size(), domain);
      emuSelectorsString = emulationSelectorListToJsonArray(emuSelectors);
    }
    else
    {
      Timber.d("Skipped requesting elemhide stylesheet and elemhideemu selectors for allowlisted %s",
        urlWithoutFragment);
    }
    url2Stylesheets.put(urlWithoutFragment, new Pair<>(stylesheetString, emuSelectorsString));
    // return true if elemhide OR elemhideemu data was provided
    return !stylesheetString.equals(EMPTY_ELEMHIDE_STRING) ||
      !emuSelectorsString.equals(EMPTY_ELEMHIDE_ARRAY_STRING);
  }

  private Pair<String, String> getStylesheetsForUrl(final String url)
  {
    return url2Stylesheets.get(Utils.getUrlWithoutFragment(url));
  }

  // warning: do not rename (used in injected JS by method name)
  @JavascriptInterface
  public String getElemhideStyleSheet(final String url)
  {
    final Pair<String, String> sylesheets = getStylesheetsForUrl(url);
    if (sylesheets != null)
    {
      Timber.d("Elemhide selectors for `%s`, %d bytes", url, sylesheets.first.length());
      return sylesheets.first;
    }
    return EMPTY_ELEMHIDE_STRING;
  }

  // warning: do not rename (used in injected JS by method name)
  @JavascriptInterface
  public String getElemhideEmulationSelectors(final String url)
  {
    final Pair<String, String> sylesheets = getStylesheetsForUrl(url);
    if (sylesheets != null)
    {
      Timber.d("Elemhideemu selectors for `%s`, %d bytes", url, sylesheets.second.length());
      return sylesheets.second;
    }
    return EMPTY_ELEMHIDE_ARRAY_STRING;
  }

  private void doDispose()
  {
    Timber.w("Disposing AdblockEngine");
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
   *
   * @param disposeFinished runnable to run when AdblockWebView is disposed
   */
  public void dispose(final Runnable disposeFinished)
  {
    Timber.d("Dispose invoked");
  }
}
