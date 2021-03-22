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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build.VERSION;

import org.adblockplus.AdblockEngineSettings;
import org.adblockplus.ConnectionType;
import org.adblockplus.ContentType;
import org.adblockplus.EmulationSelector;
import org.adblockplus.Filter;
import org.adblockplus.MatchesResult;
import org.adblockplus.Subscription;
import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.FileSystem;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;
import org.adblockplus.libadblockplus.LogSystem;
import org.adblockplus.libadblockplus.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

public final class AdblockEngine implements org.adblockplus.AdblockEngine
{
  // default base path to store subscription files in android app
  public static final String BASE_PATH_DIRECTORY = "adblock";
  /*
   * The fields below are volatile because:
   *
   * I encountered JNI related bugs/crashes caused by JNI backed Java objects. It seemed that under
   * certain conditions the objects were optimized away which resulted in crashes when trying to
   * release the object, sometimes even on access.
   *
   * The only solution that really worked was to declare the variables holding the references
   * volatile, this seems to prevent the JNI from 'optimizing away' those objects (as a volatile
   * variable might be changed at any time from any thread).
   */
  private volatile Platform platform;
  private volatile FilterEngine filterEngine;
  private volatile LogSystem logSystem;
  private volatile FileSystem fileSystem;
  private volatile HttpClient httpClient;
  private AtomicBoolean enabled = new AtomicBoolean(true);

