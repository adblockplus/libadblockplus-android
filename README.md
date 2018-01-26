Adblock Plus Library for Android
================================

An Android library project, tests and demo application for AdblockWebView widget.

## Updating the dependencies

Adblock Plus for Android has dependencies that aren't in this repository.
To update those, call:

    ./ensure_dependencies.py

## Library

An Android library that provides the core functionality of Adblock Plus.
You can find it in the 'libadblockplus-android' directory.

### Building

#### Requirements

* [The Android SDK](https://developer.android.com/sdk)
* Android SDK Build tools 24.0.1
* [The Android NDK](https://developer.android.com/ndk)

Edit 'buildToolsVersion' in 'build.gradle' files if necessary.

#### Building from command-line

In the project root directory create the file _local.properties_ and set
_sdk.dir_ and _ndk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk
    ndk.dir = /some/where/ndk

In the project root directory run:

    ./gradlew assembleDebug

This will generate *.aar library artifact in the 'libadblockplus-android/build/outputs/aar/' directory.

**Android permissions note**

An app that uses the library have to add the following permissions to `AndroidManifest.xml`:
 * `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>`
 * `<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>`

(added automatically if building with Gradle or should be added manually otherwise).

### Build directory configuration

By default Gradle uses `build` directory to build modules, however it can be undesired
for some use cases like CI or building as Chromium submodule.
Set `GRADLE_BUILD_DIR` environment variable to configure build directory:

    GRADLE_BUILD_DIR=/tmp ./gradlew clean assemble

Note

    [Configuration] Building project in /tmp

output while building

Set `SHARED_V8_LIB_DIR` environment variable as full absolute path to pass
specific directory instead of default one (`libadblockplus-android/jni/libadblockplus-binaries`).

### Building for single ARCH

By default libadblockplus-android is built for both ARM and x86 and it can be filtered when
building end-user android application. However sometimes it can be desired to build
"libadblockplus-android.aar" for single ARCH.

Pass `abi_arm` or `abi_x86` to build it for single arch or `abi_all` for all ARCHs:

    `./gradlew clean assembleAbi_arm`
    
Note

    [Configuration] Using libadblockplus-android ABI flavor: abi_arm
    
output while building.

## Library tests

Android tests for the library.
You can find them in the 'libadblockplus-android-tests' directory.

### Requirements

Make sure _Library_ requirements are present.

### Building

Make sure you've created the _local.properties_ file to build the library (see above).
In the project root directory run:

    ./gradlew assembleDebugAndroidTest

This will generate *.apk in the 'libadblockplus-android-tests/build/outputs/apk/' directory.

### Testing

You can select test class/method and click 'Run ..Test'. The library and test app will be
compiled, installed to emulator/device and launched automatically.

## Settings

An Android library that provides a configuration interface for Adblock Plus.
You can find it in the 'libadblockplus-android-settings' directory:
* GeneralSettingsFragment - main fragment
* WhitelistedDomainsSettingsFragment - whitelisted domains fragment

### Usage

Create `AdblockEngineProvider` instance and `AdblockSettingsStorage` instance.
You can use `SharedPrefsStorage` implementation to store settings in `SharedPreferences`.
Or you can use AdblockHelper:

    AdblockHelper
      .get()
      .init(this, getFilesDir().getAbsolutePath(), true, AdblockHelper.PREFERENCE_NAME);

      // optional - provide preloaded subscription files in app resoruces
      .preloadSubscriptions(AdblockHelper.PRELOAD_PREFERENCE_NAME, map);

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

Invoke `waitforReady` every time you need AdblockEngine instance if retained in asynchronous mode:

    AdblockHelper.get().getProvider().waitForReady();

Release Adblock instance in activity `onDestroy`:

    AdblockHelper.get().getProvider().release();

Insert `GeneralSettingsFragment` fragment instance in runtime to start showing settings UI.

### Building

In the project root directory run:

    ./gradlew assemble

This will generate *.aar in the 'libadblockplus-android-settings/build/outputs/aar' directory.

## WebView

An Android library that provides a WebView component with Adblock Plus integrated.
You can find it in the 'libadblockplus-android-webview' directory.

`AdblockWebView` class provides built-in ad blocking
(both resource loading filtering and element hiding) and inherits from Android
['WebView'](https://developer.android.com/reference/android/webkit/WebView.html).

### Usage

In layout XML:

    <org.adblockplus.libadblockplus.android.webview.AdblockWebView
        android:id="@+id/main_webview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

In java source code:

    AdblockWebView webView = (AdblockWebView) findViewById(R.id.main_webview);

Use `setAdblockEnabled(boolean adblockEnabled)` to enable/disable adblocking.

Use `setDebugMode(boolean debugMode)` to turn debug log output (Android log and JS console) on/off.

Use `setAllowDrawDelay(int allowDrawDelay)` to set custom delay to start render webpage after 'DOMContentLoaded' event is fired.

Use `setProvider(AdblockEngineProvider provider)` to use external adblock engine provider.
The simplest solution is to use `AdblockHelper` from `-settings` as external adblock engine provider:

    webView.setProvider(AdblockHelper.get().getProvider());

If adblock engine provider is not set, it's created by AdblockWebView instance automatically.

Use `dispose(Runnable disposeFinished)` to release resources (**required**).
Note it can be invoked from background thread.

### Building

In the project root directory run:

    ./gradlew assemble

This will generate *.aar in the 'libadblockplus-android-webview/build/outputs/aar' directory.

## WebView Application

An Android application that demonstrates how to use AdblockWebView.
You can find it in the 'libadblockplus-android-webviewapp' directory.

### Building

Make sure _Library_ requirements are present.

In the project root directory run:

    ./gradlew assemble

This will generate *.apk in the 'libadblockplus-android-webviewapp/build/outputs/apk/' directory.
