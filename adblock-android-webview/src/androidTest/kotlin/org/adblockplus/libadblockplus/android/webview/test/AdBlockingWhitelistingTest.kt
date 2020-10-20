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

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.adblockplus.libadblockplus.HttpClient
import org.adblockplus.libadblockplus.android.AndroidHttpClient
import org.adblockplus.libadblockplus.android.Utils
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.adblockplus.libadblockplus.android.webview.BaseSiteKeyExtractor
import org.adblockplus.libadblockplus.android.webview.SiteKeyHelper
import org.adblockplus.libadblockplus.android.webview.elementIsElemhidden
import org.adblockplus.libadblockplus.android.webview.elementIsNotElemhidden
import org.adblockplus.libadblockplus.android.webview.escapeForRegex
import org.adblockplus.libadblockplus.android.webview.imageIsBlocked
import org.adblockplus.libadblockplus.android.webview.imageIsNotBlocked
import org.adblockplus.libadblockplus.security.SlowSignatureVerifierWrapper
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber
import wiremock.org.apache.http.HttpStatus
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore

class AdBlockingWhitelistingTest : BaseAdblockWebViewTest() {

    companion object {
        private const val png = "png"
        const val blockedImageWithSlashId = "blockedImageWithSlashId"
        const val blockedImageWithoutSlashId = "blockedImageWithoutSlashId"
        const val blockedImageWithAbsolutePathId = "blockedImageWithAbsolutePathId"
        const val notBlockedImageWithOverlappingPathId = "notBlockedImageWithOverlappingPathId"
        const val notBlockedImageId = "notBlockedImageId"
        const val greenImage = "green.$png"
        const val redImage = "red.$png"
        const val blockingPathPrefix = "blocking/"
        const val notMatchingBlockingPathPrefix = "not${blockingPathPrefix}"
        const val blockingPathFilter = "^blocking^"

        const val localhost = "127.0.0.1"
        const val sslPort = 8443
        const val certificatePassword = "abp"

        private val allowingSelfSignedCertificatesWebViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView,
                                            handler: SslErrorHandler,
                                            error: SslError) {
                Timber.w("Proceeding self-signed certificate anyway")
                handler.proceed()
            }
        }
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
        assertTrue("$indexPageUrl exceeded loading timeout",
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
                info: AdblockWebView.EventsListener.BlockedResourceInfo) {
                blockedResources.add(info)
            }

            override fun onResourceLoadingWhitelisted(
                info: AdblockWebView.EventsListener.WhitelistedResourceInfo) {
                whitelistedResources.add(info)
            }
        })
    }

    private fun initSitekey(pageUrl: String, verifyDelayMillis: Long? = null): Pair<String, String> {
        val siteKeyHelper = SiteKeyHelper()

        if (verifyDelayMillis != null) {
            val slowSignatureVerifier = SlowSignatureVerifierWrapper(
                verifyDelayMillis, siteKeyHelper.signatureVerifier)
            siteKeyHelper.signatureVerifier = slowSignatureVerifier
            siteKeyHelper.siteKeyVerifier = SiteKeyHelper.TestSiteKeyVerifier(
                slowSignatureVerifier, siteKeyHelper.publicKeyHolder, siteKeyHelper.base64Processor)
        }

        val userAgent = "someUserAgent"
        val pair = siteKeyHelper.buildXAdblockKeyValue(pageUrl, userAgent)
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
        return Pair(sitekey, signature)
    }

    override fun initHttpServer(reqResData: Array<WireMockReqResData>) {
        super.initHttpServer(reqResData)

        // green image
        initHttpGreenImage()

        // red image
        wireMockRule
            .stubFor(any(urlMatching(""".*${redImage.escapeForRegex()}"""))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_OK)
                    .withHeader("Content-Type", "image/png")
                    .withBody(Utils.toByteArray(context.assets.open("red.png")))))
    }

    private fun initHttpGreenImage(server: WireMockServer = wireMockRule) {
        server
            .stubFor(any(urlMatching(""".*${greenImage.escapeForRegex()}"""))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_OK)
                    .withHeader("Content-Type", "image/png")
                    .withBody(Utils.toByteArray(context.assets.open("green.png")))))
    }

    @Test
    fun testResourceLoading_imageIsBlocked() {
        load(
            listOf(blockingPathFilter),
            """
            |<html>
            |<body>
            |  <img id="$notBlockedImageWithOverlappingPathId"
            |    src="${notMatchingBlockingPathPrefix}$redImage"/>
            |  <img id="$blockedImageWithSlashId"
            |    src="/${blockingPathPrefix}$redImage"/>
            |  <img id="$blockedImageWithoutSlashId"
            |    src="${blockingPathPrefix}$redImage"/>
            |  <img id="$blockedImageWithAbsolutePathId"
            |    src="${wireMockRule.baseUrl()}/${blockingPathPrefix}$redImage"/>
            |  <img id="$notBlockedImageId" src="$greenImage"/>
            |</body>
            |</html>
            |""".trimMargin()
        )

        onAdblockWebView()
            .check(imageIsNotBlocked(notBlockedImageWithOverlappingPathId))
            .check(imageIsBlocked(blockedImageWithoutSlashId))
            .check(imageIsBlocked(blockedImageWithSlashId))
            .check(imageIsBlocked(blockedImageWithAbsolutePathId))
            .check(imageIsNotBlocked(notBlockedImageId))
    }

    @Test
    fun testElementHiding_blockedImageIsElementHidden() {
        load(
            listOf(blockingPathFilter),
            """
            |<html>
            |<body>
            |  <img id="$notBlockedImageWithOverlappingPathId"
            |    src="${notMatchingBlockingPathPrefix}$redImage"/>
            |  <img id="$blockedImageWithSlashId" src="/${blockingPathPrefix}$redImage"/>
            |  <img id="$blockedImageWithoutSlashId" src="${blockingPathPrefix}$redImage"/>
            |  <img id="$blockedImageWithAbsolutePathId"
            |    src="${wireMockRule.baseUrl()}/${blockingPathPrefix}$redImage"/>
            |</body>
            |</html>
            |""".trimMargin()
        )

        onAdblockWebView()
            .check(imageIsNotBlocked(notBlockedImageWithOverlappingPathId))
            .check(imageIsBlocked(blockedImageWithoutSlashId))
            .check(imageIsBlocked(blockedImageWithSlashId))
            .check(imageIsBlocked(blockedImageWithAbsolutePathId))
            .check(elementIsNotElemhidden(notBlockedImageWithOverlappingPathId))
            .check(elementIsElemhidden(blockedImageWithoutSlashId))
            .check(elementIsElemhidden(blockedImageWithSlashId))
            .check(elementIsElemhidden(blockedImageWithAbsolutePathId))
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
            |  <img id="$blockedImageWithSlashId" src="/$greenImage"/>
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

        onAdblockWebView()
            .check(imageIsBlocked(blockedImageWithSlashId)) // green image in main frame IS blocked
            // can't access subframe with JS so there is no [known at the moment] way
            // to assert subframe resource visibility
    }

    @Test
    fun testUrlFragmentDoesNotBreakReferrerChain() {
        val subFrameHtml = "subframe.html"

        // whitelist root frame url
        val whitelistingRule = "@@localhost"

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

        val (_, whitelistedResources) = load(
            listOf(whitelistingRule),
            """
            |<html>
            |<body>
            |  <iframe src="$subFrameHtml#fragment"/>
            |</body>
            |</html>
            |""".trimMargin())

        // subframe resource is whitelisted: it must have 2 referrers (subframe + main frame)
        assertNotNull(whitelistedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$greenImage" && it.parentFrameUrls.size == 2
        })
    }

    @Test
    fun testCrossOriginReferrers() {
        val subFrameHtml = "subframe.html"

        // whitelisting main frame
        val whitelistingRule = "@@$localhost\$subdocument,document"

        val selfSignedCertificateFile = extractCertificate()
        testSuitAdblock.extWebViewClient = allowingSelfSignedCertificatesWebViewClient

        val mainFramePort = sslPort
        val subFramePort = mainFramePort + 1

        val mainFrameServer = WireMockServer(wireMockConfig()
            .bindAddress(localhost)
            .dynamicPort()
            .notifier(timberNotifier)
            .httpsPort(mainFramePort)
            .keystorePath(selfSignedCertificateFile.absolutePath)
            .keystoreType(KeyStore.getDefaultType())
            .keystorePassword(certificatePassword)
        )

        val subFramePath = "$subFrameHtml?query=m"
        val subFrameUrl = "https://$localhost:$subFramePort/$subFramePath"
        mainFrameServer
            .stubFor(any(urlPathEqualTo("/$indexHtml"))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_OK)
                    .withHeader("Content-Type", HttpClient.MIME_TYPE_TEXT_HTML)
                    .withBody(
                        """
                        |<html>
                        |<body>
                        |  <iframe src="$subFrameUrl"/>
                        |</body>
                        |</html>
                        |""".trimMargin())))
        initHttpGreenImage(mainFrameServer)

        // since the port is different it will be another origin and cross origin interaction
        val subFrameServer = WireMockServer(wireMockConfig()
            .bindAddress(localhost)
            .dynamicPort()
            .notifier(timberNotifier)
            .httpsPort(subFramePort)
            .keystorePath(selfSignedCertificateFile.absolutePath)
            .keystoreType(KeyStore.getDefaultType())
            .keystorePassword(certificatePassword))

        val mainFrameResourceUrl = "https://$localhost:$mainFramePort/$greenImage"
        subFrameServer
            .stubFor(any(urlPathEqualTo("/$subFrameHtml"))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.SC_OK)
                    .withHeader("Content-Type", HttpClient.MIME_TYPE_TEXT_HTML)
                    .withHeader("Referrer-Policy", "strict-origin-when-cross-origin") // !
                    .withBody(
                        """
                        |<html>
                        |<body>
                        |  <img src="$mainFrameResourceUrl"/>
                        |</body>
                        |</html>
                        |""".trimMargin())))

        addFilterRules(listOf(whitelistingRule))

        val blockedResources = mutableListOf<AdblockWebView.EventsListener.BlockedResourceInfo>()
        val whitelistedResources = mutableListOf<AdblockWebView.EventsListener.WhitelistedResourceInfo>()
        subscribeToAdblockWebViewEvents(blockedResources, whitelistedResources)

        subFrameServer.start()
        mainFrameServer.start()

        try {
            Timber.d("Start loading...")
            assertTrue("${mainFrameServer.baseUrl()}/$indexHtml exceeded loading timeout",
                testSuitAdblock.loadUrlAndWait("${mainFrameServer.baseUrl()}/$indexHtml"))
            Timber.d("Loaded")

            // subframe resource is whitelisted
            assertNotNull(whitelistedResources.find {
                it.requestUrl == mainFrameResourceUrl && it.parentFrameUrls.size == 2
            })
        } finally {
            mainFrameServer.stop()
            subFrameServer.stop()
            selfSignedCertificateFile.delete()
        }
    }

    @Test
    fun testCrossOriginReferrers_realWorldCase() {
        // Warning: the test requires working internet connection and expected website content:
        // frame https://5290727.fls.doubleclick.net/.. requests resources from another origin
        // - https://adservice.google.com/ ...

        // whitelisting main frame
        val whitelistingRule = "@@localhost\$subdocument,document"

        val (_, whitelistedResources) = load(
            listOf(whitelistingRule),
            """
            |<html>
            |<body>
            |  <img id="$blockedImageWithSlashId" src="/$greenImage"/>
            |  <iframe src="https://5290727.fls.doubleclick.net/activityi;src=5290727;type=allpa0;cat=nyti-0;ord=1;num=4241703782611;gtm=2wg9u1;auiddc=875088139.1602152521;u4=;u5=undefined;u6=undefined;u7=awSL7zh9ur3vET0ZkRQj3y;u8=;u10=;u11=3;u12=100000005877499;u13=undefined;u14=undefined;u15=undefined;u16=nyt-vi;u17=https%3A%2F%2Fwww.nytimes.com%2F;~oref=https%3A%2F%2Fwww.nytimes.com%2F?"/>
            |</body>
            |</html>
            |""".trimMargin())

        // subframe resource is whitelisted
        assertNotNull(whitelistedResources.find {
            it.requestUrl.contains("adservice.google.com") && it.parentFrameUrls.size == 2
        })
    }

    private fun extractCertificate(): File {
        val selfSignedCertificateFile = File.createTempFile("abp", ".bks")
        selfSignedCertificateFile.delete()
        context.assets.open("abp.bks").copyTo(FileOutputStream(selfSignedCertificateFile))
        return selfSignedCertificateFile
    }

    @Test
    fun testWhitelistedSubframeResourceIsWhitelistedWithSitekey() {
        val blockingRule = greenImage
        val subFrameHtml = "subframe.html"
        val subFrameUrl = "${wireMockRule.baseUrl()}/$subFrameHtml"
        val (sitekey, signature) = initSitekey(subFrameUrl)

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
                        |<html.data-adblockkey="$signature">
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
            |  <img id="$blockedImageWithSlashId" src="/$greenImage"/>
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

        onAdblockWebView()
            .check(imageIsBlocked(blockedImageWithSlashId)) // green image in main frame IS blocked
            // can't access subframe with JS so there is no [known at the moment] way
            // to assert subframe resource visibility
    }

    @Test
    fun testWhitelistedMainFrameResourcesHeldUntilSiteKey() {
        val blockingRule = greenImage

        // A half of max delay ensures that resources are held and whitelisted after the sitekey
        // verification concludes.
        // If the resources are not held the default blocking rule will apply.
        val (sitekey, signature) = initSitekey(indexPageUrl,
                (BaseSiteKeyExtractor.RESOURCE_HOLD_MAX_TIME_MS / 2).toLong())

        // whitelist main frame with sitekey
        val whitelistingRule = "@@\$subdocument,document,sitekey=$sitekey"

        val (_, whitelistedResources) = load(
            listOf(blockingRule, whitelistingRule),
            """
            |<html data-adblockkey="$signature">
            |<body>
            |  <img id="$blockedImageWithSlashId" src="/$greenImage"/>
            |</body>
            |</html>
            |""".trimMargin())

        // main frame resource is NOT blocked (whitelisted)
        assertNotNull(whitelistedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$greenImage" && it.parentFrameUrls.size == 1
        })

        // green image in main frame is NOT blocked
        onAdblockWebView()
            .check(imageIsNotBlocked(blockedImageWithSlashId))
            .check(elementIsNotElemhidden(blockedImageWithSlashId))
    }

    @Test
    fun testSkipWhitelistingWhenSitekeyVerificationTimeouts() {
        val blockingRule = greenImage

        // Twice the max delay is enough to simulate the timeout of holding of the resource loading
        // before sitekey is extracted. The default rule is applied in this case.
        val (sitekey, signature) = initSitekey(indexPageUrl,
                (BaseSiteKeyExtractor.RESOURCE_HOLD_MAX_TIME_MS * 2).toLong())

        // whitelist main frame with sitekey
        val whitelistingRule = "@@\$subdocument,document,sitekey=$sitekey"

        val (blockedResources, whitelistedResources) = load(
            listOf(blockingRule, whitelistingRule),
            """
            |<html data-adblockkey="$signature">
            |<body>
            |  <img id="$blockedImageWithSlashId" src="/$greenImage"/>
            |</body>
            |</html>
            |""".trimMargin())

        // main frame resource is not whitelisted
        assertNull(whitelistedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$greenImage" && it.parentFrameUrls.size == 1
        })

        // main frame resource is blocked
        assertNotNull(blockedResources.find {
            it.requestUrl == "${wireMockRule.baseUrl()}/$greenImage" && it.parentFrameUrls.size == 1
        })

        // green image in main frame is blocked with the default blocking rule
        onAdblockWebView()
            .check(imageIsBlocked(blockedImageWithSlashId))
            .check(elementIsElemhidden(blockedImageWithSlashId))
    }
}
