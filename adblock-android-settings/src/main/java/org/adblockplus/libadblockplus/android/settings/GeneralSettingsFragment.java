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

import android.app.Activity;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.adblockplus.libadblockplus.android.ConnectionType;
import org.adblockplus.libadblockplus.android.Subscription;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

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
  private String SETTINGS_AL_DOMAINS_KEY;
  private String SETTINGS_ALLOWED_CONNECTION_TYPE_KEY;

  private SwitchPreferenceCompat adblockEnabled;
  private MultiSelectListPreference filterLists;
  private SwitchPreferenceCompat acceptableAdsEnabled;
  private Preference allowlistedDomains;
  private ListPreference allowedConnectionType;

  /**
   * Listener with additional `onAllowlistedDomainsClicked` event
   */
  public interface Listener extends BaseSettingsFragment.Listener
  {
    void onAllowlistedDomainsClicked(GeneralSettingsFragment fragment);
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
  public void onCreatePreferences(Bundle bundle, String key)
  {
    readKeys();
    addPreferencesFromResource(R.xml.preference_adblock_general);
    bindPreferences();
  }

  private void readKeys()
  {
    SETTINGS_ENABLED_KEY = getString(R.string.fragment_adblock_settings_enabled_key);
    SETTINGS_FILTER_LISTS_KEY = getString(R.string.fragment_adblock_settings_filter_lists_key);
    SETTINGS_AA_ENABLED_KEY = getString(R.string.fragment_adblock_settings_aa_enabled_key);
    SETTINGS_AL_DOMAINS_KEY = getString(R.string.fragment_adblock_settings_al_key);
    SETTINGS_ALLOWED_CONNECTION_TYPE_KEY = getString(R.string.fragment_adblock_settings_allowed_connection_type_key);
  }

  private void bindPreferences()
  {
    adblockEnabled = (SwitchPreferenceCompat) findPreference(SETTINGS_ENABLED_KEY);
    filterLists = (MultiSelectListPreference) findPreference(SETTINGS_FILTER_LISTS_KEY);
    acceptableAdsEnabled = (SwitchPreferenceCompat) findPreference(SETTINGS_AA_ENABLED_KEY);
    allowlistedDomains = findPreference(SETTINGS_AL_DOMAINS_KEY);
    allowedConnectionType = (ListPreference) findPreference(SETTINGS_ALLOWED_CONNECTION_TYPE_KEY);
  }

  private void initUpdatesConnection()
  {
    CharSequence[] values =
    {
      ConnectionType.WIFI_NON_METERED.getValue(),
      ConnectionType.WIFI.getValue(),
      ConnectionType.ANY.getValue()
    };

    CharSequence[] titles =
    {
      getString(R.string.fragment_adblock_settings_allowed_connection_type_wifi_non_metered),
      getString(R.string.fragment_adblock_settings_allowed_connection_type_wifi),
      getString(R.string.fragment_adblock_settings_allowed_connection_type_all),
    };

    allowedConnectionType.setEntryValues(values);
    allowedConnectionType.setEntries(titles);

    // selected value
    ConnectionType connectionType = settings.getAllowedConnectionType();
    if (connectionType == null)
    {
      connectionType = ConnectionType.ANY;
    }
    allowedConnectionType.setValue(connectionType.getValue());
    allowedConnectionType.setOnPreferenceChangeListener(this);
  }

  private void initAllowlistedDomains()
  {
    allowlistedDomains.setOnPreferenceClickListener(this);
  }

  private void initAcceptableAdsEnabled()
  {
    acceptableAdsEnabled.setChecked(settings.isAcceptableAdsEnabled());
    acceptableAdsEnabled.setOnPreferenceChangeListener(this);
  }

  private void initFilterLists()
  {
    final Map<String, String> localeToTitle = Utils.getLocaleToTitleMap(getContext());

    // all available values
    Subscription[] availableSubscriptions = engine.getRecommendedSubscriptions();
    CharSequence[] availableSubscriptionsTitles = new CharSequence[availableSubscriptions.length];
    CharSequence[] availableSubscriptionsValues = new CharSequence[availableSubscriptions.length];
    for (int i = 0; i < availableSubscriptions.length; i++)
    {
      String title = null;
      if (availableSubscriptions[i].prefixes != null &&
          !availableSubscriptions[i].prefixes.isEmpty())
      {
        final String[] separatedPrefixes = availableSubscriptions[i].prefixes.split(",");
        title = localeToTitle.get(separatedPrefixes[0]);
      }
      availableSubscriptionsTitles[i] = title != null ? title : availableSubscriptions[i].title;
      availableSubscriptionsValues[i] = availableSubscriptions[i].url;
    }
    filterLists.setEntries(availableSubscriptionsTitles);
    filterLists.setEntryValues(availableSubscriptionsValues);

    // selected values
    Set<String> selectedSubscriptionValues = new HashSet<>();
    for (Subscription eachSubscription : settings.getSubscriptions())
    {
      selectedSubscriptionValues.add(eachSubscription.url);
    }
    filterLists.setValues(selectedSubscriptionValues);
    filterLists.setOnPreferenceChangeListener(this);
  }

  @Override
  protected void onSettingsReady()
  {
    initEnabled();
    initAcceptableAdsEnabled();
    initAllowlistedDomains();
    initUpdatesConnection();
    checkReadyAndInitFilterLists();
  }

  @Override
  protected void onAdblockEngineReady()
  {
    checkReadyAndInitFilterLists();
  }

  private void checkReadyAndInitFilterLists()
  {
    if (engine != null && settings != null)
    {
      initFilterLists();
    }
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
    Timber.d("\"%s\" new value is %s", preference.getTitle(), newValue);

    if (preference.getKey().equals(SETTINGS_ENABLED_KEY))
    {
      handleEnabledChanged((Boolean)newValue);
    }
    else if (preference.getKey().equals(SETTINGS_FILTER_LISTS_KEY))
    {
      //noinspection unchecked
      handleFilterListsChanged((Set<String>) newValue);
    }
    else if (preference.getKey().equals(SETTINGS_AA_ENABLED_KEY))
    {
      handleAcceptableAdsEnabledChanged((Boolean) newValue);
    }
    else if (preference.getKey().equals(SETTINGS_ALLOWED_CONNECTION_TYPE_KEY))
    {
      handleAllowedConnectionTypeChanged((String) newValue);
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

  private void handleAllowedConnectionTypeChanged(String value)
  {
    // update and save settings
    settings.setAllowedConnectionType(ConnectionType.findByValue(value));
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    allowedConnectionType.setValue(value);
    engine.getFilterEngine().setAllowedConnectionType(value);

    // signal event
    listener.onAdblockSettingsChanged(this);
  }

  private void handleAcceptableAdsEnabledChanged(Boolean newValue)
  {
    boolean enabledValue = newValue;

    // update and save settings
    settings.setAcceptableAdsEnabled(enabledValue);
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    engine.setAcceptableAdsEnabled(enabledValue);

    // signal event
    listener.onAdblockSettingsChanged(this);
  }

  private void handleFilterListsChanged(Set<String> newValue)
  {
    List<Subscription> selectedSubscriptions = new LinkedList<>();

    for (Subscription eachSubscription : engine.getRecommendedSubscriptions())
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
    engine.setSubscriptions(newValue);

    // since 'aa enabled' setting affects subscriptions list, we need to set it again
    engine.setAcceptableAdsEnabled(settings.isAcceptableAdsEnabled());

    // signal event
    listener.onAdblockSettingsChanged(this);
  }

  private void handleEnabledChanged(boolean newValue)
  {
    // update and save settings
    settings.setAdblockEnabled(newValue);
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    engine.setEnabled(newValue);

    // signal event
    listener.onAdblockSettingsChanged(this);

    // all other settings are meaningless if ad blocking is disabled
    applyAdblockEnabled(newValue);
  }

  private void applyAdblockEnabled(boolean enabledValue)
  {
    filterLists.setEnabled(enabledValue);
    acceptableAdsEnabled.setEnabled(enabledValue);
    allowlistedDomains.setEnabled(enabledValue);
    allowedConnectionType.setEnabled(enabledValue);
  }

  @Override
  public boolean onPreferenceClick(Preference preference)
  {
    if (preference.getKey().equals(SETTINGS_AL_DOMAINS_KEY))
    {
      listener.onAllowlistedDomainsClicked(this);
    }
    else
    {
      // should not be invoked as only 'wl' preference is subscribed for callback
      return false;
    }

    return true;
  }
}
