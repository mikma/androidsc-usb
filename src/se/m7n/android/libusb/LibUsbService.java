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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.FileObserver;
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

public class LibUsbService extends Service {
    public final static String TAG = "LibUsbSvc";
    private static final String ACTION_USB_PERMISSION = "se.m7n.android.libusb.USB_PERMISSION";
    public static final String INFO_PLIST = "Info.plist";
    public static final String PATH_USB = "usb";
    public static final String PATH_IPC = "ipc";
    public static final String PATH_BUNDLE = "ifd-ccid.bundle";
    public static final String PATH_CONTENTS = "Contents";
    public static final String FILE_PCSCD_COMM = "pcscd.comm";
    public static final String FILE_PROXY_PID = "pcscproxy.pid";
    protected static final int HANDLER_ATTACHED  = 1;
    protected static final int HANDLER_DETACHED  = 2;
    protected static final int HANDLER_HOTPLUG   = 3;
    protected static final int HANDLER_PROXY     = 4;
    protected static final int HANDLER_START     = 5;
    protected static final int HANDLER_READY     = 6;
    protected static final int HANDLER_STOP      = 7;
    private int NOTIFICATION = R.string.service_started;

    private boolean mIsStarted;
    private String mSocketName;
    private Handler mHandler;
    private UsbDevice mDevice;
    private PcscproxyController mPcscproxy = new PcscproxyController();
    private PcscdController mPcscd = new PcscdController();
    private int mRefCount;
    private UsbManager mUsbManager;
    private File mPathIpc;
    private File mPathPcscdComm;
    private File mPathProxyPidFile;
    private FileObserver mFileObserver;
    private LibUsb mLibUsb;
    private ServiceCallback mCallback;
    final private Lock startLock = new ReentrantLock();
    final private Condition startInitialized = startLock.newCondition();
    
