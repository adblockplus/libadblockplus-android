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

package org.adblockplus.libadblockplus.test;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.android.AndroidHttpClient;
import org.adblockplus.libadblockplus.android.AndroidHttpClientEngineStateWrapper;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidHttpClientEngineStateWrapperTest extends BaseFilterEngineTest
{
  private AndroidHttpClientEngineStateWrapper androidHttpClientEngineStateWrapper;
  private ThrowingAndroidHttpClient throwingAndroidHttpClient;

  private static class ThrowingAndroidHttpClient extends AndroidHttpClient
  {
    public CountDownLatch countDownLatch = null;
    public boolean doThrow = true;

    @Override
    public void request(final HttpRequest request, final Callback callback)
    {
      try
      {
        if (doThrow)
        {
          throw new AdblockPlusException("Simulated failure from AndroidHttpClient");
        }
        super.request(request, callback);
      }
      finally
      {
        if (countDownLatch != null)
        {
          countDownLatch.countDown();
        }
      }
    }
  }

  @Override
  public void setUp()
  {
    throwingAndroidHttpClient = new ThrowingAndroidHttpClient();
    androidHttpClientEngineStateWrapper = new AndroidHttpClientEngineStateWrapper(
        throwingAndroidHttpClient, null);
    setUpHttpClient(androidHttpClientEngineStateWrapper);
    super.setUp();
  }

  // Show that if AndroidHttpClient fails we don't propagate exception to Core but return http error
  @Test
  public void testHandlingException() throws InterruptedException
  {
    // Verify that FE after startup tries to download 2 default subscriptions (easylist and
    // exceptionrules) and even though http client throws, the exception is handled correctly
    // internally by a AndroidHttpClientEngineStateWrapper
    throwingAndroidHttpClient.countDownLatch = new CountDownLatch(2);
    throwingAndroidHttpClient.doThrow = true;
    try
    {
      assertTrue(throwingAndroidHttpClient.countDownLatch.await(10, TimeUnit.SECONDS));
    }
    catch (final AdblockPlusException e)
    {
      fail();
    }
    Thread.sleep(100); // we need to wait a bit to make sure error propagates
    confirmExpectedSubscriptionStatus("synchronize_connection_error");

    // Verify that FE after filter download failure can successfully retry to download subscriptions
    throwingAndroidHttpClient.countDownLatch = new CountDownLatch(2);
    throwingAndroidHttpClient.doThrow = false;

    forceSubscriptionsDownloads();
    assertTrue(throwingAndroidHttpClient.countDownLatch.await(10, TimeUnit.SECONDS));
    Thread.sleep(100); // we need to wait a bit to make sure error propagates
    confirmExpectedSubscriptionStatus("synchronize_ok");
  }

  private void forceSubscriptionsDownloads()
  {
    final List<Subscription> subscriptions = filterEngine.getListedSubscriptions();
    for (final Subscription s : subscriptions)
    {
      s.updateFilters();
    }
  }

  private void confirmExpectedSubscriptionStatus(final String expectedStatus)
  {
    final List<Subscription> subscriptions = filterEngine.getListedSubscriptions();
    for (final Subscription s : subscriptions)
    {
      assertTrue(s.getSynchronizationStatus().equals(expectedStatus));
    }
  }
}
