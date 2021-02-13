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

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.platform.app.InstrumentationRegistry;

import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

import static android.webkit.WebSettings.LOAD_NO_CACHE;

public class WebViewTestSuit<T extends WebView>
{
  private final static int MAX_PAGE_LOAD_WAIT_TIME_SEC = 60;
  public T webView;
  public WebViewClient extWebViewClient;
  public final Map<String, Long> results = new HashMap<>();
  public final WebViewClient client = null;

  public void setUp()
  {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(LOAD_NO_CACHE);
        webView.setWebViewClient(client);
      }
    });

    if (webView instanceof AdblockWebView)
    {
      final AdblockWebView adblockWebView = (AdblockWebView)webView;
      final AdblockEngineProvider adblockEngineProvider = AdblockHelper.get().getProvider();
      adblockWebView.setProvider(adblockEngineProvider);
      Timber.d("Before FE waitForReady()");
      AdblockHelper.get().getProvider().waitForReady();
      Timber.d("After FE waitForReady()");
    }
  }

  public void tearDown()
  {
    if (webView instanceof AdblockWebView)
    {
      ((AdblockWebView)webView).dispose(null);
    }
  }

  private class WebViewWaitingClient extends WebViewClient
  {
    private String lastPageStartedUrl = "";
    private String expectedUrl = null;
    private AtomicReference<Long> startTime = new AtomicReference<>(null);
    private final CountDownLatch countDownLatch;
    private final WebViewClient extWebViewClient;

    public WebViewWaitingClient(final CountDownLatch countDownLatch,
                                final WebViewClient extWebViewClient)
    {
      super();
      this.countDownLatch = countDownLatch;
      this.extWebViewClient = extWebViewClient;
    }

    public void setExpectedUrl(final String expectedUrl)
    {
      this.expectedUrl = expectedUrl;
    }

    public void resetTimer()
    {
      startTime = new AtomicReference<>(null);
    }

    @Override
    public void onPageStarted(final WebView view, final String url, final Bitmap favicon)
    {
      Timber.d("onPageStarted called for url %s", url);
      lastPageStartedUrl = url;
      if (startTime.get() == null)
      {
        startTime.set(System.currentTimeMillis());
      }
      if (extWebViewClient != null)
      {
        extWebViewClient.onPageStarted(view, url, favicon);
      }
    }

    @Override
    public void onPageFinished(final WebView view, final String url)
    {
      final Long startTimeValue = startTime.get();
      // When redirection happens there are several notifications so wee need to check if url matches
      // Empty expectedUrl could be fed to allow to work with any URL
      final Boolean urlIsOkay = expectedUrl == null
        ? Utils.getUrlWithoutParams(url).startsWith(Utils.getUrlWithoutParams((lastPageStartedUrl)))
        : url.equals(expectedUrl) || expectedUrl.isEmpty();

      if (urlIsOkay && startTimeValue != null)
      {
        final Long timeDelta = System.currentTimeMillis() - startTimeValue;
        Timber.d("onPageFinished called for urls %s after %d ms (%s)", url, timeDelta,
            lastPageStartedUrl);
        if (timeDelta > 0)
        {
          // We strip urls from params as they may differ between the calls in Adblock and system
          // WebView (f.e. param can contain timestamp)
          WebViewTestSuit.this.results.put(Utils.getUrlWithoutParams(url), timeDelta);
        }
        resetTimer();
        if (extWebViewClient != null)
        {
          extWebViewClient.onPageFinished(view, url);
        }
        countDownLatch.countDown();
      }
      else if (extWebViewClient != null)
      {
        extWebViewClient.onPageFinished(view, url);
      }
    }

    @Override
    public void onReceivedSslError(final WebView view,
                                   final SslErrorHandler handler,
                                   final SslError error)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedSslError(view, handler, error);
      }
      else
      {
        // warning: do not call `super` method if having `extWebViewClient`:
        // it will prevent relaxing SSL errors
        super.onReceivedSslError(view, handler, error);
      }
    }
  }

  // Clear cookies and cache
  public void clearWebViewsState() throws InterruptedException
  {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        webView.clearCache(true);
        CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>()
        {
          @Override
          public void onReceiveValue(final Boolean value)
          {
            if (!value)
            {
              CookieManager.getInstance().removeAllCookie();
            }
            countDownLatch.countDown();
          }
        });
      }
    });
    countDownLatch.await();
  }

  public boolean loadUrlAndWait(final String url, final String expectedUrl) throws InterruptedException
  {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final WebViewWaitingClient webViewClient = new WebViewWaitingClient(
        countDownLatch, extWebViewClient);
    if (expectedUrl != null)
    {
      webViewClient.setExpectedUrl(expectedUrl);
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        webView.setWebViewClient(webViewClient);
        webView.loadUrl(url);
      }
    });
    final boolean hasFinished = countDownLatch.await(MAX_PAGE_LOAD_WAIT_TIME_SEC, TimeUnit.SECONDS);
    if (!hasFinished)
    {
      webViewClient.resetTimer();
      Timber.w("Skipping url `%s` from measurement due to too long loading time in %s!",
          url, (webView instanceof AdblockWebView ? "AdblockWebView" : "WebView"));
    }
    return hasFinished;
  }

  public boolean loadUrlAndWait(final String url) throws InterruptedException
  {
    return loadUrlAndWait(url, null);
  }

  public void measure(final String url) throws InterruptedException
  {
    clearWebViewsState();
    loadUrlAndWait(url);
  }
}
