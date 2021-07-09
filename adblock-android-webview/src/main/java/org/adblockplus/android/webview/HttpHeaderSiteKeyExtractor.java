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

import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.adblockplus.AdblockPlusException;
import org.adblockplus.HeaderEntry;
import org.adblockplus.HttpClient;
import org.adblockplus.HttpRequest;
import org.adblockplus.ServerResponse;
import org.adblockplus.android.Utils;
import org.adblockplus.android.webview.AdblockWebView.WebResponseResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

import static org.adblockplus.android.Utils.convertHeaderEntriesToMap;
import static org.adblockplus.android.Utils.convertMapToHeadersList;

/**
 * Makes a custom HTTP request and then does the <i>Site Key</i> verification by calling
 * {@link org.adblockplus.sitekey.SiteKeyVerifier#verifyInHeaders(String, Map, Map)}
 */
public class HttpHeaderSiteKeyExtractor extends BaseSiteKeyExtractor
{
  private final AtomicBoolean acceptThirdPartyCookies = new AtomicBoolean(false);

  private static String getReasonPhrase(final ServerResponse.NsStatus status)
  {
    return status.name().replace("_", "");
  }

  private static class ResponseHolder
  {
    ServerResponse response;
  }

  public static class ResourceInfo
  {
    private static final String CHARSET = "charset=";
    private static final int CHARSET_LENGTH = CHARSET.length();

    // this is very limited list
    private static final String[] BINARY_MIMES = new String[]
    {
      "image",
      "application/octet-stream",
      "video",
      "font",
      "audio"
    };

    private String mimeType;
    private String encoding;
    private boolean isBinary = false;

    public String getMimeType()
    {
      return mimeType;
    }

    public String getEncoding()
    {
      return encoding;
    }

    public void setMimeType(final String mimeType)
    {
      this.mimeType = mimeType;
    }

    public void setEncoding(final String encoding)
    {
      this.encoding = encoding;
    }

    /**
     * Tells if header's mime type is binary
     * This detection is far from being precise, it makes a guess based on first part of MIME
     * (image, video, audio etc)
     *
     * It does not take into account all the variety of `application/..` types
     *
     * @link https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Common_types
     * This is preparsed during `parse` phase
     * @return true if mime type is binary
     */
    public boolean isBinary()
    {
      return isBinary;
    }

    // if `contentType` is null the fields will be `null` too
    public static ResourceInfo parse(final String contentType)
    {
      final ResourceInfo resourceInfo = new ResourceInfo();

      if (contentType != null)
      {
        final int semicolonPos = contentType.indexOf(";");
        if (semicolonPos > 0)
        {
          resourceInfo.mimeType = contentType.substring(0, semicolonPos);
          final int charsetPos = contentType.indexOf(CHARSET);
          if ((charsetPos >= 0) &&
              (charsetPos < contentType.length() - CHARSET_LENGTH))
          {
            resourceInfo.encoding = contentType.substring(charsetPos + CHARSET_LENGTH);
          }
        }
        else if (contentType.indexOf("/") > 0)
        {
          resourceInfo.mimeType = contentType;
        }

        if (resourceInfo.mimeType != null)
        {
          // we have to loop over the array in order to call startsWith on each
          for (final String binaryMime : BINARY_MIMES)
          {
            if (resourceInfo.mimeType.startsWith(binaryMime))
            {
              resourceInfo.isBinary = true;
              break;
            }
          }
        }
      }

      return resourceInfo;
    }

    private void trim()
    {
      if (mimeType != null)
      {
        mimeType = mimeType.trim();
      }

      if (encoding != null)
      {
        encoding = encoding.trim();
      }
    }
  }

  public static class ServerResponseProcessor
  {
    private static final String NONCE = "nonce-";
    private static final String CSP_SCRIPT_SRC_PARAM = "script-src";
    private static final String CSP_UNSAFE_INLINE = "'unsafe-inline'";
    private static final Pattern NONCE_PATTERN =
        Pattern.compile(String.format("%s[^;]*'(%s[^']+)'.*;", CSP_SCRIPT_SRC_PARAM, NONCE),
            Pattern.CASE_INSENSITIVE);
    private static final String BODY_CLOSE_TAG = "</body>";

