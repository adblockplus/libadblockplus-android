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

import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.android.AndroidHttpClient;
import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// The test requires active internet connection and actually downloads files from the internet
public class AndroidHttpClientTest extends BaseFilterEngineTest
{
  protected static final Charset charset = Charset.forName("UTF-8");
  protected ServerResponse serverResponse = null;

  private HttpClient androidHttpClient = new AndroidHttpClient(true, "UTF-8");

  @Override
  public void setUp()
  {
    setUpHttpClient(androidHttpClient);
    super.setUp();
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
      public void onFinished(ServerResponse response)
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
      public void onFinished(ServerResponse response)
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

    assertEquals(
        ServerResponse.NsStatus.OK.getStatusCode(),
        jsEngine.evaluate("request.channel.status").asLong());

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
      public void onFinished(ServerResponse response)
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
      public void onFinished(ServerResponse response)
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