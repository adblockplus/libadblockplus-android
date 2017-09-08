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

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.LazyWebRequest;
import org.adblockplus.libadblockplus.WebRequest;

public abstract class BaseFilterEngineTest extends BaseJsEngineTest
{
  protected FilterEngine filterEngine;

  @Override
  protected void setUp() throws Exception
  {
    super.setUp();
    filterEngine = new FilterEngine(jsEngine);
  }

  @Override
  protected void tearDown() throws Exception
  {
    disposeFilterEngine();
    super.tearDown();
  }

  protected void disposeFilterEngine() throws InterruptedException
  {
    if (filterEngine != null)
    {
      Thread.sleep(200); // let FS finish its operations
      filterEngine.dispose();
      filterEngine = null;
    }
  }

  @Override
  protected WebRequest createWebRequest()
  {
    return new LazyWebRequest();
  }
}
