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

package org.adblockplus

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AdblockEngineBuilderTest {
    private val appInfo = AppInfo.builder().build()
    private val context = InstrumentationRegistry.getInstrumentation().context
    private var stateCountDownLatch : CountDownLatch? = null
    private var expectedState = AsyncAdblockEngineBuilder.State.INITIAL
    private val stateListener = AsyncAdblockEngineBuilder.StateListener {
        if (it == expectedState) {
            if (expectedState == AsyncAdblockEngineBuilder.State.CREATED) {
                assertNotNull(
                        AdblockEngineFactory
                                .getFactory()
                                .asyncAdblockEngineBuilder
                                .adblockEngine)
            } else if (expectedState == AsyncAdblockEngineBuilder.State.RELEASED) {
                assertNull(
                        AdblockEngineFactory
                                .getFactory()
                                .asyncAdblockEngineBuilder
                                .adblockEngine)
            }
            stateCountDownLatch?.countDown()
        }
    }

    @get:Rule
    val folder = TemporaryFolder()

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            initLogging()
        }

        private fun initLogging() {
            if (Timber.forest().isEmpty()) {
                Timber.plant(Timber.DebugTree())
            }
        }
    }

    @After
    fun teraDown() {
        Timber.d("teraDown()")
        AdblockEngineFactory.deinit()
    }

    private fun verifyAsyncBuildStates(asyncAdblockEngineBuilder : AsyncAdblockEngineBuilder,
                                       executor : ExecutorService) {
        assertEquals(AsyncAdblockEngineBuilder.State.CREATING, asyncAdblockEngineBuilder.state)
        assertEquals(null, asyncAdblockEngineBuilder.adblockEngine)
        // Wait for build completion
        Timber.d("Waiting for AdblockEngine being created...")
        stateCountDownLatch = CountDownLatch(1)
        expectedState = AsyncAdblockEngineBuilder.State.CREATED
        stateCountDownLatch!!.await()
        assertEquals(AsyncAdblockEngineBuilder.State.CREATED, asyncAdblockEngineBuilder.state)
        val adblockEngine = asyncAdblockEngineBuilder.adblockEngine
        assertNotNull(adblockEngine)
        // Run async build again, should have no effect so 1st argument is not important
        assertEquals(AsyncAdblockEngineBuilder.State.CREATED, asyncAdblockEngineBuilder.build(executor))
        assertEquals(AsyncAdblockEngineBuilder.State.CREATED, asyncAdblockEngineBuilder.state)
        assertEquals(adblockEngine, asyncAdblockEngineBuilder.adblockEngine)
    }

    private fun verifyAsyncDisposeStates(asyncAdblockEngineBuilder : AsyncAdblockEngineBuilder,
                                         executor: ExecutorService) {
        assertEquals(AsyncAdblockEngineBuilder.State.RELEASING, asyncAdblockEngineBuilder.state)
        assertNotEquals(null, asyncAdblockEngineBuilder.adblockEngine)
        // Wait for dispose completion
        Timber.d("Waiting for AdblockEngine being disposed...")
        stateCountDownLatch = CountDownLatch(1)
        expectedState = AsyncAdblockEngineBuilder.State.RELEASED
        stateCountDownLatch!!.await()
        assertEquals(AsyncAdblockEngineBuilder.State.RELEASED, asyncAdblockEngineBuilder.state)
        val adblockEngine = asyncAdblockEngineBuilder.adblockEngine
        assertNull(adblockEngine)
        // Run async dispose again, should have no effect so 1st argument is not important
        assertEquals(AsyncAdblockEngineBuilder.State.RELEASED, asyncAdblockEngineBuilder.dispose(executor))
        assertEquals(AsyncAdblockEngineBuilder.State.RELEASED, asyncAdblockEngineBuilder.state)
        assertNull(null, adblockEngine)
    }

    @Test
    fun testAsyncAdblockEngineBuilder() {
        val executor = Executors.newSingleThreadExecutor()
        val asyncAdblockEngineBuilder = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .asyncAdblockEngineBuilder
                .subscribe(stateListener)
        assertEquals(AsyncAdblockEngineBuilder.State.INITIAL, asyncAdblockEngineBuilder.state)
        assertEquals(AsyncAdblockEngineBuilder.State.CREATING, asyncAdblockEngineBuilder.build(executor))
        verifyAsyncBuildStates(asyncAdblockEngineBuilder, executor)
        asyncAdblockEngineBuilder.dispose(executor)
        verifyAsyncDisposeStates(asyncAdblockEngineBuilder, executor)
        executor.shutdown()
    }

    @Test
    fun testCallSyncThenCheckAsyncAdblockEngineBuilderStates() {
        val syncAdblockEngineBuilder = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder
                .disabledByDefault()
        val asyncAdblockEngineBuilder = AdblockEngineFactory
                .getFactory()
                .asyncAdblockEngineBuilder
                .disabledByDefault()
        assertEquals(AsyncAdblockEngineBuilder.State.INITIAL, asyncAdblockEngineBuilder.state)
        assertNull(asyncAdblockEngineBuilder.adblockEngine)
        assertNotNull(syncAdblockEngineBuilder.build())
        assertEquals(AsyncAdblockEngineBuilder.State.CREATED, asyncAdblockEngineBuilder.state)
        assertNotNull(asyncAdblockEngineBuilder.adblockEngine)
        syncAdblockEngineBuilder.dispose()
        assertEquals(AsyncAdblockEngineBuilder.State.RELEASED, asyncAdblockEngineBuilder.state)
        assertNull(asyncAdblockEngineBuilder.adblockEngine)
    }

    @Test
    fun testTwoSyncAdblockEngineBuildersConcurrency() {
        val syncAdblockEngineBuilder1 = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder
                .disabledByDefault()
        val syncAdblockEngineBuilder2 = AdblockEngineFactory
                .getFactory()
                .adblockEngineBuilder
                .disabledByDefault()
        val asyncThread1 = Thread {
            for (i in 1..100) {
                Timber.d("SyncAdblockEngineBuilder1 loop %d", i)
                assertNotNull(syncAdblockEngineBuilder1.build())
                syncAdblockEngineBuilder2.dispose()
                Thread.sleep(i*1L)
            }
        }
        val asyncThread2 = Thread {
            for (i in 1..100) {
                Timber.d("SyncAdblockEngineBuilder2 loop %d", i)
                assertNotNull(syncAdblockEngineBuilder2.build())
                syncAdblockEngineBuilder2.dispose()
                Thread.sleep((100-i)*1L)
            }
        }
        asyncThread1.start()
        asyncThread2.start()
        asyncThread1.join()
        asyncThread2.join()
    }

    @Test
    fun testTwoAsyncAdblockEngineBuildersConcurrency() {
        val asyncAdblockEngineBuilder1 = AdblockEngineFactory
                .init(context, appInfo, folder.newFolder().absolutePath)
                .asyncAdblockEngineBuilder
                .subscribe(stateListener)
                .disabledByDefault()
        val asyncAdblockEngineBuilder2 = AdblockEngineFactory
                .getFactory()
                .asyncAdblockEngineBuilder
                .subscribe(stateListener)
                .disabledByDefault()
        val asyncThread1 = Thread {
            val executor = Executors.newSingleThreadExecutor()
            for (i in 1..100) {
                Timber.d("AsyncAdblockEngineBuilder1 loop %d", i)
                assertNotNull(asyncAdblockEngineBuilder1.build(executor))
                assertNotNull(asyncAdblockEngineBuilder1.dispose(executor))
                Thread.sleep(i*1L)
            }
            executor.shutdown()
        }
        val asyncThread2 = Thread {
            val executor = Executors.newSingleThreadExecutor()
            for (i in 1..100) {
                Timber.d("AsyncAdblockEngineBuilder2 loop %d", i)
                assertNotNull(asyncAdblockEngineBuilder2.build(executor))
                assertNotNull(asyncAdblockEngineBuilder2.dispose(executor))
                Thread.sleep((100-i)*1L)
            }
            executor.shutdown()
        }
        asyncThread1.start()
        asyncThread2.start()
        asyncThread1.join()
        asyncThread2.join()
    }
}