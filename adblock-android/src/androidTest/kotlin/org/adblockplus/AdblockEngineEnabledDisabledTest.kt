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

/* Package is `fake` and does not match this test file location to use package-private method setHttpClientForTesting */
package org.adblockplus.libadblockplus.android

import androidx.test.platform.app.InstrumentationRegistry
import java.lang.Thread.sleep
import kotlin.collections.HashMap
import org.adblockplus.AdblockEngineFactory
import org.adblockplus.AppInfo
import org.adblockplus.ContentType
import org.adblockplus.MatchesResult
import org.adblockplus.libadblockplus.HttpClient
import org.adblockplus.libadblockplus.HttpRequest
import org.adblockplus.libadblockplus.test.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import timber.log.Timber

class AdblockEngineEnabledDisabledTest {
    val appInfo = AppInfo.builder().build()
    val context = InstrumentationRegistry.getInstrumentation().context
    val EMPTY_PARENT = ""

    @get:Rule
    val folder = TemporaryFolder()

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            initLogging()
        }

        private fun initLogging() {
            if (Timber.forest().isEmpty()) {
                Timber.plant(Timber.DebugTree())
            }
        }
    }

    @After
    fun tearDown() {
        Timber.d("tearDown()")
        AdblockEngineFactory.deinit()
    }

    @Test
    fun testDisabledByDefaultReturnsNotEnabled() {
        val adblockEngine = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder.disabledByDefault().build()
        assertFalse(adblockEngine.settings().isEnabled)

        val filter = adblockEngine.getFilterFromText("adbanner.gif")
        adblockEngine.settings().edit().addCustomFilter(filter).save()

        val match: MatchesResult? = adblockEngine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), EMPTY_PARENT, "", false)
        assertEquals(MatchesResult.NOT_ENABLED, match)
    }

    @Test
    fun offAndOnShouldMatch() {
        val adblockEngine = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder.disabledByDefault().build()
        assertFalse(adblockEngine.settings().isEnabled)

        val filter = adblockEngine.getFilterFromText("adbanner.gif")
        adblockEngine.settings().edit().addCustomFilter(filter).save()

        var match: MatchesResult? = adblockEngine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), EMPTY_PARENT, "", false)
        assertEquals(MatchesResult.NOT_ENABLED, match)

        adblockEngine.settings().edit().setEnabled(true).save()
        assertTrue(adblockEngine.settings().isEnabled)

        match = adblockEngine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), EMPTY_PARENT, "", false)
        assertEquals(MatchesResult.BLOCKED, match)
    }

    @Test
    fun testDisabledByDefaultDoesNotDownloadSubscriptions() {
        val httpClient = mock(HttpClient::class.java)
        val adblockEngineBuilder = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder
        (adblockEngineBuilder as AdblockEngineBuilder).setHttpClientForTesting(httpClient)
        val adblockEngine = adblockEngineBuilder.disabledByDefault().build()
        assertFalse(adblockEngine.settings().isEnabled)
        sleep(3_000)
        // should not download subscriptions if disabled by default
        assertEquals(0, adblockEngine.settings().listedSubscriptions.size)
        verify(httpClient, times(0)).request(any(HttpRequest::class.java), any())

        // in case it re enables it should start the download
        adblockEngine.settings().edit().setEnabled(true).save()
        sleep(1_000)
        assertEquals(2, adblockEngine.settings().listedSubscriptions.size)
        verify(httpClient, times(2)).request(any(HttpRequest::class.java), any())
    }

    @Test
    fun testDoesNotDownloadSubscriptionsButHasPreloaded() {
        var count = 0
        val urls: MutableList<String> = ArrayList()
        val httpClient = object : AndroidHttpClient() {
            override fun request(request: HttpRequest?, callback: Callback?) {
                ++count
                urls.add(request?.url.toString())
            }
        }
        val map: MutableMap<String, Int> = HashMap()
        map[AndroidHttpClientResourceWrapper.EASYLIST] = R.raw.easylist
        map[AndroidHttpClientResourceWrapper.ACCEPTABLE_ADS] = R.raw.exceptionrules
        val adblockEngineBuilder = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder
        (adblockEngineBuilder as AdblockEngineBuilder).setHttpClientForTesting(httpClient)
        val adblockEngine = adblockEngineBuilder.preloadSubscriptions(map, false).build()
        assertTrue(adblockEngine.settings().isEnabled)
        sleep(3_000)
        // should have preloaded subscriptions
        assertEquals(2, adblockEngine.settings().listedSubscriptions.size)
        assertEquals(0, count)
        assertEquals(0, urls.size)

        // let's force update and make sure now updates are fetched from the network
        adblockEngine.settings().listedSubscriptions.iterator().forEach {
            it.updateFilters()
        }
        sleep(1_000)
        assertEquals(2, count)
        assertEquals(2, urls.size)
        urls.forEach {
            assertTrue(it.contains("downloadCount=1"))
            assertTrue(it.contains("lastVersion=")) // we don't check lastVersion value as it may vary
        }
    }

    @Test
    fun testForceUpdatePreloadedSubscriptions() {
        val httpClient = mock(HttpClient::class.java)
        val map: MutableMap<String, Int> = HashMap()
        map[AndroidHttpClientResourceWrapper.EASYLIST] = R.raw.easylist
        map[AndroidHttpClientResourceWrapper.ACCEPTABLE_ADS] = R.raw.exceptionrules
        val adblockEngineBuilder = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder
        (adblockEngineBuilder as AdblockEngineBuilder).setHttpClientForTesting(httpClient)
        val adblockEngine = adblockEngineBuilder.preloadSubscriptions(map, true).build()
        assertTrue(adblockEngine.settings().isEnabled)
        sleep(1_000)
        // should have preloaded subscriptions
        assertEquals(2, adblockEngine.settings().listedSubscriptions.size)
        verify(httpClient, times(2)).request(any(HttpRequest::class.java), any())
    }

    @Test
    fun byDefaultShouldBeEnabledAndDownloadSubscriptions() {
        val httpClient = mock(HttpClient::class.java)
        val adblockEngineBuilder = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder
        (adblockEngineBuilder as AdblockEngineBuilder).setHttpClientForTesting(httpClient)
        val adblockEngine = adblockEngineBuilder.build()
        assertTrue(adblockEngine.settings().isEnabled)

        sleep(1_000)
        // should download subscriptions if enable
        assertEquals(2, adblockEngine.settings().listedSubscriptions.size)
        verify(httpClient, times(2)).request(any(HttpRequest::class.java), any())
    }
}
