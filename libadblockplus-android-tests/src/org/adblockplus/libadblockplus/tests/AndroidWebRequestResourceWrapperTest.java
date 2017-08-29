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

package org.adblockplus.libadblockplus.tests;

import android.os.SystemClock;

import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.WebRequest;
import org.adblockplus.libadblockplus.android.AndroidWebRequest;
import org.adblockplus.libadblockplus.android.AndroidWebRequestResourceWrapper;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.tests.test.R;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidWebRequestResourceWrapperTest extends FilterEngineGenericTest
{
  private static final int UPDATE_SUBSCRIPTIONS_WAIT_DELAY_MS = 5 * 1000; // 5s

  private static final class TestRequest extends AndroidWebRequest
  {
    private List<String> urls = new LinkedList<String>();

    public List<String> getUrls()
    {
      return urls;
    }

    @Override
    public ServerResponse httpGET(String urlStr, List<HeaderEntry> headers)
    {
      urls.add(urlStr);
      return super.httpGET(urlStr, headers);
    }
  }

  // in-memory storage for testing only
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

  private TestRequest request;
  private Map<String, Integer> preloadMap;
  private TestStorage storage;
  private AndroidWebRequestResourceWrapper wrapper;
  private TestWrapperListener wrapperListener;

  @Override
  protected void setUp() throws Exception
  {

    request = new TestRequest();
    preloadMap = new HashMap<String, Integer>();
    storage = new TestStorage();
    wrapper = new AndroidWebRequestResourceWrapper(
      getInstrumentation().getContext(), request, preloadMap, storage);
    wrapperListener = new TestWrapperListener();
    wrapper.setListener(wrapperListener);

    super.setUp();
  }

  @Override
  protected WebRequest createWebRequest()
  {
    return wrapper;
  }

  private void updateSubscriptions()
  {
    for (final Subscription s : this.filterEngine.getListedSubscriptions())
    {
      try
      {
        s.updateFilters();
      }
      finally
      {
        s.dispose();
      }
    }
  }

  private List<String> getUrlsListWithoutParams(Collection<String> urlWithParams)
  {
    List<String> list = new LinkedList<String>();
    for (String eachUrl : urlWithParams)
    {
      list.add(Utils.getUrlWithoutParams(eachUrl));
    }
    return list;
  }

  private void testIntercepted(final String preloadUrl, final int resourceId)
  {
    preloadMap.clear();
    preloadMap.put(preloadUrl, resourceId);

    assertEquals(0, request.getUrls().size());

    assertEquals(0, storage.getInterceptedUrls().size());

    assertEquals(0, wrapperListener.getUrlsToResourceId().size());

    updateSubscriptions();
    SystemClock.sleep(UPDATE_SUBSCRIPTIONS_WAIT_DELAY_MS);

    if (request.getUrls().size() > 0)
    {
      List<String> requestsWithoutParams = getUrlsListWithoutParams(request.getUrls());
      assertFalse(requestsWithoutParams.contains(preloadUrl));
    }

    assertEquals(1, storage.getInterceptedUrls().size());
    assertTrue(storage.getInterceptedUrls().contains(preloadUrl));

    assertTrue(wrapperListener.getUrlsToResourceId().size() >= 0);
    List<String> notifiedInterceptedUrls = getUrlsListWithoutParams(
      wrapperListener.getUrlsToResourceId().keySet());
    assertTrue(notifiedInterceptedUrls.contains(preloadUrl));

    for (String eachString : wrapperListener.getUrlsToResourceId().keySet())
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
    testIntercepted(
      AndroidWebRequestResourceWrapper.EASYLIST, R.raw.easylist);
  }

  @Test
  public void testIntercepted_AcceptableAds()
  {
    testIntercepted(
      AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS, R.raw.exceptionrules);
  }

  @Test
  public void testIntercepted_OnceOnly()
  {
    final String preloadUrl = AndroidWebRequestResourceWrapper.EASYLIST;

    preloadMap.clear();
    preloadMap.put(preloadUrl, R.raw.easylist);

    assertEquals(0, request.getUrls().size());

    assertEquals(0, storage.getInterceptedUrls().size());

    assertEquals(0, wrapperListener.getUrlsToResourceId().size());

    // update #1 -  should be intercepted
    updateSubscriptions();
    SystemClock.sleep(UPDATE_SUBSCRIPTIONS_WAIT_DELAY_MS);

    int requestsCount = request.getUrls().size();
    if (requestsCount > 0)
    {
      List<String> requestsWithoutParams = getUrlsListWithoutParams(request.getUrls());
      assertFalse(requestsWithoutParams.contains(preloadUrl));
    }

    assertEquals(1, storage.getInterceptedUrls().size());
    assertTrue(storage.getInterceptedUrls().contains(preloadUrl));

    assertTrue(wrapperListener.getUrlsToResourceId().size() >= 0);
    List<String> notifiedInterceptedUrls = getUrlsListWithoutParams(
      wrapperListener.getUrlsToResourceId().keySet());
    assertTrue(notifiedInterceptedUrls.contains(preloadUrl));

    // update #2 -  should NOT be intercepted but actually requested from the web
    wrapperListener.getUrlsToResourceId().clear();

    updateSubscriptions();
    SystemClock.sleep(UPDATE_SUBSCRIPTIONS_WAIT_DELAY_MS);

    assertTrue(request.getUrls().size() > requestsCount);
    List<String> requestsWithoutParams = getUrlsListWithoutParams(request.getUrls());
    assertTrue(requestsWithoutParams.contains(preloadUrl));

    assertEquals(0, wrapperListener.getUrlsToResourceId().size());
  }

  private void testNotIntercepted(final String interceptedUrl, final int resourceId,
                                  final String notInterceptedUrl)
  {
    preloadMap.clear();
    preloadMap.put(interceptedUrl, resourceId);

    assertEquals(0, request.getUrls().size());
    assertEquals(0, storage.getInterceptedUrls().size());
    assertEquals(0, wrapperListener.getUrlsToResourceId().size());

    updateSubscriptions();
    SystemClock.sleep(UPDATE_SUBSCRIPTIONS_WAIT_DELAY_MS);

    assertEquals(1, request.getUrls().size());
    List<String> requestUrlsWithoutParams = getUrlsListWithoutParams(request.getUrls());
    assertFalse(requestUrlsWithoutParams.contains(interceptedUrl));
    assertTrue(requestUrlsWithoutParams.contains(notInterceptedUrl));
    assertEquals(1, storage.getInterceptedUrls().size());
    assertTrue(storage.getInterceptedUrls().contains(interceptedUrl));
    assertFalse(storage.getInterceptedUrls().contains(notInterceptedUrl));
    assertTrue(wrapperListener.getUrlsToResourceId().size() > 0);

    for (String eachString : wrapperListener.getUrlsToResourceId().keySet())
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
    preloadMap.clear();
    preloadMap.put(AndroidWebRequestResourceWrapper.EASYLIST, R.raw.easylist);
    preloadMap.put(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS, R.raw.exceptionrules);

    assertEquals(0, request.getUrls().size());

    assertEquals(0, storage.getInterceptedUrls().size());

    assertEquals(0, wrapperListener.getUrlsToResourceId().size());

    updateSubscriptions();
    SystemClock.sleep(UPDATE_SUBSCRIPTIONS_WAIT_DELAY_MS);

    assertEquals(0, request.getUrls().size());
    assertEquals(2, storage.getInterceptedUrls().size());
    assertTrue(storage.getInterceptedUrls().contains(AndroidWebRequestResourceWrapper.EASYLIST));
    assertTrue(storage.getInterceptedUrls().contains(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS));

    assertTrue(wrapperListener.getUrlsToResourceId().size() >= 0);
    List<String> notifiedInterceptedUrls = getUrlsListWithoutParams(
      wrapperListener.getUrlsToResourceId().keySet());
    assertTrue(notifiedInterceptedUrls.contains(AndroidWebRequestResourceWrapper.EASYLIST));
    assertTrue(notifiedInterceptedUrls.contains(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS));

    for (String eachString : wrapperListener.getUrlsToResourceId().keySet())
    {
      String urlWithoutParams = Utils.getUrlWithoutParams(eachString);
      if (urlWithoutParams.equals(AndroidWebRequestResourceWrapper.EASYLIST))
      {
        assertEquals(R.raw.easylist, wrapperListener.getUrlsToResourceId().get(eachString).intValue());
      }

      if (urlWithoutParams.equals(AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS))
      {
        assertEquals(R.raw.exceptionrules, wrapperListener.getUrlsToResourceId().get(eachString).intValue());
      }
    }
  }

  @Test
  public void testNotIntercepted_Easylist()
  {
    testNotIntercepted(
      AndroidWebRequestResourceWrapper.ACCEPTABLE_ADS, R.raw.exceptionrules,
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
