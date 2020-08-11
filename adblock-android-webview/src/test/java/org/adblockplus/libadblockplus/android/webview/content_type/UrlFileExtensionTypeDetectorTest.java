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

import android.webkit.WebResourceRequest;

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.android.webview.UrlFileExtensionTypeDetector;
import org.junit.Test;

import java.text.MessageFormat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class UrlFileExtensionTypeDetectorTest extends BaseContentTypeDetectorTest
{
  private final UrlFileExtensionTypeDetector detector = new UrlFileExtensionTypeDetector();

  private void assertUrlMatchCase(final FilterEngine.ContentType expectedContentType,
                                  final String extension)
  {
    final String[] templates =
      {
        "http://domain.com/file.{0}",
        "http://www.domain.com/file.{0}",
        "https://domain.com/file.{0}",
        "https://domain.com:4000/file.{0}",
        "https://www.domain.com/file.{0}#fragment",
        "https://www.domain.com/file.{0}?",
        "https://www.domain.com/file.{0}?name=value",
        "https://www.domain.com/file.{0}?name.asd=value.asd",
        "https://www.domain.com/file.{0}?name=value#fragment",
        "https://www.domain.com/file.{0}?name=value&something=else",
        "https://www.domain.com/file.{0}?name=value;something=else#fragment",
        "www.domain.com/file.{0}?name=value;something=else#fragment",
        "http://username:password@domain.com/path/like/file.{0}?name=value;something=else#fragment",
        "http://username:password@domain.com/file.random.{0}?name=value;something=else#fragment",
      };

    for (final String template : templates)
    {
      final String url = MessageFormat.format(template, extension);
      final WebResourceRequest request = mockRequest(parseUri(url), null);
      final FilterEngine.ContentType actualContentType = detector.detect(request);

      assertNotNull(actualContentType);
      assertEquals(expectedContentType, actualContentType);
    }
  }

  private void assertUrl(final FilterEngine.ContentType expectedContentType, final String extension)
  {
    assertUrlMatchCase(expectedContentType, extension.toLowerCase());
    assertUrlMatchCase(expectedContentType, extension.toUpperCase());
  }

  @Test
  public void testScript()
  {
    assertUrl(FilterEngine.ContentType.SCRIPT, "js");
  }

  @Test
  public void testCSS()
  {
    assertUrl(FilterEngine.ContentType.STYLESHEET, "css");
  }

  @Test
  public void testImage()
  {
    final FilterEngine.ContentType expectedContentType = FilterEngine.ContentType.IMAGE;
    final String[] imageExtensions = {"gif", "png", "jpg", "jpe", "jpeg", "bmp",
      "apng", "cur", "jfif", "ico", "pjpeg", "pjp", "svg", "tif", "tiff", "webp"};
    for (final String extension : imageExtensions)
    {
      assertUrl(expectedContentType, extension);
    }
  }

  @Test
  public void testFont()
  {
    final FilterEngine.ContentType expectedContentType = FilterEngine.ContentType.FONT;
    assertUrl(expectedContentType, "ttf");
    assertUrl(expectedContentType, "woff");
    assertUrl(expectedContentType, "woff2");
  }

  @Test
  public void testHtml()
  {
    final FilterEngine.ContentType expectedContentType = FilterEngine.ContentType.SUBDOCUMENT;
    assertUrl(expectedContentType, "htm");
    assertUrl(expectedContentType, "html");
  }

  @Test
  public void testMedia()
  {
    final FilterEngine.ContentType expectedContentType = FilterEngine.ContentType.MEDIA;
    final String[] mediaExtensions = {"webm", "mkv", "flv", "vob", "ogv", "drc", "mng", "avi",
      "gifv", "qt", "wmv", "yuv", "rm", "rmvb", "asf", "amv", "mp4", "m4p", "mp2", "mpe", "mov",
      "mpv", "mpg", "mpeg", "m2v", "m4v", "svi", "3gp", "3g2", "mxf", "roq", "nsv", "8svx",
      "aa", "aac", "aax", "act", "aiff", "alac", "amr", "ape", "au", "awb", "cda", "dct",
      "dss", "dvf", "flac", "gsm", "iklax", "ivs", "m4a", "m4b", "mmf", "mogg",
      "mp3", "mpc", "msv", "nmf", "oga", "ogg", "opus", "ra", "raw", "rf64", "sln", "tta",
      "voc", "vox", "wav", "wma", "wv"};
    for (final String extension : mediaExtensions)
    {
      assertUrl(expectedContentType, extension);
    }
  }

  @Test
  public void testInvalidUrl()
  {
    final WebResourceRequest request = mockRequest(parseUri("some_invalid_url/file.unknown"), null);
    assertNull(detector.detect(request));
  }

  @Test
  public void testUnknownContentType()
  {
    assertNull(detector.detect(mockRequest(parseUri("http://www.domain.com/file.unknown"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://www.domain.com"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://www.domain.com?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://www.gif.com?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://www.domain.com/file.gifrandomwords?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://username:password@domain.com/file.gifrandomwords?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://username:password@domain.com/path/like/file.gifrandomwords?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(null));
  }
}
