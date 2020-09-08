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

package org.adblockplus.libadblockplus.test

import org.adblockplus.libadblockplus.DomainWhitelist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainWhitelistTest {
    @Test
    fun testSetGet() {
        val whitelist = DomainWhitelist()
        whitelist.domains = listOf("A", "B", "C")

        assertEquals(listOf("A", "B", "C"), whitelist.domains)

        whitelist.domains = listOf<String>()
        assertEquals(0, whitelist.domains.size)
    }

    @Test
    fun testMatching() {
        val whitelist = DomainWhitelist()
        whitelist.domains = listOf("google.com", "amazon.co.uk", "中国")

        assertFalse(whitelist.hasAnyDomain(null, null))
        assertFalse(whitelist.hasAnyDomain("", null))

        assertTrue(whitelist.hasAnyDomain("http://maps.google.com", null))
        assertTrue(whitelist.hasAnyDomain("https://google.com", null))
        assertTrue(whitelist.hasAnyDomain("https://29869BAh7BzoQY2FsZW5kYXJfaWRpAqhqO" +
                "gx1c2VyX2lkaQKtdA==--271a7cfe6c0fb8f1812592bbaaff9b75cfe6f07d.amazon.co.uk", null))
        assertTrue(whitelist.hasAnyDomain("ftp://ftp.中国", null))

        assertTrue(whitelist.hasAnyDomain("", listOf("https://www.google.com")))

        assertFalse(whitelist.hasAnyDomain("http://maps.google.co", null))
        assertFalse(whitelist.hasAnyDomain("http://maps.google.com.com", null))
        assertFalse(whitelist.hasAnyDomain("https://amazon.co", null))
        assertFalse(whitelist.hasAnyDomain("https://gogoogle.com", null))
        assertFalse(whitelist.hasAnyDomain("https://zonamazon.co.uk.com", null))
        assertFalse(whitelist.hasAnyDomain("https://www.zonamazon.co.uk.com", null))
    }
}
