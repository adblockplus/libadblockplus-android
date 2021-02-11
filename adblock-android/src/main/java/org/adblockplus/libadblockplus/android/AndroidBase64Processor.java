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

package org.adblockplus.libadblockplus.android;

import android.util.Base64;

import org.adblockplus.libadblockplus.util.Base64Exception;
import org.adblockplus.libadblockplus.util.Base64Processor;

/**
 * Android implementation of Base64Processor
 */
public class AndroidBase64Processor implements Base64Processor
{
  // Usually it's more convenient to work with single-line strings,
  // also it's the default behaviour on JDK.
  private static final int DEFAULT_FLAGS = Base64.DEFAULT | Base64.NO_WRAP;

  @Override
  public byte[] decode(final byte[] encodedBytes) throws Base64Exception
  {
    try
    {
      return Base64.decode(encodedBytes, Base64.DEFAULT);
    }
    catch (final Throwable cause)
    {
      throw new Base64Exception(cause);
    }
  }

  @Override
  public byte[] encode(final byte[] rawBytes) throws Base64Exception
  {
    try
    {
      return Base64.encode(rawBytes, DEFAULT_FLAGS);
    }
    catch (final Throwable cause)
    {
      throw new Base64Exception(cause);
    }
  }

  @Override
  public String encodeToString(final byte[] rawBytes) throws Base64Exception
  {
    try
    {
      return Base64.encodeToString(rawBytes, DEFAULT_FLAGS);
    }
    catch (final Throwable cause)
    {
      throw new Base64Exception(cause);
    }
  }
}
