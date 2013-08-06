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

struct libusb_device_handle {
	libusb_device *dev;
	jobject conn;           // UsbDeviceConnection;
};

libusb_context gContext;
JavaVM* gVM;
jobject gCallback;
jobject gManager;

jclass gClsCallback;
jclass gClsUsbDevice;
jclass gClsManager;
jclass gClsConnection;
jclass gClsInterface;

jmethodID gid_getdevicelist;
jmethodID gid_getvendorid;
jmethodID gid_getproductid;
jmethodID gid_getclass;
jmethodID gid_getsubclass;
jmethodID gid_getdevname;
jmethodID gid_getdeviceid;
jmethodID gid_getinterfacecount;
jmethodID gid_getinterface;

// UsbInterface
jmethodID gid_getinterfaceclass;

// UsbManager
jmethodID gid_opendevice;

// UsbDeviceConnection
jmethodID gid_connclose;
jmethodID gid_claiminterface;
jmethodID gid_releaseinterface;
jmethodID gid_controltransfer;

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
	gClsManager = (jclass)env->NewGlobalRef(env->FindClass("android/hardware/usb/UsbManager"));
	gClsInterface = (jclass)env->NewGlobalRef(env->FindClass("android/hardware/usb/UsbInterface"));
	gClsConnection = (jclass)env->NewGlobalRef(env->FindClass("android/hardware/usb/UsbDeviceConnection"));

	gid_getdevicelist = env->GetMethodID(gClsCallback, "getDeviceList", "()[Ljava/lang/Object;");

	jmethodID id_manager = env->GetMethodID(gClsCallback, "getUsbManager", "()Landroid/hardware/usb/UsbManager;");
	gManager = env->NewGlobalRef(env->CallObjectMethod(gCallback, id_manager));

	// UsbManager
	gid_opendevice = env->GetMethodID(gClsManager, "openDevice", "(Landroid/hardware/usb/UsbDevice;)Landroid/hardware/usb/UsbDeviceConnection;");

	// UsbDevice
	gid_getvendorid = env->GetMethodID(gClsUsbDevice, "getVendorId", "()I");
	gid_getproductid = env->GetMethodID(gClsUsbDevice, "getProductId", "()I");
	gid_getclass = env->GetMethodID(gClsUsbDevice, "getDeviceClass", "()I");
	gid_getsubclass = env->GetMethodID(gClsUsbDevice, "getDeviceSubclass", "()I");
	gid_getdevname = env->GetMethodID(gClsUsbDevice, "getDeviceName", "()Ljava/lang/String;");
	gid_getdeviceid = env->GetMethodID(gClsUsbDevice, "getDeviceId", "()I");
	gid_getinterfacecount = env->GetMethodID(gClsUsbDevice, "getInterfaceCount", "()I");
	gid_getinterface = env->GetMethodID(gClsUsbDevice, "getInterface", "(I)Landroid/hardware/usb/UsbInterface;");

	// UsbConnection
	gid_connclose = env->GetMethodID(gClsConnection, "close", "()V");
	gid_claiminterface = env->GetMethodID(gClsConnection, "claimInterface", "(Landroid/hardware/usb/UsbInterface;Z)Z");
	gid_releaseinterface = env->GetMethodID(gClsConnection, "releaseInterface", "(Landroid/hardware/usb/UsbInterface;)Z");
	gid_controltransfer = env->GetMethodID(gClsConnection, "controlTransfer", "(IIII[BII)I");

	// UsbInterface
	gid_getinterfaceclass = env->GetMethodID(gClsInterface, "getInterfaceClass", "()I");
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
	memset(list_p, 0, sizeof(*list_p));

	for (int i=0; i<num; i++) {
		list_p[i] = new libusb_device();
		memset(list_p[i], 0, sizeof(*list_p[i]));
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

int libusb_claim_interface(libusb_device_handle *handle, int iface)
{
	if (handle == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	int c=0;
	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	// Get interface
	jobject interface = env->CallObjectMethod(handle->dev->obj, gid_getinterface, iface);
	if (!interface)
		return LIBUSB_ERROR_NO_DEVICE;

	bool force = false;     // true to disconnect kernel driver if necessary
	bool res = env->CallBooleanMethod(handle->conn, gid_claiminterface, interface, force);
	if (!res)
		return LIBUSB_ERROR_ACCESS;

	return LIBUSB_SUCCESS;
}

int libusb_release_interface(libusb_device_handle *handle, int iface)
{
	if (handle == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	// Get interface
	jobject interface = env->CallObjectMethod(handle->dev->obj, gid_getinterface, iface);
	if (!interface)
		return LIBUSB_ERROR_NO_DEVICE;

	bool res = env->CallBooleanMethod(handle->dev->obj, gid_releaseinterface, iface);
	if (!res)
		return LIBUSB_ERROR_ACCESS;

	return LIBUSB_SUCCESS;
}

int libusb_control_transfer(libusb_device_handle *handle,
	uint8_t request_type, uint8_t request, uint16_t value, uint16_t index,
	unsigned char *data, uint16_t length, unsigned int timeout)
{
	if (handle == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	jbyteArray buffer = env->NewByteArray(length);

	if ((request_type & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_OUT)
		env->SetByteArrayRegion(buffer, 0, length, (const jbyte*)data);

	jint res = env->CallIntMethod(handle->conn, gid_controltransfer, request_type, request, value, index, buffer, length, timeout);

	if ((request_type & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_IN)
		env->GetByteArrayRegion(buffer, 0, length, (jbyte*)data);

	env->DeleteLocalRef(buffer); buffer = NULL;

	// TODO translate negative error?
	return res;
}

int libusb_open(libusb_device *dev, libusb_device_handle **handle)
{
	if (dev == NULL || handle == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	libusb_device_handle *handle_p = new libusb_device_handle;
	memset(handle_p, 0, sizeof(*handle_p));

	handle_p->dev = dev;
	handle_p->conn = env->CallObjectMethod(gManager, gid_opendevice, dev->obj);

	if (handle_p->conn == NULL)
		return LIBUSB_ERROR_NO_DEVICE;

	*handle = handle_p;
	return LIBUSB_SUCCESS;
}

void libusb_close(libusb_device_handle *handle)
{
	if (handle == NULL || handle->conn == NULL)
		return;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	env->CallVoidMethod(handle->conn, gid_connclose);
	handle->conn = NULL;
	delete handle;
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

static void ccid_setb(unsigned char *buf, int index, unsigned char value)
{
	buf[index] = value;
}

static void ccid_setw(unsigned char *buf, int index, unsigned short value)
{
	ccid_setb(buf, index, value & 255);
	ccid_setb(buf, index + 1, (value >> 8) & 255);
}

static void ccid_setdw(unsigned char *buf, int index, unsigned int value)
{
	ccid_setw(buf, index, value & 65535);
	ccid_setw(buf, index + 2, (value >> 16) & 65535);
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

	libusb_interface *interfaces = new libusb_interface[config_p->bNumInterfaces];
	memset(interfaces, 0, sizeof(*interfaces));

	config_p->interface = interfaces;

	for (int i=0; i < config_p->bNumInterfaces; i++) {
		interfaces[i].num_altsetting = 1;
		libusb_interface_descriptor *desc = new libusb_interface_descriptor[1];
		memset(desc, 0, sizeof(*desc));

		interfaces[i].altsetting = desc;

                desc->bLength = sizeof(*desc);

		jobject interface = env->CallObjectMethod(obj, gid_getinterface, i);
		printf("libusb_get_active_config_descriptor %p", interface);

		if (interface) {
			desc->bInterfaceClass = env->CallIntMethod(interface, gid_getinterfaceclass);
		}
		// FIXME interface number
		desc->bInterfaceNumber = 0;
		desc->extra_length = 54;
		unsigned char *extra = new unsigned char[desc->extra_length];
		memset(extra, 0, sizeof(*extra));

		desc->extra = extra;

		// dwFeatures
		ccid_setdw(extra, 40, 0x00010230);
		// wLcdLayout
		ccid_setw(extra, 50, 0);
		// bPINSupport
		ccid_setb(extra, 52, 0);
		// dwMaxCCIDMessageLength
		ccid_setdw(extra, 44, 271);
		// dwMaxIFSD
		ccid_setdw(extra, 28, 254);
		// dwDefaultClock
		ccid_setdw(extra, 10, 4000);
		// dwMaxDataRate
		ccid_setdw(extra, 23, 344086);
		// bMaxSlotIndex
		ccid_setb(extra, 4, 0);
		// bVoltageSupport
		ccid_setb(extra, 5, 7);
	}

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
