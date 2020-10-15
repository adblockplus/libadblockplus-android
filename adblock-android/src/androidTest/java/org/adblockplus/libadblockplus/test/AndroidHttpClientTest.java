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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Notifier;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.android.AndroidHttpClient;
import org.adblockplus.libadblockplus.android.ConnectionInputStream;
import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;
import wiremock.org.apache.http.HttpStatus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// The test requires active internet connection and actually downloads files from the internet
// except local test done with use of wiremock
public class AndroidHttpClientTest extends BaseFilterEngineTest
{
  private static final Charset charset = StandardCharsets.UTF_8;
  private ServerResponse serverResponse = null;

  private static class AndroidHttpClientWithoutSubscriptions extends AndroidHttpClient
  {
    AndroidHttpClientWithoutSubscriptions(final boolean compressedStream)
    {
      super(compressedStream);
    }

    @Override
    public void request(final HttpRequest request, final Callback callback)
    {
      final String url = request.getUrl();
      if (url.contains("?addonName=libadblockplus-android"))
      {
        Timber.d("Connection refused for %s", url);
        final ServerResponse response = new ServerResponse();
        response.setResponseStatus(0);
        response.setStatus(ServerResponse.NsStatus.ERROR_CONNECTION_REFUSED);
        callback.onFinished(response);
        return;
      }
      super.request(request, callback);
    }
  }

  private final HttpClient androidHttpClient = new AndroidHttpClientWithoutSubscriptions(true);

  @Override
  public void setUp()
  {
    setUpHttpClient(androidHttpClient);
    super.setUp();
  }

  private static class ResponseHolder
  {
    ServerResponse response;
  }

  private ServerResponse makeHttpRequest(final String url, final boolean followRedirect)
  {
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
      final List<HeaderEntry> headersList = new ArrayList<>();
      final HttpRequest request = new HttpRequest(url, HttpClient.REQUEST_METHOD_GET, headersList, followRedirect, true);
      androidHttpClient.request(request, callback);
    }
    catch (final AdblockPlusException e)
    {
      Timber.e(e, "WebRequest failed");
    }

    try
    {
      latch.await();
    }
    catch (final InterruptedException e)
    {
      Timber.e(e, "WebRequest failed");
    }

