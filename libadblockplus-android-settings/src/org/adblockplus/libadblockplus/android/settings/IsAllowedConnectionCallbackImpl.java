/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2017 eyeo GmbH
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

package org.adblockplus.libadblockplus.android.settings;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;
import org.adblockplus.libadblockplus.android.Utils;

public class IsAllowedConnectionCallbackImpl extends IsAllowedConnectionCallback
{
  private static final String TAG = Utils.getTag(IsAllowedConnectionCallbackImpl.class);

  private ConnectivityManager manager;

  public IsAllowedConnectionCallbackImpl(ConnectivityManager manager)
  {
    super();
    this.manager = manager;
  }

  @Override
  public boolean isConnectionAllowed(String connection)
  {
    Log.d(TAG, "Checking connection: " + connection);

    if (connection == null)
    {
      // required connection type is not specified - any works
      return true;
    }

    NetworkInfo info = manager.getActiveNetworkInfo();
    if (info == null || !info.isConnected())
    {
      // not connected
      return false;
    }

    ConnectionType connectionType = ConnectionType.findByValue(connection);
    if (connectionType == null)
    {
      Log.e(TAG, "Unknown connection type: " + connection);
      return false;
    }

    if (!connectionType.isRequiredConnection(manager))
    {
      Log.w(TAG, "Current connection type is not allowed for web requests");
      return false;
    }

    return true;
  }
}
