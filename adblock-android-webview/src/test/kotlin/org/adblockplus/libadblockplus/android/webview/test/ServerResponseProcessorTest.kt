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

import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.whenever
import org.adblockplus.libadblockplus.HttpClient
import org.adblockplus.libadblockplus.ServerResponse
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.adblockplus.libadblockplus.android.webview.HttpHeaderSiteKeyExtractor.ServerResponseProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URL

class ServerResponseProcessorTest {

    class TestServerResponseProcessorForUpdateCSP : ServerResponseProcessor() {
        fun updateCspHeaderWrapper(responseHeaders: MutableMap<String, String>?): String? {
            return super.updateCspHeader(responseHeaders)
        }
    }

    class TestServerResponseProcessorForInject : ServerResponseProcessor() {

        var updateCspHeaderReturnValue: String? = null

        override fun updateCspHeader(responseHeaders: MutableMap<String, String>?): String? {
            return updateCspHeaderReturnValue
        }

        fun injectJavascriptWrapper(webView: AdblockWebView, requestUrl: String,
                                    response: ServerResponse,
                                    responseHeaders: Map<String, String>): Boolean? {
            return super.injectJavascript(webView, requestUrl, response, responseHeaders);
        }
    }

    class TestServerResponseProcessorForReadFileToString : ServerResponseProcessor() {
        fun readFileToStringWrapper(inputStream: InputStream): String {
            return super.readFileToString(inputStream)
        }
    }

    private fun getFileFromPath(fileName: String): File? {
        val classLoader = this.javaClass.classLoader
        val resource: URL = classLoader!!.getResource(fileName)
        return File(resource.getPath())
    }

    @Test
    fun testReadFileToString() {
        val testServerResponseProcessor = TestServerResponseProcessorForReadFileToString()
        val testFile = getFileFromPath("script.js")
        val expectedString = testFile!!.readText()
        assertEquals(expectedString, testServerResponseProcessor.readFileToStringWrapper(
            testFile!!.inputStream()))
    }

    @Test
    fun testInjectJavascript() {
        val TEST_URL = "testUrl.html" // value not important here at all
        val EMPTY_INJECT_JS = "empty" // value not important just checked for presence
        val TEST_NONCE = "testNonce" // value not important just checked for presence
        val nonceReturnValues = arrayOf(null, "$TEST_NONCE")
        val generateStylesheetReturnValues = arrayOf(false, true)

        val testServerResponseProcessor = TestServerResponseProcessorForInject()

        // Test scenario 1: (byte input stream)
        // - generateStylesheetForUrl() is not called
        // - No changes in HTML due to no body content (bytes not chars)
        var adblockWebViewMock = Mockito.mock(AdblockWebView::class.java)
        whenever(adblockWebViewMock.generateStylesheetForUrl(anyOrNull(), anyOrNull())).thenAnswer {
            fail("generateStylesheetForUrl() should not be called!")
        }
        whenever(adblockWebViewMock.getInjectJs()).thenAnswer {
            fail("getInjectJs() should not be called!")
        }
        val inputBytes = byteArrayOf(0xA, 0xB, 0xC, 0xD, 0xE, 0xF)
        val serverResponseRaw = ServerResponse()
        serverResponseRaw.inputStream = ByteArrayInputStream(inputBytes)
        testServerResponseProcessor.injectJavascriptWrapper(
            adblockWebViewMock, TEST_URL, serverResponseRaw, mutableMapOf())?.let { assertTrue(it) }
        val outputBytes= serverResponseRaw.inputStream.readBytes()
        serverResponseRaw.getInputStream().close()
        inputBytes.forEach {
            assertTrue(outputBytes.contains(it))
        }

        // Test scenario 2: (page with no </body>)
        // - generateStylesheetForUrl() is not called
        // - No changes in HTML due to no </body> tag
        val inputHtmlNoBody = "<html><head></head></html>"
        val expectedHtmlNoBody = inputHtmlNoBody
        adblockWebViewMock = Mockito.mock(AdblockWebView::class.java)
        whenever(adblockWebViewMock.generateStylesheetForUrl(anyOrNull(), anyOrNull())).thenAnswer {
            fail("generateStylesheetForUrl() should not be called!")
        }
        whenever(adblockWebViewMock.getInjectJs()).thenAnswer {
            fail("getInjectJs() should not be called!")
        }
        val inputStreamNoBody: InputStream = inputHtmlNoBody.byteInputStream()
        val serverResponseNoBody = ServerResponse()
        serverResponseNoBody.inputStream = inputStreamNoBody
        testServerResponseProcessor.injectJavascriptWrapper(
            adblockWebViewMock, TEST_URL, serverResponseNoBody, mutableMapOf())?.let { assertTrue(it) }

        assertEquals(expectedHtmlNoBody, serverResponseNoBody.getInputStream().reader().readText())
        serverResponseNoBody.getInputStream().close()

        // Test scenario 3: (page with </body>)
        // - No changes in HTML when generateStylesheetForUrl() returns false
        // - Otherwise JS is injected with or without nonce depending on updateCspHeader() response
        val inputHtmlWithBody = "<html><head></head><body></body></html>"
        adblockWebViewMock = Mockito.mock(AdblockWebView::class.java)
        whenever(adblockWebViewMock.getInjectJs()).thenAnswer {
            "$EMPTY_INJECT_JS"
        }
        generateStylesheetReturnValues.forEach { generateStylesheetReturnValue ->
            whenever(adblockWebViewMock.generateStylesheetForUrl(anyOrNull(), anyOrNull())).thenAnswer {
                generateStylesheetReturnValue
            }
            nonceReturnValues.forEach { noncewReturnValue ->
                testServerResponseProcessor.updateCspHeaderReturnValue = noncewReturnValue

                val inputStreamWithBody: InputStream = inputHtmlWithBody.byteInputStream()
                val serverResponseWithBody = ServerResponse()
                serverResponseWithBody.inputStream = inputStreamWithBody
                testServerResponseProcessor.injectJavascriptWrapper(
                    adblockWebViewMock, TEST_URL, serverResponseWithBody, mutableMapOf())?.let { assertTrue(it) }

                val expectedHtmlWithBody =
                    if (generateStylesheetReturnValue == false)
                        "<html><head></head><body></body></html>"
                    else if (noncewReturnValue == null)
                        "<html><head></head><body><script>$EMPTY_INJECT_JS</script></body></html>"
                    else
                        "<html><head></head><body><script nonce=\"$TEST_NONCE\">$EMPTY_INJECT_JS</script></body></html>"
                assertEquals(expectedHtmlWithBody, serverResponseWithBody.getInputStream().reader().readText())
                serverResponseWithBody.getInputStream().close()
            }
        }
    }

