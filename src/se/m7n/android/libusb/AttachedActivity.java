package se.m7n.android.libusb;

import java.util.HashMap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
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
import android.widget.ProgressBar;

import org.openintents.smartcard.PCSCDaemon;

public class AttachedActivity extends Activity
{
    private static final String TAG = "Attached";
    private static final String ACTION_USB_PERMISSION = "se.m7n.android.libusb.USB_PERMISSION";
    private UsbDevice mDevice;
    private TextView mStatus;
    private LibUsb mLib;
    private LibUsbService mUsb;
    private boolean mStarted;
    private UsbManager mUsbManager;
    private ProgressBar mProgress;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.d(TAG, "onCreate " + savedInstanceState);

        super.onCreate(savedInstanceState);

        mLib = new LibUsb();
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        setContentView(R.layout.attached);
        mStatus = (TextView)this.findViewById(R.id.attached_status);
        mProgress = (ProgressBar)this.findViewById(R.id.attached_progress);

        PackageManager pm = getPackageManager();
        Intent testIntent = mLib.createPcscAttachedIntent();
        ComponentName comp = testIntent.resolveActivity(pm);
        Log.d(TAG, "onCreate comp:" + comp);

        if (comp == null) {
            finish();
            return;
        }

        Intent intent = new Intent(this, LibUsbService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy()
    {
        if (mUsb != null)
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

    private final LibUsbService.ServiceCallback mCallback =
        new LibUsbService.ServiceCallback() {
            public void progressStart(int max) {
                mProgress.setMax(max);
            }
            public void progressSet(int progress) {
                mProgress.setProgress(progress);
            }
            public void progressStop() {
                mProgress.setProgress(mProgress.getMax());
                AttachedActivity.this.finish();
            }
        };

    private final ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className,
                                            IBinder service) {
                Log.d(TAG, "Bound " + className);
                LibUsbService.PCSCBinder binder =
                    (LibUsbService.PCSCBinder)service;
                mUsb = binder.getService();
                mUsb.setCallback(mCallback);
                if (mStarted) {
                    mUsb.setDevice(mDevice, true);
                }
            }
            public void onServiceDisconnected(ComponentName className) {
                mUsb.setCallback(null);
                mUsb = null;
            }
        };
}
