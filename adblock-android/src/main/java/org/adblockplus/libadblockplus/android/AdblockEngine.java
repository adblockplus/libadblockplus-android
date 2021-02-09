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
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build.VERSION;

import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.FileSystem;
import org.adblockplus.libadblockplus.Filter;
import org.adblockplus.libadblockplus.FilterChangeCallback;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.FilterEngine.ContentType;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.LogSystem;
import org.adblockplus.libadblockplus.Platform;
import org.adblockplus.libadblockplus.Subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

public final class AdblockEngine
{

  public interface SettingsChangedListener
  {
    void onEnableStateChanged(boolean enabled);
  }

  /**
   * The result of `matches()` call
   */
  public enum MatchesResult
  {
    /**
     * Blocking filter is found
     */
    BLOCKED,

    /**
     * Exception filter is found
     */
    ALLOWLISTED,

    /**
     * No filter is found
     */
    NOT_FOUND,

    /**
     * Ad blocking is disabled
     */
    NOT_ENABLED
  }

  // default base path to store subscription files in android app
  public static final String BASE_PATH_DIRECTORY = "adblock";

  // force subscription update when engine will be enabled
  private static final String FORCE_SYNC_WHEN_ENABLED_PREF = "_force_sync_when_enabled";
  private static final String ENGINE_STORAGE_NAME = "abp-engine.pref";

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
  private SharedPreferences prefs;

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

