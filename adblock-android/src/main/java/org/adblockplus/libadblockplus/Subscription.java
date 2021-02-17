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
  public final String url;
  public final String title;
  public final String homepage;
  public final String author;
  public final String languages;
  protected final WeakReference<FilterEngine> filterEngine;

  static
  {
    System.loadLibrary(BuildConfig.nativeLibraryName);
    registerNatives();
  }

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
