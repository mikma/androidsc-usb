/*
 * main_jni.cpp
 *
 *  Created on: 4 Aug 2013
 *      Author: mikael
 */

#include <jni.h>
//#include <android/log.h>
//#include <libusb/libusb.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>


extern "C" {
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_lsusb(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_pcscmain(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_pcscproxymain(JNIEnv * env, jobject obj, jobject callback);
	int main(int argc, char *argv[]);
	int pcsc_main(int argc, char *argv[]);
	int pcsc_proxy_main(int argc, char *argv[]);
	int scardcontrol_main(int argc, char *argv[]);
};

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_lsusb(JNIEnv * env, jobject obj, jobject callback)
{
	int argc = 1;
	char *argv[] = {"lsusb", NULL};

	printf("Before lsusb main");
	scardcontrol_main(argc, argv);
	printf("After lsusb main");
}

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_pcscmain(JNIEnv * env, jobject obj, jobject callback)
{
	int argc = 5;
	char *argv[] = {strdup("pcscd"), "-f", "-d", "-c", "/dev/null", NULL};
	printf("Before main");
	pcsc_main(argc, argv);
	printf("After main");
}

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_pcscproxymain(JNIEnv * env, jobject obj, jobject callback)
{
	int argc = 5;
	char *argv[] = {strdup("pcsc-proxy"), "-f", "u", "-b", "FIXME", NULL};

        // Reset getopt
        optind = 1;
	printf("Before pcsc-proxy main");
	pcsc_proxy_main(argc, argv);
	printf("After pcsc-proxy main");
}
