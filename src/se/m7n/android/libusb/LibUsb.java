package se.m7n.android.libusb;

import android.content.Intent;

class LibUsb {
    public static final String PCSC_ATTACHED = "org.openintents.smartcard.action.PCSC_ATTACHED";

    static {
        System.loadLibrary("usb");
        System.loadLibrary("ccid");
        System.loadLibrary("pcscd");
        System.loadLibrary("pcsclite");
        System.loadLibrary("scardcontrol");
        System.loadLibrary("usbjni");
    }

    Intent createPcscAttachedIntent() {
        Intent intent = new Intent(PCSC_ATTACHED);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return intent;
    }

    native public void scardcontrol();
    native public void lsusb();
    native void pcscmain();
    native void pcscstop();
    native void pcschotplug();
    native void pcscproxymain(String mSocketName, String pidFile);
    native int pcscproxystop();
}
