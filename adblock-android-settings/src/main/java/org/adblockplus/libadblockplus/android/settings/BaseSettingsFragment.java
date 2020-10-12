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
import android.support.v7.preference.PreferenceFragmentCompat;
import timber.log.Timber;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class BaseSettingsFragment
  <ListenerClass extends BaseSettingsFragment.Listener>
  extends PreferenceFragmentCompat
{
  protected AdblockSettings settings;
  protected AdblockEngine engine;
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

    void onLoadStarted();
    void onLoadFinished();
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
          onAdblockEngineReadyInternal(engine);
        }
      };

  protected abstract void onAdblockEngineReady();

  private void onAdblockEngineReadyInternal(final AdblockEngine engine)
  {
    this.engine = engine;
    checkLoadingFinished();
    onAdblockEngineReady();
    initSettings();
  }

  private void initSettings()
  {
    if (settings == null)
    {
      settings = AdblockSettingsStorage.getDefaultSettings(engine);
      checkLoadingFinished();
      onSettingsReady();
    }
  }

  private void startLoadSettings()
  {
    provider.onLoadStarted();
    settings = provider.getAdblockSettingsStorage().load();
    if (settings == null)
    {
      // null because it was not saved yet
      Timber.w("No adblock settings, yet. Using default ones from adblock engine");
    }
    else
    {
      checkLoadingFinished();
      onSettingsReady();
    }

    if (engine == null)
    {
      startGetAdblockEngine();
    }
    else
    {
      initSettings();
    }
  }

  private void checkLoadingFinished()
  {
    if (settings != null && engine != null)
    {
      provider.onLoadFinished();
    }
  }

  private void startGetAdblockEngine()
  {
    final AdblockEngineProvider adblockEngineProvider = provider.getAdblockEngineProvider();
    final ReentrantReadWriteLock.ReadLock lock = adblockEngineProvider.getReadEngineLock();
    final boolean locked = lock.tryLock();

    try
    {
      final AdblockEngine adblockEngine = adblockEngineProvider.getEngine();
      if (adblockEngine != null)
      {
        this.onAdblockEngineReadyInternal(adblockEngine);
      }
      else
      {
        adblockEngineProvider.addEngineCreatedListener(engineCreatedListener);
      }
    }
    finally
    {
      if (locked)
      {
        lock.unlock();
      }
    }
  }

  private void stopLoadSettings()
  {
    provider.getAdblockEngineProvider().removeEngineCreatedListener(engineCreatedListener);
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
    startLoadSettings();
  }

  @Override
  public void onPause()
  {
    stopLoadSettings();
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
