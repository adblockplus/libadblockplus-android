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

import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

public class UtilsTest
{
  @Test
  public void testAbsoluteUrl() throws URISyntaxException
  {
    assertFalse(Utils.isAbsoluteUrl("/index.html"));
    assertFalse(Utils.isAbsoluteUrl("../index.html"));
    assertFalse(Utils.isAbsoluteUrl("../../index.html"));

    assertTrue(Utils.isAbsoluteUrl("http://domain.com"));
    assertTrue(Utils.isAbsoluteUrl("https://domain.com"));
    assertTrue(Utils.isAbsoluteUrl("https://www.domain.com"));

    assertTrue(Utils.isAbsoluteUrl("https://www.domain.рф"));
  }

  @Test
  public void testRelativeUrl() throws MalformedURLException
  {
    final String relativeUrl = "/redirected.html";
    final String baseUrl = "http://domain.com";

    final String absoluteUrl = Utils.getAbsoluteUrl(baseUrl, relativeUrl);
    assertEquals(baseUrl + relativeUrl, absoluteUrl);
  }

  @Test
  public void testRelativeUrl_WithQuery() throws MalformedURLException
  {
    final String relativeUrl = "/redirected.html?argument=123";
    final String baseUrl = "http://domain.com";

    final String absoluteUrl = Utils.getAbsoluteUrl(baseUrl, relativeUrl);
    assertEquals(baseUrl + relativeUrl, absoluteUrl);
  }

  @Test
  public void testRelativeUrl_WithFragment() throws MalformedURLException
  {
    final String relativeUrl = "/redirected.html?argument=123#fragment";
    final String baseUrl = "http://domain.com";

    final String absoluteUrl = Utils.getAbsoluteUrl(baseUrl, relativeUrl);
    assertEquals(baseUrl + relativeUrl, absoluteUrl);
  }

  @Test
  public void testRelativeUrl_Https() throws MalformedURLException
  {
    final String relativeUrl = "/redirected.html?argument=123";
    final String baseUrl = "https://domain.com";

    final String absoluteUrl = Utils.getAbsoluteUrl(baseUrl, relativeUrl);
    assertEquals(baseUrl + relativeUrl, absoluteUrl);
  }
}
