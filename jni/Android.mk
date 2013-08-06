
PCSCD_PATH := /data/data/se.m7n.android.libusb/files

JNI_PATH := $(call my-dir)
PCSC_PATH:=$(JNI_PATH)/pcsc-lite

LOCAL_PATH := $(JNI_PATH)

include $(CLEAR_VARS)

LOCAL_MODULE    := usbjni
LOCAL_CFLAGS	:= -I$(LOCAL_PATH)
LOCAL_SRC_FILES := main_jni.cpp lsusb.c
LOCAL_LDLIBS	:= -llog
LOCAL_STATIC_LIBRARIES := libusb
#LOCAL_PRELINK_MODULE := false
#LOCAL_SHARED_LIBRARIES := libusb libccid

include $(BUILD_SHARED_LIBRARY)

#include $(LOCAL_PATH)/ccid/Android.mk
#include libusb/Android.mk
