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

import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.MockHttpClient;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HttpClientTest extends BaseFilterEngineTest
{
  private static final int RESPONSE_STATUS = 123;
  private static final String HEADER_KEY = "Foo";
  private static final String HEADER_VALUE = "Bar";
  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private static final String RESPONSE = "(responseText)";

  private final MockHttpClient mockHttpClient = new MockHttpClient();

  @Override
  public void setUp()
  {
    final ServerResponse response = new ServerResponse();
    response.setResponseStatus(RESPONSE_STATUS);
    response.setStatus(ServerResponse.NsStatus.OK);
    response.setResponse(Utils.stringToByteBuffer(RESPONSE, CHARSET));
    final List<HeaderEntry> headers = new LinkedList<>();
    headers.add(new HeaderEntry(HEADER_KEY, HEADER_VALUE));
    response.setResponseHeaders(headers);
    mockHttpClient.response = response;

    setUpHttpClient(mockHttpClient);
    super.setUp();
  }

  @Test
  public void testSuccessfulRequest()
  {
    jsEngine.evaluate(
        "let foo; _webRequest.GET('http://example.com/', {X: 'Y'}, function(result) {foo = result;} )").dispose();
    waitForDefined("foo");
    assertTrue(mockHttpClient.called.get());
    assertNotNull(mockHttpClient.getSpecificRequest("http://example.com/", HttpClient.REQUEST_METHOD_GET));
    final JsValue foo = jsEngine.evaluate("foo");
    assertFalse(foo.isUndefined());
    foo.dispose();
    final JsValue status = jsEngine.evaluate("foo.status");
    assertEquals(
        ServerResponse.NsStatus.OK.getStatusCode(),
        status.asLong());
    status.dispose();
    final JsValue responseStatus = jsEngine.evaluate("foo.responseStatus");
    assertEquals(
        Long.valueOf(RESPONSE_STATUS).longValue(),
        responseStatus.asLong());
    responseStatus.dispose();
    final JsValue responseText = jsEngine.evaluate("foo.responseText");
    assertEquals(RESPONSE, responseText.asString());
    responseStatus.dispose();
    final JsValue respHeaders = jsEngine.evaluate("JSON.stringify(foo.responseHeaders)");
    assertEquals(
        "{\"" + HEADER_KEY + "\":\"" + HEADER_VALUE + "\"}",
        respHeaders.asString());
    respHeaders.dispose();
  }

  @Test
  public void testRequestException()
  {
    mockHttpClient.exception.set(true);

    jsEngine.evaluate(
        "let foo; _webRequest.GET('http://example.com/', {X: 'Y'}, function(result) {foo = result;} )").dispose();
    waitForDefined("foo");
    assertTrue(mockHttpClient.called.get());
    assertNotNull(mockHttpClient.getSpecificRequest("http://example.com/", HttpClient.REQUEST_METHOD_GET));
    final JsValue foo = jsEngine.evaluate("foo");
    assertFalse(foo.isUndefined());
    foo.dispose();
    final JsValue status = jsEngine.evaluate("foo.status");
    assertEquals(
        ServerResponse.NsStatus.ERROR_FAILURE.getStatusCode(),
        status.asLong());
    status.dispose();
  }
}
