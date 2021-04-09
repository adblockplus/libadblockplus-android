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
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Asynchronous {@link AdblockEngine} builder.
 */
public interface AsyncAdblockEngineBuilder
{
  /**
   * List of build and dispose states of {@link AdblockEngine}.
   */
  enum State
  {
    /**
     * Not yet created
     */
    INITIAL,

    /**
     * Creating in progress
     */
    CREATING,

    /**
     * Created, ready for work
     */
    CREATED,

    /**
     * Releasing in progress
     */
    RELEASING,

    /**
     * Released
     */
    RELEASED;
  }

  /**
   * Interface to act upon build and dispose {@link State} changes.
   */
  interface StateListener
  {
    /**
     * Important notes:
     * - listener MUST be short-running (executed under the lock)
     * - during the listener the state is not changed
     * - listener can be called from a different thread than the one from which
     *   {@link AsyncAdblockEngineBuilder#subscribe} was called
     * @param state current {@link State}
     */
    void onState(@NotNull State state);
  }

  /**
   * Returns current build or dispose {@link State}
   *
   * @return {@link State}
   */
  @NotNull
  State getState();

  /**
   * Triggers asynchronous {@link AdblockEngine} building in provided ExecutorService object.
   *
   * @param executorService to run the build operation
   * @return current build {@link State}
   */
  @NotNull
  State build(@NotNull ExecutorService executorService);

  /**
   * Triggers asynchronous {@link AdblockEngine} disposing in provided ExecutorService object.
   * Should use the same ExecutorService object as {@link AsyncAdblockEngineBuilder#build} to guarantee proper order.
   *
   * @param executorService to run the dispose operation
   * @return current dispose {@link State}
   */
  @NotNull
  State dispose(@NotNull ExecutorService executorService);

  /**
   * Gets a handle of {@link AdblockEngine} instance.
   *
   * @return {@link AdblockEngine} instance or null if not yt created.
   */
  @Nullable
  AdblockEngine getAdblockEngine();

  /**
   * Call it to register {@link StateListener}.
   *
   * @param stateListener a {@link StateListener}
   * @return {@link AsyncAdblockEngineBuilder} to allow chaining
   */
  @NotNull
  AsyncAdblockEngineBuilder subscribe(@NotNull StateListener stateListener);

  /**
   * Call it to unregister {@link StateListener}.
   *
   * @param stateListener a {@link StateListener}
   * @return {@link AsyncAdblockEngineBuilder} to allow chaining
   */
  @NotNull
  AsyncAdblockEngineBuilder unsubscribe(@NotNull StateListener stateListener);

  /**
   * Call it to create {@link AdblockEngine} which is disabled by default. This means that {@link AdblockEngine} will
   * not automatically download any {@link Subscription}.
   *
   * @return {@link AsyncAdblockEngineBuilder} to allow chaining
   */
  @NotNull
  AsyncAdblockEngineBuilder disabledByDefault();

  /**
   * Call it to create {@link AdblockEngine} with preloaded {@link Subscription}s which will allow to provide ad
   * blocking functionality without the need of downloading any {@link Subscription} from the network.
   * This call is effective only when {@link AdblockEngine} is created for the very first time.
   *
   * @param resourceMap Map containing mapping of {@link Subscription#url} on Android resource Id.
   * @param forceUpdate When set to true then immediately fetches {@link Subscription}s updates from the network.
   * @return {@link AsyncAdblockEngineBuilder} to allow chaining
   */
  @NotNull
  AsyncAdblockEngineBuilder preloadSubscriptions(@NotNull Map<String, Integer> resourceMap, boolean forceUpdate);
}
