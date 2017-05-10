package org.adblockplus.libadblockplus.tests;

import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Test for Utils
 */
public class UtilsTest extends BaseJsTest
{
  @Test
  public void testJson_null()
  {
    final List<String> list = null;
    String json = Utils.stringListToJsonArray(list);
    assertEquals("[]", json);
  }

  @Test
  public void testJson_single()
  {
    final List<String> list = Arrays.asList("string1");
    String json = Utils.stringListToJsonArray(list);
    assertEquals("[\"string1\"]", json);
  }

  @Test
  public void testJson_multiple()
  {
    final List<String> list = Arrays.asList("string1", "string2");
    String json = Utils.stringListToJsonArray(list);
    assertEquals("[\"string1\",\"string2\"]", json);
  }

  @Test
  public void testJson_multipleWithNull()
  {
    final List<String> list = Arrays.asList("string1", null, "string2");
    String json = Utils.stringListToJsonArray(list);
    assertEquals("[\"string1\",\"string2\"]", json);
  }

  @Test
  public void testJson_singleWithNull()
  {
    final List<String> list = new LinkedList<String>();
    list.add(null);
    String json = Utils.stringListToJsonArray(list);
    assertEquals("[]", json);
  }

  @Test
  public void testUrlWithoutParams_null()
  {
    try
    {
      Utils.getUrlWithoutParams(null);
      fail();
    }
    catch (IllegalArgumentException ignored)
    {
      // ignored
    }
  }

  @Test
  public void testUrlWithoutParams_empty()
  {
    String url = Utils.getUrlWithoutParams("https://www.google.com?");
    assertNotNull(url);
    assertEquals("https://www.google.com", url);
  }

  @Test
  public void testUrlWithoutParams_withoutParams()
  {
    final String originalUrl = "http://www.google.com";
    String url = Utils.getUrlWithoutParams(originalUrl);
    assertNotNull(url);
    assertEquals(originalUrl, url);
  }

  @Test
  public void testUrlWithoutParams_ok()
  {
    String url = Utils.getUrlWithoutParams("https://www.google.com?q=adblockplus");
    assertNotNull(url);
    assertEquals("https://www.google.com", url);
  }

  @Test
  public void testUrlWithoutParams_multipleParams()
  {
    String url = Utils.getUrlWithoutParams("https://www.google.com?q=adblockplus&gws_rd=cr");
    assertNotNull(url);
    assertEquals("https://www.google.com", url);
  }

  @Test
  public void testUrlWithoutParams_language()
  {
    String url = Utils.getUrlWithoutParams("http://почта.рф?q=myemail"); // non-English characters in URL
    assertNotNull(url);
    assertEquals("http://почта.рф", url);
  }

  @Test
  public void testUrlWithoutParams_digits()
  {
    String url = Utils.getUrlWithoutParams("https://www.google.com?q=123");
    assertNotNull(url);
    assertEquals("https://www.google.com", url);
  }

  @Test
  public void testUrlWithoutParams_multipleQuestionMarks()
  {
    String url = Utils.getUrlWithoutParams("https://www.google.com?q=123?t=123"); // invalid URL
    assertNotNull(url);
    assertEquals("https://www.google.com", url);
  }
}
