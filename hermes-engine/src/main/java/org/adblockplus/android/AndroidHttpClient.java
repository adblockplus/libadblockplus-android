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

import android.net.TrafficStats;

import org.adblockplus.AdblockPlusException;
import org.adblockplus.HeaderEntry;
import org.adblockplus.HttpClient;
import org.adblockplus.HttpRequest;
import org.adblockplus.ServerResponse;
import org.adblockplus.ServerResponse.NsStatus;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import timber.log.Timber;

public class AndroidHttpClient extends HttpClient
{
  protected static final String ENCODING_GZIP = "gzip";
  protected static final String ENCODING_IDENTITY = "identity";

  protected static final int SOCKET_TAG = 1;

  private final boolean compressedStream;

  /**
   * Ctor
   * @param compressedStream Request for gzip compressed stream from the server
   */
  public AndroidHttpClient(final boolean compressedStream)
  {
    this.compressedStream = compressedStream;
  }

  public AndroidHttpClient()
  {
    this(true);
  }

  @Override
  public void request(final HttpRequest request, final HttpClient.Callback callback)
  {
    if (!(request.getMethod().equalsIgnoreCase(HttpClient.REQUEST_METHOD_GET) ||
        request.getMethod().equalsIgnoreCase(HttpClient.REQUEST_METHOD_HEAD)))
    {
      throw new UnsupportedOperationException("Only GET and HEAD methods are supported");
    }

    final ServerResponse response = new ServerResponse();

    final int oldTag = TrafficStats.getThreadStatsTag();
    TrafficStats.setThreadStatsTag(SOCKET_TAG);
    Timber.d("Socket TAG set to: %s", SOCKET_TAG);

    HttpURLConnection connection = null;
    InputStream inputStream = null;
    try
    {
      final URL url = new URL(request.getUrl());
      Timber.d("Downloading from: %s, request.getFollowRedirect() = %b", url, request.getFollowRedirect());

      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod(request.getMethod());

      if (request.getMethod().equalsIgnoreCase(HttpClient.REQUEST_METHOD_GET))
      {
        setGetRequestHeaders(request.getHeaders(), connection);
      }
      connection.setRequestProperty("Accept-Encoding",
        (compressedStream ? ENCODING_GZIP : ENCODING_IDENTITY));
      connection.setInstanceFollowRedirects(request.getFollowRedirect());

      Timber.d("Connecting...");
      connection.connect();
      Timber.d("Connected");

      if (connection.getHeaderFields().size() > 0)
      {
        Timber.d("Received header fields");

        final List<HeaderEntry> responseHeaders = new LinkedList<>();
        for (Map.Entry<String, List<String>> eachEntry : connection.getHeaderFields().entrySet())
        {
          for (String eachValue : eachEntry.getValue())
          {
            if (eachEntry.getKey() != null && eachValue != null)
            {
              responseHeaders.add(new HeaderEntry(eachEntry.getKey().toLowerCase(), eachValue));
            }
          }
        }
        response.setResponseHeaders(responseHeaders);
      }
      try
      {
        final int responseStatus = connection.getResponseCode();
        response.setResponseStatus(responseStatus);
        response.setStatus(HttpClient.isSuccessCode(responseStatus) || HttpClient.isRedirectCode(responseStatus) ?
            NsStatus.OK : NsStatus.ERROR_FAILURE);

        Timber.d("responseStatus: %d for url %s", responseStatus, url);

        if (HttpClient.isSuccessCode(responseStatus) || HttpClient.isRedirectCode(responseStatus))
        {
          Timber.d("Success responseStatus");
          inputStream = connection.getInputStream();
        }
        else
        {
          Timber.d("inputStream is set to Error stream");
          inputStream = connection.getErrorStream();
        }

        if (inputStream != null)
        {
          // DP-579: We need to also check if stream is not empty before creating GZIP, for example
          // this code throws: `new GZIPInputStream(new ByteArrayInputStream(new byte[0]))`
          if (compressedStream && ENCODING_GZIP.equals(connection.getContentEncoding()) &&
              !HttpClient.isNoContentCode(responseStatus))
          {
            if (request.getMethod().equalsIgnoreCase(HttpClient.REQUEST_METHOD_GET))
            {
              inputStream = new GZIPInputStream(inputStream);
            }
            else if (request.getMethod().equalsIgnoreCase(HttpClient.REQUEST_METHOD_HEAD))
            {
              Timber.i("A payload body within a HEAD method must be empty.  URL %s", url);
            }
          }

          /**
           * AndroidHttpClient is used by:
           * 1) Lower layer (JS core->C++->JNI->Java) and lower layer code expects that complete
           * response data is returned.
           * 2) Upper layer from WebViewClient.shouldInterceptRequest() (Java->Java) when we can
           * return just an InputStream allowing WebView to handle it (buffer or not).
           * To distinguish those two cases we are using now the new boolean argument in HttpRequest
           * constructor - `skipInputStreamReading`.
           * Later on we could switch just to returning InputStream for both cases but that would
           * require adaptations on lower layers (JNI/C++).
           */
          if (request.skipInputStreamReading())
          {
            Timber.d("response.setInputStream(inputStream)");
            // We need to do such a wrapping to let AdblockInputStream to call disconnect() on
            // connection when closing InputStream object. InputStream will be owned by WebView.
            inputStream = new ConnectionInputStream(inputStream, connection);
            response.setInputStream(inputStream);
          }
          else
          {
            Timber.d("readFromInputStream(inputStream)");
            response.setResponse(Utils.readFromInputStream(inputStream));
          }
        }
        else
        {
          Timber.w("inputStream is null");
        }

        if (!url.equals(connection.getURL()))
        {
          Timber.d("Url was redirected, from: %s, to: %s", url, connection.getURL());
          response.setFinalUrl(connection.getURL().toString());
        }
      }
      finally
      {
        if (!request.skipInputStreamReading() && (inputStream != null))
        {
          Timber.d("Closing connection input stream");
          inputStream.close();
        }
      }
      Timber.d("Downloading finished");
      callback.onFinished(response);
    }
    catch (final MalformedURLException e)
    {
      // MalformedURLException can be caused by wrong user input so we should not (re)throw it
      Timber.e(e, "WebRequest failed");
      response.setStatus(NsStatus.ERROR_MALFORMED_URI);
      callback.onFinished(response);
    }
    catch (final UnknownHostException e)
    {
      // UnknownHostException can be caused by wrong user input so we should not (re)throw it
      Timber.e(e, "WebRequest failed");
      response.setStatus(NsStatus.ERROR_UNKNOWN_HOST);
      callback.onFinished(response);
    }
    catch (final Throwable t)
    {
      Timber.e(t, "WebRequest failed");
      throw new AdblockPlusException("WebRequest failed", t);
    }
    finally
    {
      // when inputStream == null then connection won't be used anyway
      if (!request.skipInputStreamReading() || (inputStream == null))
      {
        if (connection != null)
        {
          connection.disconnect();
          Timber.d("Disconnected");
        }
      }
      TrafficStats.setThreadStatsTag(oldTag);
      Timber.d("Socket TAG reverted to: %d", oldTag);
    }
  }

  private void setGetRequestHeaders(final List<HeaderEntry> headers,
                                    final HttpURLConnection connection)
  {
    for (final HeaderEntry header : headers)
    {
      connection.setRequestProperty(header.getKey(), header.getValue());
    }
  }
}
