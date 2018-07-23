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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.Filter;
import org.adblockplus.libadblockplus.FilterChangeCallback;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.FilterEngine.ContentType;
import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.LogSystem;
import org.adblockplus.libadblockplus.Platform;
import org.adblockplus.libadblockplus.ShowNotificationCallback;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.UpdateAvailableCallback;
import org.adblockplus.libadblockplus.UpdateCheckDoneCallback;
import org.adblockplus.libadblockplus.WebRequest;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.util.Log;

public final class AdblockEngine
{
  // default base path to store subscription files in android app
  public static final String BASE_PATH_DIRECTORY = "adblock";

  private static final String TAG = Utils.getTag(AdblockEngine.class);

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
  private volatile WebRequest webRequest;
  private volatile UpdateAvailableCallback updateAvailableCallback;
  private volatile UpdateCheckDoneCallback updateCheckDoneCallback;
  private volatile FilterChangeCallback filterChangeCallback;
  private volatile ShowNotificationCallback showNotificationCallback;
  private volatile boolean elemhideEnabled;
  private volatile boolean enabled = true;
  private volatile List<String> whitelistedDomains;

  public static AppInfo generateAppInfo(final Context context, boolean developmentBuild,
                                        String application, String applicationVersion)
  {
    final String sdkVersion = String.valueOf(VERSION.SDK_INT);
    String locale = Locale.getDefault().toString().replace('_', '-');
    if (locale.startsWith("iw-"))
    {
      locale = "he" + locale.substring(2);
    }

    AppInfo.Builder builder =
      AppInfo
        .builder()
        .setApplicationVersion(sdkVersion)
        .setLocale(locale)
        .setDevelopmentBuild(developmentBuild);

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

  public static AppInfo generateAppInfo(final Context context, boolean developmentBuild)
  {
    try
    {
      PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      String application = context.getPackageName();
      String applicationVersion = packageInfo.versionName;

      return generateAppInfo(context, developmentBuild, application, applicationVersion);
    }
    catch (PackageManager.NameNotFoundException e)
    {
      throw new RuntimeException(e);
    }
  }

  /**
   * Builds Adblock engine
   */
  public static class Builder
  {
    private Context context;
    private Map<String, Integer> urlToResourceIdMap;
    private AndroidWebRequestResourceWrapper.Storage resourceStorage;
    private AndroidWebRequest androidWebRequest;
    private AppInfo appInfo;
    private String basePath;
    private IsAllowedConnectionCallback isAllowedConnectionCallback;
    private Long v8IsolateProviderPtr;

    private AdblockEngine engine;

    protected Builder(final AppInfo appInfo, final String basePath)
    {
      engine = new AdblockEngine();
      engine.elemhideEnabled = true;

      // we can't create JsEngine and FilterEngine right now as it starts to download subscriptions
      // and requests (AndroidWebRequest and probbaly wrappers) are not specified yet
      this.appInfo = appInfo;
      this.basePath = basePath;
    }

    public Builder enableElementHiding(boolean enable)
    {
      engine.elemhideEnabled = enable;
      return this;
    }

    public Builder preloadSubscriptions(Context context,
                                        Map<String, Integer> urlToResourceIdMap,
                                        AndroidWebRequestResourceWrapper.Storage storage)
    {
      this.context = context;
      this.urlToResourceIdMap = urlToResourceIdMap;
      this.resourceStorage = storage;
      return this;
    }

    public Builder setIsAllowedConnectionCallback(IsAllowedConnectionCallback callback)
    {
      this.isAllowedConnectionCallback = callback;
      return this;
    }

    public Builder useV8IsolateProvider(long v8IsolateProviderPtr)
    {
      this.v8IsolateProviderPtr = v8IsolateProviderPtr;
      return this;
    }

    public Builder setUpdateAvailableCallback(UpdateAvailableCallback callback)
    {
      engine.updateAvailableCallback = callback;
      return this;
    }

    public Builder setUpdateCheckDoneCallback(UpdateCheckDoneCallback callback)
    {
      engine.updateCheckDoneCallback = callback;
      return this;
    }

    public Builder setShowNotificationCallback(ShowNotificationCallback callback)
    {
      engine.showNotificationCallback = callback;
      return this;
    }

    public Builder setFilterChangeCallback(FilterChangeCallback callback)
    {
      engine.filterChangeCallback = callback;
      return this;
    }

    private void initRequests()
    {
      androidWebRequest = new AndroidWebRequest(engine.elemhideEnabled, true);
      engine.webRequest = androidWebRequest;

      if (urlToResourceIdMap != null)
      {
        AndroidWebRequestResourceWrapper wrapper = new AndroidWebRequestResourceWrapper(
          context, engine.webRequest, urlToResourceIdMap, resourceStorage);
        wrapper.setListener(new AndroidWebRequestResourceWrapper.Listener()
        {
          @Override
          public void onIntercepted(String url, int resourceId)
          {
            Log.d(TAG, "Force subscription update for intercepted URL " + url);
            if (engine.filterEngine != null)
            {
              engine.filterEngine.updateFiltersAsync(url);
            }
          }
        });

        engine.webRequest = wrapper;
      }
    }

    private void initCallbacks()
    {
      if (engine.updateAvailableCallback != null)
      {
        engine.filterEngine.setUpdateAvailableCallback(engine.updateAvailableCallback);
      }

      if (engine.showNotificationCallback != null)
      {
        engine.filterEngine.setShowNotificationCallback(engine.showNotificationCallback);
      }

      if (engine.filterChangeCallback != null)
      {
        engine.filterEngine.setFilterChangeCallback(engine.filterChangeCallback);
      }
    }

    public AdblockEngine build()
    {
      initRequests();

      // webRequest should be ready to be used passed right after JsEngine is created
      createEngines();

      initCallbacks();

      if (!engine.elemhideEnabled)
      {
        androidWebRequest.updateSubscriptionURLs(engine.filterEngine);
      }

      return engine;
    }

    private void createEngines()
    {
      engine.logSystem = new AndroidLogSystem();
      engine.platform = new Platform(engine.logSystem, engine.webRequest, basePath);
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

  public static Builder builder(AppInfo appInfo, String basePath)
  {
    return new Builder(appInfo, basePath);
  }

  public void dispose()
  {
    Log.w(TAG, "Dispose");

    // engines first
    if (this.filterEngine != null)
    {
      if (this.updateAvailableCallback != null)
      {
        this.filterEngine.removeUpdateAvailableCallback();
      }

      if (this.filterChangeCallback != null)
      {
        this.filterEngine.removeFilterChangeCallback();
      }

      if (this.showNotificationCallback != null)
      {
        this.filterEngine.removeShowNotificationCallback();
      }

      this.platform.dispose();
      this.platform = null;
    }

    // callbacks then
    if (this.updateAvailableCallback != null)
    {
      this.updateAvailableCallback.dispose();
      this.updateAvailableCallback = null;
    }

    if (this.filterChangeCallback != null)
    {
      this.filterChangeCallback.dispose();
      this.filterChangeCallback = null;
    }

    if (this.showNotificationCallback != null)
    {
      this.showNotificationCallback.dispose();
      this.showNotificationCallback = null;
    }
  }

  public boolean isFirstRun()
  {
    return this.filterEngine.isFirstRun();
  }

  public boolean isElemhideEnabled()
  {
    return this.elemhideEnabled;
  }

  private static org.adblockplus.libadblockplus.android.Subscription convertJsSubscription(final Subscription jsSubscription)
  {
    final org.adblockplus.libadblockplus.android.Subscription subscription =
      new org.adblockplus.libadblockplus.android.Subscription();

    JsValue jsTitle = jsSubscription.getProperty("title");
    try
    {
      subscription.title = jsTitle.toString();
    }
    finally
    {
      jsTitle.dispose();
    }

    JsValue jsUrl = jsSubscription.getProperty("url");
    try
    {
      subscription.url = jsUrl.toString();
    }
    finally
    {
      jsUrl.dispose();
    }

    JsValue jsSpecialization = jsSubscription.getProperty("specialization");
    try
    {
      subscription.specialization = jsSpecialization.toString();
    }
    finally
    {
      jsSpecialization.dispose();
    }

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

  public org.adblockplus.libadblockplus.android.Subscription[] getRecommendedSubscriptions()
  {
    List<Subscription> subscriptions = this.filterEngine.fetchAvailableSubscriptions();
    try
    {
      return convertJsSubscriptions(subscriptions);
    }
    finally
    {
      for (Subscription eachSubscription : subscriptions)
      {
        eachSubscription.dispose();
      }
    }
  }

  public org.adblockplus.libadblockplus.android.Subscription[] getListedSubscriptions()
  {
    List<Subscription> subscriptions = this.filterEngine.getListedSubscriptions();
    try
    {
      return convertJsSubscriptions(subscriptions);
    }
    finally
    {
      for (Subscription eachSubscription : subscriptions)
      {
        eachSubscription.dispose();
      }
    }
  }

  public void clearSubscriptions()
  {
    for (final Subscription s : this.filterEngine.getListedSubscriptions())
    {
      try
      {
        s.removeFromList();
      }
      finally
      {
        s.dispose();
      }
    }
  }

  public void setSubscription(final String url)
  {
    clearSubscriptions();

    final Subscription sub = this.filterEngine.getSubscription(url);
    if (sub != null)
    {
      try
      {
        sub.addToList();
      }
      finally
      {
        sub.dispose();
      }
    }
  }

  public void setSubscriptions(Collection<String> urls)
  {
    clearSubscriptions();

    for (String eachUrl : urls)
    {
      final Subscription sub = this.filterEngine.getSubscription(eachUrl);
      if (sub != null)
      {
        try
        {
          sub.addToList();
        }
        finally
        {
          sub.dispose();
        }
      }
    }
  }

  public void setEnabled(final boolean enabled)
  {
    this.enabled = enabled;
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

  public String getDocumentationLink()
  {
    JsValue jsPref = this.filterEngine.getPref("documentation_link");
    try
    {
      return jsPref.toString();
    }
    finally
    {
      jsPref.dispose();
    }
  }

  public boolean matches(final String fullUrl, final ContentType contentType, final String[] referrerChainArray)
  {
    if (!enabled)
    {
      return false;
    }

    final Filter filter = this.filterEngine.matches(fullUrl, contentType, referrerChainArray);

    if (filter == null)
    {
      return false;
    }

    try
    {
      // hack: if there is no referrer, block only if filter is domain-specific
      // (to re-enable in-app ads blocking, proposed on 12.11.2012 Monday meeting)
      // (documentUrls contains the referrers on Android)
      try
      {
        JsValue jsText = filter.getProperty("text");
        try
        {
          if (referrerChainArray.length == 0 && (jsText.toString()).contains("||"))
          {
            return false;
          }
        }
        finally
        {
          jsText.dispose();
        }
      }
      catch (NullPointerException e)
      {
      }

      return filter.getType() != Filter.Type.EXCEPTION;
    }
    finally
    {
      filter.dispose();
    }
  }

  public boolean isDocumentWhitelisted(final String url, final String[] referrerChainArray)
  {
    return this.filterEngine.isDocumentWhitelisted(url, referrerChainArray);
  }

  public boolean isDomainWhitelisted(final String url, final String[] referrerChainArray)
  {
    if (whitelistedDomains == null)
    {
      return false;
    }

    // using Set to remove duplicates
    Set<String> referrersAndResourceUrls = new HashSet<String>();
    if (referrerChainArray != null)
    {
      referrersAndResourceUrls.addAll(Arrays.asList(referrerChainArray));
    }
    referrersAndResourceUrls.add(url);

    for (String eachUrl : referrersAndResourceUrls)
    {
      if (whitelistedDomains.contains(filterEngine.getHostFromURL(eachUrl)))
      {
        return true;
      }
    }

    return false;
  }

  public boolean isElemhideWhitelisted(final String url, final String[] referrerChainArray)
  {
    return this.filterEngine.isElemhideWhitelisted(url, referrerChainArray);
  }

  public List<String> getElementHidingSelectors(final String url, final String domain, final String[] referrerChainArray)
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
     * possible whitelisting of either the document or element hiding for
     * the given URL and returns an empty list if so. This is needed to
     * ensure correct functioning of e.g. acceptable ads.
     */
    if (!this.enabled
        || !this.elemhideEnabled
        || this.isDomainWhitelisted(url, referrerChainArray)
        || this.isDocumentWhitelisted(url, referrerChainArray)
        || this.isElemhideWhitelisted(url, referrerChainArray))
    {
      return new ArrayList<String>();
    }
    return this.filterEngine.getElementHidingSelectors(domain);
  }

  public void checkForUpdates()
  {
    this.filterEngine.forceUpdateCheck(this.updateCheckDoneCallback);
  }

  public FilterEngine getFilterEngine()
  {
    return this.filterEngine;
  }

  public void setWhitelistedDomains(List<String> domains)
  {
    this.whitelistedDomains = domains;
  }

  public List<String> getWhitelistedDomains()
  {
    return whitelistedDomains;
  }
}
