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

import org.junit.Test;

public class GlobalJsObjectTest extends BaseJsTest
{
  @Test
  public void testSetTimeout() throws InterruptedException
  {
    jsEngine.evaluate("setTimeout(function() {foo = 'bar';}, 100)");
    assertTrue(jsEngine.evaluate("this.foo").isUndefined());
    Thread.sleep(200);
    assertEquals("bar", jsEngine.evaluate("this.foo").asString());
  }

  @Test
  public void testSetTimeoutWithArgs() throws InterruptedException
  {
    jsEngine.evaluate("setTimeout(function(s) {foo = s;}, 100, 'foobar')");
    assertTrue(jsEngine.evaluate("this.foo").isUndefined());
    Thread.sleep(200);
    assertEquals("foobar", jsEngine.evaluate("this.foo").asString());
  }

  @Test
  public void testSetTimeoutWithInvalidArgs()
  {
    try
    {
      jsEngine.evaluate("setTimeout()");
      fail();
    } catch (AdblockPlusException e)
    {
      // ignored
    }

    try
    {
      jsEngine.evaluate("setTimeout('', 1)");
      fail();
    } catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testSetMultipleTimeouts() throws InterruptedException
  {
    jsEngine.evaluate("foo = []");
    jsEngine.evaluate("setTimeout(function(s) {foo.push('1');}, 100)");
    jsEngine.evaluate("setTimeout(function(s) {foo.push('2');}, 150)");
    Thread.sleep(200);
    assertEquals("1,2", jsEngine.evaluate("this.foo").asString());
  }
}
