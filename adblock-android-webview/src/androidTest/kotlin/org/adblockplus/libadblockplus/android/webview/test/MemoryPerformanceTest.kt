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

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.adblockplus.AppInfo
import org.adblockplus.libadblockplus.android.AdblockEngine
import org.adblockplus.libadblockplus.android.AdblockEngineProvider
import org.adblockplus.libadblockplus.android.AndroidHttpClientResourceWrapper
import org.adblockplus.libadblockplus.android.SingleInstanceEngineProvider
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val SLEEP_TIME_MILLI = 100L
const val COOL_OFF_DURATION_MILLI = 5000L

// Need Mockstorage to always say that a certain string(url)
// was never stored so it will always use the custom http server
class MockStorage : AndroidHttpClientResourceWrapper.Storage {
    override fun contains(url: String?) = false
    override fun put(url: String?) {
    }
}

/**
 * Describes try stage
 *
 * We measure memory on concrete points of time during the initialization lifecycle
 * Init Engine --> FE loaded --> about:blank loaded etc
 *
 * Those are stages used currently:
 * 1 = INIT STAGE
 * 2 = ENGINE CREATED
 * 3 = FILTER LOADED
 * 4 = FIRST ADBLOCK WEBVIEW CREATED AND LOADED ABOUT:BLANK
 * 5 = 5 seconds cool off
 *
 * This applies only to memory measurement
 */
data class MemoryBenchmarkTryStage(
        /**
         * A number/id of a stage
         * This is essentially an index from 1 to N
         * The first stage should name it equal to 1, the next = 2
         */
        val id: Int,
        /**
         * The name of the stage
         */
        val name: String
) {

    override fun toString(): String =
            """
            |{
            |"id": $id,
            |"name": "$name"
            |}
            """.trimMargin()
}

/**
 * Describes the filter lists characteristics
 */
data class MemoryBenchmarkList(
        /**
         * name of the list, eg `easylist`
         */
        val name: String,
        /**
         * If minification applied to the list
         * this shows how much were cut
         * This is an external value that should be provided outside from the filter list team
         */
        val minification_coefficient: Int,

        /**
         * Size of a list in bytes
         */
        val file_size_b: Int
) {
    override fun toString(): String =
            """
            |{
            |"name": "$name",
            |"minification_coef": $minification_coefficient,
            |"file_size_kb": $file_size_b
            |}
            """.trimMargin()
}

/**
 * Describes tries -- every single measurement
 * Runs contain several tries (usually 10)
 *
 * The data reports of the try results
 */
data class MemoryBenchmarkTry(
        val stage: MemoryBenchmarkTryStage,
        val total_pss: Int,
        val total_android_studio: Int,
        val code: Int,
        val stack: Int,
        val graphics: Int,
        val java_heap: Int,
        val native_heap: Int
) {
    override fun toString(): String =
            """
            |{
            |"stage": $stage,
            |"total_pss": $total_pss,
            |"total_android_studio": $total_android_studio,
            |"code": $code,
            |"stack": $stack,
            |"graphics": $graphics,
            |"java_heap": $java_heap,
            |"native_heap": $native_heap
            |}
            """.trimMargin()
}

/**
 * Represents a single run of a test
 * Runs contain tries
 */
data class MemoryBenchmarkRun(
        val run_unit: String,
        /**
         * If Acceptable Ads were enabled during the run
         * Providing no lists is not the same as disabling AA
         */
        val is_aa_enabled: Boolean,
        /**
         * Was the adblock enabled during the run
         * Providing no lists is not the same as disabling ad blocker
         */
        val is_adblock_enabled: Boolean,
        /**
         * Time the run took in ns
         */
        val total_run_time_ns: Long,
        /**
         * Filter lists used for running
         */
        val lists: List<MemoryBenchmarkList>,
        /**
         * Tries (attempts/measurements)
         */
        val tries: List<MemoryBenchmarkTry>
) {
    override fun toString(): String =
            """
            |{
            |"run_unit": "$run_unit",
            |"is_aa_enabled": $is_aa_enabled,
            |"is_adblock_enabled": $is_adblock_enabled,
            |"total_run_time_ns": $total_run_time_ns,
            |"lists": $lists,
            |"tries": $tries
            |}
            """.trimMargin()
}

abstract class BenchmarkMemory {

