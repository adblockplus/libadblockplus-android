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

import androidx.test.espresso.web.sugar.Web.onWebView
import org.adblockplus.libadblockplus.android.webview.elementIsElemhidden
import org.adblockplus.libadblockplus.android.webview.elementIsNotElemhidden
import org.adblockplus.libadblockplus.android.webview.imageIsBlocked
import org.adblockplus.libadblockplus.android.webview.imageIsNotBlocked
import org.junit.Test

class AdblockWebViewTest : BaseAdblockWebViewTest() {

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

        onWebView()
            .withNoTimeout() // it's already loaded
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

        onWebView()
            .withNoTimeout() // it's already loaded
            .check(elementIsElemhidden(blockImageId))  // red image IS elemhidden (because blocked)
            .check(elementIsNotElemhidden(notBlockImageId)) // green image is NOT elemhidden
    }
}
