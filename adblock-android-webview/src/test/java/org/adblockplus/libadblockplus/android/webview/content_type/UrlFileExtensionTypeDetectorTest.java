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
        "https://images-na.ssl-images-amazon.com/images/I/31xSfk%2BkPLL._RC%7C31QrsA7V+jL.css_.{0}?AUIClients/SharedShoppingCartMobileAsset#243485-T1.211070-T1.242807-T1",
        "https://images-na.ssl-images-amazon.com/images/I/011kwg0OTQL._RC%7C01qIaIxJsJL.js,01dXSEbmdvL.js,01IA5zDheBL.js,01YsvHiCZdL.js,61HqCwrIKML.js,21OfLVGQ9zL.js,01XiAWfViUL.js,01fpGYmrQEL.js,014kCoIHgIL.js,01hkseOXj6L.js,01AUzbXZhcL.js,311A0yCIeJL.js,01iRN5bMQkL.js,51ZiyAEesrL.js,01IC-gBKyYL.js,61XoD-Agv6L.js,01PjTMZrF+L.js,01XEEGOr+kL.js,01PQKs49DyL.js,41vKxppoE9L.js,01y8JNON9+L.js,41heHSWCouL.js,01S8y9NkxoL.js,61zZW8V9l8L.js_.{0}?AUIClients/DetailPageMobileWebMetaAsset_SAS_239484_TradeIn#mobile.us.292695-C.292696-C.230697-T1.283706-T1.266185-C.275156-T1.290956-T1.268789-T1.271591-T1.258182-T1.247181-T1.202285-C.281385-T1.224722-T1.169593-T1.172044-T1",
        "http://www.xn--asasd-xraf.com/as%E7%9C%8B%C3%A7l.{0}"
      };

    for (final String template : templates)
    {
      final String url = MessageFormat.format(template, extension);
      final WebResourceRequest request = mockRequest(parseUri(url), null);
      final FilterEngine.ContentType actualContentType = detector.detect(request);
      System.out.printf("assertUrlMatchCase %s expects %s, got %s\n", url, expectedContentType.toString(),
          actualContentType == null ? "NULL" : actualContentType.toString());
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
    assertNull(detector.detect(mockRequest(parseUri("http://www.domain.com/file.gif/"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://www.domain.com"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://www.domain.com?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://www.gif.com?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://www.domain.com/file.gifrandomwords?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://username:password@domain.com/file.gifrandomwords?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(mockRequest(parseUri("http://username:password@domain.com/path/like/file.gifrandomwords?imasd=1293&asasd=123"), null)));
    assertNull(detector.detect(null));
  }
}
