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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.SingleInstanceEngineProvider;
import org.adblockplus.libadblockplus.android.Utils;

/**
 * AdblockHelper shared resources
 * (singleton)
 */
public class AdblockHelper
{
  private static final String TAG = Utils.getTag(AdblockHelper.class);

  /**
   * Suggested preference name to store settings
   */
  public static final String PREFERENCE_NAME = "ADBLOCK";

  /**
   * Suggested preference name to store intercepted subscription requests
   */
  public static final String PRELOAD_PREFERENCE_NAME = "ADBLOCK_PRELOAD";
  private static AdblockHelper _instance;

  private SingleInstanceEngineProvider provider;
  private AdblockSettingsStorage storage;

  private final Runnable engineCreatedCallback = new Runnable()
  {
    @Override
    public void run()
    {
      AdblockSettings settings = storage.load();
      if (settings != null)
      {
        Log.d(TAG, "Applying saved adblock settings to adblock engine");
        // apply last saved settings to adblock engine.
        // all the settings except `enabled` and whitelisted domains list
        // are saved by adblock engine itself
        provider.getEngine().setEnabled(settings.isAdblockEnabled());
        provider.getEngine().setWhitelistedDomains(settings.getWhitelistedDomains());

        // allowed connection type is saved by filter engine but we need to override it
        // as filter engine can be not created when changing
        String connectionType = (settings.getAllowedConnectionType() != null
          ? settings.getAllowedConnectionType().getValue()
          : null);
        provider.getEngine().getFilterEngine().setAllowedConnectionType(connectionType);
      }
      else
      {
        Log.w(TAG, "No saved adblock settings");
      }
    }
  };

  private final Runnable engineDisposedCallback = new Runnable()
  {
    @Override
    public void run()
    {
      Log.d(TAG, "Releasing adblock settings storage");
      storage = null;
    }
  };

  // singleton
  protected AdblockHelper()
  {
    // prevents instantiation
  }

  /**
   * Use to get AdblockHelper instance
   * @return adblock instance
   */
  public static synchronized AdblockHelper get()
  {
    if (_instance == null)
    {
      _instance = new AdblockHelper();
    }

    return _instance;
  }

  public AdblockEngineProvider getProvider()
  {
    if (provider == null)
    {
      throw new IllegalStateException("Usage exception: call init(...) first");
    }
    return provider;
  }

  public AdblockSettingsStorage getStorage()
  {
    if (storage == null)
    {
      throw new IllegalStateException("Usage exception: call init(...) first");
    }
    return storage;
  }

  /**
   * Init with context
   * @param context application context
   * @param basePath file system root to store files
   *
   *                 Adblock Plus library will download subscription files and store them on
   *                 the path passed. The path should exist and the directory content should not be
   *                 cleared out occasionally. Using `context.getCacheDir().getAbsolutePath()` is not
   *                 recommended because it can be cleared by the system.
   * @param developmentBuild debug or release?
   * @param preferenceName Shared Preferences name to store adblock settings
   */
  public SingleInstanceEngineProvider init(Context context, String basePath,
                                           boolean developmentBuild, String preferenceName)
  {
    initProvider(context, basePath, developmentBuild);
    initStorage(context, preferenceName);
    return provider;
  }

  private void initProvider(Context context, String basePath, boolean developmentBuild)
  {
    provider = new SingleInstanceEngineProvider(context, basePath, developmentBuild);
    provider.setEngineCreatedCallback(engineCreatedCallback);
    provider.setEngineDisposedCallback(engineDisposedCallback);
  }

  private void initStorage(Context context, String settingsPreferenceName)
  {
    // read and apply current settings
    SharedPreferences settingsPrefs = context.getSharedPreferences(
      settingsPreferenceName,
      Context.MODE_PRIVATE);

    storage = new SharedPrefsStorage(settingsPrefs);
  }

  /**
   * @deprecated The method is deprecated: use .getProvider().retain() instead
   */
  @Deprecated
  public boolean retain(boolean asynchronous)
  {
    return provider.retain(asynchronous);
  }

  /**
   * @deprecated The method is deprecated: use .getProvider().waitForReady() instead
   */
  @Deprecated
  public void waitForReady()
  {
    provider.waitForReady();
  }

  /**
   * @deprecated The method is deprecated: use .getProvider().getEngine() instead
   */
  @Deprecated
  public AdblockEngine getEngine()
  {
    return provider.getEngine();
  }

  /**
   * @deprecated The method is deprecated: use .getProvider().release() instead
   */
  @Deprecated
  public boolean release()
  {
    return provider.release();
  }

  /**
   * @deprecated The method is deprecated: use .getProvider().getCounter() instead
   */
  @Deprecated
  public int getCounter()
  {
    return provider.getCounter();
  }
}
