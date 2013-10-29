package se.m7n.android.libusb;

import java.nio.ByteBuffer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import org.openintents.smartcard.PCSCDaemon;

public class LibUsb extends Service {
    public final static String TAG = "LibUsb";

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
    
    @Override
    public void onCreate() {
        super.onCreate();

        mCallback = new Callback();
        setCallback(mCallback);

        mIsStarted = false;
        mSocketName = toString();

        Log.d(TAG, "onCreate: " + mSocketName);
        // Setenv.setenv("test", "value", 1);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");
        if (mIsStarted) {
            // TODO stop pcsc-proxy
        }
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
        if (mIsStarted)
            return;
        mIsStarted = true;
        // pcscmain();
        // pcscproxy();
    }

    private void stop() {
        // FIXME
    }

    private final PCSCDaemon.Stub mBinder = new PCSCBinder();

    final class PCSCBinder extends PCSCDaemon.Stub {
        public LibUsb getService() {
            return LibUsb.this;
        }

        public boolean start() {
            Log.d(TAG, "start " + String.format("pid:%d uid:%d", getCallingPid(), getCallingUid()));
            if (isStarted()) {
                Log.e(TAG, "Already started");
                return false;
            }
            LibUsb.this.start();
            return true;
        }
        public void stop() {
            if (isStarted()) {
                LibUsb.this.stop();
            } else {
                Log.e(TAG, "Not started");
            }
        }
        public String getLocalSocketAddress() {
            if (!isStarted()) {
                Log.e(TAG, "Not started");
                return null;
            }

            return mSocketName;
        }
        public boolean isTlsRequired() {
            return false;
        }
    }

    public class Callback {
        public UsbManager getUsbManager() {
            return (UsbManager) getSystemService(Context.USB_SERVICE);
        }
        public Object[] getDeviceList() {
            Log.d(TAG, "getDeviceList");
            try {
                Object[] list = getUsbManager().getDeviceList().values().toArray();
                for (int i=0;i<list.length;i++) {
                    UsbDevice dev = (UsbDevice)list[i];
                    Log.d(TAG, "Dev: " + dev + " " + dev.hashCode() + " " + dev.getVendorId() + " " + dev.getProductId() + " " + dev.getDeviceId() + " " + dev.getDeviceName());
                }
                return list;
            } catch(Exception e) {
                Log.d(TAG, "exception", e);
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

    public void pcscmain() {
        new Thread(new Runnable() {
            public void run() {
                pcscmain(mCallback);
            }
        }, "pcscmain").start();
    }
    
    public void pcscproxy() {
        new Thread(new Runnable() {
            public void run() {
                pcscproxymain(mCallback, mSocketName);
            }
        }, "pcscproxy").start();
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
    native private void pcscproxymain(Callback callback, String mSocketName);
    native private void setCallback(Callback callback); 
}
