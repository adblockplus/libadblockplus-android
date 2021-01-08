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
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity
{
  private static final String SAVED_SETTINGS = "saved_settings";
  private static final String SAVED_RESTORE_TABS_CHECK = "saved_tabs_checkbox";
  private static final String SAVED_RESTORE_TABS_COUNT = "saved_tabs_count";
  private static final String SAVED_IFRAMES_EH = "saved_iframes_eh";
  private final String testPageSubscriptionUrl =
          "https://dp-testpages.adblockplus.org/en/abp-testcase-subscription.txt";
  private Button addTab;
  private Button settings;
  private TabLayout tabLayout;
  private ViewPager viewPager;
  private CheckBox restoreTabsCheckbox;
  private CheckBox iframesEhCheckbox;
  // hidden clicks left to enable test pages subscriptions list
  private int hiddenClicksLeft = 5;


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
    super.onDestroy();
  }

  public boolean elemHideInInframesEnabled()
  {
    return iframesEhCheckbox.isChecked();
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
    super.onNewIntent(intent);
    navigateIfUrlIntent(tabs.get(0), intent);
  }

  private void setTestPageSubscription()
  {
    AdblockHelper.get().getProvider().retain(true);
    final FilterEngine fe = AdblockHelper.get().getProvider().getEngine().getFilterEngine();
    final Subscription subscription = fe.getSubscription(testPageSubscriptionUrl);
    String message = "error";
    if (subscription.isListed())
    {
      if (subscription.isDisabled())
      {
        subscription.setDisabled(false);
        message = getResources().getString(R.string.subscription_enabled);
      }
      else
      {
        message = getResources().getString(R.string.subscription_already_listed_and_enabled);
      }
    }
    else
    {
      subscription.addToList();
      message = getResources().getString(R.string.subscription_added_and_enabled);
    }
    message += testPageSubscriptionUrl;
    Timber.d(message);
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    
    AdblockHelper.get().getProvider().release();
  }

  void handleHiddenSetting()
  {
    // countdowns counter until reaches 0
    // and sets subscription to test pages
    if (hiddenClicksLeft-- == 0)
    {
      setTestPageSubscription();
    }
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
