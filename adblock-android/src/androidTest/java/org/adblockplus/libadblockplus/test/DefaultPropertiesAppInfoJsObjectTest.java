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

import org.adblockplus.libadblockplus.JsValue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultPropertiesAppInfoJsObjectTest extends BaseJsEngineTest
{
  @Test
  public void testDefaultProperties()
  {
    final JsValue version = jsEngine.evaluate("_appInfo.version");
    assertEquals("1.0", version.asString());
    version.dispose();
    final JsValue name = jsEngine.evaluate("_appInfo.name");
    assertEquals("libadblockplus-android", name.asString());
    name.dispose();
    final JsValue application = jsEngine.evaluate("_appInfo.application");
    assertEquals("android", application.asString());
    application.dispose();
    final JsValue appVersion = jsEngine.evaluate("_appInfo.applicationVersion");
    assertEquals("0", appVersion.asString());
    application.dispose();
    final JsValue locale = jsEngine.evaluate("_appInfo.locale");
    assertEquals("en_US", locale.asString());
    locale.dispose();
  }
}
