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

package org.adblockplus.libadblockplus.android.webview.content_type;

import android.net.Uri;
import android.webkit.WebResourceRequest;

import org.mockito.Mockito;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@SuppressWarnings("WeakerAccess") // Base method
abstract class BaseContentTypeDetectorTest
{
  protected static WebResourceRequest mockRequest(
      final Uri uri,
      final Map<String, String> headers)
  {
    final WebResourceRequest request = Mockito.mock(WebResourceRequest.class);
    Mockito.when(request.getRequestHeaders()).thenReturn(headers);
    Mockito.when(request.getUrl()).thenReturn(uri);
    return request;
  }

  /**
   * Mocks {@link Uri#toString()} method with returning the actual url
   *
   * @param url that will be returned when calling to {@link Uri#toString()}
   * @return mock {@link Uri} object
   */
  protected static Uri parseUri(final String url)
  {
    final Uri uri = Mockito.mock(Uri.class);
    String uriPath = null;
    try
    {
      uriPath = new URI(url).getPath();
    }
    catch (final URISyntaxException e){}
    Mockito.when(uri.toString()).thenReturn(url);
    Mockito.when(uri.getPath()).thenReturn(uriPath);
    return uri;
  }
}
