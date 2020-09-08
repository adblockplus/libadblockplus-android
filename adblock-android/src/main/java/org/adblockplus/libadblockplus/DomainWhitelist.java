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

package org.adblockplus.libadblockplus;

import org.adblockplus.libadblockplus.android.Utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DomainWhitelist
{
  private volatile List<String> domains;

  public boolean hasAnyDomain(final String url, final List<String> referrerChain)
  {
    if (domains == null)
    {
      return false;
    }

    final List<String> referrersAndResourceUrls = referrerChain == null
        ? new ArrayList<String>()
        : new ArrayList<>(referrerChain);
    if (!referrersAndResourceUrls.contains(url))
    {
      referrersAndResourceUrls.add(url);
    }

    for (final String eachUrl : referrersAndResourceUrls)
    {
      final String host;
      try
      {
        host = new URL(eachUrl).getHost();
      }
      catch (final MalformedURLException e)
      {
        continue;
      }
      for (final String whitelistedDomain : domains)
      {
        if (Utils.isSubdomainOrDomain(host, whitelistedDomain))
        {
          return true;
        }
      }
    }
    return false;
  }

  public void setDomains(final List<String> domains)
  {
    this.domains = domains;
  }

  public List<String> getDomains()
  {
    return domains;
  }
}
