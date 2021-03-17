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

package org.adblockplus.libadblockplus.android.webview;

import android.net.Uri;
import android.webkit.WebView;

import org.adblockplus.libadblockplus.android.AdblockEngineProvider;

import java.nio.charset.StandardCharsets;

public class RequestInterceptor
{
  public static final String DEBUG_URL_HOSTNAME = "abp_filters";
  public static final String COMMAND_STRING_ADD = "ADD";
  public static final String COMMAND_STRING_REMOVE = "REMOVE";
  public static final String COMMAND_STRING_CLEAR = "CLEAR";
  public static final String PAYLOAD_QUERY_PARAMETER_KEY = "base64";
  public static final String RESPONSE_MIME_TYPE = "text/plain";
  public static final String COMMAND_STRING_INVALID_COMMAND = "INVALID_COMMAND";
  public static final String COMMAND_STRING_INVALID_PAYLOAD = "INVALID_PAYLOAD";
  public static final String COMMAND_STRING_OK = "OK";
  public static final String URL_ENCODE_CHARSET = StandardCharsets.UTF_8.name();

  static Boolean isBlockedByHandlingDebugURLQuery(final WebView view, final AdblockEngineProvider provider,
                                                  final Uri url)
  {
    return false;
  }
}
