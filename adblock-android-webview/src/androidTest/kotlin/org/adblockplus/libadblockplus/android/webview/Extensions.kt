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

import androidx.test.espresso.web.assertion.WebAssertion
import androidx.test.espresso.web.assertion.WebViewAssertions
import androidx.test.espresso.web.model.Atom
import androidx.test.espresso.web.model.Atoms
import org.adblockplus.libadblockplus.Disposable
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not

/*
    At the moment we check "naturalWidth" property of "<img" to check,
    if resource loading was blocked or not.
    Keep in sync with actual image dimension (currently 50x50).
 */
const val naturalWidth = "naturalWidth"
const val notBlockedImageWidth = 50
const val blockedImageWidth = 0

const val styleDisplay = "style.display"
const val none = "none"

fun getPropertyForElementId(elementId: String, property: String): Atom<String> =
    Atoms.script(
        """return String(document.getElementById("$elementId").$property);""",
        Atoms.castOrDie(String::class.java))

fun imageIsBlocked(elementId: String): WebAssertion<String>? = WebViewAssertions.webMatches(
    getPropertyForElementId(elementId, naturalWidth),
    equalTo(blockedImageWidth.toString())) {
    """An image with id="$elementId" IS expected to be blocked, but it's NOT."""
}

fun imageIsNotBlocked(elementId: String) = WebViewAssertions.webMatches(
    getPropertyForElementId(elementId, naturalWidth),
    equalTo(notBlockedImageWidth.toString())) {
    """An image with id="$elementId" is expected to be NOT blocked, but it IS."""
}

fun elementIsElemhidden(elementId: String) = WebViewAssertions.webMatches(
    getPropertyForElementId(elementId, styleDisplay),
    equalTo(none)) {
    """A node with id="$elementId" is expected to be elemhidden, but it is NOT."""
}

fun elementIsNotElemhidden(elementId: String) = WebViewAssertions.webMatches(
    getPropertyForElementId(elementId, styleDisplay),
    not(equalTo(none))) {
    """A node with id="$elementId" is NOT expected to be elemhidden, but it IS."""
}

fun String.escapeForRegex(): String = this.replace(".", "\\.")

fun <T : Disposable> T.autoDispose(block: (t: T) -> Unit) {
    try {
        block(this)
    } finally {
        dispose()
    }
}
