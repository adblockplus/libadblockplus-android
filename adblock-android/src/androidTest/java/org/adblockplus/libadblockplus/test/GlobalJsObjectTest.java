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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GlobalJsObjectTest extends BaseJsEngineTest
{
  public static final int SLEEP_INTERVAL_MS = 200;

  @Test
  public void testSetTimeout() throws InterruptedException
  {
    jsEngine.evaluate("let foo; setTimeout(function() {foo = 'bar';}, 100)").dispose();
    JsValue foo = jsEngine.evaluate("foo");
    assertTrue(foo.isUndefined());
    foo.dispose();
    Thread.sleep(SLEEP_INTERVAL_MS);
    foo = jsEngine.evaluate("foo");
    assertEquals("bar", foo.asString());
    foo.dispose();
  }

  @Test
  public void testSetTimeoutWithArgs() throws InterruptedException
  {
    jsEngine.evaluate("let foo; setTimeout(function(s) {foo = s;}, 100, 'foobar')").dispose();
    JsValue foo = jsEngine.evaluate("foo");
    assertTrue(foo.isUndefined());
    Thread.sleep(SLEEP_INTERVAL_MS);
    foo.dispose();
    foo = jsEngine.evaluate("foo");
    assertEquals("foobar", foo.asString());
    foo.dispose();
  }

  @Test
  public void testSetTimeoutWithInvalidArgs()
  {
    try
    {
      jsEngine.evaluate("setTimeout()").dispose();
      fail();
    }
    catch (final AdblockPlusException e)
    {
      // expected exception
    }

    try
    {
      jsEngine.evaluate("setTimeout('', 1)").dispose();
      fail();
    }
    catch (final AdblockPlusException e)
    {
      // expected exception
    }
  }

  @Test
  public void testSetMultipleTimeouts() throws InterruptedException
  {
    jsEngine.evaluate("let foo = []").dispose();
    jsEngine.evaluate("setTimeout(function(s) {foo.push('1');}, 100)").dispose();
    jsEngine.evaluate("setTimeout(function(s) {foo.push('2');}, 150)").dispose();
    Thread.sleep(SLEEP_INTERVAL_MS);
    final JsValue foo = jsEngine.evaluate("foo");
    assertEquals("1,2", foo.asString());
    foo.dispose();
  }
}