    private boolean containsValidUnsafeInline(final String cspHeaderValue)
    {
      final int scriptSrcIndex = cspHeaderValue.indexOf(CSP_SCRIPT_SRC_PARAM);
      if (scriptSrcIndex < 0)
      {
        return false;
      }
      final int unsafeInlineIndex = cspHeaderValue.indexOf(CSP_UNSAFE_INLINE, scriptSrcIndex);
      if (unsafeInlineIndex < 0)
      {
        return false;
      }
      final String inBetween = cspHeaderValue.substring(
          scriptSrcIndex + CSP_SCRIPT_SRC_PARAM.length(), unsafeInlineIndex);
      // Make sure that CSP_UNSAFE_INLINE we found belongs to CSP_SCRIPT_SRC_PARAM
      return !(inBetween.contains("-src ") || inBetween.contains("-src-elem ") ||
          inBetween.contains("-src-attr ") || inBetween.contains("navigate-to ") ||
          inBetween.contains("form-action ") || inBetween.contains("base-uri "));
    }

    protected String updateCspHeader(final Map<String, String> responseHeaders)
    {
      String JS_NONCE = null;
      for (Map.Entry<String, String> eachEntry : responseHeaders.entrySet())
      {
        // We want to just execute our custom inject.js script by slightly relaxing CSP if needed
        // If a nonce for script-src is present we will reuse it, otherwise it will be added
        if (eachEntry.getKey().toLowerCase().equals(HttpClient.HEADER_CSP) &&
            !eachEntry.getValue().isEmpty())
        {
          Timber.d("Found `%s` CSP header", eachEntry.getValue());
          if (eachEntry.getValue().toLowerCase().contains(CSP_SCRIPT_SRC_PARAM))
          {
            final Matcher resultREGEX = NONCE_PATTERN.matcher(eachEntry.getValue());
            if (resultREGEX.find() && resultREGEX.groupCount() == 1)
            {
              JS_NONCE = resultREGEX.group(1);
              Timber.d("Found nonce in CSP header with value `%s`", JS_NONCE);
            }
            else
            {
              if (containsValidUnsafeInline(eachEntry.getValue().toLowerCase()))
              {
                Timber.d("Found `%s` in CSP header, no need for update", CSP_UNSAFE_INLINE);
                return null;
              }
              JS_NONCE = NONCE + UUID.randomUUID().toString();
              final String[] splittedCSP = eachEntry.getValue().split(CSP_SCRIPT_SRC_PARAM, 2);
              final String newCSPvalue = splittedCSP[0].trim() + " " + CSP_SCRIPT_SRC_PARAM + " '" +
                  JS_NONCE + "' " + splittedCSP[1].trim();
              responseHeaders.put(eachEntry.getKey(), newCSPvalue);
              Timber.d("Added nonce to CSP header, new value `%s`", newCSPvalue);
            }
          }
          break;
        }
      }
      return JS_NONCE != null ? JS_NONCE.substring(NONCE.length()) : JS_NONCE;
    }

    protected String readFileToString(final InputStream inputStream)
    {
      final Scanner scanner = new Scanner(inputStream, WebResponseResult.RESPONSE_CHARSET_NAME)
          .useDelimiter("\\A");
      return scanner.hasNext() ? scanner.next() : "";
    }

    // Return true on success or when no-op, false on error
    protected boolean injectJavascript(final AdblockWebView webView, final String requestUrl,
                                       final ServerResponse response,
                                       final Map<String, String> responseHeaders)
    {
      Timber.d("injectJavascript() reads content of `%s`", requestUrl);

      if (response.getInputStream() == null)
      {
        return true;
      }
      final byte[] rawBytes;
      String htmlString;
      try
      {
        rawBytes = Utils.toByteArray(response.getInputStream());
        htmlString = new String(rawBytes);
      }
      catch (final IOException e)
      {
        Timber.e(e, "injectJavascript() failed reading input stream to byte array");
        return false;
      }

      // When generateStylesheetForUrl() fails to generate css then we can skip js injection
      if (htmlString.toLowerCase().contains("</body>") &&
          webView.generateStylesheetForUrl(Utils.getUrlWithoutFragment(requestUrl), false))
      {
        if (BuildConfig.DEBUG)
        {
          if (htmlString.toLowerCase().contains("content-security-policy"))
          {
            Timber.w("injectJavascript() found potential CSP meta tag directive for `%s`",
                requestUrl);
          }
        }
        final String bodyEndWithScriptTag;
        // For now we don't check CSP in meta tags in HTML, just in headers
        final String JS_NONCE = updateCspHeader(responseHeaders);
        if (JS_NONCE == null)
        {
          bodyEndWithScriptTag = "<script>" + webView.getInjectJs() + "</script></body>";
        }
        else
        {
          bodyEndWithScriptTag = "<script nonce=\"" + JS_NONCE + "\">" + webView.getInjectJs()
              + "</script></body>";
        }
        Timber.d("injectJavascript() adds injectJs for `%s`", requestUrl);
        // Find and replace last occurrence of BODY_CLOSE_TAG
        final int foundIndex = htmlString.lastIndexOf(BODY_CLOSE_TAG);
        if (foundIndex > 0)
        {
          final StringBuilder builder = new StringBuilder();
          builder.append(htmlString.substring(0, foundIndex));
          builder.append(bodyEndWithScriptTag);
          builder.append(htmlString.substring(foundIndex + BODY_CLOSE_TAG.length()));
          htmlString = builder.toString();
        }
        try
        {
          // Now set up back response input stream
          response.setInputStream(
              new ByteArrayInputStream(htmlString.getBytes(WebResponseResult.RESPONSE_CHARSET_NAME)));
        }
        catch (final UnsupportedEncodingException e)
        {
          Timber.e(e, "injectJavascript() failed");
          return false;
        }
      }
      else
      {
        Timber.d("injectJavascript() skips injectJs for `%s`", requestUrl);
        response.setInputStream(new ByteArrayInputStream(rawBytes));
      }

      return true;
    }

