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

import androidx.annotation.RawRes;

import org.adblockplus.AdblockEngineSettings;
import org.adblockplus.ConnectionType;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.AndroidBase64Processor;
import org.adblockplus.libadblockplus.android.AndroidHttpClient;
import org.adblockplus.libadblockplus.android.AndroidHttpClientResourceWrapper;
import org.adblockplus.libadblockplus.android.SingleInstanceEngineProvider;
import org.adblockplus.libadblockplus.security.JavaSignatureVerifier;
import org.adblockplus.libadblockplus.security.SignatureVerifier;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolder;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl;
import org.adblockplus.libadblockplus.sitekey.SiteKeyVerifier;
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;
import org.adblockplus.libadblockplus.util.Base64Processor;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

/**
 * AdblockHelper shared resources
 * (singleton)
 */
public class AdblockHelper
{

  /**
   * Suggested preference name to store settings
   */
  public static final String PREFERENCE_NAME = "ADBLOCK";

  /**
   * Suggested preference name to store intercepted subscription requests
   */
  public static final String PRELOAD_PREFERENCE_NAME = "ADBLOCK_PRELOAD";
  private static AdblockHelper _instance;

  private boolean isInitialized;
  private Context context;
  private AdblockEngine.Builder factory;
  private SingleInstanceEngineProvider provider;
  private AdblockSettingsStorage storage;
  private SiteKeysConfiguration siteKeysConfiguration;

  private final SingleInstanceEngineProvider.EngineCreatedListener engineCreatedListener =
    new SingleInstanceEngineProvider.EngineCreatedListener()
  {
    @Override
    public void onAdblockEngineCreated(final AdblockEngine engine)
    {
      final AdblockSettings savedSettings = storage.load();
      if (savedSettings != null)
      {
        Timber.d("Applying saved adblock settings to adblock engine");
        // apply last saved settings to adblock engine
        final ConnectionType connectionType = savedSettings.getAllowedConnectionType();
        final AdblockEngineSettings.EditOperation editOperation = engine.settings().edit();
        editOperation.setEnabled(savedSettings.isAdblockEnabled());
        if (connectionType != null)
        {
          editOperation.setAllowedConnectionType(connectionType);
        }
        editOperation.save();
      }
      else
      {
        Timber.w("No saved adblock settings");
      }
    }
  };

  private final SingleInstanceEngineProvider.BeforeEngineDisposedListener
    beforeEngineDisposedListener =
      new SingleInstanceEngineProvider.BeforeEngineDisposedListener()
  {
    @Override
    public void onBeforeAdblockEngineDispose()
    {
      Timber.d("Disposing Adblock engine");
    }
  };

  private final SingleInstanceEngineProvider.EngineDisposedListener engineDisposedListener =
    new SingleInstanceEngineProvider.EngineDisposedListener()
  {
    @Override
    public void onAdblockEngineDisposed()
    {
      Timber.d("Adblock engine disposed");
    }
  };

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

  public AdblockEngine.Builder getFactory()
  {
    if (factory == null)
    {
      throw new IllegalStateException("Usage exception: call init(...) first");
    }
    return factory;
  }

  public static synchronized void deinit()
  {
    _instance = null;
  }

  public AdblockEngineProvider getProvider()
  {
    if (provider == null)
    {
      throw new IllegalStateException("Usage exception: call init(...) first");
    }
    return provider;
  }

  public AdblockSettingsStorage getStorage()
  {
    if (storage == null)
    {
      throw new IllegalStateException("Usage exception: call init(...) first");
    }
    return storage;
  }

