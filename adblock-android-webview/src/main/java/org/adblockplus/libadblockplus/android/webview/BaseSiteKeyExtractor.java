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

import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;

import java.lang.ref.WeakReference;

/*
 * This is base implementation of SiteKeyExtractor
 */
@SuppressWarnings("WeakerAccess") // API
public abstract class BaseSiteKeyExtractor implements SiteKeyExtractor
{
  private SiteKeysConfiguration siteKeysConfiguration;
  private boolean isEnabled = true;
  protected final WeakReference<AdblockWebView> webViewWeakReference;

  protected BaseSiteKeyExtractor(final AdblockWebView webView)
  {
    webViewWeakReference = new WeakReference<>(webView);
  }

  /**
   * Returns the site key config that can be used to retrieve
   * {@link org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier} and verify the site key
   *
   * @return an instance of SiteKeysConfiguration
   */
  protected SiteKeysConfiguration getSiteKeysConfiguration()
  {
    return siteKeysConfiguration;
  }

  @Override
  public void setSiteKeysConfiguration(final SiteKeysConfiguration siteKeysConfiguration)
  {
    this.siteKeysConfiguration = siteKeysConfiguration;
  }

  public boolean isEnabled()
  {
    return isEnabled;
  }

  @Override
  public void setEnabled(final boolean enabled)
  {
    isEnabled = enabled;
  }
}
