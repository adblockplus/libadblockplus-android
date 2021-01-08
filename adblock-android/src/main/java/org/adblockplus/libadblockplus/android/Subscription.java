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

package org.adblockplus.libadblockplus.android;

import java.io.Serializable;

public class Subscription implements Serializable
{
  public Subscription()
  {
    this.title = "";
    this.url = "";
    this.prefixes = "";
    this.homepage = "";
    this.author = "";
  }

  public Subscription(final String title,
                      final String url,
                      final String prefixes,
                      final String homepage,
                      final String author)
  {
    this.title = title;
    this.url = url;
    this.prefixes = prefixes;
    this.homepage = homepage;
    this.author = author;
  }

  public String title;
  public String url;
  public String homepage;
  public String prefixes;
  public String author;
}
