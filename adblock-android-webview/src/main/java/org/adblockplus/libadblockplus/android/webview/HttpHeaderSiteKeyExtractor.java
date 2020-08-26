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
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.android.webview.AdblockWebView.WebResponseResult;
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import timber.log.Timber;

import static org.adblockplus.libadblockplus.HttpClient.STATUS_CODE_OK;
import static org.adblockplus.libadblockplus.android.Utils.convertHeaderEntriesToMap;
import static org.adblockplus.libadblockplus.android.Utils.convertMapToHeaderEntries;

/**
 * Makes a custom HTTP request and then does the <i>Site Key</i> verification by calling
 * {@link org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier#verifyInHeaders(String, Map, Map)}
 */
class HttpHeaderSiteKeyExtractor extends BaseSiteKeyExtractor
{
  HttpHeaderSiteKeyExtractor(final AdblockWebView webView)
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

    Timber.d("fetchUrlAndCheckSiteKey() called from Thread %s",
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

      // DP-1277: For a top level navigation url we need to set this meta header
      // See:
      // - https://www.w3.org/TR/fetch-metadata/#sec-fetch-dest-header
      // - https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-Fetch-Mode
      if (request.isForMainFrame())
      {
        // For convenience use requestHeadersMap
        // instead of headersList to find if header is already set
        if (!requestHeadersMap.containsKey(HttpClient.HEADER_SEC_FETCH_MODE))
        {
          final String headerValue = "navigate";
          Timber.d("Adding %s (%s) request header for url %s",
              HttpClient.HEADER_SEC_FETCH_MODE,
              headerValue, url);
          headersList.add(new HeaderEntry(HttpClient.HEADER_SEC_FETCH_MODE, headerValue));
        }
      }

      final HttpRequest httpRequest = new HttpRequest(
          url,
          requestMethod,
          headersList,
          !request.isForMainFrame(),
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
    if (!HttpClient.isStatusAllowed(statusCode))
    {
      // looks like the response is just broken
      // let it go
      return WebResponseResult.ALLOW_LOAD;
    }

    final List<HeaderEntry> responseHeaders = response.getResponseHeaders();
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
      }
    }

    // since we'd like to make this extractor work
    // as a main extractor as well as a secondary
    // we are leaving actions that are happening only for main frame
    if (HttpClient.isRedirectCode(statusCode))
    {
      if (request.isForMainFrame())
      {
        return reloadWebViewUrl(webView, url, responseHolder);
      }
      return WebResponseResult.ALLOW_LOAD;
    }

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

  private static WebResourceResponse reloadWebViewUrl(final AdblockWebView webView,
                                                      final String url,
                                                      final ResponseHolder responseHolder)
  {
    String redirectedUrl = null;
    for (final HeaderEntry header : responseHolder.response.getResponseHeaders())
    {
      if (header.getKey().equalsIgnoreCase(HttpClient.HEADER_LOCATION) &&
          header.getValue() != null && !header.getValue().isEmpty())
      {
        redirectedUrl = header.getValue();
        try
        {
          // check and handle relative url redirection
          if (!Utils.isAbsoluteUrl(redirectedUrl))
          {
            redirectedUrl = Utils.getAbsoluteUrl(url, redirectedUrl);
          }
        }
        catch (final Exception e)
        {
          Timber.e(e, "Failed to build absolute redirect URL");
          redirectedUrl = null;
        }
      }
    }

    if (redirectedUrl != null)
    {
      Timber.d("redirecting a webview from %s to %s", url, redirectedUrl);
      // we need to reload webview url to make it aware of new new url after redirection
      webView.setRedirectInProgress(true);
      final Map<String, String> responseHeaders = Collections.singletonMap(
          HttpClient.HEADER_REFRESH, "0; url=" + redirectedUrl);
      return new WebResourceResponse(WebResponseResult.RESPONSE_MIME_TYPE,
          WebResponseResult.RESPONSE_CHARSET_NAME,
          STATUS_CODE_OK,
          "OK",
          responseHeaders,
          new ByteArrayInputStream(new byte[]{}));
    }
    return WebResponseResult.ALLOW_LOAD;
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
