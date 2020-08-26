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
import android.webkit.WebView;

import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;

/**
 * Extracts a <i>Site Key</i> from an {@link AdblockWebView}'s internals and verifies the Site Key
 * <p/>
 * What is expected from this class:
 * <ol>
 *   <li>Extract the <i>Site Key</i> from all available resources from {@link AdblockWebView}</li>
 *   <li>Use {@link org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier} to verify it</li>
 * </ol>
 * An instance of {@link org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier} is set to
 * `siteKeysConfiguration` property
 * <p/>
 * {@link AdblockWebView} accepts the extractor
 * by calling {@link AdblockWebView#setSiteKeyExtractor(SiteKeyExtractor)}
 * <p/>
 * For example, custom HTTP request can be made and resulting HTTP headers can be used to
 * extract the Site Key from the header
 * <p/>
 * Or Site Key might be extracted from <i>html</i> root tag by injecting
 * javascript into {@link AdblockWebView} and using JS handler to get the key back to WebView
 *
 * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#sitekey-restrictions">
 * Site Key</a>
 */
public interface SiteKeyExtractor
{
  /**
   * This method is called by the {@link AdblockWebView} during
   * {@link android.webkit.WebViewClient#shouldInterceptRequest(WebView, WebResourceRequest)}
   * <p/>
   * This method must perform custom HTTP request or return one of states from
   * {@link AdblockWebView.WebResponseResult}
   *
   * @param webView corresponding WebView (an instance of {@link AdblockWebView}
   * @param request a request that might be used for understanding
   *                additional options (e.g. is the request intended for the main frame)
   * @return a response that will be passed to
   */
  WebResourceResponse obtainAndCheckSiteKey(AdblockWebView webView,
                                            WebResourceRequest request);

  /**
   * This method is called by the {@link AdblockWebView} during
   * {@link AdblockWebView#setSiteKeysConfiguration(SiteKeysConfiguration)}
   * <p/>
   * You can later use siteKeysConfiguration in order to verify the sitekey
   */
  void setSiteKeysConfiguration(SiteKeysConfiguration siteKeysConfiguration);

  void setEnabled(boolean enabled);
}
