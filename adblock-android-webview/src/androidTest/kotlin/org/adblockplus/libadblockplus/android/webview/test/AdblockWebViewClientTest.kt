package org.adblockplus.libadblockplus.android.webview.test

import android.webkit.WebViewClient
import com.nhaarman.mockitokotlin2.anyOrNull
import org.mockito.Mockito.verify
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nhaarman.mockitokotlin2.eq
import org.junit.Before
import org.mockito.Mockito.times
import org.mockito.Mockito
import timber.log.Timber

class AdblockWebViewClientTest : BaseAdblockWebViewTest() {

    companion object {
        const val indexRedirectedHtml = "indexr.html"
    }

    protected lateinit var indexRedirectedPageUrl: String

    @Before
    override fun setUp() {
        Timber.d("setUp()")
        super.setUp()
        indexRedirectedPageUrl = "${wireMockRule.baseUrl()}/${indexRedirectedHtml}"
        Timber.d("setUp() finished")
    }

    @Test
    fun testOnPageStartedAndFinished() {
        val extWebViewClientAdblock = Mockito.mock(WebViewClient::class.java)
        val extWebViewClientSystem = Mockito.mock(WebViewClient::class.java)

        // `testSuit` is a WebViewClient itself to measure start/finish time.
        // So we have to make testSuit forward the calls (act as a wrapper) to our `extWebViewClient`
        // and register it not in `WebView` directly, but in `testSuit`.
        testSuitAdblock.extWebViewClient = extWebViewClientAdblock
        testSuitSystem.extWebViewClient = extWebViewClientSystem

        // If we redirect by "Location" response header and Http redirect code, then it won't work
        // because WireMock will simply return just final page without onPageStarted() being called
        // for initial url. The problem is that AndroidHttpClient is not used.
        // To workaround that we are redirecting by JS code within the page. The bad side is that
        // we will not find out any bug which can be introduced by our AndroidHttpClient.
        val redirectingPage = """
            |<html>
            |<body>
            |  Hello, redirecting...
            |  <script type="text/javascript">window.location.href="${indexRedirectedHtml}"</script>
            |</body>
            |</html>
            |""".trimMargin()

        val redirectedPage = """
            |<html>
            |<body>
            |  Hello, redirected!
            |</body>
            |</html>
            |""".trimMargin()

        initHttpServer(arrayOf(WireMockReqResData("/${indexHtml}", redirectingPage),
            WireMockReqResData("/${indexRedirectedHtml}", redirectedPage)))

        arrayOf(Pair(extWebViewClientSystem, testSuitSystem),
            Pair(extWebViewClientAdblock, testSuitAdblock)).forEach {

            Timber.d("Start loading...")
            assertTrue("${indexPageUrl} exceeded loading timeout",
                it.second.loadUrlAndWait(indexPageUrl))

            verify(it.first, times(1))
                .onPageStarted(eq(it.second.webView), eq(indexPageUrl), anyOrNull())
            verify(it.first, times(1))
                .onPageStarted(eq(it.second.webView), eq(indexRedirectedPageUrl), anyOrNull())
            verify(it.first, times(0))
                .onPageFinished(eq(it.second.webView), eq(indexPageUrl))
            verify(it.first, times(1))
                .onPageFinished(eq(it.second.webView), eq(indexRedirectedPageUrl))
        }
    }
}
