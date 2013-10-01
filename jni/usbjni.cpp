#include <jni.h>
#include <android/log.h>
#include <libusb/libusb.h>
#include <string.h>
#include <stdio.h>
#include <algorithm>

const char TAG[] = "LibUsb";

struct libusb_context {
	JNIEnv* env;
};

struct libusb_device {
	jbyte refs;
	jobject obj;
};

struct libusb_device_handle {
	libusb_device *dev;
	jobject conn;           // UsbDeviceConnection;
};

class libusb_transfer_jni: public libusb_transfer {
public:
	jobject req;		// UsbRequest
	jobject buffer_obj;
};

libusb_context gContext;
JavaVM* gVM;
jobject gCallback;
jobject gManager;

jclass gClsUsbDevice;
jclass gClsRequest;
jclass gClsLong;
jclass gClsByteBuffer;

// Callback
jmethodID gid_findendpoint;
jmethodID gid_submittransfer;
jmethodID gid_cbbulktransfer;

// UsbDevice
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
jmethodID gid_getendpointcount;
jmethodID gid_getendpoint;

// UsbEndpoint
jmethodID gid_getaddress;
jmethodID gid_getattributes;
jmethodID gid_getmaxpacketsize;
jmethodID gid_interval;

// UsbManager
jmethodID gid_opendevice;

// UsbDeviceConnection
jmethodID gid_connclose;
jmethodID gid_claiminterface;
jmethodID gid_releaseinterface;
jmethodID gid_controltransfer;
jmethodID gid_bulktransfer;
jmethodID gid_requestwait;

// UsbRequest
jmethodID gid_newrequest;
jmethodID gid_setclientdata;
jmethodID gid_getclientdata;
jmethodID gid_initialize;
jmethodID gid_queue;

// Long
jmethodID gid_newlong;
jmethodID gid_longvalue;

// ByteBuffer
jmethodID gid_allocate;
jmethodID gid_allocatedirect;
jmethodID gid_put;
jmethodID gid_get;
jmethodID gid_rewind;
jmethodID gid_limit;
jmethodID gid_position;
jmethodID gid_capacity;

extern "C" {
	JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback);
};

