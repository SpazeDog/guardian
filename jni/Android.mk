LOCAL_PATH := $(call my-dir)
PKG_PATH := ../src/com/spazedog/guardian

include $(CLEAR_VARS)

LOCAL_LDLIBS := -llog

LOCAL_MODULE    := processScanner
LOCAL_SRC_FILES := $(PKG_PATH)/scanner/ProcessScanner.cpp

include $(BUILD_SHARED_LIBRARY)