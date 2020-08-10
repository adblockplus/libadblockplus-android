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

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.adblockplus.libadblockplus.HttpClient
import org.adblockplus.libadblockplus.android.AdblockEngine
import org.adblockplus.libadblockplus.android.Utils
import org.adblockplus.libadblockplus.android.settings.AdblockHelper
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.adblockplus.libadblockplus.android.webview.WebViewActivity
import org.adblockplus.libadblockplus.android.webview.WebViewTestSuit
import org.adblockplus.libadblockplus.android.webview.autoDispose
import org.adblockplus.libadblockplus.android.webview.escapeForRegex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import timber.log.Timber.DebugTree

abstract class BaseAdblockWebViewTest {

    companion object {
        private const val sleepStepMillis = 100L
        private const val subscriptionsSleepTimeoutMillis = 5_000L
        const val indexHtml = "index.html"
        private const val png = "png"
        const val blockImageId = "blockImageId"
        const val notBlockImageId = "notBlockImageId"
        const val greenImage = "green.$png"
        const val redImage = "red.$png"

        protected val instrumentation = InstrumentationRegistry.getInstrumentation()
        protected val context = instrumentation.targetContext

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            Timber.plant(DebugTree())
        }

        private fun initAdblockProvider(basePath: String) {
            AdblockHelper.deinit()
            Timber.d("Initializing with basePath=$basePath")
            AdblockHelper
                .get()
                .init(context, basePath, true, AdblockHelper.PREFERENCE_NAME)
        }
    }

    @get:Rule
    val basePathRule = TemporaryFolder()

    @get:Rule
    val wireMockRule = WireMockRule(wireMockConfig().dynamicPort())

    @get:Rule
    val activityRule = ActivityTestRule(WebViewActivity::class.java, false, true)

    protected lateinit var testSuit: WebViewTestSuit<AdblockWebView>
    protected lateinit var adblockEngine: AdblockEngine

    @Before
    fun setUp() {
        Timber.d("setUp()")
        // is required to be run NOT in @BeforeClass setUp to randomize paths
        initAdblockProvider(basePathRule.root.absolutePath)
        initTestSuit()
        adblockEngine = AdblockHelper.get().provider.engine
        Timber.d("setUp() finished")
    }

    private fun initTestSuit() {
        testSuit = WebViewTestSuit()
        testSuit.webView = activityRule.activity.adblockWebView
        testSuit.setUp()
    }

    @After
    fun tearDown() {
        Timber.d("tearDown()")
        instrumentation.runOnMainSync {
            testSuit.tearDown()
        }
        AdblockHelper.deinit()
        Timber.d("tearDown() finished")
    }

    protected fun load(filterRules: List<String>, content: String) {
        waitForDefaultSubscriptions()
        clearSubscriptions()
        addFilterRules(filterRules)
        initHttpServer(content)

        Timber.d("Start loading...")
        testSuit.loadUrlAndWait("${wireMockRule.baseUrl()}/$indexHtml")
        Timber.d("Loaded")
    }

    private fun initHttpServer(content: String) {
        wireMockRule
            .stubFor(any(urlPathEqualTo("/$indexHtml"))
            .willReturn(aResponse()
                .withStatus(HttpClient.STATUS_CODE_OK)
                .withHeader("Content-Type", "text/html")
                .withBody(content)))

        // missing fav icon
        wireMockRule
            .stubFor(any(urlMatching("/favicon.ico"))
            .willReturn(aResponse()
                .withStatus(404)))

        // green image
        wireMockRule
            .stubFor(any(urlMatching(""".*${greenImage.escapeForRegex()}"""))
            .willReturn(aResponse()
                .withStatus(HttpClient.STATUS_CODE_OK)
                .withHeader("Content-Type", "image/png")
                .withBody(Utils.toByteArray(context.assets.open("green.png")))))

        // red image
        wireMockRule
            .stubFor(any(urlMatching(""".*${redImage.escapeForRegex()}"""))
            .willReturn(aResponse()
                .withStatus(HttpClient.STATUS_CODE_OK)
                .withHeader( "Content-Type", "image/png")
                .withBody(Utils.toByteArray(context.assets.open("red.png")))))
    }

    private fun addFilterRules(filterRules: List<String>) {
        filterRules.forEach { filterText ->
            val filter = adblockEngine.filterEngine.getFilter(filterText)
            filter.autoDispose {
                it.addToList()
            }
        }
    }

    private fun waitForDefaultSubscriptions() {
        var slept = 0L
        while (adblockEngine.filterEngine.listedSubscriptions.size != 2) { // 2 = locale + AA
            Timber.d("Waiting for default subscriptions ready")
            SystemClock.sleep(sleepStepMillis)
            slept += sleepStepMillis
            if (slept >= subscriptionsSleepTimeoutMillis) {
                fail("Default subscriptions ready timeout ($subscriptionsSleepTimeoutMillis ms)")
            }
        }
    }

    private fun clearSubscriptions() {
        assertNotEquals(0, adblockEngine.filterEngine.listedSubscriptions.size)
        adblockEngine.filterEngine.listedSubscriptions.forEach { subscription ->
            subscription.autoDispose {
                it.removeFromList()
            }
        }
        assertEquals(0, adblockEngine.filterEngine.listedSubscriptions.size)
    }
}
