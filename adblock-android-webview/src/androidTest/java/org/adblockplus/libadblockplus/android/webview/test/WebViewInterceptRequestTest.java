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
import androidx.test.platform.app.InstrumentationRegistry;

import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.AndroidBase64Processor;
import org.adblockplus.libadblockplus.android.AndroidHttpClient;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView;
import org.adblockplus.libadblockplus.security.JavaSignatureVerifier;
import org.adblockplus.libadblockplus.security.SignatureVerifier;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolder;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl;
import org.adblockplus.libadblockplus.sitekey.SiteKeyException;
import org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier;
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;
import org.adblockplus.libadblockplus.util.Base64Processor;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class WebViewInterceptRequestTest
{
  private final static int MAX_PAGE_LOAD_WAIT_TIME_SEC = 20;
  // Test data with map entries: url => expected number of SiteKeyVerifier.verify() calls
  private final static Map<String, Integer> urls = new HashMap<String, Integer>()
  {{
    put("http://benefitssolver.com", 1);
    put("http://myhealthvet.com", 1);
    put("http://iflychina.com", 1);
    put("http://megaloft.com/", 1);
    put("http://ww38.nextlnk1.com/", 1);
    put("http://ww12.onoticioso.com/", 1);
    put("http://directions.com", 1);
    put("http://zins.de", 1);
  }};
  private static final Context context =
      InstrumentationRegistry.getInstrumentation().getTargetContext();

  private static final WebViewTestSuit<AdblockWebView> adblockTestSuit = new WebViewTestSuit<>();

  @Rule
  public final Timeout globalTimeout = Timeout.seconds(MAX_PAGE_LOAD_WAIT_TIME_SEC
      * (urls.size() + 1));

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

  private static class WebViewTestSuit<T extends WebView>
  {
    private final static int MAX_PAGE_LOAD_WAIT_TIME_SEC = 20;
    private T webView;

    private static class WebViewWaitingClient extends WebViewClient
    {
      private String lastPageStartedUrl = "";
      final private CountDownLatch countDownLatch;

      WebViewWaitingClient(final CountDownLatch countDownLatch)
      {
        super();
        this.countDownLatch = countDownLatch;
      }

      @Override
      public void onPageStarted(final WebView view, final String url, final Bitmap favicon)
      {
        Timber.d("onPageStarted called for url %s", url);
        lastPageStartedUrl = url;
      }

      @Override
      public void onPageFinished(final WebView view, final String url)
      {
        // When redirection happens there are several notifications
        // so we need to check if url matches
        if (Utils.getUrlWithoutParams(url).startsWith(
            Utils.getUrlWithoutParams(lastPageStartedUrl)))
        {
          countDownLatch.countDown();
        }
      }
    }

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

    private boolean loadUrlAndWait(final String url) throws InterruptedException
    {
      clearWebViewsState();
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
      return countDownLatch.await(MAX_PAGE_LOAD_WAIT_TIME_SEC, TimeUnit.SECONDS);
    }
  }

  public static class TestSiteKeyVerifier extends SiteKeyVerifier
  {
    private int verificationCount = 0;

    TestSiteKeyVerifier(final SignatureVerifier signatureVerifier,
                        final PublicKeyHolder publicKeyHolder,
                        final Base64Processor base64Processor)
    {
      super(signatureVerifier, publicKeyHolder, base64Processor);
    }

    @Override
    public boolean verify(final String url, final String userAgent, final String value)
        throws SiteKeyException
    {
      ++verificationCount;
      final boolean verifyResult = super.verify(url, userAgent, value);
      Timber.d("TestSiteKeyVerifier.verify(%s) == %b", url, value);
      return verifyResult;
    }

    int getVerificationCount()
    {
      return verificationCount;
    }
  }

  @Before
  public void setUp()
  {
    // Setup AdblockWebView
    InstrumentationRegistry.getInstrumentation().
        runOnMainSync(new Runnable()
        {
          @Override
          public void run()
          {
            adblockTestSuit.webView = new AdblockWebView(context);
            adblockTestSuit.webView.getSettings().setDomStorageEnabled(true);
          }
        });

    final AdblockEngineProvider adblockEngineProvider = AdblockHelper.get().getProvider();
    adblockTestSuit.webView.setProvider(adblockEngineProvider);
    adblockEngineProvider.waitForReady();

    // Setup site key configuration
    final SignatureVerifier signatureVerifier = new JavaSignatureVerifier();
    final PublicKeyHolder publicKeyHolder = new PublicKeyHolderImpl();
    final HttpClient httpClient = new AndroidHttpClient(true, "UTF-8");
    final Base64Processor base64Processor = new AndroidBase64Processor();
    final TestSiteKeyVerifier siteKeyVerifier =
        new TestSiteKeyVerifier(signatureVerifier, publicKeyHolder, base64Processor);
    adblockTestSuit.webView.setSiteKeysConfiguration(new SiteKeysConfiguration(
        signatureVerifier, publicKeyHolder, httpClient, siteKeyVerifier));
  }

  @Test
  public void testSiteKeyVerifierWithoutAcceptableAds() throws InterruptedException
  {
    final int countExpectedSuccess = 0;
    AdblockHelper.get().getProvider().getEngine().setAcceptableAdsEnabled(false);
    for (final Map.Entry<String, Integer> entry : urls.entrySet())
    {
      assertTrue("Url load failed unexpectedly",
          adblockTestSuit.loadUrlAndWait(entry.getKey()));
    }
    assertEquals(countExpectedSuccess,
        ((TestSiteKeyVerifier)
            adblockTestSuit.webView.getSiteKeysConfiguration().getSiteKeyVerifier()
        ).getVerificationCount());
  }

  @Test
  public void testSiteKeyVerifierWithAcceptableAds() throws InterruptedException
  {
    int countExpectedSuccess = 0;
    AdblockHelper.get().getProvider().getEngine().setAcceptableAdsEnabled(true);
    for (final Map.Entry<String, Integer> entry : urls.entrySet())
    {
      assertTrue("Url load failed unexpectedly",
          adblockTestSuit.loadUrlAndWait(entry.getKey()));
      countExpectedSuccess += entry.getValue();
    }
    assertEquals(countExpectedSuccess,
        ((TestSiteKeyVerifier)
            adblockTestSuit.webView.getSiteKeysConfiguration().getSiteKeyVerifier()
        ).getVerificationCount());
  }

  @After
  public void tearDown()
  {
    getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        adblockTestSuit.webView.dispose(null);
      }
    });
  }

  @AfterClass
  public static void tearDownClass()
  {
    AdblockHelper.get().getProvider().release();
  }
}
