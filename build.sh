#!/bin/sh

NDK_MODULE_PATH=`pwd`/jni:`pwd`/../libusb.d ndk-build && ant debug && adb install -r bin/LibUsb-debug.apk && tail -f /tmp/logcat.txt

