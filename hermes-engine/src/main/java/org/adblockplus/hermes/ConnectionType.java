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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Possible connection types.
 */
public enum ConnectionType
{
  /**
   * Any connection is allowed
   */
  ANY("any"),

  /**
   * No connection is allowed
   */
  NONE("none"),

  /**
   * All WiFi networks are allowed
   */
  WIFI("wifi"),

  /**
   * Non-metered WiFi networks are allowed
   */
  WIFI_NON_METERED("wifi_non_metered");

  @NotNull final private String value;

  /**
   * Obtains String value
   */
  @NotNull public String getValue()
  {
    return value;
  }

  ConnectionType(@NotNull final String value)
  {
    this.value = value;
  }

  /**
   * Finds enum value from String value.
   * @param value String value
   * @return Matching enum value or null if not found.
   */
  @Nullable public static ConnectionType findByValue(@NotNull final String value)
  {
    for (final ConnectionType eachConnectionType : values())
    {
      if (eachConnectionType.getValue().equals(value))
      {
        return eachConnectionType;
      }
    }

    // not found
    return null;
  }
}
