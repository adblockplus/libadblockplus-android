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

import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.web.sugar.Web
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.Notifier
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.adblockplus.libadblockplus.android.AdblockEngine
import org.adblockplus.libadblockplus.android.settings.AdblockHelper
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.adblockplus.libadblockplus.android.webview.WebViewActivity
import org.adblockplus.libadblockplus.android.webview.WebViewTestSuit
import org.adblockplus.libadblockplus.android.webview.autoDispose
import org.adblockplus.libadblockplus.android.webview.*
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration
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
import wiremock.org.apache.http.HttpStatus

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseAdblockWebViewTest {

    companion object {
        private const val sleepStepMillis = 100L
        private const val subscriptionsSleepTimeoutMillis = 5_000L

        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val context: Context = instrumentation.targetContext

        const val indexHtml = "index.html"

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            if (Timber.treeCount() == 0) {
                Timber.plant(DebugTree())
            }
        }

        private fun initAdblockProvider(basePath: String) {
            AdblockHelper.deinit()
            Timber.d("Initializing with basePath=$basePath")
            AdblockHelper
                .get()
                .init(context, basePath, true, AdblockHelper.PREFERENCE_NAME)
        }
    }

    data class WireMockReqResData(val urlPath: String,
                                  val responseBody: String = "",
                                  val statusCode: Int = HttpStatus.SC_OK,
                                  val contentType: String = "text/html")

    protected lateinit var indexPageUrl: String

    /**
     * Because we start with clearing subscriptions, isAcceptableAdsEnabled will always return false
     * An extractor from it's side checks if AA enabled, and ignores the request otherwise
     *
     * Thus, we have to mock SitekeyExtractor to always respond true to isEnabled request
     *
     * @param extractor an underlying SiteKeyExtractor that will respond to all proxy requests
     *
     * !NOTE! SiteKeyExtractor will be converted into an interface soon, so initializing
     * extra SiteKeyExtractor won't be required
     */
    private class AlwaysEnabledSitekeyExtractorDelegate(private val extractor: SiteKeyExtractor)
        : SiteKeyExtractor {

        override fun obtainAndCheckSiteKey(webView: AdblockWebView?, request: WebResourceRequest?): WebResourceResponse? {
            return extractor.obtainAndCheckSiteKey(webView, request)
        }

        override fun setSiteKeysConfiguration(siteKeysConfiguration: SiteKeysConfiguration?) {
            extractor.setSiteKeysConfiguration(siteKeysConfiguration)
        }

        override fun setEnabled(enabled: Boolean) {
            extractor.setEnabled(true) // always true
        }
    }

    @get:Rule
    val basePathRule = TemporaryFolder()

    @get:Rule
    val wireMockRule = WireMockRule(wireMockConfig().dynamicPort().notifier(object : Notifier {
        override fun info(message: String?) {
            Timber.i(message)
        }

        override fun error(message: String?) {
            Timber.e(message)
        }

        override fun error(message: String?, t: Throwable?) {
            Timber.e(t, message)
        }
    }))

    @get:Rule
    val activityRule = ActivityTestRule(WebViewActivity::class.java, false, true)

    protected lateinit var testSuitSystem: WebViewTestSuit<WebView>
    protected lateinit var testSuitAdblock: WebViewTestSuit<AdblockWebView>
    protected lateinit var adblockEngine: AdblockEngine

    @Before
    open fun setUp() {
        Timber.d("setUp()")
        // is required to be run NOT in @BeforeClass setUp to randomize paths
        initAdblockProvider(basePathRule.root.absolutePath)
        initAdblockTestSuit()
        initSystemTestSuit()
        adblockEngine = AdblockHelper.get().provider.engine
        waitForDefaultSubscriptions()
        clearSubscriptions()
        indexPageUrl = "${wireMockRule.baseUrl()}/${indexHtml}"
        Timber.d("setUp() finished")
    }

    protected open fun initHttpServer(reqResData: Array<WireMockReqResData>) {
        for (entry in reqResData) {
            // Important: This actually needs more advanced logic to handle properly other codes
            // than HttpStatus.SC_OK, but this is not needed now.
            wireMockRule
                .stubFor(WireMock.any(WireMock.urlPathEqualTo(entry.urlPath))
                    .willReturn(WireMock.aResponse()
                        .withStatus(entry.statusCode)
                        .withBody(entry.responseBody)
                        .withHeader("Content-Type", entry.contentType)))
        }

        // missing fav icon
        wireMockRule
            .stubFor(WireMock.any(WireMock.urlMatching("/favicon.ico"))
                .willReturn(WireMock.aResponse().withStatus(HttpStatus.SC_NOT_FOUND)))
    }

    protected fun onAdblockWebView() : Web.WebInteraction<Void> {
        return Web.onWebView(ViewMatchers.withContentDescription(WebViewActivity.ADBLOCK_WEBVIEW))
            .withNoTimeout()
    }

    protected fun initAdblockTestSuit() {
        testSuitAdblock = WebViewTestSuit()
        testSuitAdblock.webView = activityRule.activity.adblockWebView
        testSuitAdblock.setUp()
        testSuitAdblock.webView.siteKeyExtractor =
            AlwaysEnabledSitekeyExtractorDelegate(testSuitAdblock.webView.siteKeyExtractor)
    }

    protected fun initSystemTestSuit() {
        testSuitSystem = WebViewTestSuit()
        testSuitSystem.webView = activityRule.activity.webView
        testSuitSystem.setUp()
    }

    @After
    fun tearDown() {
        Timber.d("tearDown()")
        instrumentation.runOnMainSync {
            testSuitAdblock.tearDown()
            testSuitSystem.tearDown()
        }
        AdblockHelper.deinit()
        Timber.d("tearDown() finished")
    }

    protected fun addFilterRules(filterRules: List<String>) {
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
