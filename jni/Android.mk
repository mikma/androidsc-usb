
PCSCD_PATH := /data/data/se.m7n.android.libusb/files

JNI_PATH := $(call my-dir)
PCSC_PATH:=$(JNI_PATH)/pcsc-lite

LOCAL_PATH := $(JNI_PATH)

include $(CLEAR_VARS)

LOCAL_MODULE    := usbjni
LOCAL_CFLAGS	:= -I$(LOCAL_PATH)
# Needed for coffeecatch
LOCAL_CFLAGS += -g -funwind-tables -Wl,--no-merge-exidx-entries
LOCAL_SRC_FILES := main_jni.cpp lsusb.c coffeecatch/coffeecatch.c coffeecatch/coffeejni.c
LOCAL_LDLIBS	:= -llog
#LOCAL_PRELINK_MODULE := false
LOCAL_SHARED_LIBRARIES := pcscd libscardcontrol libpcsc libpcscproxy
LOCAL_SHARED_LIBRARIES += libusb

include $(BUILD_SHARED_LIBRARY)

$(call import-module,ccid)
$(call import-module,pcsc-lite)
$(call import-module,pcsc-proxy)
