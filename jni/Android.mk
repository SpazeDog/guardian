LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS := -llog

include $(NDK_PROJECT_PATH)/src/native/Android.mk

include $(BUILD_SHARED_LIBRARY)

