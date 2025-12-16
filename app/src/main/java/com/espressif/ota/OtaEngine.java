package com.espressif.ota;



import com.espressif.AppConstants;
import com.espressif.ble.BleForegroundService;
import com.espressif.EspCRC16;

import java.util.Arrays;
import java.util.UUID;

public class OtaEngine {

    public interface Listener {
        void onProgress(int percent);
        void onLog(String msg);
        void onFinished(boolean ok, String msg);
    }

    private final BleForegroundService ble;
    private final Listener listener;

    private byte[] firmware;
    private int offset = 0;
    private int packetIndex = 0;
    private int crc16 = 0;

    private boolean running = false;

    public OtaEngine(BleForegroundService ble, Listener listener) {
        this.ble = ble;
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public void start(byte[] firmware) {
        if (running) return;

        this.firmware = firmware;
        this.offset = 0;
        this.packetIndex = 0;
        this.crc16 = EspCRC16.calc(firmware);

        running = true;

        listener.onLog("OTA START");
        sendStartCommand();
    }

    private void sendStartCommand() {
        // Ejemplo START: [0x01, size_L, size_H]
        int size = firmware.length;
        byte[] cmd = new byte[] {
                0x01,
                (byte) (size & 0xFF),
                (byte) ((size >> 8) & 0xFF)
        };

        ble.write(
                AppConstants.OTA_SERVICE,
                AppConstants.OTA_COMMAND,
                cmd,
                android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        );
    }

    /** Llamar desde NOTIFY cuando el dispositivo confirme START */
    public void onStartAck() {
        listener.onLog("START ACK");
        sendNextChunk();
    }

    private void sendNextChunk() {
        if (!running) return;

        if (offset >= firmware.length) {
            sendEndCommand();
            return;
        }

        int len = Math.min(AppConstants.OTA_EXPECT_PACKET, firmware.length - offset);
        byte[] chunk = Arrays.copyOfRange(firmware, offset, offset + len);

        // Header típico: [index_L, index_H] + data
        byte[] payload = new byte[len + 2];
        payload[0] = (byte) (packetIndex & 0xFF);
        payload[1] = (byte) ((packetIndex >> 8) & 0xFF);
        System.arraycopy(chunk, 0, payload, 2, len);

        ble.write(
                AppConstants.OTA_SERVICE,
                AppConstants.OTA_FW_DATA,
                payload,
                android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        );

        offset += len;
        packetIndex++;

        int percent = (int) ((offset * 100f) / firmware.length);
        listener.onProgress(percent);
    }

    /** Llamar cuando llega ACK de paquete */
    public void onChunkAck() {
        sendNextChunk();
    }

    private void sendEndCommand() {
        listener.onLog("OTA END");

        byte[] cmd = new byte[] {
                0x02,
                (byte) (crc16 & 0xFF),
                (byte) ((crc16 >> 8) & 0xFF)
        };

        ble.write(
                AppConstants.OTA_SERVICE,
                AppConstants.OTA_COMMAND,
                cmd,
                android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        );
    }

    /** Llamar cuando llega ACK final */
    public void onEndAck(boolean ok) {
        running = false;
        listener.onFinished(ok, ok ? "OTA OK" : "OTA FAILED");
    }

    public void abort(String reason) {
        running = false;
        listener.onFinished(false, reason);
    }
}

