

package com.espressif;


import java.util.UUID;

public final class AppConstants {
    private AppConstants() {}

    public static final String PREFS = "prefs";
    public static final String KEY_MODE = "mode";
    public static final String MODE_CONTROL = "control";
    public static final String MODE_OTA = "ota";

    public static final String EXTRA_MAC = "extra_mac";
    public static final String EXTRA_NAME = "extra_name";

    // OTA UUIDs (los tuyos)
    public static final UUID OTA_SERVICE = UUID.fromString("00008018-0000-1000-8000-00805f9b34fb");
    public static final UUID OTA_FW_DATA = UUID.fromString("00008020-0000-1000-8000-00805f9b34fb");
    public static final UUID OTA_PROGRESS = UUID.fromString("00008021-0000-1000-8000-00805f9b34fb");
    public static final UUID OTA_COMMAND = UUID.fromString("00008022-0000-1000-8000-00805f9b34fb");
    public static final UUID OTA_CUSTOMER = UUID.fromString("00008023-0000-1000-8000-00805f9b34fb");

    public static final int OTA_MTU = 517;
    public static final int OTA_EXPECT_PACKET = 463;
}
