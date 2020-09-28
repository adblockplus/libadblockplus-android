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

import com.nhaarman.mockitokotlin2.*
import org.adblockplus.libadblockplus.android.webview.CombinedSiteKeyExtractor
import org.adblockplus.libadblockplus.android.webview.HttpHeaderSiteKeyExtractor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.argThat
import org.mockito.internal.util.reflection.FieldSetter
import org.mockito.Mockito.mock


class HttpHeaderSiteKeyExtractorTest : BaseAdblockWebViewTest() {

    @Test
    fun testHttpHeaderSiteKeyIsCalled() {
        initAdblockTestSuit()
        val mainFrameUrl = "/${indexHtml}"
        val subFramePath = "/subframe.html"
        val subFrameUrl = "${wireMockRule.baseUrl()}${subFramePath}"

        val mainFrameContent = """
            |<html>
            |<body>
            | <iframe src="${subFramePath}"/>
            |</body>
            |</html>
            |""".trimMargin()

        val subFrameContent = """
            |<html>
            |<body>
            | this is a subframe
            |</body>
            |</html>
            |""".trimMargin()

        initHttpServer(arrayOf(WireMockReqResData(mainFrameUrl, mainFrameContent),
                WireMockReqResData("${subFramePath}", subFrameContent)))

        val sitekeyExtractorMock = mock(HttpHeaderSiteKeyExtractor::class.java)

        adblockEngine.isAcceptableAdsEnabled = true

        val delegateSiteKey =
                ((testSuitAdblock.webView.siteKeyExtractor as AlwaysEnabledSitekeyExtractor)
                        .extractor as AlwaysEnabledSitekeyExtractor)
        // need to inject it to mock it
        FieldSetter.setField(delegateSiteKey.extractor as CombinedSiteKeyExtractor,
                CombinedSiteKeyExtractor::class.java.getDeclaredField("httpExtractor"),
                sitekeyExtractorMock)

        assertTrue(testSuitAdblock.loadUrlAndWait(indexPageUrl))
        //test that is called only one time
        verify(sitekeyExtractorMock, times(1)).obtainAndCheckSiteKey(any(), any())
        // test that when it is called with subframe url one time
        verify(sitekeyExtractorMock, times(1)).obtainAndCheckSiteKey(any(),
                argThat { it.url.toString() == subFrameUrl }
        )
    }
}