    @Override
    public void onCreate() {
        super.onCreate();

        mLibUsb = new LibUsb();
        UsbHelper.useContext(this);

        mRefCount = 0;
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        mIsStarted = false;
        mSocketName = toString();

        mPathIpc = new File(getFilesDir(), PATH_IPC);
        mPathPcscdComm = new File (mPathIpc, FILE_PCSCD_COMM);
        mPathProxyPidFile = new File(mPathIpc, FILE_PROXY_PID);

        Log.d(TAG, "onCreate socketName:" + mSocketName);
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
                    case HANDLER_ATTACHED:
                    case HANDLER_START: {
                        Log.d(TAG, "pcscd start");
                        startForeground(NOTIFICATION, createNotification());
                        // TODO protect with mutex?
                        if (mCallback != null)
                            mCallback.progressStart(40);
                        mPcscd.start();
                        if (mCallback != null)
                            mCallback.progressSet(10);
                        // TODO improve synchronous start of daemons
                        break;
                    }
                    case HANDLER_PROXY: {
                        Log.d(TAG, "proxy start");
                        if (mCallback != null)
                            mCallback.progressSet(20);
                        mPcscproxy.start();
                        if (mCallback != null)
                            mCallback.progressSet(30);
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

                        if (mCallback != null) {
                            mCallback.progressSet(40);
                            mCallback.progressStop();
                        }

                        try {
                            Intent intent = mLibUsb.createPcscAttachedIntent();
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            // Should not happen, shutdown
                            // FIXME shutdown
                        }
                        break;
                    }
                    case HANDLER_DETACHED: {
                        // TODO protect with mutex?
                        Log.d(TAG, "proxy stop");
                        mPcscproxy.stop();
                        Log.d(TAG, "pcscd stop");
                        mPcscd.stop();
                        stopForeground(true);
                        mIsStarted = false;
                        Message stopMsg = mHandler.obtainMessage(HANDLER_STOP);
                        mHandler.sendMessageDelayed(stopMsg, 5000);
                        break;
                    }
                    case HANDLER_STOP: {
                        Log.d(TAG, "Received stop");
                        if (!mIsStarted) {
                            stopSelf();
                        } else {
                            Log.i(TAG, "Ignore stopping");
                        }
                        break;
                    }
                    case HANDLER_HOTPLUG: {
                        Log.d(TAG, "pcschotplug");
                        mLibUsb.pcschotplug();
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
        mPcscproxy.stop();
        Log.d(TAG, "pcscd stop");
        mPcscd.stop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand intent:" + (intent==null)?"not null":"null");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        Log.d(TAG, "onBind intent:" + (intent==null)?"not null":"null");

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        // Return true if you would like to have the service's
        // onRebind(Intent) method later called when new clients bind
        // to it.
        return false;
    }

    private Notification createNotification() {
        CharSequence title = getText(R.string.service_label);
        CharSequence text = getText(R.string.service_started);

        Notification notification =
            new Notification(R.drawable.notification, text,
                             System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE;

        Intent intent = new Intent(this, LibUsbActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                intent, 0);

        notification.setLatestEventInfo(this, title, text, contentIntent);
        return notification;
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

            if (mDevice != null) {
                if (!mUsbManager.hasPermission(mDevice)) {
                    Log.d(TAG, "Request permission");
                    requestUsbPermission(mDevice);
                }
            }

            if (!mPcscd.isRunning()) {
                handler = HANDLER_ATTACHED;
            } else /* mPcscd != null */ {
                handler = HANDLER_HOTPLUG;
            }
        } else {
            if (mPcscd.isRunning()) {
                handler = HANDLER_DETACHED;
            }
            mDevice = object;
        }

        if (handler >= 0) {
            Message msg = mHandler.obtainMessage(handler);
            mHandler.sendMessage(msg);
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
        mPathIpc.mkdir();
    }

    private final PCSCDaemon.Stub mBinder = new PCSCBinder();

    final class PCSCBinder extends PCSCDaemon.Stub {
        public LibUsbService getService() {
            return LibUsbService.this;
        }

        public boolean start() {
            Log.d(TAG, "start " + String.format("pid:%d uid:%d", getCallingPid(), getCallingUid()));
            LibUsbService.this.start();
            return true;
        }
        public void stop() {
            LibUsbService.this.stop();
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
        PendingIntent pendIntent = PendingIntent.getBroadcast(LibUsbService.this, 0, permIntent, 0);
        mUsbManager.requestPermission(device, pendIntent);
    }

    private UsbBroadcastReceiver mUsbReceiver = new UsbBroadcastReceiver();

    private class UsbBroadcastReceiver extends BroadcastReceiver {
        public void register() {
            IntentFilter filter = new IntentFilter();

            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
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

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (device != null) {
                    setDevice(null, false);
                }
            }
        }
    }

    interface ServiceCallback {
        void progressStart(int max);
        void progressSet(int progress);
        void progressStop();
    }

    void setCallback(ServiceCallback callback) {
        mCallback = callback;
    }

    private void watchFile(final File watched, final int message) {
        if (mFileObserver != null)
            mFileObserver.stopWatching();
        String dir = watched.getParentFile().getAbsolutePath();
        final String file = watched.getName();
        Log.d(TAG, "watchFile started:" + watched);
        mFileObserver = new FileObserver(dir,
                                         FileObserver.CREATE) {
                public void onEvent(int event, String path) {
                    Log.d(TAG, "watchFile event:" + event + " path:" + path);
                    if (file.equals(path)) {
                        Log.d(TAG, "watchFile trigger:" + message);
                        Message msg = mHandler.obtainMessage(message);
                        mHandler.sendMessage(msg);
                    }
                }
            };
        mFileObserver.startWatching();
    }

    private abstract class Controller implements Runnable {
        private Thread mThread;
        private String mName;

        private Controller(String name) {
            mName = name;
        }

        protected boolean isRunning() {
            return mThread != null;
        }

        protected abstract void doStop();
        protected abstract File getFileToWatch();
        protected abstract int getMessageCode();

        protected void start() {
            if (isRunning()) {
                Log.e(TAG, mName + " already started");
                return;
            }

            getFileToWatch().delete();
            watchFile(getFileToWatch(), getMessageCode());

            mThread = new Thread(this, mName);
            mThread.start();
        }

        protected void stop() {
            if (!isRunning()) {
                Log.e(TAG, mName + " already stopped");
                return;
            }

            Log.i(TAG, mName + " stopping");
            doStop();
            try {
                mThread.join(5000);
            } catch(InterruptedException e) {
                Log.e(TAG, mName + " join interrupted", e);
            }
            Log.i(TAG, mName + " stopped");
            mThread = null;
        }
    }

    private final class PcscdController extends Controller {
        private Thread mThread;

        private PcscdController() {
            super("pcscd");
        }

        protected File getFileToWatch() {
            return mPathPcscdComm;
        }

        protected int getMessageCode() {
            return HANDLER_PROXY;
        }

        protected void doStop() {
            mLibUsb.pcscstop();
        }

        public void run() {
            mLibUsb.pcscmain();
        }
    }

    private final class PcscproxyController extends Controller {

        private PcscproxyController() {
            super("pcscproxy");
        }

        protected File getFileToWatch() {
            return mPathProxyPidFile;
        }

        protected int getMessageCode() {
            return HANDLER_READY;
        }

        protected void doStop() {
            mLibUsb.pcscproxystop();
        }

        public void run() {
            mLibUsb.pcscproxymain(mSocketName, mPathProxyPidFile.getAbsolutePath());
        }
    }
}
