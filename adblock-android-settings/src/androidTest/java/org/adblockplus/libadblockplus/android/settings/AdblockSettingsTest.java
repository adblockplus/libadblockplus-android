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

import org.adblockplus.libadblockplus.android.ConnectionType;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdblockSettingsTest
{
  private static AdblockSettings buildModel(final int subscriptionsCount,
                                            final int allowlistedDomainsCount)
  {
    final AdblockSettings settings = new AdblockSettings();
    settings.setAdblockEnabled(true);
    settings.setAcceptableAdsEnabled(true);
    settings.setAllowedConnectionType(ConnectionType.WIFI);

    final List<SubscriptionInfo> subscriptions = new LinkedList<>();
    for (int i = 0; i < subscriptionsCount; i++)
    {
      subscriptions.add(new SubscriptionInfo("URL" + (i + 1), "Title" + (i + 1)));
    }
    settings.setSelectedSubscriptions(subscriptions);

    final List<String> domains = new LinkedList<>();
    for (int i = 0; i < allowlistedDomainsCount; i++)
    {
      domains.add("www.domain" + (i + 1) + ".com");
    }
    settings.setAllowlistedDomains(domains);

    return settings;
  }

  @Test
  public void testAdblockEnabled()
  {
    final AdblockSettings settings = new AdblockSettings();
    settings.setAdblockEnabled(true);
    assertTrue(settings.isAdblockEnabled());

    settings.setAdblockEnabled(false);
    assertFalse(settings.isAdblockEnabled());
  }

  @Test
  public void testAcceptableAds()
  {
    final AdblockSettings settings = new AdblockSettings();
    settings.setAcceptableAdsEnabled(true);
    assertTrue(settings.isAcceptableAdsEnabled());

    settings.setAcceptableAdsEnabled(false);
    assertFalse(settings.isAcceptableAdsEnabled());
  }

  @Test
  public void testAllowedConnectionType()
  {
    final AdblockSettings settings = new AdblockSettings();
    for (ConnectionType eachConnectionType : ConnectionType.values())
    {
      settings.setAllowedConnectionType(eachConnectionType);
      assertEquals(eachConnectionType, settings.getAllowedConnectionType());
    }
  }

  @Test
  public void testSubscriptions()
  {
    for (int i = 0; i < 3; i++)
    {
      final AdblockSettings settings = buildModel(i, 1);
      assertEquals(i, settings.getSelectedSubscriptions().size());
    }
  }

  @Test
  public void testAllowlistedDomains()
  {
    for (int i = 0; i < 3; i++)
    {
      final AdblockSettings settings = buildModel(1, i);
      assertEquals(i, settings.getAllowlistedDomains().size());
    }
  }
}
