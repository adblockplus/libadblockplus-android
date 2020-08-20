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

import android.os.Debug
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.adblockplus.libadblockplus.android.AdblockEngine
import org.adblockplus.libadblockplus.android.AndroidHttpClientResourceWrapper
import org.adblockplus.libadblockplus.android.SingleInstanceEngineProvider
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.adblockplus.libadblockplus.AppInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import timber.log.Timber
import timber.log.Timber.DebugTree

const val SLEEP_TIME_MILLI = 100L
const val COOL_OFF_DURATION_MILLI = 5000L

// Need Mockstorage to always say that a certain string(url)
// was never stored so it will always use the custom http server
class MockStorage : AndroidHttpClientResourceWrapper.Storage {
    override fun contains(url: String?) = false
    override fun put(url: String?) {
    }
}

abstract class BenchMarkMemory(subscriptionListResourceID: Int, exceptionListResourceID: Int) {
    @get:Rule
    val folder = TemporaryFolder()

    // mapped all subscription url to the given subscription list because the runner of the test
    // might run this in a different locale and this would result in a different easylist being
    // downloaded this way any of the subscription list download results in the injection of
    // our subscription list test file.
    val resourcesList = mapOf(
            // locale-specific
            AndroidHttpClientResourceWrapper.EASYLIST to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_INDONESIAN to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_BULGARIAN to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_CZECH_SLOVAK to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_DUTCH to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_GERMAN to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_ISRAELI to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_ITALIAN to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_LITHUANIAN to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_LATVIAN to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_ARABIAN_FRENCH to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_FRENCH to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_ROMANIAN to subscriptionListResourceID,
            AndroidHttpClientResourceWrapper.EASYLIST_RUSSIAN to subscriptionListResourceID,

            // AA
            AndroidHttpClientResourceWrapper.ACCEPTABLE_ADS to exceptionListResourceID
    )

    init {
        if (Timber.treeCount() == 0) {
            Timber.plant(DebugTree())
        }
    }

    @Test
    @Ignore
    fun benchmark() {
        var results = benchmarkFilterList(resourcesList, folder.newFolder().absolutePath)
        writeResultsOnFile(results)
    }

    private fun stampMemory(): MutableMap<String, String> {
        // calls gc to make results less volatile
        System.gc()
        val memInfoVar = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfoVar)
        // if using android studio profiler the total does not match the total pss,
        // the total in android studio is the total pss without private other and system accounted
        // for more information please read.
        // https://developer.android.com/reference/android/os/Debug.MemoryInfo
        val totalAccountedInAndroidStudio =
                memInfoVar.totalPss -
                        memInfoVar.getMemoryStat("summary.private-other").toDouble() -
                        memInfoVar.getMemoryStat("summary.system").toDouble()