    @Test
    fun testUpdateCSPheader() {
        val NONCE_PREFIX = "nonce"
        // Test data
        val HEADER_VALUE_NO_SCRIPT_SRC = "default-src * blob: data:;"
        val HEADER_VALUE_VALID_UNSAFE_INLINE = "default-src * blob: data:; script-src 'unsafe-inline'"
        val HEADER_VALUE_INVALID_UNSAFE_INLINE_NO_SCRIPT_SRC = "default-src * blob: data:; style-src 'unsafe-inline'"
        val HEADER_VALUE_INVALID_UNSAFE_INLINE_PLUS_SCRIPT_SRC = "default-src * blob: data:; style-src 'unsafe-inline script-src *'"
        val HEADER_VALUE_NO_NONCE = "default-src * blob: data:; script-src 'sha256-5CxqAdDXlHviOy7zxeRpMobzRK/JNpLvkS+k8Zj3L3A= blob: https://some.domain.dot.com;"
        val NONCE = "$NONCE_PREFIX-+B+pU8gwIWUuPqY2HDc1xA"
        val HEADER_VALUE_WITH_OBJECT_SRC_NONCE = "default-src * blob: data:; object-src '$NONCE';"
        val HEADER_VALUE_WITH_SCRIPT_SRC_NONCE = "default-src * blob: data:; script-src 'report-sample' '$NONCE' 'unsafe-eval';object-src 'none';"
        val HEADER_VALUE_WITH_SCRIPT_SRC_NONCE_AND_UNSAFE_INLINE = "default-src * blob: data:; script-src 'report-sample' '$NONCE' 'unsafe-inline';object-src 'none';"
        val SCRIPT_NONCE = "${NONCE}script"
        val OBJECT_NONCE = "${NONCE}object"
        val HEADER_VALUE_WITH_OBJECT_NONCE_AND_SCRIPT_NONCE = "default-src * blob: data:; object-src '$OBJECT_NONCE';script-src 'report-sample' '$SCRIPT_NONCE' 'unsafe-eval';"
        val HEADER_VALUE_WITH_SCRIPT_NONCE_AND_OBJECT_NONCE = "default-src * blob: data:; script-src 'report-sample' '$SCRIPT_NONCE' 'unsafe-eval';object-src '$OBJECT_NONCE';"

        val testServerResponseProcessor = TestServerResponseProcessorForUpdateCSP()
        val emptyHeaderMap : MutableMap<String, String> = mutableMapOf()
        var headerMapWithCSP : MutableMap<String, String> = mutableMapOf(HttpClient.HEADER_CSP to "")
        var returnNonce : String?

        // Verify no CSP header
        assertNull(testServerResponseProcessor.updateCspHeaderWrapper(emptyHeaderMap))
        assertTrue(emptyHeaderMap.isEmpty())

        // Verify empty CSP header
        testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertTrue(emptyHeaderMap.isEmpty())

        // Verify CSP header without script-src
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_NO_SCRIPT_SRC)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertNull(returnNonce)
        assertEquals(HEADER_VALUE_NO_SCRIPT_SRC, headerMapWithCSP.get(HttpClient.HEADER_CSP))

