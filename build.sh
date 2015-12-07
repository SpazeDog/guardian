#!/bin/bash

BUILD_PATH="$(readlink -f "$(dirname $0)")"
BUILD_TYPE="$1"
BUILD_HOME=~/.gradle/build/$(basename "$BUILD_PATH")/guardian

export PATH="$PATH:$BUILD_PATH"

if [ -z "$BUILD_TYPE" ] || [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" ]]; then
    BUILD_TYPE="release"
fi

if [ -f "$BUILD_PATH/gradlew" ]; then
    chmod +x "$BUILD_PATH/gradlew" 2> /dev/null
fi

if [ -z "$NDK_BUILD" ] && which ndk-build > /dev/null 2>&1; then
    NDK_BUILD="$(readlink -f "$(which ndk-build)")"
fi

if [ -n "$NDK_BUILD" ] && which "$NDK_BUILD" > /dev/null 2>&1; then
    if which gradlew > /dev/null 2>&1; then
        # Build Native Libraries
        cd "$BUILD_PATH"
        "$NDK_BUILD" clean || exit 1
        "$NDK_BUILD" || exit 1

        if [ ! -d "$BUILD_PATH/src/libs" ]; then
            mkdir "$BUILD_PATH/src/libs"
        fi

        cd "$BUILD_PATH/obj/out"
        rm -rf "$BUILD_PATH/src/libs/processScanner-native.jar" 2> /dev/null
        zip -r "$BUILD_PATH/src/libs/processScanner-native.jar" lib/ || exit 1

        # Build Guardian
        cd "$BUILD_PATH/projects/guardian" || exit 1
        gradlew clean || exit 1
        gradlew build || exit 1

    else
        echo "You need to setup Gradle to build this project"
    fi

else
    echo "You need to setup ndk-build variable NDK_BUILD"
fi

