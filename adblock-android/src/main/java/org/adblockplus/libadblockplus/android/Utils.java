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

import android.content.Context;
import android.text.TextUtils;

import org.adblockplus.libadblockplus.Filter;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

public final class Utils
{
  private Utils()
  {
    //
  }

  public static boolean isNullOrEmpty(final Collection< ? > c)
  {
    return c == null || c.isEmpty();
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

  public static String emulationSelectorListToJsonArray(final List<FilterEngine.EmulationSelector> list)
  {
    JSONArray array = new JSONArray();

    if (list != null)
    {
      for (FilterEngine.EmulationSelector selector : list)
      {
        if (selector != null)
        {
          try
          {
            JSONObject selectorObj = new JSONObject();
            selectorObj.put("selector", selector.selector);
            selectorObj.put("text", selector.text);
            array.put(selectorObj);
          }
          catch (JSONException e)
          {
            Timber.e(e, "Failed to create JSON object");
          }
        }
      }
    }

    return array.toString();
  }

  public static String readAssetAsString(final Context context,
                                         final String filename,
                                         final String charsetName)
      throws IOException
  {
    InputStream is = null;
    try
    {
      is = new BufferedInputStream(context.getAssets().open(filename));
      return new String(toByteArray(is), charsetName);
    }
    finally
    {
      if (is != null)
      {
        try
        {
          is.close();
        }
        catch (IOException e)
        {
          // ignored
        }
      }
    }
  }

  private static String getStringBeforeChar(final String str, final char c)
  {
    int pos = str.indexOf(c);
    return (pos >= 0 ? str.substring(0, pos) : str);
  }

  public static String getUrlWithoutParams(final String urlWithParams)
  {
    if (urlWithParams == null)
    {
      throw new IllegalArgumentException("URL can't be null");
    }

    return getStringBeforeChar(urlWithParams, '?');
  }

  public static String getUrlWithoutFragment(final String url)
  {
    return getStringBeforeChar(url, '#');
  }

  public static String getOrigin(final String url) throws MalformedURLException
  {
    if (url == null)
    {
      throw new IllegalArgumentException("URL can't be null");
    }

    final URL uri = new URL(url);
    final StringBuilder sb = new StringBuilder();
    sb.append(uri.getProtocol());
    sb.append("://");
    sb.append(uri.getHost());
    if (uri.getPort() != -1)
    {
      sb.append(":");
      sb.append(uri.getPort());
    }
    if (!TextUtils.isEmpty(uri.getPath()) || !TextUtils.isEmpty(uri.getQuery()))
    {
      sb.append("/");
    }
    return sb.toString();
  }

  public static String getDomain(final String url)
  {
    if (url == null)
    {
      throw new IllegalArgumentException("Url can't be null");
    }
    try
    {
      return new URI(getStringBeforeChar(url, '?')).getHost();
    }
    catch (final URISyntaxException e)
    {
      return null;
    }
  }

  public static boolean isFirstPartyCookie(final String documentUrl, final String requestUrl,
                                           final String cookieString)
  {
    if (documentUrl == null || requestUrl == null || cookieString == null)
    {
      throw new IllegalArgumentException("Arguments can't be null");
    }
    final String documentDomain = getDomain(documentUrl);
    if (documentDomain == null)
    {
      Timber.e("isFirstPartyCookie: Failed to getDomain(%s)", documentUrl);
      return false;
    }

    String cookieDomain = null;
    // Try to find "Domain" param value inside the cookie
    try
    {
      final List<HttpCookie> cookies = HttpCookie.parse(cookieString);
      /**
       * RFC 6265 says:
       * "Origin servers SHOULD NOT fold multiple Set-Cookie header fields into
       * a single header field".
       * Here we have a list as the API supports an old Set-Cookie2 header (RFC 2965).
       * But Set-Cookie2 is lo longer supported by browsers
       * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie2
       * Hence below we just check 1st entry (if present).
       */
      if (cookies.size() > 0)
      {
        cookieDomain = cookies.get(0).getDomain();
      }
    }
    catch (final IllegalArgumentException e)
    {
      Timber.e(e, "isFirstPartyCookie: Failed call to HttpCookie.parse()");
      return false;
    }

    // Cookie does not specify "Domain" param, obtain the domain from url which sets the cookie
    if (cookieDomain == null || cookieDomain.isEmpty())
    {
      cookieDomain = getDomain(requestUrl);
      if (cookieDomain == null || cookieDomain.isEmpty())
      {
        Timber.e("isFirstPartyCookie: Failed to getDomain(%s)", requestUrl);
        return false;
      }
    }

    if (cookieDomain == null || cookieDomain.isEmpty())
    {
      return false;
    }
    // Check if document domain includes cookie effective domain
    return HttpCookie.domainMatches(cookieDomain.toLowerCase(), documentDomain.toLowerCase());
  }


  private static final int BUFFER_SIZE = 8192;

  /** Max array length on JVM. */
  private static final int MAX_ARRAY_LEN = Integer.MAX_VALUE - 8;

  /** Large enough to never need to expand, given the geometric progression of buffer sizes. */
  private static final int TO_BYTE_ARRAY_DEQUE_SIZE = 20;

  /**
   * Returns a byte array containing the bytes from the buffers already in {@code bufs} (which have
   * a total combined length of {@code totalLen} bytes) followed by all bytes remaining in the given
   * input stream.
   *
   * Method is taken from
   * https://github.com/google/guava/blob/master/guava/src/com/google/common/io/ByteStreams.java.
   */
  private static byte[] toByteArrayInternal(final InputStream in,
                                            final Deque<byte[]> bufs,
                                            int totalLen)
      throws IOException
  {
    // Starting with an 8k buffer, double the size of each successive buffer. Buffers are retained
    // in a deque so that there's no copying between buffers while reading and so all of the bytes
    // in each new allocated buffer are available for reading from the stream.
    for (int bufSize = BUFFER_SIZE; totalLen < MAX_ARRAY_LEN; bufSize *= 2)
    {
      byte[] buf = new byte[Math.min(bufSize, MAX_ARRAY_LEN - totalLen)];
      bufs.add(buf);
      int off = 0;
      while (off < buf.length)
      {
        // always OK to fill buf; its size plus the rest of bufs is never more than MAX_ARRAY_LEN
        int r = in.read(buf, off, buf.length - off);
        if (r == -1)
        {
          return combineBuffers(bufs, totalLen);
        }
        off += r;
        totalLen += r;
      }
    }

    // read MAX_ARRAY_LEN bytes without seeing end of stream
    if (in.read() == -1)
    {
      // oh, there's the end of the stream
      return combineBuffers(bufs, MAX_ARRAY_LEN);
    }
    else
    {
      throw new OutOfMemoryError("Input is too large to fit in a byte array");
    }
  }

  /**
   * Reads all bytes from an input stream into a byte array. Does not close the stream.
   *
   * @param in the input stream to read from
   * @return a byte array containing all the bytes from the stream
   * @throws IOException if an I/O error occurs
   */
  public static byte[] toByteArray(final InputStream in) throws IOException
  {
    return toByteArrayInternal(in, new ArrayDeque<byte[]>(TO_BYTE_ARRAY_DEQUE_SIZE), 0);
  }

  /**  This is an auxiliary method for toByteArrayInternal and one should not make it public. */
  private static byte[] combineBuffers(final Deque<byte[]> bufs, final int totalLen)
  {
    byte[] result = new byte[totalLen];
    int remaining = totalLen;
    while (remaining > 0)
    {
      byte[] buf = bufs.removeFirst();
      int bytesToCopy = Math.min(remaining, buf.length);
      int resultOffset = totalLen - remaining;
      System.arraycopy(buf, 0, result, resultOffset, bytesToCopy);
      remaining -= bytesToCopy;
    }
    return result;
  }

  /**
   * Read all input stream into ByteBuffer that can be passed over JNI
   * @param inputStream input stream
   * @return byte buffer
   * @throws IOException
   */
  public static ByteBuffer readFromInputStream(final InputStream inputStream) throws IOException
  {
    final byte[] bufferBytes = Utils.toByteArray(inputStream);

    // WARNING: in order to be passed back to JNI one have to use `allocateDirect`
    final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferBytes.length);
    buffer.order(ByteOrder.nativeOrder());
    buffer.put(bufferBytes);
    buffer.rewind();

    return buffer;
  }

