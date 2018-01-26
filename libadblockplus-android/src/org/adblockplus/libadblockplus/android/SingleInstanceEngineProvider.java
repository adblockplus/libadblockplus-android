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

import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides single instance of AdblockEngine shared between registered clients
 */
public class SingleInstanceEngineProvider implements AdblockEngineProvider
{
  private static final String TAG = Utils.getTag(SingleInstanceEngineProvider.class);

  public interface EngineCreatedListener
  {
    void onAdblockEngineCreated(AdblockEngine engine);
  }

  public interface EngineDisposedListener
  {
    void onAdblockEngineDisposed();
  }

  private Context context;
  private String basePath;
  private boolean developmentBuild;
  private String preloadedPreferenceName;
  private Map<String, Integer> urlToResourceIdMap;
  private AdblockEngine engine;
  private CountDownLatch engineCreated;
  private List<EngineCreatedListener> engineCreatedListeners =
    new LinkedList<EngineCreatedListener>();
  private List<EngineDisposedListener> engineDisposedListeners =
    new LinkedList<EngineDisposedListener>();

  /*
    Simple ARC management for AdblockEngine
    Use `retain` and `release`
   */

  private AtomicInteger referenceCounter = new AtomicInteger(0);

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
   */
  public SingleInstanceEngineProvider(Context context, String basePath, boolean developmentBuild)
  {
    this.context = context.getApplicationContext();
    this.basePath = basePath;
    this.developmentBuild = developmentBuild;
  }

  /**
   * Use preloaded subscriptions
   * @param preferenceName Shared Preferences name to store intercepted requests stats
   * @param urlToResourceIdMap
   */
  public SingleInstanceEngineProvider preloadSubscriptions(String preferenceName,
                                                           Map<String, Integer> urlToResourceIdMap)
  {
    this.preloadedPreferenceName = preferenceName;
    this.urlToResourceIdMap = urlToResourceIdMap;
    return this;
  }

  public SingleInstanceEngineProvider addEngineCreatedListener(EngineCreatedListener listener)
  {
    this.engineCreatedListeners.add(listener);
    return this;
  }

  public void removeEngineCreatedListener(EngineCreatedListener listener)
  {
    this.engineCreatedListeners.remove(listener);
  }

  public void clearEngineCreatedListeners()
  {
    this.engineCreatedListeners.clear();
  }

  public SingleInstanceEngineProvider addEngineDisposedListener(EngineDisposedListener listener)
  {
    this.engineDisposedListeners.add(listener);
    return this;
  }

  public void removeEngineDisposedListener(EngineDisposedListener listener)
  {
    this.engineDisposedListeners.remove(listener);
  }

  public void clearEngineDisposedListeners()
  {
    this.engineDisposedListeners.clear();
  }

  private void createAdblock()
  {
    ConnectivityManager connectivityManager =
      (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    IsAllowedConnectionCallback isAllowedConnectionCallback =
      new IsAllowedConnectionCallbackImpl(connectivityManager);

    Log.d(TAG, "Creating adblock engine ...");

    AdblockEngine.Builder builder = AdblockEngine
      .builder(
        AdblockEngine.generateAppInfo(context, developmentBuild),
        basePath)
      .setIsAllowedConnectionCallback(isAllowedConnectionCallback)
      .enableElementHiding(true);

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

    // sometimes we need to init AdblockEngine instance, eg. set user settings
    for (EngineCreatedListener listener : engineCreatedListeners)
    {
      listener.onAdblockEngineCreated(engine);
    }
  }

  @Override
  public synchronized boolean retain(boolean asynchronous)
  {
    boolean firstInstance = false;

    if (referenceCounter.getAndIncrement() == 0)
    {
      firstInstance = true;

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
    return firstInstance;
  }

  @Override
  public void waitForReady()
  {
    if (engineCreated == null)
    {
      throw new IllegalStateException("Usage exception: call retain(true) first");
    }

    try
    {
      Log.d(TAG, "Waiting for ready in " + Thread.currentThread());
      engineCreated.await();
      Log.d(TAG, "Ready");
    }
    catch (InterruptedException e)
    {
      Log.w(TAG, "Interrupted", e);
    }
  }

  @Override
  public AdblockEngine getEngine()
  {
    return engine;
  }

  @Override
  public synchronized boolean release()
  {
    boolean lastInstance = false;

    if (referenceCounter.decrementAndGet() == 0)
    {
      lastInstance = true;

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
    return lastInstance;
  }

  private void disposeAdblock()
  {
    Log.w(TAG, "Disposing adblock engine");

    engine.dispose();
    engine = null;

    // sometimes we need to deinit something after AdblockEngine instance disposed
    // eg. release user settings
    for (EngineDisposedListener listener : engineDisposedListeners)
    {
      listener.onAdblockEngineDisposed();
    }
  }

  @Override
  public int getCounter()
  {
    return referenceCounter.get();
  }
}
