package se.m7n.android.libusb;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class LibUsbActivity extends Activity
{
    private static final String TAG = "LibUsb";
    private Object mDevice;
    private TextView mStatus;
    private LibUsb mUsb;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        mStatus = (TextView)this.findViewById(R.id.status);
        mUsb = new LibUsb(this);
        mUsb.lsusb();
    }
    
    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        Log.d(TAG, "intent: " + intent);
        String action = intent.getAction();

        UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            setDevice(device);
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            if (mDevice != null && mDevice.equals(device)) {
                setDevice(null);
            }
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mUsb = null;
    }

    private void setDevice(Object object) {
        mDevice = object;
        if (mDevice == null) {
            mStatus.setText(R.string.disconnected);
        } else {
            mStatus.setText(R.string.connected);
            mUsb.lsusb();
        }
    }
}
