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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.adblockplus.libadblockplus.android.AndroidHttpClient
import org.adblockplus.libadblockplus.android.Utils
import org.adblockplus.libadblockplus.android.webview.*
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Test
import timber.log.Timber
import wiremock.org.apache.http.HttpStatus

class AdBlockingWhitelistingTest : BaseAdblockWebViewTest() {

    companion object {
        private const val png = "png"
        const val blockImageId = "blockImageId"
        const val notBlockImageId = "notBlockImageId"
        const val greenImage = "green.$png"
        const val redImage = "red.$png"
    }

    private fun load(filterRules: List<String>, content: String):
            Pair<List<AdblockWebView.EventsListener.BlockedResourceInfo>,
                    List<AdblockWebView.EventsListener.WhitelistedResourceInfo>> {

        addFilterRules(filterRules)
        initHttpServer(arrayOf(WireMockReqResData("/${indexHtml}", content)))

        val blockedResources = mutableListOf<AdblockWebView.EventsListener.BlockedResourceInfo>()
        val whitelistedResources = mutableListOf<AdblockWebView.EventsListener.WhitelistedResourceInfo>()
        subscribeToAdblockWebViewEvents(blockedResources, whitelistedResources)

        Timber.d("Start loading...")
        Assert.assertTrue("$indexPageUrl exceeded loading timeout",
                testSuitAdblock.loadUrlAndWait(indexPageUrl))
        Timber.d("Loaded")

        return Pair(blockedResources, whitelistedResources)
    }

    private fun subscribeToAdblockWebViewEvents(
            blockedResources: MutableList<AdblockWebView.EventsListener.BlockedResourceInfo>,
            whitelistedResources: MutableList<AdblockWebView.EventsListener.WhitelistedResourceInfo>) {
        testSuitAdblock.webView.setEventsListener(object : AdblockWebView.EventsListener {
            override fun onNavigation() {
                // nothing
            }

            override fun onResourceLoadingBlocked(
                    info:AdblockWebView.EventsListener.BlockedResourceInfo) {
                blockedResources.add(info)
            }

            override fun onResourceLoadingWhitelisted(
                    info: AdblockWebView.EventsListener.WhitelistedResourceInfo) {
                whitelistedResources.add(info)
            }
        })
    }

    override fun initHttpServer(reqResData: Array<WireMockReqResData>) {
        super.initHttpServer(reqResData)

        // green image
        wireMockRule
                .stubFor(any(WireMock.urlMatching(""".*${greenImage.escapeForRegex()}"""))
                        .willReturn(aResponse()
                                .withStatus(HttpStatus.SC_OK)
                                .withHeader("Content-Type", "image/png")
                                .withBody(Utils.toByteArray(context.assets.open("green.png")))))

        // red image
        wireMockRule
                .stubFor(any(WireMock.urlMatching(""".*${redImage.escapeForRegex()}"""))
                        .willReturn(aResponse()
                                .withStatus(HttpStatus.SC_OK)
                                .withHeader( "Content-Type", "image/png")
                                .withBody(Utils.toByteArray(context.assets.open("red.png")))))
    }

    @Test
    fun testResourceLoading_imageIsBlocked() {
        val blockingPathPrefix = "/blocking/"

        load(
            listOf(blockingPathPrefix),
            """
            |<html>
            |<body>
            |  <img id="$blockImageId" src="${blockingPathPrefix}$redImage"/>
            |  <img id="$notBlockImageId" src="$greenImage"/>
            |</body>
            |</html>
            |""".trimMargin()
        )

        getAdblockWebView()
            .check(imageIsBlocked(blockImageId))       // red image IS blocked
            .check(imageIsNotBlocked(notBlockImageId)) // green image is NOT blocked
    }

    @Test
    fun testElementHiding_blockedImageIsElementHidden() {
        val blockingPathPrefix = "/blocking/"

        load(
            listOf(blockingPathPrefix),
            """
            |<html>
            |<body>
            |  <img id="$blockImageId" src="${blockingPathPrefix}$redImage"/>
            |  <img id="$notBlockImageId" src="$greenImage"/>
            |</body>
            |</html>
            |""".trimMargin()
        )

        getAdblockWebView()
            .check(elementIsElemhidden(blockImageId))  // red image IS elemhidden (because blocked)
            .check(elementIsNotElemhidden(notBlockImageId)) // green image is NOT elemhidden
    }

