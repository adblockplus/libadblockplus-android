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

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;

import timber.log.Timber;

/**
 * Decides what extractor has to be used by analyzing the data
 * that {@link AdblockWebView} has.
 * In particular, it reads the {@link WebResourceRequest#isForMainFrame()} property
 * in order to understand, if we need to fire fallback extractor
 */
// Mainly exists for composing site key extraction and decoupling it from the WebView.
// Using both extractors directly in AdblockWebView might be error-prone
// because one might forgot that some aspects of it
// for example, that `JsSiteKeyExtractor` ads a javascript interface handler
// to the WebView
class CombinedSiteKeyExtractor implements SiteKeyExtractor
{
  private final SiteKeyExtractor httpExtractor;
  private final SiteKeyExtractor jsExtractor;

  @SuppressWarnings("WeakerAccess")
  protected CombinedSiteKeyExtractor(final AdblockWebView webView)
  {
    httpExtractor = new HttpHeaderSiteKeyExtractor(webView);
    // by calling it new javascript interface handler added to the WebView
    jsExtractor = new JsSiteKeyExtractor(webView);
  }

  /*
  This implementation has the right to judge when to call what extractor
  On the main frame it calls JsSiteKeyExtractor, otherwise falls back to HttpHeaderSiteKeyExtractor

  This is to remove the obligation for the concrete implementations to decide
  Any of it may be used directly and will be doing its job properly for any request
   */
  @Override
  public WebResourceResponse obtainAndCheckSiteKey(
      final AdblockWebView webView, final WebResourceRequest frameRequest)
  {
    if (frameRequest.isForMainFrame())
    {
      // JsSiteKeyExtractor will hold the thread with the main frame request
      jsExtractor.obtainAndCheckSiteKey(webView, frameRequest);
    }
    // at this point non-frame requests must have been filtered by ContentTypeDetector
    // so this presumably all non-main frame requests are of SUBDOCUMENT type (frames and iframes)
    else
    {
      // we cannot inject JS, hence get sitekey into iframe,
      // falling back to the old implementation
      Timber.d("Falling back to native sitekey requests for %s",
          frameRequest.getUrl().toString());
      return httpExtractor.obtainAndCheckSiteKey(webView, frameRequest);
    }

    return AdblockWebView.WebResponseResult.ALLOW_LOAD;
  }

  @Override
  public void setSiteKeysConfiguration(final SiteKeysConfiguration siteKeysConfiguration)
  {
    httpExtractor.setSiteKeysConfiguration(siteKeysConfiguration);
    jsExtractor.setSiteKeysConfiguration(siteKeysConfiguration);
  }

  @Override
  public void setEnabled(final boolean enabled)
  {
    httpExtractor.setEnabled(enabled);
    jsExtractor.setEnabled(enabled);
  }
}
