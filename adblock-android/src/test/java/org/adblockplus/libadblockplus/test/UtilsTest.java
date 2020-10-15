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
import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    final Charset utf8 = Charset.forName("UTF-8");
    assertEquals("123\u202845", Utils.escapeJavaScriptString(new String(new byte[] //\u2028
        { '1', '2', '3', (byte)0xE2, (byte)0x80, (byte)0xA8, '4', '5' }, utf8)));
    assertEquals("123\u202945", Utils.escapeJavaScriptString(new String(new byte[] //\u2029
        { '1', '2', '3', (byte)0xE2, (byte)0x80, (byte)0xA9, '4', '5' }, utf8)));
  }

  @Test
  public void testGetDomain()
  {
    // Test success
    final Map<String, String> urlToDomainMap_OK = new HashMap<String, String>() {{
      put("http://domain.com:8080/", "domain.com");
      put("http://spl.abcdef.com/path?someparam=somevalue", "spl.abcdef.com");
      put("file://home/user/document.pdf", "home");
      put("data://text/vnd-example+xyz;foo=bar;base64,R0lGODdh", "text");

      put("http://spl.abcdef.com/path?someparam=somevalue", "spl.abcdef.com");
      put("file://home/user/document.pdf", "home");
      put("data://text/vnd-example+xyz;foo=bar;base64,R0lGODdh", "text");
    }};

    for (final Map.Entry<String, String> urlToDomainEntry : urlToDomainMap_OK.entrySet())
    {
      try
      {
        assertEquals(Utils.getDomain(urlToDomainEntry.getKey()), urlToDomainEntry.getValue());
      }
      catch (final URISyntaxException e)
      {
        fail(e.getMessage());
      }
    }

    // Test failures
    final List<String> wrongUrls = new ArrayList<String>() {{
      add("http://domain with spaces.com");
      add("www.unallowed%character.com");
      add("file://");
    }};

    for (final String urlEntry : wrongUrls)
    {
      try
      {
        Utils.getDomain(urlEntry);
        fail("URISyntaxException is expected to be thrown");
      }
      catch (final URISyntaxException e)
      {
        // expected
      }
    }
  }

  @Test
  public void testIsFirstPartyCookie()
  {
    final String navigationUrl = "https://some.domain.com/";

    // Test success
    final Map<String, String> urlAndCookieMap_OK = new HashMap<String, String>() {{
      put("https://some.domain.com/1", "somecookie=someValue; Path=/;");
      // "Domain" cookie parameter is used instead of a request url to obtain and compare domains
      put("https://blabla.com/2", "somecookie=someValue; Path=/; Domain=.domain.com");
      put("https://blabla.om/3", "somecookie=someValue; Path=/; Domain=some.domain.com");
    }};

    for (Map.Entry<String, String> urlAndCookieEntry : urlAndCookieMap_OK.entrySet())
    {
      assertTrue(Utils.isFirstPartyCookie(navigationUrl, urlAndCookieEntry.getKey(), urlAndCookieEntry.getValue()));
    }

    // Test failures
    final Map<String, String> urlAndCookieMap_NOK = new HashMap<String, String>() {{
      put("https://blabla.com/1", "somecookie=someValue; Path=/;");
      put("https://some.domain.com/2", "somecookie=someValue; Path=/; Domain=blabla.com");
      put("https://blabla.om/3", "somecookie=someValue; Path=/; Domain=www.some.domain.com");
    }};

    for (Map.Entry<String, String> urlAndCookieEntry : urlAndCookieMap_NOK.entrySet())
    {
      assertFalse(Utils.isFirstPartyCookie(navigationUrl, urlAndCookieEntry.getKey(), urlAndCookieEntry.getValue()));
    }
  }

  @Test
  public void testIsSubOrDomain()
  {
    assertFalse(Utils.isSubdomainOrDomain("", ""));
    assertFalse(Utils.isSubdomainOrDomain("host.com", ""));
    assertFalse(Utils.isSubdomainOrDomain("", "domain.com"));

    assertTrue(Utils.isSubdomainOrDomain("com", "com"));
    assertFalse(Utils.isSubdomainOrDomain("co", "com"));
    assertFalse(Utils.isSubdomainOrDomain("com", "co"));

    assertFalse(Utils.isSubdomainOrDomain("www,google.com", "google.com"));

    assertTrue(Utils.isSubdomainOrDomain("google.com", "com"));
    assertTrue(Utils.isSubdomainOrDomain("www.google.com", "com"));
    assertTrue(Utils.isSubdomainOrDomain("www.google.com", "google.com"));
    assertTrue(Utils.isSubdomainOrDomain("www.google.com", "www.google.com"));
    assertFalse(Utils.isSubdomainOrDomain("google.com", "www.google.com"));
    assertFalse(Utils.isSubdomainOrDomain("com", "www.google.com"));
    assertFalse(Utils.isSubdomainOrDomain("com", "google.com"));
    assertFalse(Utils.isSubdomainOrDomain("gogoogle.com", "google.com"));
    assertFalse(Utils.isSubdomainOrDomain("www.gogoogle.com", "google.com"));
    assertFalse(Utils.isSubdomainOrDomain("www.gogoogle.com", "www.google.com"));
  }
  
  private void verifyHeaderEntriesMap(final Set<String> inputHeadersSet,
                                      final Map<String, String> outputHeadersMap,
                                      final String expectedValue)
  {
    for (final String header : inputHeadersSet)
    {
      assertTrue(outputHeadersMap.containsKey(header));
      assertEquals(expectedValue, outputHeadersMap.get(header));
    }
  }

  @Test
  public void testConvertHeaderEntriesToMap()
  {
    final Set<String> commaNotMergableHeaders = Utils.commaNotMergableHeaders;
    // Just some examples, can be actually any strings not present in commaNotMergableHeaders
    final Set<String> commaMergableHeaders = new HashSet<>(Arrays.asList(
        "cache-control", "content-language", "etag", "transfer-encoding"
    ));

    // Make sure headers sets have no intersection
    for (final String header : commaNotMergableHeaders)
    {
      assertFalse(commaMergableHeaders.contains(header));
    }

    // We don't verify here headers value semantics so any string will do
    final String headerValue1 = "12334 AAAA bbbb";
    final String headerValue2 = "CCCC 5555 dddd";


    // Test not mergable headers => headerValue2 overwrites headerValue1
    final List<HeaderEntry> notMergableHeadersData = new ArrayList<>();
    for (final String header : commaNotMergableHeaders)
    {
      notMergableHeadersData.add(new HeaderEntry(header, headerValue1));
      notMergableHeadersData.add(new HeaderEntry(header, headerValue2));
    }
    final Map<String, String> notMergableHeadersDataConverted =
        Utils.convertHeaderEntriesToMap(notMergableHeadersData);

    // Verify that resulting map has expected size
    assertTrue(notMergableHeadersDataConverted.size() * 2 == notMergableHeadersData.size());
    // Verify all input headers are present and contain 2nd value
    verifyHeaderEntriesMap(commaNotMergableHeaders, notMergableHeadersDataConverted, headerValue2);


    // Test mergable headers with identical values (case insensitive) => should not be merged
    final List<HeaderEntry> mergableHeadersDataWithIdenticaltValues = new ArrayList<>();
    for (final String header : commaMergableHeaders)
    {
      mergableHeadersDataWithIdenticaltValues.add(new HeaderEntry(header, headerValue1.toLowerCase()));
      mergableHeadersDataWithIdenticaltValues.add(new HeaderEntry(header, headerValue1.toUpperCase()));
    }

    final Map<String, String> mergableHeadersDataWithIdenticaltValuesConverted =
        Utils.convertHeaderEntriesToMap(mergableHeadersDataWithIdenticaltValues);

    // Verify that resulting map has expected size
    assertTrue(mergableHeadersDataWithIdenticaltValuesConverted.size() * 2
        == mergableHeadersDataWithIdenticaltValues.size());
    // Verify all input headers are present and contain 1st value which is not duplicated
    verifyHeaderEntriesMap(commaMergableHeaders, mergableHeadersDataWithIdenticaltValuesConverted,
        headerValue1.toLowerCase());


    // Test mergable headers with distinct values => should be merged
    final List<HeaderEntry> mergableHeadersDataWithDistinctValues = new ArrayList<>();
    for (final String header : commaMergableHeaders)
    {
      mergableHeadersDataWithDistinctValues.add(new HeaderEntry(header, headerValue1));
      mergableHeadersDataWithDistinctValues.add(new HeaderEntry(header, headerValue2));
    }

    final Map<String, String> mergableHeadersDataWithDistinctValuesConverted =
        Utils.convertHeaderEntriesToMap(mergableHeadersDataWithDistinctValues);

    // Verify that resulting map has expected size
    assertTrue(mergableHeadersDataWithDistinctValuesConverted.size() * 2
        == mergableHeadersDataWithDistinctValues.size());
    // Verify all input headers are present and contain 1st value which is not duplicated
    verifyHeaderEntriesMap(commaMergableHeaders, mergableHeadersDataWithDistinctValuesConverted,
        headerValue1 + ", " + headerValue2);
  }
}
