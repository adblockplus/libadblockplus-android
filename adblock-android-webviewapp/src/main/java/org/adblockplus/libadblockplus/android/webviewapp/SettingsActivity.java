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

package org.adblockplus.libadblockplus.android.webviewapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import timber.log.Timber;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.SubscriptionsManager;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;
import org.adblockplus.libadblockplus.android.settings.GeneralSettingsFragment;
import org.adblockplus.libadblockplus.android.settings.AdblockSettings;
import org.adblockplus.libadblockplus.android.settings.BaseSettingsFragment;
import org.adblockplus.libadblockplus.android.settings.AdblockSettingsStorage;
import org.adblockplus.libadblockplus.android.settings.WhitelistedDomainsSettingsFragment;

public class SettingsActivity
  extends AppCompatActivity
  implements
    BaseSettingsFragment.Provider,
    GeneralSettingsFragment.Listener,
    WhitelistedDomainsSettingsFragment.Listener
{
  private SubscriptionsManager subscriptionsManager;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    // retaining AdblockEngine asynchronously
    AdblockHelper.get().getProvider().retain(true);

    super.onCreate(savedInstanceState);

    insertGeneralFragment();

    // helps to configure subscriptions in runtime using Intents during the testing.
    // warning: DO NOT DO IT IN PRODUCTION CODE.
    subscriptionsManager = new SubscriptionsManager(this);
  }

  private void insertGeneralFragment()
  {
    getSupportFragmentManager()
      .beginTransaction()
      .replace(
        android.R.id.content,
        GeneralSettingsFragment.newInstance())
      .commit();
  }

  private void insertWhitelistedFragment()
  {
    getSupportFragmentManager()
      .beginTransaction()
      .replace(
        android.R.id.content,
        WhitelistedDomainsSettingsFragment.newInstance())
      .addToBackStack(WhitelistedDomainsSettingsFragment.class.getSimpleName())
      .commit();
  }

  // provider

  @Override
  public AdblockEngine getAdblockEngine()
  {
    // if it's retained asynchronously we have to wait until it's ready
    AdblockHelper.get().getProvider().waitForReady();
    return AdblockHelper.get().getProvider().getEngine();
  }

  @Override
  public AdblockSettingsStorage getAdblockSettingsStorage()
  {
    return AdblockHelper.get().getStorage();
  }

  // listener

  @Override
  public void onAdblockSettingsChanged(BaseSettingsFragment fragment)
  {
    Timber.d("AdblockHelper setting changed:\n%s" , fragment.getSettings().toString());
  }

  @Override
  public void onWhitelistedDomainsClicked(GeneralSettingsFragment fragment)
  {
    insertWhitelistedFragment();
  }

  @Override
  public boolean isValidDomain(WhitelistedDomainsSettingsFragment fragment,
                               String domain,
                               AdblockSettings settings)
  {
    // show error here if domain is invalid
    return domain != null && domain.length() > 0;
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    subscriptionsManager.dispose();
    AdblockHelper.get().getProvider().release();
  }
}
