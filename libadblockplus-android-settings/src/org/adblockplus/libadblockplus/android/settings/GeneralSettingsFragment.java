/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2016 Eyeo GmbH
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

import android.app.Activity;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.Log;

import org.adblockplus.libadblockplus.android.Subscription;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * General Adblock settings fragment.
 * Use the {@link GeneralSettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class GeneralSettingsFragment
  extends BaseSettingsFragment<GeneralSettingsFragment.Listener>
  implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener
{
  private String SETTINGS_ENABLED_KEY;
  private String SETTINGS_FILTER_LISTS_KEY;
  private String SETTINGS_AA_ENABLED_KEY;
  private String SETTINGS_WL_DOMAINS_KEY;

  private SwitchPreference adblockEnabled;
  private MultiSelectListPreference filterLists;
  private SwitchPreference acceptableAdsEnabled;
  private Preference whitelistedDomains;

  /**
   * Listener with additional `onWhitelistedDomainsClicked` event
   */
  public interface Listener extends BaseSettingsFragment.Listener
  {
    void onWhitelistedDomainsClicked(GeneralSettingsFragment fragment);
  }

  /**
   * Use this factory method to create a new instance of
   * this fragment using the provided parameters.
   * @return A new instance of fragment GeneralSettingsFragment.
   */
  public static GeneralSettingsFragment newInstance()
  {
    return new GeneralSettingsFragment();
  }

  public GeneralSettingsFragment()
  {
    // required empty public constructor
  }

  @Override
  public void onAttach(Activity activity)
  {
    super.onAttach(activity);
    listener = castOrThrow(activity, Listener.class);
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    readKeys();

    addPreferencesFromResource(R.xml.preference_adblock_general);
    bindPreferences();
  }

  @Override
  public void onResume()
  {
    super.onResume();
    initPreferences();
  }

  private void readKeys()
  {
    SETTINGS_ENABLED_KEY = getString(R.string.fragment_adblock_settings_enabled_key);
    SETTINGS_FILTER_LISTS_KEY = getString(R.string.fragment_adblock_settings_filter_lists_key);
    SETTINGS_AA_ENABLED_KEY = getString(R.string.fragment_adblock_settings_aa_enabled_key);
    SETTINGS_WL_DOMAINS_KEY = getString(R.string.fragment_adblock_settings_wl_key);
  }

  private void bindPreferences()
  {
    adblockEnabled = (SwitchPreference) findPreference(SETTINGS_ENABLED_KEY);
    filterLists = (MultiSelectListPreference) findPreference(SETTINGS_FILTER_LISTS_KEY);
    acceptableAdsEnabled = (SwitchPreference) findPreference(SETTINGS_AA_ENABLED_KEY);
    whitelistedDomains = findPreference(SETTINGS_WL_DOMAINS_KEY);
  }

  private void initPreferences()
  {
    initEnabled();
    initFilterLists();
    initAcceptableAdsEnabled();
    initWhitelistedDomains();
  }

  private void initWhitelistedDomains()
  {
    whitelistedDomains.setOnPreferenceClickListener(this);
  }

  private void initAcceptableAdsEnabled()
  {
    acceptableAdsEnabled.setChecked(settings.isAcceptableAdsEnabled());
    acceptableAdsEnabled.setOnPreferenceChangeListener(this);
  }

  private void initFilterLists()
  {
    // all available values
    Subscription[] availableSubscriptions = provider.getAdblockEngine().getRecommendedSubscriptions();
    CharSequence[] availableSubscriptionsTitles = new CharSequence[availableSubscriptions.length];
    CharSequence[] availableSubscriptionsValues = new CharSequence[availableSubscriptions.length];
    for (int i = 0; i < availableSubscriptions.length; i++)
    {
      availableSubscriptionsTitles[i] = availableSubscriptions[i].title;
      availableSubscriptionsValues[i] = availableSubscriptions[i].url;
    }
    filterLists.setEntries(availableSubscriptionsTitles);
    filterLists.setEntryValues(availableSubscriptionsValues);

    // selected values
    Set<String> selectedSubscriptionValues = new HashSet<String>();
    for (Subscription eachSubscription : settings.getSubscriptions())
    {
      selectedSubscriptionValues.add(eachSubscription.url);
    }
    filterLists.setValues(selectedSubscriptionValues);
    filterLists.setOnPreferenceChangeListener(this);
  }

  private void initEnabled()
  {
    boolean enabled = settings.isAdblockEnabled();
    adblockEnabled.setChecked(enabled);
    adblockEnabled.setOnPreferenceChangeListener(this);
    applyAdblockEnabled(enabled);
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue)
  {
    Log.d(TAG, "\"" + preference.getTitle() + "\" new value is " + newValue);

    if (preference.getKey().equals(SETTINGS_ENABLED_KEY))
    {
      handleEnabledChanged((Boolean)newValue);
    }
    else if (preference.getKey().equals(SETTINGS_FILTER_LISTS_KEY))
    {
      handleFilterListsChanged((Set<String>) newValue);
    }
    else if (preference.getKey().equals(SETTINGS_AA_ENABLED_KEY))
    {
      handleAcceptableAdsEnabledChanged((Boolean) newValue);
    }
    else
    {
      // handle other values if changed
      // `false` for NOT update preference view state
      return false;
    }

    // `true` for update preference view state
    return true;
  }

  private void handleAcceptableAdsEnabledChanged(Boolean newValue)
  {
    boolean enabledValue = newValue;

    // update and save settings
    settings.setAcceptableAdsEnabled(enabledValue);
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    provider.getAdblockEngine().setAcceptableAdsEnabled(enabledValue);

    // signal event
    listener.onAdblockSettingsChanged(this);
  }

  private void handleFilterListsChanged(Set<String> newValue)
  {
    List<Subscription> selectedSubscriptions = new LinkedList<Subscription>();

    for (Subscription eachSubscription : provider.getAdblockEngine().getRecommendedSubscriptions())
    {
      if (newValue.contains(eachSubscription.url))
      {
        selectedSubscriptions.add(eachSubscription);
      }
    }

    // update and save settings
    settings.setSubscriptions(selectedSubscriptions);
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    provider.getAdblockEngine().setSubscriptions(newValue);

    // since 'aa enabled' setting affects subscriptions list, we need to set it again
    provider.getAdblockEngine().setAcceptableAdsEnabled(settings.isAcceptableAdsEnabled());

    // signal event
    listener.onAdblockSettingsChanged(this);
  }

  private void handleEnabledChanged(boolean newValue)
  {
    // update and save settings
    settings.setAdblockEnabled(newValue);
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    provider.getAdblockEngine().setEnabled(newValue);

    // signal event
    listener.onAdblockSettingsChanged(this);

    // all other settings are meaningless if adblocking is disabled
    applyAdblockEnabled(newValue);
  }

  private void applyAdblockEnabled(boolean enabledValue)
  {
    filterLists.setEnabled(enabledValue);
    acceptableAdsEnabled.setEnabled(enabledValue);
    whitelistedDomains.setEnabled(enabledValue);
  }

  @Override
  public boolean onPreferenceClick(Preference preference)
  {
    if (preference.getKey().equals(SETTINGS_WL_DOMAINS_KEY))
    {
      listener.onWhitelistedDomainsClicked(this);
    }
    else
    {
      // should not be invoked as only 'wl' preference is subscribed for callback
      return false;
    }

    return true;
  }
}
