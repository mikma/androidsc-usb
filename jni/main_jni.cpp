/*
 * main_jni.cpp
 *
 *  Created on: 4 Aug 2013
 *      Author: mikael
 */

#include <jni.h>
//#include <android/log.h>
//#include <libusb/libusb.h>
//#include <string.h>
//#include <stdio.h>


extern "C" {
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_lsusb(JNIEnv * env, jobject obj, jobject callback);
	int main(void);
};

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_lsusb(JNIEnv * env, jobject obj, jobject callback)
{
	main();
}

