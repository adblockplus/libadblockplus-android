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

package org.adblockplus.libadblockplus.android.webview;

import android.view.View;
import java.lang.ref.WeakReference;

/**
 * A class for ad blocking counters, such as a counter of blocked resources, of a particular
 * instance of WebView.
 * This class is specifically designed for UI.
 */
final public class WebViewCounters
{
  private int blockedCounter = 0;
  private EventsListener eventsListener;

  // In order to synchronously change counter value and to emit events in the UI thread we schedule
  // the corresponding operations to be only executed in the UI thread. The following couple of
  // implementations of Runnable makes it memory heap friendly by reducing the number of allocations
  // and objects collected by GC.
  private View view; // for `View.post`
  private Runnable uiThreadResetDelegate;
  private Runnable uiThreadIncrementDelegate;

  /**
   * A helper method for creation of AdblockWebView.EventsListener and binding it with
   * Counters.EventsListener.
   *
   * @param view for scheduling calls of EventListener in the UI thread as well as for internal
   *             thread safety. Pass e.g. a component which renders the counters and which is
   *             notified by `eventListener` parameter.
   * @param eventsListener which should be bound to the newly created implementation of AdblockWebView.EventsListener.
   * @return a newly created implementation of AdblockWebView.EventsListener.
   */
  public static AdblockWebView.EventsListener bindAdblockWebView(final View view,
                                                                 final EventsListener eventsListener)
  {
    final WebViewCounters counters = new WebViewCounters(view, eventsListener);
    return new AdblockWebView.EventsListener()
    {
      @Override
      public void onNavigation()
      {
        counters.resetBlocked();
      }

      @Override
      public void onResourceLoadingBlocked(final BlockedResourceInfo info)
      {
        counters.incrementBlocked();
      }
    };
  }

  /**
   * Listener for changing events.
   */
  public interface EventsListener
  {
    /**
     * An event signalling about changing of the counter of blocked resources. This event
     * is emitted from a user interface thread (via constructor's `view.post`).
     *
     * @param newValue A new value of blocked resources.
     */
    void onBlockedChanged(final int newValue);
  }

  /**
   * Constructs an instance of Counters class.
   *
   * @param view This parameter is merely used to schedule the events within a corresponding UI thread.
   * @param eventsListener A reference to an implementation of EventsListener.
   */
  public WebViewCounters(final View view, final EventsListener eventsListener)
  {
    this.view = view;
    this.eventsListener = eventsListener;
    uiThreadResetDelegate = new UIThreadDelegate(this, new ResetBlocked_UiOperation());
    uiThreadIncrementDelegate = new UIThreadDelegate(this, new IncrementBlocked_UiOperation());
  }

  /**
   * Thread safe resetting of the blocked counter.
   */
  public void resetBlocked()
  {
    view.post(uiThreadResetDelegate);
  }

  private void resetBlockedUiThreadImpl()
  {
    blockedCounter = 0;
    if (eventsListener != null)
    {
      eventsListener.onBlockedChanged(blockedCounter);
    }
  }

  /**
   * Thread safe incrementation of the blocked counter.
   */
  public void incrementBlocked()
  {
    view.post(uiThreadIncrementDelegate);
  }

  private void incrementBlockedUiThreadImpl()
  {
    ++blockedCounter;
    if (eventsListener != null)
    {
      eventsListener.onBlockedChanged(blockedCounter);
    }
  }

  /**
   * A helper class to schedule a particular operation in the UI thread.
   *
   * Since the current implementation of Counters schedules the operations via `View.post` it
   * can happen that the operation "is" still in the message queue, but the activity with/or the
   * corresponding View is already destroyed. In order to not prevent the activity from being
   * collected we keep a weak reference to Counters (which has a strong reference to EventsListener,
   * which normally holds a strong reference to some View, which finally holds a strong reference to
   * an activity).
   */
  private final static class UIThreadDelegate implements Runnable
  {
    private WeakReference<WebViewCounters> weakCounters;
    private Operation operation;

    interface Operation
    {
      void run(final WebViewCounters counters);
    }

    UIThreadDelegate(final WebViewCounters counters, final Operation operation)
    {
      weakCounters = new WeakReference<WebViewCounters>(counters);
      this.operation = operation;
    }

    @Override
    public void run()
    {
      final WebViewCounters counters = weakCounters.get();
      if (counters != null)
      {
        operation.run(counters);
      }
    }
  }

  private static class ResetBlocked_UiOperation implements UIThreadDelegate.Operation
  {
    @Override
    public void run(final WebViewCounters counters)
    {
      counters.resetBlockedUiThreadImpl();
    }
  }

  private static class IncrementBlocked_UiOperation implements UIThreadDelegate.Operation
  {
    @Override
    public void run(final WebViewCounters counters)
    {
      counters.incrementBlockedUiThreadImpl();
    }
  }
}
