package org.adblockplus.libadblockplus.android.webview.content_type;

import android.net.Uri;

import org.adblockplus.libadblockplus.FilterEngine;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.adblockplus.libadblockplus.HttpClient.HEADER_ACCEPT;
import static org.adblockplus.libadblockplus.HttpClient.HEADER_CONTENT_LENGTH;
import static org.adblockplus.libadblockplus.HttpClient.HEADER_REQUESTED_WITH;
import static org.adblockplus.libadblockplus.HttpClient.HEADER_REQUESTED_WITH_XMLHTTPREQUEST;
import static org.adblockplus.libadblockplus.HttpClient.MIME_TYPE_TEXT_HTML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HeadersContentTypeDetectorTest extends BaseContentTypeDetectorTest
{
  private static final Uri URI_EXAMPLE = parseUri("https://example.com");

  private static final Map<String, String> XML_HEADER = new HashMap<String, String>()
  {{
    put(HEADER_REQUESTED_WITH, HEADER_REQUESTED_WITH_XMLHTTPREQUEST);
  }};
  private static final Map<String, String> BROKEN_XML_HEADER = new HashMap<String, String>()
  {{
    put(HEADER_REQUESTED_WITH, "OtherValue");
  }};
  private static final Map<String, String> SUBDOCUMENT_HEADER = new HashMap<String, String>()
  {{
    put(HEADER_ACCEPT, MIME_TYPE_TEXT_HTML);
  }};
  private static final Map<String, String> CONTENT_LENGTH_HEADER = new HashMap<String, String>()
  {{
    put(HEADER_CONTENT_LENGTH, "1000");
  }};

  private final HeadersContentTypeDetector detector = new HeadersContentTypeDetector();

  @Test
  public void testHeaderRequests()
  {
    assertEquals(FilterEngine.ContentType.XMLHTTPREQUEST,
        detector.detect(mockRequest(URI_EXAMPLE, XML_HEADER)));
    assertEquals(FilterEngine.ContentType.SUBDOCUMENT,
        detector.detect(mockRequest(URI_EXAMPLE, SUBDOCUMENT_HEADER)));

    // not detected
    assertNull(detector.detect(mockRequest(URI_EXAMPLE, BROKEN_XML_HEADER)));

    // totally different header (expected not detected)
    assertNull(detector.detect(mockRequest(URI_EXAMPLE, CONTENT_LENGTH_HEADER)));
  }
}
