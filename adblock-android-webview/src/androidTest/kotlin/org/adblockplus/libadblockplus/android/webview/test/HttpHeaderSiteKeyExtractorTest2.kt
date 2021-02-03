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

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.whenever
import org.adblockplus.libadblockplus.AdblockPlusException
import org.adblockplus.libadblockplus.HeaderEntry
import org.adblockplus.libadblockplus.HttpClient
import org.adblockplus.libadblockplus.HttpClient.MIME_TYPE_TEXT_HTML
import org.adblockplus.libadblockplus.ServerResponse
import org.adblockplus.libadblockplus.android.AndroidHttpClient
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.adblockplus.libadblockplus.android.webview.AdblockWebView.WebResponseResult
import org.adblockplus.libadblockplus.android.webview.HttpHeaderSiteKeyExtractor
import org.adblockplus.libadblockplus.android.webview.SiteKeyHelper
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import timber.log.Timber

typealias ResponseInitializer = ServerResponse.() -> Unit

class HttpHeaderSiteKeyExtractorTest2 {

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

        private val MIME_TYPES_BINARY = arrayOf(
                "image/jpeg",
                "application/octet-stream",
                "video/mp4",
                "font/woff",
                "audio/mpeg"
        )
        private const val RESPONSE_BODY = "hello, world"
        private const val UTF_8 = "utf-8"
        private val uri = Uri.parse("http://domain.com")
        private val siteKeyHelper = SiteKeyHelper()
    }

    private val adblockWebView = Mockito.mock(AdblockWebView::class.java)
    private lateinit var extractor: HttpHeaderSiteKeyExtractor

    @Before
    fun setUp() {
        initExtractor()
    }

    private fun initExtractor() {
        extractor = HttpHeaderSiteKeyExtractor(adblockWebView)
        extractor.setSiteKeysConfiguration(
            SiteKeysConfiguration(
                siteKeyHelper.signatureVerifier,
                siteKeyHelper.publicKeyHolder,
                AndroidHttpClient(),
                siteKeyHelper.siteKeyVerifier
            )
        )
    }

    private fun initWithHttpClient(httpClient: HttpClient) {
        extractor.setSiteKeysConfiguration(
            SiteKeysConfiguration(
                siteKeyHelper.signatureVerifier,
                siteKeyHelper.publicKeyHolder,
                httpClient,
                siteKeyHelper.siteKeyVerifier
            )
        )
    }

    private fun extract(initializer: ResponseInitializer): WebResourceResponse {
        initWithResponse(initializer)

        val request = Mockito.mock(WebResourceRequest::class.java)
        whenever(request.method).thenReturn(HttpClient.REQUEST_METHOD_GET)
        whenever(request.url).thenReturn(uri)

        return extractor.extract(request)
    }

    private fun initWithResponse(initializer: ResponseInitializer) {
        val httpClient = Mockito.mock(HttpClient::class.java)
        whenever(httpClient.request(anyOrNull(), anyOrNull())).thenAnswer {
            val response = ServerResponse()
            initializer(response)
            (it.arguments[1] as HttpClient.Callback).onFinished(response)
        }
        initWithHttpClient(httpClient)
    }

    @Test
    fun testAllowIfDisabled() {
        extractor.isEnabled = false

        val request = Mockito.mock(WebResourceRequest::class.java)
        whenever(request.method).thenReturn(HttpClient.REQUEST_METHOD_GET)
        whenever(request.url).thenReturn(uri)
        val response = extractor.extract(request)

        assertEquals(WebResponseResult.ALLOW_LOAD, response)
    }

    @Test
    fun testAllowIfNotGETRequest() {
        fun test(httpMethod: String) {
            val request = Mockito.mock(WebResourceRequest::class.java)
            whenever(request.method).thenReturn(httpMethod)
            val response = extractor.extract(request)
            assertEquals(WebResponseResult.ALLOW_LOAD, response)
        }

        // all methods except GET
        test(HttpClient.REQUEST_METHOD_PUT)
        test(HttpClient.REQUEST_METHOD_POST)
        test(HttpClient.REQUEST_METHOD_HEAD)
        test(HttpClient.REQUEST_METHOD_OPTIONS)
        test(HttpClient.REQUEST_METHOD_DELETE)
        test(HttpClient.REQUEST_METHOD_TRACE)
    }

    @Test
    fun testAllowIfInvalidCode() {
        initWithResponse {
            responseStatus = 700 // invalid!
        }

        val request = Mockito.mock(WebResourceRequest::class.java)
        whenever(request.method).thenReturn(HttpClient.REQUEST_METHOD_GET)
        whenever(request.url).thenReturn(uri)
        val response = extractor.extract(request)

        assertEquals(WebResponseResult.ALLOW_LOAD, response)
    }

    @Test
    fun testAllowIfABPException() {
        val throwingHttpClient = Mockito.mock(HttpClient::class.java)
        whenever(throwingHttpClient.request(anyOrNull(), anyOrNull()))
            .thenThrow(AdblockPlusException("TestException"))
        initWithHttpClient(throwingHttpClient)

        val request = Mockito.mock(WebResourceRequest::class.java)
        whenever(request.method).thenReturn(HttpClient.REQUEST_METHOD_GET)
        whenever(request.url).thenReturn(uri)
        val response = extractor.extract(request)

        assertEquals(WebResponseResult.ALLOW_LOAD, response)
    }

    @Test
    fun testAllowIfNullInputStream() {
        initWithResponse {
            status = ServerResponse.NsStatus.OK
            responseStatus = HttpClient.STATUS_CODE_OK
            inputStream = null // null!
        }

        val request = Mockito.mock(WebResourceRequest::class.java)
        whenever(request.method).thenReturn(HttpClient.REQUEST_METHOD_GET)
        whenever(request.url).thenReturn(uri)
        val response = extractor.extract(request)

        assertEquals(WebResponseResult.ALLOW_LOAD, response)
    }

    @Test
    fun testMimeTypeHeaderIsRemovedAfterProcessed() {
        fun test(headers: List<HeaderEntry>): WebResourceResponse {
            val response = extract {
                responseHeaders = headers
                responseStatus = HttpClient.STATUS_CODE_OK
                inputStream = RESPONSE_BODY.byteInputStream()
            }

            assertFalse(response.responseHeaders.containsKey(HttpClient.HEADER_CONTENT_TYPE)) // removed
            return response
        }

        test(listOf(
            HeaderEntry(HttpClient.HEADER_CONTENT_TYPE, MIME_TYPE_TEXT_HTML)
        ))

        val response = test(listOf(
            HeaderEntry(HttpClient.HEADER_CONTENT_TYPE, MIME_TYPE_TEXT_HTML),
            HeaderEntry(HttpClient.HEADER_SITEKEY, "someSiteKey")
        ))
        assertTrue(response.responseHeaders.containsKey(HttpClient.HEADER_SITEKEY)) // still present
    }

    @Test
    fun testEncodingIsRemovedForBinaryTypes() {
        fun test(contentTypeHeader: String, testedResultMime: String) {
            val response = extract {
                responseHeaders = listOf(
                    HeaderEntry(HttpClient.HEADER_CONTENT_TYPE, contentTypeHeader)
                )
                responseStatus = HttpClient.STATUS_CODE_OK
                inputStream = RESPONSE_BODY.byteInputStream()
            }
            assertEquals(testedResultMime, response.mimeType) // set explicitly
            assertNull("Response encoding is not set to `null` for ${response.mimeType}",
                    response.encoding) // removed!
        }

        for (mime: String in MIME_TYPES_BINARY) {
            test("$mime; charset=utf-8", mime)
            test("$mime;", mime)
            test(mime, mime)
        }

    }

    @Test
    fun testMimeTypeIsSetToDefaultIfContentLengthIsInvalid() {
        fun test(contentLength: String) = extract {
            responseHeaders = listOf(
                HeaderEntry(HttpClient.HEADER_CONTENT_LENGTH, contentLength)
            )
            responseStatus = HttpClient.STATUS_CODE_OK
            inputStream = RESPONSE_BODY.byteInputStream()
        }

        // invalid "content-length" header
        val response1 = test("invalidInteger") // invalid
        assertNotNull(response1.mimeType)
        assertEquals(WebResponseResult.RESPONSE_MIME_TYPE, response1.mimeType)

        // valid "content-length" header
        val response2 = test(1024.toString()) // valid
        assertNull(response2.mimeType)
    }

    @Test
    fun testTrim() {
        fun test(contentType: String) {
            val response = extract {
                responseHeaders = listOf(
                    HeaderEntry(HttpClient.HEADER_CONTENT_TYPE, contentType)
                )
                responseStatus = HttpClient.STATUS_CODE_OK
                inputStream = RESPONSE_BODY.byteInputStream()
            }
            assertEquals(MIME_TYPE_TEXT_HTML, response.mimeType)
            assertEquals(UTF_8, response.encoding)
        }
        test("$MIME_TYPE_TEXT_HTML; charset=$UTF_8")
        test(" $MIME_TYPE_TEXT_HTML; charset=$UTF_8")
        test("$MIME_TYPE_TEXT_HTML; charset=$UTF_8 ")
        test(" $MIME_TYPE_TEXT_HTML;    charset=$UTF_8 ")
    }
}
