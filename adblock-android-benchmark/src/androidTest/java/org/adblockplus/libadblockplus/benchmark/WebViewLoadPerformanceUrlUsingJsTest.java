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

package org.adblockplus.libadblockplus.benchmark;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.benchmark.BenchmarkState;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class WebViewLoadPerformanceUrlUsingJsTest
{
  private static final List<String> TESTING_URLS = Arrays.asList(
      "https://www.amazon.com/s?k=iphone&ref=nb_sb_noss_2",
      "https://www.google.com/search?source=hp&ei=Di5NXdzQL6S0gwePqrugDw&q=laptops&oq=laptops&gs_l=psy-ab.3..0l10.871.2625..2730...3.0..0.59.396.9......0....1..gws-wiz.....0.LLzQyUKDP2A&ved=0ahUKEwjcj6TgrvXjAhUk2uAKHQ_VDvQQ4dUDCAc&uact=5",
      "https://www.xvideos.com/",
      "https://www.bing.com/search?q=laptop&qs=n&form=QBLH&sp=-1&pq=laptop&sc=8-6&sk=&cvid=81A2899A72C44903A148188906E7DCEE",
//      "https://www.baidu.com/s?tn=50000021_hao_pg&ie=utf-8&sc=UWd1pgw-pA7EnHc1FMfqnHRvPHn1rjTzP10YPiuW5y99U1Dznzu9m1YzPW01n1RsPjTd&ssl_sample=normal&srcqid=3368502456200926350&H123Tmp=nunew7&word=iphone",
      "https://search.yahoo.com/search;_ylt=AwrEzedALE1d9ocA.lpDDWVH;_ylc=X1MDMTE5NzgwNDg2NwRfcgMyBGZyAwRncHJpZANqQjNGWDVlZFFDbWlWMlR1OXNCQVhBBG5fcnNsdAMwBG5fc3VnZwM5BG9yaWdpbgNzZWFyY2gueWFob28uY29tBHBvcwMwBHBxc3RyAwRwcXN0cmwDBHFzdHJsAzYEcXVlcnkDaXBob25lBHRfc3RtcAMxNTY1MzM4Njkx?fr2=sb-top-search&p=iphone&fr=sfp&iscqry=",
      "https://yandex.com/search/?text=iphone&lr=98",
      "https://www.youtube.com/results?search_query=casey+neistat",
      "https://en.wikipedia.org/wiki/Neodymium_magnet",
      "https://www.imdb.com/title/tt0119654/",
      "https://cn.hao123.com/",
      "https://www.ndtv.com/world-news/pentagon-readies-talks-with-russia-on-syria-operations-1224527",
      "https://www.zomato.com/lublin/mandragora-lublin",
      "https://dir.indiamart.com/search.mp?ss=iphone&prdsrc=1&mcatid=23429&catid=213",
      "https://www.flipkart.com/search?q=iphone");

  private static final int PAGE_LOAD_TIMEOUT_SEC = 40;
  private static final int TEST_ITERATIONS = 10;
  private static final int MAX_FAILURES_PER_URL = TEST_ITERATIONS / 3;
  private static final int TEST_TIMEOUT_S = PAGE_LOAD_TIMEOUT_SEC
      * TEST_ITERATIONS * TESTING_URLS.size() * 2;

  @Rule
  public final Timeout globalTimeout = Timeout.seconds(TEST_TIMEOUT_S);

  @Rule
  public final ActivityTestRule<WebViewActivity> activityRule
      = new ActivityTestRule<>(WebViewActivity.class, false, true);

  static final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  static final Context context = instrumentation.getTargetContext();

  static final WebViewTestSuit<AdblockWebView> adblockViewTestSuit = new WebViewTestSuit<>();
  static final WebViewTestSuit<WebView> webViewTestSuit = new WebViewTestSuit<>();
  CountDownLatch countDownLatch;

  final LoadTimeAggregator adblockWebViewResultsOnPageFinished = new LoadTimeAggregator();
  final LoadTimeAggregator adblockWebViewResultsJsOnLoad = new LoadTimeAggregator();
  final LoadTimeAggregator webViewResultsOnPageFinished = new LoadTimeAggregator();
  final LoadTimeAggregator webViewResultsJsOnLoad = new LoadTimeAggregator();
  Map<String, Integer> urlFailures = new HashMap<>();


  static class LoadTimeAggregator
  {
    final Map<String, Vector<Long>> UrlsToTimes = new HashMap<>();

    LoadTimeAggregator()
    {
      for (final String url : TESTING_URLS)
      {
        UrlsToTimes.put(url, new Vector<Long>());
      }
    }

    public void putUrlResult(final String url, final long loadTime)
    {
      assertNotNull(url);
      final Vector<Long> resultsJsVector = UrlsToTimes.get(url);
      assertNotNull(resultsJsVector);
      resultsJsVector.add(loadTime);
    }

    public void reportData(final String testType, Map<String, Integer> urlFailures)
    {
      for (final String url : TESTING_URLS)
      {
        if (urlFailures.get(url) > MAX_FAILURES_PER_URL)
        {
          Timber.e("Page load failed repeatedly, skipping: %s, %d times of %d",
              url, urlFailures.get(url), TEST_ITERATIONS);
          continue;
        }

        final String[] domainParts;
        try
        {
          domainParts = Utils.getDomain(url).split("\\.");
        }
        catch (final URISyntaxException e)
        {
          continue;
        }
        assertTrue(domainParts.length > 1);
        final String secondLevelDomain = domainParts[domainParts.length - 2];
        assertNotNull(secondLevelDomain);

        final List<Long> results = UrlsToTimes.get(url);
        assertNotNull(results);

        long urlTotalTime = 0;
        for (long result : results)
        {
          urlTotalTime += result;
        }

        Timber.d("%s: %s for %s", getClass().getName(), results.toString(), url);

        BenchmarkState.reportData(this.getClass().getName(), secondLevelDomain + testType,
            urlTotalTime,
            results,
            0, 0, 1);
      }
    }
  }

  private static class WebViewWaitingClient extends WebViewClient
  {
    private static final String JS_OBJECT_NAME = "PageLoadJsBridge";
    private WebView webView;
    private String lastPageStartedUrl = "";
    private long loadTimeOnPageFinishedLoading = 0;
    private long loadTimeJsOnLoad = 0;
    private AtomicReference<Long> startTime = new AtomicReference<>(null);
    private CountDownLatch countDownLatch;

    public WebViewWaitingClient(final WebView webView)
    {
      super();
      replaceWebView(webView);
    }

    public void replaceWebView(final WebView webView)
    {
      this.webView = webView;
      instrumentation.runOnMainSync(new Runnable()
      {
        @Override
        public void run()
        {
          setupJsCallbacks();
        }
      });
    }

    public void loadUrl(final CountDownLatch latch, String url)
    {
      lastPageStartedUrl = "";
      loadTimeOnPageFinishedLoading = 0;
      loadTimeJsOnLoad = 0;
      startTime = new AtomicReference<>(null);
      countDownLatch = latch;
      webView.loadUrl(url);
    }

    public long getLoadTimeOnPageFinishedLoading()
    {
      return loadTimeOnPageFinishedLoading;
    }

    public long getLoadTimeJsOnLoad()
    {
      return loadTimeJsOnLoad;
    }

    private void setupJsCallbacks()
    {
      Timber.d("UsingJs: PageLoadJsBridge");
      webView.removeJavascriptInterface(JS_OBJECT_NAME);
      webView.addJavascriptInterface(this, JS_OBJECT_NAME);
      webView.setWebViewClient(this);
    }

    @Override
    public void onPageStarted(final WebView view, final String url, final Bitmap favicon)
    {
      lastPageStartedUrl = url;
      if (startTime.get() == null)
      {
        startTime.set(System.nanoTime());
      }
      Timber.d("UsingJs: onPageStarted called for url %s", url);
      view.evaluateJavascript(
          "window.addEventListener('load', function() { "
              + JS_OBJECT_NAME
              + ".onPageFinishedLoading(window.location.href); }); ", null);
    }

    @JavascriptInterface
    public void onPageFinishedLoading(String url) // It's referenced from JS, see above
    {
      final Long startTimeValue = startTime.get();
      assertTrue(Utils.getUrlWithoutParams(url).
          startsWith(Utils.getUrlWithoutParams((lastPageStartedUrl))));
      // When redirection happens there are several notifications so wee need to check if url matches
      if (startTimeValue == null || !Utils.getUrlWithoutParams(url).
          startsWith(Utils.getUrlWithoutParams((lastPageStartedUrl))))
      {
        return;
      }
      final long timeDelta = System.nanoTime() - startTimeValue;
      Timber.d("UsingJs: onPageFinishedLoading called for urls %s after %d ms (%s)", url,
          timeDelta / 1000000, lastPageStartedUrl);
      if (timeDelta > 0)
      {
        loadTimeJsOnLoad = timeDelta;
      }
      countDownLatch.countDown();
    }

    @Override
    public void onPageFinished(final WebView view, final String url)
    {
      final Long startTimeValue = startTime.get();
      // When redirection happens there are several notifications so wee need to check if url matches
      if (startTimeValue == null || !Utils.getUrlWithoutParams(url).
          startsWith(Utils.getUrlWithoutParams((lastPageStartedUrl))))
      {
        return;
      }
      final long timeDelta = System.nanoTime() - startTimeValue;
      Timber.d("UsingJs: onPageFinished called for urls %s after %d ms (%s)", url,
          timeDelta / 1000000, lastPageStartedUrl);
      if (timeDelta > 0)
      {
        loadTimeOnPageFinishedLoading = timeDelta;
      }
      countDownLatch.countDown();
    }
  }


  @BeforeClass
  public static void setUpClass()
  {
    if (Timber.treeCount() == 0)
    {
      Timber.plant(new Timber.DebugTree());
    }
    if (!AdblockHelper.get().isInit())
    {
      final String basePath = context.getDir(UUID.randomUUID().toString(),
          Context.MODE_PRIVATE).getAbsolutePath();
      AdblockHelper
          .get()
          .init(context, basePath, AdblockHelper.PREFERENCE_NAME)
          .getProvider().retain(true);
    }
  }

  @Before
  public void SetUp()
  {
    recreateWebView(adblockViewTestSuit);
    recreateWebView(webViewTestSuit);
  }

  void clearErrors()
  {
    urlFailures = new HashMap<>();
    for (final String url : TESTING_URLS)
    {
      urlFailures.put(url, 0);
    }
  }

  void recreateWebView(final WebViewTestSuit<?> testSuit)
  {
    instrumentation.runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        if (testSuit.webView instanceof AdblockWebView)
        {
          ((WebViewTestSuit<AdblockWebView>) testSuit)
              .webView = new AdblockWebView(context);
        }
        else
        {
          ((WebViewTestSuit<WebView>) testSuit)
              .webView = new WebView(context);
        }
      }
    });
    testSuit.setUp();
  }

  void handleUrlFailure(final WebViewTestSuit<?> testSuit,
                        final WebViewWaitingClient webViewClient, final String url)
  {
    assertTrue(urlFailures.containsKey(url));
    urlFailures.put(url, urlFailures.get(url) + 1);
    Timber.d("Recreating WebView, URL failed: " + url);
    recreateWebView(testSuit);
    instrumentation.runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        webViewClient.setupJsCallbacks();
      }
    });
  }

  private void commonTestLogic(final WebViewTestSuit<?> testSuit,
                               final LoadTimeAggregator resultsOnPageFinished,
                               final LoadTimeAggregator resultsJsOnLoad)
      throws InterruptedException
  {
    final WebViewWaitingClient webViewClient = new WebViewWaitingClient(testSuit.webView);

    testSuit.clearWebViewsState();

    instrumentation.runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        webViewClient.setupJsCallbacks();
        activityRule.getActivity().setContentView(testSuit.webView);
      }
    });

    for (final String url : TESTING_URLS)
    {
      Timber.d("UsingJs: testLoadTime() loads %s", url);
      countDownLatch = new CountDownLatch(2);

      instrumentation.runOnMainSync(new Runnable()
      {
        @Override
        public void run()
        {
          webViewClient.loadUrl(countDownLatch, url);
        }
      });

      if (!countDownLatch.await(PAGE_LOAD_TIMEOUT_SEC, TimeUnit.SECONDS))
      {
        handleUrlFailure(testSuit, webViewClient, url);
        continue;
      }
      resultsOnPageFinished.putUrlResult(url, webViewClient.getLoadTimeOnPageFinishedLoading());
      resultsJsOnLoad.putUrlResult(url, webViewClient.getLoadTimeJsOnLoad());
    }
  }

  @Test
  @LargeTest
  public void testLoadTime() throws InterruptedException
  {
    clearErrors();
    int repetitionCount = 0;
    while (repetitionCount++ < TEST_ITERATIONS)
    {
      Timber.d("UsingJs: running WebView iteration " + repetitionCount);
      commonTestLogic(webViewTestSuit,
          webViewResultsOnPageFinished, webViewResultsJsOnLoad);

      Timber.d("UsingJs: running AdblockWebView iteration " + repetitionCount);
      commonTestLogic(adblockViewTestSuit,
          adblockWebViewResultsOnPageFinished, adblockWebViewResultsJsOnLoad);
    }

    webViewResultsJsOnLoad.reportData("WebViewJS", urlFailures);
    webViewResultsOnPageFinished.reportData("WebViewNative", urlFailures);
    adblockWebViewResultsJsOnLoad.reportData("AdblockWebViewJS", urlFailures);
    adblockWebViewResultsOnPageFinished.reportData("AdblockWebViewNative", urlFailures);
  }
}
