/*
 * main_jni.cpp
 *
 *  Created on: 4 Aug 2013
 *      Author: mikael
 */

#include <jni.h>
#include <android/log.h>
//#include <libusb/libusb.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include "coffeecatch/coffeecatch.h"
#include "coffeecatch/coffeejni.h"

#define TAG "libusb"

extern "C" {
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_scardcontrol(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_lsusb(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_pcscmain(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_pcscproxymain(JNIEnv * env, jobject obj, jobject callback, jstring socketName);
	int main(int argc, char *argv[]);
	int pcsc_main(int argc, char *argv[]);
	int pcsc_proxy_main(int argc, char *argv[]);
	int scardcontrol_main(int argc, char *argv[]);
};

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_scardcontrol(JNIEnv * env, jobject obj, jobject callback)
{
	int argc = 1;
	char *argv[] = {"lsusb", NULL};

	printf("Before lsusb main");
        // Reset getopt
        optind = 1;
#ifdef COFFEE_TRY_JNI
	COFFEE_TRY_JNI(scardcontrol_main(argc, argv));
#else
	scardcontrol_main(argc, argv);
#endif
	printf("After lsusb main");
}

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_lsusb(JNIEnv * env, jobject obj, jobject callback)
{
	int argc = 1;
	char *argv[] = {"lsusb", NULL};

	printf("Before lsusb main");
        // Reset getopt
        optind = 1;
#ifdef COFFEE_TRY_JNI
	COFFEE_TRY_JNI(main(argc, argv));
#else
	main(argc, argv);
#endif
	printf("After lsusb main");
}

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_pcscmain(JNIEnv * env, jobject obj, jobject callback)
{
	int argc = 5;
	char *argv[] = {strdup("pcscd"), "-f", "-d", "-c", "/dev/null", NULL};
	printf("Before main");
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "Before pcscd main");
        // Reset getopt
        optind = 1;
#ifdef COFFEE_TRY_JNI
	COFFEE_TRY_JNI(env, pcsc_main(argc, argv));
#else
	pcsc_main(argc, argv);
#endif
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "After pcscd main");
}

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_pcscproxymain(JNIEnv * env, jobject obj, jobject callback, jstring socketName)
{
	int argc = 6;
        // TODO make writeable copy?
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "Enter pcscproxymain %p", socketName);
        char *utf8Name = (char*)env->GetStringUTFChars(socketName, NULL);

	// FIXME throw exception
	if (utf8Name == NULL)
		return;

	char *argv[] = {strdup("pcsc-proxy"), "-f", "u", "-b", utf8Name, "-u", NULL};

        // Reset getopt
        optind = 1;
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "Before pcsc-proxy main");
#ifdef COFFEE_TRY_JNI
	COFFEE_TRY_JNI(env, pcscproxymain_protected(env, obj, callback, socketName));
#else
	pcsc_proxy_main(argc, argv);
#endif
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "After pcsc-proxy main");
	env->ReleaseStringUTFChars(socketName, utf8Name);
}
