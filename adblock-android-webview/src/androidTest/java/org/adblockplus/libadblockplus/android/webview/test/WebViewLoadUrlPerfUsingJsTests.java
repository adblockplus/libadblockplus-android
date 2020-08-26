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

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.adblockplus.libadblockplus.android.webview.WebViewActivity;
import org.adblockplus.libadblockplus.android.webview.WebViewTestSuit;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class WebViewLoadUrlPerfUsingJsTests
{
  private static final List<String> TESTING_URLS = Arrays.asList(
      "http://incident.net/v8/files/mp4/",
      "http://incident.net/v8/files/mp4/1.mp4",
      "https://ndtv.com",
      "https://zomato.com",
      "https://www.indiamart.com",
      "https://booking.com",
      "https://billdesk.com",
      "https://www.flipkart.com");

  private static final int PAGE_LOAD_TIMEOUT_SEC = 40;
  private static final int TEST_TIMEOUT = 2000;
  private static final int TEST_ITERATIONS = 5;
  // Strips first and last number of items from result list.
  private static final int FILTER_OUTLIERS_TO_REMOVE = 1;
  private static final int MAX_FAILURES_PER_URL = 2;
  private static final int MAX_FAILURES_TOTAL = TESTING_URLS.size();

  @Rule
  public final Timeout globalTimeout = Timeout.seconds(TEST_TIMEOUT);

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
    final Map<String, Vector<Long>> UrlsToResults = new HashMap<>();

    LoadTimeAggregator()
    {
      for (final String url : TESTING_URLS)
      {
        UrlsToResults.put(url, new Vector<Long>());
      }
    }

    public void putUrlResult(final String url, final long loadTime)
    {
      assertNotNull(url);
      final Vector<Long> resultsJsVector = UrlsToResults.get(url);
      assertNotNull(resultsJsVector);
      resultsJsVector.add(loadTime);
    }

    static long filterValues(final AbstractList<Long> list)
    {
      assertFalse(list.isEmpty());

      // Remove the extreme values in sorted array
      Collections.sort(list);
      final List<Long> subList = list.subList(FILTER_OUTLIERS_TO_REMOVE, list.size() - 1 - FILTER_OUTLIERS_TO_REMOVE);
      assertFalse(subList.isEmpty());

      // Average the rest of the entries
      long sum = 0;
      for (long time : subList)
      {
        sum += time;
      }
      return sum / subList.size();
    }

    public long getTotalTime()
    {
      long urlLoadTotalTime = 0;
      for (final String url : TESTING_URLS)
      {
        final Vector<Long> results = UrlsToResults.get(url);
        assertNotNull(results);
        final long urlLoadTime = filterValues(results);
        urlLoadTotalTime += urlLoadTime;
        Timber.d("%s: %s (%d) for %s", getClass().getName(), results.toString(), urlLoadTime, url);
      }
      return urlLoadTotalTime;
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
        startTime.set(System.currentTimeMillis());
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
      final long timeDelta = System.currentTimeMillis() - startTimeValue;
      Timber.d("UsingJs: onPageFinishedLoading called for urls %s after %d ms (%s)", url,
          timeDelta, lastPageStartedUrl);
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
      final long timeDelta = System.currentTimeMillis() - startTimeValue;
      Timber.d("UsingJs: onPageFinished called for urls %s after %d ms (%s)", url,
          timeDelta, lastPageStartedUrl);
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
          .init(context, basePath, true, AdblockHelper.PREFERENCE_NAME)
          .retain(true);
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
    assertTrue("Page load failed repeatedly and unexpectedly: " + url,
        urlFailures.get(url) <= MAX_FAILURES_PER_URL);

    long totalFailures = 0;
    for (long v : urlFailures.values())
    {
      totalFailures += v;
    }
    assertTrue("Too many load failures", totalFailures <= MAX_FAILURES_TOTAL);
  }

  /**
   * Notes for QA (mostly).
   * Before running the tests one needs to install the test package and main application Android
   * package files (two separate .apk files) to current Android device or emulator. Currently those are:
   * - ./adblock-android-webviewapp/build/outputs/apk/debug/adblock-android-webviewapp-debug.apk
   * - ./adblock-android-webviewapp/build/outputs/apk/androidTest/debug/adblock-android-webviewapp-debug-androidTest.apk
   * <p>
   * To run both test from CLI using ADB run:
   * adb shell am instrument -w -e \
   * class org.adblockplus.libadblockplus.android.webviewapp.test.WebViewEspressoTest \
   * org.adblockplus.libadblockplus.android.webviewapp.test/androidx.test.runner.AndroidJUnitRunner
   * <p>
   * Tu run specific test append #<testName>, f.e.:
   * adb shell am instrument -w -e \
   * *  class org.adblockplus.libadblockplus.android.webviewapp.test.WebViewEspressoTest#testLoadTimeInAdblockWebView \
   * *  org.adblockplus.libadblockplus.android.webviewapp.test/androidx.test.runner.AndroidJUnitRunner
   */

  @Ignore("waiting for nightly CI build")
  @Test
  public void testLoadTime() throws InterruptedException, IOException
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

    recordResults();
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

  private void recordResults() throws IOException
  {
    Timber.d("WebView Results:");
    Timber.d("Total time: onPageFinished - %d, JS onLoad - %d ",
        webViewResultsOnPageFinished.getTotalTime(), webViewResultsJsOnLoad.getTotalTime());
    Timber.d("AdblockWebView Results:");
    Timber.d("Total time: onPageFinished - %d, JS onLoad - %d ",
        adblockWebViewResultsOnPageFinished.getTotalTime(),
        adblockWebViewResultsJsOnLoad.getTotalTime());
    return;

// The code below is commented out, but should be enabled when switching the test on.

//    StringBuilder sb = new StringBuilder();
//
//    sb.append("AVERAGE_ONPAGEFINISHED_TIME_WV " +
//        webViewResultsOnPageFinished.getTotalTime() + "\n");
//    sb.append("AVERAGE_ONLOAD_TIME_WV " + webViewResultsJsOnLoad.getTotalTime() + "\n");
//    sb.append("AVERAGE_ONPAGEFINISHED_TIME_AWV " +
//        adblockWebViewResultsOnPageFinished.getTotalTime() + "\n");
//    sb.append("AVERAGE_ONLOAD_TIME_AWV " + adblockWebViewResultsJsOnLoad.getTotalTime() + "\n");
//
//    File file = new File("/storage/emulated/0/Download/pageload_benchmark.metrics");
//    if (!file.exists())
//    {
//      file.createNewFile();
//    }
//    assertTrue(file.exists());
//    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
//    writer.write(sb.toString());
//    writer.close();
  }
}
