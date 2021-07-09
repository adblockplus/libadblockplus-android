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

package org.adblockplus;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Builder for building different kinds of Adblock filters.
 *
 * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters">How to write filters</a>
 */
public class AdblockFilterBuilder
{
  private final AdblockEngine adblockEngine;
  private String url;
  private boolean isAllowlisting;
  private boolean isBeginMatchingDomain;
  private Set<ContentType> contentTypes;
  private String domainRestriction;
  private String sitekey;

  /**
   * Constructs adblock filter builder
   *
   * @param adblockEngine adblock engine
   */
  public AdblockFilterBuilder(@NotNull final AdblockEngine adblockEngine)
  {
    this.adblockEngine = adblockEngine;
  }

  /**
   * Allowlist web resource for a given url
   *
   * @param url url address to be allowlisted
   * @return AdblockFilterBuilder
   * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#allowlist">allowlist</a>
   */
  @NotNull
  public AdblockFilterBuilder allowlistAddress(@NotNull final String url)
  {
    this.isAllowlisting = true;
    this.url = url;
    return this;
  }

  /**
   * Block web resource  for  a given url
   *
   * @param url url address to be blocked
   * @return AdblockFilterBuilder
   * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#creating-filters">create filters</a>
   */
  @NotNull
  public AdblockFilterBuilder blockAddress(@NotNull final String url)
  {
    this.isAllowlisting = false;
    this.url = url;
    return this;
  }

  /**
   * Set matching at the beginning of an address
   *
   * @return AdblockFilterBuilder
   * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#anchors">anchors</a>
   */
  @NotNull
  public AdblockFilterBuilder setBeginMatchingDomain()
  {
    this.isBeginMatchingDomain = true;
    return this;
  }

  /**
   * Set content types to be applied for the filter
   *
   * @param contentTypes Set that contains content types to be applied for the filter
   * @return AdblockFilterBuilder
   * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#type-options">Type options</a>
   */
  @NotNull
  public AdblockFilterBuilder setContentTypes(@NotNull final Set<ContentType> contentTypes)
  {
    this.contentTypes = contentTypes;
    return this;
  }

  /**
   * Set domain restriction.
   *
   * @param domainRestriction domain to be restricted
   * @return Adblock filter builder
   * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#domain-restrictions">
   */
  @NotNull
  public AdblockFilterBuilder setDomainRestriction(@NotNull final String domainRestriction)
  {
    this.domainRestriction = domainRestriction;
    return this;
  }

  /**
   * Sets site key restriction
   *
   * @param sitekey sitekey value
   * @return Adblock filter builder
   * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#sitekey-restrictions">
   */
  @NotNull
  public AdblockFilterBuilder setSitekey(@NotNull final String sitekey, final boolean isAllowlisting)
  {
    this.isAllowlisting = isAllowlisting;
    this.sitekey = sitekey;
    return this;
  }


  /**
   * Builds filter rule for a given paramaters
   *
   * @return Filter
   */
  @NotNull
  public Filter build()
  {
    final StringBuilder filterText = new StringBuilder();

    if (isAllowlisting)
    {
      filterText.append("@@");
    }
    if (isBeginMatchingDomain)
    {
      filterText.append("||");
    }

    final StringBuilder contentTypesSB = new StringBuilder();
    if (contentTypes != null)
    {
      for (final ContentType contentType : contentTypes)
      {
        if (contentTypesSB.length() == 0)
        {
          contentTypesSB.append("$");
        }
        else
        {
          contentTypesSB.append(",");
        }
        contentTypesSB.append(contentType.toString().toLowerCase());
      }
    }

    if (sitekey != null)
    {
      filterText.append(contentTypesSB).append(",sitekey=").append(sitekey);
    }
    else
    {
      filterText.append(url).append("^").append(contentTypesSB);
      if (domainRestriction != null)
      {
        filterText.append(",domain=").append(domainRestriction);
      }
    }
    return adblockEngine.getFilterFromText(filterText.toString());
  }
}
