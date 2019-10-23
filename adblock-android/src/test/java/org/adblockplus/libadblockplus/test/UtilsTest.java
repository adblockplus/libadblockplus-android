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

import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

public class UtilsTest
{
  @Test
  public void testAbsoluteUrl() throws URISyntaxException
  {
    assertFalse(Utils.isAbsoluteUrl("/index.html"));
    assertFalse(Utils.isAbsoluteUrl("../index.html"));
    assertFalse(Utils.isAbsoluteUrl("../../index.html"));

    assertTrue(Utils.isAbsoluteUrl("http://domain.com"));
    assertTrue(Utils.isAbsoluteUrl("https://domain.com"));
    assertTrue(Utils.isAbsoluteUrl("https://www.domain.com"));

    assertTrue(Utils.isAbsoluteUrl("https://www.domain.рф"));
  }

  @Test
  public void testRelativeUrl() throws MalformedURLException
  {
    final String relativeUrl = "/redirected.html";
    final String baseUrl = "http://domain.com";

    final String absoluteUrl = Utils.getAbsoluteUrl(baseUrl, relativeUrl);
    assertEquals(baseUrl + relativeUrl, absoluteUrl);
  }

  @Test
  public void testRelativeUrl_WithQuery() throws MalformedURLException
  {
    final String relativeUrl = "/redirected.html?argument=123";
    final String baseUrl = "http://domain.com";

    final String absoluteUrl = Utils.getAbsoluteUrl(baseUrl, relativeUrl);
    assertEquals(baseUrl + relativeUrl, absoluteUrl);
  }

  @Test
  public void testRelativeUrl_WithFragment() throws MalformedURLException
  {
    final String relativeUrl = "/redirected.html?argument=123#fragment";
    final String baseUrl = "http://domain.com";

    final String absoluteUrl = Utils.getAbsoluteUrl(baseUrl, relativeUrl);
    assertEquals(baseUrl + relativeUrl, absoluteUrl);
  }

  @Test
  public void testRelativeUrl_Https() throws MalformedURLException
  {
    final String relativeUrl = "/redirected.html?argument=123";
    final String baseUrl = "https://domain.com";

    final String absoluteUrl = Utils.getAbsoluteUrl(baseUrl, relativeUrl);
    assertEquals(baseUrl + relativeUrl, absoluteUrl);
  }

  @Test
  public void testExtractPathThrowsForInvalidUrl()
  {
    final String url = "some invalid url";
    try
    {
      Utils.extractPathWithQuery(url);
      fail("MalformedURLException is expected to be thrown");
    }
    catch (final MalformedURLException e)
    {
      // ignored
    }
  }

  @Test
  public void testExtractPathFromUrlsWithUnescapedCharacters() throws MalformedURLException
  {
    // "[" is illegal and throws URISyntaxException exception in `new URI(...)`
    final String url =
      "https://static-news.someurl.com/static-mcnews/2013/06/sbi-loan[1]_28825168_300x250.jpg";
    assertEquals("/static-mcnews/2013/06/sbi-loan[1]_28825168_300x250.jpg",
        Utils.extractPathWithQuery(url));
  }

  @Test
  public void testExtractPathWithoutQuery() throws MalformedURLException
  {
    final String url = "http://domain.com/image.jpeg";
    assertEquals("/image.jpeg", Utils.extractPathWithQuery(url));
  }

  @Test
  public void testExtractPathWithQuery() throws MalformedURLException
  {
    final String url = "http://domain.com/image.jpeg?id=5";
    assertEquals("/image.jpeg?id=5", Utils.extractPathWithQuery(url));
  }

  @Test
  public void testExtractPathWithoutQueryIgnoresFragment() throws MalformedURLException
  {
    final String url = "http://domain.com/image.jpeg#fragment";
    assertEquals("/image.jpeg", Utils.extractPathWithQuery(url));
  }

  @Test
  public void testExtractPathWithQueryIgnoresFragment() throws MalformedURLException
  {
    final String url = "http://domain.com/image.jpeg?id=5#fragment";
    assertEquals("/image.jpeg?id=5", Utils.extractPathWithQuery(url));
  }

  @Test
  public void testEscapeJavaScriptString()
  {
    assertEquals("name123", Utils.escapeJavaScriptString("name123"));       // nothing
    assertEquals("name\\\"123", Utils.escapeJavaScriptString("name\"123")); // "
    assertEquals("name\\'123", Utils.escapeJavaScriptString("name'123"));   // '
    assertEquals("name\\\\123", Utils.escapeJavaScriptString("name\\123")); // \
    assertEquals("name\\n123", Utils.escapeJavaScriptString("name\n123"));  // \n
    assertEquals("name\\r123", Utils.escapeJavaScriptString("name\r123"));  // \r
    assertEquals("123", Utils.escapeJavaScriptString(new String(new byte[]
        { '1', '2', '3' })));
    assertEquals("123\u202845", Utils.escapeJavaScriptString(new String(new byte[] //\u2028
        { '1', '2', '3', (byte)0xE2, (byte)0x80, (byte)0xA8, '4', '5' })));
    assertEquals("123\u202945", Utils.escapeJavaScriptString(new String(new byte[] //\u2029
        { '1', '2', '3', (byte)0xE2, (byte)0x80, (byte)0xA9, '4', '5' })));
  }
}
