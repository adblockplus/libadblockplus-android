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
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.platform.app.InstrumentationRegistry
import org.adblockplus.hermes.Engine
import org.adblockplus.libadblockplus.android.AndroidHttpClientResourceWrapper
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
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

    private fun createAndDispose(path: String,
                                 blockingList : Int,
                                 exceptionList : Int) {

            Timber.d("Creating FilterEngine ...")
            val loadTime = measureTimeMillis {
                Engine(context)
            }
            Timber.d("Created in $loadTime millis")
    }

    private fun test(dir: File,
                     blockingList: Int,
                     exceptionList: Int,
                     exceptedPatternsIniSize: Long) {

        createAndDispose(dir.absolutePath, blockingList, exceptionList)
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
    fun measureVeryFirstTime() {
        // "very first time" means the subscriptions data is going to be downloaded (actually loaded
        // from app resources), parsed and saved

        benchmarkRule.measureRepeated {
            test(randomDirectory(),
                    easylist,
                    exceptionRules,
                    PATTERNS_INI_FULL_SIZE)
        }
    }

}
