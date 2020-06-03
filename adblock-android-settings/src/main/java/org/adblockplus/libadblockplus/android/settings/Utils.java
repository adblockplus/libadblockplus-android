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
import android.content.res.Resources;

import java.util.HashMap;
import java.util.Map;

public class Utils
{
  public static Map<String, String> getLocaleToTitleMap(final Context context)
  {
    final Resources resources = context.getResources();
    final String[] locales = resources.getStringArray(R.array.fragment_adblock_general_locale_title);
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
}