    return responseHolder.response;
  }

  @Test
  public void testAdblockWebViewHttpRequest()
  {
    final ServerResponse response = makeHttpRequest("https://easylist-downloads.adblockplus.org/exceptionrules.txt", true);
    assertNotNull(response);

    assertEquals(HTTP_OK, response.getResponseStatus());
    assertNotNull(response.getInputStream());

    final ConnectionInputStream connectionInputStream = (ConnectionInputStream)(response.getInputStream());

    final Scanner scanner = new Scanner(connectionInputStream).useDelimiter("\\A");
    final String result = scanner.hasNext() ? scanner.next() : "";
    final String matchingString = "[Adblock Plus 2.0]";
    assertEquals(matchingString, result.substring(0, matchingString.length()));
    try
    {
      connectionInputStream.close();
    }
    catch (final IOException e)
    {
      Timber.e(e, "inputStream.close() failed");
    }
  }

  @Test
  public void testFollowingRedirectedHttpRequest()
  {
    // https://d.android.com is a redirect site to https://developer.android.com/
    final ServerResponse response = makeHttpRequest("https://d.android.com", true);
    assertNotNull(response);

    assertEquals(HTTP_OK, response.getResponseStatus());
    assertEquals("https://developer.android.com/", response.getFinalUrl());
    assertNotNull(response.getInputStream());
  }

  @Test
  public void testFollowingRedirectedHttpRequestLocal() throws IOException
  {
    final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().notifier(
        new Notifier()
        {
          @Override
          public void info(final String message)
          {
            Timber.i(message);
          }

          @Override
          public void error(final String message)
          {
            Timber.e(message);
          }

          @Override
          public void error(final String message, final Throwable t)
          {
            Timber.e(t, message);
          }
        }
    ));

    try
    {
      wireMockServer.start();
      final String initialResource = "/index.html";
      final String initialUrl = wireMockServer.baseUrl() + initialResource;
      final String redirectedResource = "/redirected.html";
      final String redirectedUrl = wireMockServer.baseUrl() + redirectedResource;
      final String redirectedContent = "Hello, World";

      wireMockServer
          .stubFor(any(urlPathEqualTo(initialResource))
          .willReturn(aResponse()
            .withStatus(HttpStatus.SC_TEMPORARY_REDIRECT)
            .withHeader(HttpClient.HEADER_LOCATION, redirectedResource)));

      wireMockServer
          .stubFor(any(urlPathEqualTo(redirectedResource))
              .willReturn(aResponse()
                  .withStatus(HttpStatus.SC_OK)
                  .withHeader(HttpClient.HEADER_CONTENT_TYPE, "text/plain")
                  .withBody(redirectedContent)));

      final ServerResponse response = makeHttpRequest(initialUrl, true);

      assertNotNull(response);
      assertEquals(redirectedUrl, response.getFinalUrl());
      final String actualContent = new String(Utils.toByteArray(response.getInputStream()));
      assertEquals(redirectedContent, actualContent);
    }
    finally
    {
      wireMockServer.stop();
    }
  }

  @Test
  public void testNotFollowingRedirectedHttpRequest()
  {
    // https://d.android.com is a redirect site to https://developer.android.com/
    final ServerResponse response = makeHttpRequest("https://d.android.com", false);
    assertNotNull(response);

    assertEquals(HTTP_MOVED_PERM, response.getResponseStatus());
    assertNotNull(response.getInputStream());
  }

  @Test
  public void testSuccessfulRealHttpClientRequest()
  {
    jsEngine.evaluate(
        "let foo; _webRequest.GET('https://easylist-downloads.adblockplus.org/easylist.txt', {}, " +
        "function(result) {foo = result;} )");

    waitForDefined("foo");

    final String response = jsEngine.evaluate("foo.responseText").asString();
    assertNotNull(response);
    assertEquals(
        ServerResponse.NsStatus.OK.getStatusCode(),
        jsEngine.evaluate("foo.status").asLong());
    assertEquals(HTTP_OK, jsEngine.evaluate("foo.responseStatus").asLong());
    assertEquals(
        "[Adblock Plus ",
        jsEngine.evaluate("foo.responseText.substr(0, 14)").asString());
    final JsValue jsHeaders = jsEngine.evaluate("foo.responseHeaders");
    assertNotNull(jsHeaders);
    assertFalse(jsHeaders.isUndefined());
    assertFalse(jsHeaders.isNull());
    assertTrue(jsHeaders.isObject());
    assertEquals(
        "text/plain",
        jsEngine.evaluate("foo.responseHeaders['content-type'].substr(0, 10)").asString());
    assertTrue(jsEngine.evaluate("foo.responseHeaders['location']").isUndefined());
  }

  @Test
  public void testSuccessfulRealHttpClientRequestWithJavaCallback() throws InterruptedException
  {
    serverResponse = null;
    final CountDownLatch latch = new CountDownLatch(1);
    final HttpClient.Callback javaCallback = new HttpClient.Callback() // java callback
    {
      @Override
      public void onFinished(final ServerResponse response)
      {
        serverResponse = response;
        latch.countDown();
      }
    };
    androidHttpClient.request(
        new HttpRequest("https://easylist-downloads.adblockplus.org/easylist.txt"),
        javaCallback);
    latch.await(SLEEP_MAX_TIME_MS, TimeUnit.MILLISECONDS);

    assertNotNull(serverResponse);
    assertEquals(
        ServerResponse.NsStatus.OK,
        serverResponse.getStatus());
    assertEquals(HTTP_OK, serverResponse.getResponseStatus());
    assertEquals(
        "[Adblock Plus ",
        new String(Utils.byteBufferToByteArray(serverResponse.getResponse()), charset).substring(0, 14));
  }

  @Test
  public void testRealHttpClientRequestErrorWithJavaCallback() throws InterruptedException
  {
    serverResponse = null;
    final CountDownLatch latch = new CountDownLatch(1);
    final HttpClient.Callback javaCallback = new HttpClient.Callback() // java callback
    {
      @Override
      public void onFinished(final ServerResponse response)
      {
        serverResponse = response;
        latch.countDown();
      }
    };
    androidHttpClient.request(
        new HttpRequest("https://easylist-downloads.adblockplus.org/easylist_invalid.txt"),
        javaCallback);
    latch.await(SLEEP_MAX_TIME_MS, TimeUnit.MILLISECONDS);

    assertNotNull(serverResponse);
    assertEquals(
        ServerResponse.NsStatus.ERROR_FAILURE,
        serverResponse.getStatus());
    assertEquals(HTTP_NOT_FOUND, serverResponse.getResponseStatus());
  }

  @Test
  public void testRealHttpClientRequestError()
  {
    jsEngine.evaluate(
        "let foo; _webRequest.GET('https://easylist-downloads.adblockplus.org/easylist_invalid.txt', {}, " +
            "function(result) {foo = result;} )");

    waitForDefined("foo");

    final String response = jsEngine.evaluate("foo.responseText").asString();
    assertNotNull(response);
    assertEquals(
        ServerResponse.NsStatus.ERROR_FAILURE.getStatusCode(),
        jsEngine.evaluate("foo.status").asLong());
    assertEquals(HTTP_NOT_FOUND, jsEngine.evaluate("foo.responseStatus").asLong());
  }

  @Test
  public void testXMLHttpRequest()
  {
    jsEngine.evaluate(
        "var result;\n" +
        "var request = new XMLHttpRequest();\n" +
        "request.open('GET', 'https://easylist-downloads.adblockplus.org/easylist.txt');\n" +
        "request.setRequestHeader('X', 'Y');\n" +
        "request.setRequestHeader('X2', 'Y2');\n" +
        "request.overrideMimeType('text/plain');\n" +
        "request.addEventListener('load',function() {result=request.responseText;}, false);\n" +
        "request.addEventListener('error',function() {result='error';}, false);\n" +
        "request.send(null);");

    waitForDefined("result");

    assertEquals(HTTP_OK, jsEngine.evaluate("request.status").asLong());
    assertEquals("[Adblock Plus ", jsEngine.evaluate("result.substr(0, 14)").asString());
    assertEquals(
        "text/plain",
        jsEngine.evaluate("request.getResponseHeader('Content-Type').substr(0, 10)").asString());
    assertTrue(jsEngine.evaluate("request.getResponseHeader('Location')").isNull());
  }

  @Test
  public void testMalformedUriError() throws InterruptedException
  {
    serverResponse = null;
    final CountDownLatch latch = new CountDownLatch(1);
    final HttpClient.Callback javaCallback = new HttpClient.Callback() // java callback
    {
      @Override
      public void onFinished(final ServerResponse response)
      {
        serverResponse = response;
        latch.countDown();
      }
    };
    androidHttpClient.request(new HttpRequest("wronProtool://some.url"), javaCallback);
    latch.await(SLEEP_MAX_TIME_MS, TimeUnit.MILLISECONDS);

    assertNotNull(serverResponse);
    assertEquals(
            ServerResponse.NsStatus.ERROR_MALFORMED_URI,
            serverResponse.getStatus());
  }

  @Test
  public void testUnknownHostError() throws InterruptedException
  {
    serverResponse = null;
    final CountDownLatch latch = new CountDownLatch(1);
    final HttpClient.Callback javaCallback = new HttpClient.Callback() // java callback
    {
      @Override
      public void onFinished(final ServerResponse response)
      {
        serverResponse = response;
        latch.countDown();
      }
    };
    androidHttpClient.request(
        new HttpRequest("https://non.exisiting.url.abc.def"),
        javaCallback);
    latch.await(SLEEP_MAX_TIME_MS, TimeUnit.MILLISECONDS);

    assertNotNull(serverResponse);
    assertEquals(
            ServerResponse.NsStatus.ERROR_UNKNOWN_HOST,
            serverResponse.getStatus());
  }
}