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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity
{
  private static final String SAVED_SETTINGS = "saved_settings";
  private static final String SAVED_RESTORE_TABS_CHECK = "saved_tabs_checkbox";
  private static final String SAVED_RESTORE_TABS_COUNT = "saved_tabs_count";

  private Button addTab;
  private Button settings;
  private TabLayout tabLayout;
  private ViewPager viewPager;
  private CheckBox restoreTabsCheckbox;

  private final List<TabFragment> tabs = new ArrayList<>();

  @Override
  protected void onCreate(final Bundle savedInstanceState)
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

    final SharedPreferences sharedPreferences = getSharedPreferences(SAVED_SETTINGS, 0);
    final boolean restoreChecked = sharedPreferences.getBoolean(SAVED_RESTORE_TABS_CHECK, false);
    if (!restoreChecked)
    {
      addTab(false, getIntent().getDataString());
    }
    navigateIfUrlIntent(tabs.get(0), getIntent());
  }

  private static void navigateIfUrlIntent(final TabFragment tabFragment, final Intent intent)
  {
    if (Intent.ACTION_VIEW.equals(intent.getAction()))
    {
      final Uri uri = intent.getData();
      if (uri != null)
      {
        tabFragment.navigate(uri.toString());
      }
    }
  }

  private void saveTabs()
  {
    final SharedPreferences sharedPreferences = getSharedPreferences(SAVED_SETTINGS, 0);
    final boolean restoreChecked = sharedPreferences.getBoolean(SAVED_RESTORE_TABS_CHECK, false);
    int savedTabs = 0;
    if (restoreChecked)
    {
      for (final TabFragment tab : tabs)
      {
        Timber.d("saveTabs() saves tab %s", savedTabs);
        if (tab.saveTabState(getApplicationContext(), savedTabs))
        {
          ++savedTabs;
        }
      }
    }
    sharedPreferences.edit().putInt(SAVED_RESTORE_TABS_COUNT, restoreChecked ? savedTabs : 0).apply();
  }

  @Override
  protected void onStop()
  {
    saveTabs();
    super.onStop();
  }

  public int getTabId(final TabFragment tab)
  {
    return tabs.indexOf(tab);
  }

  public void checkResume(final TabFragment tab)
  {
    final SharedPreferences sharedPreferences = getSharedPreferences(SAVED_SETTINGS, 0);
    restoreTabsCheckbox.setChecked(sharedPreferences.getBoolean(SAVED_RESTORE_TABS_CHECK, false));

    if (restoreTabsCheckbox.isChecked())
    {
      Timber.d("checkResume() restores tab %d", getTabId(tab));
      tab.restoreTabState(getApplicationContext(), getTabId(tab));
      // Need to call this otherwise restored tabs have no title
      viewPager.getAdapter().notifyDataSetChanged();
    }
  }

  @Override
  protected void onNewIntent(final Intent intent)
  {
    navigateIfUrlIntent(tabs.get(0), intent);
  }

  private void initControls()
  {
    addTab.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View v)
      {
        addTab(false, null);
      }
    });
    addTab.setOnLongClickListener(new View.OnLongClickListener()
    {
      @Override
      public boolean onLongClick(final View view)
      {
        addTab(true, null);
        return true;
      }
    });

    settings.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View v)
      {
        navigateSettings();
      }
    });

    viewPager.setAdapter(new TabFragmentAdapter(getSupportFragmentManager(), tabs));
    tabLayout.setupWithViewPager(viewPager);

    restoreTabsCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
    {
      @Override
      public void onCheckedChanged(final CompoundButton compoundButton, final boolean newValue)
      {
        final SharedPreferences sharedPreferences = getSharedPreferences(SAVED_SETTINGS, 0);
        sharedPreferences.edit().putBoolean(SAVED_RESTORE_TABS_CHECK, newValue).apply();
        if (!newValue)
        {
          for (int i = sharedPreferences.getInt(SAVED_RESTORE_TABS_COUNT, 0) - 1; i >= 0; --i)
          {
            TabFragment.deleteTabState(getApplicationContext(), i);
          }
          sharedPreferences.edit().putInt(SAVED_RESTORE_TABS_COUNT, 0).apply();
        }
      }
    });

    final SharedPreferences sharedPreferences = getSharedPreferences(SAVED_SETTINGS, 0);
    final int tabsCount = sharedPreferences.getInt(SAVED_RESTORE_TABS_COUNT, 0);
    for (int i = 0; i < tabsCount; ++i)
    {
      Timber.d("initControls() adds tab %d", i);
      addTab(false, null);
    }
  }

  /**
   * Adds a tab
   *
   * @param useCustomIntercept (used for QA) will add #TabInterceptingWebViewClient
   *                           instead #TabWebViewClient to the WebView inside the tab
   *                           #TabInterceptingWebViewClient uses custom shouldInterceptRequest
   */
  private void addTab(final boolean useCustomIntercept, final String navigateToUrl)
  {
    final int newTabsCount = tabs.size() + 1;
    viewPager.setOffscreenPageLimit(newTabsCount);
    tabs.add(TabFragment.newInstance(getString(R.string.main_tab_title, newTabsCount),
            navigateToUrl,
            useCustomIntercept));
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
    restoreTabsCheckbox = findViewById(R.id.restore_checkbox);
  }
}
