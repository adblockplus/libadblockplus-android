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

package org.adblockplus.libadblockplus.test;

import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.Subscription;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FilterEngineAATest extends BaseFilterEngineTest
{
  @Override
  public void setUp()
  {
    setUpAppInfo(AppInfo.builder()
        .setLocale("zh")
        .build());
    super.setUp();
  }

  @Test
  public void testLangAndAASubscriptionsAreChosenOnFirstRun()
  {
    final String langSubscriptionUrl = "https://easylist-downloads.adblockplus.org/easylistchina+easylist.txt";
    final List<Subscription> subscriptions = filterEngine.getListedSubscriptions();
    assertNotNull(subscriptions);
    assertEquals(2, subscriptions.size());

    Subscription aaSubscription = null;
    Subscription langSubscription = null;
    if (subscriptions.get(0).isAcceptableAds())
    {
      aaSubscription = subscriptions.get(0);
      langSubscription = subscriptions.get(1);
    }
    else if (subscriptions.get(1).isAcceptableAds())
    {
      aaSubscription = subscriptions.get(1);
      langSubscription = subscriptions.get(0);
    }
    assertNotNull(aaSubscription);
    assertNotNull(langSubscription);
    assertEquals(langSubscriptionUrl, langSubscription.getProperty("url").asString());
    assertTrue(filterEngine.isAcceptableAdsEnabled());
  }
}
