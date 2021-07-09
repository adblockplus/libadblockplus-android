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

package org.adblockplus.android.settings;

import org.adblockplus.ConnectionType;

import java.io.Serializable;
import java.util.List;

/**
 * Adblock settings
 */
public class AdblockSettings implements Serializable
{
  private volatile boolean adblockEnabled;
  private volatile boolean acceptableAdsEnabled;
  private List<SubscriptionInfo> selectedSubscriptions;
  private List<SubscriptionInfo> availableSubscriptions;
  private List<String> allowlistedDomains;
  private ConnectionType allowedConnectionType;

  public boolean isAdblockEnabled()
  {
    return adblockEnabled;
  }

  public void setAdblockEnabled(final boolean adblockEnabled)
  {
    this.adblockEnabled = adblockEnabled;
  }

  public boolean isAcceptableAdsEnabled()
  {
    return acceptableAdsEnabled;
  }

  public void setAcceptableAdsEnabled(final boolean acceptableAdsEnabled)
  {
    this.acceptableAdsEnabled = acceptableAdsEnabled;
  }

  public List<SubscriptionInfo> getSelectedSubscriptions()
  {
    return selectedSubscriptions;
  }

  public void setSelectedSubscriptions(final List<SubscriptionInfo> selectedSubscriptions)
  {
    this.selectedSubscriptions = selectedSubscriptions;
  }

  public List<SubscriptionInfo> getAvailableSubscriptions()
  {
    return availableSubscriptions;
  }

  public void setAvailableSubscriptions(final List<SubscriptionInfo> availableSubscriptions)
  {
    this.availableSubscriptions = availableSubscriptions;
  }

  public List<String> getAllowlistedDomains()
  {
    return allowlistedDomains;
  }

  public void setAllowlistedDomains(final List<String> allowlistedDomains)
  {
    this.allowlistedDomains = allowlistedDomains;
  }

  public ConnectionType getAllowedConnectionType()
  {
    return allowedConnectionType;
  }

  public void setAllowedConnectionType(final ConnectionType allowedConnectionType)
  {
    this.allowedConnectionType = allowedConnectionType;
  }

  @Override
  public String toString()
  {
    return "AdblockSettings{" +
      "adblockEnabled=" + adblockEnabled +
      ", acceptableAdsEnabled=" + acceptableAdsEnabled +
      ", availableSubscriptions:" + (availableSubscriptions != null ? availableSubscriptions.size() : 0) +
      ", selectedSubscriptions:" + (selectedSubscriptions != null ? selectedSubscriptions.size() : 0) +
      ", allowlistedDomains:" + (allowlistedDomains != null ? allowlistedDomains.size() : 0) +
      ", allowedConnectionType=" + (allowedConnectionType != null ? allowedConnectionType.getValue() : "null") +
      '}';
  }
}
