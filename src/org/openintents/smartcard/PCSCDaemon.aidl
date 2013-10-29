package org.openintents.smartcard;

interface PCSCDaemon {
    boolean start();
    void stop();
    String getLocalSocketAddress();
    boolean isTlsRequired();
}
