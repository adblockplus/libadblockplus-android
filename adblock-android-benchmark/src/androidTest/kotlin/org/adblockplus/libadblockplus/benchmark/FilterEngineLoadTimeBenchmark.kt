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
import androidx.test.platform.app.InstrumentationRegistry
import org.adblockplus.libadblockplus.FileSystem
import org.adblockplus.libadblockplus.FilterEngine
import org.adblockplus.libadblockplus.Platform
import org.adblockplus.libadblockplus.android.AdblockEngine
import org.adblockplus.libadblockplus.android.AndroidHttpClient
import org.adblockplus.libadblockplus.android.AndroidHttpClientResourceWrapper
import org.adblockplus.libadblockplus.android.AndroidHttpClientResourceWrapper.Storage
import org.adblockplus.libadblockplus.android.TimberLogSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

fun randomDirectory() = File.createTempFile("adblock", ".tmpdir").also {
    it.delete()
    it.mkdirs()
}

class FilterEngineLoadTimeBenchmark {
    companion object {
        // used for waiting of subscriptions ready
        private const val SLEEP_INTERVAL_MILLIS = 1L

        // Warning: should be in sync with main/res/raw/easylist.txt and exceptionrules.txt
        private const val PATTERNS_INI_LENGTH = 365427L

        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val resourceIdToResourceMap = mapOf(
            // Warning: it's required to have all the possible values from ..Wrapper

            // locale-specific
            AndroidHttpClientResourceWrapper.EASYLIST to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_INDONESIAN to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_BULGARIAN to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_CHINESE to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_CZECH_SLOVAK to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_DUTCH to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_GERMAN to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_ISRAELI to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_ITALIAN to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_LITHUANIAN to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_LATVIAN to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_ARABIAN_FRENCH to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_FRENCH to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_ROMANIAN to R.raw.easylist,
            AndroidHttpClientResourceWrapper.EASYLIST_RUSSIAN to R.raw.easylist,

            // acceptable ads
            AndroidHttpClientResourceWrapper.ACCEPTABLE_ADS to R.raw.exceptionrules
        )
    }

    @get:Rule
    val globalTimeout = Timeout(15, TimeUnit.MINUTES)

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private class Setup(basePath: String) {
        val logSystem = TimberLogSystem()
        val fileSystem: FileSystem? = null // default C++ impl
        val wrapperStorage = object : Storage {
            override fun put(url: String?) { /* nothing */ }
            override fun contains(url: String?) = false // we don't need downloading at all
        }

        // we want to isolate the changes and remove the influence of network speed.
        // so providing subscriptions content from test resources instead of actual downloading
        val httpClientWrapper = AndroidHttpClientResourceWrapper(
            context, AndroidHttpClient(), resourceIdToResourceMap, wrapperStorage)

        // we need to randomize basePath to avoid test interference (shared state from persistence)
        val platform = Platform(logSystem, fileSystem, httpClientWrapper, basePath)

        init {
            httpClientWrapper.setListener {
                url, _ -> Timber.i("Intercepted $url to avoid networking")
            }
            platform.setUpJsEngine(AdblockEngine.generateAppInfo(context, false))
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
                    it.getProperty("downloadStatus")?.asString() == "synchronize_ok"
                }) {
                SystemClock.sleep(SLEEP_INTERVAL_MILLIS)
                subscriptions = filterEngine.listedSubscriptions
                Timber.d("Current subscriptions: $subscriptions")
            }
        }
        Timber.d("Subscriptions ready in $subscriptionsReadyMillis millis")
    }

    private fun waitForSubscriptionsSaved(pattersIniFile: File) {
        while (!pattersIniFile.exists() || pattersIniFile.length() < PATTERNS_INI_LENGTH) {
            SystemClock.sleep(SLEEP_INTERVAL_MILLIS)
        }
    }

    private inline fun getPatternsIniFile(dir: File) = File(dir, "patterns.ini")

    private fun createAndDispose(path: String, onCreated: ((FilterEngine) -> Unit)? = null) {
        val setup = Setup(path)
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

    private inline fun test(dir: File, waitForSaved: Boolean) {
        createAndDispose(dir.absolutePath) {
            waitForSubscriptionsLoaded(it)
            if (waitForSaved) {
                waitForSubscriptionsSaved(getPatternsIniFile(dir))
            }
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
    fun measureVeryFirstTime() {
        // "very first time" means the subscriptions data is going to be downloaded (actually loaded
        // from app resources), parsed and saved

        benchmarkRule.measureRepeated {
            test(randomDirectory(), false)
        }
    }

    @Test
    fun measureNotTheFirstTime() {
        // "not the first time" means subscriptions data is already downloaded and saved on SD

        val dir = randomDirectory()
        val pattersIniFile = getPatternsIniFile(dir)

        // download, wait for subscriptions to be ready and saved
        test(dir, true)

        // check saved subscriptions data
        assertTrue(pattersIniFile.exists())
        assertEquals(PATTERNS_INI_LENGTH, pattersIniFile.length())
        Timber.d("State is ready, starting benchmarking")

        // the dir is the same as it was used for the first FE create,
        // so it will read the subscriptions data from the persistent storage
        benchmarkRule.measureRepeated {
            test(dir, false)
        }
    }
}
