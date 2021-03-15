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

import java.util.List;
import java.util.Set;

/**
 * The main interface to the filtering engine. It exposes all the functionalities to filter urls,
 * get specific stylesheet and {@link EmulationSelector}s for a page.
 */
public interface AdblockEngine
{
  /**
   * Obtains element hiding CSS stylesheet for a domain.
   *
   * @param domain the domain for which you want the stylesheet, if empty or incorrect then empty
   *               String is returned
   * @param specificOnly if true returns only the stylesheet for the specif domain, otherwise
   *                     returns the specific <b>and</b> the generic ones
   * @return CSS stylesheet string
   */
  @NotNull
  String getElementHidingStyleSheet(@NotNull String domain,
                                    boolean specificOnly);

  /**
   * Obtains element hiding emulation selectors for a domain.
   *
   * @param domain the domain for which you want the selectors, if empty or incorrect then empty
   *               List is returned
   * @return a List of {@link EmulationSelector}s
   * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#elemhide-emulation">
   *      Extended CSS selectors (Adblock Plus-specific)</a>
   */
  @NotNull
  List<EmulationSelector> getElementHidingEmulationSelectors(@NotNull String domain);

  /**
   * Checks whether the resource at the supplied URL is allowlisted.
   *
   * @param url URL of the resource
   * @param contentTypes Set of content types for requested resource
   * @param referrerChain Chain of URLs requesting the resource
   * @param siteKey Public key provided by the document, can be empty
   * @return `true` if the URL is allowlisted
   */
  boolean isContentAllowlisted(@NotNull String url,
                               @NotNull Set<ContentType> contentTypes,
                               @NotNull List<String> referrerChain,
                               @NotNull String siteKey);

  /**
   * Checks whether the resource at the supplied URL has a blocking filter.
   * For checking allowlisting filters use {@link AdblockEngine#isContentAllowlisted}.
   *
   * @param url URL of the resource
   * @param contentTypes Set of content types for requested resource
   * @param parent Immediate parent of the {@param url}.
   * @param siteKey Public key provided by the document, can be empty
   * @param domainSpecificOnly If `true` then we check only domain specific filters
   * @return {@link MatchesResult#NOT_ENABLED} if FilterEngine is not enabled,
   *         {@link MatchesResult#BLOCKED} when blocking filter was found or
   *         {@link MatchesResult#NOT_FOUND} when blocking filter was not found.
   */
  @NotNull
  MatchesResult matches(@NotNull String url, @NotNull Set<ContentType> contentTypes,
                        @NotNull String parent, @NotNull String siteKey,
                        @NotNull boolean domainSpecificOnly);

  /**
   * Creates a {@link Subscription} object from url.
   *
   * @param url URL of the subscription
   * @return {@link Subscription} object
   */
  @NotNull
  Subscription getSubscription(@NotNull String url);

  /**
   * Creates a {@link Filter} object from String.
   *
   * @param text String representing raw filter
   * @return {@link {@link Filter} object
   */
  @NotNull
  Filter getFilterFromText(@NotNull String text);
}
