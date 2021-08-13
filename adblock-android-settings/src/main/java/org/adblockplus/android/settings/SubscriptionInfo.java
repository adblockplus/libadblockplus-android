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

import org.adblockplus.Subscription;

public class SubscriptionInfo
{
  public SubscriptionInfo(final Subscription subscription)
  {
    this.url = subscription.url;
    this.title = subscription.title;
    this.languages = subscription.languages;
    this.homepage = subscription.homepage;
  }

  public SubscriptionInfo(final String url,
                          final String title,
                          final String languages,
                          final String homepage,
                          final String author)
  {
    this.url = url;
    this.title = title;
    this.languages = languages;
    this.homepage = homepage;
  }

  public SubscriptionInfo(final String url,
                          final String title)
  {
    this(url, title, "", "", "");
  }

  public String url;
  public String title;
  public String homepage;
  public String languages;
}
