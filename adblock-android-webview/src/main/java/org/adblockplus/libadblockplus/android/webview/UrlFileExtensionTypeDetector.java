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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlFileExtensionTypeDetector
{
  private static final String[] EXTENSIONS_JS    = { "js" };
  private static final String[] EXTENSIONS_CSS   = { "css" };
  private static final String[] EXTENSIONS_FONT  = { "ttf", "woff", "woff2" };
  private static final String[] EXTENSIONS_HTML = { "htm", "html" };
  // listed https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types
  private static final String[] EXTENSIONS_IMAGE = { "gif", "png", "jpg", "jpe", "jpeg", "bmp",
    "apng", "cur", "jfif", "ico", "pjpeg", "pjp", "svg", "tif", "tiff", "webp" };
  // video files listed here https://en.wikipedia.org/wiki/Video_file_format
  // audio files listed here https://en.wikipedia.org/wiki/Audio_file_format
  private static final String[] EXTENSIONS_MEDIA = { "webm", "mkv", "flv", "vob", "ogv",
    "drc", "mng", "avi", "mov", "gifv", "qt", "wmv", "yuv", "rm", "rmvb", "asf", "amv", "mp4",
    "m4p", "mp2","mpe", "mpv", "mpg", "mpeg", "m2v", "m4v", "svi", "3gp", "3g2", "mxf", "roq",
    "nsv", "8svx", "aa", "aac", "aax", "act", "aiff", "alac", "amr", "ape", "au", "awb",
    "cda", "dct", "dss", "dvf", "flac", "gsm", "iklax", "ivs", "m4a", "m4b", "mmf", "mogg",
    "mp3", "mpc", "msv", "nmf", "oga", "ogg", "opus", "ra", "raw", "rf64", "sln", "tta",
    "voc", "vox", "wav", "wma", "wv" };

  private static final Map<String, FilterEngine.ContentType> extensionTypeMap
    = new HashMap<String, FilterEngine.ContentType>();

  private static void mapExtensions(
    final String[] extensions,
    final FilterEngine.ContentType contentType)
  {
    for (final String extension : extensions)
    {
      // all comparisons are in lower case, force that the extensions are in lower case
      extensionTypeMap.put(extension.toLowerCase(), contentType);
    }
  }

  static
  {
    mapExtensions(EXTENSIONS_JS, FilterEngine.ContentType.SCRIPT);
    mapExtensions(EXTENSIONS_CSS, FilterEngine.ContentType.STYLESHEET);
    mapExtensions(EXTENSIONS_FONT, FilterEngine.ContentType.FONT);
    mapExtensions(EXTENSIONS_HTML, FilterEngine.ContentType.SUBDOCUMENT);
    mapExtensions(EXTENSIONS_IMAGE, FilterEngine.ContentType.IMAGE);
    mapExtensions(EXTENSIONS_MEDIA, FilterEngine.ContentType.MEDIA);
  };

  private static final Pattern RE_FILE_EXTENSION =
    Pattern.compile(".*/.+?(\\.(.+?))+(?:\\?.*)?(?:#.*)?$", Pattern.CASE_INSENSITIVE);

  /**
   * Detects ContentType for given URL
   * @param url URL
   * @return ContentType or `null` if not detected
   */
  public FilterEngine.ContentType detect(final String url)
  {
    if (url == null)
    {
      return null;
    }
    final Matcher result = RE_FILE_EXTENSION.matcher(url);
    if (result.find() && result.groupCount() == 2)
    {
      final String fileExtension = result.group(2).toLowerCase();
      return extensionTypeMap.get(fileExtension);
    }
    return null;
  }
}
