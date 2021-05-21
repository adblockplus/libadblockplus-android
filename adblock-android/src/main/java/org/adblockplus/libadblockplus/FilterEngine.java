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

import org.adblockplus.ContentType;
import org.adblockplus.EmulationSelector;
import org.adblockplus.Filter;
import org.adblockplus.Subscription;

import java.util.List;
import java.util.Set;

public final class FilterEngine
{
  public static final String EMPTY_PARENT = "";
  public static final String EMPTY_SITEKEY = "";
  protected final long ptr;

  FilterEngine(final long jniPlatformPtr)
  {
    this.ptr = jniPlatformPtr;
  }

  private static native void addFilter(long ptr, String raw);

  private static native void removeFilter(long ptr, String raw);

  public Filter getFilterFromText(final String text)
  {
    return null;
  }

  public List<Filter> getListedFilters()
  {
    return null;
  }

  public Subscription getSubscription(final String url)
  {
    return null;
  }

  public List<Subscription> getListedSubscriptions()
  {
    return null;
  }

  public List<Subscription> fetchAvailableSubscriptions()
  {
    return null;
  }

  public String getElementHidingStyleSheet(final String domain)
  {
    return getElementHidingStyleSheet(domain, false);
  }

  public String getElementHidingStyleSheet(final String domain, final boolean specificOnly)
  {
    return null;
  }

  public List<EmulationSelector> getElementHidingEmulationSelectors(final String domain)
  {
    return null;
  }

  /**
   * Checks if any active filter matches the supplied URL.
   * @param url url URL to match.
   * @param contentTypes Content type mask of the requested resource.
   * @param parent immediate parent of the {@param url}.
   * @param siteKey sitekey or null/empty string
   * @param specificOnly if set to `true` then skips generic filters
   * @return Matching filter, or a `null` if there was no match.
   */
  public Filter matches(final String url, final Set<ContentType> contentTypes,
                        final String parent, final String siteKey,
                        final boolean specificOnly)
  {
    return null;
  }

  /**
   * Checks whether the resource at the supplied URL is allowlisted.
   *
   * @param url URL of the resource.
   * @param contentTypes Set of content types for requested resource.
   * @param documentUrls Chain of URLs requesting the resource
   * @param siteKey public key provided by the document, can be empty.
   * @return `true` if the URL is allowlisted.
   */
  public boolean isContentAllowlisted(final String url,
                                      final Set<ContentType> contentTypes,
                                      final List<String> documentUrls,
                                      final String siteKey)
  {
    return false;
  }

  public JsValue getPref(final String pref)
  {
    return null;
  }

  /**
   * Set libadblockplus preference. Only known preferences will be stored after engine is disposed.
   * @param pref preference name. See lib/pref.js in libadblockplus for valid names
   * @param value preference value
   */
  public void setPref(final String pref, final JsValue value)
  {
  }

  public String getHostFromURL(final String url)
  {
    return null;
  }

  public void setAllowedConnectionType(final String value)
  {
  }

  public String getAllowedConnectionType()
  {
    return null;
  }

  public void setAcceptableAdsEnabled(final boolean enabled)
  {
  }

  public boolean isAcceptableAdsEnabled()
  {
    return false;
  }

  /**
   * Sets FilterEngine enable state, at this moment this functionality
   * is not complete and will match added filters, if added with it disabled.
   * This Functionality automatically downloads and updates subscription if switched
   * from disable to enable state. Will not update subscriptions otherwise
   * For feature complete functionality use @see AdblockEngine#SetEnabled
   * @param enabled
   */
  public void setEnabled(final boolean enabled)
  {
  }

  public boolean isEnabled()
  {
    return false;
  }

  /**
   * Schedules updating of a subscription corresponding to the passed URL.
   *
   * @param subscriptionUrl may contain query parameters, only the beginning of the string is used
   *                        to find a corresponding subscription.
   */
  public void updateFiltersAsync(final String subscriptionUrl)
  {
  }

  /**
   * Get FilterEngine pointer
   * @return C++ FilterEngine instance pointer (AdblockPlus::FilterEngine*)
   */
  public long getNativePtr()
  {
    return 0L;
  }

  public void addSubscription(final Subscription subscription)
  {
  }

  public void removeSubscription(final Subscription subscription)
  {
  }

  public void addFilter(final Filter filter)
  {
  }

  public void removeFilter(final Filter filter)
  {
  }
}
