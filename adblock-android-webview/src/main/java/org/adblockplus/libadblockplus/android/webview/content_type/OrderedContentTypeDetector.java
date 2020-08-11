package org.adblockplus.libadblockplus.android.webview.content_type;

import android.webkit.WebResourceRequest;

import org.adblockplus.libadblockplus.FilterEngine;

/**
 * Detects content type based on {@link HeadersContentTypeDetector}
 * and {@link org.adblockplus.libadblockplus.android.webview.UrlFileExtensionTypeDetector}
 * <p>
 * Can accept a list of content type detectors
 * <p>
 * {@link FilterEngine.ContentType#XMLHTTPREQUEST} is detected separately
 * just by checking header `HEADER_REQUESTED_WITH_XMLHTTPREQUEST`
 */
public class OrderedContentTypeDetector implements ContentTypeDetector
{
  private final ContentTypeDetector[] detectors;

  /**
   * Creates an instance of a `MultipleContentTypeDetector`
   * with provided detectors
   * <p>
   * At the moment only {@link HeadersContentTypeDetector}
   * and {@link org.adblockplus.libadblockplus.android.webview.UrlFileExtensionTypeDetector} exists
   *
   * @param detectors an array of instances of {@link ContentTypeDetector}
   */
  public OrderedContentTypeDetector(final ContentTypeDetector... detectors)
  {
    this.detectors = detectors;
  }

  @Override
  public FilterEngine.ContentType detect(final WebResourceRequest request)
  {
    FilterEngine.ContentType contentType;

    for (final ContentTypeDetector detector : detectors)
    {
      contentType = detector.detect(request);

      // if contentType == null, that means
      // that the detector was unavailable to detect content type
      if (contentType != null)
      {
        return contentType;
      }
    }

    // returning result
    // if nothing found, its safe to return null
    return null;
  }
}
