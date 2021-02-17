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

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Settings storage base class
 */
public abstract class AdblockSettingsStorage
{
  /**
   * Load settings from the storage
   *
   * Warning: can return null if not saved yet
   * Warning: subscriptions can have `url` and `title` only to be identified among available in AdblockEngine
   * @return AdblockSettings instance or null
   */
  public abstract AdblockSettings load();

  /**
   * Save settings to the storage
   *
   * @param settings should be not null
   */
  public abstract void save(AdblockSettings settings);

  /**
   * Get default settings
   *
   * @param context application environment for getting resources
   * @return not null default settings
   */
  public static AdblockSettings getDefaultSettings(final Context context)
  {
    final AdblockSettings settings = new AdblockSettings();

    settings.setAdblockEnabled(true);
    settings.setAcceptableAdsEnabled(true);
    settings.setAllowedConnectionType(null);

    final InputStream inputStream = context.getResources().openRawResource(R.raw.subscriptions);
    final List<SubscriptionInfo> defaultSubscriptions =
        Utils.getSubscriptionsFromResourceStream(inputStream);
    settings.setAvailableSubscriptions(defaultSubscriptions);

    final SubscriptionInfo selectedSubscription =
        Utils.chooseDefaultSubscription(defaultSubscriptions);
    settings.setSelectedSubscriptions(
        selectedSubscription != null
            ? new LinkedList<>(Collections.singletonList(selectedSubscription))
            : new LinkedList<SubscriptionInfo>());

    return settings;
  }
}
