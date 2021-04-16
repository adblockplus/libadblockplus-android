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

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Synchronous {@link AdblockEngine} builder.
 */
public interface AdblockEngineBuilder
{
  /**
   * Builds {@link AdblockEngine} blocking current thread.
   *
   * @return {@link AdblockEngine} instance
   */
  @NotNull
  AdblockEngine build();

  /**
   * Disposes {@link AdblockEngine} blocking current thread.
   */
  void dispose();

  /**
   * Call it to create {@link AdblockEngine} which is disabled by default. This means that {@link AdblockEngine} will
   * not automatically download any {@link Subscription}.
   *
   * @return {@link AdblockEngineBuilder} to allow chaining
   */
  @NotNull
  AdblockEngineBuilder disabledByDefault();

  /**
   * Call it to create {@link AdblockEngine} with preloaded {@link Subscription}s which will allow to provide ad
   * blocking functionality without the need of downloading any {@link Subscription} from the network.
   * This call is effective only when {@link AdblockEngine} is created for the very first time.
   *
   * @param resourceMap Map containing mapping of {@link Subscription#url} on Android resource Id.
   * @param forceUpdate When set to true then immediately fetches {@link Subscription}s updates from the network.
   * @return {@link AdblockEngineBuilder} to allow chaining
   */
  @NotNull
  AdblockEngineBuilder preloadSubscriptions(@NotNull Map<String, Integer> resourceMap, boolean forceUpdate);
}
