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

  static
  {
    System.loadLibrary(BuildConfig.nativeLibraryName);
    registerNatives();
  }

  FilterEngine(final long jniPlatformPtr)
  {
    this.ptr = jniPlatformPtr;
  }

  private static native void addFilter(long ptr, String raw);

  private static native void removeFilter(long ptr, String raw);

  public Filter getFilterFromText(final String text)
  {
    return getFilter(this.ptr, text);
  }

  public List<Filter> getListedFilters()
  {
    return getListedFilters(this.ptr);
  }

  public Subscription getSubscription(final String url)
  {
    return getSubscription(this.ptr, url, this);
  }

  public List<Subscription> getListedSubscriptions()
  {
    return getListedSubscriptions(this.ptr, this);
  }

  public List<Subscription> fetchAvailableSubscriptions()
  {
    return fetchAvailableSubscriptions(this.ptr, this);
  }

  public String getElementHidingStyleSheet(final String domain)
  {
    return getElementHidingStyleSheet(domain, false);
  }

  public String getElementHidingStyleSheet(final String domain, final boolean specificOnly)
  {
    return getElementHidingStyleSheet(this.ptr, domain, specificOnly);
  }

  public List<EmulationSelector> getElementHidingEmulationSelectors(final String domain)
  {
    return getElementHidingEmulationSelectors(this.ptr, domain);
  }

  /**
   * Checks if any active filter matches the supplied URL.
   * @param url url URL to match.
   * @param contentTypes Content type mask of the requested resource.
   * @param documentUrls Chain of documents requesting the resource, starting
   *                     with the current resource's parent frame, ending with the
   *                     top-level frame.
   * @param siteKey sitekey or null/empty string
   * @return Matching filter, or a `null` if there was no match.
   *
   * @deprecated Use {@link FilterEngine#matches) which takes a single parent as a String instead of
   * {@param documentUrls} and {@param specificOnly} argument.
   */
  @Deprecated
  public Filter matches(final String url, final Set<ContentType> contentTypes,
                        final List<String> documentUrls, final String siteKey)
  {
    return matches(this.ptr, url, contentTypes.toArray(new ContentType[contentTypes.size()]),
        documentUrls, siteKey, false);
  }

  /**
   * Checks if any active filter matches the supplied URL.
   * @param url url URL to match.
   * @param contentTypes Content type mask of the requested resource.
   * @param documentUrls Chain of documents requesting the resource, starting
   *                     with the current resource's parent frame, ending with the
   *                     top-level frame.
   * @param siteKey sitekey or null/empty string
   * @param specificOnly if set to `true` then skips generic filters
   * @return Matching filter, or a `null` if there was no match.
   *
   * @deprecated Use {@link FilterEngine#matches) which takes a single parent as a String instead of
   * {@param documentUrls}.
   */
  @Deprecated
  public Filter matches(final String url, final Set<ContentType> contentTypes,
                        final List<String> documentUrls, final String siteKey,
                        final boolean specificOnly)
  {
    return matches(this.ptr, url, contentTypes.toArray(new ContentType[contentTypes.size()]),
        documentUrls, siteKey,  specificOnly);
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
    return matches(this.ptr, url, contentTypes.toArray(new ContentType[contentTypes.size()]),
        parent, siteKey, specificOnly);
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
    return isContentAllowlisted(this.ptr, url,
        contentTypes.toArray(new ContentType[contentTypes.size()]), documentUrls, siteKey);
  }

  /**
   * Checks if any active filter matches the supplied URL.
   * @param url URL to match which is actually first parent of URL for which we
   *            want to check a $genericblock filter.
   *            Value obtained by `IsGenericblockAllowlisted()` is used later
   *            on as a `specificOnly` parameter value for `Matches()` call.
   * @param documentUrls Chain of documents requesting the resource, starting
   *                     with the current resource's parent frame, ending with the
   *                     top-level frame.
   * @param siteKey sitekey or null/empty string
   * @return `true` if the URL is allowlisted by $genericblock filter
   *
   * @deprecated Use {@link FilterEngine#isContentAllowlisted) with contentType containing
   *             {@link ContentType#GENERICBLOCK} instead.
   */
  @Deprecated
  public boolean isGenericblockAllowlisted(final String url, final List<String> documentUrls,
                                           final String siteKey)
  {
    return isGenericblockAllowlisted(this.ptr, url, documentUrls, siteKey);
  }

  /**
   * Check if the document with URL is allowlisted
   * @param url URL
   * @param documentUrls Chain of document URLs requesting the document,
   *                     starting with the current document's parent frame, ending with
   *                     the top-level frame.
   * @param siteKey sitekey or null/empty string
   * @return `true` if the URL is allowlisted
   *
   * @deprecated Use {@link FilterEngine#isContentAllowlisted) with contentType containing
   *             {@link ContentType#DOCUMENT} instead.
   */
  @Deprecated
  public boolean isDocumentAllowlisted(final String url,
                                       final List<String> documentUrls,
                                       final String siteKey)
  {
    return isDocumentAllowlisted(this.ptr, url, documentUrls, siteKey);
  }

  /**
   * Check if the element hiding is allowlisted
   * @param url URL
   * @param documentUrls Chain of document URLs requesting the document,
   *                     starting with the current document's parent frame, ending with
   *                     the top-level frame.
   * @param siteKey sitekey or null/empty string
   * @return `true` if element hiding is allowlisted for the supplied URL.
   *
   * @deprecated Use {@link FilterEngine#isContentAllowlisted) with contentType containing
   *             {@link ContentType#ELEMHIDE} instead.
   */
  @Deprecated
  public boolean isElemhideAllowlisted(final String url,
                                       final List<String> documentUrls,
                                       final String siteKey)
  {
    return isElemhideAllowlisted(this.ptr, url, documentUrls, siteKey);
  }

  public JsValue getPref(final String pref)
  {
    return getPref(this.ptr, pref);
  }

  /**
   * Set libadblockplus preference. Only known preferences will be stored after engine is disposed.
   * @param pref preference name. See lib/pref.js in libadblockplus for valid names
   * @param value preference value
   */
  public void setPref(final String pref, final JsValue value)
  {
    setPref(this.ptr, pref, value.ptr);
  }

  public String getHostFromURL(final String url)
  {
    return getHostFromURL(this.ptr, url);
  }

  public void setAllowedConnectionType(final String value)
  {
    setAllowedConnectionType(this.ptr, value);
  }

  public String getAllowedConnectionType()
  {
    return getAllowedConnectionType(this.ptr);
  }

  public void setAcceptableAdsEnabled(final boolean enabled)
  {
    setAcceptableAdsEnabled(this.ptr, enabled);
  }

  public boolean isAcceptableAdsEnabled()
  {
    return isAcceptableAdsEnabled(this.ptr);
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
    setEnabled(this.ptr, enabled);
  }

  public boolean isEnabled()
  {
    return isEnabled(this.ptr);
  }

  public String getAcceptableAdsSubscriptionURL()
  {
    return getAcceptableAdsSubscriptionURL(this.ptr);
  }

  /**
   * Schedules updating of a subscription corresponding to the passed URL.
   *
   * @param subscriptionUrl may contain query parameters, only the beginning of the string is used
   *                        to find a corresponding subscription.
   */
  public void updateFiltersAsync(final String subscriptionUrl)
  {
    updateFiltersAsync(this.ptr, subscriptionUrl);
  }

  /**
   * Get FilterEngine pointer
   * @return C++ FilterEngine instance pointer (AdblockPlus::FilterEngine*)
   */
  public long getNativePtr()
  {
    return getNativePtr(this.ptr);
  }

  public void addSubscription(final Subscription subscription)
  {
    addSubscription(this.ptr, subscription.url);
  }

  public void removeSubscription(final Subscription subscription)
  {
    removeSubscription(this.ptr, subscription.url);
  }

  public void addFilter(final Filter filter)
  {
    addFilter(this.ptr, filter.text);
  }

  public void removeFilter(final Filter filter)
  {
    removeFilter(this.ptr, filter.text);
  }


  private static native void registerNatives();

  private static native Filter getFilter(long ptr, String text);

  private static native List<Filter> getListedFilters(long ptr);

  private static native Subscription getSubscription(long ptr, String url, FilterEngine engine);

  private static native List<Subscription> getListedSubscriptions(long ptr, FilterEngine engine);

  private static native List<Subscription> fetchAvailableSubscriptions(long ptr, FilterEngine engine);

  private static native String getElementHidingStyleSheet(long ptr, String domain, boolean specificOnly);

  private static native List<EmulationSelector> getElementHidingEmulationSelectors(long ptr, String domain);

  private static native JsValue getPref(long ptr, String pref);

  private static native Filter matches(long ptr, String url, ContentType[] contentType,
                                       List<String> referrerChain, String siteKey,
                                       boolean specificOnly);

  private static native Filter matches(long ptr, String url, ContentType[] contentType,
                                       String parent, String siteKey,
                                       boolean specificOnly);

  private static native boolean isContentAllowlisted(long ptr, String url, ContentType[] contentType,
                                                     List<String> referrerChain, String siteKey);

  private static native boolean isGenericblockAllowlisted(long ptr, String url,
                                                                List<String> referrerChain,
                                                                String siteKey);

  private static native boolean isDocumentAllowlisted(long ptr, String url,
                                                      List<String> referrerChain,
                                                      String siteKey);

  private static native boolean isElemhideAllowlisted(long ptr, String url,
                                                      List<String> referrerChain,
                                                      String siteKey);

  private static native void setPref(long ptr, String pref, long valuePtr);

  private static native String getHostFromURL(long ptr, String url);

  private static native void setAllowedConnectionType(long ptr, String value);

  private static native String getAllowedConnectionType(long ptr);

  private static native void setAcceptableAdsEnabled(long ptr, boolean enabled);

  private static native boolean isAcceptableAdsEnabled(long ptr);

  private static native void setEnabled(long ptr, boolean enabled);

  private static native boolean isEnabled(long ptr);

  private static native String getAcceptableAdsSubscriptionURL(long ptr);

  private static native void updateFiltersAsync(long ptr, String subscriptionUrl);

  private static native long getNativePtr(long ptr);

  private static native void addSubscription(long ptr, String subscriptionUrl);

  private static native void removeSubscription(long ptr, String subscriptionUrl);
}
