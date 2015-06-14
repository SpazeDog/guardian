# Android has a very limited C++ library by default. This means that in order to fully use 
# C++ with Android, we need to make a static build. 
# 
# The c++_static implementation contains issues that stops it from building in most cases. 
# The gnustl_static has some 'NewStringUTF' crash issues. 
# stlport_static seams stable so far. 
APP_STL := stlport_static

# The GNU STL implementation does not enable c++11 by default
APP_CPPFLAGS += -std=c++11

# Make sure that we do not use the 4.6 version (Wonder when Google will add 4.9?)
NDK_TOOLCHAIN_VERSION := 4.8

# Make sure that we have a path to the header files
LOCAL_C_INCLUDES += ${ANDROID_NDK}/sources/cxx-stl/gnu-libstdc++/${NDK_TOOLCHAIN_VERSION}/include

# This should match out minimum SDK in AndroidManifest.xml
APP_PLATFORM := android-14

# Build for all Android platforms supported by NDK r10c
# 
#  - armeabi
#  - armeabi-v7a
#  - arm64-v8a
#  - x86
#  - x86_64
#  - mips
#  - mips64
# 
APP_ABI := all

# Compile state, either 'debug' or 'release'
ifeq ($(BUILD_TYPE),debug)
    APP_OPTIM := debug
else
    APP_OPTIM := release
endif

# Overwrite the compiled output directory
NDK_APP_LIBS_OUT := obj/out/lib

-include $(NDK_PROJECT_PATH)/src/native/Application.mk

