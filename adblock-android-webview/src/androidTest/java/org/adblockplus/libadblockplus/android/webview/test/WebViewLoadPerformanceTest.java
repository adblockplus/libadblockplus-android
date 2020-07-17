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

package org.adblockplus.libadblockplus.android.webview.test;

import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

import static android.webkit.WebSettings.LOAD_NO_CACHE;
import static org.junit.Assert.assertTrue;


@RunWith(AndroidJUnit4.class)
public class WebViewLoadPerformanceTest
{
  private static final Context context =
      InstrumentationRegistry.getInstrumentation().getTargetContext();

  private static class WebViewTestSuit<T extends WebView>
  {
    private static final int MAX_PAGE_LOAD_WAIT_TIME_SEC = 20;
    private T webView;
    private final Map<String, Long> results = new HashMap<>();
    private final WebViewClient client = null;

    private class WebViewWaitingClient extends  WebViewClient
    {
      private String lastPageStartedUrl = "";
      private AtomicReference<Long> startTime = new AtomicReference<>(null);
      private final CountDownLatch countDownLatch;

      public WebViewWaitingClient(final CountDownLatch countDownLatch)
      {
        super();
        this.countDownLatch = countDownLatch;
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
      }

      @Override
      public void onPageFinished(final WebView view, final String url)
      {
        final Long startTimeValue = startTime.get();
        // When redirection happens there are several notifications so wee need to check if url matches
        if (Utils.getUrlWithoutParams(url).startsWith(Utils.getUrlWithoutParams((lastPageStartedUrl)))
            && startTimeValue != null)
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
          countDownLatch.countDown();
        }
      }
    }

    // Clear cookies and cache
    private void clearWebViewsState() throws InterruptedException
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

    private void loadUrlAndWait(final String url) throws InterruptedException
    {
      final CountDownLatch countDownLatch = new CountDownLatch(1);
      final WebViewWaitingClient webViewClient = new WebViewWaitingClient(countDownLatch);
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
    }

