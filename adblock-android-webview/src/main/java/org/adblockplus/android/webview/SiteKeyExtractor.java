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

package org.adblockplus.android.webview;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import org.adblockplus.sitekey.SiteKeysConfiguration;

/**
 * Extracts a <i>Site Key</i> from an {@link AdblockWebView}'s internals and verifies the Site Key
 * What is expected from this class:
 * <ol>
 *   <li>Extract the <i>Site Key</i> from all available resources from {@link AdblockWebView}</li>
 *   <li>Use {@link org.adblockplus.sitekey.SiteKeyVerifier} to verify it</li>
 * </ol>
 * An instance of {@link org.adblockplus.sitekey.SiteKeyVerifier} is set to
 * `siteKeysConfiguration` property
 * {@link AdblockWebView} accepts the extractor
 * by calling {@link AdblockWebView#setSiteKeyExtractor(SiteKeyExtractor)}
 * For example, custom HTTP request can be made and resulting HTTP headers can be used to
 * extract the Site Key from the header
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
   * This method must perform custom HTTP request or return one of states from
   * {@link AdblockWebView.WebResponseResult}
   *
   * @param request a request that might be used for understanding
   *                additional options (e.g. is the request intended for the main frame)
   * @return a response that will be passed to
   */
  WebResourceResponse extract(WebResourceRequest request);

  /**
   * Notifies about starting of a new page
   */
  void startNewPage();

  /**
   * Blocks the calling thread while checking the sitekey
   * <p>
   * Will be removed later in a favor of setting internal WebViewClient
   * for every SiteKeyExtractor
   *
   * @param url a request url which is held back by this call
   * @param isMainFrame a boolean indicating whether this is a main or a subframe request
   * @return true if had to wait
   */
  boolean waitForSitekeyCheck(final String url, final boolean isMainFrame);

  /**
   * This method is called by the {@link AdblockWebView} during
   * {@link AdblockWebView#setSiteKeysConfiguration(SiteKeysConfiguration)}
   * You can later use siteKeysConfiguration in order to verify the sitekey
   *
   * @param siteKeysConfiguration the configuration to set
   */
  void setSiteKeysConfiguration(SiteKeysConfiguration siteKeysConfiguration);

  void setEnabled(boolean enabled);
}
