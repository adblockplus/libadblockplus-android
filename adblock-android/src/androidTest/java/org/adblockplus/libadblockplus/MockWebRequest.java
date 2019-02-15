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

import java.util.LinkedList;
import java.util.List;

public class MockWebRequest extends WebRequest
{
  public boolean exception;
  public List<String> urls = new LinkedList<String>();
  public ServerResponse response;
  public boolean called = false;

  public String getLastUrl()
  {
    return (urls.size() > 0 ? urls.get(urls.size() - 1) : null);
  }

  @Override
  public void GET(final String url, final List<HeaderEntry> headers, final Callback callback)
  {
    this.called = true;
    this.urls.add(url);

    if (exception)
    {
      throw new RuntimeException("Exception simulation while downloading " + url);
    }

    callback.onFinished(response);
  }
}
