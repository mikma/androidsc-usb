package se.m7n.android.libusb;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
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
    protected static final int HANDLER_PCSCD = 2;
    private Object mDevice;
    private TextView mStatus;
    private LibUsb mUsb;
    private Handler mHandler;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        mStatus = (TextView)this.findViewById(R.id.status);
        ((Button)this.findViewById(R.id.start_scardcontrol)).setOnClickListener(mStartScardcontrol);
        ((Button)this.findViewById(R.id.start_pcscproxy)).setOnClickListener(mStartPcscProxy);
        ((Button)this.findViewById(R.id.start_lsusb)).setOnClickListener(mStartLsusb);
        ((Button)this.findViewById(R.id.start_pcscd)).setOnClickListener(mStartPcscd);
        
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
                    case HANDLER_PCSCD:
                        Log.d(TAG, "pcscd start");
                        mUsb.pcscmain();
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

    OnClickListener mStartScardcontrol = new OnClickListener() {
        public void onClick(View v) {
            mUsb.scardcontrol();
        }
    };

    OnClickListener mStartPcscProxy = new OnClickListener() {
        public void onClick(View v) {
            mUsb.pcscproxy();
        }
    };
    OnClickListener mStartLsusb = new OnClickListener() {
        public void onClick(View v) {
            mUsb.lsusb();
        }
    };
    OnClickListener mStartPcscd = new OnClickListener() {
        public void onClick(View v) {
            mUsb.pcscmain();
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
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            if (mDevice != null && mDevice.equals(device)) {
                setDevice(null, false);
            }
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
        mDevice = object;
        if (!start) {
            mStatus.setText(R.string.disconnected);
        } else {
            mStatus.setText(R.string.connected);
            Message msg = mHandler.obtainMessage(HANDLER_PCSCD);
            mHandler.sendMessageDelayed(msg, 500);
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className,
                                            IBinder service) {
                Log.d(TAG, "Bound " + className);
                LibUsb.PCSCBinder binder = (LibUsb.PCSCBinder)service;
                mUsb = binder.getService();
            }
            public void onServiceDisconnected(ComponentName className) {
                mUsb = null;
            }
        };
}
