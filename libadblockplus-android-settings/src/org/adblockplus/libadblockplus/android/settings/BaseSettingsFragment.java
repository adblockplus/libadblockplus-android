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
import android.preference.PreferenceFragment;
import android.util.Log;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.Utils;

public abstract class BaseSettingsFragment
  <ListenerClass extends BaseSettingsFragment.Listener>
  extends PreferenceFragment
{
  protected final String TAG = Utils.getTag(this.getClass());
  protected AdblockSettings settings;
  protected Provider provider;
  protected ListenerClass listener;

  /**
   * Provides AdblockEngine and SharedPreferences to store settings
   * (activity holding BaseSettingsFragment fragment should implement this interface)
   */
  public interface Provider
  {
    AdblockEngine getAdblockEngine();
    AdblockSettingsStorage getAdblockSettingsStorage();
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

  protected <T> T castOrThrow(Activity activity, Class<T> clazz)
  {
    if (!(activity instanceof Provider))
    {
      String message = activity.getClass().getSimpleName()
        + " should implement "
        + clazz.getSimpleName()
        + " interface";

      Log.e(TAG, message);
      throw new RuntimeException(message);
    }

    return (T) activity;
  }

  public void loadSettings()
  {
    settings = provider.getAdblockSettingsStorage().load();
    if (settings == null)
    {
      Log.w(TAG, "No adblock settings, yet. Using defdault ones from adblock engine");

      // null because it was not saved yet
      settings = AdblockSettingsStorage.getDefaultSettings(provider.getAdblockEngine());
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    loadSettings();
  }

  @Override
  public void onAttach(Activity activity)
  {
    super.onAttach(activity);
    provider = castOrThrow(activity, Provider.class);
  }

  @Override
  public void onResume()
  {
    super.onResume();
    loadSettings();
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
