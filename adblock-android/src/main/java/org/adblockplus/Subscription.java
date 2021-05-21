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

import org.adblockplus.libadblockplus.BuildConfig;
import org.adblockplus.libadblockplus.FilterEngine;

import java.lang.ref.WeakReference;

public final class Subscription
{
  public final String url;
  public final String title;
  public final String homepage;
  public final String author;
  public final String languages;
  protected final WeakReference<FilterEngine> filterEngine;

  private Subscription(final String url,
                       final String title,
                       final String homepage,
                       final String author,
                       final String languages,
                       final FilterEngine filterEngine)
  {
    this.url = url;
    this.title = title;
    this.homepage = homepage;
    this.author = author;
    this.languages = languages;
    this.filterEngine = new WeakReference<>(filterEngine);
  }

  public boolean isDisabled()
  {
    return false;
  }

  public void setDisabled(final boolean disabled)
  {
  }

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

  public String getSynchronizationStatus()
  {
    return null;
  }

  @Override
  public int hashCode()
  {
    return this.url.hashCode();
  }

  @Override
  public boolean equals(final Object o)
  {
    if (!(o instanceof Subscription))
    {
      return false;
    }
    return this.url.equals(((Subscription) o).url);
  }


}
