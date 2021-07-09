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

import java.lang.ref.WeakReference;

/**
 * Class representing a filter list subscription
 */
public final class Subscription
{

  /**
   * The url to the filter list
   */
  public final String url;

  /**
   * The filter list title (i.e. easylist)
   */
  public final String title;

  /**
   * The home page of the filter list
   */
  public final String homepage;

  /**
   * The author of the filter list
   */
  public final String author;

  /**
   * The languages of the filter list
   */
  public final String languages;

  protected final WeakReference<AdblockEngine> adblockEngine;

  // Constructor used by a native code
  private Subscription(final String url,
                       final String title,
                       final String homepage,
                       final String author,
                       final String languages,
                       final AdblockEngine adblockEngine)
  {
    this.url = url;
    this.title = title;
    this.homepage = homepage;
    this.author = author;
    this.languages = languages;
    this.adblockEngine = new WeakReference<>(adblockEngine);
  }

  @Override
  public boolean equals(final Object other)
  {
    if (!(other instanceof Subscription))
    {
      return false;
    }
    return this.url.equals(((Subscription) other).url);
  }

  // Methods below requires AdblockEngine handle, to be implemented later
  public void updateFilters()
  {
  }

  public boolean isUpdating()
  {
    return false;
  }

  public boolean isAcceptableAds()
  {
    return false;
  }

  public boolean isDisabled()
  {
    return false;
  }

  public void setDisabled(final boolean disabled)
  {
  }

  public String getSynchronizationStatus()
  {
    return "";
  }
}
