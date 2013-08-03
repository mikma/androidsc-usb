#include <jni.h>
#include <android/log.h>
#include <libusb/libusb.h>
#include <string.h>
#include <stdio.h>

const char TAG[] = "LibUsb";

struct libusb_context {
	JNIEnv* env;
};

struct libusb_device {
	jobject obj;
};

libusb_context gContext;
JNIEnv* gEnv;
jobject gCallback;
jobject gManager;
jclass gClsManager;
jclass gClsCallback;

extern "C" {
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback);
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_lsusb(JNIEnv * env, jobject obj, jobject callback);
	int main(void);
};

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback)
{
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "setCallback\n");
	gCallback = env->NewLocalRef(callback);

	gClsCallback = (jclass)env->NewLocalRef(env->FindClass("se/m7n/android/libusb/LibUsb$Callback"));
	jmethodID id_manager = env->GetMethodID(gClsCallback, "getUsbManager", "()Landroid/hardware/usb/UsbManager;");
	gManager = env->NewLocalRef(env->CallObjectMethod(gCallback, id_manager));
	gClsManager = (jclass)env->NewLocalRef(env->FindClass("android/hardware/usb/UsbManager"));
}

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_lsusb(JNIEnv * env, jobject obj, jobject callback)
{
	gEnv = env;
	gCallback = callback;
	main();
}

int libusb_init(libusb_context **ctx)
{
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "libusb_init\n");
	printf("init success");
	return LIBUSB_SUCCESS;
}

void libusb_exit(libusb_context *ctx)
{
}

ssize_t libusb_get_device_list(libusb_context *ctx,
	libusb_device ***list)
{
	int c=0;
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "libusb_get_device_list %d\n", c++);

	gClsCallback = gEnv->FindClass("se/m7n/android/libusb/LibUsb$Callback");
	jclass clsUsbDevice = gEnv->FindClass("android/hardware/usb/UsbDevice");
	jmethodID id_getdevicelist = gEnv->GetMethodID(gClsCallback, "getDeviceList", "()[Ljava/lang/Object;");
	jobjectArray device_list = (jobjectArray)gEnv->CallObjectMethod(gCallback, id_getdevicelist);

	printf("libusb_get_device_list foo %d\n", 42);

	int num = gEnv->GetArrayLength(device_list);
	libusb_device **list_p = new libusb_device*[num+1];

	for (int i=0; i<num; i++) {
		list_p[i] = new libusb_device();
		__android_log_print(ANDROID_LOG_DEBUG, TAG, "libusb_get_device_list %d\n", c++);
		//jobject obj = gEnv->NewLocalRef(gEnv->GetObjectArrayElement(device_list, i));
		jobject obj = gEnv->GetObjectArrayElement(device_list, i);
		if (gEnv->IsInstanceOf(obj, clsUsbDevice)) {
			jmethodID id_getvendorid = gEnv->GetMethodID(clsUsbDevice, "getVendorId", "()I");
			jint idVendor = gEnv->CallIntMethod(obj, id_getvendorid);
			jmethodID id_getproductid = gEnv->GetMethodID(clsUsbDevice, "getProductId", "()I");
			jint idProduct = gEnv->CallIntMethod(obj, id_getproductid);
			jmethodID id_getclass = gEnv->GetMethodID(clsUsbDevice, "getDeviceClass", "()I");
			jint bDeviceClass = gEnv->CallIntMethod(obj, id_getclass);
			jmethodID id_getsubclass = gEnv->GetMethodID(clsUsbDevice, "getDeviceSubclass", "()I");
			jint bDeviceSubClass= gEnv->CallIntMethod(obj, id_getsubclass);
			jmethodID id_getdevname = gEnv->GetMethodID(clsUsbDevice, "getDeviceName", "()Ljava/lang/String;");
			jstring devName = (jstring)gEnv->CallObjectMethod(obj, id_getdevname);
			unsigned char isCopy = false;
			const char *utf8 = gEnv->GetStringUTFChars(devName, &isCopy);

			printf("libusb_get_device_list %04x:%04x %d %d '%s'", idVendor, idProduct, bDeviceClass, bDeviceSubClass, utf8);
			gEnv->ReleaseStringUTFChars(devName, utf8);
			list_p[i]->obj = gEnv->NewLocalRef(obj);
		} else {
			// FIXME throw error?
			__android_log_print(ANDROID_LOG_DEBUG, TAG, "libusb_get_device_list not UsbDevice %d\n", c++);
		}
		__android_log_print(ANDROID_LOG_DEBUG, TAG, "libusb_get_device_list %d\n", c++);
	}
	list_p[num] = 0;
	*list = list_p;

	__android_log_print(ANDROID_LOG_DEBUG, TAG, "libusb_get_device_list exit %d\n", num);
	return num;
}

void libusb_free_device_list(libusb_device **list, int unref_devices)
{
	int num = 0;
	// TODO unref_devices

	while(list[num] != NULL) num++;

	for (int i=0; i<num; i++) {
		gEnv->DeleteLocalRef(list[i]->obj);
		delete list[i];
	}

	delete[] list;
}

int libusb_get_device_descriptor(libusb_device *dev,
	struct libusb_device_descriptor *desc)
{
	if (dev == NULL || desc == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	memset(desc, 0, sizeof(*desc));
	desc->bLength = sizeof(*desc);

	jclass clsUsbDevice = gEnv->FindClass("android/hardware/usb/UsbDevice");

	jobject obj = dev->obj;
	jmethodID id_getvendorid = gEnv->GetMethodID(clsUsbDevice, "getVendorId", "()I");
	jmethodID id_getproductid = gEnv->GetMethodID(clsUsbDevice, "getProductId", "()I");
	desc->idVendor = gEnv->CallIntMethod(obj, id_getvendorid);
	desc->idProduct = gEnv->CallIntMethod(obj, id_getproductid);

	printf("libusb_get_device_descriptor %04x:%04x", desc->idVendor, desc->idProduct);
	// TODO more entries

	return LIBUSB_SUCCESS;
}

uint8_t libusb_get_bus_number(libusb_device *dev)
{
	if (dev == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	jobject obj = dev->obj;

	// TODO do Android have more than one bus?
	return 0;
}

uint8_t libusb_get_device_address(libusb_device *dev)
{
	if (dev == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	jobject obj = dev->obj;
	jclass clsUsbDevice = gEnv->FindClass("android/hardware/usb/UsbDevice");
	jmethodID id_getdeviceid = gEnv->GetMethodID(clsUsbDevice, "getDeviceId", "()I");
	int addr = gEnv->CallIntMethod(obj, id_getdeviceid);

	printf("libusb_get_device_address %08x", addr);
	return addr;
}

int printf(const char *format, ...)
{
	va_list ap;
	va_start(ap, format);
	return __android_log_vprint(ANDROID_LOG_DEBUG, TAG, format, ap);
}

#if 0
int fprintf(FILE *out, const char *format, ...)
{
	va_list ap;
	va_start(ap, format);

	int level = ANDROID_LOG_DEBUG;
	if (out == stderr)
		level = ANDROID_LOG_ERROR;

	return __android_log_print(ANDROID_LOG_DEBUG, TAG, format, ap);
}
#endif
