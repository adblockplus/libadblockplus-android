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

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import org.adblockplus.libadblockplus.android.ConnectionType;

import java.util.LinkedList;
import java.util.List;

/**
 * Settings storage implementation in Shared Preferences
 */
public class SharedPrefsStorage extends AdblockSettingsStorage
{
  private static final String SETTINGS_ENABLED_KEY = "enabled";
  private static final String SETTINGS_AA_ENABLED_KEY = "aa_enabled";
  private static final String SETTINGS_SELECTED_SUBSCRIPTION_ITEM_KEY = "subscription";
  private static final String SETTINGS_AVAILABLE_SUBSCRIPTION_ITEM_KEY = "availableSubscription";
  private static final String SETTINGS_SUBSCRIPTION_COUNT_KEY_SUFFIX = "s";
  private static final String SETTINGS_SUBSCRIPTION_URL_KEY = "url";
  private static final String SETTINGS_SUBSCRIPTION_PREFIXES_KEY = "prefixes";
  private static final String SETTINGS_SUBSCRIPTION_TITLE_KEY = "title";
  private static final String SETTINGS_AL_DOMAINS_KEY = "allowlisted_domains";
  private static final String SETTINGS_AL_DOMAIN_KEY = "domain";
  private static final String SETTINGS_ALLOWED_CONNECTION_TYPE_KEY = "allowed_connection_type";

  private final SharedPreferences prefs;
  private boolean commit = true;

  public SharedPrefsStorage(final SharedPreferences prefs)
  {
    this.prefs = prefs;
  }

  public boolean isCommit()
  {
    return commit;
  }

  /**
   * Do commit the changes in save() before return
   *
   * @param commit `true` to commit, `false`
   */
  public void setCommit(final boolean commit)
  {
    this.commit = commit;
  }

  @Override
  public AdblockSettings load()
  {
    if (!prefs.contains(SETTINGS_ENABLED_KEY))
    {
      // settings were not saved yet
      return null;
    }

    final AdblockSettings settings = new AdblockSettings();
    settings.setAdblockEnabled(prefs.getBoolean(SETTINGS_ENABLED_KEY, true));
    settings.setAcceptableAdsEnabled(prefs.getBoolean(SETTINGS_AA_ENABLED_KEY, true));
    final String connectionType = prefs.getString(SETTINGS_ALLOWED_CONNECTION_TYPE_KEY, null);
    settings.setAllowedConnectionType(ConnectionType.findByValue(connectionType));

    List<SubscriptionInfo> subscriptions =
        loadSubscriptions(SETTINGS_SELECTED_SUBSCRIPTION_ITEM_KEY);
    if (subscriptions != null)
    {
      settings.setSelectedSubscriptions(subscriptions);
    }
    subscriptions = loadSubscriptions(SETTINGS_AVAILABLE_SUBSCRIPTION_ITEM_KEY);
    if (subscriptions != null)
    {
      settings.setAvailableSubscriptions(subscriptions);
    }

    loadAllowlistedDomains(settings);

    return settings;
  }

  private void loadAllowlistedDomains(final AdblockSettings settings)
  {
    if (prefs.contains(SETTINGS_AL_DOMAINS_KEY))
    {
      // count
      final int allowlistedDomainsCount = prefs.getInt(SETTINGS_AL_DOMAINS_KEY, 0);

      // each domain
      final List<String> allowlistedDomains = new LinkedList<>();
      for (int i = 0; i < allowlistedDomainsCount; i++)
      {
        final String allowlistedDomain = prefs.getString(getArrayItemKey(i,
            SETTINGS_AL_DOMAIN_KEY), "");
        allowlistedDomains.add(allowlistedDomain);
      }
      settings.setAllowlistedDomains(allowlistedDomains);
    }
  }

  private List<SubscriptionInfo> loadSubscriptions(final String subscriptionItemKey)
  {
    if (!prefs.contains(getCountKey(subscriptionItemKey)))
    {
      return null;
    }
    final int subscriptionsCount = prefs.getInt(getCountKey(subscriptionItemKey), 0);
    final List<SubscriptionInfo> subscriptions = new LinkedList<>();
    for (int i = 0; i < subscriptionsCount; i++)
    {
      subscriptions.add(new SubscriptionInfo(
        prefs.getString(getSubscriptionURLKey(subscriptionItemKey, i), ""),
        prefs.getString(getSubscriptionTitleKey(subscriptionItemKey, i), ""),
        prefs.getString(getSubscriptionPrefixesKey(subscriptionItemKey, i), ""), "", ""));
    }
    return subscriptions;
  }

