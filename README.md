Adblock Android SDK
================================

An Android library project, tests, settings fragments and demo application for AdblockWebView.

## Git commits

This repo uses [pre-commit](https://pre-commit.com) to maintain agreed conventions in the repo. It should
be [installed](https://pre-commit.com/#installation) (tldr; `pip install pre-commit` then `pre-commit install`)
before making any new commits to the repo.

## Updating the dependencies

Adblock Android SDK has dependencies that aren't in this repository.
To update those, call:

    ./ensure_dependencies.py

Please make sure you have *python2* installed

## Library

An Android library that provides the core functionality of Adblock Plus.
You can find it in the 'adblock-android' directory.

### Using as a Gradle dependency

Make sure you have `jcenter()` in the list of repositories and then add the following dependency:

```groovy
dependencies {
    implementation 'org.adblockplus:adblock-android:3.0'
}
```

In general case it's suggested to use the most recent version.

### Building

#### Requirements

* [The Android SDK](https://developer.android.com/sdk)
* Android SDK Build tools 28.0.3
* [The Android NDK, 16b](https://developer.android.com/ndk)

Edit 'buildToolsVersion' in 'build.gradle' files if necessary.

#### Building of libadblockplus

First, make sure all the [prerequisites](https://gitlab.com/eyeo/adblockplus/libadblockplus/blob/master/README.md#supported-target-platforms-and-prerequisites) are installed.
Second, one needs to build `V8` required for `libadblockplus`.
See `libadblockplus/README` or V8 documentation on how to build V8 or
fetch precompiled one. For the latter, run in 'libadblockplus' directory:

    make TARGET_OS=android ABP_TARGET_ARCH=arm Configuration=release get-prebuilt-v8
    make TARGET_OS=android ABP_TARGET_ARCH=arm64 Configuration=release get-prebuilt-v8
    make TARGET_OS=android ABP_TARGET_ARCH=ia32 Configuration=release get-prebuilt-v8
    make TARGET_OS=android ABP_TARGET_ARCH=x64 Configuration=release get-prebuilt-v8

Make sure to set `ANDROID_NDK_ROOT` environment variable to point to Android NDK installation, eg.:

    export ANDROID_NDK_ROOT=/Users/developer/ndk/android-ndk-r16b

After that we can build `libadblockplus`:

    make TARGET_OS=android ABP_TARGET_ARCH=arm Configuration=release
    make TARGET_OS=android ABP_TARGET_ARCH=arm64 Configuration=release
    make TARGET_OS=android ABP_TARGET_ARCH=ia32 Configuration=release
    make TARGET_OS=android ABP_TARGET_ARCH=x64 Configuration=release

#### Building from command-line

In the project root directory create the file _local.properties_ and set
_sdk.dir_ and _ndk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk
    ndk.dir = /some/where/ndk

In the project root directory run:

    ./gradlew assembleDebug

This will generate *.aar artifacts in the '.../build/outputs/aar/' directories:

* adblock-android-abi_all-... - AAR for all the ARCHs (x86, armv7a, arm64)
* adblock-android-abi_x86-... - AAR for x86 only
* adblock-android-abi_x86_64-... - AAR for x86_64 only
* adblock-android-abi_arm-... - AAR for armv7a only
* adblock-android-abi_arm64-... - AAR for arm64 only
* adblock-android-webview-... - AAR for AdblockWebView
* adblock-android-settings-... - AAR for Settings

**Android permissions note**

An app that uses the library have to add the following permissions to `AndroidManifest.xml`:
 * `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>`
 * `<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>`
 * `<uses-permission android:name="android.permission.INTERNET"/>`

(added automatically if building with Gradle or should be added manually otherwise).

### Build directory configuration

By default Gradle uses `build` directory to build modules, however it can be undesired
for some use cases like CI or building as Chromium submodule.
Set `GRADLE_BUILD_DIR` environment variable to configure build directory:

    GRADLE_BUILD_DIR=/tmp ./gradlew clean assemble

Note

    [Configuration] Building project in /tmp

output while building

### Building with prebuilt shared V8

This can be desired to use product's V8 (let's say Chromium) instead of built-in V8.
Put prebuilt shared V8 library file(s) in ARCH directories and set `SHARED_V8_LIB_FILENAMES`
environment variable and `SHARED_V8_LIB_DIR` before building.
You can pass multiple filenames in `SHARED_V8_LIB_FILENAMES`, separated with space.
Libadblockplus is required to be linked with that library file(s).

For example:

    SHARED_V8_LIB_FILENAMES=libv8.cr.so SHARED_V8_LIB_DIR="/tmp/shared_v8" ./gradlew clean assembleAbi_arm

or

    SHARED_V8_LIB_FILENAMES="libv8.cr.so libv8_libbase.cr.so libv8_libplatform.cr.so" SHARED_V8_LIB_DIR="/tmp/shared_v8" ./gradlew clean assembleAbi_arm

for multiple library files.

Note

    [Configuration] Excluding shared v8 library libv8.cr.so from AAR
    ...
    [Configuration] Linking dynamically with shared v8 library /tmp/shared_v8/release/libv8.cr.so
    ...

output while building.

### Building with exposing of libadblockplus classes

Set `EXPOSE_LIBABP_OBJECTS` environment variable to expose libadblockplus classes in shared library.

For example:

    EXPOSE_LIBABP_OBJECTS=y ./gradlew clean assembleAbi_arm

### JNI adjustments

In order to load custom library name pass `LIBABP_SHARED_LIBRARY_NAME` environment variable (without `lib` and `.so`):

    LIBABP_SHARED_LIBRARY_NAME=adblockplus ./gradlew assembleRelease

In order to skip compilation of JNI classes pass `SKIP_JNI_COMPILATION` environment variable:

    SKIP_JNI_COMPILATION=true ./gradlew assembleRelease

### Building for single ARCH

By default adblock-android is built for ARM/ARM64 and x86/x86_64 and it can be filtered when
building end-user android application. However sometimes it can be desired to build
"adblock-android.aar" for single ARCH.

Pass `abi_arm`, `abi_arm64`, `abi_x86`, or `abi_x86_64` to build it for single arch or `abi_all` for all ARCHs:

    `./gradlew clean assembleAbi_arm`

Note

    [Configuration] Using adblock-android ABI flavor: abi_arm

output while building.

## SDK tests

### Pure java tests

You can find pure Java tests in 'src/test' directories of the modules (if provided).
In the project directory run:

    ./gradlew test

You can select test class/method and click 'Run ..Test'.
No Android emulator/device running required.

### Android tests

You can find Android tests in 'src/androidTest' directories of the modules (if provided).
In the project directory run:

    ./gradlew connectedAbi_x86DebugAndroidTest

to test with x86 device/emulator or run:

    ./gradlew connectedAbi_armDebugAndroidTest

to test with ARM device/emulator.
You can select test class/method and click 'Run ..Test'.

## Settings

An Android library that provides a configuration interface for Adblock Plus.
You can find it in the 'adblock-android-settings' directory:
* GeneralSettingsFragment - main fragment
* WhitelistedDomainsSettingsFragment - whitelisted domains fragment

### Using as a Gradle dependency

Make sure you have `jcenter()` in the list of repositories and then add the following dependency:

```groovy
dependencies {
    implementation 'org.adblockplus:adblock-android-settings:3.0'
}
```

In general case it's suggested to use the most recent version.

### Usage

Create `AdblockEngineProvider` instance and `AdblockSettingsStorage` instance.
You can use `SharedPrefsStorage` implementation to store settings in `SharedPreferences`.
Or you can use AdblockHelper:

    AdblockHelper
      .get()
      .init(this, getFilesDir().getAbsolutePath(), true, AdblockHelper.PREFERENCE_NAME);

      // optional - provide preloaded subscription files in app resources
      .preloadSubscriptions(AdblockHelper.PRELOAD_PREFERENCE_NAME, map);

Make sure you initialize it once during app launch, call `isInit()` to check it:

    if (!AdblockHelper.get().isInit())
    {
      // requires initialization
      ...
    }

Sometimes it's desired to initialize or deinitialize FilterEngine instance
when created:

    AdblockHelper
      .get()
      .init(...)
      .addEngineCreatedListener(engineCreatedListener)

or disposed:

    AdblockHelper
      .get()
      .init(...)
      .addEngineDisposedListener(engineDisposedListener)

Make sure you deinitialize it when values used during initialization are no longer valid:

    AdblockHelper.get().deinit();

Note one have to initialize it again to be used.

Implement the following interfaces in your settings activity:

* `BaseSettingsFragment.Provider`
* `GeneralSettingsFragment.Listener`
* `WhitelistedDomainsSettingsFragment.Listener`

and return created instance or AdblockHelper instances:

    AdblockHelper.get().getProvider().getEngine();  // engine
    AdblockHelper.get().getStorage(); // storage

Retain Adblock instance in activity `onCreate` in synchronous mode (it may take few seconds):

    AdblockHelper.get().getProvider().retain(false);

or in asynchronous mode (without current thread lock):

    AdblockHelper.get().getProvider().retain(true);

Invoke `waitForReady` every time you need AdblockEngine instance if retained in asynchronous mode:

    AdblockHelper.get().getProvider().waitForReady();

Release Adblock instance in activity `onDestroy`:

    AdblockHelper.get().getProvider().release();

Insert `GeneralSettingsFragment` fragment instance in runtime to start showing settings UI.



#### Background operations

By default filter engine will do some background operations like subscriptions synchronizations in background shortly
after initialized. If you want to have ad blocking as optional feature, you should consider use `setDisabledByDefault`

```
    AdblockHelper
      .get()
      .init(...)
      .setDisabledByDefault()
```

In this case no background operations will be done once you will call `AdblockEngine.setEnabled(true)`. Please note that
this method configures only default state. If user preference on enabled state is stored in settings, this value will be
preferred.

Other thing to take into account is synchronization time. If you have configured `setDisabledByDefault` and then enable
engine, first synchronization will be done only after some time. You can combine configuration with
`preloadSubscriptions` to load data from local file first time rather then from web.

#### Preloaded subscriptions

As mentioned, there is an option to set preloaded subscriptions. This means that at the application's first boot time, and every time the user clears the app's data, it will load the subscription lists that are bundled with the app. It will also set async calls to update these lists at once so that they are updated as soon as possible. Keep in mind that the ad-block engine will still ping periodically for updates on the subscriptions at an hourly interval.

The benefit of using this method is that it provides a better UX since the app does not have to wait for the subscription lists to be downloaded first, hence allowing the user to have an ad-blocking experience right away.

On the other hand, this is an opt-in feature that you have to set up. It also increases the footprint of the app by bundling the subscription lists with it, and you have to update the lists when building the apk. This is because subscription lists become outdated very fast. Ideally, you can set a gradle task for that, which is what we did.

By running `./gradlew downloadSubscriptionLists`, you update the preloaded EasyList and exception list to the latest ones.

To set it up in the code, you have to first map the URLs of the subscriptions to local files.

``` java
Map<String, Integer> map = new HashMap<String, Integer>();
map.put(AndroidHttpClientResourceWrapper.EASYLIST, R.raw.easylist);
map.put(AndroidHttpClientResourceWrapper.ACCEPTABLE_ADS, R.raw.exceptionrules);
```

Note that in this example we use the general EasyList subscription. So for example, if you are using subscription lists for another locale, you need to change the URL and replace the file with the correct one. The effect is not the same without these preloaded subscriptions.

Then, when using the adblockhelper for example, you can set it like:

``` java
adblockHelper
    .get()
    .init(this, basePath, true, AdblockHelper.PREFERENCE_NAME)
    .preloadSubscriptions(AdblockHelper.PRELOAD_PREFERENCE_NAME, map)
    .addEngineCreatedListener(engineCreatedListener)
    .addEngineDisposedListener(engineDisposedListener)
```


### Theme

Make sure to set application theme with `PreferenceThemeOverlay.v14.Material` parent theme
(see `AndroidManifest.xml` and `styles.xml` in `adblock-android-webviewapp` as an example).

### Building

In the project root directory run:

    ./gradlew assemble

This will generate *.aar in the 'adblock-android-settings/build/outputs/aar' directory.

## WebView

An Android library that provides a WebView component with Adblock Plus integrated.
You can find it in the 'adblock-android-webview' directory.

`AdblockWebView` class provides built-in ad blocking
(both resource loading filtering and element hiding) and inherits from Android
['WebView'](https://developer.android.com/reference/android/webkit/WebView.html).

### Using as a Gradle dependency

Make sure you have `jcenter()` in the list of repositories and then add the following dependency:

```groovy
dependencies {
    implementation 'org.adblockplus:adblock-android-webview:3.0'
}
```

In general case it's suggested to use the most recent version.

### Usage

In layout XML:

    <org.adblockplus.libadblockplus.android.webview.AdblockWebView
        android:id="@+id/main_webview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

In java source code:

    AdblockWebView webView = findViewById(R.id.main_webview);

Use `AdblockEngine.setEnabled(boolean enabled)` to enable/disable ad blocking for AdblockEngine.
Make sure you update the settings model if you want the new value to be applied after application restart, eg:
```
AdblockSettingsStorage storage = AdblockHelper.get().getStorage();
AdblockSettings settings = storage.load();
if (settings == null) // not yet saved
{
  settings = AdblockSettingsStorage.getDefaultSettings(...); // default
}
...
settings.setAdblockEnabled(newValue);
storage.save(settings);
```

Android SDK logging system is based on Timber library.

If you are configuring your project using Maven dependencies to consume our Android SDK
then Timber dependency is automatically installed.
If you are just copying AAR files to your project workspace then you need to add this line to your dependencies:

`implementation 'com.jakewharton.timber:timber:4.7.1'`.

To enable desired log output level configure Timber logger in your application code.

For example this code enables all debug logs in DEBUG mode:
```
if (BuildConfig.DEBUG) {
    Timber.plant(new Timber.DebugTree());
}
```
Please refer to https://github.com/JakeWharton/timber for more information about Timber.

Use `setAllowDrawDelay(int allowDrawDelay)` to set custom delay to start rendering a web page after 'DOMContentLoaded' event is fired.

Use `setProvider(@NotNull AdblockEngineProvider provider)` to use external adblock engine provider.
The simplest solution is to use `AdblockHelper` from `-settings` as external adblock engine provider:

    webView.setProvider(AdblockHelper.get().getProvider());

If adblock engine provider is not set, it's created by AdblockWebView instance automatically.

Use `setSiteKeysConfiguration(..)` to support sitekeys whitelisting.
This is optional but highly suggested. See `TabFragment.java` on usage example.

Use `setEventsListener()` to subscribe and unsubscribe to ad blocking and whitelisting events, eg.
"resource loading blocked" or "resource loading whitelisted" event that can be used for stats.
For the latter there is a convenience class `WebViewCounters` which can be bound to `EventsListener`
and notify your View about new values. See an example of usage in WebView Application.

Use `dispose(Runnable disposeFinished)` to release resources (**required**).
Note it can be invoked from background thread.

Enabling/disabling of ad blocking per AdblockWebView is not supported.

Ad blocking requires JavaScript to be enabled and every AdblockWebView instance enables JavaScript
during the initialization. 

### Building

In the project root directory run:

    ./gradlew assemble

This will generate *.aar in the 'adblock-android-webview/build/outputs/aar' directory.

## WebView Application

An Android application that demonstrates how to use AdblockWebView.
You can find it in the 'adblock-android-webviewapp' directory.

### Building

Make sure _Library_ requirements are present.

In the project root directory run:

    ./gradlew assemble

This will generate *.apk in the 'adblock-android-webviewapp/build/outputs/apk/' directory.

### Proguard

## Required configuration changes

Configure Proguard/R8 to skip Adblock Android SDK files (root package `org.adblockplus.libadblockplus`)
from being modified for `Release` build of end-user application.
See `adblock-android/proguard-rules-adblock.txt` as an example. If building end-user application
with Gradle, no actions are required - Gradle will use provided consumer Proguard file automatically.
See https://developer.android.com/studio/projects/android-library
"A library module may include its own ProGuard configuration file" section for further information.

## Reason

Adblock Android SDK uses JNI behind the scene so Java classes and methods are accessed by full
names from native code. If class names/members are modified by Proguard/R8 during `Release` build
they can't be accessed from native code resulting into Runtime exceptions like follows:

    java.lang.NoSuchMethodError: no non-static method "Lorg/adblockplus/libadblockplus/JsValue;.<init>(J)V"
