package org.adblockplus.libadblockplus.android.webview.test

import androidx.test.platform.app.InstrumentationRegistry
import org.adblockplus.libadblockplus.android.AndroidBase64Processor
import org.adblockplus.libadblockplus.android.settings.AdblockHelper
import org.adblockplus.libadblockplus.android.webview.RequestInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestInterceptorTest : BaseAdblockWebViewTest() {

    companion object {
        private const val SCHEMA = "http://"
        const val BASE_URL = SCHEMA + RequestInterceptor.DEBUG_URL_HOSTNAME + "/"
        const val ADD_URL = BASE_URL + RequestInterceptor.COMMAND_STRING_ADD
        const val REMOVE_URL = BASE_URL + RequestInterceptor.COMMAND_STRING_REMOVE
        const val CLEAR_URL = BASE_URL + RequestInterceptor.COMMAND_STRING_CLEAR
        const val BASE64_QUERY_PARAM_NAME = "/?" + RequestInterceptor.PAYLOAD_QUERY_PARAMETER_KEY + "="

        private const val PLAIN_URL_BASE = "data:" + RequestInterceptor.RESPONSE_MIME_TYPE + ","
        const val RESPONSE_INVALID_COMMAND = PLAIN_URL_BASE + RequestInterceptor.COMMAND_STRING_INVALID_COMMAND
        const val RESPONSE_INVALID_PAYLOAD = PLAIN_URL_BASE + RequestInterceptor.COMMAND_STRING_INVALID_PAYLOAD
        const val RESPONSE_OK = PLAIN_URL_BASE + RequestInterceptor.COMMAND_STRING_OK
    }

    @Test
    fun testInvalidCommand() {
        assertTrue("$BASE_URL exceeded loading timeout", testSuitAdblock.loadUrlAndWait(BASE_URL, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_INVALID_COMMAND)
        }
    }

    @Test
    fun testAddRemoveErrorWithoutPayload() {
        assertTrue("$ADD_URL exceeded loading timeout", testSuitAdblock.loadUrlAndWait(ADD_URL, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_INVALID_PAYLOAD)
        }
        assertTrue("$REMOVE_URL exceeded loading timeout", testSuitAdblock.loadUrlAndWait(REMOVE_URL, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_INVALID_PAYLOAD)
        }
    }

    @Test
    fun testAddRemoveClearFilter() {
        val base64 = AndroidBase64Processor()

        val filters = "/adv?banners=\n/ad_iframe/*\$domain=~convert-video-online.com|~online-audio-converter.com"
        val addUrl = ADD_URL + BASE64_QUERY_PARAM_NAME + base64.encodeToString(filters.toByteArray())

        assertEquals(AdblockHelper.get().provider.engine.filterEngine.listedFilters.size, 0)

        // Test add
        assertTrue("$addUrl exceeded loading timeout", testSuitAdblock.loadUrlAndWait(addUrl, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_OK)
        }
        assertEquals(AdblockHelper.get().provider.engine.filterEngine.listedFilters.size, 2)

        // Test duplicate
        assertTrue("$addUrl exceeded loading timeout", testSuitAdblock.loadUrlAndWait(addUrl, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_OK)
        }
        assertEquals(AdblockHelper.get().provider.engine.filterEngine.listedFilters.size, 2)

        val filter = "/adv?banners="

        // Test remove
        val removeUrl = REMOVE_URL + BASE64_QUERY_PARAM_NAME + base64.encodeToString(filter.toByteArray())
        assertTrue("$removeUrl exceeded loading timeout", testSuitAdblock.loadUrlAndWait(removeUrl, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_OK)
        }
        assertEquals(AdblockHelper.get().provider.engine.filterEngine.listedFilters.size, 1)

        // Test repeated remove
        assertTrue("$removeUrl exceeded loading timeout", testSuitAdblock.loadUrlAndWait(removeUrl, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_OK)
        }
        assertEquals(AdblockHelper.get().provider.engine.filterEngine.listedFilters.size, 1)

        // Test duplicate
        assertTrue("$addUrl exceeded loading timeout", testSuitAdblock.loadUrlAndWait(addUrl, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_OK)
        }
        assertEquals(AdblockHelper.get().provider.engine.filterEngine.listedFilters.size, 2)

        // Test clear
        assertTrue("$CLEAR_URL exceeded loading timeout", testSuitAdblock.loadUrlAndWait(CLEAR_URL, ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(testSuitAdblock.webView.originalUrl, RESPONSE_OK)
        }
        assertEquals(AdblockHelper.get().provider.engine.filterEngine.listedFilters.size, 0)
    }

}