    @get:Rule
    val folder = TemporaryFolder()

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    // `getArguments()` returns `Bundle` that cannot cast from string to int despite the value is an int
    val numTries: Int =  InstrumentationRegistry.getArguments().getString("num_tries", "1").toInt()

    init {
        if (Timber.treeCount() == 0) {
            Timber.plant(DebugTree())
        }
    }

    @LargeTest
    @Test
    abstract fun benchmark()

    protected fun stampMemory(stageName: String, stageId: Int, engineProvider: AdblockEngineProvider?): MemoryBenchmarkTry {
        // calls gc to make results less volatile
        System.gc()

        // calling V8's garbage collector
        engineProvider?.onLowMemory()

        // measure; at this point GC usually frees up to ~70% of memory,
        // also making measurements less volatile, so its suggested to use this measurement
        // as a reference
        val memInfoVar = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfoVar)
        // if using android studio profiler the total does not match the total pss,
        // the total in android studio is the total pss without private other and system accounted
        // for more information please read.
        // https://developer.android.com/reference/android/os/Debug.MemoryInfo
        val totalAccountedInAndroidStudio =
                memInfoVar.totalPss -
                        memInfoVar.getMemoryStat("summary.private-other").toInt() -
                        memInfoVar.getMemoryStat("summary.system").toInt()

        return MemoryBenchmarkTry(MemoryBenchmarkTryStage(stageId, stageName),
                memInfoVar.totalPss,
                totalAccountedInAndroidStudio,
                memInfoVar.getMemoryStat("summary.code").toInt(),
                memInfoVar.getMemoryStat("summary.stack").toInt(),
                memInfoVar.getMemoryStat("summary.graphics").toInt(),
                memInfoVar.getMemoryStat("summary.java-heap").toInt(),
                memInfoVar.getMemoryStat("summary.native-heap").toInt())
    }

    fun writeResultsOnFile(results: MemoryBenchmarkRun) {
        /*
        We append results to a memory_benchmark.json, therefore we have to manually modify json service chars
        in every next write

        1. When the file is empty, we write `[]`
        2. On every next write we read the whole json into a string
        3. Remove the last `]` char
        4. If this is not the first write, we also add a comma to append the next object (`},`)
        5. Add new content and write the file
         */

        // hardcoded because it is the same folder hardcoded in the host script
        // this is for readability.
        val file = File("/storage/emulated/0/Download/memory_benchmark.json")
        if (!file.exists()) {
            file.createNewFile()
            file.writeText("[]") // write empty array that we will extend every run
            // IMPORTANT: no new line at the end
        }
        assertTrue(file.exists())

        // the file shouldn't be too big, so read it at once
        var jsonFileContents = file.readText()
        // remove last "]" char
        jsonFileContents = jsonFileContents.dropLast(1)
        // if this is not the first write, add a comma to append the next object (`},`)
        if (jsonFileContents != "[") {
            jsonFileContents = jsonFileContents.plus(',')
        }
        jsonFileContents = jsonFileContents
                .plus(System.lineSeparator()) // carriage return
                .plus(results)
                .plus("]")

        file.writeText(jsonFileContents)
    }
}

/*
    Benchmarks memory of system webview,
    this is necessary to compare to our or solution.
 */
class SystemWebViewBenchmark : BenchmarkMemory() {
    override fun benchmark() {
        val runTimeStart: Long = SystemClock.elapsedRealtimeNanos()
        val tries = mutableListOf<MemoryBenchmarkTry>()

        repeat(numTries) {
            // we use int values as stage ids along with names to be able to order them
            // for SystemWebView we are using only 3 stages
            // 1 = INIT STAGE
            // 4 = FIRST ADBLOCK WEBVIEW CREATED AND LOADED ABOUT:BLANK
            // 5 = 5 seconds cool off
            var stageCounter = 1 // starting from one

            tries.add(stampMemory("INIT STAGE", stageCounter, null))

            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val webView = WebView(context)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        latch.countDown()
                        super.onPageFinished(view, url)
                    }
                }
                webView.loadUrl("about:blank")
            }

            assertTrue(latch.await(1, TimeUnit.MINUTES))
            stageCounter = +3 // = 4
            tries.add(stampMemory("FIRST WEBVIEW CREATED AND LOADED ABOUT:BLANK", stageCounter++, null))
            // added last stage the cool off period, looking at some measurements throughout the test
            // it continues to change the memory usage after the loading of webview there is a pattern
            // of a short rise and fall after the 4 seconds mark.
            SystemClock.sleep(COOL_OFF_DURATION_MILLI)
            tries.add(stampMemory("5 seconds cool off", stageCounter, null))
        }
        writeResultsOnFile(MemoryBenchmarkRun(
                run_unit = "kb",
                is_aa_enabled = false,
                is_adblock_enabled = false,
                total_run_time_ns = (SystemClock.elapsedRealtimeNanos() - runTimeStart),
                lists = emptyList(), tries = tries))
    }
}

