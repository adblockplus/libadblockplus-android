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
import org.adblockplus.ContentType.DOCUMENT
import org.adblockplus.ContentType.ELEMHIDE
import org.adblockplus.ContentType.maskOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber

class AdblockFilterBuilderTest {

    private val appInfo = AppInfo.builder().build()
    private val context = InstrumentationRegistry.getInstrumentation().context

    private lateinit var adblockEngine: AdblockEngine

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

    @Before
    fun setUp() {
        Timber.d("setUp()")
        adblockEngine = AdblockEngineFactory.init(context, appInfo, folder.newFolder().absolutePath)
                .adblockEngineBuilder
                .disabledByDefault()
                .build()
    }

    @After
    fun tearDown() {
        Timber.d("tearDown()")
        AdblockEngineFactory.deinit()
    }

    @Test
    fun testAllowlistingFilterBuilding() {
        val allowAddress = "example.com"
        val domainRestriction = "example.com"

        val adblockFilter: Filter = AdblockFilterBuilder(adblockEngine)
                .setBeginMatchingDomain()
                .allowlistAddress(allowAddress)
                .setContentTypes(maskOf(DOCUMENT))
                .setDomainRestriction(domainRestriction)
                .build()
        val allowlistingFilter = "@@||$allowAddress^\$document,domain=$domainRestriction"
        assertEquals(allowlistingFilter, adblockFilter.text)
    }

    @Test
    fun testBlockingFilterBuilding() {
        val blockAddress = "a.b.c/example-com/test"
        val domainRestriction = "example.com"

        val adblockFilter: Filter = AdblockFilterBuilder(adblockEngine)
                .blockAddress(blockAddress)
                .setBeginMatchingDomain()
                .setContentTypes(maskOf(DOCUMENT))
                .setDomainRestriction(domainRestriction)
                .build()
        val blockingFilter = "||$blockAddress^\$document,domain=$domainRestriction"
        assertEquals(blockingFilter, adblockFilter.text)
    }

    @Test
    fun testContentTypeFilterBuilding() {
        val blockAddress = "a.b.c/example-com/test"
        val domainRestriction = "example.com"

        val adblockFilter: Filter = AdblockFilterBuilder(adblockEngine)
                .blockAddress(blockAddress)
                .setBeginMatchingDomain()
                .setContentTypes(maskOf(DOCUMENT, ELEMHIDE))
                .setDomainRestriction(domainRestriction)
                .build()

        val blockingFilter = "||$blockAddress^\$document,elemhide,domain=$domainRestriction"

        assertTrue(adblockFilter.text.contains("document"))
        assertTrue(adblockFilter.text.contains("elemhide"))

        assertEquals(blockingFilter.substringBefore("\$"), adblockFilter.text.substringBefore("\$"))
        assertEquals(blockingFilter.substringAfterLast(","), adblockFilter.text.substringAfterLast(","))
    }

    @Test
    fun testSitekeyBlockBuilding() {
        val sitekey = "abcdsitekeydcba"

        val adblockFilter: Filter = AdblockFilterBuilder(adblockEngine)
                .setContentTypes(maskOf(DOCUMENT))
                .setSitekey(sitekey, false)
                .build()

        val blockingFilter = "\$document,sitekey=$sitekey"
        assertEquals(blockingFilter, adblockFilter.text)
    }

    @Test
    fun testSitekeyAllowlistingBuilding() {
        val sitekey = "abcdsitekeydcba"

        val adblockFilter: Filter = AdblockFilterBuilder(adblockEngine)
                .setContentTypes(maskOf(DOCUMENT))
                .setSitekey(sitekey, true)
                .build()

        val blockingFilter = "@@\$document,sitekey=$sitekey"
        assertEquals(blockingFilter, adblockFilter.text)
    }

}
