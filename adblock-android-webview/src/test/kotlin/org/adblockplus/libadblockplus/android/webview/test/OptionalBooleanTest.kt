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

package org.adblockplus.libadblockplus.android.webview.test

import org.adblockplus.libadblockplus.android.webview.AdblockWebView.OptionalBoolean
import org.adblockplus.libadblockplus.android.webview.AdblockWebView.OptionalBoolean.from
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class OptionalBooleanTest {
    @Test
    fun testFrom() {
        assertEquals(OptionalBoolean.TRUE, from(true))
        assertEquals(OptionalBoolean.FALSE, from(false))
    }

    @Test
    fun testToString() {
        // just to show how it looks like in the debug output
        assertEquals("TRUE", OptionalBoolean.TRUE.toString())
        assertEquals("FALSE", OptionalBoolean.FALSE.toString())
        assertEquals("UNDEFINED", OptionalBoolean.UNDEFINED.toString())

        assertEquals("TRUE", AtomicReference(OptionalBoolean.TRUE).toString())
    }
}
