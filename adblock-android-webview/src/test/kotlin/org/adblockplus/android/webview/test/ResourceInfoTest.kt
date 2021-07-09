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
package org.adblockplus.android.webview.test

import org.adblockplus.android.webview.HttpHeaderSiteKeyExtractor.ResourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceInfoTest {

    @Test
    fun testNoContentTypeHeader() {
        val resourceInfo = ResourceInfo.parse(null)
        assertNull(resourceInfo.encoding)
        assertNull(resourceInfo.mimeType)
        assertFalse(resourceInfo.isBinary)
    }

    @Test
    fun testBrokenContentTypeHeader() {
        fun test(contentType: String) {
            val resourceInfo = ResourceInfo.parse(contentType)
            assertNull("Tested content-type: ".plus(contentType), resourceInfo.encoding)
            assertNull("Tested content-type: ".plus(contentType), resourceInfo.mimeType)
            assertFalse("Tested content-type: ".plus(contentType), resourceInfo.isBinary)
        }
        test(";charset=UTF-8")
        test(";")
        test(";html")
    }

    @Test
    fun testContentTypeHeaderIsRegular() {
        val resourceInfo = ResourceInfo.parse("text/html; charset=utf-8")
        assertEquals("text/html", resourceInfo.mimeType)
        assertEquals("utf-8", resourceInfo.encoding)
    }

    @Test
    fun testContentTypeHeaderHasMimeOnly() {
        val resourceInfo = ResourceInfo.parse("text/html")
        assertEquals("text/html", resourceInfo.mimeType)
        assertNull(resourceInfo.encoding)
    }
}
