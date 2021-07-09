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

package org.adblockplus.android.webview;

import android.os.Handler;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import org.adblockplus.android.Utils;
import org.adblockplus.sitekey.SiteKeyException;
import org.adblockplus.sitekey.SiteKeyVerifier;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

/**
 * Extracts <i>Site Key</i> from `html.data-adblockkey` tag
 *
 * <ol>
 *   <li>Adds a JS interface that will listen to the received key</li>
 *   <li>Injects JS during {@link android.webkit.WebChromeClient#onProgressChanged(WebView, int)}.
 *   See "assets/inject.js"</li>
 *   <li>JS calls {@link JsCallbackInterface#onSiteKeyExtracted(String, String, String)}
 *   and verifies the sitekey with {@link SiteKeyVerifier}</li>
 * </ol>
 *
 * @see BaseSiteKeyExtractor
 */
class JsSiteKeyExtractor extends BaseSiteKeyExtractor
{
  private volatile CountDownLatch latch;
  private final AtomicBoolean isSiteKeyProcessingFinished;
  private final AtomicBoolean isJavascriptInterfaceSet;
  private final Handler callerThreadHandler;
  private Runnable enableStateRunner;
  private Runnable disableStateRunner;

  JsSiteKeyExtractor(final AdblockWebView view)
  {
    super(view);
    callerThreadHandler = new Handler();
    view.addJavascriptInterface(new JsCallbackInterface(this), JsCallbackInterface.NAME);
    isJavascriptInterfaceSet = new AtomicBoolean(true);
    isSiteKeyProcessingFinished = new AtomicBoolean(false);
  }

  @Override
  public void setEnabled(final boolean enabled)
  {
    super.setEnabled(enabled);
    final AdblockWebView webView = webViewWeakReference.get();
    if (webView == null)
    {
      return;
    }
    if (enabled)
    {
      // we'd like not to change states rapidly
      // in case if setEnabled will be called repeatedly
      if (!isJavascriptInterfaceSet.get())
      {
        // lazy init + doing it in place for better readability
        if (enableStateRunner == null)
        {
          enableStateRunner = new Runnable()
          {
            @Override
            public void run()
            {
              webView.addJavascriptInterface(
                  new JsCallbackInterface(JsSiteKeyExtractor.this),
                  JsCallbackInterface.NAME);
              isJavascriptInterfaceSet.set(true);
            }
          };
        }
        Timber.d("Enabling JsSiteKeyExtractor");
        callerThreadHandler.post(enableStateRunner);
      }
    }
    else
    {
      // lazy init + doing it in place for better readability
      if (disableStateRunner == null)
      {
        disableStateRunner = new Runnable()
        {
          @Override
          public void run()
          {
            webView.removeJavascriptInterface(JsCallbackInterface.NAME);
            isJavascriptInterfaceSet.set(false);
          }
        };
      }
      Timber.d("Disabling JsSiteKeyExtractor");
      callerThreadHandler.post(disableStateRunner);
      if (latch != null)
      {
        latch.countDown();
        latch = null;
      }
    }
  }

  @Override
  public WebResourceResponse extract(final WebResourceRequest request)
  {
  /*
    obtainAndCheckSiteKey is not used here because was mainly created for
    HttpSiteKeyExtractor to be able to intercept request and initiate a custom one.

    Call to waitForSitekeyCheck does all the magic by holding the thread while
    extracting and verifying sitekey.

    Hence we are returning null and allowing all requests.
   */
    return null;
  }

  @Override
  public void startNewPage()
  {
    Timber.d("startNewPage() called");
    isSiteKeyProcessingFinished.set(false);
    latch = new CountDownLatch(1);
  }

