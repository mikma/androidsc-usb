package se.m7n.android.libusb;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

public class LibUsb {
    public final static String TAG = "LibUsb";

    static {
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
    };
    
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
