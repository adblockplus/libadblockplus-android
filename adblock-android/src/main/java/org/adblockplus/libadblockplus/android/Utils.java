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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

import org.json.JSONArray;

import android.content.Context;
import android.util.Log;

import static org.adblockplus.libadblockplus.android.AndroidWebRequest.TAG;

public final class Utils
{
  private Utils()
  {
    //
  }

  public static String getTag(final Class<?> clazz)
  {
    return clazz.getSimpleName();
  }

  public static String stringListToJsonArray(final List<String> list)
  {
    JSONArray array = new JSONArray();

    if (list != null)
    {
      for (String eachString : list)
      {
        if (eachString != null)
        {
          array.put(eachString);
        }
      }
    }

    return array.toString();
  }

  public static String readAssetAsString(final Context context,
                                         final String filename)
      throws IOException
  {
    BufferedReader in = null;
    try
    {
      StringBuilder buf = new StringBuilder();
      InputStream is = context.getAssets().open(filename);
      in = new BufferedReader(new InputStreamReader(is));

      String str;
      boolean isFirst = true;
      while ((str = in.readLine()) != null)
      {
        if (isFirst)
        {
          isFirst = false;
        }
        else
        {
          buf.append('\n');
        }
        buf.append(str);
      }
      return buf.toString();
    }
    finally
    {
      if (in != null)
      {
        try
        {
          in.close();
        }
        catch (IOException e)
        {
          // ignored
        }
      }
    }
  }

  public static String getUrlWithoutParams(final String urlWithParams)
  {
    if (urlWithParams == null)
    {
      throw new IllegalArgumentException("URL can't be null");
    }

    int pos = urlWithParams.indexOf("?");
    return (pos >= 0 ? urlWithParams.substring(0, pos) : urlWithParams);
  }

  public static String readInputStreamAsString(final InputStream is) throws IOException
  {
    BufferedReader br = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();
    String line;
    boolean firstLine = true;
    while ((line = br.readLine()) != null)
    {
      if (firstLine)
      {
        firstLine = false;
      }
      else
      {
        sb.append("\r\n");
      }
      sb.append(line);
    }

    Log.d(TAG, "Resource read (" + sb.length() + " bytes)");
    return sb.toString();
  }

  /**
   * Convert String to ByteBuffer
   * @param string string
   * @param charset charset
   * @return byte buffer (*direct* buffer, allocated with `ByteBuffer.allocateDirect`)
   */
  public static ByteBuffer stringToByteBuffer(final String string, final Charset charset)
  {
    byte[] bytes = string.getBytes(charset);
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bytes.length);
    byteBuffer.put(bytes);
    return byteBuffer;
  }

  /**
   * Convert ByteBuffer to String
   * @param buffer buffer
   * @param charset charset
   * @return string
   */
  public static String byteBufferToString(final ByteBuffer buffer, final Charset charset)
  {
    byte[] bytes;
    if (buffer.hasArray())
    {
      bytes = buffer.array();
    }
    else
    {
      bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
    }
    return new String(bytes, charset);
  }
}
