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

package org.adblockplus.android;

import org.adblockplus.AdblockEngine;
import org.adblockplus.android.AdblockEngine.Factory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import timber.log.Timber;

/**
 * Provides single instance of AdblockEngine shared between registered clients
 */
public class SingleInstanceEngineProvider implements AdblockEngineProvider
{
  private final org.adblockplus.android.AdblockEngine.Factory engineFactory;
  private final AtomicReference<org.adblockplus.android.AdblockEngine> engineReference
    = new AtomicReference<>();
  private final List<EngineCreatedListener> engineCreatedListeners = new CopyOnWriteArrayList<>();
  private final List<EngineDisposedListener> engineDisposedListeners = new CopyOnWriteArrayList<>();
  private final ReentrantReadWriteLock engineLock = new ReentrantReadWriteLock();
  private final ReentrantReadWriteLock referenceCounterLock = new ReentrantReadWriteLock();
  private final ExecutorService executorService;

  /*
    Simple ARC management for AdblockEngine
    Use `retain` and `release`
   */

  private final AtomicInteger referenceCounter = new AtomicInteger(0);

  // shutdowns `ExecutorService` instance on system shutdown
  private static class ExecutorServiceShutdownHook extends Thread
  {
    private final ExecutorService executorService;

    private ExecutorServiceShutdownHook(final ExecutorService executorService)
    {
      Timber.w("Hooking on executor service %s", executorService);
      this.executorService = executorService;
    }

    @Override
    public void run()
    {
      Timber.w("Shutting down executor service %s", executorService);
      executorService.shutdown();
    }
  }

  /**
   * Init with factory
   * @param engineFactory Factory to build AdblockEngine
   */
  public SingleInstanceEngineProvider(final Factory engineFactory)
  {
    this.engineFactory = engineFactory;
    this.executorService = createExecutorService();
  }

  protected ExecutorService createExecutorService()
  {
    final ExecutorService executorService = Executors.newSingleThreadExecutor();
    Runtime.getRuntime().addShutdownHook(new ExecutorServiceShutdownHook(executorService));
    return executorService;
  }

  @Override
  public SingleInstanceEngineProvider addEngineCreatedListener(final EngineCreatedListener listener)
  {
    this.engineCreatedListeners.add(listener);
    return this;
  }

  @Override
  public void removeEngineCreatedListener(final EngineCreatedListener listener)
  {
    this.engineCreatedListeners.remove(listener);
  }

  @Override
  public SingleInstanceEngineProvider addEngineDisposedListener(final EngineDisposedListener listener)
  {
    this.engineDisposedListeners.add(listener);
    return this;
  }

  @Override
  public void removeEngineDisposedListener(final EngineDisposedListener listener)
  {
    this.engineDisposedListeners.remove(listener);
  }

  private void createAdblock()
  {
    Timber.d("Creating adblock engine ...");
    final org.adblockplus.android.AdblockEngine engine = engineFactory.build();
    Timber.d("Engine created");

    engineReference.set(engine);

    // sometimes we need to init AdblockEngine instance, eg. set user settings
    for (final EngineCreatedListener listener : engineCreatedListeners)
    {
      listener.onAdblockEngineCreated(engine);
    }
  }

  @Override
  public boolean retain(final boolean asynchronous)
  {
    final Future future;
    referenceCounterLock.writeLock().lock();
    try
    {
      final boolean firstInstance = (referenceCounter.getAndIncrement() == 0);
      if (!firstInstance)
      {
        return false;
      }
      future = scheduleTask(retainTask);
    }
    finally
    {
      referenceCounterLock.writeLock().unlock();
    }

    if (!asynchronous)
    {
      waitForTask(future);
    }
    return true;
  }

  private final Runnable retainTask = new Runnable()
  {
    @Override
    public void run()
    {
      Timber.w("Waiting for lock in " + Thread.currentThread());
      engineLock.writeLock().lock();

      try
      {
        createAdblock();
      }
      finally
      {
        engineLock.writeLock().unlock();
      }
    }
  };

  // the task does nothing and can be used as a way to wait
  // for all the current tasks to be finished
  private final Runnable waitForTheTasksTask = new Runnable()
  {
    @Override
    public void run()
    {
      // nothing
    }
  };

  @Override
  public void waitForReady()
  {
    Timber.d("Waiting for ready in %s", Thread.currentThread());
    waitForTask(scheduleTask(waitForTheTasksTask));
    Timber.d("Ready");
  }

  @Override
  public AdblockEngine getEngine()
  {
    return engineReference.get();
  }

  @Override
  public boolean release()
  {
    final Future future;
    referenceCounterLock.writeLock().lock();
    try
    {
      final boolean lastInstance = (referenceCounter.decrementAndGet() == 0);
      if (!lastInstance)
      {
        return false;
      }
      future = scheduleTask(releaseTask);
    }
    finally
    {
      referenceCounterLock.writeLock().unlock();
    }

    waitForTask(future); // release() is always synchronous
    return true;
  }

  private Future scheduleTask(final Runnable task)
  {
    return executorService.submit(task);
  }

  private void waitForTask(final Future future) throws RuntimeException
  {
    try
    {
      future.get(); // block the thread and wait
    }
    catch (final Exception e)
    {
      Timber.e(e);
      throw new RuntimeException(e);
    }
  }

  private final Runnable releaseTask = new Runnable()
  {
    @Override
    public void run()
    {
      Timber.w("Waiting for lock in " + Thread.currentThread());
      engineLock.writeLock().lock();

      try
      {
        disposeAdblock();
      }
      finally
      {
        engineLock.writeLock().unlock();
      }
    }
  };

  private void disposeAdblock()
  {
    Timber.w("Disposing adblock engine");

    engineReference.getAndSet(null).dispose();

    // sometimes we need to deinit something after AdblockEngine instance disposed
    // eg. release user settings
    for (final EngineDisposedListener listener : engineDisposedListeners)
    {
      listener.onAdblockEngineDisposed();
    }
  }

  @Override
  public int getCounter()
  {
    referenceCounterLock.readLock().lock();
    try
    {
      return referenceCounter.get();
    }
    finally
    {
      referenceCounterLock.readLock().unlock();
    }
  }

  @Override
  public ReentrantReadWriteLock.ReadLock getReadEngineLock()
  {
    Timber.d("getReadEngineLock() called from " + Thread.currentThread());
    return engineLock.readLock();
  }
}
