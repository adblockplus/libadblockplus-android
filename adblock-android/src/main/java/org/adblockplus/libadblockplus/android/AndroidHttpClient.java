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

package org.adblockplus.libadblockplus.android;

import android.util.Log;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.ServerResponse.NsStatus;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.adblockplus.libadblockplus.android.Utils.readFromInputStream;

public class AndroidHttpClient extends HttpClient
{
  public final static String TAG = Utils.getTag(HttpClient.class);

  protected static final String ENCODING_GZIP = "gzip";
  protected static final String ENCODING_IDENTITY = "identity";

  private final boolean compressedStream;
  private final String charsetName;

  /**
   * Ctor
   * @param compressedStream Request for gzip compressed stream from the server
   * @param charsetName Optional charset name for sending POST data
   */
  public AndroidHttpClient(final boolean compressedStream,
                           final String charsetName)
  {
    this.compressedStream = compressedStream;
    this.charsetName = charsetName;
  }

  public AndroidHttpClient()
  {
    this(true, "UTF-8");
  }

  @Override
  public void request(final HttpRequest request, final Callback callback)
  {
    if (!request.getMethod().equalsIgnoreCase(REQUEST_METHOD_GET))
    {
      throw new UnsupportedOperationException("Only GET method is supported");
    }

    final ServerResponse response = new ServerResponse();
    try
    {
      final URL url = new URL(request.getUrl());
      Log.d(TAG, "Downloading from: " + url);

      final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod(request.getMethod());

      if (request.getMethod().equalsIgnoreCase(REQUEST_METHOD_GET))
      {
        setGetRequestHeaders(request.getHeaders(), connection);
      }
      connection.setRequestProperty("Accept-Encoding",
        (compressedStream ? ENCODING_GZIP : ENCODING_IDENTITY));
      connection.setInstanceFollowRedirects(request.getFollowRedirect());
      connection.connect();

      if (connection.getHeaderFields().size() > 0)
      {
        List<HeaderEntry> responseHeaders = new LinkedList<>();
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
      InputStream inputStream = null;
      try
      {
        int responseStatus = connection.getResponseCode();
        response.setResponseStatus(responseStatus);
        response.setStatus(!isSuccessCode(responseStatus) ? NsStatus.ERROR_FAILURE : NsStatus.OK);

        inputStream = isSuccessCode(responseStatus) ?
          connection.getInputStream() : connection.getErrorStream();

        if (inputStream != null && compressedStream && ENCODING_GZIP.equals(connection.getContentEncoding()))
        {
          inputStream = new GZIPInputStream(inputStream);
        }

        if (inputStream != null)
        {
          response.setResponse(readFromInputStream(inputStream));
        }

        if (!url.equals(connection.getURL()))
        {
          Log.d(TAG, "Url was redirected, from: " + url + ", to: " + connection.getURL());
          response.setFinalUrl(connection.getURL().toString());
        }
      }
      finally
      {
        if (inputStream != null)
        {
          inputStream.close();
        }
        connection.disconnect();
      }
      Log.d(TAG, "Downloading finished");
      callback.onFinished(response);
    }
    catch (final MalformedURLException e)
    {
      // MalformedURLException can be caused by wrong user input so we should not (re)throw it
      Log.e(TAG, "WebRequest failed", e);
      response.setStatus(NsStatus.ERROR_MALFORMED_URI);
      callback.onFinished(response);
    }
    catch (final UnknownHostException e)
    {
      // UnknownHostException can be caused by wrong user input so we should not (re)throw it
      Log.e(TAG, "WebRequest failed", e);
      response.setStatus(NsStatus.ERROR_UNKNOWN_HOST);
      callback.onFinished(response);
    }
    catch (final Throwable t)
    {
      Log.e(TAG, "WebRequest failed", t);
      throw new AdblockPlusException("WebRequest failed", t);
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
