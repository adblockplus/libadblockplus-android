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

package org.adblockplus.libadblockplus.android;

import android.content.Context;
import android.net.ConnectivityManager;

import org.adblockplus.AdblockEngine;
import org.adblockplus.AppInfo;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;
import org.adblockplus.libadblockplus.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;

import timber.log.Timber;


public class AdblockEngineBuilder implements org.adblockplus.AdblockEngineBuilder
{
  // default base path to store subscription files in android app
  private static final String BASE_PATH_DIRECTORY = "adblock";

  private final Context context;
  private final AppInfo appInfo;
  private final String basePath;
  private boolean disabledByDefault;
  private boolean forceUpdatePreloadedSubscriptions;
  private Map<String, Integer> resourceMap;
  private HttpClient httpClientForTesting = null;

  public AdblockEngineBuilder(@NotNull final Context context, @NotNull final AppInfo appInfo,
                              @Nullable final String basePath)
  {
    this.context = context;
    this.appInfo = appInfo == null ? org.adblockplus.libadblockplus.android.AdblockEngine.generateAppInfo(context) :
      appInfo;
    this.basePath = basePath == null ? BASE_PATH_DIRECTORY : basePath;
    disabledByDefault = false;
    resourceMap = null;
  }

  @Override
  @NotNull
  public AdblockEngineBuilder disabledByDefault()
  {
    disabledByDefault = true;
    return this;
  }

  @Override
  @NotNull
  public AdblockEngineBuilder preloadSubscriptions(@NotNull final Map<String, Integer> resourceMap,
                                                   final boolean forceUpdate)
  {
    this.resourceMap = resourceMap;
    forceUpdatePreloadedSubscriptions = forceUpdate;
    return this;
  }

  @TestOnly
  @NotNull
  public AdblockEngineBuilder setHttpClientForTesting(@NotNull final HttpClient httpClient)
  {
    this.httpClientForTesting = httpClient;
    return this;
  }

  @Override
  @NotNull
  public AdblockEngine build()
  {
    final org.adblockplus.libadblockplus.android.AdblockEngine adblockEngine
      = new org.adblockplus.libadblockplus.android.AdblockEngine();
    adblockEngine.logSystem = new TimberLogSystem();
    adblockEngine.fileSystem = null; // using default
    HttpClient httpClient = this.httpClientForTesting == null ? new AndroidHttpClient(true)
      : this.httpClientForTesting;
    if (resourceMap != null)
    {
      httpClient = new AndroidHttpClientResourceWrapper(context, httpClient, resourceMap, null);
      if (forceUpdatePreloadedSubscriptions)
      {
        ((AndroidHttpClientResourceWrapper) httpClient).setListener(new AndroidHttpClientResourceWrapper.Listener()
        {
          @Override
          public void onIntercepted(final String url, final int resourceId)
          {
            Timber.d("Force subscription update for intercepted URL %s", url);
            if (adblockEngine.filterEngine != null)
            {
              adblockEngine.filterEngine.updateFiltersAsync(url);
            }
          }
        });
      }
    }
    adblockEngine.platform = new Platform(adblockEngine.logSystem, adblockEngine.fileSystem,
      httpClient, basePath);
    adblockEngine.platform.setUpJsEngine(appInfo);
    final ConnectivityManager connectivityManager =
      (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    final IsAllowedConnectionCallback isAllowedConnectionCallback =
      new IsAllowedConnectionCallbackImpl(connectivityManager);
    adblockEngine.platform.setUpFilterEngine(isAllowedConnectionCallback, !disabledByDefault);
    adblockEngine.enabled.set(!disabledByDefault);
    adblockEngine.filterEngine = adblockEngine.platform.getFilterEngine();
    return adblockEngine;
  }
}
