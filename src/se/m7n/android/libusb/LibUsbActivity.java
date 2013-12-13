package se.m7n.android.libusb;

import java.util.HashMap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.openintents.smartcard.PCSCDaemon;

public class LibUsbActivity extends Activity
{
    private static final String TAG = "LibUsb";
    protected static final int HANDLER_LSUSB = 1;
    private TextView mStatus;
    private LibUsb mUsb;
    private Handler mHandler;
    private UsbManager mUsbManager;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.d(TAG, "onCreate " + savedInstanceState);

        super.onCreate(savedInstanceState);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        setContentView(R.layout.main);
        mStatus = (TextView)this.findViewById(R.id.status);
        ((Button)this.findViewById(R.id.set_device)).setOnClickListener(mSetDevice);
        ((Button)this.findViewById(R.id.start_scardcontrol)).setOnClickListener(mStartScardcontrol);
        ((Button)this.findViewById(R.id.start_pcscproxy)).setOnClickListener(mStartPcscProxy);
        ((Button)this.findViewById(R.id.stop_pcscproxy)).setOnClickListener(mStopPcscProxy);
        ((Button)this.findViewById(R.id.start_lsusb)).setOnClickListener(mStartLsusb);
        ((Button)this.findViewById(R.id.start_pcscd)).setOnClickListener(mStartPcscd);
        ((Button)this.findViewById(R.id.stop_pcscd)).setOnClickListener(mStopPcscd);
        
        //mUsb = new LibUsb(this);
        //mUsb.pcscmain();

        Intent intent = new Intent(this, LibUsb.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
        
        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                    switch (msg.what) {
                    case HANDLER_LSUSB:
                        mUsb.lsusb();
                        break;
                    }
            }
        };
    }
    
    @Override
    public void onDestroy()
    {
        unbindService(connection);
        super.onDestroy();
    }

    OnClickListener mSetDevice = new OnClickListener() {
        public void onClick(View v) {
            if (mUsb != null) {
                mUsb.setDevice(findDevice(), true);
            }
        }
    };

    OnClickListener mStartScardcontrol = new OnClickListener() {
        public void onClick(View v) {
            mUsb.scardcontrol();
        }
    };

    OnClickListener mStartPcscProxy = new OnClickListener() {
        public void onClick(View v) {
            mUsb.startPcscproxy();
        }
    };
    OnClickListener mStopPcscProxy = new OnClickListener() {
        public void onClick(View v) {
            mUsb.stopPcscproxy();
        }
    };
    OnClickListener mStartLsusb = new OnClickListener() {
        public void onClick(View v) {
            mUsb.lsusb();
        }
    };
    OnClickListener mStartPcscd = new OnClickListener() {
        public void onClick(View v) {
            mUsb.startPcscd();
        }
    };
    OnClickListener mStopPcscd = new OnClickListener() {
        public void onClick(View v) {
            mUsb.stopPcscd();
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        Log.d(TAG, "intent: " + intent);
    }
    
    @Override
    public void onPause() {
        super.onPause();
    }

    private UsbDevice findDevice() {
        HashMap<String, UsbDevice> devList = mUsbManager.getDeviceList();

        for (UsbDevice dev : devList.values()) {
            Log.d(TAG, "Dev: " + dev + " " + dev.hashCode() + " " + dev.getVendorId() + " " + dev.getProductId() + " " + dev.getDeviceId() + " " + dev.getDeviceName());

            if (isCSCID(dev)) {
                return dev;
            }
        }
        return null;
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

    private final ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className,
                                            IBinder service) {
                Log.d(TAG, "Bound " + className);
                LibUsb.PCSCBinder binder = (LibUsb.PCSCBinder)service;
                mUsb = binder.getService();
                mStatus.setText(R.string.connected);
            }
            public void onServiceDisconnected(ComponentName className) {
                mUsb = null;
                mStatus.setText(R.string.disconnected);
            }
        };
}
