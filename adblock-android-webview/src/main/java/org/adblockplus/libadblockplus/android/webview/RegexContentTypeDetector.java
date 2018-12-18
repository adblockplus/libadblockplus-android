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

import org.adblockplus.libadblockplus.FilterEngine;

import java.util.regex.Pattern;

public class RegexContentTypeDetector
{
  private static final Pattern RE_JS = Pattern.compile("\\.js(?:\\?.+)?", Pattern.CASE_INSENSITIVE);
  private static final Pattern RE_CSS = Pattern.compile("\\.css(?:\\?.+)?", Pattern.CASE_INSENSITIVE);
  private static final Pattern RE_IMAGE = Pattern.compile("\\.(?:gif|png|jpe?g|bmp|ico)(?:\\?.+)?", Pattern.CASE_INSENSITIVE);
  private static final Pattern RE_FONT = Pattern.compile("\\.(?:ttf|woff)(?:\\?.+)?", Pattern.CASE_INSENSITIVE);
  private static final Pattern RE_HTML = Pattern.compile("\\.html?(?:\\?.+)?", Pattern.CASE_INSENSITIVE);

  /**
   * Detects ContentType for given URL
   * @param url URL
   * @return ContentType or `null` if not detected
   */
  public FilterEngine.ContentType detect(final String url)
  {
    FilterEngine.ContentType contentType = null;
    if (RE_JS.matcher(url).find())
    {
      contentType = FilterEngine.ContentType.SCRIPT;
    }
    else if (RE_CSS.matcher(url).find())
    {
      contentType = FilterEngine.ContentType.STYLESHEET;
    }
    else if (RE_IMAGE.matcher(url).find())
    {
      contentType = FilterEngine.ContentType.IMAGE;
    }
    else if (RE_FONT.matcher(url).find())
    {
      contentType = FilterEngine.ContentType.FONT;
    }
    else if (RE_HTML.matcher(url).find())
    {
      contentType = FilterEngine.ContentType.SUBDOCUMENT;
    }
    return contentType;
  }
}
