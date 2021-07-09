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

package org.adblockplus.android.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import static android.text.TextUtils.isEmpty;

public class Utils
{
  public static final String SUBSCRIPTION_FIELD_URL = "url";
  public static final String SUBSCRIPTION_FIELD_LANGUAGES = "languages";
  public static final String SUBSCRIPTION_FIELD_TITLE = "title";
  @SuppressLint("ConstantLocale") // This is used only on fresh start
  public static final String LOCALE = Locale.getDefault().toString()
    .replace('_', '-')
    .replaceAll("^iw-", "he-");

  public static Map<String, String> getLocaleToTitleMap(final Context context)
  {
    final Resources resources = context.getResources();
    final String[] locales = resources.getStringArray(
      R.array.fragment_adblock_general_locale_title);
    final String separator = resources.getString(R.string.fragment_adblock_general_separator);
    final Map<String, String> localeToTitle = new HashMap<>(locales.length);
    for (final String localeAndTitlePair : locales)
    {
      // in `String.split()` separator is a regexp, but we want to treat it as a string
      final int separatorIndex = localeAndTitlePair.indexOf(separator);
      final String locale = localeAndTitlePair.substring(0, separatorIndex);
      final String title = localeAndTitlePair.substring(separatorIndex + 1);
      localeToTitle.put(locale, title);
    }
    return localeToTitle;
  }

  public static String parseLanguages(final JSONArray languagesArray)
  {
    if (languagesArray == null || languagesArray.length() < 1)
    {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    String language;
    for (int i = 0; i < languagesArray.length(); i++)
    {
      try
      {
        language = languagesArray.getString(i);
      }
      catch (final JSONException e)
      {
        return "";
      }
      sb.append(sb.length() == 0 ? language : "," + language);
    }
    return sb.toString();
  }

  public static SubscriptionInfo parseSubscription(final JSONObject jsonObject)
  {
    final String url = jsonObject.optString(SUBSCRIPTION_FIELD_URL);
    final String title = jsonObject.optString(SUBSCRIPTION_FIELD_TITLE);
    final String languages = parseLanguages(jsonObject.optJSONArray(SUBSCRIPTION_FIELD_LANGUAGES));

    if (isEmpty(url) || isEmpty(title))
    {
      return null;
    }

    return new SubscriptionInfo(url, title, languages, "", "");
  }

  public static List<SubscriptionInfo> getSubscriptionsFromResourceStream(final InputStream stream)
  {
    final List<SubscriptionInfo> subscriptions = new LinkedList<>();

    if (stream == null)
    {
      return subscriptions;
    }

    final Scanner scanner = new Scanner(stream).useDelimiter("\\A");
    if (!scanner.hasNext())
    {
      return subscriptions;
    }

    final JSONArray array;
    try
    {
      array = new JSONArray(scanner.next());
    }
    catch (final JSONException e)
    {
      return subscriptions;
    }

    for (int i = 0; i < array.length(); i++)
    {
      final SubscriptionInfo subscription;
      try
      {
        subscription = parseSubscription(array.getJSONObject(i));
      }
      catch (final JSONException e)
      {
        continue;
      }
      if (subscription != null)
      {
        subscriptions.add(subscription);
      }
    }
    return subscriptions;
  }

  // The following two methods just copy the ones from the Core: libadblockplus\\lib\\utils.js

  public static String checkLocaleLanguageMatch(final String languages)
  {
    final String[] languageList = languages.split(",");
    for (final String language : languageList)
    {
      if (LOCALE.matches("^" + language + "\\b.+"))
      {
        return language;
      }
    }
    return null;
  }

  public static SubscriptionInfo chooseDefaultSubscription(
    final List<SubscriptionInfo> subscriptions)
  {
    SubscriptionInfo selectedSubscription = null;
    String selectedLanguage = null;
    int matchCount = 0;
    final Random rand = new Random();
    for (final SubscriptionInfo subscription : subscriptions)
    {
      if (selectedSubscription == null)
      {
        selectedSubscription = subscription;
      }
      final String language = checkLocaleLanguageMatch(subscription.languages);
      if (language == null)
      {
        continue;
      }
      if (selectedLanguage == null || selectedLanguage.length() < language.length())
      {
        selectedSubscription = subscription;
        selectedLanguage = language;
        matchCount = 1;
      }
      else if (selectedLanguage.length() == language.length())
      {
        matchCount++;
        // If multiple items have a matching language of the same length,
        // Select one of the items randomly, probability should be the same
        // for all items. So we replace the previous match here with
        // probability 1/N (N being the number of matches).
        if (rand.nextInt(matchCount) == 0)
        {
          selectedSubscription = subscription;
          selectedLanguage = language;
        }
      }
    }
    return selectedSubscription;
  }

  public static List<SubscriptionInfo> chooseSelectedSubscriptions(
    final List<SubscriptionInfo> subscriptions,
    final Set<String> selectedTitles)
  {
    final List<SubscriptionInfo> selectedSubscriptions = new LinkedList<>();
    for (final SubscriptionInfo eachSubscription : subscriptions)
    {
      if (selectedTitles.contains(eachSubscription.url))
      {
        selectedSubscriptions.add(eachSubscription);
      }
    }
    return selectedSubscriptions;
  }

}
