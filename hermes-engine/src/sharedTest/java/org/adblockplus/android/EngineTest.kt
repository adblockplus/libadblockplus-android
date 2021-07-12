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

package org.adblockplus.android

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.adblockplus.ContentType
import org.adblockplus.EmulationSelector
import org.adblockplus.MatchesResult
import java.util.Collections
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [21])
@RunWith(AndroidJUnit4::class)
class EngineTest {
    val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun evaluateJS() {
        val engine = AdblockEngine(application)
        val result = engine.evaluateJS("\"21\" + 21")
        Assert.assertEquals("We should be able to evaluate simple expressions", "2121", result)
    }

    @Test
    fun testSetTimeout() {
        val engine = AdblockEngine(application)
        try {
            engine.evaluateJS("setTimeout()")
            fail("Should fail without arguments!")
        } catch (e: RuntimeException) {
            //We can check message here
        }
        try {
            engine.evaluateJS("setTimeout(\"not a function\")")
            fail("Should fail with wrong first argument!")
        } catch (e: RuntimeException) {
            //We can check message here
        }
        try {
            engine.evaluateJS("setTimeout(function() {}, \"not a number\")")
            fail("Should fail with wrong second argument!")
        } catch (e: RuntimeException) {
            //We can check message here
        }

        engine.evaluateJS("var wasCalled = false;" +
                "function setToTrue() { wasCalled = true; };" +
                "setTimeout(setToTrue, 100)")
        Thread.sleep(50)
        // Should be false as timeout didn't tick
        Assert.assertFalse(engine.evaluateJS("wasCalled").toBoolean())

        // setTimeout without args and 0 timeout
        engine.evaluateJS("var wasCalled = false;" +
                "function setToTrue() { wasCalled = true; };" +
                "setTimeout(setToTrue, 0)")
        Thread.sleep(10)
        Assert.assertTrue(engine.evaluateJS("wasCalled").toBoolean())

        // setTimeout without args
        engine.evaluateJS("var wasCalled = false;" +
                "function setToTrue() { wasCalled = true; };" +
                "setTimeout(setToTrue, 100)")
        Assert.assertFalse(engine.evaluateJS("wasCalled").toBoolean())
        Thread.sleep(120)
        Assert.assertTrue(engine.evaluateJS("wasCalled").toBoolean())

        // setTimeout with args
        engine.evaluateJS("var value = 0;" +
                "function plusPlus(arg1, arg2) { value = arg1 + arg2; };" +
                "setTimeout(plusPlus, 100, 2, 2)")
        Assert.assertEquals(0, engine.evaluateJS("value").toInt())
        Thread.sleep(120)
        Assert.assertEquals(4, engine.evaluateJS("value").toInt())
    }

    @Test
    fun testSetImmediate() {
        val engine = AdblockEngine(application)
        try {
            engine.evaluateJS("setImmediate()")
            fail("Should fail without arguments!")
        } catch (e: RuntimeException) {
            //We can check message here
        }
        try {
            engine.evaluateJS("setImmediate(\"not a function\")")
            fail("Should fail with wrong first argument!")
        } catch (e: RuntimeException) {
            //We can check message here
        }
        // setTimeout with args
        engine.evaluateJS("var value = 0;" +
                "function plusPlus(arg1, arg2) { value = arg1 + arg2; };" +
                "setImmediate(plusPlus, 2, 2)");
        Thread.sleep(50)
        val result2 = engine.evaluateJS("value").toInt()
        Assert.assertEquals(4, result2)
    }

    @Test
    fun testLog() {
        val engine = AdblockEngine(application)
        try {
            engine.evaluateJS("__log()")
            fail("Should fail without arguments!")
        } catch (e: RuntimeException) {
            //We can check message here
        }
        try {
            engine.evaluateJS("__log(0)")
            fail("Should fail with wrong first argument!")
        } catch (e: RuntimeException) {
            //We can check message here
        }
        try {
            engine.evaluateJS("__log(\"error\", 0)")
            fail("Should fail with wrong second argument!")
        } catch (e: RuntimeException) {
            //We can check message here
        }
        engine.evaluateJS("__log(\"error\",\"error message\")")
        engine.evaluateJS("console.log(\"info\",\"console.log message\")")
    }

    @Test
    fun testMatches() {
        val engine = AdblockEngine(application)
        Assert.assertEquals(
            MatchesResult.NOT_FOUND,
            engine.matches("http://example.org/ad_banner.gif",
                ContentType.maskOf(ContentType.IMAGE), "", "", false))

        engine.evaluateJS("API.addFilter(\"ad_banner.gif\");")

        Assert.assertEquals(
            MatchesResult.BLOCKED,
            engine.matches("http://example.org/ad_banner.gif",
                ContentType.maskOf(ContentType.IMAGE), "", "", false))

        engine.evaluateJS("API.removeFilter(\"ad_banner.gif\");")

        Assert.assertEquals(
            MatchesResult.NOT_FOUND,
            engine.matches("http://example.org/ad_banner.gif",
                ContentType.maskOf(ContentType.IMAGE), "", "", false))
    }

    @Test
    fun testGetElementHidingStyleSheet() {
        val engine = AdblockEngine(application)

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
        val engine = AdblockEngine(application)

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
    fun testCustomFilters() {
        val engine = AdblockEngine(application)

        assertEquals(MatchesResult.NOT_FOUND,
                engine.matches("http://example.org/foobar.gif",ContentType.maskOf(ContentType.IMAGE),"", "", false));

        engine._addCustomFilter("foobar.gif")
        assertEquals(MatchesResult.BLOCKED,
                engine.matches("http://example.org/foobar.gif",ContentType.maskOf(ContentType.IMAGE),"", "", false));

        engine._removeCustomFilter("foobar.gif")
        assertEquals(MatchesResult.NOT_FOUND,
                engine.matches("http://example.org/foobar.gif",ContentType.maskOf(ContentType.IMAGE),"", "", false));
    }


    @Test
    fun testAllowlisting()
    {
        val engine = AdblockEngine(application)

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
        val engine = AdblockEngine(application)

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

    fun run(engine: AdblockEngine)
    {
        for (i in 1..1000) {
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
        val engine = AdblockEngine(application)
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
