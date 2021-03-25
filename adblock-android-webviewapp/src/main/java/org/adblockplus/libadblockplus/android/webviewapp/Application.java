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

import org.adblockplus.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;

import timber.log.Timber;

public class Application extends android.app.Application
{
  private final AdblockEngineProvider.EngineCreatedListener engineCreatedListener =
    new AdblockEngineProvider.EngineCreatedListener()
  {
    @Override
    public void onAdblockEngineCreated(final AdblockEngine adblockEngine)
    {
      // put your AdblockEngine initialization here
    }
  };

  private final AdblockEngineProvider.EngineDisposedListener engineDisposedListener =
    new AdblockEngineProvider.EngineDisposedListener()
  {
    @Override
    public void onAdblockEngineDisposed()
    {
      // put your AdblockEngine de-initialization here
    }
  };

  @Override
  public void onCreate()
  {
    super.onCreate();

    if (BuildConfig.DEBUG)
    {
      Timber.plant(new Timber.DebugTree());
    }

    // it's not initialized here but we check it just to show API usage
    if (!AdblockHelper.get().isInit())
    {

      final AdblockHelper helper = AdblockHelper.get();
      helper
        .init(this, null /*use default value*/, AdblockHelper.PREFERENCE_NAME)
        .preloadSubscriptions(
                R.raw.easylist_minified,
                R.raw.exceptionrules_minimal)
        .addEngineCreatedListener(engineCreatedListener)
        .addEngineDisposedListener(engineDisposedListener);

      if (!BuildConfig.ADBLOCK_ENABLED)
      {
        Timber.d("onCreate() with DisabledByDefault");
        helper.setDisabledByDefault();
      }

      helper.getSiteKeysConfiguration().setForceChecks(true);
    }
  }
}
