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

package org.adblockplus.hermes;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

/**
 * A simple class to add an event loop to the engine (setTimeout support)
 */
class EngineScheduler extends Thread
{
  private Handler handler;

  EngineScheduler()
  {
    setName("JSRuntime Scheduler");
    start();
  }

  @Override
  public void run()
  {
    Looper.prepare();
    handler = new Handler(Looper.myLooper());
    Looper.loop();
  }

  /**
   * The main method to schedule javascript function calls with or without a delay.
   *
   * @param engine the {@link Engine} that will execute the method
   * @param jsFunction the {@link JSFunctionWrapper} instance to be passed back to the engine to be executed
   * @param millis the delay, can be any number. If less than 0, the used delay will be 0.
   */
  void post(@NonNull final Engine engine,
            @NonNull final JSFunctionWrapper jsFunction,
            final long millis)
  {
    handler.postDelayed((Runnable) () ->
    {
      synchronized (engine)
      {
        engine._executeJSFunction(jsFunction);
      }
    }, Math.max(millis, 0L));
  }
}
