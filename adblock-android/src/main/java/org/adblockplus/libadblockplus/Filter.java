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

public final class Filter
{
  private final Type type;
  private final String raw;

  // this is for deprecated methods
  private WeakReference<FilterEngine> filterEngine;

  // Called by Native
  private Filter(final Type type, final String raw)
  {
    this.type = type;
    this.raw = raw;
  }

  /**
   * Retrieves the type of this filter.
   *
   * @return Type of this filter.
   */
  public Type getType()
  {
    return type;
  }

  public String getRaw()
  {
    return raw;
  }

  /**
   * Checks whether this filter has been added to the list of custom filters.
   *
   * @return `true` if this filter has been added.
   * @deprecated Use {@link FilterEngine#getListedFilters()} combined with find instead.
   */
  @Deprecated
  public boolean isListed()
  {
    final FilterEngine engine = this.filterEngine.get();
    return engine != null && engine.getListedFilters().contains(this);
  }

  /**
   * Adds this filter to the list of custom filters.
   *
   * @deprecated Use {@link FilterEngine#addFilter(Filter)} instead.
   */
  @Deprecated
  public void addToList()
  {
    final FilterEngine engine = this.filterEngine.get();
    if (engine != null)
    {
      engine.addFilter(this);
    }
  }

  /**
   * Removes this filter from the list of custom filters.
   *
   * @deprecated Use {@link FilterEngine#removeFilter(Filter)} instead.
   */
  @Deprecated
  public void removeFromList()
  {
    final FilterEngine engine = this.filterEngine.get();
    if (engine != null)
    {
      engine.removeFilter(this);
    }
  }

  @Override
  public boolean equals(final Object o)
  {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Filter filter = (Filter) o;
    return type == filter.type &&
        raw.equals(filter.raw);
  }

  void setFilterEngine(final FilterEngine filterEngine)
  {
    this.filterEngine = new WeakReference<>(filterEngine);
  }

  public enum Type
  {
    BLOCKING, EXCEPTION, ELEMHIDE, ELEMHIDE_EXCEPTION, ELEMHIDE_EMULATION,
    COMMENT, INVALID
  }

}
