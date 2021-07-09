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

package org.adblockplus.android.webviewapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.adblockplus.AdblockEngine;
import org.adblockplus.android.AdblockEngineProvider;
import org.adblockplus.android.SubscriptionsManager;
import org.adblockplus.android.settings.AdblockHelper;
import org.adblockplus.android.settings.AdblockSettings;
import org.adblockplus.android.settings.AdblockSettingsStorage;
import org.adblockplus.android.settings.AllowlistedDomainsSettingsFragment;
import org.adblockplus.android.settings.BaseSettingsFragment;
import org.adblockplus.android.settings.GeneralSettingsFragment;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import timber.log.Timber;

public class SettingsActivity
  extends AppCompatActivity
  implements
    BaseSettingsFragment.Provider,
    GeneralSettingsFragment.Listener,
    AllowlistedDomainsSettingsFragment.Listener
{
  private SubscriptionsManager subscriptionsManager;

  @Override
  protected void onCreate(final Bundle savedInstanceState)
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

  private void insertAllowlistedFragment()
  {
    getSupportFragmentManager()
      .beginTransaction()
      .replace(
        android.R.id.content,
        AllowlistedDomainsSettingsFragment.newInstance())
      .addToBackStack(AllowlistedDomainsSettingsFragment.class.getSimpleName())
      .commit();
  }

  // provider

  @Override
  public AdblockEngineProvider getAdblockEngineProvider()
  {
    return AdblockHelper.get().getProvider();
  }

  @Override
  public AdblockSettingsStorage getAdblockSettingsStorage()
  {
    return AdblockHelper.get().getStorage();
  }

  @Override
  public AdblockEngine lockEngine()
  {
    final AdblockEngineProvider adblockEngineProvider = getAdblockEngineProvider();
    final ReentrantReadWriteLock.ReadLock lock = adblockEngineProvider.getReadEngineLock();
    final boolean locked = lock.tryLock();

    if (!locked)
    {
      return null;
    }

    final AdblockEngine adblockEngine = adblockEngineProvider.getEngine();
    if (adblockEngine != null)
    {
      return adblockEngine;
    }

    if (locked)
    {
      lock.unlock();
    }
    return null;
  }

  /**
   * Should only be called if prior call to lockEngine returned a non-null reference
   * If the current thread does not hold this lock then IllegalMonitorStateException is thrown.
   */
  @Override
  public void unlockEngine()
  {
    getAdblockEngineProvider().getReadEngineLock().unlock();
  }

  // listener

  @Override
  public void onAdblockSettingsChanged(final BaseSettingsFragment fragment)
  {
    Timber.d("AdblockHelper setting changed:\n%s" , fragment.getSettings().toString());
  }

  @Override
  public void onAllowlistedDomainsClicked(final GeneralSettingsFragment fragment)
  {
    insertAllowlistedFragment();
  }

  @Override
  public boolean isValidDomain(final AllowlistedDomainsSettingsFragment fragment,
                               final String domain,
                               final AdblockSettings settings)
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
