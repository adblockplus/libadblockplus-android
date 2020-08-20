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
import android.webkit.WebView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.adblockplus.libadblockplus.android.webview.WebViewTestSuit;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

import static org.junit.Assert.assertTrue;


@RunWith(AndroidJUnit4.class)
public class WebViewLoadPerformanceTest
{
  private static final Context context =
      InstrumentationRegistry.getInstrumentation().getTargetContext();

  private final WebViewTestSuit<AdblockWebView> adblockTestSuit = new WebViewTestSuit<>();
  private final WebViewTestSuit<WebView> systemTestSuit = new WebViewTestSuit();

  @Rule
  public final Timeout globalTimeout = Timeout.seconds(900);

  @BeforeClass
  public static void setUpClass()
  {
    if (Timber.treeCount() == 0)
    {
      Timber.plant(new Timber.DebugTree());
    }
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
    testSuit.setUp();
  }

  @LargeTest
  @Test
  @Ignore("The test lasts for too long to be executed as a part of build routine")
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
        adblockTestSuit.tearDown();
      }
    });
    Timber.d("Adblock: compareResults() final pages load time is %s ms", adblockFinalResult);
    Timber.d("System: compareResults() final pages load time is %s ms", systemFinalResult);
    // Acceptance criteria: AdblockWebView adds no more than 10% delay on top of a system WebView
    assertTrue(adblockFinalResult - systemFinalResult < (systemFinalResult / 10));
  }
}
