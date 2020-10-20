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
package org.adblockplus.libadblockplus.test.org.adblockplus.libadblockplus.android

import org.adblockplus.libadblockplus.android.Utils
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidUtilsTest {
    @Test
    fun testGetOrigin() {
        assertEquals("http://domain.com", Utils.getOrigin("http://domain.com"))
        assertEquals("http://sub.domain.com", Utils.getOrigin("http://sub.domain.com"))
        assertEquals("http://domain.com/", Utils.getOrigin("http://domain.com/path"))
        assertEquals("http://sub.domain.com/", Utils.getOrigin("http://sub.domain.com/path"))
        assertEquals("http://domain.com/", Utils.getOrigin("http://domain.com/path?query"))
        assertEquals("http://domain.com:80", Utils.getOrigin("http://domain.com:80"))
        assertEquals("http://domain.com:80/", Utils.getOrigin("http://domain.com:80/path"))
        assertEquals("http://domain.com:8080/", Utils.getOrigin("http://domain.com:8080/path"))
        assertEquals("http://domain.com:8080/", Utils.getOrigin("http://domain.com:8080?query=m"))
    }
}
