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

package org.adblockplus.libadblockplus.android.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.adblockplus.ConnectionType;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import timber.log.Timber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SharedPrefsStorageTest
{
  protected static final String NAME = "prefs";

  protected SharedPreferences prefs;
  protected SharedPrefsStorage storage;

  @BeforeClass
  public static void beforeClass()
  {
    if (BuildConfig.DEBUG)
    {
      if (Timber.treeCount() == 0)
      {
        Timber.plant(new Timber.DebugTree());
      }
    }
  }

  public static AdblockSettings buildModel(final int subscriptionsCount,
                                           final int allowlistedDomainsCount)
  {
    final AdblockSettings settings = new AdblockSettings();
    settings.setAdblockEnabled(true);
    settings.setAcceptableAdsEnabled(true);
    settings.setAllowedConnectionType(ConnectionType.WIFI);

    final List<SubscriptionInfo> subscriptions = new LinkedList<>();
    for (int i = 0; i < subscriptionsCount; i++)
    {
      final SubscriptionInfo subscription = new SubscriptionInfo(
          "URL" + (i + 1),
          "Title" + (i + 1));
      subscriptions.add(subscription);
    }
    settings.setSelectedSubscriptions(subscriptions);

    final List<String> domains = new LinkedList<>();
    for (int i = 0; i < allowlistedDomainsCount; i++)
    {
      domains.add("http://www.domain" + (i + 1) + ".com");
    }
    settings.setAllowlistedDomains(domains);

    return settings;
  }

  public static void assertSettingsEquals(final AdblockSettings expected,
                                          final AdblockSettings actual)
  {
    assertEquals(expected.isAdblockEnabled(), actual.isAdblockEnabled());
    assertEquals(expected.isAcceptableAdsEnabled(), actual.isAcceptableAdsEnabled());
    assertEquals(expected.getAllowedConnectionType(), actual.getAllowedConnectionType());

    assertNotNull(actual.getSelectedSubscriptions());
    assertEquals(expected.getSelectedSubscriptions().size(),
        actual.getSelectedSubscriptions().size());
    for (int i = 0; i < expected.getSelectedSubscriptions().size(); i++)
    {
      assertEquals(expected.getSelectedSubscriptions().get(i).title,
          actual.getSelectedSubscriptions().get(i).title);
      assertEquals(expected.getSelectedSubscriptions().get(i).url,
          actual.getSelectedSubscriptions().get(i).url);
    }

    assertNotNull(actual.getAllowlistedDomains());
    assertEquals(expected.getAllowlistedDomains().size(), actual.getAllowlistedDomains().size());

    for (int i = 0; i < expected.getAllowlistedDomains().size(); i++)
    {
      assertEquals(expected.getAllowlistedDomains().get(i), actual.getAllowlistedDomains().get(i));
    }
  }

  protected void _testAdblockEnabled()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);
    storage.save(savedSettings);

    AdblockSettings loadedSettings = storage.load();
    assertEquals(savedSettings.isAdblockEnabled(), loadedSettings.isAdblockEnabled());

    savedSettings.setAdblockEnabled(false);
    storage.save(savedSettings);

    loadedSettings = storage.load();
    assertEquals(savedSettings.isAdblockEnabled(), loadedSettings.isAdblockEnabled());
  }

  @Before
  public void setUp()
  {
    prefs = ApplicationProvider.getApplicationContext()
        .getSharedPreferences(NAME, Context.MODE_PRIVATE);
    prefs.edit().clear().commit();

    storage = new SharedPrefsStorage(prefs);
    storage.setCommit(true);
  }

  @Test
  public void testNeverSaved()
  {
    final AdblockSettings settings = storage.load();
    assertNull(settings);
  }

  @Test
  public void testAdblockEnabled()
  {
    storage.setCommit(true);
    _testAdblockEnabled();
  }

  @Test
  public void testAdblockEnabled_Apply()
  {
    storage.setCommit(false); // commit is sync (for `true`), apply is async (for `false`)
    _testAdblockEnabled();
  }

  @Test
  public void testAcceptableAdsEnabled()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAcceptableAdsEnabled(true);
    storage.save(savedSettings);

    AdblockSettings loadedSettings = storage.load();
    assertEquals(savedSettings.isAcceptableAdsEnabled(), loadedSettings.isAcceptableAdsEnabled());

    savedSettings.setAcceptableAdsEnabled(false);
    storage.save(savedSettings);

    loadedSettings = storage.load();
    assertEquals(savedSettings.isAcceptableAdsEnabled(), loadedSettings.isAcceptableAdsEnabled());
  }

  @Test
  public void testSubscriptions_Null()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);
    assertNull(savedSettings.getSelectedSubscriptions());
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNull(loadedSettings.getSelectedSubscriptions());
  }

  @Test
  public void testSubscriptions_Empty()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);
    savedSettings.setSelectedSubscriptions(Collections.<SubscriptionInfo>emptyList());
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getSelectedSubscriptions());
    assertEquals(0, loadedSettings.getSelectedSubscriptions().size());
  }

  @Test
  public void testSubscriptions_OneValue()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);

    final List<SubscriptionInfo> subscriptions = new LinkedList<>();
    final SubscriptionInfo savedSubscription = new SubscriptionInfo("URL", "Title");
    subscriptions.add(savedSubscription);

    savedSettings.setSelectedSubscriptions(subscriptions);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getSelectedSubscriptions());
    assertEquals(1, loadedSettings.getSelectedSubscriptions().size());

    final SubscriptionInfo loadedSubscription = loadedSettings.getSelectedSubscriptions().get(0);
    assertEquals(savedSubscription.title, loadedSubscription.title);
    assertEquals(savedSubscription.url, loadedSubscription.url);
  }

  @Test
  public void testSubscriptions_OneValueNonEnglishTitle()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);

    final List<SubscriptionInfo> subscriptions = new LinkedList<>();
    // non-English characters
    final SubscriptionInfo savedSubscription = new SubscriptionInfo("URL", "Заголовок");
    subscriptions.add(savedSubscription);

    savedSettings.setSelectedSubscriptions(subscriptions);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getSelectedSubscriptions());
    assertEquals(1, loadedSettings.getSelectedSubscriptions().size());

    final SubscriptionInfo loadedSubscription = loadedSettings.getSelectedSubscriptions().get(0);
    assertEquals(savedSubscription.title, loadedSubscription.title);
    assertEquals(savedSubscription.url, loadedSubscription.url);
  }

  @Test
  public void testSubscriptions_OneValueNonEnglishUrl()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);

    final List<SubscriptionInfo> subscriptions = new LinkedList<>();
    // non-English characters
    final SubscriptionInfo savedSubscription = new SubscriptionInfo("http://почта.рф", "Title");
    subscriptions.add(savedSubscription);

    savedSettings.setSelectedSubscriptions(subscriptions);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getSelectedSubscriptions());
    assertEquals(1, loadedSettings.getSelectedSubscriptions().size());

    final SubscriptionInfo loadedSubscription = loadedSettings.getSelectedSubscriptions().get(0);
    assertEquals(savedSubscription.title, loadedSubscription.title);
    assertEquals(savedSubscription.url, loadedSubscription.url);
  }

  @Test
  public void testSubscriptions_MultipleValues()
  {
    AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);

    final List<SubscriptionInfo> subscriptions = new LinkedList<>();
    final SubscriptionInfo savedSubscription1 = new SubscriptionInfo("URL1", "Title1");
    subscriptions.add(savedSubscription1);

    final SubscriptionInfo savedSubscription2 = new SubscriptionInfo("URL2", "Title2");
    subscriptions.add(savedSubscription2);

    savedSettings.setSelectedSubscriptions(subscriptions);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getSelectedSubscriptions());
    assertEquals(2, loadedSettings.getSelectedSubscriptions().size());

    final SubscriptionInfo loadedSubscription1 = loadedSettings.getSelectedSubscriptions().get(0);
    assertEquals(savedSubscription1.title, loadedSubscription1.title);
    assertEquals(savedSubscription1.url, loadedSubscription1.url);

    final SubscriptionInfo loadedSubscription2 = loadedSettings.getSelectedSubscriptions().get(1);
    assertEquals(savedSubscription2.title, loadedSubscription2.title);
    assertEquals(savedSubscription2.url, loadedSubscription2.url);
  }

  @Test
  public void testSubscriptions_Selected_vs_Available()
  {
    AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);

    final List<SubscriptionInfo> selectedSubscriptions = new LinkedList<>();
    final SubscriptionInfo savedSubscription1 = new SubscriptionInfo("URL1", "Title1");
    selectedSubscriptions.add(savedSubscription1);

    final List<SubscriptionInfo> availableSubscriptions = new LinkedList<>();
    final SubscriptionInfo savedSubscription2 = new SubscriptionInfo("URL2", "Title2");
    availableSubscriptions.add(savedSubscription2);

    savedSettings.setSelectedSubscriptions(selectedSubscriptions);
    savedSettings.setAvailableSubscriptions(availableSubscriptions);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getSelectedSubscriptions());
    assertEquals(1, loadedSettings.getSelectedSubscriptions().size());

    final SubscriptionInfo loadedSubscription1 = loadedSettings.getSelectedSubscriptions().get(0);
    assertEquals(savedSubscription1.title, loadedSubscription1.title);
    assertEquals(savedSubscription1.url, loadedSubscription1.url);

    final SubscriptionInfo loadedSubscription2 = loadedSettings.getAvailableSubscriptions().get(0);
    assertEquals(savedSubscription2.title, loadedSubscription2.title);
    assertEquals(savedSubscription2.url, loadedSubscription2.url);
  }

  @Test
  public void testAllowlistedDomains_Null()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);
    assertNull(savedSettings.getAllowlistedDomains());
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNull(loadedSettings.getAllowlistedDomains());
  }

  @Test
  public void testAllowlistedDomains_Empty()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);
    savedSettings.setAllowlistedDomains(Collections.<String>emptyList());
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getAllowlistedDomains());
    assertEquals(0, loadedSettings.getAllowlistedDomains().size());
  }

  @Test
  public void testAllowlistedDomains_SingleValue()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);

    final List<String> allowlistedDomains = new LinkedList<>();
    allowlistedDomains.add("http://www.domain1.com");

    savedSettings.setAllowlistedDomains(allowlistedDomains);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getAllowlistedDomains());
    assertEquals(1, loadedSettings.getAllowlistedDomains().size());

    assertEquals(
        savedSettings.getAllowlistedDomains().get(0),
        loadedSettings.getAllowlistedDomains().get(0));
  }

  @Test
  public void testAllowlistedDomains_SingleValueNonEnglish()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);

    final List<String> allowlistedDomains = new LinkedList<>();
    allowlistedDomains.add("http://почта.рф");

    savedSettings.setAllowlistedDomains(allowlistedDomains);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getAllowlistedDomains());
    assertEquals(1, loadedSettings.getAllowlistedDomains().size());

    assertEquals(
        savedSettings.getAllowlistedDomains().get(0),
        loadedSettings.getAllowlistedDomains().get(0));
  }

  @Test
  public void testAllowlistedDomains_MultipleValues()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAdblockEnabled(true);

    final List<String> allowlistedDomains = new LinkedList<>();
    allowlistedDomains.add("http://www.domain1.com");
    allowlistedDomains.add("http://www.domain2.com");

    savedSettings.setAllowlistedDomains(allowlistedDomains);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNotNull(loadedSettings.getAllowlistedDomains());
    assertEquals(2, loadedSettings.getAllowlistedDomains().size());

    assertEquals(
        savedSettings.getAllowlistedDomains().get(0),
        loadedSettings.getAllowlistedDomains().get(0));

    assertEquals(
        savedSettings.getAllowlistedDomains().get(1),
        loadedSettings.getAllowlistedDomains().get(1));
  }

  @Test
  public void testAllowedConnectionType_Null()
  {
    final AdblockSettings savedSettings = new AdblockSettings();
    savedSettings.setAllowedConnectionType(null);
    assertNull(savedSettings.getAllowedConnectionType());
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();
    assertNull(loadedSettings.getAllowedConnectionType());
  }

  @Test
  public void testAllowedConnectionType()
  {
    final AdblockSettings savedSettings = new AdblockSettings();

    for (ConnectionType eachConnectionType : ConnectionType.values())
    {
      savedSettings.setAllowedConnectionType(eachConnectionType);
      storage.save(savedSettings);

      final AdblockSettings loadedSettings = storage.load();
      assertEquals(savedSettings.getAllowedConnectionType(),
          loadedSettings.getAllowedConnectionType());
    }
  }

  @Test
  public void testFullModel()
  {
    final AdblockSettings savedSettings = buildModel(2, 1);
    storage.save(savedSettings);

    final AdblockSettings loadedSettings = storage.load();

    assertSettingsEquals(savedSettings, loadedSettings);
  }

  @After
  public void tearDown()
  {
    prefs.edit().clear().commit();
  }
}