JNIEXPORT void JNICALL Java_se_m7n_android_libusb_LibUsb_setCallback(JNIEnv * env, jobject obj, jobject callback)
{
	__android_log_print(ANDROID_LOG_DEBUG, TAG, "setCallback\n");
	jint res = env->GetJavaVM(&gVM);
	printf("setCallback VM %d %p\n", res, gVM);
	gCallback = env->NewGlobalRef(callback);

	jclass clsCallback = env->FindClass("se/m7n/android/libusb/LibUsb$Callback");
	gClsUsbDevice = (jclass)env->NewGlobalRef(env->FindClass("android/hardware/usb/UsbDevice"));
	jclass clsManager = env->FindClass("android/hardware/usb/UsbManager");
	jclass clsInterface = env->FindClass("android/hardware/usb/UsbInterface");
	jclass clsConnection = env->FindClass("android/hardware/usb/UsbDeviceConnection");
	jclass clsEndpoint = env->FindClass("android/hardware/usb/UsbEndpoint");
	gClsRequest = (jclass)env->NewGlobalRef(env->FindClass("android/hardware/usb/UsbRequest"));
	gClsLong = (jclass)env->NewGlobalRef(env->FindClass("java/lang/Long"));
	gClsByteBuffer = (jclass)env->NewGlobalRef(env->FindClass("java/nio/ByteBuffer"));

	gid_getdevicelist = env->GetMethodID(clsCallback, "getDeviceList", "()[Ljava/lang/Object;");

	jmethodID id_manager = env->GetMethodID(clsCallback, "getUsbManager", "()Landroid/hardware/usb/UsbManager;");
	gManager = env->NewGlobalRef(env->CallObjectMethod(gCallback, id_manager));

	// Callback
	gid_findendpoint = env->GetMethodID(clsCallback, "findEndpoint", "(Landroid/hardware/usb/UsbDevice;I)Landroid/hardware/usb/UsbEndpoint;");
	gid_submittransfer = env->GetMethodID(clsCallback, "submitTransfer", "(Landroid/hardware/usb/UsbDeviceConnection;Landroid/hardware/usb/UsbDevice;Landroid/hardware/usb/UsbRequest;Ljava/nio/ByteBuffer;II)Z");
	gid_cbbulktransfer = env->GetMethodID(clsCallback, "bulkTransfer", "(Landroid/hardware/usb/UsbDeviceConnection;Landroid/hardware/usb/UsbEndpoint;[BII)I");

	// UsbManager
	gid_opendevice = env->GetMethodID(clsManager, "openDevice", "(Landroid/hardware/usb/UsbDevice;)Landroid/hardware/usb/UsbDeviceConnection;");

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
	gid_connclose = env->GetMethodID(clsConnection, "close", "()V");
	gid_claiminterface = env->GetMethodID(clsConnection, "claimInterface", "(Landroid/hardware/usb/UsbInterface;Z)Z");
	gid_releaseinterface = env->GetMethodID(clsConnection, "releaseInterface", "(Landroid/hardware/usb/UsbInterface;)Z");
	gid_controltransfer = env->GetMethodID(clsConnection, "controlTransfer", "(IIII[BII)I");
	gid_bulktransfer = env->GetMethodID(clsConnection, "bulkTransfer", "(Landroid/hardware/usb/UsbEndpoint;[BII)I");
	gid_requestwait = env->GetMethodID(clsConnection, "requestWait", "()Landroid/hardware/usb/UsbRequest;");

	// UsbInterface
	gid_getinterfaceclass = env->GetMethodID(clsInterface, "getInterfaceClass", "()I");
	gid_getendpointcount = env->GetMethodID(clsInterface, "getEndpointCount", "()I");
	gid_getendpoint = env->GetMethodID(clsInterface, "getEndpoint", "(I)Landroid/hardware/usb/UsbEndpoint;");

	// UsbEndpoint
	gid_getaddress = env->GetMethodID(clsEndpoint, "getAddress", "()I");
	gid_getattributes = env->GetMethodID(clsEndpoint, "getAttributes", "()I");;
	gid_getmaxpacketsize = env->GetMethodID(clsEndpoint, "getMaxPacketSize", "()I");;
	gid_interval = env->GetMethodID(clsEndpoint, "getInterval", "()I");;

	// UsbRequest
	gid_newrequest = env->GetMethodID(gClsRequest, "<init>", "()V");
	gid_setclientdata = env->GetMethodID(gClsRequest, "setClientData", "(Ljava/lang/Object;)V");
	gid_getclientdata = env->GetMethodID(gClsRequest, "getClientData", "()Ljava/lang/Object;");
	gid_queue = env->GetMethodID(gClsRequest, "queue", "(Ljava/nio/ByteBuffer;I)Z");
	gid_initialize = env->GetMethodID(gClsRequest, "initialize", "(Landroid/hardware/usb/UsbDeviceConnection;Landroid/hardware/usb/UsbEndpoint;)Z");

	// Long
	gid_newlong = env->GetMethodID(gClsLong, "<init>", "(J)V");
	gid_longvalue = env->GetMethodID(gClsLong, "longValue", "()J");

	// ByteBuffer
	gid_allocate = env->GetStaticMethodID(gClsByteBuffer, "allocate", "(I)Ljava/nio/ByteBuffer;");
	gid_allocatedirect = env->GetStaticMethodID(gClsByteBuffer, "allocateDirect", "(I)Ljava/nio/ByteBuffer;");
	gid_put = env->GetMethodID(gClsByteBuffer, "put", "([B)Ljava/nio/ByteBuffer;");
	gid_get = env->GetMethodID(gClsByteBuffer, "get", "([B)Ljava/nio/ByteBuffer;");
	gid_rewind = env->GetMethodID(gClsByteBuffer, "rewind", "()Ljava/nio/Buffer;");
	gid_limit = env->GetMethodID(gClsByteBuffer, "limit", "()I");
	gid_position = env->GetMethodID(gClsByteBuffer, "position", "()I");
	gid_capacity = env->GetMethodID(gClsByteBuffer, "capacity", "()I");
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
	JNIEnv *env=NULL;
	jint res = gVM->AttachCurrentThread(&env, NULL);

	jobjectArray device_list = (jobjectArray)env->CallObjectMethod(gCallback, gid_getdevicelist);

	int num = env->GetArrayLength(device_list);
	libusb_device **list_p = new libusb_device*[num+1];
	memset(list_p, 0, sizeof(*list_p) * (num+1));

	for (int i=0; i<num; i++) {
		list_p[i] = new libusb_device();
		memset(list_p[i], 0, sizeof(*list_p[i]));
		list_p[i]->refs = 1;
		//jobject obj = env->NewLocalRef(env->GetObjectArrayElement(device_list, i));
		jobject obj = env->GetObjectArrayElement(device_list, i);
		if (env->IsInstanceOf(obj, gClsUsbDevice)) {
			jint idVendor = env->CallIntMethod(obj, gid_getvendorid);
			jint idProduct = env->CallIntMethod(obj, gid_getproductid);
			jint bDeviceClass = env->CallIntMethod(obj, gid_getclass);
			jint bDeviceSubClass= env->CallIntMethod(obj, gid_getsubclass);
			jstring devName = (jstring)env->CallObjectMethod(obj, gid_getdevname);
			unsigned char isCopy = false;
			list_p[i]->obj = env->NewGlobalRef(obj);
		} else {
			// FIXME throw error?
			printf("libusb_get_device_list not anUsbDevice");
		}
	}
	list_p[num] = 0;
	*list = list_p;

	return num;
}

