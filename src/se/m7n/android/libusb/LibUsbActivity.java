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
    private static final String ACTION_USB_PERMISSION = "se.m7n.android.libusb.USB_PERMISSION";
    protected static final int HANDLER_LSUSB = 1;
    private Object mDevice;
    private TextView mStatus;
    private LibUsb mUsb;
    private Handler mHandler;
    private boolean mStarted;
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
        ((Button)this.findViewById(R.id.start_scardcontrol)).setOnClickListener(mStartScardcontrol);
        ((Button)this.findViewById(R.id.start_pcscproxy)).setOnClickListener(mStartPcscProxy);
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

        mUsbReceiver.register();
    }
    
    @Override
    public void onDestroy()
    {
        mUsbReceiver.unregister();
        unbindService(connection);
        super.onDestroy();
    }

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
        String action = intent.getAction();

        UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            setDevice(device, true);
        } else {
            // Normal start
            setDevice(null, true);
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
    }

    private void setDevice(Object object, boolean start) {
        Log.d(TAG, "setDevice started:" + mStarted + " device:" + object + " start:" + start);
        mDevice = object;
        if (!start) {
            mStatus.setText(R.string.disconnected);
            mStarted = false;
        } else if (!mStarted) {
            mStatus.setText(R.string.connected);
            mStarted = true;
        } else /* mStarted */ {
        }

        if (mUsb != null) {
            mUsb.setDevice(object, start);
        }
    }

    // TODO unused, remove
    private UsbDevice findDevice() {
        HashMap<String, UsbDevice> devList = mUsbManager.getDeviceList();

        for (UsbDevice dev : devList.values()) {
            Log.d(TAG, "Dev: " + dev + " " + dev.hashCode() + " " + dev.getVendorId() + " " + dev.getProductId() + " " + dev.getDeviceId() + " " + dev.getDeviceName());

            if (dev.getDeviceClass() == UsbConstants.USB_CLASS_CSCID) {
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
                    Intent permIntent = new Intent(ACTION_USB_PERMISSION);
                    permIntent.putExtra(UsbManager.EXTRA_DEVICE,
                                        device);
                    PendingIntent pendIntent = PendingIntent.getBroadcast(LibUsbActivity.this, 0, permIntent, 0);
                    mUsbManager.requestPermission(device, pendIntent);
                }
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                if (device != null) {
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

    private final ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className,
                                            IBinder service) {
                Log.d(TAG, "Bound " + className);
                LibUsb.PCSCBinder binder = (LibUsb.PCSCBinder)service;
                mUsb = binder.getService();
                if (mStarted) {
                    mUsb.setDevice(mDevice, true);
                }
            }
            public void onServiceDisconnected(ComponentName className) {
                mUsb = null;
            }
        };
}
