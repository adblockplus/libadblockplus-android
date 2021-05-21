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

import java.util.Collections;
import java.util.List;

public class JsValue implements Disposable
{
  private final Disposer disposer;
  protected final long ptr;

  protected JsValue(final long ptr)
  {
    this.ptr = ptr;
    this.disposer = new Disposer(this, new DisposeWrapper(ptr));
  }

  @Override
  public void dispose()
  {
    this.disposer.dispose();
  }

  public boolean isUndefined()
  {
    return false;
  }

  public boolean isNull()
  {
    return false;
  }

  public boolean isString()
  {
    return false;
  }

  public boolean isNumber()
  {
    return false;
  }

  public boolean isBoolean()
  {
    return false;
  }

  public boolean isObject()
  {
    return false;
  }

  public boolean isArray()
  {
    return false;
  }

  public boolean isFunction()
  {
    return false;
  }

  public String asString()
  {
    return null;
  }

  public long asLong()
  {
    return 0L;
  }

  public boolean asBoolean()
  {
    return false;
  }

  protected long[] convertToPtrArray(final List<JsValue> params)
  {
    final long[] paramPtrs = new long[params.size()];
    for (int i = 0; i < params.size(); i++)
    {
      paramPtrs[i] = params.get(i).ptr;
    }
    return paramPtrs;
  }

  public JsValue call(final List<JsValue> params)
  {
    return null;
  }

  public JsValue call(final List<JsValue> params, final JsValue thisValue)
  {
    return null;
  }

  public JsValue call()
  {
    return this.call(Collections.<JsValue>emptyList());
  }

  public JsValue getProperty(final String name)
  {
    return null;
  }

  public void setProperty(final String name, final JsValue value)
  {

  }

  // `getClass()` is Object's method and is reserved
  public String getJsClass()
  {
    return null;
  }

  public List<String> getOwnPropertyNames()
  {
    return null;
  }

  public List<JsValue> asList()
  {
    return null;
  }

  @Override
  public String toString()
  {
    return null;
  }

  private static final class DisposeWrapper implements Disposable
  {
    private final long ptr;

    public DisposeWrapper(final long ptr)
    {
      this.ptr = ptr;
    }

    @Override
    public void dispose()
    {
    }
  }

}
