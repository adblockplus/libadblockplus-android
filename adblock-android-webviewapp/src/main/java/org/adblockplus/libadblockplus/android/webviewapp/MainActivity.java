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

import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.eyeo.hermes.AdblockEngine;
import com.eyeo.hermes.Engine;
import com.google.android.material.tabs.TabLayout;

import org.adblockplus.libadblockplus.android.settings.AdblockHelper;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements ComponentCallbacks2
{
  private static final String SAVED_SETTINGS = "saved_settings";
  private static final String SAVED_RESTORE_TABS_CHECK = "saved_tabs_checkbox";
  private static final String SAVED_RESTORE_TABS_COUNT = "saved_tabs_count";
  private static final String SAVED_IFRAMES_EH = "saved_iframes_eh";
  private static final String testPageSubscriptionUrl =
          "https://dp-testpages.adblockplus.org/en/abp-testcase-subscription.txt";
  private Button addTab;
  private Button settings;
  private TabLayout tabLayout;
  private ViewPager viewPager;
  private CheckBox restoreTabsCheckbox;
  private CheckBox iframesEhCheckbox;
  // hidden clicks left to enable test pages subscriptions list
  private int hiddenClicksLeft = 5;
  AdblockEngine adblockEngine;

  private final List<TabFragment> tabs = new ArrayList<>();

  @Override
  protected void onCreate(final Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    registerComponentCallbacks(this);
    bindControls();
    initControls();

    // allow WebView debugging in "Debug" build variant
    // https://developers.google.com/web/tools/chrome-devtools/remote-debugging/webviews
    if (BuildConfig.DEBUG)
    {
      WebView.setWebContentsDebuggingEnabled(true);
    }

    if (adblockEngine == null)
    {
      adblockEngine = new Engine(getApplicationContext());
    }
    if (tabs.isEmpty())
    {
      addTab(false, getIntent().getDataString());
    }
    navigateIfUrlIntent(tabs.get(0), getIntent());
  }

  @Override
  protected void onDestroy()
  {
    final SharedPreferences sharedPreferences = getSharedPreferences(SAVED_SETTINGS, 0);
    sharedPreferences.edit().putBoolean(SAVED_IFRAMES_EH, iframesEhCheckbox.isChecked()).apply();
    unregisterComponentCallbacks(this);
    super.onDestroy();
  }

  public boolean elemHideInInframesEnabled()
  {
    return iframesEhCheckbox.isChecked();
  }

  private void navigateIfUrlIntent(final TabFragment tabFragment, final Intent intent)
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
    super.onNewIntent(intent);
    navigateIfUrlIntent(tabs.get(0), intent);
  }

  @Override
  public void onTrimMemory(final int level)
  {
    // if a system demands more memory, call the GC of the adblock engine to release some
    // this can free up to ~60-70% of memory occupied by the engine
    if (level == TRIM_MEMORY_RUNNING_CRITICAL && AdblockHelper.get().isInit())
    {
      AdblockHelper.get().getProvider().onLowMemory();
      Timber.w("Lacking memory! Notifying AdBlock about memory constraint");
    }
  }

  void handleHiddenSetting()
  {
    // countdowns counter until reaches 0
    // and sets subscription to test pages
    if (hiddenClicksLeft-- == 0)
    {
      Toast.makeText(getApplicationContext(), "Hidden settings are not supported!", Toast.LENGTH_SHORT).show();
    }
  }

  private void initControls()
  {
    addTab.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View v)
      {
        Toast.makeText(getApplicationContext(), "Multiple tabs are not supported!", Toast.LENGTH_SHORT).show();
        //addTab(false, null);
      }
    });
    addTab.setOnLongClickListener(new View.OnLongClickListener()
    {
      @Override
      public boolean onLongClick(final View view)
      {
        Toast.makeText(getApplicationContext(), "Multiple tabs are not supported!", Toast.LENGTH_SHORT).show();
        //addTab(true, null);
        return true;
      }
    });

    settings.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(final View v)
      {
        Toast.makeText(getApplicationContext(), "Settings are not supported!", Toast.LENGTH_SHORT).show();
        //navigateSettings();
      }
    });

    viewPager.setAdapter(new TabFragmentAdapter(getSupportFragmentManager(), tabs));
    tabLayout.setupWithViewPager(viewPager);

    restoreTabsCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
    {
      @Override
      public void onCheckedChanged(final CompoundButton compoundButton, final boolean newValue)
      {
        handleHiddenSetting();
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

    iframesEhCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
    {
      @Override
      public void onCheckedChanged(final CompoundButton compoundButton, final boolean newValue)
      {
        final SharedPreferences sharedPreferences = getSharedPreferences(SAVED_SETTINGS, 0);
        sharedPreferences.edit().putBoolean(SAVED_IFRAMES_EH, newValue).apply();
        for (final TabFragment tabFragment : tabs)
        {
          tabFragment.setJsInIframesEnabled(newValue);
        }
      }
    });

    final SharedPreferences sharedPreferences = getSharedPreferences(SAVED_SETTINGS, 0);
    final int tabsCount = sharedPreferences.getInt(SAVED_RESTORE_TABS_COUNT, 0);

    iframesEhCheckbox.setChecked(sharedPreferences.getBoolean(SAVED_IFRAMES_EH, false));

    final List<Fragment> fragments = this.getSupportFragmentManager().getFragments();

    final TabFragment[] tabFragments = new TabFragment[fragments.size()];
    for (final Fragment fragment : fragments)
    {
      final TabFragment tabFragment = (TabFragment) fragment;
      final String title = tabFragment.getTitle();
      final String extractIdString = title.replaceAll(getString(R.string.main_tab_title), "");
      final int index = Integer.parseInt(extractIdString) - 1;
      tabFragments[index] = tabFragment;
    }
    int i = 0;
    for (final TabFragment tabFragment : tabFragments)
    {
      // some tab fragments might be recovered through
      // android on create so we just need to add them into
      // tabs
      addTab(tabFragment);
      ++i;
    }
    for (; i < tabsCount; ++i)
    {
      // the rest of the tab fragments need to be explicitly
      // created through add tab
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
    final TabFragment newTab = TabFragment.newInstance(
        getString(R.string.main_tab_title).concat(String.valueOf(tabs.size() + 1)),
        navigateToUrl,
        useCustomIntercept);
    addTab(newTab);
  }

  private void addTab(final TabFragment tab)
  {
    final int newTabsCount = tabs.size() + 1;
    viewPager.setOffscreenPageLimit(newTabsCount);
    tabs.add(tab);
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
    iframesEhCheckbox = findViewById(R.id.iframes_eh_checkbox);
  }
}