  private AdblockEngineSettings adblockEngineSettings = new AdblockEngineSettings()
  {
    private final Set<EnableStateChangedListener> enableStateChangedListeners = new HashSet<>();
    private final Set<FiltersChangedListener> filtersChangedListeners = new HashSet<>();
    private final Set<SubscriptionsChangedListener> subscriptionsChangedListeners = new HashSet<>();

    class EditOperationIml implements EditOperation
    {
      private Boolean enabled = null;
      private Boolean aaEnabled = null;
      private ConnectionType connectionType = null;
      private boolean connectionTypeSet = false; //connectionType can be null
      private Set<Subscription> addSubscriptionSet = new HashSet<>();
      private Set<Subscription> removeSubscriptionSet = new HashSet<>();
      private boolean clearSubscriptions = false;
      private Set<Filter> addCustomFilterSet = new HashSet<>();
      private Set<Filter> removeCustomFilterSet = new HashSet<>();
      private boolean clearCustomFilters = false;

      private void resetAll()
      {
        enabled = null;
        aaEnabled = null;
        connectionType = null;
        connectionTypeSet = true;
        addSubscriptionSet.clear();
        removeSubscriptionSet.clear();
        clearSubscriptions = false;
        addCustomFilterSet.clear();
        removeCustomFilterSet.clear();
        clearCustomFilters = false;
      }

      @Override
      @NotNull
      public EditOperation setEnabled(final boolean enabled)
      {
        this.enabled = enabled;
        return this;
      }

      @Override
      @NotNull
      public EditOperation setAcceptableAdsEnabled(final boolean enabled)
      {
        aaEnabled = enabled;
        return this;
      }

      @Override
      @NotNull
      public EditOperation setAllowedConnectionType(@Nullable final ConnectionType connectionType)
      {
        this.connectionType = connectionType;
        connectionTypeSet = true;
        return this;
      }

      @Override
      @NotNull
      public EditOperation addSubscription(@NotNull final Subscription subscription)
      {
        addSubscriptionSet.add(subscription);
        return this;
      }

      @Override
      @NotNull
      public EditOperation removeSubscription(@NotNull final Subscription subscription)
      {
        removeSubscriptionSet.add(subscription);
        return this;
      }

      @Override
      @NotNull
      public EditOperation addAllSubscriptions(@NotNull final Iterable<Subscription> subscriptions)
      {
        for (final Subscription subscription : subscriptions)
        {
          addSubscriptionSet.add(subscription);
        }
        return this;
      }

      @Override
      @NotNull
      public EditOperation clearSubscriptions()
      {
        clearSubscriptions = true;
        return this;
      }

      @Override
      @NotNull
      public EditOperation addCustomFilter(@NotNull final Filter filter)
      {
        addCustomFilterSet.add(filter);
        return this;
      }

      @Override
      @NotNull
      public EditOperation removeCustomFilter(@NotNull final Filter filter)
      {
        removeCustomFilterSet.add(filter);
        return this;
      }

      @Override
      @NotNull
      public EditOperation clearCustomFilters()
      {
        clearCustomFilters = true;
        return this;
      }

      @Override
      public synchronized void save()
      {
        // Handle Subscriptions
        final Map<Subscription, SubscriptionsChangedListener.SubscriptionEvent> subscriptionToEventMap
          = new HashMap<>();
        final Set<Subscription> finalRemoveSubscriptionSet = clearSubscriptions ?
          new HashSet(getListedSubscriptions()) : removeSubscriptionSet;
        for (final Subscription subscription : finalRemoveSubscriptionSet)
        {
          filterEngine.removeSubscription(subscription);
          subscriptionToEventMap.put(subscription, SubscriptionsChangedListener.SubscriptionEvent.SUBSCRIPTION_REMOVED);
        }
        for (final Subscription subscription : addSubscriptionSet)
        {
          filterEngine.addSubscription(subscription);
          subscriptionToEventMap.put(subscription,
            SubscriptionsChangedListener.SubscriptionEvent.SUBSCRIPTION_ADDED);
        }
        // Send notification
        if (!subscriptionToEventMap.isEmpty())
        {
          for (final SubscriptionsChangedListener subscriptionsChangedListener : subscriptionsChangedListeners)
          {
            subscriptionsChangedListener.onSubscriptionEvent(subscriptionToEventMap);
          }
          subscriptionToEventMap.clear();
        }

        // Handle Filters
        final Map<Filter, FiltersChangedListener.FilterEvent> filterToEventMap = new HashMap<>();
        final Set<Filter> finalRemoveCustomFilterSet = clearCustomFilters ?
          new HashSet(getListedFilters()) : removeCustomFilterSet;
        for (final Filter filter : finalRemoveCustomFilterSet)
        {
          filterEngine.removeFilter(filter);
          filterToEventMap.put(filter, FiltersChangedListener.FilterEvent.FILTER_REMOVED);
        }
        for (final Filter filter : addCustomFilterSet)
        {
          filterEngine.addFilter(filter);
          filterToEventMap.put(filter, FiltersChangedListener.FilterEvent.FILTER_ADDED);
        }
        // Send notification
        if (!filterToEventMap.isEmpty())
        {
          for (final FiltersChangedListener filtersChangedListener : filtersChangedListeners)
          {
            filtersChangedListener.onFilterEvent(filterToEventMap);
          }
          filterToEventMap.clear();
        }

        // Handle all the rest
        if (aaEnabled != null)
        {
          filterEngine.setAcceptableAdsEnabled(aaEnabled);
          for (final EnableStateChangedListener enableStateChangedListener : enableStateChangedListeners)
          {
            enableStateChangedListener.onAcceptableAdsEnableStateChanged(aaEnabled);
          }
        }

        if (connectionTypeSet)
        {
          filterEngine.setAllowedConnectionType(connectionType == null ? null : connectionType.getValue());
        }

        if (enabled != null)
        {
          AdblockEngine.this.enabled.set(enabled);
          filterEngine.setEnabled(enabled);
          for (final EnableStateChangedListener enableStateChangedListener : enableStateChangedListeners)
          {
            enableStateChangedListener.onAdblockEngineEnableStateChanged(enabled);
          }
        }

        resetAll();
      }
    }

    @Override
    @NotNull
    public AdblockEngineSettings.EditOperation edit()
    {
      return new EditOperationIml();
    }

    @Override
    public boolean isEnabled()
    {
      return enabled.get();
    }

    @Override
    public boolean isAcceptableAdsEnabled()
    {
      return filterEngine.isAcceptableAdsEnabled();
    }

    @Override
    @Nullable
    public ConnectionType getAllowedConnectionType()
    {
      final String connectionType = filterEngine.getAllowedConnectionType();
      return connectionType == null ? null : ConnectionType.findByValue(connectionType);
    }

    @Override
    @NotNull
    public List<Subscription> getDefaultSubscriptions()
    {
      return filterEngine.fetchAvailableSubscriptions();
    }

    @Override
    @NotNull
    public List<Subscription> getListedSubscriptions()
    {
      return filterEngine.getListedSubscriptions();
    }

    @Override
    public boolean isListed(@NotNull final Subscription subscription)
    {
      return getListedSubscriptions().contains(subscription);
    }

    @Override
    @NotNull
    public List<Filter> getListedFilters()
    {
      return filterEngine.getListedFilters();
    }

    @Override
    public boolean isListed(@NotNull final Filter filter)
    {
      // This is a bit expensive if there are many custom filters. A note to change if Core will offer other way.
      return getListedFilters().contains(filter);
    }

    @Override
    @NotNull
    public synchronized AdblockEngineSettings addEnableStateChangedListener(
      @NotNull final EnableStateChangedListener listener)
    {
      enableStateChangedListeners.add(listener);
      return this;
    }

    @Override
    @NotNull
    public synchronized AdblockEngineSettings removeEnableStateChangedListener(
      @NotNull final EnableStateChangedListener listener)
    {
      enableStateChangedListeners.remove(listener);
      return this;
    }

    @Override
    @NotNull
    public synchronized AdblockEngineSettings addFiltersChangedListener(
      @NotNull final FiltersChangedListener listener)
    {
      filtersChangedListeners.add(listener);
      return this;
    }

    @Override
    @NotNull
    public synchronized AdblockEngineSettings removeFiltersChangedListener(
      @NotNull final FiltersChangedListener listener)
    {
      filtersChangedListeners.remove(listener);
      return this;
    }

    @Override
    @NotNull
    public synchronized AdblockEngineSettings addSubscriptionsChangedListener(
      final SubscriptionsChangedListener listener)
    {
      subscriptionsChangedListeners.add(listener);
      return this;
    }

    @Override
    @NotNull
    public synchronized AdblockEngineSettings removeSubscriptionsChangedListener(
      @NotNull final SubscriptionsChangedListener listener)
    {
      subscriptionsChangedListeners.remove(listener);
      return this;
    }
  };