        return mutableMapOf("TOTAL PSS (KB)" to memInfoVar.totalPss.toString(),
                "TOTAL_ANDROID_STUDIO (KB) " to totalAccountedInAndroidStudio.toString(),
                "CODE (KB) " to memInfoVar.getMemoryStat("summary.code"),
                "STACK (KB) " to memInfoVar.getMemoryStat("summary.stack"),
                "GRAPHICS (KB) " to memInfoVar.getMemoryStat("summary.graphics"),
                "JAVA-HEAP(KB) " to memInfoVar.getMemoryStat("summary.java-heap"),
                "NATIVE-HEAP(KB) " to memInfoVar.getMemoryStat("summary.native-heap"))
    }

    /**
     * Benchmarks memory by stamping the memory footprint at each stage of a normal
     * usage of adblockwebview.
     * First stamps init stage (without anything initialized)
     * Second stamps after engine is created.
     * Third stamps when all the filter lists are downloaded and ready
     * Fourth stamps when the first adblockwebview has loaded with about:blank
     * Fifth stamps after a cool off period of 5 seconds
     */
    private fun benchmarkFilterList(
            localResources: Map<String, Int>,
            folder: String) : Map<String, Map<String, String>> {

        var output = mutableMapOf<String, MutableMap<String, String>>()
        output["INIT STAGE"] = stampMemory()

        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

        val engineFactory = AdblockEngine
                .builder(AppInfo.builder().build(), folder)
                .preloadSubscriptions(context, localResources, MockStorage())
                .setForceUpdatePreloadedSubscriptions(false)
        val customEngineProvider = SingleInstanceEngineProvider(engineFactory)

        assertTrue(customEngineProvider.retain(false))
        output["ENGINE CREATED"] = stampMemory()
        assertNotNull(customEngineProvider.engine)

        // wait for all subscriptions to be loaded
        while (!customEngineProvider.engine.filterEngine.listedSubscriptions.all {
                    it.getProperty("downloadStatus")?.asString() == "synchronize_ok"
                }) {
            SystemClock.sleep(SLEEP_TIME_MILLI)
        }

        output["FILTER LOADED"] = stampMemory()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync{
            val webview = AdblockWebView(context)
            webview.setProvider(customEngineProvider)

            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    latch.countDown()
                    super.onPageFinished(view, url)
                }
            }
            webview.loadUrl("about:blank")
        }

        assertTrue(latch.await(1, TimeUnit.MINUTES))
        output["FIRST ADBLOCK WEBVIEW CREATED AND LOADED ABOUT:BLANK"] = stampMemory()
        // added last stage the cool off period, looking at some measurements throughout the test
        // it continues to change the memory usage after the loading of webview there is a pattern
        // of a short rise and fall after the 4 seconds mark.
        SystemClock.sleep(COOL_OFF_DURATION_MILLI)
        output["5 seconds cool off"] = stampMemory()
        return output
    }

    fun writeResultsOnFile(results: Map<String, Map<String, String>>) {

        var hasPrintedHeader = false
        val output = StringBuilder()
        val memoryValuesNames = arrayListOf<String>()
        for (stage in results) {
            if (!hasPrintedHeader) {
                output.append("STAGE , ")
                for (memoryValue in stage.value) {
                    memoryValuesNames.add(memoryValue.key)
                    output.append(memoryValue.key)
                    output.append(", ")
                }
                hasPrintedHeader = true
                output.append(System.lineSeparator())
            }
            output.append(stage.key)
            for (memoryValue in memoryValuesNames) {
                output.append(", ")
                output.append(stage.value[memoryValue])
            }
            output.append(System.lineSeparator())
        }
        // hardcoded because it is the same folder hardcoded in the host script
        // this is for readability.
        val file = File("/storage/emulated/0/Download/memory_benchmark.csv")
        if (!file.exists()) {
            file.createNewFile()
        }
        assertTrue(file.exists())
        file.writeText(output.toString())
    }
}

@Ignore
class MemoryBenchmark_20_full_AA : BenchMarkMemory(R.raw.easy_20, R.raw.exceptionrules)

@Ignore
class MemoryBenchmark_20_min_AA : BenchMarkMemory(R.raw.easy_20, R.raw.exceptionrules_min)

@Ignore
class MemoryBenchmark_50_full_AA : BenchMarkMemory(R.raw.easy_50, R.raw.exceptionrules)

@Ignore
class MemoryBenchmark_50_min_AA :
        BenchMarkMemory(R.raw.easy_50, R.raw.exceptionrules_min)
@Ignore
class MemoryBenchmark_80_full_AA :
        BenchMarkMemory(R.raw.easy_80, R.raw.exceptionrules)
@Ignore
class MemoryBenchmark_80_min_AA :
        BenchMarkMemory(R.raw.easy_80, R.raw.exceptionrules_min)
@Ignore
class MemoryBenchmark_full_easy_full_AA :
        BenchMarkMemory(R.raw.easylist, R.raw.exceptionrules)
@Ignore
class MemoryBenchmark_full_easy_min_AA :
        BenchMarkMemory(R.raw.easylist, R.raw.exceptionrules_min)
@Ignore
class MemoryBenchmark_minDist_full_AA :
        BenchMarkMemory(R.raw.easylist_min_uc, R.raw.exceptionrules)
class MemoryBenchmark_minDist_min_AA :
        BenchMarkMemory(R.raw.easylist_min_uc, R.raw.exceptionrules_min)
