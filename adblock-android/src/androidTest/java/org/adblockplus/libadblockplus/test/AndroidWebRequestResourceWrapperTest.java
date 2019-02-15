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

import android.os.SystemClock;

import org.adblockplus.libadblockplus.MockWebRequest;
import org.adblockplus.libadblockplus.android.AndroidWebRequestResourceWrapper;
import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AndroidWebRequestResourceWrapperTest extends BaseJsEngineTest
{
  protected final static int UPDATE_SUBSCRIPTIONS_WAIT_DELAY_MS = 5 * 1000;
  protected final static int UPDATE_SUBSCRIPTIONS_WAIT_CHUNKS = 50;

  private static final class TestStorage implements AndroidWebRequestResourceWrapper.Storage
  {
    private Set<String> interceptedUrls = new HashSet<String>();

    public Set<String> getInterceptedUrls()
    {
      return interceptedUrls;
    }

    @Override
    public synchronized void put(String url)
    {
      interceptedUrls.add(url);
    }

    @Override
    public synchronized boolean contains(String url)
    {
      return interceptedUrls.contains(url);
    }
  }

  private static final class TestWrapperListener implements AndroidWebRequestResourceWrapper.Listener
  {
    private Map<String, Integer> urlsToResourceId = new HashMap<String, Integer>();

    public Map<String, Integer> getUrlsToResourceId()
    {
      return urlsToResourceId;
    }

    @Override
    public void onIntercepted(String url, int resourceId)
    {
      urlsToResourceId.put(url, resourceId);
    }
  }

  private MockWebRequest request;
  private Map<String, Integer> preloadMap;
  private TestStorage storage;
  private AndroidWebRequestResourceWrapper wrapper;
  private TestWrapperListener wrapperListener;

  @Override
  public void setUp()
  {
    request = new MockWebRequest();
    request.exception = true;
    preloadMap = new HashMap<String, Integer>();
    storage = new TestStorage();
    wrapper = new AndroidWebRequestResourceWrapper(
        ApplicationProvider.getApplicationContext(), request, preloadMap, storage);
    wrapperListener = new TestWrapperListener();
    wrapper.setListener(wrapperListener);

    setUpWebRequest(wrapper);
    super.setUp();
  }

  private List<String> getUrlsListWithoutParams(Collection<String> urlWithParams)
  {
    final List<String> list = new LinkedList<String>();
    for (String eachUrl : urlWithParams)
    {
      list.add(Utils.getUrlWithoutParams(eachUrl));
    }
    return list;
  }

  private void reset()
  {
    preloadMap.clear();
    request.urls.clear();
    storage.getInterceptedUrls().clear();
    wrapperListener.getUrlsToResourceId().clear();
  }

  protected int getUpdateRequestCount()
  {
    return request.urls.size() + storage.getInterceptedUrls().size();
  }

  protected String getUrlRequestJs(String url)
  {
    return "_webRequest.GET('" + url + "', {}, function(result) { } )";
  }

  protected void updateSubscriptions()
  {
    updateSubscriptions(new String[]
      {
        AndroidWebRequestResourceWrapper.EASYLIST,
        AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS,
      },
      true);
  }

  protected void updateSubscriptions(String[] urls, boolean wait)
  {
    for (final String url : urls)
    {
      jsEngine.evaluate(getUrlRequestJs(url));
    }

    final int init = getUpdateRequestCount();

    if (wait)
    {
      for (int i = 0; i < UPDATE_SUBSCRIPTIONS_WAIT_CHUNKS; i++)
      {
        if (getUpdateRequestCount() - init >= urls.length)
        {
          break;
        }
        SystemClock.sleep(UPDATE_SUBSCRIPTIONS_WAIT_DELAY_MS / UPDATE_SUBSCRIPTIONS_WAIT_CHUNKS);
      }
    }
  }

  private void testIntercepted(final String preloadUrl, final int resourceId)
  {
    reset();
    preloadMap.put(preloadUrl, resourceId);
    assertEquals(0, request.urls.size());
    assertEquals(0, storage.getInterceptedUrls().size());
    assertEquals(0, wrapperListener.getUrlsToResourceId().size());

    updateSubscriptions();

    if (request.urls.size() > 0)
    {
      final List<String> requestsWithoutParams = getUrlsListWithoutParams(request.urls);
      assertFalse(requestsWithoutParams.contains(preloadUrl));
    }

    assertEquals(1, storage.getInterceptedUrls().size());
    assertTrue(storage.getInterceptedUrls().contains(preloadUrl));

    assertTrue(wrapperListener.getUrlsToResourceId().size() >= 0);
    List<String> notifiedInterceptedUrls = getUrlsListWithoutParams(
        wrapperListener.getUrlsToResourceId().keySet());
    assertTrue(notifiedInterceptedUrls.contains(preloadUrl));
    for (final String eachString : wrapperListener.getUrlsToResourceId().keySet())
    {
      if (Utils.getUrlWithoutParams(eachString).equals(preloadUrl))
      {
        assertEquals(resourceId, wrapperListener.getUrlsToResourceId().get(eachString).intValue());
        break;
      }
    }
  }

  @Test
  public void testIntercepted_Easylist()
  {
    testIntercepted(AndroidWebRequestResourceWrapper.EASYLIST, R.raw.easylist);
  }

  @Test
  public void testIntercepted_AcceptableAds()
  {
    testIntercepted(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS, R.raw.exceptionrules);
  }

  @Test
  public void testIntercepted_OnceOnly()
  {
    reset();
    final String preloadUrl = AndroidWebRequestResourceWrapper.EASYLIST;

    preloadMap.put(preloadUrl, R.raw.easylist);
    assertEquals(0, request.urls.size());
    assertEquals(0, storage.getInterceptedUrls().size());
    assertEquals(0, wrapperListener.getUrlsToResourceId().size());

    // update #1 -  should be intercepted
    updateSubscriptions(new String[]
        {
          AndroidWebRequestResourceWrapper.EASYLIST
        },
        true);

    assertEquals(0, request.urls.size());
    assertEquals(1, storage.getInterceptedUrls().size());
    assertTrue(storage.getInterceptedUrls().contains(preloadUrl));

    assertTrue(wrapperListener.getUrlsToResourceId().size() >= 0);
    final List<String> notifiedInterceptedUrls = getUrlsListWithoutParams(
        wrapperListener.getUrlsToResourceId().keySet());
    assertTrue(notifiedInterceptedUrls.contains(preloadUrl));

    // update #2 -  should NOT be intercepted but actually requested from the web
    wrapperListener.getUrlsToResourceId().clear();

    updateSubscriptions();

    assertTrue(request.urls.size() > 0);
    final List<String> requestsWithoutParams = getUrlsListWithoutParams(request.urls);
    assertTrue(requestsWithoutParams.contains(preloadUrl));
    assertEquals(0, wrapperListener.getUrlsToResourceId().size());
  }

  private void testNotIntercepted(final String interceptedUrl, final int resourceId,
                                  final String notInterceptedUrl)
  {
    reset();
    preloadMap.put(interceptedUrl, resourceId);
    assertEquals(0, request.urls.size());
    assertEquals(0, storage.getInterceptedUrls().size());
    assertEquals(0, wrapperListener.getUrlsToResourceId().size());

    updateSubscriptions();

    assertEquals(1, request.urls.size());
    List<String> requestUrlsWithoutParams = getUrlsListWithoutParams(request.urls);
    assertFalse(requestUrlsWithoutParams.contains(interceptedUrl));
    assertTrue(requestUrlsWithoutParams.contains(notInterceptedUrl));
    assertEquals(1, storage.getInterceptedUrls().size());
    assertTrue(storage.getInterceptedUrls().contains(interceptedUrl));
    assertFalse(storage.getInterceptedUrls().contains(notInterceptedUrl));
    assertTrue(wrapperListener.getUrlsToResourceId().size() > 0);

    for (final String eachString : wrapperListener.getUrlsToResourceId().keySet())
    {
      if (Utils.getUrlWithoutParams(eachString).equals(notInterceptedUrl))
      {
        fail();
      }
    }
  }

  @Test
  public void testInterceptedAll()
  {
    reset();
    preloadMap.put(AndroidWebRequestResourceWrapper.EASYLIST, R.raw.easylist);
    preloadMap.put(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS, R.raw.exceptionrules);

    assertEquals(0, request.urls.size());
    assertEquals(0, storage.getInterceptedUrls().size());
    assertEquals(0, wrapperListener.getUrlsToResourceId().size());

    updateSubscriptions();

    assertEquals(0, request.urls.size());
    assertEquals(2, storage.getInterceptedUrls().size());
    assertTrue(storage.getInterceptedUrls().contains(AndroidWebRequestResourceWrapper.EASYLIST));
    assertTrue(storage.getInterceptedUrls().contains(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS));
    assertTrue(wrapperListener.getUrlsToResourceId().size() >= 0);
    final List<String> notifiedInterceptedUrls = getUrlsListWithoutParams(
        wrapperListener.getUrlsToResourceId().keySet());
    assertTrue(notifiedInterceptedUrls.contains(AndroidWebRequestResourceWrapper.EASYLIST));
    assertTrue(notifiedInterceptedUrls.contains(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS));

    for (final String eachString : wrapperListener.getUrlsToResourceId().keySet())
    {
      final String urlWithoutParams = Utils.getUrlWithoutParams(eachString);
      if (urlWithoutParams.equals(AndroidWebRequestResourceWrapper.EASYLIST))
      {
        assertEquals(
            R.raw.easylist,
            wrapperListener.getUrlsToResourceId().get(eachString).intValue());
      }

      if (urlWithoutParams.equals(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS))
      {
        assertEquals(
            R.raw.exceptionrules,
            wrapperListener.getUrlsToResourceId().get(eachString).intValue());
      }
    }
  }

  @Test
  public void testNotIntercepted_Easylist()
  {
    testNotIntercepted(
        AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS,R.raw.exceptionrules,
        AndroidWebRequestResourceWrapper.EASYLIST);
  }

  @Test
  public void testNotIntercepted_AcceptableAds()
  {
    testNotIntercepted(
        AndroidWebRequestResourceWrapper.EASYLIST, R.raw.easylist,
        AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS);
  }
}
