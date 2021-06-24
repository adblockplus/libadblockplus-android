# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

string(TOLOWER "${CMAKE_BUILD_TYPE}" ENGINE_BUILD_TYPE)

if (ANDROID_ABI)
    set(LIBRARY_OS_NAME "android")
    set(LIBRARY_ABI "${ANDROID_ABI}")
else()
    set(LIBRARY_OS_NAME "linux")
    set(LIBRARY_ABI "amd64")
endif()

get_filename_component(
        FBJNI_LIBRARY_PATH
        "../hermes-libs/build/cmake/fbjni/${ENGINE_BUILD_TYPE}/${LIBRARY_OS_NAME}/${LIBRARY_ABI}/libfbjni.so"
        REALPATH )
get_filename_component(
        HERMES_LIBRARY_PATH
        "../hermes-libs/build/cmake/hermes/${ENGINE_BUILD_TYPE}/${LIBRARY_OS_NAME}/${LIBRARY_ABI}/API/hermes/libhermes.so"
        REALPATH )

add_library(fbjni SHARED IMPORTED)
set_property(TARGET fbjni PROPERTY
        IMPORTED_LOCATION
        "${FBJNI_LIBRARY_PATH}" )

add_library(hermes SHARED IMPORTED)
set_property(TARGET hermes PROPERTY
        IMPORTED_LOCATION
        "${HERMES_LIBRARY_PATH}" )

add_library(
        engine
        SHARED
        src/main/cpp/native-lib.cpp
        src/main/cpp/Engine.cpp
        src/main/cpp/DefaultFileSystem.cpp
        src/main/cpp/JsFileSystem.cpp
        src/main/cpp/MappedFileBuffer.cpp
        src/main/cpp/Api.cpp )

target_include_directories(
        engine
        PUBLIC
        ../extern/fbjni/cxx
        ../extern/hermes/API
        ../extern/hermes/API/jsi
        ../extern/hermes/public )

set_property(TARGET engine PROPERTY CXX_STANDARD 14)

if (NOT ANDROID_ABI)
    if (NOT JAVA_HOME)
        message(FATAL_ERROR
                "fbjni requires JAVA_HOME to be defined for non-Android builds.")
    endif()
    target_include_directories(engine PUBLIC ${JAVA_HOME}/include)
    if (CMAKE_SYSTEM_NAME STREQUAL Linux)
        target_include_directories(engine PUBLIC ${JAVA_HOME}/include/linux)
    endif()
    if (CMAKE_SYSTEM_NAME STREQUAL Darwin)
        target_include_directories(engine PUBLIC ${JAVA_HOME}/include/darwin)
    endif()
endif()

# Adding android log library
if (ANDROID_ABI)
    # Finds library with name `log` from NDK and assigns it to the var `log-lib`
    find_library(log-lib log)

    target_link_libraries(engine fbjni hermes ${log-lib})
else()
    target_link_libraries(engine fbjni hermes)
endif()
