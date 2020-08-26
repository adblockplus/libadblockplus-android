package org.adblockplus.libadblockplus.android.webview.content_type;

import android.net.Uri;

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.android.webview.UrlFileExtensionTypeDetector;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.adblockplus.libadblockplus.HttpClient.HEADER_ACCEPT;
import static org.adblockplus.libadblockplus.HttpClient.HEADER_REQUESTED_WITH;
import static org.adblockplus.libadblockplus.HttpClient.HEADER_REQUESTED_WITH_XMLHTTPREQUEST;
import static org.adblockplus.libadblockplus.HttpClient.MIME_TYPE_TEXT_HTML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OrderedContentTypeDetectorTest extends BaseContentTypeDetectorTest
{
  private static final Uri URI_IMAGE = parseUri("https://www.example.com/file.jpg?name=value");

  private static final Map<String, String> XML_HEADER = new HashMap<String, String>()
  {{
    put(HEADER_REQUESTED_WITH, HEADER_REQUESTED_WITH_XMLHTTPREQUEST);
  }};
  private static final Map<String, String> SUBDOCUMENT_HEADER = new HashMap<String, String>()
  {{
    put(HEADER_ACCEPT, MIME_TYPE_TEXT_HTML);
  }};

  @Test
  public void testEmptyDetector()
  {
    assertNull(new OrderedContentTypeDetector().detect(mockRequest(URI_IMAGE, XML_HEADER)));
  }

  @Test
  public void testProperDetectingOrder()
  {
    final OrderedContentTypeDetector regexFirst = new OrderedContentTypeDetector(
        new UrlFileExtensionTypeDetector(),
        new HeadersContentTypeDetector()
    );
    final OrderedContentTypeDetector headersFirst = new OrderedContentTypeDetector(
        new HeadersContentTypeDetector(),
        new UrlFileExtensionTypeDetector()
    );

    assertEquals(FilterEngine.ContentType.IMAGE,
        regexFirst.detect(mockRequest(URI_IMAGE, XML_HEADER)));
    assertEquals(FilterEngine.ContentType.XMLHTTPREQUEST,
        headersFirst.detect(mockRequest(URI_IMAGE, XML_HEADER)));
    assertEquals(FilterEngine.ContentType.SUBDOCUMENT,
        headersFirst.detect(mockRequest(URI_IMAGE, SUBDOCUMENT_HEADER)));
  }
}
