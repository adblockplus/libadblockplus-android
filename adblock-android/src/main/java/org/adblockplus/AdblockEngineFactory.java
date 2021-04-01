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
 * Allows to obtain a {@link AdblockEngineBuilder} instance.
 */
public class AdblockEngineFactory
{
  /**
   * Allows to obtain a {@link AdblockEngineBuilder} instance.
   *
   * @param context Android Context object.
   * @param appInfo {@link AppInfo) object which identifies the application when downloading {@link Subscription}s.
   *                In most cases it is recommended to pass a null value and proper {@link AppInfo) object will be
   *                created internally.
   * @param basePath Allows to overwrite default base path to store {@link Subscription} files.
   *
   */
  @NotNull
  public static AdblockEngineBuilder getAdblockEngineBuilder(@NotNull final Context context,
                                                             @Nullable final AppInfo appInfo,
                                                             @Nullable final String basePath)
  {
    return new org.adblockplus.libadblockplus.android.AdblockEngineBuilder(context, appInfo, basePath);
  }
}
