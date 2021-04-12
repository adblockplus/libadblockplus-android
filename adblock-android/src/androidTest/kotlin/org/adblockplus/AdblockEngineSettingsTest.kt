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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber

class AdblockEngineSettingsTest {
    private val appInfo = AppInfo.builder().build()!!
    val context = InstrumentationRegistry.getInstrumentation().context!!

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var adblockEngine: AdblockEngine

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
                .build()
    }

    @After
    fun tearDown() {
        Timber.d("tearDown()")
        AdblockEngineFactory.deinit()
    }

    @Test
    fun testEnableStateChangesAndListeners() {
        // Verify preconditions
        assertTrue(adblockEngine.settings().isEnabled)
        assertTrue(adblockEngine.settings().isAcceptableAdsEnabled)

        var enableCounter = 0
        var enableAACounter = 0
        var enableValue = true
        var enableAAValue = true

        val enableStatesListener = object : AdblockEngineSettings.EnableStateChangedListener {
            override fun onAdblockEngineEnableStateChanged(isEnabled: Boolean) {
                ++enableCounter
                enableValue = isEnabled
            }

            override fun onAcceptableAdsEnableStateChanged(isEnabled: Boolean) {
                ++enableAACounter
                enableAAValue = isEnabled
            }
        }
        adblockEngine.settings().addEnableStateChangedListener(enableStatesListener)

        // Disable all and verify
        adblockEngine.settings().edit().setEnabled(false).setAcceptableAdsEnabled(false).save()
        assertEquals(enableValue, adblockEngine.settings().isEnabled)
        assertEquals(enableAAValue, adblockEngine.settings().isAcceptableAdsEnabled)
        assertFalse(enableValue)
        assertFalse(enableAAValue)
        assertEquals(1, enableCounter)
        assertEquals(1, enableAACounter)

        // Enable all and verify
        adblockEngine.settings().edit().setEnabled(true).setAcceptableAdsEnabled(true).save()
        assertEquals(enableValue, adblockEngine.settings().isEnabled)
        assertEquals(enableAAValue, adblockEngine.settings().isAcceptableAdsEnabled)
        assertTrue(enableValue)
        assertTrue(enableAAValue)
        assertEquals(2, enableCounter)
        assertEquals(2, enableAACounter)
    }

    @Test
    fun testFilterChangesAndListeners() {
        // Verify preconditions
        assertEquals(0, adblockEngine.settings().listedFilters.size)

        var addRemoveCounter = 0
        val filter = AdblockFilterBuilder(adblockEngine)
                .allowlistAddress("some.domain.com")
                .build()
        var action: AdblockEngineSettings.FiltersChangedListener.FilterEvent? = null

        val addRemoveFilterListener = object : AdblockEngineSettings.FiltersChangedListener {
            override fun onFilterEvent(filterToEventMap: MutableMap<Filter,
                    AdblockEngineSettings.FiltersChangedListener.FilterEvent>) {
                action = filterToEventMap.values.first()
                ++addRemoveCounter
            }
        }
        adblockEngine.settings().addFiltersChangedListener(addRemoveFilterListener)

        // Add filter and verify
        adblockEngine.settings().edit().addCustomFilter(filter).save()
        assertEquals(1, addRemoveCounter)
        assertEquals(1, adblockEngine.settings().listedFilters.size)
        assertEquals(AdblockEngineSettings.FiltersChangedListener.FilterEvent.FILTER_ADDED, action)
        assertTrue(adblockEngine.settings().listedFilters.contains(filter))

        // Remove filter and verify
        adblockEngine.settings().edit().removeCustomFilter(filter).save()
        assertEquals(2, addRemoveCounter)
        assertEquals(0, adblockEngine.settings().listedFilters.size)
        assertEquals(AdblockEngineSettings.FiltersChangedListener.FilterEvent.FILTER_REMOVED, action)

        // Add filter and verify clear all
        adblockEngine.settings().edit().addCustomFilter(filter).save()
        assertEquals(3, addRemoveCounter)
        assertEquals(1, adblockEngine.settings().listedFilters.size)
        assertEquals(AdblockEngineSettings.FiltersChangedListener.FilterEvent.FILTER_ADDED, action)
        assertTrue(adblockEngine.settings().listedFilters.contains(filter))
        // Now clear all
        adblockEngine.settings().edit().clearCustomFilters().save()
        assertEquals(4, addRemoveCounter)
        assertEquals(0, adblockEngine.settings().listedFilters.size)
        assertEquals(AdblockEngineSettings.FiltersChangedListener.FilterEvent.FILTER_REMOVED, action)
    }

    @Test
    fun testSubscriptionChangesAndListeners() {
        // Verify preconditions
        assertEquals(2, adblockEngine.settings().listedSubscriptions.size)

        var addRemoveCounter = 0
        val subscription = adblockEngine
                .getSubscription("https://testpages.adblockplus.org/en/abp-testcase-subscription.txt")
        var action: AdblockEngineSettings.SubscriptionsChangedListener.SubscriptionEvent? = null

        val addRemoveSubscriptionListener = object : AdblockEngineSettings.SubscriptionsChangedListener {
            override fun onSubscriptionEvent(subscriptionToEventMap: MutableMap<Subscription,
                    AdblockEngineSettings.SubscriptionsChangedListener.SubscriptionEvent>) {
                action = subscriptionToEventMap.values.first()
                ++addRemoveCounter
            }
        }
        adblockEngine.settings().addSubscriptionsChangedListener(addRemoveSubscriptionListener)

        // Clear all subscriptions and verify
        adblockEngine.settings().edit().clearSubscriptions().save()
        assertEquals(1, addRemoveCounter)
        assertEquals(0, adblockEngine.settings().listedSubscriptions.size)
        assertEquals(AdblockEngineSettings.SubscriptionsChangedListener.SubscriptionEvent.SUBSCRIPTION_REMOVED, action)

        // Add subscription and verify
        adblockEngine.settings().edit().addSubscription(subscription).save()
        assertEquals(2, addRemoveCounter)
        assertEquals(1, adblockEngine.settings().listedSubscriptions.size)
        assertEquals(AdblockEngineSettings.SubscriptionsChangedListener.SubscriptionEvent.SUBSCRIPTION_ADDED, action)
        assertTrue(adblockEngine.settings().listedSubscriptions.contains(subscription))

        // Remove subscription and verify
        adblockEngine.settings().edit().removeSubscription(subscription).save()
        assertEquals(3, addRemoveCounter)
        assertEquals(0, adblockEngine.settings().listedSubscriptions.size)
        assertEquals(AdblockEngineSettings.SubscriptionsChangedListener.SubscriptionEvent.SUBSCRIPTION_REMOVED, action)
    }

    @Test
    fun testConnectionTypeChange() {
        // Verify preconditions
        assertEquals(null, adblockEngine.settings().allowedConnectionType)

        arrayOf(ConnectionType.ANY, ConnectionType.WIFI, ConnectionType.WIFI_NON_METERED, null).forEach {
            adblockEngine.settings().edit().setAllowedConnectionType(it).save()
            assertEquals(it, adblockEngine.settings().allowedConnectionType)
        }
    }

    @Test
    fun testDuplicatedSaveFiresListenersOnce() {
        var enableCounter = 0
        var enableAACounter = 0
        var addRemoveFilterCounter = 0
        var addRemoveSubscriptionCounter = 0

        //Add listeners
        val enableStatesListener = object : AdblockEngineSettings.EnableStateChangedListener {
            override fun onAdblockEngineEnableStateChanged(isEnabled: Boolean) {
                ++enableCounter
            }

            override fun onAcceptableAdsEnableStateChanged(isEnabled: Boolean) {
                ++enableAACounter
            }
        }
        adblockEngine.settings().addEnableStateChangedListener(enableStatesListener)

        val addRemoveFilterListener = object : AdblockEngineSettings.FiltersChangedListener {
            override fun onFilterEvent(filterToEventMap: MutableMap<Filter,
                    AdblockEngineSettings.FiltersChangedListener.FilterEvent>) {
                ++addRemoveFilterCounter
            }
        }
        adblockEngine.settings().addFiltersChangedListener(addRemoveFilterListener)

        val addRemoveSubscriptionListener = object : AdblockEngineSettings.SubscriptionsChangedListener {
            override fun onSubscriptionEvent(subscriptionToEventMap: MutableMap<Subscription,
                    AdblockEngineSettings.SubscriptionsChangedListener.SubscriptionEvent>) {
                ++addRemoveSubscriptionCounter
            }
        }
        adblockEngine.settings().addSubscriptionsChangedListener(addRemoveSubscriptionListener)

        val filter = adblockEngine.getFilterFromText("@@some.domain.com")
        val subscription = adblockEngine
                .getSubscription("https://testpages.adblockplus.org/en/abp-testcase-subscription.txt")

        val editOperation = adblockEngine.settings().edit()

        // Trigger save() without any changes and verify listeners are NOT called
        editOperation.save()
        assertEquals(0, enableCounter)
        assertEquals(0, enableAACounter)
        assertEquals(0, addRemoveFilterCounter)
        assertEquals(0, addRemoveSubscriptionCounter)

        editOperation.setEnabled(false).setAcceptableAdsEnabled(false).addCustomFilter(filter)
                .addSubscription(subscription)

        // Trigger first save() after changes and verify listeners are called
        editOperation.save()
        assertEquals(1, enableCounter)
        assertEquals(1, enableAACounter)
        assertEquals(1, addRemoveFilterCounter)
        assertEquals(1, addRemoveSubscriptionCounter)

        // Trigger second save() immediately after and verify listeners are NOT called due to lack of changes to save
        editOperation.save()
        assertEquals(1, enableCounter)
        assertEquals(1, enableAACounter)
        assertEquals(1, addRemoveFilterCounter)
        assertEquals(1, addRemoveSubscriptionCounter)

        // Now make changes and verify listeners are called
        editOperation.setEnabled(true)
                .setAcceptableAdsEnabled(true).removeCustomFilter(filter).removeSubscription(subscription).save()
        assertEquals(2, enableCounter)
        assertEquals(2, enableAACounter)
        assertEquals(2, addRemoveFilterCounter)
        assertEquals(2, addRemoveSubscriptionCounter)
    }

    @Test
    fun testSaveAndRemoveFilter() {
        val newDomain = "example.com"
        val newDomainFilter = AdblockFilterBuilder(adblockEngine)
                .setBeginMatchingDomain()
                .allowlistAddress(newDomain)
                .setContentTypes(ContentType.maskOf(ContentType.DOCUMENT))
                .setDomainRestriction(newDomain)
                .build()
        adblockEngine.settings().edit().addCustomFilter(newDomainFilter).save()
        assertEquals("@@||example.com^\$document,domain=example.com",
                adblockEngine.settings().listedFilters[0].text)

        val removeDomainFilter = AdblockFilterBuilder(adblockEngine)
                .setBeginMatchingDomain()
                .allowlistAddress(newDomain)
                .setContentTypes(ContentType.maskOf(ContentType.DOCUMENT))
                .setDomainRestriction(newDomain)
                .build()
        adblockEngine.settings().edit().removeCustomFilter(removeDomainFilter).save()
        assertEquals(0, adblockEngine.settings().listedFilters.size)
    }
}
