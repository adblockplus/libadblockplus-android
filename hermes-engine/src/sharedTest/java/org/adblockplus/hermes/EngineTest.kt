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

package org.adblockplus.hermes

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Collections

@Config(sdk = [21])
@RunWith(AndroidJUnit4::class)
class EngineTest {
    val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun evaluateJS() {
        val engine = Engine(application)
        val result = engine.evaluateJS("\"21\" + 21")
        Assert.assertEquals("We should be able to evaluate simple expressions", "2121", result)
    }

    @Test
    fun testMatches() {
        val engine = Engine(application)
        Assert.assertEquals(
            MatchesResult.NOT_FOUND,
            engine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), "", "", false))

        engine.evaluateJS("API.addFilter(\"adbanner.gif\");")

        Assert.assertEquals(
            MatchesResult.BLOCKED,
            engine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), "", "", false))

        engine.evaluateJS("API.removeFilter(\"adbanner.gif\");")

        Assert.assertEquals(
            MatchesResult.NOT_FOUND,
            engine.matches("http://example.org/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), "", "", false))
    }

    @Test
    fun testGetElementHidingStyleSheet() {
        val engine = Engine(application)

        Assert.assertEquals(
            "",
            engine.getElementHidingStyleSheet("foo.example.com", true))

        engine.evaluateJS("API.addFilter(\"foo.example.com##turnip\");")

        Assert.assertEquals(
            "turnip {display: none !important;}\n",
            engine.getElementHidingStyleSheet("foo.example.com", true))

        engine.evaluateJS("API.removeFilter(\"foo.example.com##turnip\");")

        Assert.assertEquals(
            "",
            engine.getElementHidingStyleSheet("foo.example.com", true))
    }

    @Test
    fun testGetElementHidingEmulationSelectors() {
        val engine = Engine(application)

        var list = engine.getElementHidingEmulationSelectors("example.org")
        Assert.assertEquals(0, list.size.toLong())

        engine.evaluateJS("API.addFilter(\"example.org#?#foo\");")
        engine.evaluateJS("API.addFilter(\"example.org#?#another\");")

        list = engine.getElementHidingEmulationSelectors("example.org")
        Assert.assertEquals(2, list.size.toLong())
        Assert.assertEquals(EmulationSelector("foo", "example.org#?#foo"), list[0])
        Assert.assertEquals(EmulationSelector("another", "example.org#?#another"), list[1])

        engine.evaluateJS("API.removeFilter(\"example.org#?#foo\");")
        engine.evaluateJS("API.removeFilter(\"example.org#?#another\");")

        list = engine.getElementHidingEmulationSelectors("example.org")
        Assert.assertEquals(0, list.size.toLong())
    }

    @Test
    fun testAllowlisting()
    {
        val engine = Engine(application)

        engine.evaluateJS("API.addFilter(\"##.testcase-generichide-generic\");")
        engine.evaluateJS("API.addFilter(\"example.org##.testcase-generichide-notgeneric\");")

        val url = "http://www.example.org"

        // before generichide option
        Assert.assertFalse(engine.isContentAllowlisted(url,
            ContentType.maskOf(ContentType.GENERICHIDE),
            Collections.singletonList(url), ""));

        // add filter with generichide option
        engine.evaluateJS("API.addFilter(\"@@||example.org\$generichide\");")

        Assert.assertTrue(engine.isContentAllowlisted(url,
            ContentType.maskOf(ContentType.GENERICHIDE),
            Collections.singletonList(url), ""));

        Assert.assertFalse(engine.isContentAllowlisted("$url/ad.html",
            ContentType.maskOf(ContentType.DOCUMENT), listOf("$url/ad.html"), ""))

        engine.evaluateJS("API.addFilter(\"@@||example.org^\$document\");")

        Assert.assertTrue(engine.isContentAllowlisted("$url/ad.html",
            ContentType.maskOf(ContentType.DOCUMENT), listOf("$url/ad.html"), ""))

        // Check sitekey
        val documentUrls = listOf(
            "http://example.com/",
            "http://ads.com/")
        val docSiteKey: String = "cNAQEBBQADSwAwSAJBAJRmzcpTevQqkWn6dJuX_document"

        Assert.assertFalse(engine.isContentAllowlisted("http://my-ads.com/adframe",
            ContentType.maskOf(ContentType.DOCUMENT), documentUrls, docSiteKey))

        engine.evaluateJS("API.addFilter(\"@@\$document,sitekey=$docSiteKey\");")

        Assert.assertFalse(engine.isContentAllowlisted("http://my-ads.com/adframe",
            ContentType.maskOf(ContentType.DOCUMENT), documentUrls, ""))

        Assert.assertTrue(engine.isContentAllowlisted("http://my-ads.com/adframe",
            ContentType.maskOf(ContentType.DOCUMENT), documentUrls, docSiteKey))
    }

    @Test
    fun testRealFilter()
    {
        val engine = Engine(application)

        Assert.assertEquals(
            MatchesResult.BLOCKED,
            engine.matches("http://example.org/ad/headercreative/adbanner.gif",
                ContentType.maskOf(ContentType.IMAGE), "", "", false))

        Assert.assertFalse(
            engine.getElementHidingStyleSheet("youtube.com", true).isEmpty())

        // Confirm exceptions were loaded
        Assert.assertTrue(engine.isContentAllowlisted("http://speedtest.net",
            ContentType.maskOf(ContentType.DOCUMENT), listOf("http://related.speedtest.net"), ""))
    }

    fun run(engine: Engine)
    {
        for (i in 1..100000) {
            Log.i("TEST","Loop: " + i);
            Assert.assertEquals(
                MatchesResult.BLOCKED,
                engine.matches("http://example.org/ad/headercreative/adbanner.gif",
                    ContentType.maskOf(ContentType.IMAGE), "", "", false))
            Assert.assertFalse(
                engine.getElementHidingStyleSheet("youtube.com", false).isEmpty())
            // Confirm exceptions were loaded
            Assert.assertTrue(engine.isContentAllowlisted("http://speedtest.net",
                ContentType.maskOf(ContentType.DOCUMENT), listOf("http://related.speedtest.net"), ""))
        }
    }

    @Test
    fun stressTestRealData() {
        val engine = Engine(application)
        val thread1 = Thread {
            run(engine)
        }
        val thread2 = Thread {
            run(engine)
        }
        val thread3 = Thread {
            run(engine)
        }
        thread1.start()
        thread2.start()
        thread3.start()
        thread1.join()
        thread2.join()
        thread3.join()
    }
}
