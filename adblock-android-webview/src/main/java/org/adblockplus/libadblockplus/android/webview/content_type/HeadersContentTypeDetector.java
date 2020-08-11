package org.adblockplus.libadblockplus.android.webview.content_type;

import android.webkit.WebResourceRequest;

import org.adblockplus.libadblockplus.FilterEngine;

import java.util.Map;

import timber.log.Timber;

import static org.adblockplus.libadblockplus.HttpClient.HEADER_ACCEPT;
import static org.adblockplus.libadblockplus.HttpClient.HEADER_REQUESTED_WITH;
import static org.adblockplus.libadblockplus.HttpClient.HEADER_REQUESTED_WITH_XMLHTTPREQUEST;
import static org.adblockplus.libadblockplus.HttpClient.MIME_TYPE_TEXT_HTML;

/**
 * Detects content type based on headers
 * <p>
 * It has a limited functionality and can detect only
 * two types of content:
 * - {@link FilterEngine.ContentType#XMLHTTPREQUEST} and
 * - {@link FilterEngine.ContentType#SUBDOCUMENT}
 * <p>
 * Should be used in {@link OrderedContentTypeDetector}
 */
public class HeadersContentTypeDetector implements ContentTypeDetector
{
  @Override
  public FilterEngine.ContentType detect(final WebResourceRequest request)
  {
    final Map<String, String> headers = request.getRequestHeaders();

    final boolean isXmlHttpRequest =
        headers.containsKey(HEADER_REQUESTED_WITH) &&
            HEADER_REQUESTED_WITH_XMLHTTPREQUEST.equals(headers.get(HEADER_REQUESTED_WITH));

    if (isXmlHttpRequest)
    {
      Timber.w("using xmlhttprequest content type");
      return FilterEngine.ContentType.XMLHTTPREQUEST;
    }

    final String acceptType = headers.get(HEADER_ACCEPT);
    if (acceptType != null && acceptType.contains(MIME_TYPE_TEXT_HTML))
    {
      Timber.w("using subdocument content type");
      return FilterEngine.ContentType.SUBDOCUMENT;
    }

    // not detected
    return null;
  }
}
