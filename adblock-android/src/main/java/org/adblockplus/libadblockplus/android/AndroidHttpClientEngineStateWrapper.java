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

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.ServerResponse;

import java.lang.ref.WeakReference;

import timber.log.Timber;

public class AndroidHttpClientEngineStateWrapper extends HttpClient
{
  private final HttpClient httpClient;
  private final WeakReference<AdblockEngine> engineRef;

  public AndroidHttpClientEngineStateWrapper(final HttpClient httpClient, final AdblockEngine engine)
  {
    this.httpClient = httpClient;
    this.engineRef = new WeakReference<>(engine);
  }

  @Override
  public void request(final HttpRequest request, final Callback callback)
  {
    final AdblockEngine engine = engineRef.get();
    final ServerResponse response = new ServerResponse();

    if (engine != null && !engine.isEnabled())
    {
      Timber.d("Connection refused: engine is disabled");

      response.setResponseStatus(0);
      response.setStatus(ServerResponse.NsStatus.ERROR_CONNECTION_REFUSED);

      callback.onFinished(response);
      return;
    }

    try
    {
      httpClient.request(request, callback);
    }
    catch (final AdblockPlusException e)
    {
      Timber.e(e, "WebRequest failed");

      response.setResponseStatus(500);
      response.setStatus(ServerResponse.NsStatus.ERROR_FAILURE);

      callback.onFinished(response);
    }

  }
}
