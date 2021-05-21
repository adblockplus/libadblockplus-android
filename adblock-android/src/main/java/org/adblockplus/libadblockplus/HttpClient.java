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
  public static final String HEADER_REFERRER = "Referer";
  public static final String HEADER_REQUESTED_WITH = "X-Requested-With";
  public static final String HEADER_REQUESTED_WITH_XMLHTTPREQUEST = "XMLHttpRequest";
  public static final String HEADER_REQUESTED_RANGE = "Range";
  public static final String HEADER_LOCATION = "Location";
  public static final String HEADER_COOKIE = "Cookie";
  public static final String HEADER_USER_AGENT = "User-Agent";
  public static final String HEADER_ACCEPT = "Accept";
  public static final String HEADER_REFRESH = "Refresh";
  // use low-case strings as in WebResponse all header keys are lowered-case
  public static final String HEADER_SET_COOKIE = "set-cookie";
  public static final String HEADER_WWW_AUTHENTICATE = "www-authenticate";
  public static final String HEADER_PROXY_AUTHENTICATE = "proxy-authenticate";
  public static final String HEADER_EXPIRES = "expires";
  public static final String HEADER_DATE = "date";
  public static final String HEADER_RETRY_AFTER = "retry-after";
  public static final String HEADER_LAST_MODIFIED = "last-modified";
  public static final String HEADER_VIA = "via";
  public static final String HEADER_SITEKEY = "x-adblock-key";
  public static final String HEADER_CONTENT_TYPE = "content-type";
  public static final String HEADER_CONTENT_LENGTH = "content-length";
  public static final String HEADER_CSP = "content-security-policy";

  public static final int STATUS_CODE_OK = 200;

  /**
   * Possible values for request method argument (see `request(..)` method)
   */
  public static final String REQUEST_METHOD_GET = "GET";
  public static final String REQUEST_METHOD_POST = "POST";
  public static final String REQUEST_METHOD_HEAD = "HEAD";
  public static final String REQUEST_METHOD_OPTIONS = "OPTIONS";
  public static final String REQUEST_METHOD_PUT = "PUT";
  public static final String REQUEST_METHOD_DELETE = "DELETE";
  public static final String REQUEST_METHOD_TRACE = "TRACE";

  /**
   * Some MIME types
   */
  public static final String MIME_TYPE_TEXT_HTML = "text/html";

  /**
   * Checks if HTTP status code is a redirection.
   * @param httpStatusCode HTTP status code to check.
   * @return True for redirect status code.
   */
  public static boolean isRedirectCode(final int httpStatusCode)
  {
    return httpStatusCode >= 300 && httpStatusCode <= 399;
  }

  /**
   * HTTP status cannot be greater that 599 and less than 100
   *
   * @param httpStatusCode HTTP status code to check.
   * @return True when status is valid
   */
  public static boolean isValidCode(final int httpStatusCode)
  {
    return httpStatusCode >= 100 && httpStatusCode <= 599;
  }

  /**
   * Checks if HTTP status code is a success code.
   * @param httpStatusCode HTTP status code to check.
   * @return True for success status code.
   */
  public static boolean isSuccessCode(final int httpStatusCode)
  {
    return httpStatusCode >= STATUS_CODE_OK && httpStatusCode <= 299;
  }

  /**
   * Checks if HTTP status code means no content.
   * See: https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html
   * @param httpStatusCode HTTP status code to check.
   * @return True for no content code.
   */
  public static boolean isNoContentCode(final int httpStatusCode)
  {
    return httpStatusCode == 204 || httpStatusCode == 304 ||
        (httpStatusCode >= 100 && httpStatusCode <= 199);
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

    private static final class DisposeWrapper implements Disposable
    {
      private final long ptr;

      public DisposeWrapper(final long ptr)
      {
        this.ptr = ptr;
      }

      @Override
      public void dispose()
      {
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
    }
  }

  /**
   * Performs a HTTP request.
   * @param request HttpRequest
   * @param callback to invoke when the server response is ready.
   */
  public abstract void request(final HttpRequest request, final Callback callback);
}
