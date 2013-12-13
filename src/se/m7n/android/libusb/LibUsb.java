package se.m7n.android.libusb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import org.libusb.UsbHelper;

import org.openintents.smartcard.PCSCDaemon;

public class LibUsb extends Service {
    public final static String TAG = "LibUsb";
    private static final String ACTION_USB_PERMISSION = "se.m7n.android.libusb.USB_PERMISSION";
    public static final String INFO_PLIST = "Info.plist";
    public static final String PATH_USB = "usb";
    public static final String PATH_IPC = "ipc";
    public static final String PATH_BUNDLE = "ifd-ccid.bundle";
    public static final String PATH_CONTENTS = "Contents";
    protected static final int HANDLER_ATTACHED  = 1;
    protected static final int HANDLER_DETACHED  = 2;
    protected static final int HANDLER_HOTPLUG   = 3;
    protected static final int HANDLER_PROXY     = 4;
    protected static final int HANDLER_START     = 5;
    protected static final int HANDLER_READY     = 6;

    static {
        System.loadLibrary("usb");
        System.loadLibrary("ccid");
        System.loadLibrary("pcscd");
        System.loadLibrary("pcsclite");
        System.loadLibrary("scardcontrol");
        System.loadLibrary("usbjni");
    }
    private Callback mCallback;
    private boolean mIsStarted;
    private String mSocketName;
    private Handler mHandler;
    private UsbDevice mDevice;
    private PcscproxyThread mPcscproxy;
    private PcscdThread mPcscd;
    private int mRefCount;
    private UsbManager mUsbManager;
    final private Lock startLock = new ReentrantLock();
    final private Condition startInitialized = startLock.newCondition();
    
    @Override
    public void onCreate() {
        super.onCreate();

        UsbHelper.useContext(this);

        mRefCount = 0;
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        mCallback = new Callback();
        //setCallback(mCallback);

        mIsStarted = false;
        mSocketName = toString();

        Log.d(TAG, "onCreate: " + mSocketName);
        // Setenv.setenv("test", "value", 1);

        try {
            copyAssets();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                    switch (msg.what) {
                    case HANDLER_ATTACHED: {
                        Log.d(TAG, "attached");
                        // TODO improve synchronous start of daemons
                        Message msg2 = mHandler.obtainMessage(HANDLER_START);
                        mHandler.sendMessageDelayed(msg2, 1000);
                        break;
                    }
                    case HANDLER_START: {
                        Log.d(TAG, "pcscd start");
                        // TODO protect with mutex?
                        startPcscd();
                        // TODO improve synchronous start of daemons
                        Message msg2 = mHandler.obtainMessage(HANDLER_PROXY);
                        mHandler.sendMessageDelayed(msg2, 1000);
                        break;
                    }
                    case HANDLER_PROXY: {
                        Log.d(TAG, "proxy start");
                        startPcscproxy();
                        break;
                    }
                    case HANDLER_READY: {
                        Log.d(TAG, "ready");
                        try {
                            startLock.lock();
                            Log.d(TAG, "Signal initialized");
                            mIsStarted = true;
                            startInitialized.signalAll();
                        } finally {
                            startLock.unlock();
                        }
                        break;
                    }
                    case HANDLER_DETACHED: {
                        // TODO protect with mutex?
                        Log.d(TAG, "proxy stop");
                        stopPcscproxy();
                        Log.d(TAG, "pcscd stop");
                        stopPcscd();
                        break;
                    }
                    case HANDLER_HOTPLUG: {
                        Log.d(TAG, "pcschotplug");
                        pcschotplug();
                        break;
                    }
                    }
            }
        };

        mUsbReceiver.register();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");

        mUsbReceiver.unregister();

