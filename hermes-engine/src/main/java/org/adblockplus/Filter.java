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

/**
 * Data class representing filter
 */
public final class Filter
{
  /**
   * The raw filter text
   */
  @NotNull
  public final String text;

  /**
   * The filter type
   */
  @NotNull
  public final Type type;

  @Override
  public boolean equals(final Object other)
  {
    if (!(other instanceof Filter))
    {
      return false;
    }
    // Actually text alone determines filter type
    return this.text.equals(((Filter) other).text);
  }

  /**
   * Possible resource filter types
   */
  public enum Type
  {
    BLOCKING,
    EXCEPTION,
    ELEMHIDE,
    ELEMHIDE_EXCEPTION,
    ELEMHIDE_EMULATION,
    COMMENT,
    INVALID
  }

  /**
   * Filter objects are created by a native code, hence private constructor
   *
   * @param text the non-null raw filter text
   * @param type the non-null filter type
   */
  private Filter(@NotNull final String text, @NotNull final Type type)
  {
    this.text = text;
    this.type = type;
  }
}