    public WebResourceResponse process(final AdblockWebView webView,
                                       final String requestUrl,
                                       final ServerResponse response,
                                       final Map<String, String> responseHeaders)
    {
      final String responseContentType = responseHeaders.get(HttpClient.HEADER_CONTENT_TYPE);
      final ResourceInfo responseInfo = ResourceInfo.parse(responseContentType);

      if (responseInfo.getMimeType() != null)
      {
        Timber.d("Removing %s to avoid Content-Type duplication",
            HttpClient.HEADER_CONTENT_TYPE);
        // Cookies were already stored by SharedCookieManager in a storage used by
        // the android.webkit.CookieManager, we can strip them.
        responseHeaders.remove(HttpClient.HEADER_CONTENT_TYPE);

      /*
        Quoting https://developer.android.com/reference/android/webkit/WebResourceResponse:
        Do not use the value of a HTTP Content-Encoding header for encoding, as that header does not
        specify a character encoding. Content without a defined character encoding
        (for example image resources) should pass null for encoding.
       */
        if (responseInfo.getEncoding() != null && responseInfo.isBinary())
        {
          Timber.d("Setting responseEncoding to null for contentType == %s",
              responseInfo.getMimeType());
          responseInfo.setEncoding(null);
        }
      }
      else if (responseHeaders.get(HttpClient.HEADER_CONTENT_LENGTH) != null)
      {
        // For some reason for responses which lack Content-Type header and has Content-Length==0,
        // underlying WebView layer can trigger a DownloadListener. Applying "default" Content-Type
        // value helps. To reduce risk we apply it only when Content-Length==0 as there is no body
        // so there is no risk that browser will
        // render that even when we apply a wrong Content-Type.
        Integer contentLength = null;
        try
        {
          // we are catching NPE so disabling lint
          contentLength = Integer.parseInt(
              responseHeaders.get(HttpClient.HEADER_CONTENT_LENGTH).trim()
          );
        }
        catch (final NumberFormatException | NullPointerException e)
        {
          Timber.e(e, "Integer.parseInt(responseHeadersMap.get(HEADER_CONTENT_LENGTH)) failed");
        }

        if (contentLength == null)
        {
          Timber.d("Setting responseMimeType to %s",
              AdblockWebView.WebResponseResult.RESPONSE_MIME_TYPE);
          responseInfo.setMimeType(AdblockWebView.WebResponseResult.RESPONSE_MIME_TYPE);
        }
      }

      responseInfo.trim();
      Timber.d("Using responseMimeType and responseEncoding: %s => %s (url == %s)",
          responseInfo.getMimeType() != null ? responseInfo.getMimeType() : "null",
          responseInfo.getEncoding(), requestUrl);

      // Check if feature is enabled and inspect mimeType if not null to avoid calling
      // injectJavascript() when not necessary
      if (!webView.getJsInIframesEnabled() ||
          (responseInfo.getMimeType() != null &&
              !responseInfo.getMimeType().toLowerCase().contains(HttpClient.MIME_TYPE_TEXT_HTML)) ||
          injectJavascript(webView, requestUrl, response, responseHeaders))
      {
        return new WebResourceResponse(
            responseInfo.getMimeType(), responseInfo.getEncoding(),
            response.getResponseStatus(), getReasonPhrase(response.getStatus()),
            responseHeaders, response.getInputStream());
      }
      else
      {
        Timber.w("Processing ServerResponse failed, request for `%s` will be repeated!",
            requestUrl);
        return WebResponseResult.ALLOW_LOAD;
      }
    }
  }