  public SiteKeysConfiguration getSiteKeysConfiguration()
  {
    return siteKeysConfiguration;
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
   * @param preferenceName Shared Preferences name to store adblock settings
   */
  public AdblockHelper init(final Context context,
                            final String basePath,
                            final String preferenceName)
  {
    if (isInitialized)
    {
      throw new IllegalStateException("Usage exception: already initialized. Check `isInit()`");
    }

    final Context appContext = context.getApplicationContext();
    initFactory(appContext, basePath);
    initProvider();
    initStorage(appContext, preferenceName);
    initSiteKeysConfiguration();
    isInitialized = true;
    return this;
  }

  /**
   * Check if it is already initialized
   * @return
   */
  public boolean isInit()
  {
    return isInitialized;
  }

  private void initFactory(final Context context, final String basePath)
  {
    factory = AdblockEngine.builder(context, basePath);
    this.context = context;
  }

  private void initProvider()
  {
    provider = new SingleInstanceEngineProvider(factory);
    provider.addEngineCreatedListener(engineCreatedListener);
    provider.addBeforeEngineDisposedListener(beforeEngineDisposedListener);
    provider.addEngineDisposedListener(engineDisposedListener);
  }

  private void initStorage(final Context context, final String settingsPreferenceName)
  {
    // read and apply current settings
    final SharedPreferences settingsPrefs = context.getSharedPreferences(
      settingsPreferenceName,
      Context.MODE_PRIVATE);

    storage = new SharedPrefsStorage(settingsPrefs);
  }

  private void initSiteKeysConfiguration()
  {
    final SignatureVerifier signatureVerifier = new JavaSignatureVerifier();
    final PublicKeyHolder publicKeyHolder = new PublicKeyHolderImpl();
    final HttpClient httpClient = new AndroidHttpClient(true);
    final Base64Processor base64Processor = new AndroidBase64Processor();
    final SiteKeyVerifier siteKeyVerifier =
        new SiteKeyVerifier(signatureVerifier, publicKeyHolder, base64Processor);

    siteKeysConfiguration = new SiteKeysConfiguration(
        signatureVerifier, publicKeyHolder, httpClient, siteKeyVerifier);
  }

  /**
   * Use preloaded subscriptions
   * @param preferenceName Shared Preferences name to store intercepted requests stats
   * @param urlToResourceIdMap URL to Android resource id map
   * @return this (for method chaining)
   */
  public AdblockHelper preloadSubscriptions(final String preferenceName,
                                            final Map<String, Integer> urlToResourceIdMap)
  {
    final SharedPreferences prefs = context.getSharedPreferences(
        preferenceName,
        Context.MODE_PRIVATE);
    factory.preloadSubscriptions(
        context,
        urlToResourceIdMap,
        new AndroidHttpClientResourceWrapper.SharedPrefsStorage(prefs));
    return this;
  }

  /**
   * Sets preloaded subscriptions simplified. It sets a preloaded subscription to all possible
   * default non AA subscriptions (based on locale). And a preload a subscription for the default
   * acceptable ads subscription. This method does not directly inject the subscriptions into the
   * engine, but rather will return this files at the first time the adblock engine needs them.
   * @param subscriptionResource resource id for non aa subscription
   * @param acceptableAdsSubscriptionResource resource id for aa subscription
   * @return this (for method chaining)
   */
  public AdblockHelper preloadSubscriptions(@RawRes final Integer subscriptionResource,
                                            @RawRes final Integer acceptableAdsSubscriptionResource)
  {
    final Map<String, Integer> map = new HashMap<>();
    // all non AA subscriptions
    map.put(AndroidHttpClientResourceWrapper.EASYLIST, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_INDONESIAN, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_BULGARIAN, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_CHINESE, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_CZECH_SLOVAK, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_DUTCH, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_GERMAN, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_ISRAELI, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_ITALIAN, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_LITHUANIAN, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_LATVIAN, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_ARABIAN_FRENCH, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_FRENCH, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_POLISH, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_ROMANIAN, subscriptionResource);
    map.put(AndroidHttpClientResourceWrapper.EASYLIST_RUSSIAN, subscriptionResource);
    // single AA subscription
    map.put(AndroidHttpClientResourceWrapper.ACCEPTABLE_ADS, acceptableAdsSubscriptionResource);
    return preloadSubscriptions(PRELOAD_PREFERENCE_NAME, map);
  }

  /**
   * Add "engine created" even listener
   * @param listener Listener
   */
  public AdblockHelper addEngineCreatedListener(
      final AdblockEngineProvider.EngineCreatedListener listener)
  {
    provider.addEngineCreatedListener(listener);
    return this;
  }

  /**
   * Add "engine disposed" listener
   * @param listener Listener
   */
  public AdblockHelper addEngineDisposedListener(
      final AdblockEngineProvider.EngineDisposedListener listener)
  {
    provider.addEngineDisposedListener(listener);
    return this;
  }

  /**
   * Will create adblock engine disabled by default. This means subscriptions will be updated only
   * when setEnabled(true) will be called. This function configures only default engine state. If
   * other state is stored in settings, it will be preferred.
   */
  public AdblockHelper setDisabledByDefault()
  {
    Timber.d("setDisabledByDefault()");
    factory.setDisableByDefault();
    return this;
  }

  public boolean getDisabledByDefault()
  {
    return factory.getDisableByDefault();
  }
}
