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

package org.adblockplus;

import android.content.Context;
import android.content.res.Resources;

import org.adblockplus.engine.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import timber.log.Timber;

import static android.text.TextUtils.isEmpty;

/**
 * Class representing a list of Recommended Subscriptions.
 * The Recommended Subscriptions list is the compile-time preloaded list of Subscriptions
 * with some additional attributes like the supported human languages and a homepage
 */
public class RecommendedSubscriptions
{
  private static final String SUBSCRIPTION_FIELD_URL = "url";
  private static final String SUBSCRIPTION_FIELD_LANGUAGES = "languages";
  private static final String SUBSCRIPTION_FIELD_TITLE = "title";
  private static List<Subscription> defaultSubscriptions;

  /**
   * @param context is required to load the file from resources
   */
  public RecommendedSubscriptions(final Context context)
  {
    try
    {
      final InputStream inputStream = context.getResources().openRawResource(R.raw.subscriptions);
      defaultSubscriptions = getSubscriptionsFromResourceStream(inputStream);
    }
    catch (final Resources.NotFoundException exception)
    {
      Timber.e("subscriptions.json is not found");
      defaultSubscriptions = new ArrayList<>();
    }
  }

  /**
   * @return list of all the Recommended Subscriptions
   */
  public List<Subscription> get()
  {
    return Collections.unmodifiableList(defaultSubscriptions);
  }

  /**
   * @param sub subscription to amend (URL is used as a key)
   * @return amended subscription with all blank fields filled if exist in Recommended Subscription
   */
  public Subscription amendSubscription(final Subscription sub)
  {
    for (final Subscription defaultSubscription: defaultSubscriptions)
    {
      if (sub.url.equals(defaultSubscription.url))
      {
        final String languages = sub.languages.isEmpty() ? defaultSubscription.languages : sub.languages;
        final String title = sub.title.isEmpty() ? defaultSubscription.title : sub.title;
        final String homepage = sub.homepage.isEmpty() ? defaultSubscription.homepage : sub.homepage;
        return new Subscription(sub.url, title, languages, homepage);
      }
    }
    return sub;
  }

  private static String parseLanguages(final JSONArray languagesArray)
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

  private static Subscription parseSubscription(final JSONObject jsonObject)
  {
    final String url = jsonObject.optString(SUBSCRIPTION_FIELD_URL);
    final String title = jsonObject.optString(SUBSCRIPTION_FIELD_TITLE);
    final String languages = parseLanguages(jsonObject.optJSONArray(SUBSCRIPTION_FIELD_LANGUAGES));

    if (isEmpty(url) || isEmpty(title))
    {
      return null;
    }

    return new Subscription(url, title, languages, "");
  }

  private static List<Subscription> getSubscriptionsFromResourceStream(final InputStream stream)
  {
    final List<Subscription> subscriptions = new ArrayList<>();

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
      final Subscription subscription;
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
}
