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

public class MockHttpClient extends HttpClient
{
  public boolean exception;
  public List<HttpRequest> requests = new LinkedList<HttpRequest>();
  public ServerResponse response;
  public boolean called = false;

  public HttpRequest getLastRequest()
  {
    return (requests.size() > 0 ? requests.get(requests.size() - 1) : null);
  }

  @Override
  public void request(final HttpRequest request, final Callback callback)
  {
    this.called = true;
    this.requests.add(request);

    if (exception)
    {
      throw new RuntimeException("Exception simulation while downloading " + request.getUrl());
    }

    callback.onFinished(response);
  }
}
