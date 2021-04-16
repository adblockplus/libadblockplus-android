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

package org.adblockplus.libadblockplus.benchmark

import android.opengl.Matrix
import android.os.SystemClock
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.adblockplus.libadblockplus.FileSystem
import org.adblockplus.libadblockplus.FilterEngine
import org.adblockplus.libadblockplus.Platform
import org.adblockplus.libadblockplus.android.AdblockEngine
import org.adblockplus.libadblockplus.android.AndroidHttpClient
import org.adblockplus.libadblockplus.android.AndroidHttpClientResourceWrapper
import org.adblockplus.libadblockplus.android.AndroidHttpClientResourceWrapper.Storage
import org.adblockplus.libadblockplus.android.TimberLogSystem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.system.measureTimeMillis


class FilterEngineLoadTimeBenchmark {
    @get:Rule
    val folder = TemporaryFolder()

    fun randomDirectory() : File {
        return folder.newFolder()
    }

    companion object {

        // number of bytes of patterns ini file minified and full
        // should be in sync with the filters lists used

        private const val PATTERNS_INI_MINIFIED_SIZE = 1_425_053L
        private const val PATTERNS_INI_FULL_SIZE = 11_308_451L

        private val easylist = R.raw.easylist
        private val exceptionRules = R.raw.exceptionrules

        private val minifiedEasyList = R.raw.easylist_min_uc
        private val minifiedExceptionList = R.raw.exceptionrules_min

        // used for waiting of subscriptions ready
        private const val SLEEP_INTERVAL_MILLIS = 1L

        private val context = InstrumentationRegistry.getInstrumentation().targetContext

        fun localeToResourceId(blockingListResourceID  : Int,
                               exceptionListResourceID : Int) : Map<String, Int> {
            return mapOf(
                AndroidHttpClientResourceWrapper.EASYLIST to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_INDONESIAN to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_BULGARIAN to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_CHINESE to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_CZECH_SLOVAK to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_DUTCH to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_GERMAN to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_ISRAELI to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_ITALIAN to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_LITHUANIAN to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_POLISH to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_LATVIAN to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_ARABIAN_FRENCH to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_FRENCH to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_ROMANIAN to blockingListResourceID,
                AndroidHttpClientResourceWrapper.EASYLIST_RUSSIAN to blockingListResourceID,

                // acceptable ads
                AndroidHttpClientResourceWrapper.ACCEPTABLE_ADS to exceptionListResourceID)
        }
    }
    init {
        if (Timber.treeCount() == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }
    @get:Rule
    val globalTimeout = Timeout(15, TimeUnit.MINUTES)

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private class Setup(basePath: String,
                        blockingList : Int,
                        exceptionList : Int) {
        val logSystem = TimberLogSystem()
        val fileSystem: FileSystem? = null // default C++ impl
        val wrapperStorage = object : Storage {
            override fun put(url: String?) { /* nothing */ }
            override fun contains(url: String?) = false // we don't need downloading at all
        }

        // we want to isolate the changes and remove the influence of network speed.
        // so providing subscriptions content from test resources instead of actual downloading
        val httpClientWrapper = AndroidHttpClientResourceWrapper(
            context, AndroidHttpClient(), localeToResourceId(blockingList, exceptionList), wrapperStorage)

        // we need to randomize basePath to avoid test interference (shared state from persistence)
        val platform = Platform(logSystem, fileSystem, httpClientWrapper, basePath)

        init {
            httpClientWrapper.setListener {
                url, _ -> Timber.d("Intercepted $url to avoid networking")
            }
            platform.setUpJsEngine(AdblockEngine.generateAppInfo(context))
            Timber.d("Path = $basePath")
        }
    }

    private fun waitForSubscriptionsLoaded(filterEngine: FilterEngine) {
        Timber.d("Waiting for ready subscriptions ...")
        val subscriptionsReadyMillis = measureTimeMillis {
            var subscriptions = filterEngine.listedSubscriptions
            Timber.d("Current subscriptions: $subscriptions")
            while (subscriptions.size != 2 || // 2 = locale-specific + AA
                !subscriptions.all {
                    it.synchronizationStatus == "synchronize_ok"
                }) {
                SystemClock.sleep(SLEEP_INTERVAL_MILLIS)
                subscriptions = filterEngine.listedSubscriptions
                Timber.d("Current subscriptions: $subscriptions")
            }
        }
        Timber.d("Subscriptions ready in $subscriptionsReadyMillis millis")
    }

    private fun waitForSubscriptionsSaved(pattersIniFile: File, totalLines : Long) {
        while (!pattersIniFile.exists() || pattersIniFile.length() < totalLines) {
            Timber.d("waiting loaded %s of %s bytes should load", pattersIniFile.length().toString(), totalLines.toString())
            SystemClock.sleep(SLEEP_INTERVAL_MILLIS)
        }
        Timber.d("end waiting loaded %s of %s bytes should load",
                pattersIniFile.length().toString(), totalLines.toString())
    }

    private inline fun getPatternsIniFile(dir: File) = File(dir, "patterns.ini")

    private fun createAndDispose(path: String,
                                 blockingList : Int,
                                 exceptionList : Int,
                                 onCreated: ((FilterEngine) -> Unit)?) {
        val setup = Setup(path, blockingList, exceptionList)
        with(setup.platform) {
            Timber.d("Creating FilterEngine ...")
            lateinit var filterEngine: FilterEngine
            try {
                val loadTime = measureTimeMillis {
                    filterEngine = getFilterEngine()
                }
                Timber.d("Created in $loadTime millis")
                if (onCreated != null) {
                    onCreated(filterEngine)
                }
            } finally {
                dispose()
            }
        }
    }

    private inline fun test(dir: File,
                            waitForSaved  : Boolean,
                            blockingList  : Int,
                            exceptionList : Int,
                            exceptedPatternsIniSize : Long) {

        createAndDispose(dir.absolutePath, blockingList, exceptionList) {
            waitForSubscriptionsLoaded(it)
            if (waitForSaved) {
                waitForSubscriptionsSaved(getPatternsIniFile(dir), exceptedPatternsIniSize)
            }
        }
    }

    private fun genericMeasureSecondTimeFELT(blockingRulesResourceId : Int,
                                             exceptionRulesResourceId : Int,
                                             patternsInitExpectedByteSize : Long) {
        // "not the first time" means subscriptions data is already downloaded and saved on SD
        val dir = randomDirectory()
        val pattersIniFile = getPatternsIniFile(dir)

        // download, wait for subscriptions to be ready and saved
        test(dir, true, blockingRulesResourceId, exceptionRulesResourceId, patternsInitExpectedByteSize)

        // check saved subscriptions data
        assertTrue(pattersIniFile.exists())

        // Note: the actual file size can be a bit different when building locally vs. on CI
        // due to different datetimes (lastDownload, lastSuccess) and some empty lines
        assertTrue(abs(pattersIniFile.length() - patternsInitExpectedByteSize) < 10)
        Timber.d("State is ready, starting benchmarking")

        // the dir is the same as it was used for the first FE create,
        // so it will read the subscriptions data from the persistent storage
        benchmarkRule.measureRepeated {
            test(dir, false, blockingRulesResourceId, exceptionRulesResourceId, patternsInitExpectedByteSize)
        }
    }

    // an example for proper benchmarking sample, taken from:
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-master-dev:benchmark/common/src/main/java/androidx/benchmark/ThrottleDetector.kt;l=47;drc=18266f9efb44e8e63a45e055ffd471dd11544c02
    @Test
    fun measurePureCalculation() {
        val sourceMatrix = FloatArray(16) { System.nanoTime().toFloat() }
        val resultMatrix = FloatArray(16)

        benchmarkRule.measureRepeated {
            repeat(1000) {
                Matrix.translateM(resultMatrix, 0, sourceMatrix, 0, 1F, 2F, 3F)
            }
        }
    }

    @Test
    fun measureVeryFirstTimeMinified() {
        // "very first time" means the subscriptions data is going to be downloaded (actually loaded
        // from app resources), parsed and saved

        benchmarkRule.measureRepeated {
            test(randomDirectory(), false, minifiedEasyList, minifiedExceptionList, PATTERNS_INI_MINIFIED_SIZE)
        }
    }

    @Test
    fun measureNotTheFirstTimeMinified() {
        genericMeasureSecondTimeFELT(minifiedEasyList, minifiedExceptionList, PATTERNS_INI_MINIFIED_SIZE)
    }

    @Test
    fun measureVeryFirstTime() {
        // "very first time" means the subscriptions data is going to be downloaded (actually loaded
        // from app resources), parsed and saved

        benchmarkRule.measureRepeated {
            test(randomDirectory(),
                    false,
                    easylist,
                    exceptionRules,
                    PATTERNS_INI_FULL_SIZE)
        }
    }

    @Test
    fun measureNotTheFirstTime() {
        genericMeasureSecondTimeFELT(easylist, exceptionRules, PATTERNS_INI_FULL_SIZE)
    }
}
