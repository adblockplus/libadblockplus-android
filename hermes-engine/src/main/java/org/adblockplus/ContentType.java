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

import java.util.HashSet;
import java.util.Set;

/**
 * Possible resource content types.
 */
public enum ContentType
{
  DOCUMENT(1 << 26),
  ELEMHIDE(1 << 28),
  FONT(32768),
  GENERICBLOCK(1 << 27),
  GENERICHIDE(1 << 29),
  IMAGE(4),
  MEDIA(16384),
  OBJECT(16),
  OBJECT_SUBREQUEST(0), //@TODO this does not exist
  OTHER(1),
  PING(1024),
  SCRIPT(2),
  STYLESHEET(8),
  SUBDOCUMENT(32),
  WEBRTC(256),
  WEBSOCKET(128),
  XMLHTTPREQUEST(2048);

  private final int val;

  ContentType(final int val)
  {
    this.val = val;
  }

  public int getValue()
  {
    return val;
  }

  public static int getCombinedValue(final Set<ContentType> contentTypes)
  {
    int contentTypeMask = 0;
    for (final ContentType contentType : contentTypes)
    {
      contentTypeMask |= contentType.getValue();
    }
    return contentTypeMask;
  }

  /**
   * Creates a set of {@link ContentType}s
   *
   * @param contentTypes
   * @return a Set of {@link ContentType}s
   */
  @NotNull
  public static Set<ContentType> maskOf(@NotNull final ContentType... contentTypes)
  {
    final Set<ContentType> set = new HashSet<>(contentTypes.length);
    for (final ContentType contentType : contentTypes)
    {
      set.add(contentType);
    }
    return set;
  }
}
