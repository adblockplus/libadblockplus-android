Adblock Plus Library for Android
========================

An Android library project that runs a proxy to block ads.

Updating the dependencies
-------------------------

Adblock Plus Library for Android has dependencies that aren't in this repository.
To update those, call:

    ./ensure_dependencies.py

## Library

Building with Ant
------------------

### Requirements

* [The Android SDK](http://developer.android.com/sdk)
* [The Android NDK](https://developer.android.com/tools/sdk/ndk)
* [Ant](http://ant.apache.org)

### Building

In the 'libadblockplus-android' directory create the file _local.properties_ and set
_sdk.dir_ and _ndk.dir_ to where you installed it, e.g.:

    sdk.dir = /some/where/sdk
    ndk.dir = /some/where/ndk

Then run:

    ant debug

Building with Maven
-------------------

### Requirements

All 'Building with Ant' requirements and additional requirements:

* [Maven](https://maven.apache.org)

### Building

Set environment variable ANDROID_HOME to your Android SDK directory or pass it in command-line (below).
In the 'libadblockplus-android' directory run:

	mvn clean install [-Dandroid.sdk.path=/some/where/sdk]

This will generate *.aar library artifact in the 'target' directory. 

Building with Gradle/Android Studio
-----------------------------------

### Requirements

* [The Android SDK](http://developer.android.com/sdk)
* Android SDK Build tools 22.0.1
* [The Android NDK](https://developer.android.com/tools/sdk/ndk)

Edit 'buildToolsVersion' in 'build.gradle' files if necessary.

### Building from command-line

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
