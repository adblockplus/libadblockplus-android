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

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.TestEventCallback;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsEngineTest extends BaseJsEngineTest
{
  protected boolean isSame(final JsValue value1, final JsValue value2)
  {
    final List<JsValue> params = new ArrayList<>();
    params.add(value1);
    params.add(value2);

    final JsValue call = jsEngine
        .evaluate("(function(a, b) { return a == b })")
        .call(params);
    final boolean result = call.asBoolean();
    call.dispose();
    return result;
  }

  @Test
  public void testEvaluate()
  {
    jsEngine.evaluate("function hello() { return 'Hello'; }").dispose();
    final JsValue result = jsEngine.evaluate("hello()");
    assertNotNull(result);
    assertTrue(result.isString());
    assertEquals("Hello", result.asString());
    result.dispose();
  }

  @Test
  public void testRuntimeExceptionIsThrown()
  {
    try
    {
      jsEngine.evaluate("doesnotexist()").dispose();
      fail();
    }
    catch (final AdblockPlusException e)
    {
      // expected exception
    }
  }

  @Test
  public void testCompileTimeExceptionIsThrown()
  {
    try
    {
      jsEngine.evaluate("'foo'bar'").dispose();
      fail();
    }
    catch (final AdblockPlusException e)
    {
      // expected exception
    }
  }

  @Test
  public void testValueCreation()
  {
    JsValue value;

    final String STRING_VALUE = "foo";
    value = jsEngine.newValue(STRING_VALUE);
    assertNotNull(value);
    assertTrue(value.isString());
    assertEquals(STRING_VALUE, value.asString());
    value.dispose();

    final long LONG_VALUE = 12345678901234L;
    value = jsEngine.newValue(LONG_VALUE);
    assertNotNull(value);
    assertTrue(value.isNumber());
    assertEquals(LONG_VALUE, value.asLong());
    value.dispose();

    final boolean BOOLEAN_VALUE = true;
    value = jsEngine.newValue(BOOLEAN_VALUE);
    assertNotNull(value);
    assertTrue(value.isBoolean());
    assertEquals(BOOLEAN_VALUE, value.asBoolean());
    value.dispose();
  }

  @Test
  public void testValueCopyString()
  {
    final String STRING_VALUE = "foo";

    final JsValue value1 = jsEngine.newValue(STRING_VALUE);
    assertNotNull(value1);
    assertTrue(value1.isString());
    assertEquals(STRING_VALUE, value1.asString());

    final JsValue value2 = jsEngine.newValue(STRING_VALUE);
    assertNotNull(value2);
    assertTrue(value2.isString());
    assertEquals(STRING_VALUE, value2.asString());

    assertTrue(isSame(value1, value2));
    value1.dispose();
    value2.dispose();
  }

  @Test
  public void testValueCopyLong()
  {
    final long LONG_VALUE = 12345678901234L;

    final JsValue value1 = jsEngine.newValue(LONG_VALUE);
    assertNotNull(value1);
    assertTrue(value1.isNumber());
    assertEquals(LONG_VALUE, value1.asLong());

    final JsValue value2 = jsEngine.newValue(LONG_VALUE);
    assertNotNull(value2);
    assertTrue(value2.isNumber());
    assertEquals(LONG_VALUE, value2.asLong());

    assertTrue(isSame(value1, value2));
    value1.dispose();
    value2.dispose();
  }

  @Test
  public void testValueCopyBool()
  {
    final boolean BOOL_VALUE = true;

    final JsValue value1 = jsEngine.newValue(BOOL_VALUE);
    assertNotNull(value1);
    assertTrue(value1.isBoolean());
    assertEquals(BOOL_VALUE, value1.asBoolean());

    final JsValue value2 = jsEngine.newValue(BOOL_VALUE);
    assertNotNull(value2);
    assertTrue(value2.isBoolean());
    assertEquals(BOOL_VALUE, value2.asBoolean());

    assertTrue(isSame(value1, value2));
    value1.dispose();
    value2.dispose();
  }

  @Test
  public void testEventCallbacks()
  {
    // Not using Mockito as `JniCallbackBase.javaVM` stays uninitialized causing NPE
    final TestEventCallback eventCallback = new TestEventCallback();

    // Trigger event without a callback
    eventCallback.reset();
    jsEngine.evaluate("_triggerEvent('foobar')").dispose();
    assertFalse(eventCallback.isCalled());

    // Set callback
    eventCallback.reset();
    final String EVENT_NAME = "foobar";
    jsEngine.setEventCallback(EVENT_NAME, eventCallback);
    jsEngine.evaluate("_triggerEvent('foobar', 1, 'x', true)").dispose();
    assertTrue(eventCallback.isCalled());
    assertNotNull(eventCallback.getParams());
    assertEquals(3, eventCallback.getParams().size());
    assertEquals(1L, eventCallback.getParams().get(0).asLong());
    assertEquals("x", eventCallback.getParams().get(1).asString());
    assertTrue(eventCallback.getParams().get(2).asBoolean());

    // Trigger a different event
    eventCallback.reset();
    jsEngine.evaluate("_triggerEvent('barfoo')").dispose();
    assertFalse(eventCallback.isCalled());

    // Remove callback
    eventCallback.reset();
    jsEngine.removeEventCallback(EVENT_NAME);
    jsEngine.evaluate("_triggerEvent('foobar')").dispose();
    assertFalse(eventCallback.isCalled());
    eventCallback.dispose();
  }

  @Test
  public void testGlobalProperty()
  {
    final String PROPERTY = "foo";
    final String VALUE = "bar";

    jsEngine.setGlobalProperty(PROPERTY, jsEngine.newValue(VALUE));
    final JsValue value = jsEngine.evaluate(PROPERTY);
    assertNotNull(value);
    assertTrue(value.isString());
    assertEquals(VALUE, value.asString());
    value.dispose();
  }
}
