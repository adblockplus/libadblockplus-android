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

import androidx.preference.PreferenceFragmentCompat;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;

import timber.log.Timber;

public abstract class BaseSettingsFragment
  <ListenerClass extends BaseSettingsFragment.Listener>
  extends PreferenceFragmentCompat
{
  protected AdblockSettings settings;
  protected Provider provider;
  protected ListenerClass listener;

  /**
   * Provides AdblockEngine and SharedPreferences to store settings
   * (activity holding BaseSettingsFragment fragment should implement this interface)
   */
  public interface Provider
  {
    AdblockEngineProvider getAdblockEngineProvider();

    AdblockSettingsStorage getAdblockSettingsStorage();

    AdblockEngine lockEngine();

    void unlockEngine();
  }

  /**
   * Listens for Adblock settings events
   */
  public interface Listener
  {
    /**
     * `Settings were changed` callback
     * Note: settings are available using BaseSettingsFragment.getSettings()
     *
     * @param fragment fragment
     */
    void onAdblockSettingsChanged(BaseSettingsFragment fragment);
  }

  protected <T> T castOrThrow(final Activity activity, final Class<T> clazz)
  {
    if (!(activity instanceof Provider))
    {
      final String message = activity.getClass().getSimpleName()
        + " should implement "
        + clazz.getSimpleName()
        + " interface";

      Timber.e(message);
      throw new RuntimeException(message);
    }

    return clazz.cast(activity);
  }

  protected abstract void onSettingsReady();

  private final AdblockEngineProvider.EngineCreatedListener engineCreatedListener = new
    AdblockEngineProvider.EngineCreatedListener()
    {
      @Override
      public void onAdblockEngineCreated(final AdblockEngine engine)
      {
        onAdblockEngineReadyInternal();
      }
    };

  protected abstract void onAdblockEngineReady();

  private void onAdblockEngineReadyInternal()
  {
    onAdblockEngineReady();
  }

  private void startLoadSettings()
  {
    settings = provider.getAdblockSettingsStorage().load();
    if (settings == null)
    {
      // null because it was not saved yet
      Timber.w("No adblock settings, yet. Using default ones from adblock engine");
      settings = AdblockSettingsStorage.getDefaultSettings(getActivity());
    }
    onSettingsReady();
  }

  @Override
  public void onAttach(final Activity activity)
  {
    super.onAttach(activity);
    provider = castOrThrow(activity, Provider.class);
  }

  @Override
  public void onResume()
  {
    super.onResume();
    provider.getAdblockEngineProvider().addEngineCreatedListener(engineCreatedListener);
    startLoadSettings();
  }

  @Override
  public void onPause()
  {
    provider.getAdblockEngineProvider().removeEngineCreatedListener(engineCreatedListener);
    super.onPause();
  }

  public AdblockSettings getSettings()
  {
    return settings;
  }

  @Override
  public void onDetach()
  {
    super.onDetach();
    provider = null;
    listener = null;
  }
}
