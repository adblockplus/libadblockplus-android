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

package org.adblockplus.hermes;

/**
 * Opaque type to keep Javascript function references to be scheduled with setTimeout (or setImmediate) Javascript API.
 * This class should be built only by the C++ support code when a function reference is available. EVERY FIELD SHOULD BE
 * PRIVATE.
 */
class JSFunctionWrapper
{
  // Built only by C++ code
  private JSFunctionWrapper(final long nativePtr)
  {
    this.nativePtr = nativePtr;
  }

  // This should have only private fields
  private final long nativePtr;
}