  @Override
  @NotNull
  public AdblockEngineSettings settings()
  {
    return adblockEngineSettings;
  }

  @Override
  @NotNull
  public String getElementHidingStyleSheet(@NotNull final String domain, final boolean specificOnly)
  {
    if (!enabled.get() || domain.isEmpty())
    {
      return "";
    }

    return filterEngine.getElementHidingStyleSheet(domain, specificOnly);
  }

  @Override
  @NotNull
  public List<EmulationSelector> getElementHidingEmulationSelectors(@NotNull final String domain)
  {
    if (!enabled.get() || domain.isEmpty())
    {
      return new ArrayList<>();
    }

    return filterEngine.getElementHidingEmulationSelectors(domain);
  }

  @Override
  @NotNull
  public boolean isContentAllowlisted(@NotNull final String url, @NotNull final Set<ContentType> contentTypes,
                                      @NotNull final List<String> referrerChain, @NotNull final String siteKey)
  {
    return filterEngine.isContentAllowlisted(url, contentTypes, referrerChain, siteKey);
  }

  @Override
  @NotNull
  public MatchesResult matches(@NotNull final String url, @NotNull final Set<ContentType> contentTypes,
                               @NotNull final String parent, @NotNull final String siteKey,
                               final boolean domainSpecificOnly)
  {
    if (!enabled.get())
    {
      return MatchesResult.NOT_ENABLED;
    }

    final Filter filter = filterEngine.matches(url, contentTypes, parent, siteKey, domainSpecificOnly);

    if (filter == null)
    {
      return MatchesResult.NOT_FOUND;
    }

    Timber.d("Found filter `%s` for url `%s`", filter.text, url);

    return filter.type == Filter.Type.BLOCKING
      ? MatchesResult.BLOCKED
      : MatchesResult.NOT_FOUND;
  }

  @Override
  @NotNull
  public Subscription getSubscription(@NotNull final String url)
  {
    return filterEngine.getSubscription(url);
  }

  @Override
  @NotNull
  public Filter getFilterFromText(@NotNull final String text)
  {
    return filterEngine.getFilterFromText(text);
  }

  public static AppInfo generateAppInfo(final Context context,
                                        final String application,
                                        final String applicationVersion)
  {
    final String sdkVersion = String.valueOf(VERSION.SDK_INT);
    String locale = Locale.getDefault().toString().replace('_', '-');
    if (locale.startsWith("iw-"))
    {
      locale = "he" + locale.substring(2);
    }

    final AppInfo.Builder builder =
        AppInfo
            .builder()
            .setApplicationVersion(sdkVersion)
            .setLocale(locale);

    if (application != null)
    {
      builder.setApplication(application);
    }

    if (applicationVersion != null)
    {
      builder.setApplicationVersion(applicationVersion);
    }

    return builder.build();
  }

  public static AppInfo generateAppInfo(final Context context)
  {
    try
    {
      final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      final String application = context.getPackageName();
      final String applicationVersion = packageInfo.versionName;

      return generateAppInfo(context, application, applicationVersion);
    }
    catch (final PackageManager.NameNotFoundException e)
    {
      throw new RuntimeException(e);
    }
  }

  /**
   * Builds Adblock engine
   */
  public interface Factory
  {
    AdblockEngine build();
  }

  /**
   * Calls the platform's garbage collector
   * Assuming the default implementation, V8 garbage collector will be called
   */
  public void onLowMemory()
  {
    if (platform != null && platform.getJsEngine() != null)
    {
      platform.getJsEngine().onLowMemory();
    }
  }

  public static Builder builder(final Context context,
                                final AppInfo appInfo,
                                final String basePath)
  {
    return new Builder(context, appInfo, basePath);
  }