        Log.d(TAG, "proxy stop");
        stopPcscproxy();
        Log.d(TAG, "pcscd stop");
        stopPcscd();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        Log.d(TAG, "onBind");

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return true;
    }

    private boolean isStarted() {
        return mIsStarted;
    }

    private void start() {
        mRefCount++;
        if (mRefCount > 1)
            return;

        Message msg = mHandler.obtainMessage(HANDLER_START);
        mHandler.sendMessageDelayed(msg, 1000);
    }

    private void stop() {
        mRefCount--;
        if (mRefCount > 0)
            return;
        // FIXME
    }

    private boolean waitStart() {
        try {
            startLock.lock();
            while (!isStarted()) {
                Log.d(TAG, "waitStart");
                startInitialized.await();
            }
            Log.d(TAG, "ready");
            return true;
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted");
            return false;
        } finally {
            startLock.unlock();
        }
    }

    private boolean isCSCID(UsbDevice device) {
        if (device.getDeviceClass() == UsbConstants.USB_CLASS_CSCID)
            return true;

        int count = device.getInterfaceCount();
        for (int i=0; i<count; i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface == null) {
                Log.d(TAG, "isCSCID null interface");
                continue;
            }

            Log.d(TAG, "isCSCID iface class:" + iface.getInterfaceClass());

            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_CSCID) {
                return true;
            }
        }

        return false;
    }

    void setDevice(UsbDevice object, boolean start) {
        int handler = -1;

        Log.d(TAG, "setDevice " + start + ": " + object);

        if (start) {
            mDevice = object;

            if (mDevice == null) {
                HashMap<String, UsbDevice> devList = mUsbManager.getDeviceList();

                Log.d(TAG, "Enumerate devices");
                for (UsbDevice device: devList.values()) {
                    Log.d(TAG, "Check device " + device);
                    if (isCSCID(device)) {
                        Log.d(TAG, "Device is CSCID");
                        if (!mUsbManager.hasPermission(device)) {
                            Log.d(TAG, "Request permission");
                            requestUsbPermission(device);
                        }
                    }
                }
            } else {
                if (!mUsbManager.hasPermission(mDevice)) {
                    Log.d(TAG, "Request permission");
                    requestUsbPermission(mDevice);
                }
            }

            if (mPcscd == null) {
                handler = HANDLER_ATTACHED;
            } else /* mPcscd != null */ {
                handler = HANDLER_HOTPLUG;
            }
        } else {
            if (mPcscd != null) {
                handler = HANDLER_DETACHED;
            }
            mDevice = object;
        }

        if (handler >= 0) {
            Message msg = mHandler.obtainMessage(handler);
            mHandler.sendMessageDelayed(msg, 500);
        }
    }

    /** Copy one asset */
    private void copyAsset(String fileName, File destDir) throws IOException {
        Log.d(TAG, "copyAsset " + fileName + " " + destDir);
        InputStream is = null;
        OutputStream os = null;
        try {
            is = getAssets().open(fileName);
            destDir.mkdirs();
            os = new FileOutputStream(new File(destDir, fileName));
            byte[] buf = new byte[1024];
            int res;

            while ((res = is.read(buf)) >= 0) {
                os.write(buf, 0, res);
            }
        } finally {
            if (is != null)
                is.close();
            if (os != null)
                os.close();
        }
    }

    /** Copy assets and make directories  */
    private void copyAssets() throws IOException {
        String infoFilename = INFO_PLIST;
        File contentsDir = new File (new File(new File(getFilesDir(), PATH_USB), PATH_BUNDLE), PATH_CONTENTS);
        File infoFile = new File(contentsDir, infoFilename);
        copyAsset(infoFilename, contentsDir);
        new File(getFilesDir(), PATH_IPC).mkdir();
    }

    private final PCSCDaemon.Stub mBinder = new PCSCBinder();

    final class PCSCBinder extends PCSCDaemon.Stub {
        public LibUsb getService() {
            return LibUsb.this;
        }

        public boolean start() {
            Log.d(TAG, "start " + String.format("pid:%d uid:%d", getCallingPid(), getCallingUid()));
            LibUsb.this.start();
            return true;
        }
        public void stop() {
            LibUsb.this.stop();
        }
        public String getLocalSocketAddress() {
            if (!isStarted()) {
                if (!waitStart()) {
                    Log.e(TAG, "Not started");
                    return null;
                }
            }

            return mSocketName;
        }
        public boolean isTlsRequired() {
            return false;
        }
    }

    private void requestUsbPermission(UsbDevice device) {
        Intent permIntent = new Intent(ACTION_USB_PERMISSION);
        permIntent.putExtra(UsbManager.EXTRA_DEVICE, device);
        PendingIntent pendIntent = PendingIntent.getBroadcast(LibUsb.this, 0, permIntent, 0);
        mUsbManager.requestPermission(device, pendIntent);
    }

    private UsbBroadcastReceiver mUsbReceiver = new UsbBroadcastReceiver();

    private class UsbBroadcastReceiver extends BroadcastReceiver {
        public void register() {
            IntentFilter filter = new IntentFilter();

            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(ACTION_USB_PERMISSION);
            registerReceiver(this, filter);
            Log.d(TAG, "Register usb broadcast " + filter);
        }
        public void unregister() {
            unregisterReceiver(this);
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            Log.d(TAG, "onReceive action:" + action + " intent:" + intent + " dev:" + device);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                if (device != null && isCSCID(device)) {
                    Log.d(TAG, "onReceive request perm");

                    if (!mUsbManager.hasPermission(device)) {
                        requestUsbPermission(device);
                    }
                }
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

                if (granted && device != null) {
                    Log.d(TAG, "onReceive perm:" + device);
                    // TODO change start to hotplug
                    setDevice(device, true);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (device != null) {
                    setDevice(null, false);
                }
            }
        }
    }

    public class Callback {
        public UsbManager getUsbManager() {
            return (UsbManager) getSystemService(Context.USB_SERVICE);
        }
        public Object[] getDeviceList() {
            if (mDevice == null)
                return new Object[0];

            UsbManager manager = getUsbManager();
            HashMap<String, UsbDevice> devList = manager.getDeviceList();

            if (devList.containsValue(mDevice))
                return new Object[]{mDevice};

            // USB device detached
            setDevice(null, false);
            return new Object[0];
        }
        // TODO unused, remove
        public Object[] getDeviceListX() {
            Log.d(TAG, "getDeviceList");
            Log.d(TAG, "getDeviceList0");
            try {
                Log.d(TAG, "getDeviceList1");
                UsbManager manager = getUsbManager();
                Log.d(TAG, "getDeviceList2");
                HashMap<String, UsbDevice> devList = manager.getDeviceList();
                Log.d(TAG, "getDeviceList3");
                Collection<UsbDevice> devices = devList.values();
                Log.d(TAG, "getDeviceList4");
                Object[] list = devices.toArray();
                Log.d(TAG, "getDeviceList5");
                // Object[] list = getUsbManager().getDeviceList().values().toArray();
                for (int i=0;i<list.length;i++) {
                    UsbDevice dev = (UsbDevice)list[i];
                    Log.d(TAG, "Dev: " + dev + " " + dev.hashCode() + " " + dev.getVendorId() + " " + dev.getProductId() + " " + dev.getDeviceId() + " " + dev.getDeviceName());
                }
                Log.d(TAG, "getDeviceList returns " + list.length);
                return list;
            } catch(Exception e) {
                Log.d(TAG, "exception", e);
                return null;
            } catch(Throwable e) {
                Log.d(TAG, "throwable", e);
                return null;
            }
        }

        public UsbEndpoint findEndpoint(UsbDevice device, int endpoint_addr) {
            int num_ifaces = device.getInterfaceCount();
            for (int i=0; i < num_ifaces; i++) {
                // Get interface
                UsbInterface iface = device.getInterface(i);

                int num_endpoints = iface.getEndpointCount();
                for (int j=0; j < num_endpoints; j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    int addr = ep.getAddress();
                    if (endpoint_addr == addr) {
                        return ep;
                    }
                }
            }
            Log.w(TAG, "find_endpoint not found: " + endpoint_addr);
            return null;
        }

        public boolean submitTransfer(UsbDeviceConnection conn, UsbDevice dev, UsbRequest req, ByteBuffer buffer, int endpoint_addr, int length) {
            UsbEndpoint ep = findEndpoint(dev, endpoint_addr);
            if (ep == null) {
                Log.d(TAG, "submitTransfer ep not found");
                return false;
            }

            if (!req.initialize(conn, ep)) {
                Log.d(TAG, "submitTransfer req init failed");
                return false;
            }

            boolean res = req.queue(buffer, length);
            Log.d(TAG, "submitTransfer res:" + res);
            return res;
        }

        public int controlTransfer(UsbDeviceConnection conn, int requestType, int request, int value, int index, byte[] buffer, int length, int timeout) {
            return conn.controlTransfer(requestType, request, value, index, buffer, length, timeout);
        }
        public int bulkTransfer(UsbDeviceConnection conn, UsbEndpoint ep, byte[] data, int length, int timeout) {
            return conn.bulkTransfer(ep, data, length, timeout);
        }
        public int bulkTransferX(UsbDeviceConnection conn, UsbEndpoint ep, byte[] data, int length, int timeout) {
            UsbRequest req = new UsbRequest();
            if (!req.initialize(conn, ep)) {
                Log.e(TAG, "bulkTransfer initialize failed");
                return -2;
            }
            ByteBuffer buffer = ByteBuffer.allocate(length);
            if (ep.getDirection()==UsbConstants.USB_DIR_OUT) {
                Log.e(TAG, "bulkTransfer put data " + length);
                buffer.put(data, 0, length);
            }

            if (!req.queue(buffer, length)) {
                Log.e(TAG, "bulkTransfer queue failed");
                return -3;
            }

            UsbRequest req2 = conn.requestWait();
            if (req2 != req) {
                Log.e(TAG, "bulkTransfer unexpected request");
                return -4;
            }
            int res = buffer.position();

            if (ep.getDirection()==UsbConstants.USB_DIR_IN) {
                Log.e(TAG, "bulkTransfer got data " + res);
                buffer.rewind();
                // TODO compare res and length
                buffer.get(data, 0, length);
            }

            req.close();
            req2.close();

            return res;
        }

    }

    class PcscdThread extends Thread {
        public PcscdThread() {
            super("pcscd");
        }
        public void run() {
            pcscmain(mCallback);
        }
    }

    public void startPcscd() {
        if (mPcscd != null) {
            Log.e(TAG, "pcscd already started");
            return;
        }

        mPcscd = new PcscdThread();
        mPcscd.start();
    }

    public void stopPcscd() {
        if (mPcscd == null) {
            Log.e(TAG, "pcscd already stopped");
            return;
        }

        Log.i(TAG, "pcscd stopping");
        pcscstop();
        try {
            mPcscd.join(5000);
        } catch(InterruptedException e) {
            Log.e(TAG, "pcscd join interrupted", e);
        }
        Log.i(TAG, "pcscd stopped");
        mPcscd = null;
    }

    class PcscproxyThread extends Thread {
        public PcscproxyThread() {
            super("pcscproxy");
        }
        public void run() {
            Message msg = mHandler.obtainMessage(HANDLER_READY);
            mHandler.sendMessageDelayed(msg, 1000);
            pcscproxymain(mCallback, mSocketName);
        }
    }

    public void startPcscproxy() {
        if (mPcscproxy != null) {
            Log.e(TAG, "pcscproxy already started");
            return;
        }

        mPcscproxy = new PcscproxyThread();
        mPcscproxy.start();
    }

    public void stopPcscproxy() {
        if (mPcscproxy == null) {
            Log.e(TAG, "pcscproxy already stopped");
            return;
        }

        Log.i(TAG, "pcscproxy stopping");
        pcscproxystop();
        try {
            mPcscproxy.join(1000);
        } catch(InterruptedException e) {
            Log.e(TAG, "pcscproxy join interrupted", e);
        }
        Log.i(TAG, "pcscproxy stopped");
        mPcscproxy = null;
    }

    public void scardcontrol() {
        scardcontrol(mCallback);
    }

    public void lsusb() {
        lsusb(mCallback);
    }

    native private void scardcontrol(Callback callback);
    native private void lsusb(Callback callback); 
    native private void pcscmain(Callback callback);
    native private void pcscstop();
    native public void pcschotplug();
    native private void pcscproxymain(Callback callback, String mSocketName);
    native private int pcscproxystop();
    native private void setCallback(Callback callback); 
}
