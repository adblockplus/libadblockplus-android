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
package org.adblockplus.libadblockplus.android.webview

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout


/**
 * An activity to host AdblockWebView
 */
class WebViewActivity : Activity() {

    companion object {
        const val SYSTEM_WEBVIEW = "adblock"
        const val ADBLOCK_WEBVIEW = "system"
    }

    lateinit var webView: WebView
    lateinit var adblockWebView: AdblockWebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true) // allow devtools connection
        val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.layoutParams = layoutParams
        webView = WebView(this)
        webView.contentDescription = SYSTEM_WEBVIEW
        layout.addView(webView)
        adblockWebView = AdblockWebView(this)
        adblockWebView.contentDescription = ADBLOCK_WEBVIEW
        layout.addView(adblockWebView)
        setContentView(layout)
    }
}
