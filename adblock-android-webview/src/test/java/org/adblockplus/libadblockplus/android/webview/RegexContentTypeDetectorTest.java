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
import org.junit.Test;

import java.text.MessageFormat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RegexContentTypeDetectorTest
{
  private final RegexContentTypeDetector detector = new RegexContentTypeDetector();

  private void assertUrlMatchCase(final FilterEngine.ContentType expectedContentType,
                                  final String extension)
  {
    final String[] templates =
    {
      "http://domain.com/file.{0}",
      "http://www.domain.com/file.{0}",
      "https://domain.com/file.{0}",
      "https://www.domain.com/file.{0}",
      "https://www.domain.com/file.{0}?",
      "https://www.domain.com/file.{0}?name=value"
    };
    for (final String template : templates)
    {
      final String url = MessageFormat.format(template, extension);
      final FilterEngine.ContentType actualContentType = detector.detect(url);

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
    assertUrl(expectedContentType, "png");
    assertUrl(expectedContentType, "gif");
    assertUrl(expectedContentType, "jpg");
    assertUrl(expectedContentType, "jpeg");
    assertUrl(expectedContentType, "bmp");
    assertUrl(expectedContentType, "ico");
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
  public void testInvalidUrl()
  {
    assertNull(detector.detect("some_invalid_url/file.unknown"));
  }

  @Test
  public void testUnknownContentType()
  {
    assertNull(detector.detect("http://www.domain.com/file.unknown"));
  }
}
