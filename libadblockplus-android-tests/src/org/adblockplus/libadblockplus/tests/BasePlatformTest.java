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

package org.adblockplus.libadblockplus.tests;

import org.adblockplus.libadblockplus.Platform;

import org.adblockplus.libadblockplus.LogSystem;
import org.adblockplus.libadblockplus.ThrowingWebRequest;
import org.adblockplus.libadblockplus.WebRequest;

import android.content.Context;
import android.test.InstrumentationTestCase;

public abstract class BasePlatformTest extends InstrumentationTestCase
{
  protected Platform platform;

  @Override
  protected void setUp() throws Exception
  {
    super.setUp();

    platform = new Platform(createLogSystem(), createWebRequest(),
        getContext().getFilesDir().getAbsolutePath());
  }

  @Override
  protected void tearDown() throws Exception
  {
    if (platform != null)
    {
      platform.dispose();
      platform = null;
    }
    super.tearDown();
  }

  // If the method returns null then a default implementation of the Log System
  // provided by libadblockplus is used.
  protected LogSystem createLogSystem()
  {
    return null;
  }

  protected WebRequest createWebRequest()
  {
    return new ThrowingWebRequest();
  }

  protected Context getContext()
  {
    return getInstrumentation().getTargetContext();
  }
}
