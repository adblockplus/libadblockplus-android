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

package org.adblockplus.libadblockplus.android.webviewapp;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.List;

public class TabFragmentAdapter extends FragmentPagerAdapter
{
  private final List<TabFragment> fragments;

  public TabFragmentAdapter(final FragmentManager fragmentManager, final List<TabFragment> fragments)
  {
    super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    this.fragments = fragments;
  }

  @Override
  public Fragment getItem(final int position)
  {
    return fragments.get(position);
  }

  @Override
  public int getCount()
  {
    return fragments.size();
  }

  @Override
  public CharSequence getPageTitle(final int position)
  {
    return fragments.get(position).getTitle();
  }
}
