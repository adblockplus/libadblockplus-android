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

package org.adblockplus.libadblockplus.test;

import org.adblockplus.AppInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AllPropertiesAppInfoJsObjectTest extends BaseJsEngineTest
{
  private final String VERSION = "1.0";
  private final String NAME = "libadblockplus-android";
  private final String APPLICATION = "4";
  private final String APPLICATION_VERSION = "5";
  private final String LOCALE = "2";

  @Override
  public void setUp()
  {
    final AppInfo appInfo = AppInfo.builder()
        .setApplication(APPLICATION)
        .setApplicationVersion(APPLICATION_VERSION)
        .setLocale(LOCALE)
        .build();
    setUpAppInfo(appInfo);
    super.setUp();
  }

  @Test
  public void testAllProperties()
  {
    assertEquals(VERSION, jsEngine.evaluate("_appInfo.version").asString());
    assertEquals(NAME, jsEngine.evaluate("_appInfo.name").asString());
    assertEquals(APPLICATION, jsEngine.evaluate("_appInfo.application").asString());
    assertEquals(APPLICATION_VERSION, jsEngine.evaluate("_appInfo.applicationVersion").asString());
    assertEquals(LOCALE, jsEngine.evaluate("_appInfo.locale").asString());
  }
}