  public static Builder builder(final Context context, final String basePath)
  {
    final AppInfo appInfo = AdblockEngine.generateAppInfo(context);
    final ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    final IsAllowedConnectionCallback isAllowedConnectionCallback =
        new IsAllowedConnectionCallbackImpl(connectivityManager);
    return builder(context, appInfo, basePath)
        .setIsAllowedConnectionCallback(isAllowedConnectionCallback);
  }

  public void dispose()
  {
    Timber.w("Dispose");

    // engines first
    if (filterEngine != null)
    {

      this.platform.dispose();
      this.platform = null;
    }
  }

  public FilterEngine getFilterEngine()
  {
    return filterEngine;
  }

  /**
   * Builds Adblock engine piece-by-pieece
   */
  public static class Builder implements Factory
  {
    private Context context;
    private Map<String, Integer> urlToResourceIdMap;
    private boolean forceUpdatePreloadedSubscriptions = true;
    private boolean enabledByDefault = true;
    private AndroidHttpClientResourceWrapper.Storage resourceStorage;
    private HttpClient androidHttpClient;
    private final AppInfo appInfo;
    private final String basePath;
    private IsAllowedConnectionCallback isAllowedConnectionCallback;
    private Long v8IsolateProviderPtr;

    private final AdblockEngine engine;

    protected Builder(final Context context,
                      final AppInfo appInfo,
                      final String basePath)
    {
      engine = new AdblockEngine();

      // we can't create JsEngine and FilterEngine right now as it starts to download subscriptions
      // and requests (AndroidHttpClient and probably wrappers) are not specified yet
      this.context = context;
      this.appInfo = appInfo;
      this.basePath = basePath;
    }

    public Builder setDisableByDefault()
    {
      this.engine.configureDisabledByDefault(context);
      return this;
    }

    public boolean getDisableByDefault()
    {
      return !enabledByDefault;
    }

    public Builder enableElementHiding(final boolean enable)
    {
      engine.elemhideEnabled = enable;
      return this;
    }

    public Builder setHttpClient(final HttpClient httpClient)
    {
      this.androidHttpClient = httpClient;
      return this;
    }

    public Builder preloadSubscriptions(final Context context,
                                        final Map<String, Integer> urlToResourceIdMap,
                                        final AndroidHttpClientResourceWrapper.Storage storage)
    {
      this.context = context;
      this.urlToResourceIdMap = urlToResourceIdMap;
      this.resourceStorage = storage;
      return this;
    }

    public Builder setForceUpdatePreloadedSubscriptions(final boolean forceUpdate)
    {
      this.forceUpdatePreloadedSubscriptions = forceUpdate;
      return this;
    }

    public Builder setIsAllowedConnectionCallback(final IsAllowedConnectionCallback callback)
    {
      this.isAllowedConnectionCallback = callback;
      return this;
    }

    public Builder useV8IsolateProvider(final long v8IsolateProviderPtr)
    {
      this.v8IsolateProviderPtr = v8IsolateProviderPtr;
      return this;
    }

    private void initRequests()
    {
      if (androidHttpClient == null)
      {
        androidHttpClient = new AndroidHttpClient(true);
      }
      engine.httpClient = androidHttpClient;

      if (urlToResourceIdMap != null)
      {
        final AndroidHttpClientResourceWrapper wrapper = new AndroidHttpClientResourceWrapper(
            context, engine.httpClient, urlToResourceIdMap, resourceStorage);

        if (forceUpdatePreloadedSubscriptions)
        {
          wrapper.setListener(new AndroidHttpClientResourceWrapper.Listener()
          {
            @Override
            public void onIntercepted(final String url, final int resourceId)
            {
              Timber.d("Force subscription update for intercepted URL %s", url);
              if (engine.filterEngine != null)
              {
                engine.filterEngine.updateFiltersAsync(url);
              }
            }
          });
        }

        engine.httpClient = wrapper;
      }

    }

    public AdblockEngine build()
    {
      initRequests();

      // httpClient should be ready to be used passed right after JsEngine is created
      createEngines();

      return engine;
    }

    private void createEngines()
    {
      engine.logSystem = new TimberLogSystem();
      engine.fileSystem = null; // using default
      engine.platform = new Platform(engine.logSystem, engine.fileSystem, engine.httpClient, basePath);
      if (v8IsolateProviderPtr != null)
      {
        engine.platform.setUpJsEngine(appInfo, v8IsolateProviderPtr);
      }
      else
      {
        engine.platform.setUpJsEngine(appInfo);
      }
      engine.platform.setUpFilterEngine(isAllowedConnectionCallback, enabledByDefault);
      engine.filterEngine = engine.platform.getFilterEngine();
    }
  }
}
