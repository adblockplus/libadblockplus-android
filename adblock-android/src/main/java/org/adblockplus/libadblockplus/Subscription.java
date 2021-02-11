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

import java.lang.ref.WeakReference;

public final class Subscription
{
  protected final WeakReference<FilterEngine> filterEngine;
  protected final String url;

  static
  {
    System.loadLibrary(BuildConfig.nativeLibraryName);
    registerNatives();
  }

  private final String title;
  private final String homepage;
  private final String author;
  private final String languages;

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
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return true;
    }
    return isDisabled(engine.getNativePtr(), this.url);
  }

  public void setDisabled(final boolean disabled)
  {
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return;
    }
    setDisabled(engine.getNativePtr(), disabled, this.url);
  }

  //  @Deprecated. Use FilterEngine.getListedSubscriptions() combined with find instead.
  public boolean isListed()
  {
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return false;
    }
    return engine.getListedSubscriptions().contains(this);
  }

  //  @Deprecated. Use FilterEngine.addSubscription() instead.
  public void addToList()
  {
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return;
    }
    engine.addSubscription(this);
  }

  //  @Deprecated. Use FilterEngine.removeSubscription() instead.
  public void removeFromList()
  {
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return;
    }
    engine.removeSubscription(this);
  }

  public void updateFilters()
  {
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return;
    }
    updateFilters(engine.getNativePtr(), this.url);
  }

  public boolean isUpdating()
  {
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return false;
    }
    return isUpdating(engine.getNativePtr(), this.url);
  }

  public boolean isAcceptableAds()
  {
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return false;
    }
    return isAcceptableAds(engine.getNativePtr(), this.url);
  }

  public String getTitle()
  {
    return this.title;
  }

  public String getUrl()
  {
    return this.url;
  }

  public String getHomepage()
  {
    return this.homepage;
  }

  public String getAuthor()
  {
    return this.author;
  }

  public String getLanguages()
  {
    return this.languages;
  }

  public String getSynchronizationStatus()
  {
    final FilterEngine engine = filterEngine.get();
    if (engine == null)
    {
      return "Filter Engine destroyed";
    }
    return getSynchronizationStatus(engine.getNativePtr(), this.url);
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

  private static native void registerNatives();

  private static native boolean isDisabled(long ptr, String url);

  private static native void setDisabled(long ptr, boolean disabled, String url);

  private static native void updateFilters(long ptr, String url);

  private static native boolean isUpdating(long ptr, String url);

  private static native boolean isAcceptableAds(long ptr, String url);

  private static native String getSynchronizationStatus(long ptr, String url);

}