        // Verify CSP header with applicable 'unsafe-inline'
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_VALID_UNSAFE_INLINE)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertNull(returnNonce)

        // Verify CSP header with non applicable 'unsafe-inline' and no script-src
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_INVALID_UNSAFE_INLINE_NO_SCRIPT_SRC)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertNull(returnNonce)
        assertEquals(HEADER_VALUE_INVALID_UNSAFE_INLINE_NO_SCRIPT_SRC,
            headerMapWithCSP.get(HttpClient.HEADER_CSP))

        // Verify CSP header with non applicable 'unsafe-inline' and script-src
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_INVALID_UNSAFE_INLINE_PLUS_SCRIPT_SRC)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertNotNull(returnNonce)
        var expectedValue = HEADER_VALUE_INVALID_UNSAFE_INLINE_PLUS_SCRIPT_SRC
            .replace("script-src", "script-src '$NONCE_PREFIX-$returnNonce'")
        assertEquals(expectedValue, headerMapWithCSP.get(HttpClient.HEADER_CSP))

        // Verify CSP header without script-src nonce
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_NO_NONCE)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertNotNull(returnNonce)
        expectedValue = HEADER_VALUE_NO_NONCE.replace("script-src",
            "script-src '$NONCE_PREFIX-$returnNonce'")
        assertEquals(expectedValue, headerMapWithCSP.get(HttpClient.HEADER_CSP))

        // Verify CSP header with object-src nonce
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_WITH_OBJECT_SRC_NONCE)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertNull(returnNonce)
        assertEquals(HEADER_VALUE_WITH_OBJECT_SRC_NONCE, headerMapWithCSP.get(HttpClient.HEADER_CSP))

        // Verify CSP header with script-src nonce
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_WITH_SCRIPT_SRC_NONCE)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertNotNull(returnNonce)
        assertEquals(HEADER_VALUE_WITH_SCRIPT_SRC_NONCE, headerMapWithCSP.get(HttpClient.HEADER_CSP))

        // Verify CSP header with script-src nonce and applicable 'unsafe-inline'
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_WITH_SCRIPT_SRC_NONCE_AND_UNSAFE_INLINE)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertNotNull(returnNonce)
        assertEquals(HEADER_VALUE_WITH_SCRIPT_SRC_NONCE_AND_UNSAFE_INLINE,
            headerMapWithCSP.get(HttpClient.HEADER_CSP))

        // Verify CSP header with object-src nonce followed by script-src nonce
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_WITH_OBJECT_NONCE_AND_SCRIPT_NONCE)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertEquals(SCRIPT_NONCE, "$NONCE_PREFIX-$returnNonce")
        assertEquals(HEADER_VALUE_WITH_OBJECT_NONCE_AND_SCRIPT_NONCE,
            headerMapWithCSP.get(HttpClient.HEADER_CSP))

        // Verify CSP header with script-src nonce followed by object-src nonce
        headerMapWithCSP.set(HttpClient.HEADER_CSP, HEADER_VALUE_WITH_SCRIPT_NONCE_AND_OBJECT_NONCE)
        returnNonce = testServerResponseProcessor.updateCspHeaderWrapper(headerMapWithCSP)
        assertEquals(SCRIPT_NONCE, "$NONCE_PREFIX-$returnNonce")
        assertEquals(HEADER_VALUE_WITH_SCRIPT_NONCE_AND_OBJECT_NONCE,
            headerMapWithCSP.get(HttpClient.HEADER_CSP))
    }
}
