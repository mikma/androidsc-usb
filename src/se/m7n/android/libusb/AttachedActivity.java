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

public class AttachedActivity extends Activity
{
    private static final String TAG = "Attached";
    private static final String ACTION_USB_PERMISSION = "se.m7n.android.libusb.USB_PERMISSION";
    private UsbDevice mDevice;
    private TextView mStatus;
    private LibUsb mUsb;
    private boolean mStarted;
    private UsbManager mUsbManager;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.d(TAG, "onCreate " + savedInstanceState);

        super.onCreate(savedInstanceState);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        setContentView(R.layout.attached);
        mStatus = (TextView)this.findViewById(R.id.attached_status);

        Intent intent = new Intent(this, LibUsb.class);
        bindService(intent, connection, BIND_AUTO_CREATE);

        mUsbReceiver.register();
    }

    @Override
    public void onDestroy()
    {
        mUsbReceiver.unregister();
        unbindService(connection);
        super.onDestroy();
    }

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

    private void setDevice(UsbDevice object, boolean start) {
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
                    PendingIntent pendIntent = PendingIntent.getBroadcast(AttachedActivity.this, 0, permIntent, 0);
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
                    AttachedActivity.this.finish();
                }
            }
            public void onServiceDisconnected(ComponentName className) {
                mUsb = null;
            }
        };
}
