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
}
