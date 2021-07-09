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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class FileSystemInstrumentedTest {
    private lateinit var engine: AdblockEngine

    @Before
    fun setUp() {
        engine = AdblockEngine(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun testReadWrite() {
        val filePath = "/sdcard/fileName"
        val fileContent = "some test content"

        engine.evaluateJS("var result = {}; " +
            "__fileSystem_writeToFile(\"$filePath\", " +
            "\"$fileContent\", " +
            "function(error) { if (error) result.error = error; });")
        var resultError = engine.evaluateJS("result.error")
        assert(resultError == "")

        engine.evaluateJS("var result = {}; " +
            "__fileSystem_readFromFile(\"$filePath\", " +
            "function(data) { if (data) result.data = data; }, " + /* data listener callback */
            "function() {}, " + /* empty resolve callback */
            "function (error) { if (error) result.error = error; });")
        resultError = engine.evaluateJS("result.error")
        val resultData = engine.evaluateJS("result.data")
        assert(resultError == "")
        assert(fileContent == resultData)
    }

    @Test
    fun testWriteWithRenaming() {
        val filePath1 = "/sdcard/fileName1"
        val filePath2 = "/sdcard/fileName2"
        val fileContent = "some test content"

        // create file
        engine.evaluateJS("var result = {}; " +
                "__fileSystem_writeToFile(\"$filePath1\", " +
                "\"$fileContent\", " +
                "function(error) { if (error) result.error = error; });")
        var resultError = engine.evaluateJS("result.error")
        assert(resultError == "")

        // rename it (move)
        engine.evaluateJS("var result = {}; " +
                "__fileSystem_moveFile(\"$filePath1\", \"$filePath2\", " +
                "function (error) { if (error) result.error = error; });")
        resultError = engine.evaluateJS("result.error")
        assert(resultError == "")

        // read and validate
        engine.evaluateJS("var result = {}; " +
                "__fileSystem_readFromFile(\"$filePath2\", " +
                "function(data) { if (data) result.data = data; }, " + /* data listener callback */
                "function() {}, " + /* empty resolve callback */
                "function (error) { if (error) result.error = error; });")
        resultError = engine.evaluateJS("result.error")
        val resultData = engine.evaluateJS("result.data")
        assert(resultError == "")
        assert(fileContent == resultData)
    }

    @Test
    fun testStat() {
        val filePath = "/sdcard/fileName"
        val fileContent = "some test content"

        engine.evaluateJS("var result = {}; " +
                "__fileSystem_writeToFile(\"$filePath\", " +
                "\"$fileContent\", " +
                "function(error) { if (error) result.error = error; });")
        var resultError = engine.evaluateJS("result.error")
        assert(resultError.isEmpty())

        engine.evaluateJS("var result = {}; " +
                "__fileSystem_statFile(\"$filePath\", " +
                "function (res) { result = res; });")
        resultError = engine.evaluateJS("result.error")
        val exists = engine.evaluateJS("result.exists")
        val lastModified = engine.evaluateJS("result.lastModified")
        assert(resultError.isEmpty())
        assert(exists.toBoolean())
        assert(lastModified.toLong() > 0)
    }
}
