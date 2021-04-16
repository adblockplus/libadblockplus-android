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

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.adblockplus.ConnectionType;
import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;

import timber.log.Timber;

public class IsAllowedConnectionCallbackImpl implements IsAllowedConnectionCallback
{
  private ConnectivityManager manager;

  public IsAllowedConnectionCallbackImpl(final ConnectivityManager manager)
  {
    super();
    this.manager = manager;
  }

  @Override
  public boolean isConnectionAllowed(final String connection)
  {
    Timber.d("Checking connection: %s", connection);

    if (connection == null)
    {
      // required connection type is not specified - any works
      return true;
    }

    final NetworkInfo info = manager.getActiveNetworkInfo();
    if (info == null || !info.isConnected())
    {
      // not connected
      return false;
    }

    final ConnectionType connectionType = ConnectionType.findByValue(connection);
    if (connectionType == null)
    {
      Timber.e("Unknown connection type: %s", connection);
      return false;
    }

    if (!isRequiredConnection(connectionType))
    {
      Timber.w("Current connection type `%s` is not allowed for web requests", connectionType.getValue());
      return false;
    }

    return true;
  }

  private boolean isRequiredConnection(final ConnectionType connectionType)
  {
    if (connectionType.equals(ConnectionType.ANY))
    {
      return true;
    }
    if (connectionType.equals(ConnectionType.NONE))
    {
      return false;
    }
    // Here we just know that value can be WIFI or WIFI_NON_METERED
    final boolean isCurrentlyWifi = manager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI;
    if (!isCurrentlyWifi)
    {
      return false;
    }
    else if (connectionType.equals(ConnectionType.WIFI))
    {
      return true;
    }
    else //WIFI_NON_METERED
    {
      return !manager.isActiveNetworkMetered();
    }
  }
}
