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

import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.AndroidBase64Processor;
import org.adblockplus.libadblockplus.android.AndroidHttpClient;
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
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import timber.log.Timber;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class WebViewSiteKeyTest
{
  private static final Context context =
      InstrumentationRegistry.getInstrumentation().getTargetContext();
  private AdblockWebView adblockWebView = null;

  @Rule
  public final Timeout globalTimeout = Timeout.seconds(120);

  @BeforeClass
  public static void setUpClass()
  {
    Timber.plant(new Timber.DebugTree());
    if (!AdblockHelper.get().isInit())
    {
      final String basePath = context.getDir(AdblockEngine.BASE_PATH_DIRECTORY, Context.MODE_PRIVATE).getAbsolutePath();
      AdblockHelper
          .get()
          .init(context, basePath, true, AdblockHelper.PREFERENCE_NAME);
    }
  }

  public static class TestSiteKeyVerifier extends SiteKeyVerifier
  {
    public int countSuccess = 0;
    public int countFailure = 0;
    public CountDownLatch countDownLatch = null;

    public TestSiteKeyVerifier(final SignatureVerifier signatureVerifier,
                               final PublicKeyHolder publicKeyHolder,
                               final Base64Processor base64Processor)
    {
      super(signatureVerifier, publicKeyHolder, base64Processor);
    }

    @Override
    public boolean verify(final String url, final String userAgent, final String value)
        throws SiteKeyException
    {
      final boolean verifyResult = super.verify(url, userAgent, value);
      if (verifyResult)
      {
        ++countSuccess;
      }
      else
      {
        ++countFailure;
      }
      Timber.d("TestSiteKeyVerifier.verify(%s) == %b (%b)", url, value,
          countDownLatch != null);
      if (countDownLatch != null)
      {
        countDownLatch.countDown();
      }
      return verifyResult;
    }
  }

  @Test
  public void testSiteKeyVerifierCount() throws InterruptedException
  {
    // Test data with map entries: url => expected number of SiteKeyVerifier.verify() calls
    final Map<String, Integer> urls = new HashMap<String, Integer>()
    {{
      put("http://cook.com", 1);
      put("http://benefitssolver.com", 1);
      put("http://myhealthvet.com", 1);
      put("http://iflychina.com", 1);
      put("http://recipes.com", 1);
      put("http://builders.com", 1);
      put("http://cards.com", 1);
    }};

    // Setup adblockWebView
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        adblockWebView = new AdblockWebView(context);
        adblockWebView.getSettings().setDomStorageEnabled(true);
      }
    });

    final AdblockEngineProvider adblockEngineProvider = AdblockHelper.get().getProvider();
    adblockWebView.setProvider(adblockEngineProvider);
    adblockEngineProvider.waitForReady();

    // Setup site key configuration
    final SignatureVerifier signatureVerifier = new JavaSignatureVerifier();
    final PublicKeyHolder publicKeyHolder = new PublicKeyHolderImpl();
    final HttpClient httpClient = new AndroidHttpClient(true, "UTF-8");
    final Base64Processor base64Processor = new AndroidBase64Processor();
    final TestSiteKeyVerifier siteKeyVerifier =
        new TestSiteKeyVerifier(signatureVerifier, publicKeyHolder, base64Processor);
    adblockWebView.setSiteKeysConfiguration(new SiteKeysConfiguration(
        signatureVerifier, publicKeyHolder, httpClient, siteKeyVerifier));

    int countExpectedSuccess = 0;
    for (final Map.Entry<String, Integer> entry : urls.entrySet())
    {
      countExpectedSuccess += entry.getValue();
      siteKeyVerifier.countDownLatch = new CountDownLatch(entry.getValue());
      InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
      {
        @Override
        public void run()
        {
          adblockWebView.loadUrl(entry.getKey());
        }
      });
      siteKeyVerifier.countDownLatch.await();
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable()
    {
      @Override
      public void run()
      {
        adblockWebView.dispose(null);
      }
    });
    assertEquals(0, siteKeyVerifier.countFailure);
    assertEquals(countExpectedSuccess, siteKeyVerifier.countSuccess);
  }
}
