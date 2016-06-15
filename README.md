Adblock Plus Library for Android
========================

An Android library project that runs a proxy to block ads.

Updating the dependencies
-------------------------

Adblock Plus Library for Android has dependencies that aren't in this repository.
To update those, call:

    ./ensure_dependencies.py

Building with Ant
------------------

### Requirements

- [The Android SDK](http://developer.android.com/sdk)
- [The Android NDK](https://developer.android.com/tools/sdk/ndk)
- [Ant](http://ant.apache.org)

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

- [Maven](https://maven.apache.org)

### Building

Set environment variable ANDROID_HOME to your Android SDK directory or pass it in command-line (below).
In the 'libadblockplus-android' directory run:

	mvn clean install [-Dandroid.sdk.path=/some/where/sdk]

This will generate *.aar library artifact in the 'target' directory. 
