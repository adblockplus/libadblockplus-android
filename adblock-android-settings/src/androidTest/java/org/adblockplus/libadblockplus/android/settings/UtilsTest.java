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

package org.adblockplus.libadblockplus.android.settings;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.test.core.app.ApplicationProvider;

import static org.adblockplus.libadblockplus.android.settings.Utils.LOCALE;
import static org.adblockplus.libadblockplus.android.settings.Utils.SUBSCRIPTION_FIELD_LANGUAGES;
import static org.adblockplus.libadblockplus.android.settings.Utils.SUBSCRIPTION_FIELD_TITLE;
import static org.adblockplus.libadblockplus.android.settings.Utils.SUBSCRIPTION_FIELD_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UtilsTest
{
  @Test
  public void testLocalesTitles()
  {
    final Context context = ApplicationProvider.getApplicationContext();
    final Map<String, String> localeToTitle = Utils.getLocaleToTitleMap(context);
    assertNotNull(localeToTitle);
    assertTrue(localeToTitle.size() > 0);
    final String actualEnglishTitle = localeToTitle.get("en");
    assertTrue("English".equalsIgnoreCase(actualEnglishTitle));
  }

  @Test
  public void testParseLanguages()
  {
    assertEquals("", Utils.parseLanguages(null));

    final JSONArray emptyArray = new JSONArray();
    assertEquals("", Utils.parseLanguages(emptyArray));

    try
    {
      assertEquals("", Utils.parseLanguages(new JSONArray("[]")));
      assertEquals("en", Utils.parseLanguages(new JSONArray("[\"en\"]")));
      assertEquals("en,fr", Utils.parseLanguages(new JSONArray("[\"en\",\"fr\"]")));
    }
    catch (final org.json.JSONException e)
    {
      fail();
    }
  }

  @Test
  public void testParseSubscription()
  {
    assertNull(Utils.parseSubscription(new JSONObject()));
    try
    {
      String json = "{\"" + SUBSCRIPTION_FIELD_TITLE + "\":\"" + SUBSCRIPTION_FIELD_TITLE + "\"," +
          "\"" + SUBSCRIPTION_FIELD_URL + "\":\"" + SUBSCRIPTION_FIELD_URL + "\"," +
          "\"" + SUBSCRIPTION_FIELD_LANGUAGES + "\":[\"en\",\"fr\"]}";
      assertNotNull(Utils.parseSubscription(new JSONObject(json)));
      assertEquals(SUBSCRIPTION_FIELD_TITLE, Utils.parseSubscription(new JSONObject(json)).title);
      assertEquals(SUBSCRIPTION_FIELD_URL, Utils.parseSubscription(new JSONObject(json)).url);
      assertEquals("en,fr", Utils.parseSubscription(new JSONObject(json)).languages);

      json = "{\"" + SUBSCRIPTION_FIELD_URL + "\":\"" + SUBSCRIPTION_FIELD_URL + "\"," +
          "\"" + SUBSCRIPTION_FIELD_LANGUAGES + "\":[\"en\"]}";
      assertNull(Utils.parseSubscription(new JSONObject(json)));
      json = "{\"" + SUBSCRIPTION_FIELD_TITLE + "\":\"" + SUBSCRIPTION_FIELD_TITLE + "\"," +
          "\"" + SUBSCRIPTION_FIELD_LANGUAGES + "\":[\"en\"]}";
      assertNull(Utils.parseSubscription(new JSONObject(json)));
    }
    catch (final org.json.JSONException e)
    {
      fail();
    }
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testGetSubscriptionsFromResourceStream()
  {
    assertEquals(Utils.getSubscriptionsFromResourceStream(null), new LinkedList<SubscriptionInfo>());

    InputStream stream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
    assertEquals(Utils.getSubscriptionsFromResourceStream(stream), new LinkedList<SubscriptionInfo>());

    stream = new ByteArrayInputStream(("[\n" +
        "  {\n" +
        "    \"type\": \"ads\",\n" +
        "    \"languages\": [\n" +
        "      \"id\",\n" +
        "      \"ms\"\n" +
        "    ],\n" +
        "    \"title\": \"ABPindo+EasyList\",\n" +
        "    \"url\": \"https://easylist-downloads.adblockplus.org/abpindo+easylist.txt\",\n" +
        "    \"homepage\": \"http://abpindo.blogspot.com/\"\n" +
        "  },\n" +
        "  {\n" +
        "    \"type\": \"ads\",\n" +
        "    \"languages\": [\n" +
        "      \"vi\"\n" +
        "    ],\n" +
        "    \"title\": \"ABPVN List+EasyList\",\n" +
        "    \"url\": \"https://easylist-downloads.adblockplus.org/abpvn+easylist.txt\",\n" +
        "    \"homepage\": \"http://abpvn.com/\"\n" +
        "  }" +
        "]").getBytes(StandardCharsets.UTF_8));
    final List<SubscriptionInfo> subs = Utils.getSubscriptionsFromResourceStream(stream);
    assertNotEquals(subs, new LinkedList<SubscriptionInfo>());
    assertEquals(2, subs.size());
    SubscriptionInfo sub = subs.get(0);
    assertEquals(sub.title, "ABPindo+EasyList");
    assertEquals(sub.url, "https://easylist-downloads.adblockplus.org/abpindo+easylist.txt");
    assertEquals(sub.languages, "id,ms");
    sub = subs.get(1);
    assertEquals(sub.title, "ABPVN List+EasyList");
    assertEquals(sub.url, "https://easylist-downloads.adblockplus.org/abpvn+easylist.txt");
    assertEquals(sub.languages, "vi");
  }

  @Test
  public void testCheckLocaleLanguageMatch()
  {
    final String expectedLocale = "en";
    final Locale locale = new Locale(expectedLocale, expectedLocale.toUpperCase());
    Locale.setDefault(locale);
    assertTrue(LOCALE.matches("[a-z]{2}-[A-Z]{2}")); // Could fail for very special cases of locale e.g. ja_JP_JP
    assertEquals(expectedLocale, Utils.checkLocaleLanguageMatch("fr," + expectedLocale + ",de"));
    assertNull(Utils.checkLocaleLanguageMatch("fr,ru"));
  }

}
