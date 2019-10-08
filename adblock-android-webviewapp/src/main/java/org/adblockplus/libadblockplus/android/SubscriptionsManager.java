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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.util.Log;

import java.util.List;

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.android.settings.AdblockHelper;

public class SubscriptionsManager
{
  private static final String TAG = SubscriptionsManager.class.getSimpleName();

  private static final String ACTION_PREFIX =
      "org.adblockplus.libadblockplus.android.intent.action.SUBSCRIPTION_";
  private static final String ACTION_LIST = ACTION_PREFIX + "LIST";
  private static final String ACTION_ADD = ACTION_PREFIX + "ADD";
  private static final String ACTION_ENABLE = ACTION_PREFIX + "ENABLE";
  private static final String ACTION_DISABLE = ACTION_PREFIX + "DISABLE";
  private static final String ACTION_REMOVE = ACTION_PREFIX + "REMOVE";
  private static final String ACTION_UPDATE = ACTION_PREFIX + "UPDATE";
  private static final String EXTRA_URL = "URL";

  private Context context;
  private BroadcastReceiver receiver;

  public SubscriptionsManager(final Context context)
  {
    this.context = context;
    Log.v(TAG, "Initializing subscription management");

    receiver = new BroadcastReceiver()
    {
      @Override
      public void onReceive(final Context context, final Intent intent)
      {
        Log.v(TAG, "Received intent " + intent);

        AdblockHelper.get().getProvider().waitForReady();
        if (intent.getAction().equals(ACTION_LIST))
        {
          list();
        }
        else
        {
          final String url = intent.getStringExtra(EXTRA_URL);
          Log.d(TAG, "Subscription = " + url);
          final FilterEngine filterEngine =
              AdblockHelper.get().getProvider().getEngine().getFilterEngine();
          final Subscription subscription = filterEngine.getSubscription(url);

          try
          {
            if (intent.getAction().equals(ACTION_ADD))
            {
              add(subscription);
            }
            else if (intent.getAction().equals(ACTION_REMOVE))
            {
              remove(subscription);
            }
            else if (intent.getAction().equals(ACTION_ENABLE))
            {
              enable(subscription);
            }
            else if (intent.getAction().equals(ACTION_DISABLE))
            {
              disable(subscription);
            }
            else if (intent.getAction().equals(ACTION_UPDATE))
            {
              update(subscription);
            }
          }
          finally
          {
            subscription.dispose();
          }
        }
      }

      private void remove(final Subscription subscription)
      {
        if (!assertIsListed(subscription))
        {
          return;
        }

        subscription.removeFromList();
        Log.d(TAG, "Removed subscription");
      }

      private void update(final Subscription subscription)
      {
        if (!assertIsListed(subscription))
        {
          return;
        }

        if (!assertIsEnabled(subscription))
        {
          return;
        }

        subscription.updateFilters();
        Log.d(TAG, "Forced subscription update");
      }

      private void enable(final Subscription subscription)
      {
        if (!assertIsListed(subscription))
        {
          return;
        }

        if (!subscription.isDisabled())
        {
          Log.e(TAG, "Subscription is already enabled");
          return;
        }

        subscription.setDisabled(false);
        Log.d(TAG, "Enabled subscription");
      }

      private void disable(final Subscription subscription)
      {
        if (!assertIsListed(subscription))
        {
          return;
        }

        if (!assertIsEnabled(subscription))
        {
          return;
        }

        subscription.setDisabled(true);
        Log.d(TAG, "Disabled subscription");
      }

      private void add(final Subscription subscription)
      {
        if (subscription.isListed())
        {
          if (subscription.isDisabled())
          {
            subscription.setDisabled(false);
            Log.d(TAG, "Enabled subscription");
          }
          else
          {
            Log.e(TAG, "Already listed and enabled subscription");
          }
        }
        else
        {
          subscription.addToList();
          Log.d(TAG, "Added subscription");
        }
      }

      private void list()
      {
        final FilterEngine filterEngine =
            AdblockHelper.get().getProvider().getEngine().getFilterEngine();
        final List<Subscription> subscriptions = filterEngine.getListedSubscriptions();
        for (final Subscription subscription : subscriptions)
        {
          try
          {
            Log.d(TAG, subscription.toString() + " is " +
                (subscription.isDisabled() ? "disabled" : "enabled"));
          }
          finally
          {
            subscription.dispose();
          }
        }
      }

      private boolean assertIsListed(final Subscription subscription)
      {
        if (!subscription.isListed())
        {
          Log.e(TAG, "Subscription is not listed");
          return false;
        }
        return true;
      }

      private boolean assertIsEnabled(final Subscription subscription)
      {
        if (subscription.isDisabled())
        {
          Log.e(TAG, "Subscription is disabled");
          return false;
        }
        return true;
      }
    };

    final IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_LIST);
    filter.addAction(ACTION_ADD);
    filter.addAction(ACTION_REMOVE);
    filter.addAction(ACTION_ENABLE);
    filter.addAction(ACTION_DISABLE);
    filter.addAction(ACTION_UPDATE);
    context.registerReceiver(receiver, filter);
  }

  public void dispose()
  {
    context.unregisterReceiver(receiver);
    context = null;
  }
}