  private static String getArrayItemKey(final int index, final String entity)
  {
    // f.e. "domain0"
    return entity + index;
  }

  private static String getCountKey(final String subscriptionItemKey)
  {
    return subscriptionItemKey + SETTINGS_SUBSCRIPTION_COUNT_KEY_SUFFIX;
  }

  private String getSubscriptionItemKey(final String subscriptionKey, final int index, final String field)
  {
    // f.e. `subscription0.field`
    return getArrayItemKey(index, subscriptionKey) + "." + field;
  }

  private String getSubscriptionTitleKey(final String subscriptionKey, final int index)
  {
    return getSubscriptionItemKey(subscriptionKey, index, SETTINGS_SUBSCRIPTION_TITLE_KEY);
  }

  private String getSubscriptionURLKey(final String subscriptionKey, final int index)
  {
    return getSubscriptionItemKey(subscriptionKey, index, SETTINGS_SUBSCRIPTION_URL_KEY);
  }

  private String getSubscriptionPrefixesKey(final String subscriptionKey, final int index)
  {
    return getSubscriptionItemKey(subscriptionKey, index, SETTINGS_SUBSCRIPTION_PREFIXES_KEY);
  }

  @SuppressLint("ApplySharedPref")
  @Override
  public void save(final AdblockSettings settings)
  {
    final SharedPreferences.Editor editor = prefs
      .edit()
      .clear()
      .putBoolean(SETTINGS_ENABLED_KEY, settings.isAdblockEnabled())
      .putBoolean(SETTINGS_AA_ENABLED_KEY, settings.isAcceptableAdsEnabled());

    if (settings.getAllowedConnectionType() != null)
    {
      editor.putString(SETTINGS_ALLOWED_CONNECTION_TYPE_KEY,
          settings.getAllowedConnectionType().getValue());
    }

    saveSubscriptions(editor, settings.getSelectedSubscriptions(),
        SETTINGS_SELECTED_SUBSCRIPTION_ITEM_KEY);
    saveSubscriptions(editor, settings.getAvailableSubscriptions(),
        SETTINGS_AVAILABLE_SUBSCRIPTION_ITEM_KEY);
    saveAllowlistedDomains(settings, editor);

    if (commit)
    {
      editor.commit();
    }
    else
    {
      // faster but not finished most likely before return
      editor.apply();
    }
  }

  private void saveAllowlistedDomains(final AdblockSettings settings,
                                      final SharedPreferences.Editor editor)
  {
    if (settings.getAllowlistedDomains() != null)
    {
      // count
      editor.putInt(SETTINGS_AL_DOMAINS_KEY, settings.getAllowlistedDomains().size());

      // each domain
      for (int i = 0; i < settings.getAllowlistedDomains().size(); i++)
      {
        final String eachDomain = settings.getAllowlistedDomains().get(i);
        editor.putString(getArrayItemKey(i, SETTINGS_AL_DOMAIN_KEY), eachDomain);
      }
    }
  }

  private void saveSubscriptions(final SharedPreferences.Editor editor,
                                 final List<SubscriptionInfo> subscriptions,
                                 final String subscriptionItemKey)
  {
    if (subscriptions != null)
    {
      // count
      editor.putInt(getCountKey(subscriptionItemKey), subscriptions.size());

      // each subscription
      for (int i = 0; i < subscriptions.size(); i++)
      {
        final SubscriptionInfo eachSubscription = subscriptions.get(i);

        // warning: saving `title`, `url` and `prefixes` fields only
        editor.putString(getSubscriptionTitleKey(subscriptionItemKey, i), eachSubscription.title);
        editor.putString(getSubscriptionURLKey(subscriptionItemKey, i), eachSubscription.url);
        editor.putString(getSubscriptionPrefixesKey(subscriptionItemKey, i),
            eachSubscription.languages);
      }
    }
  }
}
