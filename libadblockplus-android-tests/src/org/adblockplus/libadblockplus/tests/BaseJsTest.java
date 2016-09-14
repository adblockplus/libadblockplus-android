/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2016 Eyeo GmbH
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

import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.JsEngine;
import org.adblockplus.libadblockplus.LazyLogSystem;
import org.adblockplus.libadblockplus.ThrowingWebRequest;

import android.test.AndroidTestCase;

public abstract class BaseJsTest extends AndroidTestCase
{
  protected JsEngine jsEngine;

  @Override
  protected void setUp() throws Exception
  {
    super.setUp();

    jsEngine = new JsEngine(AppInfo.builder().build());
    jsEngine.setDefaultLogSystem();
    jsEngine.setDefaultFileSystem(getContext().getFilesDir().getAbsolutePath());
    jsEngine.setWebRequest(new ThrowingWebRequest());
  }
}