    public void measure(final String url) throws InterruptedException
    {
      clearWebViewsState();
      loadUrlAndWait(url);
    }
  }

  private final WebViewTestSuit<AdblockWebView> adblockTestSuit = new WebViewTestSuit<>();
  private final WebViewTestSuit<WebView> systemTestSuit = new WebViewTestSuit();

  @Rule
  public final Timeout globalTimeout = Timeout.seconds(900);

  @BeforeClass
  public static void setUpClass()
  {
    Timber.plant(new Timber.DebugTree());
    if (!AdblockHelper.get().isInit())
    {
      final String basePath =
          context.getDir(AdblockEngine.BASE_PATH_DIRECTORY, Context.MODE_PRIVATE).getAbsolutePath();
      AdblockHelper
          .get()
          .init(context, basePath, true, AdblockHelper.PREFERENCE_NAME);
    }
  }

  private void setUpWebView(final WebViewTestSuit testSuit)
  {
    Timber.d("setUp()");
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        testSuit.webView.getSettings().setJavaScriptEnabled(true);
        testSuit.webView.getSettings().setDomStorageEnabled(true);
        testSuit.webView.getSettings().setCacheMode(LOAD_NO_CACHE);
        testSuit.webView.setWebViewClient(testSuit.client);
      }
    });
    if (testSuit.webView instanceof AdblockWebView)
    {
      final AdblockWebView adblockWebView = (AdblockWebView) testSuit.webView;
      final AdblockEngineProvider adblockEngineProvider = AdblockHelper.get().getProvider();
      adblockWebView.setProvider(adblockEngineProvider);
      Timber.d("Before FE waitForReady()");
      AdblockHelper.get().getProvider().waitForReady();
      Timber.d("After FE waitForReady()");
    }
  }

  @LargeTest
  @Test
  public void testOnPageFinishedLoadTime() throws InterruptedException
  {
    // Setup adblockWebView
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        adblockTestSuit.webView = new AdblockWebView(context);
        systemTestSuit.webView = new WebView(context);
      }
    });

    setUpWebView(adblockTestSuit);
    setUpWebView(systemTestSuit);

    final List<String> urls = Arrays.asList(
     "https://ess.jio.com",
     "https://www.jiocinema.com",
     "https://www.jiomart.com",
     "https://www.jio.com",
     "https://www.flipkart.com",
     "https://www.amazon.com",
     "https://www.news18.com",
     "https://timesofindia.indiatimes.com/",
     "https://www.ndtv.com/",
     "https://www.indiatoday.in/",
     "https://indianexpress.com/",
     "https://www.thehindu.com/",
     "https://www.news18.com/",
     "https://www.firstpost.com/",
     "https://www.business-standard.com/",
     "https://www.dnaindia.com/",
     "https://www.deccanchronicle.com/",
     "https://www.oneindia.com/",
     "https://scroll.in/",
     "https://www.financialexpress.com/",
     "https://www.outlookindia.com/",
     "https://www.thequint.com/",
     "https://www.freepressjournal.in/",
     "https://telanganatoday.com/",
     "https://www.asianage.com/",
     "https://www.tentaran.com/",
     "https://topyaps.com/",
     "http://www.socialsamosa.com/",
     "https://www.techgenyz.com/",
     "https://www.orissapost.com/",
     "http://www.teluguglobal.in/",
     "https://www.yovizag.com/",
     "http://www.abcrnews.com/",
     "http://www.navhindtimes.in/",
     "https://chandigarhmetro.com/",
     "https://starofmysore.com/",
     "http://www.nagpurtoday.in/",
     "https://leagueofindia.com/",
     "https://arunachaltimes.in/",
     "https://www.latestnews1.com/",
     "https://knnindia.co.in/home",
     "https://newstodaynet.com/",
     "https://www.headlinesoftoday.com/",
     "https://www.gudstory.com/",
     "http://www.thetimesofbengal.com/",
     "http://www.risingkashmir.com/",
     "http://news.statetimes.in");

    int loopRandomizer = 1;
    for (final String url : urls)
    {
      Timber.d("testOnPageFinishedLoadTime() loads %s", url);
      // Let's randomized order
      final WebViewTestSuit[] testSuit = (loopRandomizer++ % 2 == 0
          ? new WebViewTestSuit[] { adblockTestSuit, systemTestSuit }
          : new WebViewTestSuit[] { systemTestSuit, adblockTestSuit });
      for (final WebViewTestSuit eachTestSuit : testSuit)
      {
        eachTestSuit.measure(url);
      }
    }

    // Above this threshold delta is suspicious so let's not count it
    final long MAX_DELTA_THRESHOLD_MS = 10000; // 10 seconds
    long adblockFinalResult = 0;
    long systemFinalResult = 0;
    for (final Map.Entry<String, Long> entry : systemTestSuit.results.entrySet())
    {
      // Check if entry exists in both maps and has valid value (value > 0)
      final Long adblockLoadTime = adblockTestSuit.results.get(entry.getKey());
      final long systemLoadTime = entry.getValue();
      if (adblockLoadTime != null)
      {
        if (adblockLoadTime > 0 && systemLoadTime > 0)
        {
          final long diff = adblockLoadTime - systemLoadTime;
          if (Math.abs(diff) > MAX_DELTA_THRESHOLD_MS)
          {
            Timber.d("Adblock is %s for %s of %d ms, rejecting this result!",
                (diff > 0 ? "slower" : "faster"), entry.getKey(), Math.abs(diff));
          }
          else
          {
            adblockFinalResult += adblockLoadTime;
            systemFinalResult += systemLoadTime;
          }
        }
        else
        {
          Timber.w("Skipping url `%s` from measurement due to lack of value!", entry.getKey());
        }
      }
      else
      {
        Timber.w("Skipping url `%s` from measurement as it was completed only in WebView!", entry.getKey());
      }
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        adblockTestSuit.webView.dispose(null);
      }
    });
    Timber.d("Adblock: compareResults() final pages load time is %s ms", adblockFinalResult);
    Timber.d("System: compareResults() final pages load time is %s ms", systemFinalResult);
    // Acceptance criteria: AdblockWebView adds no more than 10% delay on top of a system WebView
    assertTrue(adblockFinalResult - systemFinalResult < (systemFinalResult / 10));
  }
}
