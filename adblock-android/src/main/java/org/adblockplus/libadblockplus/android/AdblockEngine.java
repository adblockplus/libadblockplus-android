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

import org.adblockplus.ContentType;
import org.adblockplus.EmulationSelector;
import org.adblockplus.Filter;
import org.adblockplus.MatchesResult;
import org.adblockplus.Subscription;
import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.FileSystem;
import org.adblockplus.libadblockplus.FilterChangeCallback;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.LogSystem;
import org.adblockplus.libadblockplus.Platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

public final class AdblockEngine implements org.adblockplus.AdblockEngine
{

  public interface SettingsChangedListener
  {
    void onEnableStateChanged(boolean enabled);
  }

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
  private volatile FilterChangeCallback filterChangeCallback;
  private volatile boolean elemhideEnabled = true;
  private volatile boolean enabled = true;
  private final Set<SettingsChangedListener> settingsChangedListeners = new HashSet<>();

  public synchronized AdblockEngine addSettingsChangedListener(final SettingsChangedListener listener)
  {
    if (listener == null)
    {
      throw new IllegalArgumentException("SettingsChangedListener cannot be null");
    }
    settingsChangedListeners.add(listener);
    return this;
  }

  public synchronized AdblockEngine removeSettingsChangedListener(final SettingsChangedListener listener)
  {
    settingsChangedListeners.remove(listener);
    return this;
  }

  @Override
  public String getElementHidingStyleSheet(final String domain, final boolean specificOnly)
  {
    if (!enabled || !elemhideEnabled || domain.isEmpty())
    {
      return "";
    }

    return filterEngine.getElementHidingStyleSheet(domain, specificOnly);
  }

  @Override
  public List<EmulationSelector> getElementHidingEmulationSelectors(final String domain)
  {
    if (!enabled || !elemhideEnabled || domain.isEmpty())
    {
      return new ArrayList<>();
    }

    return filterEngine.getElementHidingEmulationSelectors(domain);
  }

  @Override
  public boolean isContentAllowlisted(final String url, final Set<ContentType> contentTypes,
                                      final List<String> referrerChain, final String siteKey)
  {
    return filterEngine.isContentAllowlisted(url, contentTypes, referrerChain, siteKey);
  }

  @Override
  public MatchesResult matches(final String url, final Set<ContentType> contentTypes, final String parent,
                               final String siteKey, final boolean domainSpecificOnly)
  {
    if (!enabled)
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
  public Subscription getSubscription(final String url)
  {
    return filterEngine.getSubscription(url);
  }

  @Override
  public Filter getFilterFromText(final String text)
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

  public List<Subscription> getRecommendedSubscriptions()
  {
    return filterEngine.fetchAvailableSubscriptions();
  }

  public List<Subscription> getListedSubscriptions()
  {
    return filterEngine.getListedSubscriptions();
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

      if (this.filterChangeCallback != null)
      {
        filterEngine.removeFilterChangeCallback();
      }

      this.platform.dispose();
      this.platform = null;
    }

    // callbacks then
    if (this.filterChangeCallback != null)
    {
      this.filterChangeCallback.dispose();
      this.filterChangeCallback = null;
    }
  }

  public boolean isElemhideEnabled()
  {
    return this.elemhideEnabled;
  }

  public String getDocumentationLink()
  {
    final JsValue jsPref = filterEngine.getPref("documentation_link");
    try
    {
      return jsPref.toString();
    }
    finally
    {
      jsPref.dispose();
    }
  }

  public void clearSubscriptions()
  {
    for (final Subscription s : filterEngine.getListedSubscriptions())
    {
      filterEngine.removeSubscription(s);
    }
  }

  public void setSubscriptions(final Collection<String> urls)
  {
    final List<Subscription> currentSubscriptions = filterEngine.getListedSubscriptions();

    // remove the removed ones
    for (final Subscription eachCurrentSubscription : currentSubscriptions)
    {
      if (!urls.contains(eachCurrentSubscription.url))
      {
        filterEngine.removeSubscription(eachCurrentSubscription);
      }
    }

    // add new subscriptions
    for (final String eachNewUrl : urls)
    {
      final Subscription eachNewSubscription = filterEngine.getSubscription(eachNewUrl);
      if (eachNewSubscription != null)
      {
        if (!filterEngine.getListedSubscriptions().contains(eachNewSubscription))
        {
          filterEngine.addSubscription(eachNewSubscription);
        }
      }
    }
  }

  public void setEnabled(final boolean enabled)
  {
    // if the same no need to change anything
    if (enabled == this.enabled)
    {
      return;
    }
    this.enabled = enabled;
    if (filterEngine != null)
    {
      filterEngine.setEnabled(enabled);
    }
    synchronized(this)
    {
      for (final SettingsChangedListener listener : settingsChangedListeners)
      {
        listener.onEnableStateChanged(enabled);
      }
    }
  }

  public boolean isEnabled()
  {
    // should be in sync with filterEngine.isEnabled()
    return this.enabled;
  }

  public String getAcceptableAdsSubscriptionURL()
  {
    return filterEngine.getAcceptableAdsSubscriptionURL();
  }

  public boolean isAcceptableAdsEnabled()
  {
    return filterEngine.isAcceptableAdsEnabled();
  }

  public void setAcceptableAdsEnabled(final boolean enabled)
  {
    filterEngine.setAcceptableAdsEnabled(enabled);
  }

  public FilterEngine getFilterEngine()
  {
    return filterEngine;
  }

  /**
   * Init allowlisting filters.
   * @param domains List of domains to be allowlisting
   */
  public void initAllowlistedDomains(final List<String> domains)
  {
    if (domains != null)
    {
      for (final String domain : domains)
      {
        addDomainAllowlistingFilter(domain);
      }
    }
  }

  /**
   * Add allowlisting filter for a given domain.
   * @param domain Domain to be added for allowlisting
   */
  public void addDomainAllowlistingFilter(final String domain)
  {
    final Filter filter = Utils.createDomainAllowlistingFilter(this, domain);
    filterEngine.addFilter(filter);
  }

  /**
   * Remove allowlisting filter for given domain.
   * @param domain Domain to be removed from allowlisting
   */
  public void removeDomainAllowlistingFilter(final String domain)
  {
    final Filter filter = Utils.createDomainAllowlistingFilter(this, domain);
    filterEngine.removeFilter(filter);
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
      engine.elemhideEnabled = true;

      // we can't create JsEngine and FilterEngine right now as it starts to download subscriptions
      // and requests (AndroidHttpClient and probably wrappers) are not specified yet
      this.context = context;
      this.appInfo = appInfo;
      this.basePath = basePath;
    }

    public Builder setDisableByDefault()
    {
      this.enabledByDefault = false;
      return this;
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

    public Builder setFilterChangeCallback(final FilterChangeCallback callback)
    {
      engine.filterChangeCallback = callback;
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

    private void initCallbacks()
    {
      if (engine.filterChangeCallback != null)
      {
        engine.filterEngine.setFilterChangeCallback(engine.filterChangeCallback);
      }
    }

    public AdblockEngine build()
    {
      initRequests();

      // httpClient should be ready to be used passed right after JsEngine is created
      createEngines();

      initCallbacks();

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
      engine.enabled = enabledByDefault; // to keep it in sync
      engine.filterEngine = engine.platform.getFilterEngine();
    }
  }
}