  @Override
  public boolean waitForSitekeyCheck(final String url, final boolean isMainFrame)
  {
    if (isMainFrame)
    {
      return false;
    }

    if (!isEnabled() || isSiteKeyProcessingFinished.get())
    {
      return false;
    }

    final CountDownLatch countDownLatch = latch;
    if (countDownLatch == null)
    {
      Timber.w("waitForSitekeyCheck() called for `%s` with `latch == null`!", url);
      return false;
    }
    // waitSitekeyCheck is used only blocking the network thread while
    // the key verification is ongoing
    Timber.d("Holding request %s", url);
    try
    {
      countDownLatch.await(RESOURCE_HOLD_MAX_TIME_MS, TimeUnit.MILLISECONDS);
      Timber.d("Un-holding request %s", url);
    }
    catch (final InterruptedException error)
    {
      // not likely to happen
      Timber.e("Holding request error: %s", error);
    }
    return true;
  }

  private void verifySiteKey(final String url, final String userAgent, final String value)
  {
    try
    {
      final SiteKeyVerifier verifier = getSiteKeysConfiguration().getSiteKeyVerifier();
      if (verifier == null)
      {
        throw new AssertionError("Verifier must be set before this is called");
      }
      else if (verifier.verify(Utils.getUrlWithoutFragment(url), userAgent, value))
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
  }

  public static class JsCallbackInterface
  {
    static final String NAME = "AbpCallback";
    private final WeakReference<JsSiteKeyExtractor> extractorRef;
    // keeping ref for the purpose of self-remove from WebView
    // in case of JsSiteKeyExtractor has been destroyed
    private final WeakReference<AdblockWebView> webViewRef;

    private JsCallbackInterface(final JsSiteKeyExtractor extractor)
    {
      this.extractorRef = new WeakReference<>(extractor);
      this.webViewRef = extractor.webViewWeakReference;
    }

    /**
     * If reference to JsSiteKeyExtractor is dead
     * remove self from Javascript interfaces
     *
     * @return JsSiteKeyExtractor if still exist
     */
    private JsSiteKeyExtractor getExtractorIfStillExist()
    {
      final JsSiteKeyExtractor extractor = extractorRef.get();
      if (extractor != null)
      {
        return extractor;
      }
      final AdblockWebView webView = webViewRef.get();
      if (webView != null)
      {
        webView.removeJavascriptInterface(NAME);
      }
      return null;
    }

    @JavascriptInterface
    public void onSiteKeyExtracted(final String key, final String url, final String userAgent)
    {
      Timber.d("Received sitekey for \nurl: %s", url);
      final JsSiteKeyExtractor extractor = getExtractorIfStillExist();
      if (extractor == null || !extractor.isEnabled())
      {
        return;
      }
      if (key != null && !key.isEmpty())
      {
        extractor.verifySiteKey(url, userAgent, key);
      }
      // it its null, then we are faster then
      // the call to `waitForSitekeyCheck` and its safe to ignore
      final CountDownLatch countDownLatch = extractor.latch;
      if (countDownLatch != null)
      {
        countDownLatch.countDown();
      }
      extractor.isSiteKeyProcessingFinished.set(true);
    }

    @JavascriptInterface
    public void onSiteKeyDoesNotExist(final String url)
    {
      final JsSiteKeyExtractor extractor = getExtractorIfStillExist();
      if (extractor == null || !extractor.isEnabled())
      {
        return;
      }
      // this means that the DOM is ready
      // but the html site key does not exist
      // and no reason to wait more
      final CountDownLatch countDownLatch = extractor.latch;
      if (countDownLatch != null)
      {
        countDownLatch.countDown();
      }
      // collecting the url
      extractor.isSiteKeyProcessingFinished.set(true);
      Timber.d("Key does not exist on url %s", url);
    }

    @JavascriptInterface
    public void onDomNotReady(final String url)
    {
      final JsSiteKeyExtractor extractor = getExtractorIfStillExist();
      if (extractor == null || !extractor.isEnabled())
      {
        return;
      }
      // doing nothing at the moment
      // still holding the lock
      extractor.isSiteKeyProcessingFinished.set(false);
      Timber.d("DOM not yet ready on url %s", url);
    }
  }
}
