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
import org.adblockplus.libadblockplus.MockWebRequest;
import org.adblockplus.libadblockplus.ServerResponse;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WebRequestTest extends BaseFilterEngineTest
{
  private final static int RESPONSE_STATUS = 123;
  private final static String HEADER_KEY = "Foo";
  private final static String HEADER_VALUE = "Bar";
  private final static String RESPONSE = "(responseText)";

  private MockWebRequest mockWebRequest = new MockWebRequest();

  @Override
  public void setUp()
  {
    final ServerResponse response = new ServerResponse();
    response.setResponseStatus(RESPONSE_STATUS);
    response.setStatus(ServerResponse.NsStatus.OK);
    response.setResponse(RESPONSE);
    final List<HeaderEntry> headers = new LinkedList<HeaderEntry>();
    headers.add(new HeaderEntry(HEADER_KEY, HEADER_VALUE));
    response.setResponseHeaders(headers);
    mockWebRequest.response = response;

    setUpWebRequest(mockWebRequest);
    super.setUp();
  }

  @Test
  public void testSuccessfulRequest()
  {
    jsEngine.evaluate(
        "let foo; _webRequest.GET('http://example.com/', {X: 'Y'}, function(result) {foo = result;} )");
    assertTrue(mockWebRequest.called);
    assertNotNull(mockWebRequest.getLastUrl());
    assertEquals("http://example.com/", mockWebRequest.getLastUrl());
    assertFalse(jsEngine.evaluate("foo").isUndefined());
    assertEquals(
        ServerResponse.NsStatus.OK.getStatusCode(),
        jsEngine.evaluate("foo.status").asLong());
    assertEquals(
        Long.valueOf(RESPONSE_STATUS).longValue(),
        jsEngine.evaluate("foo.responseStatus").asLong());
    assertEquals(RESPONSE, jsEngine.evaluate("foo.responseText").asString());
    assertEquals(
        "{\"" + HEADER_KEY + "\":\"" + HEADER_VALUE + "\"}",
        jsEngine.evaluate("JSON.stringify(foo.responseHeaders)").asString());
  }

  @Test
  public void testRequestException()
  {
    mockWebRequest.exception = true;

    jsEngine.evaluate(
        "let foo; _webRequest.GET('http://example.com/', {X: 'Y'}, function(result) {foo = result;} )");
    assertTrue(mockWebRequest.called);
    assertNotNull(mockWebRequest.getLastUrl());
    assertEquals("http://example.com/", mockWebRequest.getLastUrl());
    assertFalse(jsEngine.evaluate("foo").isUndefined());
    assertEquals(
        ServerResponse.NsStatus.ERROR_FAILURE.getStatusCode(),
        jsEngine.evaluate("foo.status").asLong());
  }
}
