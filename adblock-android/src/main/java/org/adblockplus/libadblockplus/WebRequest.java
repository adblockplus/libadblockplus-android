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

package org.adblockplus.libadblockplus;

import java.util.List;

public abstract class WebRequest
{
  static
  {
    System.loadLibrary("adblockplus-jni");
    registerNatives();
  }

  /**
   * Callback type invoked when the server response is ready.
   */
  public static class Callback implements Disposable
  {
    protected final long ptr;
    private final Disposer disposer;

    Callback(final long ptr)
    {
      this.ptr = ptr;
      this.disposer = new Disposer(this, new DisposeWrapper(this.ptr));
    }

    private final static class DisposeWrapper implements Disposable
    {
      private final long ptr;

      public DisposeWrapper(final long ptr)
      {
        this.ptr = ptr;
      }

      @Override
      public void dispose()
      {
        callbackDtor(this.ptr);
      }
    }

    @Override
    public void dispose()
    {
      this.disposer.dispose();
    }

    /**
     * @param response server response.
     */
    public void onFinished(final ServerResponse response)
    {
      callbackOnFinished(this.ptr, response);
    }
  }

  /**
   * Performs a GET request.
   * @param url Request URL.
   * @param headers Request headers.
   * @param callback to invoke when the server response is ready.
   */
  public abstract void GET(final String url, final List<HeaderEntry> headers, final Callback callback);

  private final static native void callbackOnFinished(long ptr, ServerResponse response);
  private final static native void callbackDtor(long ptr);
  private final static native void registerNatives();
}
