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

import android.webkit.JsResult
import android.webkit.WebChromeClient
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.whenever
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.junit.Assert.assertTrue
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
        arrayOf(extWebChromeClientSystem, extWebChromeClientAdblock).forEach {
            whenever(
                it.onJsAlert(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenAnswer {
                Timber.i("onJsAlert() fired in extWebChromeClient")
                it.getArgument<JsResult>(3)?.confirm()
                true // the client handles alert itself (confirm called)
            }
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

        arrayOf(Pair(extWebChromeClientSystem, testSuitSystem),
            Pair(extWebChromeClientAdblock, testSuitAdblock)).forEach {

            Timber.d("Start loading...")
            assertTrue("${indexPageUrl} exceeded loading timeout",
                it.second.loadUrlAndWait(indexPageUrl))

            verify(it.first, times(1)).onJsAlert(
                eq(it.second.webView), eq(indexPageUrl), eq(message), anyOrNull())
        }
    }
}
