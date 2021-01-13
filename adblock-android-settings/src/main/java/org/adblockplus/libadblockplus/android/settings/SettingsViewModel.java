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

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.ConnectionType;
import org.adblockplus.libadblockplus.android.Subscription;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

public class SettingsViewModel extends AndroidViewModel
{
  final private AdblockSettings settings;
  final private AdblockEngine engine;
  final private BaseSettingsFragment.Provider provider;

  private final MutableLiveData<CharSequence[]> availableSubscriptionsTitles = new MutableLiveData<>();
  private final MutableLiveData<CharSequence[]> availableSubscriptionsValues = new MutableLiveData<>();
  private final MutableLiveData<Set<String>> selectedSubscriptionValues = new MutableLiveData<>();

  protected MutableLiveData<CharSequence[]> getAvailableSubscriptionsTitles()
  {
    return availableSubscriptionsTitles;
  }

  protected SettingsViewModel(final Application application,
                              final AdblockSettings settings,
                              final AdblockEngine engine,
                              final BaseSettingsFragment.Provider provider)
  {
    super(application);
    this.settings = settings;
    this.engine = engine;
    this.provider = provider;
    initFilterListsValues();
  }

  protected MutableLiveData<CharSequence[]> getAvailableSubscriptionsValues()
  {
    return availableSubscriptionsValues;
  }

  protected MutableLiveData<Set<String>> getSelectedSubscriptionValues()
  {
    return selectedSubscriptionValues;
  }

  public boolean isAdblockEnabled()
  {
    return settings.isAdblockEnabled();
  }

  public boolean isAcceptableAdsEnabled()
  {
    return settings.isAcceptableAdsEnabled();
  }

  protected ConnectionType getAllowedConnectionType()
  {
    ConnectionType connectionType = settings.getAllowedConnectionType();
    if (connectionType == null)
    {
      connectionType = ConnectionType.ANY;
    }
    return connectionType;
  }

  private void initFilterListsValues()
  {
    final Map<String, String> localeToTitle = Utils.getLocaleToTitleMap(getApplication());

    // all available values
    final Subscription[] availableSubscriptions = engine.getRecommendedSubscriptions();
    final CharSequence[] availableSubscriptionsTitles = new CharSequence[availableSubscriptions.length];
    final CharSequence[] availableSubscriptionsValues = new CharSequence[availableSubscriptions.length];
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
    this.availableSubscriptionsTitles.postValue(availableSubscriptionsTitles);
    this.availableSubscriptionsValues.postValue(availableSubscriptionsValues);

    // selected values
    final Set<String> selectedSubscriptionValues = new HashSet<>();
    for (final Subscription eachSubscription : settings.getSubscriptions())
    {
      selectedSubscriptionValues.add(eachSubscription.url);
    }
    this.selectedSubscriptionValues.postValue(selectedSubscriptionValues);
  }

  protected void handleEnabledChanged(final boolean newValue)
  {
    // update and save settings
    settings.setAdblockEnabled(newValue);
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    engine.setEnabled(newValue);
  }

  protected void handleFilterListsChanged(final Set<String> newValue)
  {
    final List<Subscription> selectedSubscriptions = new LinkedList<>();

    for (final Subscription eachSubscription : engine.getRecommendedSubscriptions())
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
  }

  protected void handleAcceptableAdsEnabledChanged(final Boolean newValue)
  {
    // update and save settings
    settings.setAcceptableAdsEnabled(newValue);
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    engine.setAcceptableAdsEnabled(newValue);
  }

  protected void handleAllowedConnectionTypeChanged(final String value)
  {
    // update and save settings
    settings.setAllowedConnectionType(ConnectionType.findByValue(value));
    provider.getAdblockSettingsStorage().save(settings);

    engine.getFilterEngine().setAllowedConnectionType(value);
  }

  protected String prepareDomain(final String domain)
  {
    String text = domain.trim();

    try
    {
      final URL url = new URL(text);
      text = url.getHost();
    }
    catch (final MalformedURLException ignored)
    {
      // If the text can't be parsed as a valid URL, just assume that the user entered the hostname.
    }

    return text;
  }

  protected boolean addDomain(final String newDomain)
  {
    List<String> allowlistedDomains = settings.getAllowlistedDomains();
    if (allowlistedDomains == null)
    {
      allowlistedDomains = new LinkedList<>();
      settings.setAllowlistedDomains(allowlistedDomains);
    }
    if (!allowlistedDomains.contains(newDomain))
    {
      Timber.d("New allowlisted domain added: %s", newDomain);
      // update and save settings
      allowlistedDomains.add(newDomain);
      Collections.sort(allowlistedDomains);

      provider.getAdblockSettingsStorage().save(settings);

      // apply settings
      engine.addDomainAllowlistingFilter(newDomain);
      return true;
    }
    else
    {
      Timber.d("Allowlisted domain is already added: %s", newDomain);
      return false;
    }
  }

  protected void removeDomain(final int position)
  {
    final String removeDomain = settings.getAllowlistedDomains().get(position);
    Timber.i("Removing domain: %s, %d", removeDomain, position);

    settings.getAllowlistedDomains().remove(position);
    provider.getAdblockSettingsStorage().save(settings);

    // apply settings
    engine.removeDomainAllowlistingFilter(removeDomain);
  }

  protected int getAllowlistedDomainsCount()
  {
    return settings.getAllowlistedDomains() != null
        ? settings.getAllowlistedDomains().size()
        : 0;
  }

  protected Object getAllowlistedDomain(final int position)
  {
    return settings.getAllowlistedDomains().get(position);
  }

}
