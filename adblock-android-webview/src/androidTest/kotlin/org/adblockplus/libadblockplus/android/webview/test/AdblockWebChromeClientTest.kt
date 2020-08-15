package org.adblockplus.libadblockplus.android.webview.test

import android.webkit.JsResult
import android.webkit.WebChromeClient
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.whenever
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.junit.Assert
import org.junit.Test
import com.nhaarman.mockitokotlin2.eq
import org.mockito.Mockito
import timber.log.Timber

class AdblockWebChromeClientTest : BaseAdblockWebViewTest() {

    @Test
    fun testOnJsAlert() {
        val extWebChromeClientAdblock = Mockito.mock(WebChromeClient::class.java)
        val extWebChromeClientSystem = Mockito.mock(WebChromeClient::class.java)

        // dismiss all the alerts
        whenever(extWebChromeClientAdblock
                .onJsAlert(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenAnswer {
                    Timber.i("onJsAlert() fired in extWebChromeClient")
                    it.getArgument<JsResult>(3)?.confirm()
                    true // the client handles alert itself (confirm called)
                }
        whenever(extWebChromeClientSystem
                .onJsAlert(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenAnswer {
                    Timber.i("onJsAlert() fired in extWebChromeClient")
                    it.getArgument<JsResult>(3)?.confirm()
                    true // the client handles alert itself (confirm called)
                }

        instrumentation.runOnMainSync {
            testSuitAdblock.webView.webChromeClient = extWebChromeClientAdblock
            testSuitSystem.webView.webChromeClient = extWebChromeClientSystem
        }

        val message = "Hello, world"
        val alertPage =
                """
                |<html>
                |<body>
                |  <script type="text/javascript">alert("$message");</script>
                |</body>
                |</html>
                |""".trimMargin()

        initHttpServer(arrayOf(WireMockReqResData("/${indexHtml}", alertPage)))

        Timber.d("Start loading...")
        Assert.assertTrue("${indexPageUrl} exceeded loading timeout", testSuitSystem.loadUrlAndWait(indexPageUrl))
        Assert.assertTrue("${indexPageUrl} exceeded loading timeout", testSuitAdblock.loadUrlAndWait(indexPageUrl))
        Timber.d("Loaded")

        verify(extWebChromeClientSystem, times(1)).onJsAlert(
                eq(testSuitSystem.webView), eq(indexPageUrl), eq(message), anyOrNull())

        verify(extWebChromeClientAdblock, times(1)).onJsAlert(
                eq(testSuitAdblock.webView), eq(indexPageUrl), eq(message), anyOrNull())
    }
}