abstract class AdblockWebViewBenchmarkMemory(private val subscriptionListResourceID: Int,
                                             private val exceptionListResourceID: Int,
                                             private val isAaEnabled: Boolean = true,
                                             private val isAdblockEnabled: Boolean = true) :

        BenchmarkMemory() {

    init {
        // if AA is enabled then adblock must be enable
        if (isAaEnabled) {
            assertTrue(isAdblockEnabled)
        }
    }

    // mapped all subscription url to the given subscription list because the runner of the test
    // might run this in a different locale and this would result in a different easylist being
    // downloaded this way any of the subscription list download results in the injection of
    // our subscription list test file.
    private val resourcesList = mapOf(
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

    /*
    A map of lists to list objects describing every filter list characteristics
     */
    private val listToMemoryListObjMap = mapOf(
            R.raw.exceptionrules to MemoryBenchmarkList("exceptionrules",
                    minification_coefficient = 0,
                    file_size_b = -1),
            R.raw.exceptionrules_min to MemoryBenchmarkList("exceptionrules_min",
                    minification_coefficient = 100,
                    file_size_b = -1),
            R.raw.easy_20 to MemoryBenchmarkList("easy_20",
                    minification_coefficient = 20,
                    file_size_b = -1),
            R.raw.easy_50 to MemoryBenchmarkList("easy_50",
                    minification_coefficient = 50,
                    file_size_b = -1),
            R.raw.easy_80 to MemoryBenchmarkList("easy_80",
                    minification_coefficient = 80,
                    file_size_b = -1),
            R.raw.easylist to MemoryBenchmarkList("easylist",
                    minification_coefficient = 0,
                    file_size_b = -1),
            R.raw.easylist_min_uc to MemoryBenchmarkList("easylist_min_uc",
                    minification_coefficient = 100,
                    file_size_b = -1)
    )

    @LargeTest
    @Test
    override fun benchmark() {
        val results = benchmarkFilterList(resourcesList, folder.newFolder().absolutePath)
        writeResultsOnFile(results)
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
            folder: String): MemoryBenchmarkRun {
        val runTimeStart: Long = SystemClock.elapsedRealtimeNanos()

        val tries = mutableListOf<MemoryBenchmarkTry>()
        repeat(numTries) {
            // we use int values as stage ids along with names to be able to order them
            // 1 = INIT STAGE
            // 2 = ENGINE CREATED
            // 3 = FILTER LOADED
            // 4 = FIRST ADBLOCK WEBVIEW CREATED AND LOADED ABOUT:BLANK
            // 5 = 5 seconds cool off
            var stageCounter = 1 // starting from one

            tries.add(stampMemory("INIT STAGE", stageCounter++, null))

            val engineFactory = AdblockEngine
                    .builder(context, AppInfo.builder().build(), folder)
                    .preloadSubscriptions(context, localResources, MockStorage())
                    .setForceUpdatePreloadedSubscriptions(false)

            val customEngineProvider = SingleInstanceEngineProvider(engineFactory)

            assertTrue(customEngineProvider.retain(false))
            customEngineProvider.engine.settings().edit().setAcceptableAdsEnabled(isAaEnabled).setEnabled(isAaEnabled)
                    .save()
            tries.add(stampMemory("ENGINE CREATED", stageCounter++, customEngineProvider))
            assertNotNull(customEngineProvider.engine)

            // wait for all subscriptions to be loaded
            while (!customEngineProvider.engine.settings().listedSubscriptions.all {
                        it.synchronizationStatus == "synchronize_ok"
                    }) {
                SystemClock.sleep(SLEEP_TIME_MILLI)
            }

            tries.add(stampMemory("FILTER LOADED", stageCounter++, customEngineProvider))
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
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
            tries.add(stampMemory("FIRST ADBLOCK WEBVIEW CREATED AND LOADED ABOUT:BLANK", stageCounter++, customEngineProvider))

            // added last stage the cool off period, looking at some measurements throughout the test
            // it continues to change the memory usage after the loading of webview there is a pattern
            // of a short rise and fall after the 4 seconds mark.
            SystemClock.sleep(COOL_OFF_DURATION_MILLI)
            tries.add(stampMemory("5 seconds cool off", stageCounter, customEngineProvider))
        }

        return MemoryBenchmarkRun(
                run_unit = "kb",
                is_aa_enabled = true,
                is_adblock_enabled = isAdblockEnabled,
                total_run_time_ns = (SystemClock.elapsedRealtimeNanos() - runTimeStart),
                lists = listOf(listToMemoryListObjMap[subscriptionListResourceID]!!,
                        listToMemoryListObjMap[exceptionListResourceID]!!),
                tries = tries
        )
    }
}

/* AA on */
class MemoryBenchmark_20_full_AA : AdblockWebViewBenchmarkMemory(R.raw.easy_20, R.raw.exceptionrules)

class MemoryBenchmark_20_min_AA : AdblockWebViewBenchmarkMemory(R.raw.easy_20, R.raw.exceptionrules_min)

class MemoryBenchmark_50_full_AA : AdblockWebViewBenchmarkMemory(R.raw.easy_50, R.raw.exceptionrules)

class MemoryBenchmark_50_min_AA :
        AdblockWebViewBenchmarkMemory(R.raw.easy_50, R.raw.exceptionrules_min)

class MemoryBenchmark_80_full_AA :
        AdblockWebViewBenchmarkMemory(R.raw.easy_80, R.raw.exceptionrules)

class MemoryBenchmark_80_min_AA :
        AdblockWebViewBenchmarkMemory(R.raw.easy_80, R.raw.exceptionrules_min)

class MemoryBenchmark_full_easy_full_AA :
        AdblockWebViewBenchmarkMemory(R.raw.easylist, R.raw.exceptionrules)

class MemoryBenchmark_full_easy_min_AA :
        AdblockWebViewBenchmarkMemory(R.raw.easylist, R.raw.exceptionrules_min)

class MemoryBenchmark_minDist_full_AA :
        AdblockWebViewBenchmarkMemory(R.raw.easylist_min_uc, R.raw.exceptionrules)

class MemoryBenchmark_minDist_min_AA :
        AdblockWebViewBenchmarkMemory(R.raw.easylist_min_uc, R.raw.exceptionrules_min)

/** With AA off
Here we still set the AA subscriptions to either full or min because it even though it is
off the sdk still downloads the subscriptions and might loaded it, affecting its memory usage
 */
class MemoryBenchmark_20_full_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easy_20, R.raw.exceptionrules, false)

