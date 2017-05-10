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
package org.adblockplus.libadblockplus.tests;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.adblockplus.libadblockplus.android.settings.ConnectionType;
import org.adblockplus.libadblockplus.android.settings.IsAllowedConnectionCallbackImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IsAllowedConnectionCallbackImplTest extends BaseJsTest
{
  private final int[] connectionTypes = new int[]
  {
    ConnectivityManager.TYPE_WIFI,
    ConnectivityManager.TYPE_MOBILE,
    ConnectivityManager.TYPE_ETHERNET,
  };

  @Override
  protected void setUp() throws Exception
  {
    super.setUp();

    System.setProperty(
      "dexmaker.dexcache",
      getInstrumentation().getTargetContext().getCacheDir().getPath());
  }

  public static ConnectivityManager buildNoActiveConnectionCM()
  {
    final ConnectivityManager connectivityManager = Mockito.mock(ConnectivityManager.class);

    Mockito
      .when(connectivityManager.getActiveNetworkInfo())
      .thenReturn(null);

    return connectivityManager;
  }

  public static ConnectivityManager buildConnectivityManager(boolean connected, int type, boolean metered)
  {
    final ConnectivityManager connectivityManager = Mockito.mock(ConnectivityManager.class);
    final NetworkInfo networkInfo = Mockito.mock(NetworkInfo.class);

    Mockito
      .when(connectivityManager.getActiveNetworkInfo())
      .thenReturn(networkInfo);

    Mockito
      .when(connectivityManager.isActiveNetworkMetered())
      .thenReturn(metered);

    Mockito
      .when(networkInfo.isConnected())
      .thenReturn(connected);

    Mockito
      .when(networkInfo.getType())
      .then(new Returns(type));

    return connectivityManager;
  }

  @Test
  public void testNullAllowedConnection()
  {
    final IsAllowedConnectionCallbackImpl callback = new IsAllowedConnectionCallbackImpl(null);
    assertTrue(callback.isConnectionAllowed(null));
  }

  @Test
  public void testNoActiveConnection()
  {
    ConnectivityManager connectivityManager = buildNoActiveConnectionCM();
    final IsAllowedConnectionCallbackImpl callback =
      new IsAllowedConnectionCallbackImpl(connectivityManager);

    for (ConnectionType eachConnectionType : ConnectionType.values())
    {
      assertFalse(callback.isConnectionAllowed(eachConnectionType.getValue()));
    }
  }

  @Test
  public void testFalseForAnyTypeIfNotConnected()
  {
    for (int currentConnectionType : connectionTypes)
    {
      ConnectivityManager cm = buildConnectivityManager(false, currentConnectionType, false);
      final IsAllowedConnectionCallbackImpl callback = new IsAllowedConnectionCallbackImpl(cm);

      for (ConnectionType eachConnectionType : ConnectionType.values())
      {
        assertFalse(callback.isConnectionAllowed(eachConnectionType.getValue()));
      }
    }
  }

  @Test
  public void testFalseForUnknownConnectionType()
  {
    final String allowedConnectionType = "unknown";

    for (int currentConnectionType : connectionTypes)
    {
      ConnectivityManager cm = buildConnectivityManager(true, currentConnectionType, false);
      final IsAllowedConnectionCallbackImpl callback = new IsAllowedConnectionCallbackImpl(cm);

      assertFalse(callback.isConnectionAllowed(allowedConnectionType));
    }
  }

  @Test
  public void testWifiNonMetered()
  {
    // current connection is wifi non metered
    final IsAllowedConnectionCallbackImpl callback =
      new IsAllowedConnectionCallbackImpl(
        buildConnectivityManager(true, ConnectivityManager.TYPE_WIFI, false));

    assertTrue(callback.isConnectionAllowed(ConnectionType.ANY.getValue()));
    assertTrue(callback.isConnectionAllowed(ConnectionType.WIFI.getValue()));
    assertTrue(callback.isConnectionAllowed(ConnectionType.WIFI_NON_METERED.getValue()));
  }

  @Test
  public void testWifiMetered()
  {
    // current connection is wifi metered
    final IsAllowedConnectionCallbackImpl callback =
      new IsAllowedConnectionCallbackImpl(
        buildConnectivityManager(true, ConnectivityManager.TYPE_WIFI, true));

    assertTrue(callback.isConnectionAllowed(ConnectionType.ANY.getValue()));
    assertTrue(callback.isConnectionAllowed(ConnectionType.WIFI.getValue()));
    assertFalse(callback.isConnectionAllowed(ConnectionType.WIFI_NON_METERED.getValue()));
  }

  @Test
  public void testMobile()
  {
    // current connection is mobile
    final IsAllowedConnectionCallbackImpl callback =
      new IsAllowedConnectionCallbackImpl(
        buildConnectivityManager(true, ConnectivityManager.TYPE_MOBILE, false));

    assertTrue(callback.isConnectionAllowed(ConnectionType.ANY.getValue()));
    assertFalse(callback.isConnectionAllowed(ConnectionType.WIFI.getValue()));
    assertFalse(callback.isConnectionAllowed(ConnectionType.WIFI_NON_METERED.getValue()));
  }

  @Test
  public void testEthernet()
  {
    // current connection is ethernet
    final IsAllowedConnectionCallbackImpl callback =
      new IsAllowedConnectionCallbackImpl(
        buildConnectivityManager(true, ConnectivityManager.TYPE_ETHERNET, false));

    assertTrue(callback.isConnectionAllowed(ConnectionType.ANY.getValue()));
    assertFalse(callback.isConnectionAllowed(ConnectionType.WIFI.getValue()));
    assertFalse(callback.isConnectionAllowed(ConnectionType.WIFI_NON_METERED.getValue()));
  }
}
