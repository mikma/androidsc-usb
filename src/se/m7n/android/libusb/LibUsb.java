package se.m7n.android.libusb;

import java.nio.ByteBuffer;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.util.Log;

public class LibUsb {
    public final static String TAG = "LibUsb";

    static {
        System.loadLibrary("usb");
        System.loadLibrary("ccid");
        System.loadLibrary("pcscd");
        System.loadLibrary("pcsclite");
        System.loadLibrary("scardcontrol");
        System.loadLibrary("usbjni");
    }
    private Context mContext;
    private Callback mCallback;
    
    LibUsb(Context context) {
        mContext = context;
        mCallback = new Callback();
        setCallback(mCallback);
    }
    
    public class Callback {
        public UsbManager getUsbManager() {
            return (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
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
        }).start();
    }
    
    public void lsusb() {
        lsusb(mCallback);                
    }

    native private void lsusb(Callback callback); 
    native private void pcscmain(Callback callback); 
    native private void setCallback(Callback callback); 
}
