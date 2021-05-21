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

import java.util.List;

public final class JsEngine
{
  protected final long ptr;

  JsEngine(final long ptr)
  {
    this.ptr = ptr;
  }

  public void setEventCallback(final String eventName, final EventCallback callback)
  {
  }

  public void removeEventCallback(final String eventName)
  {
  }

  public JsValue evaluate(final String source, final String filename)
  {
    return null;
  }

  public JsValue evaluate(final String source)
  {
    return null;
  }

  public void setGlobalProperty(final String property, final JsValue value)
  {
  }

  public void triggerEvent(final String eventName, final List<JsValue> params)
  {
  }

  /**
   * Notifies the engine about the need of reducing the amount of allocated memory
   */
  public void onLowMemory()
  {

  }


}
