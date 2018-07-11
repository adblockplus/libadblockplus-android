Adblock Android SDK
================================

An Android library project, tests, settings fragments and demo application for AdblockWebView.

## Updating the dependencies

Adblock Android SDK has dependencies that aren't in this repository.
To update those, call:

    ./ensure_dependencies.py

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

### Building

#### Requirements

* [The Android SDK](https://developer.android.com/sdk)
* Android SDK Build tools 25.0.0
* [The Android NDK, 16b](https://developer.android.com/ndk)

Edit 'buildToolsVersion' in 'build.gradle' files if necessary.

#### Building of libadblockplus

First, we need to build `V8` required for `libadblockplus`.
See `libadblockplus/README` or V8 documentation on how to build V8 or
fetch precompiled one. For the latter, run in 'libadblockplus' directory:

    make TARGET_OS=android ABP_TARGET_ARCH=arm Configuration=release get-prebuilt-v8
    make TARGET_OS=android ABP_TARGET_ARCH=ia32 Configuration=release get-prebuilt-v8

Then we can build `libadblockplus`:

    make TARGET_OS=android ABP_TARGET_ARCH=arm Configuration=release
    make TARGET_OS=android ABP_TARGET_ARCH=ia32 Configuration=release

#### Building from command-line

In the project root directory create the file _local.properties_ and set
_sdk.dir_ and _ndk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk
    ndk.dir = /some/where/ndk

In the project root directory run:

    ./gradlew assembleDebug

This will generate *.aar library artifact in the 'adblock-android/build/outputs/aar/' directory.

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

### Building for single ARCH

By default adblock-android is built for both ARM and x86 and it can be filtered when
building end-user android application. However sometimes it can be desired to build
"adblock-android.aar" for single ARCH.

Pass `abi_arm` or `abi_x86` to build it for single arch or `abi_all` for all ARCHs:

    `./gradlew clean assembleAbi_arm`

Note

    [Configuration] Using adblock-android ABI flavor: abi_arm

output while building.

## Library tests

Android tests for the library.
You can find them in the 'adblock-android-tests' directory.

### Requirements

Make sure _Library_ requirements are present.

### Building

Make sure you've created the _local.properties_ file to build the library (see above).
In the project root directory run:

    ./gradlew assembleDebugAndroidTest

This will generate *.apk in the 'adblock-android-tests/build/outputs/apk/' directory.

### Testing

You can select test class/method and click 'Run ..Test'. The library and test app will be
compiled, installed to emulator/device and launched automatically.

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

### Usage

Create `AdblockEngineProvider` instance and `AdblockSettingsStorage` instance.
You can use `SharedPrefsStorage` implementation to store settings in `SharedPreferences`.
Or you can use AdblockHelper:

    AdblockHelper
      .get()
      .init(this, getFilesDir().getAbsolutePath(), true, AdblockHelper.PREFERENCE_NAME);

      // optional - provide preloaded subscription files in app resoruces
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

This will generate *.aar in the 'adblock-android-webview/build/outputs/aar' directory.

## WebView Application

An Android application that demonstrates how to use AdblockWebView.
You can find it in the 'adblock-android-webviewapp' directory.

### Building

Make sure _Library_ requirements are present.

In the project root directory run:

    ./gradlew assemble

This will generate *.apk in the 'adblock-android-webviewapp/build/outputs/apk/' directory.
