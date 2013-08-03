LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libusb
#LOCAL_CFLAGS	:= -I$(LOCAL_PATH)
LOCAL_SRC_FILES := usbjni.cpp
#LOCAL_LDLIBS	:= -llog
#LOCAL_PRELINK_MODULE := false

include $(BUILD_STATIC_LIBRARY)

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
