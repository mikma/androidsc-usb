LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := usbjni
LOCAL_CFLAGS	:= -I$(LOCAL_PATH)
LOCAL_SRC_FILES := usbjni.cpp lsusb.c
LOCAL_LDLIBS	:= -llog

include $(BUILD_SHARED_LIBRARY)

#include libusb/Android.mk
