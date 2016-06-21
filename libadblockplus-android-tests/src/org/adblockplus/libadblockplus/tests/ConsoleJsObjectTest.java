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

import org.adblockplus.libadblockplus.LogSystem;
import org.adblockplus.libadblockplus.MockLogSystem;

import org.junit.Test;

public class ConsoleJsObjectTest extends BaseJsTest
{
    protected MockLogSystem mockLogSystem;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        mockLogSystem = new MockLogSystem();
        jsEngine.setLogSystem(mockLogSystem);
    }

    @Test
    public void testConsoleLogCall()
    {
        jsEngine.evaluate("\n\nconsole.log('foo', 'bar');\n\n", "eval");
        assertEquals(LogSystem.LogLevel.LOG, mockLogSystem.getLastLogLevel());
        assertEquals("foo bar", mockLogSystem.getLastMessage());
        assertEquals("eval:3", mockLogSystem.getLastSource());
    }

    @Test
    public void testConsoleDebugCall()
    {
        jsEngine.evaluate("console.debug('foo', 'bar')");
        assertEquals(LogSystem.LogLevel.LOG, mockLogSystem.getLastLogLevel());
        assertEquals("foo bar", mockLogSystem.getLastMessage());
        assertEquals(":1", mockLogSystem.getLastSource());
    }

    @Test
    public void testConsoleInfoCall()
    {
        jsEngine.evaluate("console.info('foo', 'bar')");
        assertEquals(LogSystem.LogLevel.INFO, mockLogSystem.getLastLogLevel());
        assertEquals("foo bar", mockLogSystem.getLastMessage());
        assertEquals(":1", mockLogSystem.getLastSource());
    }

    @Test
    public void testConsoleWarnCall()
    {
        jsEngine.evaluate("console.warn('foo', 'bar')");
        assertEquals(LogSystem.LogLevel.WARN, mockLogSystem.getLastLogLevel());
        assertEquals("foo bar", mockLogSystem.getLastMessage());
        assertEquals(":1", mockLogSystem.getLastSource());
    }

    @Test
    public void testConsoleErrorCall()
    {
        jsEngine.evaluate("console.error('foo', 'bar')");
        assertEquals(LogSystem.LogLevel.ERROR, mockLogSystem.getLastLogLevel());
        assertEquals("foo bar", mockLogSystem.getLastMessage());
        assertEquals(":1", mockLogSystem.getLastSource());
    }

    @Test
    public void testConsoleTraceCall()
    {
        jsEngine.evaluate(
            "\n" +
            "function foo()\n" +
            "{\n" +
            "   (function() {\n" +
            "       console.trace();\n" +
            "   })();\n" +
            "}\n" +
            "foo();", "eval");
        assertEquals(LogSystem.LogLevel.TRACE, mockLogSystem.getLastLogLevel());
        assertEquals(
            "1: /* anonymous */() at eval:5\n" +
            "2: foo() at eval:6\n" +
            "3: /* anonymous */() at eval:8\n", mockLogSystem.getLastMessage());
        assertEquals("", mockLogSystem.getLastSource());
    }
}
