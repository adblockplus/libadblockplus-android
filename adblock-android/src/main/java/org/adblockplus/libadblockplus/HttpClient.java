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

public abstract class HttpClient
{
  static
  {
    System.loadLibrary(BuildConfig.nativeLibraryName);
    registerNatives();
  }

  /**
   * Possible values for request method argument (see `request(..)` method)
   */
  public static String REQUEST_METHOD_GET = "GET";
  public static String REQUEST_METHOD_POST = "POST";
  public static String REQUEST_METHOD_HEAD = "HEAD";
  public static String REQUEST_METHOD_OPTIONS = "OPTIONS";
  public static String REQUEST_METHOD_PUT = "PUT";
  public static String REQUEST_METHOD_DELETE = "DELETE";
  public static String REQUEST_METHOD_TRACE = "TRACE";

  /**
   * Checks if HTTP status code is a redirection.
   * @param httpStatusCode HTTP status code to check.
   * @return True for redirect status code.
   */
  public static boolean isRedirectCode(int httpStatusCode)
  {
    return httpStatusCode >= 300 && httpStatusCode <= 399;
  }

  /**
   * Checks if HTTP status code is a success code.
   * @param httpStatusCode HTTP status code to check.
   * @return True for success status code.
   */
  public static boolean isSuccessCode(int httpStatusCode)
  {
    return httpStatusCode >= 200 && httpStatusCode <= 299;
  }

  /**
   * Generic callback
   */
  public interface Callback
  {
    /**
     * @param response server response.
     */
    void onFinished(final ServerResponse response);
  }

  /**
   * Callback type invoked when the server response is ready (used from JNI code).
   */
  public static class JniCallback implements Callback, Disposable
  {
    protected final long ptr;
    private final Disposer disposer;

    public JniCallback(final long ptr)
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
    @Override
    public void onFinished(final ServerResponse response)
    {
      callbackOnFinished(this.ptr, response);
    }
  }

  /**
   * Performs a HTTP request.
   * @param request HttpRequest
   * @param callback to invoke when the server response is ready.
   */
  public abstract void request(final HttpRequest request, final Callback callback);

  private final static native void callbackOnFinished(long ptr, ServerResponse response);
  private final static native void callbackDtor(long ptr);
  private final static native void registerNatives();
}
