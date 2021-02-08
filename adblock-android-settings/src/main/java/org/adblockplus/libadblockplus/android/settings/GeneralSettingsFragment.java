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

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.adblockplus.libadblockplus.android.ConnectionType;

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
  private SettingsViewModel settingsViewModel;
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
   *
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
    final CharSequence[] values =
        {
            ConnectionType.WIFI_NON_METERED.getValue(),
            ConnectionType.WIFI.getValue(),
            ConnectionType.ANY.getValue()
        };

    final CharSequence[] titles =
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

  @Override
  protected void onSettingsReady()
  {
    initAcceptableAdsEnabled();
    initAllowlistedDomains();
    initUpdatesConnection();
    checkReadyAndInit();
  }

  @Override
  protected void onAdblockEngineReady()
  {
    checkReadyAndInit();
  }

  private void checkReadyAndInit()
  {
    if (settings != null)
    {
      initViewModel();
    }
  }

  private void initViewModel()
  {
    settingsViewModel = new ViewModelProvider(requireActivity(),
        new SettingsViewModelFactory(
            getActivity().getApplication(),
            settings,
            provider)).get(SettingsViewModel.class);

    initFilterLists();

    final boolean isAdblockEnabled = settingsViewModel.isAdblockEnabled();
    adblockEnabled.setChecked(isAdblockEnabled);
    adblockEnabled.setOnPreferenceChangeListener(this);
    applyAdblockEnabled(isAdblockEnabled);

    acceptableAdsEnabled.setChecked(settingsViewModel.isAcceptableAdsEnabled());
    acceptableAdsEnabled.setOnPreferenceChangeListener(this);

    allowedConnectionType.setValue(settingsViewModel.getAllowedConnectionType().getValue());
  }

  private void initFilterLists()
  {
    settingsViewModel.getAvailableSubscriptionsTitles().observe(this,
        new Observer<CharSequence[]>()
        {
          @Override
          public void onChanged(final CharSequence[] availableSubscriptionsTitles)
          {
            filterLists.setEntries(availableSubscriptionsTitles);
          }
        });

    settingsViewModel.getAvailableSubscriptionsValues().observe(this,
        new Observer<CharSequence[]>()
        {
          @Override
          public void onChanged(final CharSequence[] availableSubscriptionsValues)
          {
            filterLists.setEntryValues(availableSubscriptionsValues);
          }
        });

    settingsViewModel.getSelectedSubscriptionValues().observe(this,
        new Observer<Set<String>>()
        {
          @Override
          public void onChanged(final Set<String> selectedSubscriptionValues)
          {
            filterLists.setValues(selectedSubscriptionValues);
          }
        });
    filterLists.setOnPreferenceChangeListener(this);
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue)
  {
    Timber.d("\"%s\" new value is %s", preference.getTitle(), newValue);
    boolean arePresencesUpdated = true;

    if (preference.getKey().equals(SETTINGS_ENABLED_KEY))
    {
      final boolean isEnabled = (Boolean) newValue;
      settingsViewModel.handleEnabledChanged(isEnabled);
      // all other settings are meaningless if ad blocking is disabled
      applyAdblockEnabled(isEnabled);
    }
    else if (preference.getKey().equals(SETTINGS_FILTER_LISTS_KEY))
    {
      settingsViewModel.handleFilterListsChanged((Set<String>) newValue);
    }
    else if (preference.getKey().equals(SETTINGS_AA_ENABLED_KEY))
    {
      settingsViewModel.handleAcceptableAdsEnabledChanged((Boolean) newValue);
    }
    else if (preference.getKey().equals(SETTINGS_ALLOWED_CONNECTION_TYPE_KEY))
    {
      final String allowedConnectionTypeValue = (String) newValue;
      settingsViewModel.handleAllowedConnectionTypeChanged(allowedConnectionTypeValue);
      allowedConnectionType.setValue(allowedConnectionTypeValue);
    }
    else
    {
      arePresencesUpdated = false;
    }

    if (arePresencesUpdated)
    {
      // signal event
      listener.onAdblockSettingsChanged(this);
    }

    return arePresencesUpdated;
  }

  private void applyAdblockEnabled(final boolean enabledValue)
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