    @Test
    fun testWhitelistedSubframeResourceIsWhitelistedWithPath() {
        val blockingRule = greenImage
        val subFrameHtml = "subframe.html"

        // whitelist with path
        val whitelistingRule = "@@$subFrameHtml\$subdocument,document"

        wireMockRule
            .stubFor(any(urlPathEqualTo("/$subFrameHtml"))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_OK)
                    .withHeader("Content-Type", "text/html")
                    .withBody(
                        """
                        |<html>
                        |<body>
                        |  <img src="$greenImage"/>
                        |</body>
                        |</html>
                        |""".trimMargin())))

        val (blockedResources, whitelistedResources) = load(
            listOf(blockingRule, whitelistingRule),
            """
            |<html>
            |<body>
            |  <img id="$blockImageId" src="/$greenImage"/>
            |  <iframe src="$subFrameHtml"/>
            |</body>
            |</html>
            |""".trimMargin())

        // main frame resource is blocked
        assertNotNull(blockedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$greenImage" && it.parentFrameUrls.size == 1
        })

        // subframe itself is whitelisted
        assertNotNull(whitelistedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$subFrameHtml" && it.parentFrameUrls.size == 1
        })

        // subframe resource is whitelisted
        assertNotNull(whitelistedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$greenImage" && it.parentFrameUrls.size == 2
        })

        getAdblockWebView()
            .check(imageIsBlocked(blockImageId)) // green image in main frame IS blocked
            // can't access subframe with JS so there is no [known at the moment] way
            // to assert subframe resource visibility
    }

    @Test
    fun testWhitelistedSubframeResourceIsWhitelistedWithSitekey() {
        val blockingRule = greenImage
        val subFrameHtml = "subframe.html"

        val siteKeyHelper = SiteKeyHelper()
        val subFrameUrl = "${wireMockRule.baseUrl()}/$subFrameHtml"
        val userAgent = "someUserAgent"
        val pair = siteKeyHelper.buildXAdblockKeyValue(subFrameUrl, userAgent)
        val publicKey = PublicKeyHolderImpl.stripPadding(pair.first) // stripping '==' at the end
        val sitekey = publicKey
        val signature = pair.second

        instrumentation.runOnMainSync {
            testSuitAdblock.webView.settings.userAgentString = userAgent
            testSuitAdblock.webView.siteKeysConfiguration = SiteKeysConfiguration(
                siteKeyHelper.signatureVerifier,
                siteKeyHelper.publicKeyHolder,
                AndroidHttpClient(),
                siteKeyHelper.siteKeyVerifier)
            testSuitAdblock.webView.siteKeysConfiguration.forceChecks = true
        }

        // whitelist with sitekey
        val whitelistingRule = "@@\$subdocument,document,sitekey=$sitekey"

        wireMockRule
            .stubFor(any(urlPathEqualTo("/$subFrameHtml"))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_OK)
                    .withHeader("Content-Type", "text/html")
                    .withHeader("X-Adblock-key", signature) // sitekey
                    .withBody(
                        """
                        |<html>
                        |<body>
                        |  <img src="$greenImage"/>
                        |</body>
                        |</html>
                        |""".trimMargin())))

        val (blockedResources, whitelistedResources) = load(
            listOf(blockingRule, whitelistingRule),
            """
            |<html>
            |<body>
            |  <img id="$blockImageId" src="/$greenImage"/>
            |  <iframe src="$subFrameHtml"/>
            |</body>
            |</html>
            |""".trimMargin())

        // main frame resource is blocked
        assertNotNull(blockedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$greenImage" && it.parentFrameUrls.size == 1
        })

        // subframe itself is not whitelisted with sitekey:
        // the decision (allow/block) is made before the resource is loaded
        // and sitekey value is passed in resource response headers/html body

        // subframe resource is whitelisted
        assertNotNull(whitelistedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$greenImage" && it.parentFrameUrls.size == 2
        })

        getAdblockWebView()
            .check(imageIsBlocked(blockImageId)) // green image in main frame IS blocked
            // can't access subframe with JS so there is no [known at the moment] way
            // to assert subframe resource visibility
    }
}
