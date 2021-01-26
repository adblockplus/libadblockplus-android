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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import timber.log.Timber;

/**
 * InputStream wrapper that wraps `HttpURLConnection`s inputStream and closes wrapped connection
 * when it's input stream is closed.
 */
public class ConnectionInputStream extends InputStream
{
  private final HttpURLConnection httpURLConnection;
  private boolean closed = false;
  private static final int CACHE_SIZE = 4096;
  private final BufferedInputStream bufferedInputStream;

  public ConnectionInputStream(final InputStream inputStream, final HttpURLConnection httpURLConnection)
  {
    this.httpURLConnection = httpURLConnection;
    this.bufferedInputStream = new BufferedInputStream(inputStream, CACHE_SIZE);
    try
    {
      // tries to read CACHE_SIZE preemptively
      // it marks the current position, and keeps it valid for CACHE_SIZE + 1
      // reads and internally keeps it buffered
      // resets to the previously marked position
      this.bufferedInputStream.mark(CACHE_SIZE + 1);
      this.bufferedInputStream.read(new byte[CACHE_SIZE], 0, CACHE_SIZE);
      this.bufferedInputStream.reset();
    }
    catch (final IOException e)
    {
      // no need to throw it
      // it will throw again when webview tries to read
      Timber.d("Error while reading cached buffer for url %s and error %s",
              httpURLConnection.getURL(),
              e.getMessage());
    }
  }

  @Override
  public int read() throws IOException
  {
    return bufferedInputStream.read();
  }

  @Override
  public int read(byte b[]) throws IOException
  {
    return bufferedInputStream.read(b);
  }

  @Override
  public int read(byte b[], int off, int len) throws IOException
  {
    return bufferedInputStream.read(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException
  {
    return bufferedInputStream.skip(n);
  }

  @Override
  public int available() throws IOException
  {
    return bufferedInputStream.available();
  }

  @Override
  public void close() throws IOException
  {
    try
    {
      Timber.d("close()");
      // will close inner stream
      bufferedInputStream.close();
      closed = true;
    }
    finally
    {
      httpURLConnection.disconnect();
    }
  }

  @Override
  public synchronized void mark(int readlimit)
  {
    bufferedInputStream.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException
  {
    bufferedInputStream.reset();
  }

  @Override
  public boolean markSupported()
  {
    return bufferedInputStream.markSupported();
  }

  @Override
  protected void finalize() throws Throwable
  {
    try
    {
      if (!closed)
      {
        Timber.d("close() from finalize");
        httpURLConnection.disconnect();
      }
    }
    catch(final Throwable t)
    {
      throw t;
    }
    finally
    {
      super.finalize();
    }
  }
}