  public org.adblockplus.libadblockplus.android.Subscription[] getRecommendedSubscriptions()
  {
    final List<Subscription> subscriptions = this.filterEngine.fetchAvailableSubscriptions();
    return convertJsSubscriptions(subscriptions);
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
    if (this.filterEngine != null)
    {

      if (this.filterChangeCallback != null)
      {
        this.filterEngine.removeFilterChangeCallback();
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

  private static org.adblockplus.libadblockplus.android.Subscription convertJsSubscription(
          final Subscription jsSubscription)
  {
    final org.adblockplus.libadblockplus.android.Subscription subscription =
      new org.adblockplus.libadblockplus.android.Subscription(
          jsSubscription.getTitle(),
          jsSubscription.getUrl(),
          jsSubscription.getLanguages(),
          jsSubscription.getHomepage(),
          jsSubscription.getAuthor());
    return subscription;
  }

  private static org.adblockplus.libadblockplus.android.Subscription[] convertJsSubscriptions(
    final List<Subscription> jsSubscriptions)
  {
    final org.adblockplus.libadblockplus.android.Subscription[] subscriptions =
      new org.adblockplus.libadblockplus.android.Subscription[jsSubscriptions.size()];

    for (int i = 0; i < subscriptions.length; i++)
    {
      subscriptions[i] = convertJsSubscription(jsSubscriptions.get(i));
    }

    return subscriptions;
  }

  public org.adblockplus.libadblockplus.android.Subscription[] getListedSubscriptions()
  {
    final List<Subscription> subscriptions = this.filterEngine.getListedSubscriptions();
    return convertJsSubscriptions(subscriptions);
  }

  public String getDocumentationLink()
  {
    final JsValue jsPref = this.filterEngine.getPref("documentation_link");
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
    for (final Subscription s : this.filterEngine.getListedSubscriptions())
    {
      s.removeFromList();
    }
  }

  public void setSubscription(final String url)
  {
    clearSubscriptions();

    final Subscription sub = this.filterEngine.getSubscription(url);
    if (sub != null)
    {
      sub.addToList();
    }
  }

  public void setSubscriptions(final Collection<String> urls)
  {
    final List<Subscription> currentSubscriptions = this.filterEngine.getListedSubscriptions();

    // remove the removed ones
    for (final Subscription eachCurrentSubscription : currentSubscriptions)
    {
      if (!urls.contains(eachCurrentSubscription.getUrl()))
      {
        eachCurrentSubscription.removeFromList();
      }
    }

    // add new subscriptions
    for (final String eachNewUrl : urls)
    {
      final Subscription eachNewSubscription = this.filterEngine.getSubscription(eachNewUrl);
      if (eachNewSubscription != null)
      {
        if (!eachNewSubscription.isListed())
        {
          eachNewSubscription.addToList();
        }
      }
    }
  }

  // This method is called when SingleInstanceEngineProvider configured to have filter engine
  // disabled by default. It will configure setting to force subscriptions to be updated
  // when engine will be enabled first time
  public void configureDisabledByDefault(final Context context)
  {
    setEnabled(false);
    ensurePrefs(context);

    if (!prefs.contains(FORCE_SYNC_WHEN_ENABLED_PREF))
    {
      saveShouldForceSyncWhenEnabled(true);
    }
  }

  private void ensurePrefs(final Context context)
  {
    if (prefs == null)
    {
      loadPrefs(context);
    }
  }

  private void saveShouldForceSyncWhenEnabled(final boolean force)
  {
    prefs.edit().putBoolean(FORCE_SYNC_WHEN_ENABLED_PREF, force).commit();
  }

  private void loadPrefs(final Context context)
  {
    prefs = context.getSharedPreferences(ENGINE_STORAGE_NAME, Context.MODE_PRIVATE);
  }

  public void setEnabled(final boolean enabled)
  {
    final boolean valueChanged = this.enabled != enabled;
    this.enabled = enabled;

    // Filter engine can be created disabled by default. In this case initial subscription sync
    // will fail and and once it will be enabled first synchronization will take place only
    // when retry timeout will trigger. In order to have something when enabling it first time
    // let us check pref forcing update.
    // See configureDisabledByDefault method for preference setup.

    if (enabled && valueChanged && prefs != null && shouldForceSyncWhenEnabled())
    {
      forceSync();
    }

    if (valueChanged)
    {
      synchronized(this)
      {
        for (final SettingsChangedListener listener : settingsChangedListeners)
        {
          listener.onEnableStateChanged(enabled);
        }
      }
    }
  }

  private void forceSync()
  {
    Timber.i("Force updating subscription filters");
    final List<Subscription> listed = filterEngine.getListedSubscriptions();

    for (final Subscription subscription : listed)
    {
      subscription.updateFilters();
    }

    saveShouldForceSyncWhenEnabled(false);
  }

  private boolean shouldForceSyncWhenEnabled()
  {
    return prefs.getBoolean(FORCE_SYNC_WHEN_ENABLED_PREF, false);
  }

  public boolean isEnabled()
  {
    return enabled;
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

  /**
   * Checks whether the resource at the supplied URL has a blocking filter.
   * For checking allowlisting filters use {@link AdblockEngine#isContentAllowlisted}.
   *
   * @param url URL of the resource
   * @param contentTypes Set of content types for requested resource
   * @param parent Immediate parent of the {@param url}.
   * @param siteKey Public key provided by the document, can be empty
   * @param specificOnly If `true` then we check only domain specific filters
   * @return {@link MatchesResult#NOT_ENABLED} if FilterEngine is not enabled,
   *         {@link MatchesResult#BLOCKED} when blocking filter was found or
   *         {@link MatchesResult#NOT_FOUND} when blocking filter was not found.
   */
  public MatchesResult matches(final String url, final Set<ContentType> contentTypes,
                               final String parent, final String siteKey,
                               final boolean specificOnly)
  {
    if (!enabled)
    {
      return MatchesResult.NOT_ENABLED;
    }

    final Filter filter = this.filterEngine.matches(url, contentTypes, parent, siteKey,
        specificOnly);

    if (filter == null)
    {
      return MatchesResult.NOT_FOUND;
    }

    Timber.d("Found filter `%s` for url `%s`", filter.getRaw(), url);

    return filter.getType() == Filter.Type.BLOCKING
        ? MatchesResult.BLOCKED
        : MatchesResult.NOT_FOUND;
  }

  /**
   * Add whitelisting filter for a given domain.
   *
   * @param domain Domain to be added for whitelisting
   */
  public void addDomainWhitelistingFilter(final String domain)
  {
    final Filter filter = Utils.createDomainAllowlistingFilter(filterEngine, domain);
    this.filterEngine.addFilter(filter);
  }

  /**
   * Checks whether the resource at the supplied URL is allowlisted.
   *
   * @param url URL of the resource
   * @param contentTypes Set of content types for requested resource
   * @param referrerChain Chain of URLs requesting the resource
   * @param siteKey Public key provided by the document, can be empty
   * @return `true` if the URL is allowlisted
   */
  public boolean isContentAllowlisted(final String url,
                                      final Set<ContentType> contentTypes,
                                      final List<String> referrerChain,
                                      final String siteKey)
  {
    return this.filterEngine.isContentAllowlisted(url, contentTypes, referrerChain, siteKey);
  }

  public String getElementHidingStyleSheet(
      final String url,
      final String domain,
      final List<String> referrerChain,
      final String sitekey,
      final boolean specificOnly)
  {
    /*
     * Issue 3364 (https://issues.adblockplus.org/ticket/3364) introduced the
     * feature to re-enabled element hiding.
     *
     * Nothing changes for Adblock Plus for Android, as `this.elemhideEnabled`
     * is `false`, which results in an empty list being returned and converted
     * into a `(String[])null` in AdblockPlus.java, which is the only place
     * this function here is called from Adblock Plus for Android.
     *
     * If element hiding is enabled, then this function now first checks for
     * possible allowlisting of either the document or element hiding for
     * the given URL and returns an empty list if so. This is needed to
     * ensure correct functioning of e.g. acceptable ads.
     */
    if (!this.enabled
        || !this.elemhideEnabled
        || this.isContentAllowlisted(url,
            FilterEngine.ContentType.maskOf(ContentType.DOCUMENT), referrerChain, sitekey)
        || this.isContentAllowlisted(url,
            FilterEngine.ContentType.maskOf(ContentType.ELEMHIDE), referrerChain, sitekey))
    {
      return "";
    }

    return this.filterEngine.getElementHidingStyleSheet(domain, specificOnly);
  }

  public List<FilterEngine.EmulationSelector> getElementHidingEmulationSelectors(
      final String url,
      final String domain,
      final List<String> referrerChain,
      final String sitekey)
  {
    if (!this.enabled
        || !this.elemhideEnabled
        || this.isContentAllowlisted(url,
            FilterEngine.ContentType.maskOf(ContentType.DOCUMENT), referrerChain, sitekey)
        || this.isContentAllowlisted(url,
            FilterEngine.ContentType.maskOf(ContentType.ELEMHIDE), referrerChain, sitekey))
    {
      return new ArrayList<>();
    }
    return this.filterEngine.getElementHidingEmulationSelectors(domain);
  }

  public FilterEngine getFilterEngine()
  {
    return this.filterEngine;
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
    final Filter filter = Utils.createDomainAllowlistingFilter(filterEngine, domain);
    this.filterEngine.addFilter(filter);
  }

  /**
   * Remove allowlisting filter for given domain.
   * @param domain Domain to be removed from allowlisting
   */
  public void removeDomainAllowlistingFilter(final String domain)
  {
    final Filter filter = Utils.createDomainAllowlistingFilter(filterEngine, domain);
    this.filterEngine.removeFilter(filter);
  }

  /**
   * Builds Adblock engine piece-by-pieece
   */
  public static class Builder implements Factory
  {
    private Context context;
    private Map<String, Integer> urlToResourceIdMap;
    private boolean forceUpdatePreloadedSubscriptions = true;
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
      this.engine.configureDisabledByDefault(context);
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

      engine.httpClient = new AndroidHttpClientEngineStateWrapper(engine.httpClient, engine);
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

      // force update filters if moved from "disabled by default" state to regular state,
      // see https://jira.eyeo.com/browse/DP-1558
      if (engine.isEnabled())
      {
        engine.ensurePrefs(context);
        if (engine.shouldForceSyncWhenEnabled())
        {
          engine.saveShouldForceSyncWhenEnabled(false);
          engine.forceSync();
        }
      }

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
      engine.platform.setUpFilterEngine(isAllowedConnectionCallback);
      engine.filterEngine = engine.platform.getFilterEngine();
    }
  }
}
