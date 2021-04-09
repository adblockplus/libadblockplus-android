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

package org.adblockplus;

import android.content.Context;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory to obtain {@link AdblockEngineFactory} or {@link AsyncAdblockEngineBuilder} instance.
 */
public class AdblockEngineFactory
{
  private static AdblockEngineFactory adblockEngineFactory;

  private org.adblockplus.libadblockplus.android.AdblockEngineBuilder adblockEngineBuilder;
  private Context context;
  private AppInfo appInfo;
  private String basePath;

  private AdblockEngineFactory(@NotNull final Context context,
                               @Nullable final AppInfo appInfo,
                               @Nullable final String basePath)
  {
    this.context = context;
    this.appInfo = appInfo;
    this.basePath = basePath;
  }

  private synchronized org.adblockplus.libadblockplus.android.AdblockEngineBuilder getBuilder()
  {
    if (adblockEngineFactory == null)
    {
      throw new IllegalStateException("Call AdblockEngineFactory.init(...) first!");
    }
    if (adblockEngineBuilder == null)
    {
      adblockEngineBuilder =
        new org.adblockplus.libadblockplus.android.AdblockEngineBuilder(context, appInfo, basePath);
    }
    return adblockEngineBuilder;
  }

  /**
   * Allows to init and obtain a {@link AdblockEngineFactory} instance.
   *
   * @param context Android Context object.
   * @param appInfo {@link AppInfo) object which identifies the application when downloading {@link Subscription}s.
   *                In most cases it is recommended to pass a null value and proper {@link AppInfo) object will be
   *                created internally.
   * @param basePath Allows to overwrite default base path to store {@link Subscription} files.
   * @return {@link AdblockEngineFactory}
   */
  @NotNull
  public static synchronized AdblockEngineFactory init(@NotNull final Context context,
                                                       @Nullable final AppInfo appInfo,
                                                       @Nullable final String basePath)
    throws IllegalStateException
  {
    if (adblockEngineFactory != null)
    {
      throw new IllegalStateException("Call AdblockEngineFactory.deinit() first!");
    }
    adblockEngineFactory = new AdblockEngineFactory(context, appInfo, basePath);
    return adblockEngineFactory;
  }

  /**
   * Deinitializes {@link AdblockEngineFactory}.
   */
  public static synchronized void deinit()
  {
    adblockEngineFactory = null;
  }

  /**
   * Allows to get a handle of {@link AdblockEngineFactory} instance.
   * One needs to call {@link AdblockEngineFactory#init} first.
   * @return {@link AdblockEngineFactory}
   */
  @NotNull
  public static synchronized AdblockEngineFactory getFactory() throws IllegalStateException
  {
    if (adblockEngineFactory == null)
    {
      throw new IllegalStateException("Call AdblockEngineFactory.init(...) first!");
    }
    return adblockEngineFactory;
  }

  /**
   * Returns synchronous {@link AdblockEngineBuilder} object
   * @return {@link AdblockEngineBuilder}
   */
  @NotNull
  public AdblockEngineBuilder getAdblockEngineBuilder() throws IllegalStateException
  {
    return getBuilder();
  }

  /**
   * Returns asynchronous {@link AsyncAdblockEngineBuilder} object
   * @return {@link AsyncAdblockEngineBuilder}
   */
  @NotNull
  public AsyncAdblockEngineBuilder getAsyncAdblockEngineBuilder() throws IllegalStateException
  {
    return getBuilder();
  }
}
