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

import java.util.HashSet;
import java.util.Set;

/**
 * Possible resource content types.
 */
public enum ContentType
{
  DOCUMENT,
  ELEMHIDE,
  FONT,
  GENERICBLOCK,
  GENERICHIDE,
  IMAGE,
  MEDIA,
  OBJECT,
  OBJECT_SUBREQUEST,
  OTHER,
  PING,
  SCRIPT,
  STYLESHEET,
  SUBDOCUMENT,
  WEBRTC,
  WEBSOCKET,
  XMLHTTPREQUEST;

  /**
   * Creates a set of {@link ContentType}s
   *
   * @param contentTypes
   * @return a Set of {@link ContentType}s
   */
  public static Set<ContentType> maskOf(final ContentType... contentTypes)
  {
    final Set<ContentType> set = new HashSet<>(contentTypes.length);
    for (final ContentType contentType : contentTypes)
    {
      set.add(contentType);
    }
    return set;
  }
}
