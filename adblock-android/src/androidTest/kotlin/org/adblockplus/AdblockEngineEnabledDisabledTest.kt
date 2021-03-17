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
package org.adblockplus.test.org.adblockplus

import androidx.test.platform.app.InstrumentationRegistry
import org.adblockplus.ContentType
import org.adblockplus.MatchesResult
import org.adblockplus.libadblockplus.AppInfo
import org.adblockplus.libadblockplus.HttpClient
import org.adblockplus.libadblockplus.HttpRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.lang.Thread.sleep

class AdblockEngineEnabledDisabledTest  {
    val appInfo = AppInfo.builder().build()
    val context = InstrumentationRegistry.getInstrumentation().context
    val EMPTY_PARENT = ""

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun testDisabledByDefault() {
        var builder = org.adblockplus.libadblockplus.android.AdblockEngine.builder(
                context, appInfo,  folder.newFolder().absolutePath)
                .setDisableByDefault()
        var adblockEngine = builder.build()
        assertFalse(adblockEngine.settings().isEnabled)
        assertFalse(adblockEngine.filterEngine.isEnabled)

        var filter = adblockEngine.filterEngine.getFilterFromText("adbanner.gif")
        adblockEngine.filterEngine.addFilter(filter)

        val match2: MatchesResult? = adblockEngine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), EMPTY_PARENT, "", false)
        assertTrue(match2 == null || match2 == MatchesResult.NOT_ENABLED)
    }

    @Test
    fun testDoesNotDownloadSubscriptions() {

        var anhttpclient = mock(HttpClient::class.java)

        var builder = org.adblockplus.libadblockplus.android.AdblockEngine.builder(
                context, appInfo,  folder.newFolder().absolutePath)
                .setDisableByDefault()
                .setHttpClient(anhttpclient)

        var adblockEngine = builder.build()
        assertFalse(adblockEngine.settings().isEnabled)
        assertFalse(adblockEngine.filterEngine.isEnabled)
        sleep(4_000)
        // should not download subscriptions if disabled by default
        verify(anhttpclient, times(0)).request(any(HttpRequest::class.java), any());

        // in case it re enables it should start the download
        adblockEngine.settings().edit().setEnabled(true).save()
        sleep(1_000)
        verify(anhttpclient, times(2)).request(any(HttpRequest::class.java), any());
    }

    @Test
    fun byDefaultShouldBeEnableAndDownloadSubscriptions() {

        var anhttpclient = mock(HttpClient::class.java)

        var builder = org.adblockplus.libadblockplus.android.AdblockEngine.builder(
                context, appInfo,  folder.newFolder().absolutePath)
                .setHttpClient(anhttpclient)

        var adblockEngine = builder.build()
        assertTrue(adblockEngine.settings().isEnabled)
        assertTrue(adblockEngine.filterEngine.isEnabled)
        sleep(1_000)
        // should download subscriptions if enable
        verify(anhttpclient, times(2)).request(any(HttpRequest::class.java), any());
    }

    @Test
    fun offAndOnShouldMatch() {

        var anhttpclient = mock(HttpClient::class.java)

        var builder = org.adblockplus.libadblockplus.android.AdblockEngine.builder(
                context, appInfo,  folder.newFolder().absolutePath)
                .setHttpClient(anhttpclient)
                .setDisableByDefault()

        var adblockEngine = builder.build()
        assertFalse(adblockEngine.settings().isEnabled)
        assertFalse(adblockEngine.filterEngine.isEnabled)

        var filter = adblockEngine.filterEngine.getFilterFromText("adbanner.gif")
        adblockEngine.filterEngine.addFilter(filter)

        var match: MatchesResult? = adblockEngine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), EMPTY_PARENT, "", false)
        // should allow
        assertTrue(match == null || match == MatchesResult.NOT_ENABLED)

        adblockEngine.settings().edit().setEnabled(true).save()
        assertTrue(adblockEngine.settings().isEnabled)
        assertTrue(adblockEngine.filterEngine.isEnabled)

        match = adblockEngine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), EMPTY_PARENT, "", false)

        assertTrue(match != null && match == MatchesResult.BLOCKED)
    }
}