class MemoryBenchmark_20_min_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easy_20, R.raw.exceptionrules_min, false)

class MemoryBenchmark_50_full_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easy_50, R.raw.exceptionrules, false)

class MemoryBenchmark_50_min_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easy_50, R.raw.exceptionrules_min, false)

class MemoryBenchmark_80_full_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easy_80, R.raw.exceptionrules, false)

class MemoryBenchmark_80_min_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easy_80, R.raw.exceptionrules_min, false)

class MemoryBenchmark_full_easy_full_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easylist, R.raw.exceptionrules, false)

class MemoryBenchmark_full_easy_min_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easylist, R.raw.exceptionrules_min, false)

class MemoryBenchmark_minDist_full_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easylist_min_uc, R.raw.exceptionrules, false)

class MemoryBenchmark_minDist_min_AA_AA_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easylist_min_uc, R.raw.exceptionrules_min, false)

/** With Adblock disable
The purpose of these benchmarks is to measure bare memory cost of adblockwebview without adblock
enable. It should come close to systemwebview, also removed some of the partial lists 20, 50, 80.
 */
class MemoryBenchmark_full_easy_full_AA_adblock_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easylist, R.raw.exceptionrules, false, false)

class MemoryBenchmark_full_easy_min_AA_adblock_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easylist, R.raw.exceptionrules_min, false, false)

class MemoryBenchmark_minDist_full_AA_adblock_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easylist_min_uc, R.raw.exceptionrules, false, false)

class MemoryBenchmark_minDist_min_AA_adblock_disabled :
        AdblockWebViewBenchmarkMemory(R.raw.easylist_min_uc, R.raw.exceptionrules_min, false, false)