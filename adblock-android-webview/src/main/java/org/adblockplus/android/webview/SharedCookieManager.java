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

package org.adblockplus.android.webview;

import org.adblockplus.HeaderEntry;
import org.adblockplus.HttpClient;
import org.adblockplus.android.Utils;

import java.net.CookieHandler;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

/**
 * SharedCookieManager overrides java.net.CookieManager to allow sharing cookies between
 * java.net.CookieManager and android.webkit.CookieManager cookie jars.
 * It is accomplished by storing and reading cookie data in android.webkit.CookieManager storage.
 */
class SharedCookieManager extends java.net.CookieManager
{
  static final String PROP_ACCEPT_TPC = "X-Prop-Accept-Tpc";
  static final String PROP_NAVIGATION_URL = "X-Prop-Navigation-Url";

  private static final AtomicReference<CookieHandler> previousCookieManager = new AtomicReference();

  @Override
  public void put(final URI uri, final Map<String, List<String>> responseHeaders)
  {
    /*
     * For non redirection responses cookies data will be passed to WebView and ultimately will be
     * stored internally in android.webkit.CookieManager (HttpHeaderSiteKeyExtractor no longer
     * removes cookie headers so they are passed to the underlying WebView).
     *
     * Only 3xx redirection responses (so also cookie data) from HttpURLConnection are not visible
     * outside and we need to handle that here.
     */
    if (!responseHeaders.containsKey(HttpClient.HEADER_LOCATION) ||
        !android.webkit.CookieManager.getInstance().acceptCookie())
    {
      return;
    }
    for (final Map.Entry<String, List<String>> entry : responseHeaders.entrySet())
    {
      if (HttpClient.HEADER_SET_COOKIE.equalsIgnoreCase(entry.getKey()))
      {
        for (final String cookie : entry.getValue())
        {
          // Set any cookie now, we filter out 3rd party cookies before we sent them back.
          // Here we have no context data to filter out 3rd party cookies.
          android.webkit.CookieManager.getInstance().setCookie(
              Utils.getUrlWithoutFragment(uri.toString()), cookie);
        }
        // Flush to make sure data is persisted in case app is killed
        android.webkit.CookieManager.getInstance().flush();
        break;
      }
    }
  }

  @Override
  public Map<String, List<String>> get(final URI uri,
                                       final Map<String, List<String>> requestHeaders)
  {
    if (uri == null || requestHeaders == null)
    {
      throw new IllegalArgumentException("Arguments can't be null!");
    }

    if (!android.webkit.CookieManager.getInstance().acceptCookie())
    {
      return requestHeaders;
    }

    final String url = uri.toString();
    final String cookie = android.webkit.CookieManager.getInstance().getCookie(
        Utils.getUrlWithoutFragment(url));

    // Make a copy as `requestHeaders` is not modifiable
    final Map<String, List<String>> res = new HashMap<>(requestHeaders);
    // Cleanup our fake headers used to pass context data
    final List<String> acceptThirdPartyCookieHolder = res.remove(PROP_ACCEPT_TPC);
    final List<String> navigationUrlHolder = res.remove(PROP_NAVIGATION_URL);
    if (cookie != null)
    {
      final boolean acceptThirdPartyCookie = !Utils.isNullOrEmpty(acceptThirdPartyCookieHolder) ?
          Boolean.parseBoolean(acceptThirdPartyCookieHolder.get(0)): false;
      final String navigationUrl = !Utils.isNullOrEmpty(navigationUrlHolder)  ?
          navigationUrlHolder.get(0) : uri.toString();
      final List<String> currentCookies = res.get(HttpClient.HEADER_COOKIE);
      if (acceptThirdPartyCookie || Utils.isFirstPartyCookie(navigationUrl, uri.toString(), cookie))
      {
        if (currentCookies == null)
        {
          res.put(HttpClient.HEADER_COOKIE, Arrays.asList(cookie));
        }
        else
        {
          currentCookies.add(cookie);
        }
      }
    }
    return res;
  }

  @Override
  public void setCookiePolicy(final CookiePolicy cookiePolicy)
  {
    throw new UnsupportedOperationException(
        "SharedCookieManager uses settings of android.webkit.CookieManager!");
  }

  @Override
  public CookieStore getCookieStore()
  {
    throw new UnsupportedOperationException(
        "SharedCookieManager stores cookies in android.webkit.CookieManager storage!");
  }

  private SharedCookieManager()
  {
    super();
  }

  // Those headers will be removed later on by SharedCookieManager.get() before sending the request
  static void injectPropertyHeaders(final boolean acceptThirdPartyCookie, final String navigationUrl,
                                    final List<HeaderEntry> requestHeadersList)
  {
    requestHeadersList.add(
        new HeaderEntry(SharedCookieManager.PROP_ACCEPT_TPC, String.valueOf(acceptThirdPartyCookie)));
    requestHeadersList.add(
        new HeaderEntry(SharedCookieManager.PROP_NAVIGATION_URL, navigationUrl));
  }

  static void unloadCookieManager()
  {
    final CookieHandler currentCookieHandler = CookieHandler.getDefault();
    if (currentCookieHandler instanceof SharedCookieManager)
    {
      CookieHandler.setDefault(previousCookieManager.get());
    }
  }

  static void enforceCookieManager()
  {
    final CookieHandler currentCookieHandler = CookieHandler.getDefault();
    if (!(currentCookieHandler instanceof SharedCookieManager))
    {
      previousCookieManager.set(currentCookieHandler);
      CookieHandler.setDefault(new SharedCookieManager());
      if (currentCookieHandler == null)
      {
        Timber.d("SharedCookieManager set as a default java.net.CookieManager");
      }
      else
      {
        Timber.w("SharedCookieManager overwrites existing java.net.CookieManager");
      }
    }
  }
}