void libusb_free_device_list(libusb_device **list, int unref_devices)
{
	int num = 0;
	// TODO unref_devices

	printf("libusb_free_device_list %p %p %d", list, *list, unref_devices);
	// // TODO skip free now
	// return;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	while(list[num] != NULL) num++;

	for (int i=0; i<num; i++) {
		libusb_unref_device(list[i]);
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

#if 0
static int find_endpoint(JNIEnv *env, libusb_device_handle *handle, int endpoint_id, jobject *endpoint_p, int *dir)
{
	int c=0;
	printf("find_endpoint %d %p %p", c++, env, handle->dev->obj);
	int num_ifaces = env->CallIntMethod(handle->dev->obj, gid_getinterfacecount);

	printf("find_endpoint %d %p %d", c++, env, num_ifaces);

	for (int i=0; i < num_ifaces; i++) {
		printf("find_endpoint %d %d", c++, i);
		// Get interface
		jobject interface = env->CallObjectMethod(handle->dev->obj, gid_getinterface, i);
		if (!interface)
			return LIBUSB_ERROR_NO_DEVICE;

		printf("find_endpoint %d %p", c++, interface);

		jint num_endpoints = env->CallIntMethod(interface, gid_getendpointcount);
		printf("find_endpoint %d %d", c++, num_endpoints);
		for (int j=0; j < num_endpoints; j++) {
			jobject endpoint = env->CallObjectMethod(interface, gid_getendpoint, j);
			printf("find_endpoint %d %p", c++, endpoint);
			if (endpoint == NULL)
				continue;
			jint id = env->CallIntMethod(endpoint, gid_getaddress);
			printf("find_endpoint %d %d", c++, id);
			if (endpoint_id == id) {
				printf("find_endpoint %d found", c++);
				*endpoint_p = endpoint;
				return LIBUSB_SUCCESS;
			}
		}

		// TODO release interface?
	}

	return LIBUSB_ERROR_NOT_FOUND;
}
#endif


int libusb_bulk_transfer(libusb_device_handle *handle,
	unsigned char endpoint_id, unsigned char *data, int length,
	int *actual_length, unsigned int timeout)
{
	if (handle == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	jobject endpoint = NULL;
	int dir = false;

	endpoint = env->CallObjectMethod(gCallback, gid_findendpoint, handle->dev->obj, endpoint_id);
	if (endpoint == NULL)
		return LIBUSB_ERROR_NOT_FOUND;
	// if (find_endpoint(env, handle, endpoint_id, &endpoint, &dir) < 0)
	// 	return LIBUSB_ERROR_NOT_FOUND;

	jbyteArray buffer = env->NewByteArray(length);

	if ((endpoint_id & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_OUT)
		env->SetByteArrayRegion(buffer, 0, length, (const jbyte*)data);

#if 0
        jint timeout_new = 2000;
        printf("Bulk transfer timeout %d->%d", timeout, timeout_new);
        timeout = timeout_new;
#endif

#if 1
	jint res = env->CallIntMethod(handle->conn, gid_bulktransfer, endpoint, buffer, length, timeout);
#else
        jint res = env->CallIntMethod(gCallback, gid_cbbulktransfer, handle->conn, endpoint, buffer, length, timeout);
#endif

        printf("Bulk transfer res %d", res);

	if ((res > 0) && ((endpoint_id & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_IN)) {
		env->GetByteArrayRegion(buffer, 0, res, (jbyte*)data);
	}

	if (res >= 0)
		*actual_length = res;

	env->DeleteLocalRef(buffer); buffer = NULL;
	env->DeleteLocalRef(endpoint); endpoint = NULL;

	// TODO translate negative error?
	return res;
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

#if 1
	jint timeout_new = 2000;
	printf("libusb_control_transfer timeout %d->%d", timeout, timeout_new);
	timeout = timeout_new;
#endif

	jint res = env->CallIntMethod(handle->conn, gid_controltransfer, request_type, request, value, index, buffer, length, timeout);

	printf("libusb_control_transfer res %d", res);

	if ((res > 0) && (request_type & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_IN)
		env->GetByteArrayRegion(buffer, 0, res, (jbyte*)data);

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
	handle_p->conn = env->NewGlobalRef(env->CallObjectMethod(gManager, gid_opendevice, dev->obj));

	printf("libusb_open2 %p %p %p %p", handle_p, handle_p->dev, handle_p->dev->obj, handle_p->conn);

	if (handle_p->conn == NULL)
		return LIBUSB_ERROR_NO_DEVICE;

	libusb_ref_device(handle_p->dev);
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
	env->DeleteGlobalRef(handle->conn);
	handle->conn = NULL;
	libusb_unref_device(handle->dev);
	delete handle;
}

libusb_device *libusb_ref_device(libusb_device *dev)
{
	printf("libusb_ref_device %p %d %p", dev, dev->refs, dev->obj);
	dev->refs++;
}

void libusb_unref_device(libusb_device *dev)
{
	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	printf("libusb_unref_device %p %d %p", dev, dev->refs, dev->obj);

	if (dev->refs-- == 0) {
		printf("libusb_unref_device free global ref %p", dev->obj);
		env->DeleteGlobalRef(dev->obj);
		dev->obj = NULL;
		delete dev;
	}
}

struct libusb_transfer *libusb_alloc_transfer(int iso_packets)
{
	printf("libusb_alloc_transfer");

	if (iso_packets != 0)
		return NULL;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	libusb_transfer_jni *transfer = new libusb_transfer_jni;
	memset(transfer, 0, sizeof(*transfer));
	transfer->req = env->NewGlobalRef(env->NewObject(gClsRequest, gid_newrequest));

	jobject object = env->NewObject(gClsLong, gid_newlong, (jlong)transfer);
	if (object == NULL) {
		env->DeleteGlobalRef(transfer->req);
		delete transfer;
		return NULL;
	}

	env->CallVoidMethod(transfer->req, gid_setclientdata, object);
	return transfer;
}

#if 0
static libusb_transfer_jni *get_transfer(JNIEnv *env, jobject req)
{
	jobject obj = env->CallObjectMethod(req, gid_getclientdata);
	if (obj == NULL)
		return NULL;
	jlong l = env->CallLongMethod(obj, gid_longvalue);
	return (libusb_transfer_jni *)l;
}
#endif

int libusb_submit_transfer(struct libusb_transfer *a_transfer)
{
	// FIXME
	int c=0;
	printf("libusb_submit_transfer %d %p", c++, a_transfer);
	if (a_transfer == NULL)
		return LIBUSB_ERROR_INVALID_PARAM;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	libusb_transfer_jni *transfer = (libusb_transfer_jni*)a_transfer;

	transfer->buffer_obj = env->NewGlobalRef(env->CallStaticObjectMethod(gClsByteBuffer, gid_allocatedirect, transfer->length));

	printf("libusb_submit_transfer %d %p", c++, transfer->buffer_obj);

	if (env->CallObjectMethod(gCallback, gid_submittransfer, transfer->dev_handle->conn, transfer->dev_handle->dev->obj, transfer->req, transfer->buffer_obj, transfer->endpoint, transfer->length))
		return LIBUSB_SUCCESS;
	else
		return LIBUSB_ERROR_IO;

	jobject endpoint = NULL;
	printf("libusb_submit_transfer %d %p %p %p %p %d", c++, env, transfer->dev_handle, transfer->dev_handle->dev, transfer->dev_handle->dev->obj, transfer->endpoint);
	// if (find_endpoint(env, transfer->dev_handle, transfer->endpoint, &endpoint, NULL) < 0)
	// 	return LIBUSB_ERROR_NOT_FOUND;

	endpoint = env->CallObjectMethod(gCallback, gid_findendpoint, transfer->dev_handle->dev->obj, transfer->endpoint);
	if (endpoint == NULL)
		return LIBUSB_ERROR_NOT_FOUND;

	printf("libusb_submit_transfer %d %p %p", c++, transfer->dev_handle->dev->obj, endpoint);
	if (!env->CallBooleanMethod(transfer->req, gid_initialize, transfer->dev_handle->dev->obj, endpoint))
		return LIBUSB_ERROR_ACCESS;

	printf("libusb_submit_transfer %d %d", c++, transfer->length);
	transfer->buffer_obj = env->NewGlobalRef(env->CallStaticObjectMethod(gClsByteBuffer, gid_allocatedirect, transfer->length));
	printf("libusb_submit_transfer %d", c++);

	if ((transfer->endpoint & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_OUT) {
		jbyteArray array = env->NewByteArray(transfer->length);
		env->SetByteArrayRegion(array, 0, transfer->length, (const jbyte*)transfer->buffer);
		env->CallObjectMethod(transfer->buffer_obj, gid_put, array);
		// env->CallVoidMethod(transfer->buffer_obj, gid_rewind);
	}
	printf("libusb_submit_transfer %d %p %p %d", c++, transfer->req, transfer->buffer_obj, transfer->length);

	if (!env->CallBooleanMethod(transfer->req, gid_queue, transfer->buffer_obj, transfer->length)) {
		printf("libusb_submit_transfer failed");
		return LIBUSB_ERROR_IO;
	}

	printf("libusb_submit_transfer %d", c++);
	return LIBUSB_SUCCESS;
}

int libusb_cancel_transfer(struct libusb_transfer *transfer)
{
	// FIXME
	printf("libusb_cancel_transfer %p", transfer);
	return LIBUSB_ERROR_OTHER;
}

void libusb_free_transfer(struct libusb_transfer *a_transfer)
{
	printf("libusb_free_transfer %p", a_transfer);
	if (a_transfer == NULL)
		return;

	JNIEnv *env=NULL;
	gVM->AttachCurrentThread(&env, NULL);

	libusb_transfer_jni *transfer = (libusb_transfer_jni*)a_transfer;

	if (transfer->flags & LIBUSB_TRANSFER_FREE_BUFFER) {
		free(transfer->buffer); transfer->buffer = NULL;
	}

	env->CallVoidMethod(transfer->req, gid_setclientdata, NULL);
	env->DeleteGlobalRef(transfer->req);
	transfer->req = NULL;
	delete transfer;
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
	memset(interfaces, 0, sizeof(*interfaces) * config_p->bNumInterfaces);

	config_p->interface = interfaces;

	for (int i=0; i < config_p->bNumInterfaces; i++) {
		interfaces[i].num_altsetting = 1;
		libusb_interface_descriptor *desc = new libusb_interface_descriptor[1];
		memset(desc, 0, sizeof(*desc) * 1);

		interfaces[i].altsetting = desc;

                desc->bLength = sizeof(*desc);

		jobject interface = env->CallObjectMethod(obj, gid_getinterface, i);
		printf("libusb_get_active_config_descriptor %p", interface);

		if (interface) {
			desc->bInterfaceClass = env->CallIntMethod(interface, gid_getinterfaceclass);
		}
		// FIXME interface number
		desc->bInterfaceNumber = 0;

		// Write endpoints
		desc->bNumEndpoints = env->CallIntMethod(interface, gid_getendpointcount);

		libusb_endpoint_descriptor *endpoints = new libusb_endpoint_descriptor[desc->bNumEndpoints];
		memset(endpoints, 0, sizeof(*endpoints) * desc->bNumEndpoints);
		desc->endpoint = endpoints;

		for (int j=0; j < desc->bNumEndpoints; j++) {
			jobject endpoint = env->CallObjectMethod(interface, gid_getendpoint, j);

			endpoints[j].bDescriptorType = LIBUSB_DT_ENDPOINT;
			endpoints[j].bLength = sizeof(endpoints[j]);
			endpoints[j].bEndpointAddress = env->CallIntMethod(endpoint, gid_getaddress);
			endpoints[j].bmAttributes = env->CallIntMethod(endpoint, gid_getattributes);
			endpoints[j].wMaxPacketSize = env->CallIntMethod(endpoint, gid_getmaxpacketsize);
			endpoints[j].bInterval = env->CallIntMethod(endpoint, gid_interval);
		}

		desc->extra_length = 54;
		unsigned char *extra = new unsigned char[desc->extra_length];
		memset(extra, 0, desc->extra_length);

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
