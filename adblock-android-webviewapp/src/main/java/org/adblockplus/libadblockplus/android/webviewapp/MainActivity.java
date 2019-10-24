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

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
{
  private Button addTab;
  private Button settings;
  private TabLayout tabLayout;
  private ViewPager viewPager;

  private final List<TabFragment> tabs = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    bindControls();
    initControls();

    // allow WebView debugging in "Debug" build variant
    // https://developers.google.com/web/tools/chrome-devtools/remote-debugging/webviews
    if (BuildConfig.DEBUG)
    {
      WebView.setWebContentsDebuggingEnabled(true);
    }

    addTab();
  }

  private void initControls()
  {
    addTab.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View v)
      {
        addTab();
      }
    });

    settings.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        navigateSettings();
      }
    });

    viewPager.setAdapter(new TabFragmentAdapter(getSupportFragmentManager(), tabs));
    tabLayout.setupWithViewPager(viewPager);
  }

  private void addTab()
  {
    int newTabsCount = tabs.size() + 1;
    viewPager.setOffscreenPageLimit(newTabsCount);
    tabs.add(TabFragment.newInstance(getString(R.string.main_tab_title, newTabsCount)));
    viewPager.getAdapter().notifyDataSetChanged();
    tabLayout.getTabAt(tabs.size() - 1).select(); // scroll to the last tab
    tabLayout.invalidate();
  }

  private void navigateSettings()
  {
    startActivity(new Intent(this, SettingsActivity.class));
  }

  private void bindControls()
  {
    addTab = findViewById(R.id.main_add_tab);
    settings = findViewById(R.id.main_settings);
    tabLayout = findViewById(R.id.main_tabs);
    viewPager = findViewById(R.id.main_viewpager);
  }
}
