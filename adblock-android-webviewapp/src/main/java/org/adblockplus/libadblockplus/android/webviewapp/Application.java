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

import android.content.Context;

import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.SingleInstanceEngineProvider;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;

import timber.log.Timber;

public class Application extends android.app.Application
{
  private final SingleInstanceEngineProvider.EngineCreatedListener engineCreatedListener =
    new SingleInstanceEngineProvider.EngineCreatedListener()
  {
    @Override
    public void onAdblockEngineCreated(final AdblockEngine engine)
    {
      // put your Adblock FilterEngine init here
    }
  };

  private final SingleInstanceEngineProvider.EngineDisposedListener engineDisposedListener =
    new SingleInstanceEngineProvider.EngineDisposedListener()
  {
    @Override
    public void onAdblockEngineDisposed()
    {
      // put your Adblock FilterEngine de-initialization here
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
      // init Adblock
      final String basePath = getDir(AdblockEngine.BASE_PATH_DIRECTORY, Context.MODE_PRIVATE).getAbsolutePath();

      final AdblockHelper helper = AdblockHelper.get();
      helper
        .init(this, basePath, AdblockHelper.PREFERENCE_NAME)
        .preloadSubscriptions(
                R.raw.easylist_minified,
                R.raw.exceptionrules_minimal)
        .addEngineCreatedListener(engineCreatedListener)
        .addEngineDisposedListener(engineDisposedListener);

      if (!BuildConfig.ADBLOCK_ENABLED)
      {
        helper.setDisabledByDefault();
      }

      helper.getSiteKeysConfiguration().setForceChecks(true);
    }
  }
}
