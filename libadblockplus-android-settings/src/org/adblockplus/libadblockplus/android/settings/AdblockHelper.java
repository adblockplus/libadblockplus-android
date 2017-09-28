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
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.util.Log;

import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;
import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AndroidWebRequestResourceWrapper;
import org.adblockplus.libadblockplus.android.Utils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AdblockHelper shared resources
 * (singleton)
 */
public class AdblockHelper
{
  private static final String TAG = Utils.getTag(AdblockHelper.class);

  /**
   * Suggested preference name to store settings
   */
  public static final String PREFERENCE_NAME = "ADBLOCK";

  /**
   * Suggested preference name to store intercepted subscription requests
   */
  public static final String PRELOAD_PREFERENCE_NAME = "ADBLOCK_PRELOAD";
  private static AdblockHelper _instance;

  private Context context;
  private String basePath;
  private boolean developmentBuild;
  private String settingsPreferenceName;
  private String preloadedPreferenceName;
  private Map<String, Integer> urlToResourceIdMap;
  private AdblockEngine engine;
  private AdblockSettingsStorage storage;
  private CountDownLatch engineCreated;
  private Long v8IsolatePtr;

  /*
    Simple ARC management for AdblockEngine
    Use `retain` and `release`
   */

  private AtomicInteger referenceCounter = new AtomicInteger(0);

  // singleton
  protected AdblockHelper()
  {
    // prevents instantiation
  }

  /**
   * Use to get AdblockHelper instance
   * @return adblock instance
   */
  public static synchronized AdblockHelper get()
  {
    if (_instance == null)
    {
      _instance = new AdblockHelper();
    }

    return _instance;
  }

  public AdblockEngine getEngine()
  {
    return engine;
  }

  public AdblockSettingsStorage getStorage()
  {
    return storage;
  }

  /**
   * Init with context
   * @param context application context
   * @param basePath file system root to store files
   *
   *                 Adblock Plus library will download subscription files and store them on
   *                 the path passed. The path should exist and the directory content should not be
   *                 cleared out occasionally. Using `context.getCacheDir().getAbsolutePath()` is not
   *                 recommended because it can be cleared by the system.
   * @param developmentBuild debug or release?
   * @param preferenceName Shared Preferences name to store adblock settings
   */
  public AdblockHelper init(Context context, String basePath,
                            boolean developmentBuild, String preferenceName)
  {
    this.context = context.getApplicationContext();
    this.basePath = basePath;
    this.developmentBuild = developmentBuild;
    this.settingsPreferenceName = preferenceName;
    return this;
  }

  /**
   * Use preloaded subscriptions
   * @param preferenceName Shared Preferences name to store intercepted requests stats
   * @param urlToResourceIdMap
   */
  public AdblockHelper preloadSubscriptions(String preferenceName, Map<String, Integer> urlToResourceIdMap)
  {
    this.preloadedPreferenceName = preferenceName;
    this.urlToResourceIdMap = urlToResourceIdMap;
    return this;
  }

  public void useV8Isolate(long ptr)
  {
    this.v8IsolatePtr = ptr;
  }

  private void createAdblock()
  {
    ConnectivityManager connectivityManager =
      (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    IsAllowedConnectionCallback isAllowedConnectionCallback = new IsAllowedConnectionCallbackImpl(connectivityManager);

    Log.d(TAG, "Creating adblock engine ...");

    // read and apply current settings
    SharedPreferences settingsPrefs = context.getSharedPreferences(
      settingsPreferenceName,
      Context.MODE_PRIVATE);
    storage = new SharedPrefsStorage(settingsPrefs);

    AdblockEngine.Builder builder = AdblockEngine
      .builder(
        AdblockEngine.generateAppInfo(context, developmentBuild),
        basePath)
      .setIsAllowedConnectionCallback(isAllowedConnectionCallback)
      .enableElementHiding(true);

    if (v8IsolatePtr != null)
    {
      builder.useV8Isolate(v8IsolatePtr);
    }

    // if preloaded subscriptions provided
    if (preloadedPreferenceName != null)
    {
      SharedPreferences preloadedSubscriptionsPrefs = context.getSharedPreferences(
        preloadedPreferenceName,
        Context.MODE_PRIVATE);
      builder.preloadSubscriptions(
        context,
        urlToResourceIdMap,
        new AndroidWebRequestResourceWrapper.SharedPrefsStorage(preloadedSubscriptionsPrefs));
    }

    engine = builder.build();

    Log.d(TAG, "AdblockHelper engine created");

    AdblockSettings settings = storage.load();
    if (settings != null)
    {
      Log.d(TAG, "Applying saved adblock settings to adblock engine");
      // apply last saved settings to adblock engine

      // all the settings except `enabled` and whitelisted domains list
      // are saved by adblock engine itself
      engine.setEnabled(settings.isAdblockEnabled());
      engine.setWhitelistedDomains(settings.getWhitelistedDomains());

      // allowed connection type is saved by filter engine but we need to override it
      // as filter engine can be not created when changing
      String connectionType = (settings.getAllowedConnectionType() != null
       ? settings.getAllowedConnectionType().getValue()
       : null);
      engine.getFilterEngine().setAllowedConnectionType(connectionType);
    }
    else
    {
      Log.w(TAG, "No saved adblock settings");
    }
  }

  /**
   * Wait until everything is ready (used for `retain(true)`)
   * Warning: locks current thread
   */
  public void waitForReady()
  {
    if (engineCreated == null)
    {
      throw new RuntimeException("AdblockHelper Plus usage exception: call retain(true) first");
    }

    try
    {
      Log.d(TAG, "Waiting for ready ...");
      engineCreated.await();
      Log.d(TAG, "Ready");
    }
    catch (InterruptedException e)
    {
      Log.w(TAG, "Interrupted", e);
    }
  }

  private void disposeAdblock()
  {
    Log.w(TAG, "Disposing adblock engine");

    engine.dispose();
    engine = null;

    storage = null;
  }

  /**
   * Get registered clients count
   * @return registered clients count
   */
  public int getCounter()
  {
    return referenceCounter.get();
  }

  /**
   * Register AdblockHelper engine client
   * @param asynchronous If `true` engines will be created in background thread without locking of
   *                     current thread. Use waitForReady() before getEngine() later.
   *                     If `false` locks current thread.
   */
  public synchronized void retain(boolean asynchronous)
  {
    if (referenceCounter.getAndIncrement() == 0)
    {
      if (!asynchronous)
      {
        createAdblock();
      }
      else
      {
        // latch is required for async (see `waitForReady()`)
        engineCreated = new CountDownLatch(1);

        new Thread(new Runnable()
        {
          @Override
          public void run()
          {
            createAdblock();

            // unlock waiting client thread
            engineCreated.countDown();
          }
        }).start();
      }
    }
  }

  /**
   * Unregister AdblockHelper engine client
   */
  public synchronized void release()
  {
    if (referenceCounter.decrementAndGet() == 0)
    {
      if (engineCreated != null)
      {
        // retained asynchronously
        waitForReady();
        disposeAdblock();

        // to unlock waiting client in waitForReady()
        engineCreated.countDown();
        engineCreated = null;
      }
      else
      {
        disposeAdblock();
      }
    }
  }
}
