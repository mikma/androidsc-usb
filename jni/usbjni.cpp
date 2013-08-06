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
JavaVM* gVM;
jobject gCallback;
jclass gClsCallback;
jclass gClsUsbDevice;
jmethodID gid_getdevicelist;
jmethodID gid_getvendorid;
jmethodID gid_getproductid;
jmethodID gid_getclass;
jmethodID gid_getsubclass;
jmethodID gid_getdevname;
jmethodID gid_getdeviceid;
jmethodID gid_getinterfacecount;

extern "C" {
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback);
};

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback)
{
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "setCallback\n");
	jint res = env->GetJavaVM(&gVM);
	printf("setCallback VM %d %p\n", res, gVM);
	gCallback = env->NewGlobalRef(callback);

	gClsCallback = (jclass)env->NewGlobalRef(env->FindClass("se/m7n/android/libusb/LibUsb$Callback"));
	gClsUsbDevice = (jclass)env->NewGlobalRef(env->FindClass("android/hardware/usb/UsbDevice"));
	gid_getdevicelist = env->GetMethodID(gClsCallback, "getDeviceList", "()[Ljava/lang/Object;");
	gid_getvendorid = env->GetMethodID(gClsUsbDevice, "getVendorId", "()I");
	gid_getproductid = env->GetMethodID(gClsUsbDevice, "getProductId", "()I");
	gid_getclass = env->GetMethodID(gClsUsbDevice, "getDeviceClass", "()I");
	gid_getsubclass = env->GetMethodID(gClsUsbDevice, "getDeviceSubclass", "()I");
	gid_getdevname = env->GetMethodID(gClsUsbDevice, "getDeviceName", "()Ljava/lang/String;");
	gid_getdeviceid = env->GetMethodID(gClsUsbDevice, "getDeviceId", "()I");
	gid_getinterfacecount = env->GetMethodID(gClsUsbDevice, "getInterfaceCount", "()I");
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

	JNIEnv *env=NULL;
	jint res = gVM->AttachCurrentThread(&env, NULL);

	printf("libusb_get_device_list attach %d %p\n", res, env);

	jobjectArray device_list = (jobjectArray)env->CallObjectMethod(gCallback, gid_getdevicelist);

	printf("libusb_get_device_list foo %d\n", 42);

	int num = env->GetArrayLength(device_list);
	libusb_device **list_p = new libusb_device*[num+1];

	for (int i=0; i<num; i++) {
		list_p[i] = new libusb_device();
		__android_log_print(ANDROID_LOG_DEBUG, TAG, "libusb_get_device_list %d\n", c++);
		//jobject obj = env->NewLocalRef(env->GetObjectArrayElement(device_list, i));
		jobject obj = env->GetObjectArrayElement(device_list, i);
		if (env->IsInstanceOf(obj, gClsUsbDevice)) {
			jint idVendor = env->CallIntMethod(obj, gid_getvendorid);
			jint idProduct = env->CallIntMethod(obj, gid_getproductid);
			jint bDeviceClass = env->CallIntMethod(obj, gid_getclass);
			jint bDeviceSubClass= env->CallIntMethod(obj, gid_getsubclass);
			jstring devName = (jstring)env->CallObjectMethod(obj, gid_getdevname);
			unsigned char isCopy = false;
			const char *utf8 = env->GetStringUTFChars(devName, &isCopy);

			printf("libusb_get_device_list %04x:%04x %d %d '%s'", idVendor, idProduct, bDeviceClass, bDeviceSubClass, utf8);
			env->ReleaseStringUTFChars(devName, utf8);
			list_p[i]->obj = env->NewLocalRef(obj);
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

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	while(list[num] != NULL) num++;

	for (int i=0; i<num; i++) {
		env->DeleteLocalRef(list[i]->obj);
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

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	jobject obj = dev->obj;
	desc->idVendor = env->CallIntMethod(obj, gid_getvendorid);
	desc->idProduct = env->CallIntMethod(obj, gid_getproductid);

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

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	jobject obj = dev->obj;
	int addr = env->CallIntMethod(obj, gid_getdeviceid);

	printf("libusb_get_device_address %08x", addr);
	return addr;
}

int libusb_bulk_transfer(libusb_device_handle *dev_handle,
	unsigned char endpoint, unsigned char *data, int length,
	int *actual_length, unsigned int timeout)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

int libusb_reset_device(libusb_device_handle *dev)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

int libusb_claim_interface(libusb_device_handle *dev, int iface)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

int libusb_release_interface(libusb_device_handle *dev, int iface)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

int libusb_control_transfer(libusb_device_handle *dev_handle,
	uint8_t request_type, uint8_t request, uint16_t value, uint16_t index,
	unsigned char *data, uint16_t length, unsigned int timeout)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

int libusb_open(libusb_device *dev, libusb_device_handle **handle)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

void libusb_close(libusb_device_handle *dev_handle)
{
	// FIXME
}

struct libusb_transfer *libusb_alloc_transfer(int iso_packets)
{
	// FIXME
	return NULL;
}

int libusb_submit_transfer(struct libusb_transfer *transfer)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

int libusb_cancel_transfer(struct libusb_transfer *transfer)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

void libusb_free_transfer(struct libusb_transfer *transfer)
{
	// FIXME
}

int libusb_get_string_descriptor_ascii(libusb_device_handle *dev,
	uint8_t index, unsigned char *data, int length)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

int libusb_get_active_config_descriptor(libusb_device *dev,
	struct libusb_config_descriptor **config)
{
	// FIXME
	if (dev == NULL || config == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	struct libusb_config_descriptor *config_p = new libusb_config_descriptor;

	memset(config_p, 0, sizeof(*config_p));
	config_p->bLength = sizeof(*config_p);

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	jobject obj = dev->obj;
	config_p->bNumInterfaces = env->CallIntMethod(obj, gid_getinterfacecount);

	printf("libusb_get_active_config_descriptor %d", config_p->bNumInterfaces);
	// TODO more entries

        *config = config_p;
	return LIBUSB_SUCCESS;
}

int libusb_handle_events(libusb_context *ctx)
{
	// FIXME
	return LIBUSB_ERROR_OTHER;
}

void libusb_free_config_descriptor(struct libusb_config_descriptor *config)
{
	delete config;
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
