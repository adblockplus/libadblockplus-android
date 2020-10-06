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

package org.adblockplus.libadblockplus.android.webview;

import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView.WebResponseResult;
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import timber.log.Timber;

import static org.adblockplus.libadblockplus.android.Utils.convertHeaderEntriesToMap;
import static org.adblockplus.libadblockplus.android.Utils.convertMapToHeaderEntries;

/**
 * Makes a custom HTTP request and then does the <i>Site Key</i> verification by calling
 * {@link org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier#verifyInHeaders(String, Map, Map)}
 */
public class HttpHeaderSiteKeyExtractor extends BaseSiteKeyExtractor
{
  public HttpHeaderSiteKeyExtractor(final AdblockWebView webView)
  {
    super(webView);
  }

  @Override
  public WebResourceResponse obtainAndCheckSiteKey(final AdblockWebView webView,
                                                   final WebResourceRequest request)
  {
    // if disabled (probably AA is disabled)
    // do nothing
    if (!isEnabled())
    {
      return WebResponseResult.ALLOW_LOAD;
    }

    final Map<String, String> requestHeadersMap = request.getRequestHeaders();
    final String requestMethod = request.getMethod();
    String url = request.getUrl().toString();

    final SiteKeysConfiguration configuration = getSiteKeysConfiguration();

    if (configuration == null ||
        !requestMethod.equalsIgnoreCase(HttpClient.REQUEST_METHOD_GET))
    {
      // for now we handle site key only for GET requests
      return WebResponseResult.ALLOW_LOAD;
    }

    Timber.d("obtainAndCheckSiteKey() called from Thread %s",
        Thread.currentThread().getId());
    final ResponseHolder responseHolder = new ResponseHolder();
    final CountDownLatch latch = new CountDownLatch(1);
    final HttpClient.Callback callback = new HttpClient.Callback()
    {
      @Override
      public void onFinished(final ServerResponse response_)
      {
        responseHolder.response = response_;
        latch.countDown();
      }
    };

    try
    {
      final List<HeaderEntry> headersList = convertMapToHeaderEntries(requestHeadersMap);
      final String cookieValue = CookieManager.getInstance().getCookie(url);
      if (cookieValue != null && !cookieValue.isEmpty())
      {
        Timber.d("Adding %s request header for url %s", HttpClient.HEADER_COOKIE, url);
        headersList.add(new HeaderEntry(HttpClient.HEADER_COOKIE, cookieValue));
      }

      final HttpRequest httpRequest = new HttpRequest(
          url,
          requestMethod,
          headersList,
          true,         // always true since we don't use it for main frame
          true);
      configuration.getHttpClient().request(httpRequest, callback);
    }
    catch (final AdblockPlusException e)
    {
      Timber.e(e, "WebRequest failed");
      // allow WebView to continue, repeating the request and handling the response
      return WebResponseResult.ALLOW_LOAD;
    }

    try
    {
      latch.await();
    }
    catch (final InterruptedException e)
    {
      // error waiting for the response, continue by returning null
      return WebResponseResult.ALLOW_LOAD;
    }

    final ServerResponse response = responseHolder.response;
    final ServerResponse.NsStatus status = response.getStatus();
    final int statusCode = response.getResponseStatus();

    // in some circumstances statusCode gets > 599
    // also checking redirect should not happen but
    // jic it would not crash
    if (!HttpClient.isStatusAllowed(statusCode) ||
         HttpClient.isRedirectCode(statusCode))
    {
      // looks like the response is just broken
      // let it go
      return WebResponseResult.ALLOW_LOAD;
    }

    final List<HeaderEntry> responseHeaders = response.getResponseHeaders();
    final List<HeaderEntry> cookieHeadersToRemove = new ArrayList<>();
    for (final HeaderEntry eachEntry : responseHeaders)
    {
      if (HttpClient.HEADER_SET_COOKIE.equalsIgnoreCase(eachEntry.getKey()))
      {
        if (webView.canAcceptCookie(url, eachEntry.getValue()))
        {
          Timber.d("Calling setCookie(%s)", url);
          CookieManager.getInstance().setCookie(url, eachEntry.getValue());
        }
        else
        {
          Timber.d("Rejecting setCookie(%s)", url);
        }
        cookieHeadersToRemove.add(eachEntry);
      }
    }
    // DP-971: We don't need to pass HEADER_SET_COOKIE data further
    responseHeaders.removeAll(cookieHeadersToRemove);

    if (response.getFinalUrl() != null)
    {
      Timber.d("Updating url to %s, was (%s)", response.getFinalUrl(), url);
      url = response.getFinalUrl();
    }

    final Map<String, String> responseHeadersMap = convertHeaderEntriesToMap(responseHeaders);

    configuration.getSiteKeyVerifier().verifyInHeaders(url,
        requestHeadersMap,
        responseHeadersMap);

    if (response.getInputStream() != null)
    {
      final String responseContentType = responseHeadersMap.get(HttpClient.HEADER_CONTENT_TYPE);
      String responseMimeType = null;
      String responseEncoding = null;
      if (responseContentType != null)
      {
        final int semicolonPos = responseContentType.indexOf(";");
        if (semicolonPos > 0)
        {
          responseMimeType = responseContentType.substring(0, semicolonPos);
          final String charsetKey = "charset=";
          final int charsetPos = responseContentType.indexOf(charsetKey);
          if ((charsetPos >= 0)
              && (charsetPos < responseContentType.length() - charsetKey.length()))
          {
            responseEncoding = responseContentType.substring(charsetPos + charsetKey.length());
          }
        }
        else if (responseContentType.indexOf("/") > 0)
        {
          responseMimeType = responseContentType;
        }
      }

      if (responseMimeType != null)
      {
        Timber.d("Removing %s to avoid Content-Type duplication",
            HttpClient.HEADER_CONTENT_TYPE);
        responseHeadersMap.remove(HttpClient.HEADER_CONTENT_TYPE);

      /*
        Quoting https://developer.android.com/reference/android/webkit/WebResourceResponse:
        Do not use the value of a HTTP Content-Encoding header for encoding, as that header does not
        specify a character encoding. Content without a defined character encoding
        (for example image resources) should pass null for encoding.
        TODO: Include here other contentTypes also, not only "image".
       */
        if ((responseEncoding != null) && responseMimeType.startsWith("image"))
        {
          Timber.d("Setting responseEncoding to null for contentType == %s (url == %s)",
              responseMimeType, url);
          responseEncoding = null;
        }

      }
      else if (responseHeadersMap.get(HttpClient.HEADER_CONTENT_LENGTH) != null)
      {
        // For some reason for responses which lack Content-Type header and has Content-Length==0,
        // underlying WebView layer can trigger a DownloadListener. Applying "default" Content-Type
        // value helps. To reduce risk we apply it only when Content-Length==0 as there is no body
        // so there is no risk that browser will
        // render that even when we apply a wrong Content-Type.
        int contentLength = 0;
        try
        {
          // we are catching NPE so disabling lint
          //noinspection ConstantConditions
          contentLength = Integer.parseInt(
              responseHeadersMap.get(HttpClient.HEADER_CONTENT_LENGTH).trim()
          );
        }
        catch (final NumberFormatException | NullPointerException e)
        {
          Timber.e(e, "Integer.parseInt(responseHeadersMap.get(HEADER_CONTENT_LENGTH)) failed");
        }

        if (contentLength == 0)
        {
          Timber.d("Setting responseMimeType to %s (url == %s)",
              WebResponseResult.RESPONSE_MIME_TYPE, url);
          responseMimeType = WebResponseResult.RESPONSE_MIME_TYPE;
        }
      }

      if (responseMimeType != null)
      {
        responseMimeType = responseMimeType.trim();
      }
      if (responseEncoding != null)
      {
        responseEncoding = responseEncoding.trim();
      }

      Timber.d("Using responseMimeType and responseEncoding: %s => %s (url == %s)",
          responseMimeType, responseEncoding, url);
      return new WebResourceResponse(
          responseMimeType, responseEncoding,
          statusCode, getReasonPhrase(status),
          responseHeadersMap, response.getInputStream());
    }
    Timber.w("fetchUrlAndCheckSiteKey() passes control to WebView");
    return WebResponseResult.ALLOW_LOAD;
  }

  @Override
  public void startNewPage()
  {
    // no-op
  }

  @Override
  public boolean waitForSitekeyCheck(final WebResourceRequest request)
  {
    // no need to block the network request for this extractor
    // this callback is used in JsSiteKeyExtractor
    return false;
  }

  private static String getReasonPhrase(final ServerResponse.NsStatus status)
  {
    return status.name().replace("_", "");
  }

  private static class ResponseHolder
  {
    ServerResponse response;
  }
}
