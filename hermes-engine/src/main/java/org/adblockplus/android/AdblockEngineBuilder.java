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

package org.adblockplus.android;

import android.content.Context;

import org.adblockplus.AdblockEngine;
import org.adblockplus.AppInfo;
import org.adblockplus.AsyncAdblockEngineBuilder;
import org.adblockplus.HttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import timber.log.Timber;

public class AdblockEngineBuilder implements org.adblockplus.AdblockEngineBuilder,
  AsyncAdblockEngineBuilder
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

  private final Set<StateListener> stateListenerSet = new HashSet<>();
  private AdblockEngine adblockEngine;
  private State state = State.INITIAL;

  public AdblockEngineBuilder(@NotNull final Context context, @Nullable final AppInfo appInfo,
                              @Nullable final String basePath)
  {
    this.context = context;
    this.appInfo = appInfo == null ? org.adblockplus.android.AdblockEngine.generateAppInfo(context) :
      appInfo;
    this.basePath = basePath == null ? BASE_PATH_DIRECTORY : basePath;
    disabledByDefault = false;
    resourceMap = null;
  }

  @TestOnly
  @NotNull
  AdblockEngineBuilder setHttpClientForTesting(@NotNull final HttpClient httpClient)
  {
    this.httpClientForTesting = httpClient;
    return this;
  }

  // Common Builder methods starts
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
  // Common Builder methods ends

  // AdblockEngineBuilder methods starts
  @Override
  @NotNull
  public synchronized AdblockEngine build()
  {
    if (adblockEngine != null)
    {
      return adblockEngine;
    }
    adblockEngine = buildInternal();
    setState(State.CREATED);
    return adblockEngine;
  }

  @Override
  public synchronized void dispose()
  {
    if (adblockEngine == null)
    {
      return;
    }
    ((org.adblockplus.android.AdblockEngine) adblockEngine).dispose();
    adblockEngine = null;
    setState(State.RELEASED);
  }
  // AdblockEngineBuilder methods ends

  // AsyncAdblockEngineBuilder methods starts
  @Override
  @NotNull
  public synchronized State getState()
  {
    return state;
  }

  @Override
  @NotNull
  public synchronized State build(@NotNull final ExecutorService executorService)
  {
    // Check if created or creating and if so just return the current state
    if (state == State.CREATING || state == State.CREATED)
    {
      return state;
    }
    // Start creation
    final State state = State.CREATING;
    setState(state);

    executorService.submit(new Runnable()
    {
      @Override
      public void run()
      {
        Timber.d("AdblockEngine build() runnable");
        org.adblockplus.android.AdblockEngine adblockEngineLocalRef;
        synchronized (AdblockEngineBuilder.this)
        {
          adblockEngineLocalRef =
            ((org.adblockplus.android.AdblockEngine) AdblockEngineBuilder.this.adblockEngine);
          if (adblockEngineLocalRef != null)
          {
            return;
          }
        }
        adblockEngineLocalRef = buildInternal();
        synchronized (AdblockEngineBuilder.this)
        {
          if (AdblockEngineBuilder.this.adblockEngine == null)
          {
            AdblockEngineBuilder.this.adblockEngine = adblockEngineLocalRef;
            setState(State.CREATED);
          }
          else
          {
            adblockEngineLocalRef.dispose();
          }
        }
      }
    });
    return state;
  }

  @Override
  @NotNull
  public synchronized State dispose(@NotNull final ExecutorService executorService)
  {
    if (state == State.INITIAL || state == State.RELEASING || state == State.RELEASED)
    {
      return state;
    }
    // Start disposing
    final State state = State.RELEASING;
    setState(state);
    executorService.submit(new Runnable()
    {
      @Override
      public void run()
      {
        Timber.d("AdblockEngine dispose() runnable");
        final org.adblockplus.android.AdblockEngine adblockEngineLocalRef;
        synchronized (AdblockEngineBuilder.this)
        {
          adblockEngineLocalRef =
            ((org.adblockplus.android.AdblockEngine) AdblockEngineBuilder.this.adblockEngine);
          if (adblockEngineLocalRef == null)
          {
            return;
          }
          AdblockEngineBuilder.this.adblockEngine = null;
          setState(State.RELEASED);
        }
        adblockEngineLocalRef.dispose();
      }
    });
    return state;
  }

  @NotNull
  public synchronized AsyncAdblockEngineBuilder subscribe(@NotNull final StateListener stateListener)
  {
    stateListenerSet.add(stateListener);
    return this;
  }

  @NotNull
  public synchronized AsyncAdblockEngineBuilder unsubscribe(@NotNull final StateListener stateListener)
  {
    stateListenerSet.remove(stateListener);
    return this;
  }

  @Override
  public synchronized AdblockEngine getAdblockEngine()
  {
    return adblockEngine;
  }
  // AsyncAdblockEngineBuilder methods ends

  private synchronized void setState(final State state)
  {
    final boolean stateChanged = this.state != state;
    if (stateChanged)
    {
      Timber.d("AdblockEngine setState() changes state to %s", state);
      this.state = state;
      notifyListeners();
    }
  }

  private void notifyListeners()
  {
    for (final StateListener stateListener : stateListenerSet)
    {
      stateListener.onState(state);
    }
  }

  private org.adblockplus.android.AdblockEngine buildInternal()
  {
    Timber.d("AdblockEngine buildInternal() started");
    final org.adblockplus.android.AdblockEngine adblockEngine
      = new org.adblockplus.android.AdblockEngine(context);
    adblockEngine.enabled.set(!disabledByDefault);
    Timber.d("AdblockEngine buildInternal() finished");
    return adblockEngine;
  }
}