  public static final Set<String> commaNotMergableHeaders = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList(
          HttpClient.HEADER_SET_COOKIE, HttpClient.HEADER_WWW_AUTHENTICATE,
          HttpClient.HEADER_PROXY_AUTHENTICATE, HttpClient.HEADER_EXPIRES, HttpClient.HEADER_DATE,
          HttpClient.HEADER_RETRY_AFTER, HttpClient.HEADER_LAST_MODIFIED, HttpClient.HEADER_VIA
      ))
  );

  /**
   * Convert map to list of headers
   * @param headersListLowerCase list of headers
   * @return map of headers
   */
  public static Map<String, String> convertHeaderEntriesToMap(final List<HeaderEntry> headersListLowerCase)
  {
    final Map<String, String> map = new HashMap<>(headersListLowerCase.size());
    for (final HeaderEntry header : headersListLowerCase)
    {
      if (!map.containsKey((header.getKey())))
      {
        map.put(header.getKey(), header.getValue());
      }
      else
      {
        final boolean skipMerge = commaNotMergableHeaders.contains(header.getKey());
        /*
         * See DP-971 for more context of this code but here we are trying to merge headers values.
         * HTTP 1.1 Section 4.2 (RFC 2616):
         * Multiple message-header fields with the same field-name MAY be present in a message if and
         * only if the entire field-value for that header field is defined as a comma-separated list
         * [i.e., #(values)].
         * It MUST be possible to combine the multiple header fields into one "field-name: field-value"
         * pair, without changing the semantics of the message, by appending each subsequent field-value
         * to the first, each separated by a comma.
         *
         * Newer versions of HTTP should not change that:
         * HTTP/2 leaves all of HTTP 1.1's high-level semantics, such as methods, status codes,
         * header fields, and URIs, the same. What is new is how the data is framed and transported
         * between the client and the server.
         * HTTP/3 uses the same semantics as HTTP/1.1 and HTTP/2 (the same operations like GET and POST)
         * and same response codes (like 200 or 404) but uses a different transport protocol aware of
         * these semantics and capable of recovering from packet loss with minimal performance loss.
        */
        if (skipMerge)
        {
          /*
           * Concatenating Set-Cookie header values using "\n" did not work, so we try to merge only
           * those headers which can be concatenated by "," character, while other duplicated headers
           * we just overwrite.
           */
          Timber.d("convertHeaderEntriesToMap() overwrites value of `%s` header",
              header.getKey());
          map.put(header.getKey(), header.getValue());
        }
        else if (map.get(header.getKey()).equalsIgnoreCase(header.getValue()))
        {
          Timber.d("convertHeaderEntriesToMap() skips duplicated value of `%s` header",
              header.getKey());
        }
        else
        {
          final String mergedValue = map.get(header.getKey()) + ", " + header.getValue();
          Timber.d("convertHeaderEntriesToMap() merges values of `%s` header",
              header.getKey());
          map.put(header.getKey(), mergedValue);
        }
      }
    }
    return map;
  }

  /**
   * Convert list to map of headers
   * @param map map of headers
   * @return list of headers
   */
  public static List<HeaderEntry> convertMapToHeadersList(final Map<String, String> map)
  {
    final List<HeaderEntry> list = new ArrayList<>(map.size());
    for (final Map.Entry<String, String> header : map.entrySet())
    {
      list.add(new HeaderEntry(header.getKey(), header.getValue()));
    }
    return list;
  }

  /**
   * Convert String to ByteBuffer
   * @param string string
   * @param charset charset
   * @return byte buffer (*direct* buffer, allocated with `ByteBuffer.allocateDirect`)
   */
  public static ByteBuffer stringToByteBuffer(final String string, final Charset charset)
  {
    final byte[] bytes = string.getBytes(charset);
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bytes.length);
    byteBuffer.put(bytes);
    return byteBuffer;
  }

  /**
   * Convert ByteBuffer to byte array
   * @param buffer buffer
   * @return byte array
   */
  public static byte[] byteBufferToByteArray(final ByteBuffer buffer)
  {
    buffer.rewind();
    final byte[] bytes = new byte[buffer.limit()];
    buffer.get(bytes);
    return bytes;
  }

  /**
   * Check if URL is absolute
   * @param url URL
   * @return URL is absolute
   * @throws URISyntaxException
   */
  public static boolean isAbsoluteUrl(final String url) throws URISyntaxException
  {
    final URI uri = new URI(url);
    return uri.isAbsolute();
  }

  /**
   * Build full absolute URL from base and relative URLs
   * @param baseUrl base URL
   * @param relativeUrl relative URL
   * @return full absolute URL
   */
  public static String getAbsoluteUrl(final String baseUrl, final String relativeUrl)
    throws MalformedURLException
  {
    return new URL(new URL(baseUrl), relativeUrl).toExternalForm();
  }

  /**
   * Extract path with query from URL
   * @param urlString URL
   * @return path with optional query part
   * @throws MalformedURLException
   */
  public static String extractPathWithQuery(final String urlString) throws MalformedURLException
  {
    final URL url = new URL(urlString);
    final StringBuilder sb = new StringBuilder(url.getPath());
    if (url.getQuery() != null)
    {
      sb.append("?");
      sb.append(url.getQuery());
    }
    return sb.toString();
  }

  private static String U2028 = new String(new byte[]{ (byte)0xE2, (byte)0x80, (byte)0xA8 });
  private static String U2029 = new String(new byte[]{ (byte)0xE2, (byte)0x80, (byte)0xA9 });

  /**
   * Escape JavaString string
   * @param line unescaped string
   * @return escaped string
   */
  public static String escapeJavaScriptString(final String line)
  {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < line.length(); i++)
    {
      char c = line.charAt(i);
      switch (c)
      {
        case '"':
        case '\'':
        case '\\':
          sb.append('\\');
          sb.append(c);
          break;

        case '\n':
          sb.append("\\n");
          break;

        case '\r':
          sb.append("\\r");
          break;

        default:
          sb.append(c);
      }
    }

    return sb.toString()
        .replace(U2028, "\u2028")
        .replace(U2029, "\u2029");
  }

  /**
   * Checks that the host is subdomain of (or equals to) the domain
   * @param host host
   * @param domain domain
   * @return host is a subdomain or domain
   */
  public static boolean isSubdomainOrDomain(final String host, final String domain)
  {
    if (host.length() == 0 || domain.length() == 0 || !host.endsWith(domain))
    {
      return false;
    }
    final String[] domainPieces = domain.split("\\.");
    final String[] hostPieces = host.split("\\.");
    if (hostPieces.length < domainPieces.length)
    {
      return false;
    }
    final int domainLastPiece = domainPieces.length - 1;
    final int hostLastPiece = hostPieces.length - 1;
    for (int piece = 0; piece <= domainLastPiece; ++piece)
    {
      if (!hostPieces[hostLastPiece - piece].equals(domainPieces[domainLastPiece - piece]))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Creates allowlisting filter for a given domain
   * @param filterEngine Filtering engine
   * @param domain Domain that needs to be allow listed
   * @return Allowlisting filter
   */
  public static Filter createDomainAllowlistingFilter(final FilterEngine filterEngine,
                                                      final String domain)
  {
    return filterEngine.getFilter("@@||" + domain + "^$document,domain=" + domain);
  }
}