  public HttpHeaderSiteKeyExtractor(final AdblockWebView webView)
  {
    super(webView);
  }

  @Override
  public WebResourceResponse extract(final WebResourceRequest request)
  {
    // if disabled (probably AA is disabled) do nothing
    if (!isEnabled())
    {
      return WebResponseResult.ALLOW_LOAD;
    }

    if (getSiteKeysConfiguration() == null ||
        !request.getMethod().equalsIgnoreCase(HttpClient.REQUEST_METHOD_GET))
    {
      // for now we handle site key only for GET requests
      return WebResponseResult.ALLOW_LOAD;
    }

    Timber.d("extract() called from Thread %s",
        Thread.currentThread().getId());

    final ServerResponse response;
    try
    {
      response = sendRequest(request);
    }
    catch (final AdblockPlusException e)
    {
      Timber.e(e, "WebRequest failed");
      // allow WebView to continue, repeating the request and handling the response
      return WebResponseResult.ALLOW_LOAD;
    }
    catch (final InterruptedException e)
    {
      // error waiting for the response, continue by returning null
      return WebResponseResult.ALLOW_LOAD;
    }

    // in some circumstances statusCode gets > 599
    // also checking redirect should not happen but
    // jic it would not crash
    if (!HttpClient.isValidCode(response.getResponseStatus()) ||
        HttpClient.isRedirectCode(response.getResponseStatus()))
    {
      // looks like the response is just broken, let it go
      return WebResponseResult.ALLOW_LOAD;
    }

    String url = request.getUrl().toString();
    if (response.getFinalUrl() != null)
    {
      Timber.d("Updating url to %s, was (%s)", response.getFinalUrl(), url);
      url = response.getFinalUrl();
    }

    if (response.getInputStream() == null)
    {
      Timber.w("extract() passes control to WebView");
      return WebResponseResult.ALLOW_LOAD;
    }

    final Map<String, String> requestHeaders = request.getRequestHeaders();
    final Map<String, String> responseHeaders =
        convertHeaderEntriesToMap(response.getResponseHeaders());

    // extract the sitekey from HTTP response header
    getSiteKeysConfiguration().getSiteKeyVerifier().verifyInHeaders(
        url, requestHeaders, responseHeaders);

    final AdblockWebView adblockWebView = webViewWeakReference.get();
    if (adblockWebView == null)
    {
      Timber.w("extract() couldn't get a handle to AdblockWebView, returning ALLOW_LOAD");
      return WebResponseResult.ALLOW_LOAD;
    }
    return new ServerResponseProcessor().process(adblockWebView, url, response, responseHeaders);
  }

  private ServerResponse sendRequest(final WebResourceRequest request) throws InterruptedException
  {
    final String requestUrl = request.getUrl().toString();
    final Map<String, String> requestHeadersMap = request.getRequestHeaders();

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

    final List<HeaderEntry> requestHeadersList = convertMapToHeadersList(requestHeadersMap);
    final AdblockWebView adblockWebView = webViewWeakReference.get();
    if (adblockWebView != null)
    {
      // Add fake headers to pass context data for HttpURLConnection, it will be removed later on
      // by SharedCookieManager.get().
      SharedCookieManager.injectPropertyHeaders(
          acceptThirdPartyCookies.get(), adblockWebView.getNavigationUrl(), requestHeadersList);
    }

    final HttpRequest httpRequest = new HttpRequest(
        requestUrl,
        request.getMethod(),
        requestHeadersList,
        true, // always true since we don't use it for main frame
        true);
    getSiteKeysConfiguration().getHttpClient().request(httpRequest, callback);

    latch.await();

    return responseHolder.response;
  }

  @Override
  public void setEnabled(final boolean enabled)
  {
    super.setEnabled(enabled);
    if (!enabled)
    {
      SharedCookieManager.unloadCookieManager();
    }
  }

  @Override
  public void startNewPage()
  {
    if (isEnabled())
    {
      final AdblockWebView adblockWebView = webViewWeakReference.get();
      if (adblockWebView != null)
      {
        // This needs to be called from UI thread!
        acceptThirdPartyCookies.set(CookieManager.getInstance().acceptThirdPartyCookies(adblockWebView));
      }
      SharedCookieManager.enforceCookieManager();
    }
  }

  @Override
  public boolean waitForSitekeyCheck(final String url, final boolean isMainFrame)
  {
    // no need to block the network request for this extractor
    // this callback is used in JsSiteKeyExtractor
    return false;
  }
}