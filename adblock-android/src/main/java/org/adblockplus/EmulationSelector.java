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
 * Data class representing an element hiding emulation selector
 *
 * @see <a href="https://help.eyeo.com/adblockplus/how-to-write-filters#elemhide-emulation">
 *      Extended CSS selectors (Adblock Plus-specific)</a>
 */
public class EmulationSelector
{
  public final String selector;
  public final String text;

  public EmulationSelector(@NotNull final String selector, @NotNull final String text)
  {
    this.selector = selector;
    this.text = text;
  }
}
