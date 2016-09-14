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
import org.adblockplus.libadblockplus.EventCallback;
import org.adblockplus.libadblockplus.JsValue;

import org.junit.Test;

import java.util.List;

public class JsEngineTest extends BaseJsTest
{
  @Test
  public void testEvaluate()
  {
    jsEngine.evaluate("function hello() { return 'Hello'; }");
    JsValue result = jsEngine.evaluate("hello()");
    assertTrue(result.isString());
    assertEquals("Hello", result.asString());
  }

  @Test
  public void testRuntimeExceptionIsThrown()
  {
    try
    {
      jsEngine.evaluate("doesnotexist()");
      fail();
    } catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testCompileTimeExceptionIsThrown()
  {
    try
    {
      jsEngine.evaluate("'foo'bar'");
      fail();
    } catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testValueCreation()
  {
    JsValue value;

    final String STRING_VALUE = "foo";
    value = jsEngine.newValue(STRING_VALUE);
    assertTrue(value.isString());
    assertEquals(STRING_VALUE, value.asString());

    final long LONG_VALUE = 12345678901234l;
    value = jsEngine.newValue(LONG_VALUE);
    assertTrue(value.isNumber());
    assertEquals(LONG_VALUE, value.asLong());

    final boolean BOOLEAN_VALUE = true;
    value = jsEngine.newValue(BOOLEAN_VALUE);
    assertTrue(value.isBoolean());
    assertEquals(BOOLEAN_VALUE, value.asBoolean());
  }

  private boolean callbackCalled;
  private List<JsValue> callbackParams;
  private EventCallback callback = new EventCallback()
  {
    @Override
    public void eventCallback(List<JsValue> params)
    {
      callbackCalled = true;
      callbackParams = params;
    }
  };

  @Test
  public void testEventCallbacks()
  {
    callbackCalled = false;

    // Trigger event without a callback
    callbackCalled = false;
    jsEngine.evaluate("_triggerEvent('foobar')");
    assertFalse(callbackCalled);

    // Set callback
    final String EVENT_NAME = "foobar";
    jsEngine.setEventCallback(EVENT_NAME, callback);
    callbackCalled = false;
    jsEngine.evaluate("_triggerEvent('foobar', 1, 'x', true)");
    assertTrue(callbackCalled);
    assertNotNull(callbackParams);
    assertEquals(3, callbackParams.size());
    assertEquals(1, callbackParams.get(0).asLong());
    assertEquals("x", callbackParams.get(1).asString());
    assertTrue(callbackParams.get(2).asBoolean());

    // Trigger a different event
    callbackCalled = false;
    jsEngine.evaluate("_triggerEvent('barfoo')");
    assertFalse(callbackCalled);

    // Remove callback
    jsEngine.removeEventCallback(EVENT_NAME);
    callbackCalled = false;
    jsEngine.evaluate("_triggerEvent('foobar')");
    assertFalse(callbackCalled);
  }
}
