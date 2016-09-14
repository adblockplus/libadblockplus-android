/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2016 Eyeo GmbH
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

package org.adblockplus.libadblockplus.tests;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.JsValue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class JsTest extends BaseJsTest
{
  @Test
  public void testUndefinedValue()
  {
    final JsValue value = jsEngine.evaluate("undefined");
    assertTrue(value.isUndefined());
    assertFalse(value.isNull());
    assertFalse(value.isString());
    assertFalse(value.isBoolean());
    assertFalse(value.isNumber());
    assertFalse(value.isObject());
    assertFalse(value.isArray());
    assertEquals("undefined", value.asString());
    try
    {
      value.asList();
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
    try
    {
      value.getProperty("foo");
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testNullValue()
  {
    final JsValue value = jsEngine.evaluate("null");
    assertFalse(value.isUndefined());
    assertTrue(value.isNull());
    assertFalse(value.isString());
    assertFalse(value.isBoolean());
    assertFalse(value.isNumber());
    assertFalse(value.isObject());
    assertFalse(value.isArray());
    assertFalse(value.isFunction());
    assertEquals("null", value.asString());
    assertFalse(value.asBoolean());
    try
    {
      value.asList();
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
    try
    {
      value.getProperty("foo");
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testStringValue()
  {
    JsValue value = jsEngine.evaluate("'123'");
    assertFalse(value.isUndefined());
    assertFalse(value.isNull());
    assertTrue(value.isString());
    assertFalse(value.isBoolean());
    assertFalse(value.isNumber());
    assertFalse(value.isObject());
    assertFalse(value.isArray());
    assertFalse(value.isFunction());
    assertEquals("123", value.asString());
    assertEquals(123l, value.asLong());
    assertTrue(value.asBoolean());
    try
    {
      value.asList();
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
    try
    {
      value.getProperty("foo");
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testLongValue()
  {
    JsValue value = jsEngine.evaluate("12345678901234");
    assertFalse(value.isUndefined());
    assertFalse(value.isNull());
    assertFalse(value.isString());
    assertFalse(value.isBoolean());
    assertTrue(value.isNumber());
    assertFalse(value.isObject());
    assertFalse(value.isArray());
    assertFalse(value.isFunction());
    assertEquals("12345678901234", value.asString());
    assertEquals(12345678901234l, value.asLong());
    assertTrue(value.asBoolean());
    try
    {
      value.asList();
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
    try
    {
      value.getProperty("foo");
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testBoolValue()
  {
    JsValue value = jsEngine.evaluate("true");
    assertFalse(value.isUndefined());
    assertFalse(value.isNull());
    assertFalse(value.isString());
    assertTrue(value.isBoolean());
    assertFalse(value.isNumber());
    assertFalse(value.isObject());
    assertFalse(value.isArray());
    assertFalse(value.isFunction());
    assertEquals("true", value.asString());
    assertTrue(value.asBoolean());
    try
    {
      value.asList();
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
    try
    {
      value.getProperty("foo");
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testObjectValue()
  {
    final String source =
      "function Foo() {\n" +
        "   this.x = 2;\n" +
        "   this.toString = function() {return 'foo';};\n" +
        "   this.valueOf = function() {return 123;};\n" +
        "};\n" +
        "new Foo()";
    JsValue value = jsEngine.evaluate(source);
    assertFalse(value.isUndefined());
    assertFalse(value.isNull());
    assertFalse(value.isString());
    assertFalse(value.isBoolean());
    assertFalse(value.isNumber());
    assertTrue(value.isObject());
    assertFalse(value.isArray());
    assertFalse(value.isFunction());
    assertEquals("foo", value.asString());
    assertEquals(123l, value.asLong());
    assertTrue(value.asBoolean());
    try
    {
      value.asList();
      fail(AdblockPlusException.class.getSimpleName() + " is expected to be thrown");
    } catch (AdblockPlusException e)
    {
      // ignored
    }
    assertEquals(2l, value.getProperty("x").asLong());
  }

  @Test
  public void testArrayValue()
  {
    JsValue value = jsEngine.evaluate("[5,8,12]");
    assertFalse(value.isUndefined());
    assertFalse(value.isNull());
    assertFalse(value.isString());
    assertFalse(value.isBoolean());
    assertFalse(value.isNumber());
    assertTrue(value.isObject());
    assertTrue(value.isArray());
    assertEquals("5,8,12", value.asString());
    assertTrue(value.asBoolean());
    assertEquals(3l, value.asList().size());
    assertEquals(5l, value.asList().get(0).asLong());
    assertEquals(8l, value.asList().get(1).asLong());
    assertEquals(12l, value.asList().get(2).asLong());
    assertEquals(3l, value.getProperty("length").asLong());
  }

  @Test
  public void testThrowingCoversion()
  {
    final String source =
      "function Foo() {\n" +
        "   this.toString = function() {throw 'test1';};\n" +
        "   this.valueOf = function() {throw 'test2';};\n" +
        "};\n" +
        "new Foo()";

    JsValue value = jsEngine.evaluate(source);
    assertEquals("", value.asString());
    assertEquals(0l, value.asLong());
  }
}
