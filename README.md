Adblock Plus Library for Android
========================

An Android library project, tests and demo application for AdblockWebView widget.

## Updating the dependencies

Adblock Plus Library for Android has dependencies that aren't in this repository.
To update those, call:

    ./ensure_dependencies.py

## Library

### Building with Ant

#### Requirements

* [The Android SDK](http://developer.android.com/sdk)
* [The Android NDK](https://developer.android.com/tools/sdk/ndk)
* [Ant](http://ant.apache.org)

#### Building

In the 'libadblockplus-android' directory create the file _local.properties_ and set
_sdk.dir_ and _ndk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk
    ndk.dir = /some/where/ndk

Then run:

    ant debug

### Building with Maven

#### Requirements

All 'Building with Ant' requirements and additional requirements:

* [Maven](https://maven.apache.org)

#### Building

Go to android sdk directory '/platforms/android-21' and run:

    mvn install:install-file -Dfile=./android.jar -DgroupId=com.google.android -DartifactId=android
     -Dversion=5.0 -Dpackaging=jar -DgeneratePom=true

Set environment variable ANDROID_HOME to your Android SDK directory or pass it in command-line (below).
In the root directory run:

	mvn clean install [-Dandroid.sdk.path=/some/where/sdk]

This will generate *.aar library artifacts in the 'libadblockplus-android/target',
'libadblockplus-android-settings/target', 'libadblockplus-android-webview/target' directories
and *.apk in the 'libadblockplus-android-webviewapp/target' directory.

### Building with Gradle/Android Studio

#### Requirements

* [The Android SDK](http://developer.android.com/sdk)
* Android SDK Build tools 24.0.1
* [The Android NDK](https://developer.android.com/tools/sdk/ndk)

Edit 'buildToolsVersion' in 'build.gradle' files if necessary.

#### Building from command-line

In the project root directory create the file _local.properties_ and set
_sdk.dir_ and _ndk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk
    ndk.dir = /some/where/ndk

In the project root directory run:

    ./gradlew assembleDebug

This will generate *.aar library artifact in the 'libadblockplus-android/build/outputs/aar/' directory.

## Library tests

### Requirements

Make sure _Library_ requirements are present.

### Building with Ant

Set ANDROID_HOME environment variable to your Android SDK directory.

In the 'libadblockplus-android-tests' directory run:

    ant instrument

### Testing with Ant

1. Connect an Android device or start the Android Emulator.
2. In the 'libadblockplus-android-tests' directory run:

    ant instrument install test

to build instrumentation tests app and perform testing or run:

    ant test

to run installed instrumentation tests app.

To run specific **test** run:

    ant testOnly -DtestClass=full.test.class.name

For example:

    ant testOnly -DtestClass=org.adblockplus.libadblockplus.tests.NotificationTest

To run specific **test method** run:

    ant testOnly -DtestClass=full.test.class.name#testMethod

For example:

    ant testOnly -DtestClass=org.adblockplus.libadblockplus.tests.NotificationTest#testAddNotification

### Building with Gradle/Android Studio

Make sure you've created the _local.properties_ file to build the library (see above).
In the project root directory run:

    ./gradlew assembleDebugAndroidTest

This will generate *.apk in the 'libadblockplus-android-tests/build/outputs/apk/' directory.

### Testing with Gradle/Android Studio

You can select test class/method and click 'Run ..Test'. The library and test app will be
compiled, installed to emulator/device and launched automatically.

## Settings

You can find adblock fragments in the 'libadblockplus-android-settings' directory:
* GeneralSettingsFragment - main fragment
* WhitelistedDomainsSettingsFragment - whitelisted domains fragment

### Usage

Create `AdblockEngine` instance with factory methods and `AdblockSettingsStorage` instance.
You can use `SharedPrefsStorage` implementation to store settings in `SharedPreferences`.
Or you can use AdblockHelper:

    AdblockHelper.get().init(this, true, AdblockHelper.PREFERENCE_NAME);

Implement the following interfaces in your settings activity:

* `BaseSettingsFragment.Provider`
* `GeneralSettingsFragment.Listener`
* `WhitelistedDomainsSettingsFragment.Listener`

and return created instance or AdblockHelper instances:

    AdblockHelper.get().getEngine();  // engine
    AdblockHelper.get().getStorage(); // storage

Retain Adblock instance in activity `onCreate` in synchronous mode (it may take few seconds):

    AdblockHelper.get().retain(false);

or in asynchronous mode (without current thread lock):

    AdblockHelper.get().retain(true);

Invoke `waitforReady` every time you need AdblockEngine instance if retained in asynchronous mode:

    AdblockHelper.get().waitForReady();

Release Adblock instance in activity `onDestroy`:

    AdblockHelper.get().release();

Insert `GeneralSettingsFragment` fragment instance in runtime to start showing settings UI.

### Building

#### Building with Ant

In the 'libadblockplus-android-settings' directory create the file _local.properties_ and set
_sdk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk

Then run:

    ant debug


#### Building with Gradle

In the project root directory run:

    ./gradlew assemble

This will generate *.aar in the 'libadblockplus-android-settings/build/outputs/aar' directory.

## WebView Application

You can find demo application for 'AdblockWebView' class in
'libadblockplus-android-webviewapp' directory.

### Building

Make sure _Library_ requirements are present.

#### Building with Ant

In the 'libadblockplus-android-webviewapp' directory create the file _local.properties_ and set
_sdk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk

Then run:

    ant debug

This will generate *.apk in the 'libadblockplus-android-webviewapp/bin/' directory.

#### Building with Gradle

In the project root directory run:

    ./gradlew assemble

This will generate *.apk in the 'libadblockplus-android-webviewapp/build/outputs/apk/' directory.


## WebView

You can find 'AdblockWebView' class in the 'libadblockplus-android-webview' directory.

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

Use `setAdblockEngine(AdblockEngine adblockEngine)` to use external adblock engine.
If adblock engine is not set, it's created by AdblockWebView instance automatically.

Use `dispose(Runnable disposeFinished)` to release resources (**required**).
Note it can be invoked from background thread.

### Building

#### Building with Ant

In the 'libadblockplus-android-webview' directory create the file _local.properties_ and set
_sdk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk

Then run:

    ant debug


#### Building with Gradle

In the project root directory run:

    ./gradlew assemble

This will generate *.aar in the 'libadblockplus-android-webview/build/outputs/aar' directory.

## WebView Application

You can find demo application for 'AdblockWebView' class in the
'libadblockplus-android-webviewapp' directory.

### Building

Make sure _Library_ requirements are present.

#### Building with Ant

In the 'libadblockplus-android-webviewapp' directory create the file _local.properties_ and set
_sdk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk

Then run:

    ant debug

This will generate *.apk in the 'libadblockplus-android-webviewapp/bin/' directory.

#### Building with Gradle

In the project root directory run:

    ./gradlew assemble

This will generate *.apk in the 'libadblockplus-android-webviewapp/build/outputs/apk/' directory.
