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

package org.adblockplus.libadblockplus.sitekey;

import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.security.SignatureVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

public class SiteKeysConfiguration
{
  private SignatureVerifier signatureVerifier;
  private PublicKeyHolder publicKeyHolder;
  private HttpClient httpClient;
  private SiteKeyVerifier siteKeyVerifier;

  // Allow doing sitekey checks even if AA subscription is not listed or disabled.
  // (this helps to apply sitekey rules that come not from AA subscription,
  // eg. from test pages subscription or partner-specific subscriptions)
  private AtomicBoolean forceChecks = new AtomicBoolean(false);

  public SiteKeysConfiguration(
      final SignatureVerifier signatureVerifier,
      final PublicKeyHolder publicKeyHolder,
      final HttpClient httpClient,
      final SiteKeyVerifier siteKeyVerifier)
  {
    this.signatureVerifier = signatureVerifier;
    this.publicKeyHolder = publicKeyHolder;
    this.httpClient = httpClient;
    this.siteKeyVerifier = siteKeyVerifier;
  }

  public SignatureVerifier getSignatureVerifier()
  {
    return signatureVerifier;
  }

  public PublicKeyHolder getPublicKeyHolder()
  {
    return publicKeyHolder;
  }

  public HttpClient getHttpClient()
  {
    return httpClient;
  }

  public SiteKeyVerifier getSiteKeyVerifier()
  {
    return siteKeyVerifier;
  }

  public boolean getForceChecks()
  {
    return forceChecks.get();
  }

  public void setForceChecks(boolean forceChecks)
  {
    this.forceChecks.set(forceChecks);
  }
}
