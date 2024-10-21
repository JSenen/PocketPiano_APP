package com.espressif;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.espressif.message.BleOTAMessage;
import com.espressif.message.EndCommandAckMessage;
import com.espressif.message.StartCommandAckMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BleOTAClient implements Closeable {
    private static final String TAG = "BleOTAClient";
    private static final boolean DEBUG = true;

    private static final int COMMAND_ID_START = 0x0001;
    private static final int COMMAND_ID_END = 0x0002;
    private static final int COMMAND_ID_ACK = 0x0003;

    public static final int COMMAND_ACK_ACCEPT = 0x0000;
    public static final int COMMAND_ACK_REFUSE = 0x0001;

    private static final int BIN_ACK_SUCCESS = 0x0000;
    private static final int BIN_ACK_CRC_ERROR = 0x0001;
    private static final int BIN_ACK_SECTOR_INDEX_ERROR = 0x0002;
    private static final int BIN_ACK_PAYLOAD_LENGTH_ERROR = 0x0003;

    private static final int MTU_SIZE = 517;
    private static final int MTU_STATUS_FAILED = 20000;
    private static final int EXPECT_PACKET_SIZE = 463;

    private static final UUID SERVICE_UUID = UUID.fromString("00008018-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_RECV_FW_UUID = UUID.fromString("00008020-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_PROGRESS_UUID = UUID.fromString("00008021-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_COMMAND_UUID = UUID.fromString("00008022-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_CUSTOMER_UUID = UUID.fromString("00008023-0000-1000-8000-00805f9b34fb");

    private Context context;
    private BluetoothDevice device;
    private File bin;

    private BluetoothGatt gatt;
    private BluetoothGattService service;
    private BluetoothGattCharacteristic recvFwChar;
    private BluetoothGattCharacteristic progressChar;
    private BluetoothGattCharacteristic commandChar;
    private BluetoothGattCharacteristic customerChar;

    private BluetoothGattCharacteristic nextNotifyChar;

    private GattCallback callback;
    private LinkedList<byte[]> packets = new LinkedList<>();
    private AtomicInteger sectorAckIndex = new AtomicInteger(0);
    private byte[] sectorAckMark = new byte[0];

    private int packetSize = 20;
    private int totalSize = 1;
    private UploadFileFragment fragment; // Referencia al fragmento
    private int progress = 0;

    public BleOTAClient(Context context, BluetoothDevice device, File bin, UploadFileFragment fragment) {
        this.context = context;
        this.device = device;
        this.bin = bin;
        this.fragment = fragment; // Asigna la referencia al fragmento
        this.progress = 0;
    }

    @SuppressLint("MissingPermission")
    public void start(GattCallback callback) {
        Log.i(TAG, "start OTA");
        stop();

        this.callback = callback; // Asegúrate de asignar el callback aquí
        callback.setClient(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(context, false, callback);
        }
    }


    @SuppressLint("MissingPermission")
    public void stop() {
        if (gatt != null) {
            gatt.close();
            gatt = null;
        }
        callback = null;
        packets.clear();
    }

    @Override
    public void close() {
        stop();
    }

    private void initPackets() throws IOException {
        sectorAckIndex.set(0);
        packets.clear();

        ArrayList<byte[]> sectors = new ArrayList<>();
        try (FileInputStream inputStream = new FileInputStream(bin)) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = inputStream.read(buf)) != -1) {
                byte[] sector = Arrays.copyOf(buf, read);
                sectors.add(sector);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (DEBUG) {
            Log.d(TAG, "initPackets: sectors size = " + sectors.size());
        }

        totalSize = sectors.size(); // Total de sectores a enviar

        // Procesa cada sector para dividirlo en paquetes
        byte[] block = new byte[packetSize - 3];
        for (int index = 0; index < sectors.size(); index++) {
            byte[] sector = sectors.get(index);
            ByteArrayInputStream stream = new ByteArrayInputStream(sector);
            int sequence = 0;
            int read;
            while ((read = stream.read(block)) != -1) {
                int crc = 0;
                boolean bLast = stream.available() == 0;
                if (bLast) {
                    sequence = -1;
                    crc = EspCRC16.crc(sector);
                }

                ByteArrayOutputStream packetStream = new ByteArrayOutputStream();
                packetStream.write(index & 0xff);
                packetStream.write((index >> 8) & 0xff);
                packetStream.write(sequence);
                packetStream.write(block, 0, read);
                if (bLast) {
                    packetStream.write(crc & 0xff);
                    packetStream.write((crc >> 8) & 0xff);
                }
                packets.add(packetStream.toByteArray());
                sequence++;
            }
            packets.add(sectorAckMark);
        }

        if (DEBUG) {
            Log.d(TAG, "initPackets: packets size = " + packets.size());
        }

        // Total de paquetes para el cálculo del progreso
        totalSize = packets.size();
    }

    @SuppressLint("MissingPermission")
    private BluetoothGattCharacteristic notifyNextDescWrite() {
        nextNotifyChar = (nextNotifyChar == null) ? recvFwChar :
                (nextNotifyChar == recvFwChar) ? progressChar :
                        (nextNotifyChar == progressChar) ? commandChar :
                                (nextNotifyChar == commandChar) ? customerChar : null;

        if (nextNotifyChar != null) {
            BluetoothGattDescriptor descriptor = nextNotifyChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                boolean success = gatt.writeDescriptor(descriptor);
                Log.d(TAG, "writeDescriptor success: " + success + " for " + nextNotifyChar.getUuid());
            } else {
                Log.e(TAG, "Descriptor not found for characteristic: " + nextNotifyChar.getUuid());
            }
        }
        return nextNotifyChar;
    }


    private byte[] genCommandPacket(int id, byte[] payload) {
        byte[] packet = new byte[20];
        packet[0] = (byte) (id & 0xff);
        packet[1] = (byte) ((id >> 8) & 0xff);
        System.arraycopy(payload, 0, packet, 2, payload.length);
        int crc = EspCRC16.crc(packet, 0, 18);
        packet[18] = (byte) (crc & 0xff);
        packet[19] = (byte) ((crc >> 8) & 0xff);
        return packet;
    }

    @SuppressLint("MissingPermission")
    private void postCommandStart() {
        Log.i(TAG, "postCommandStart");
        if (bin == null) {
            Log.e(TAG, "Error: bin file is null");
            callback.onError(-1); // o algún código de error específico
            return;
        }
        long binSize = bin.length();
        byte[] payload = {
                (byte) (binSize & 0xff),
                (byte) ((binSize >> 8) & 0xff),
                (byte) ((binSize >> 16) & 0xff),
                (byte) ((binSize >> 24) & 0xff)
        };
        byte[] packet = genCommandPacket(COMMAND_ID_START, payload);
        commandChar.setValue(packet);
        gatt.writeCharacteristic(commandChar);
    }

    private void receiveCommandStartAck(int status) {
        Log.i(TAG, "receiveCommandStartAck: status=" + status);
        if (status == COMMAND_ACK_ACCEPT) {
            // Comenzar la transferencia de firmware
            postBinData();
            // No establecer progreso aquí
        }
        callback.onOTA(new StartCommandAckMessage(status));
    }

    @SuppressLint("MissingPermission")
    private void postCommandEnd() {
        Log.i(TAG, "postCommandEnd");
        byte[] packet = genCommandPacket(COMMAND_ID_END, new byte[0]);
        commandChar.setValue(packet);
        gatt.writeCharacteristic(commandChar);
    }

    private void receiveCommandEndAck(int status) {
        Log.i(TAG, "receiveCommandEndAck: status=" + status);
        callback.onOTA(new EndCommandAckMessage(status));
    }

    private void postBinData() {
        new Thread(() -> {
            try {
                initPackets();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            postNextPacket();
        }).start();
    }

    @SuppressLint("MissingPermission")
    private void postNextPacket() {
        byte[] packet = packets.pollFirst();
        if (packet == null) {
            // Cuando todos los paquetes se han enviado, envía el comando de finalización.
            postCommandEnd();
        } else if (packet == sectorAckMark) {
            if (DEBUG) {
                Log.d(TAG, "postNextPacket: wait for sector ACK");
            }
        } else {
            recvFwChar.setValue(packet);
            boolean writeResult = gatt.writeCharacteristic(recvFwChar);

            Log.d(TAG, "Paquete enviado, resultado writeCharacteristic: " + writeResult);

            if (writeResult) {
                // Recalcular el progreso basado en el número de paquetes enviados
                int packetsSent = totalSize - packets.size(); // Calcula los paquetes enviados

                // Asegúrate de que totalSize > 0 para evitar división por cero
                if (totalSize > 0 && packetsSent >= 0 && packetsSent <= totalSize) {
                    progress = (int) ((float) packetsSent / totalSize * 100);
                } else {
                    progress = 0;  // Manejo de errores
                }

                // Llama al callback para actualizar la interfaz de usuario con el progreso actual.
                Log.d(TAG, "Callback de progreso con: " + progress + "%");
                callback.onProgress(progress);

                Log.d(TAG, "Progreso calculado después del envío: " + progress + "%");

            } else {
                // Manejo de error si `writeCharacteristic` falla
                Log.e(TAG, "Error al enviar el paquete");
            }
        }
    }




    private void parseSectorAck(byte[] data) {
        try {
            int expectIndex = sectorAckIndex.getAndIncrement();
            int ackIndex = (data[0] & 0xff) | ((data[1] & 0xff) << 8);
            if (ackIndex != expectIndex) {
                Log.w(TAG, "takeSectorAck: Receive error index " + ackIndex + ", expect " + expectIndex);
                callback.onError(1);
                return;
            }
            int ackStatus = (data[2] & 0xff) | ((data[3] & 0xff) << 8);
            Log.d(TAG, "takeSectorAck: index=" + ackIndex + ", status=" + ackStatus);
            if (ackStatus == BIN_ACK_SUCCESS) {
                // Calcula el progreso basado en el número de sectores enviados
                int sectorsSent = sectorAckIndex.get();

                // Verificación adicional para evitar división por cero
                if (totalSize > 0) {
                    progress = (int) ((float) sectorsSent / totalSize * 100);
                } else {
                    progress = 0;  // O manejo de errores
                }
                // Llama a la función de callback para actualizar el progreso
                if (callback != null) {
                    callback.onProgress(progress);
                }
                // Procede con el siguiente paquete
                postNextPacket();
            } else if (ackStatus == BIN_ACK_CRC_ERROR) {
                callback.onError(2);
            } else if (ackStatus == BIN_ACK_SECTOR_INDEX_ERROR) {
                int devExpectIndex = (data[4] & 0xff) | ((data[5] & 0xff) << 8);
                Log.w(TAG, "parseSectorAck: device expect index = " + devExpectIndex);
                callback.onError(3);
            } else if (ackStatus == BIN_ACK_PAYLOAD_LENGTH_ERROR) {
                callback.onError(4);
            } else {
                callback.onError(5);
            }
        } catch (Exception e) {
            Log.w(TAG, "parseSectorAck error", e);
            callback.onError(-1);
        }
    }


    private void parseCommandPacket() {
        byte[] packet = commandChar.getValue();
        if (DEBUG) {
            Log.i(TAG, "parseCommandPacket: " + Arrays.toString(packet));
        }

        int id = (packet[0] & 0xff) | ((packet[1] & 0xff) << 8);
        int ackId = (packet[2] & 0xff) | ((packet[3] & 0xff) << 8);
        int ackStatus = (packet[4] & 0xff) | ((packet[5] & 0xff) << 8);

        Log.d(TAG, "Received Command ACK - id: " + ackId + ", status: " + ackStatus);

        if (id == COMMAND_ID_ACK) {
            if (ackId == COMMAND_ID_START) {
                Log.d(TAG, "Start command ACK received with status: " + ackStatus);
                receiveCommandStartAck(ackStatus);
            } else if (ackId == COMMAND_ID_END) {
                Log.d(TAG, "End command ACK received with status: " + ackStatus);
                receiveCommandEndAck(ackStatus);
            }
        }
    }

    public static class GattCallback extends BluetoothGattCallback {
        private BleOTAClient client;

        public void setClient(BleOTAClient client) {
            this.client = client;
        }

        protected boolean isGattSuccess(int status) {
            return status == BluetoothGatt.GATT_SUCCESS;
        }

        protected boolean isGattFailed(int status) {
            return status != BluetoothGatt.GATT_SUCCESS;
        }

        public void onError(int code) {
        }

        public void onProgress(int progress) {
            if (client.fragment != null) {
                client.fragment.getActivity().runOnUiThread(() -> {
                    Log.d(TAG, "Updating progress on UI thread: " + progress + "%");
                    client.fragment.updateProgress(progress);
                });
            }

        }

        public void onOTA(BleOTAMessage message) {
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                if (!gatt.requestMtu(MTU_SIZE)) {
                    onMtuChanged(gatt, MTU_SIZE, MTU_STATUS_FAILED);
                }
                // Llama al método en el fragmento para actualizar el estado de la conexión
                client.fragment.updateConnectionStatus(true);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                // Llama al método en el fragmento para actualizar el estado de la conexión
                client.fragment.updateConnectionStatus(false);
                client.close(); // Limpia los recursos
            }
        }


        @SuppressLint("MissingPermission")
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            client.packetSize = isGattSuccess(status) ? EXPECT_PACKET_SIZE : 20;
            gatt.discoverServices();
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered: status=" + status);
            if (isGattFailed(status)) {
                Log.e(TAG, "Service discovery failed with status: " + status);
                return;
            }

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service == null) {
                Log.e(TAG, "Service not found!");
                return;
            }
            Log.d(TAG, "Service found: " + service.getUuid());

            BluetoothGattCharacteristic recvFwChar = service.getCharacteristic(CHAR_RECV_FW_UUID);
            if (recvFwChar != null) {
                gatt.setCharacteristicNotification(recvFwChar, true);
                Log.d(TAG, "recvFwChar notification set");
            } else {
                Log.e(TAG, "recvFwChar not found!");
            }

            BluetoothGattCharacteristic progressChar = service.getCharacteristic(CHAR_PROGRESS_UUID);
            if (progressChar != null) {
                gatt.setCharacteristicNotification(progressChar, true);
                Log.d(TAG, "progressChar notification set");
            } else {
                Log.e(TAG, "progressChar not found!");
            }

            BluetoothGattCharacteristic commandChar = service.getCharacteristic(CHAR_COMMAND_UUID);
            if (commandChar != null) {
                gatt.setCharacteristicNotification(commandChar, true);
                Log.d(TAG, "commandChar notification set");
            } else {
                Log.e(TAG, "commandChar not found!");
            }

            BluetoothGattCharacteristic customerChar = service.getCharacteristic(CHAR_CUSTOMER_UUID);
            if (customerChar != null) {
                gatt.setCharacteristicNotification(customerChar, true);
                Log.d(TAG, "customerChar notification set");
            } else {
                Log.e(TAG, "customerChar not found!");
            }

            client.service = service;
            client.recvFwChar = recvFwChar;
            client.progressChar = progressChar;
            client.commandChar = commandChar;
            client.customerChar = customerChar;

            if (recvFwChar != null && progressChar != null && commandChar != null && customerChar != null) {
                Log.d(TAG, "All characteristics found, proceeding to notifyNextDescWrite()");
                client.notifyNextDescWrite();
            } else {
                Log.e(TAG, "One or more characteristics are missing.");
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful for: " + descriptor.getCharacteristic().getUuid());
                if (client.notifyNextDescWrite() == null) {
                    Log.d(TAG, "All descriptors written, starting OTA process");
                    client.postCommandStart(); // Inicia el proceso OTA
                }
            } else {
                Log.e(TAG, "Descriptor write failed for: " + descriptor.getCharacteristic().getUuid() + " with status: " + status);
            }
        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicWrite: status=" + status + ", char=" + characteristic.getUuid());
            }
            if (isGattFailed(status)) {
                Log.w(TAG, "onCharacteristicWrite: status=" + status);
                return;
            }

            if (characteristic == client.recvFwChar) {
                // Continuar con el siguiente paquete después de que se haya escrito correctamente el actual
                client.postNextPacket();

            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicChanged: char=" + characteristic.getUuid());
            }
            if (characteristic == client.progressChar) {
                byte[] data = characteristic.getValue();
                int progress = 0;

                if (data.length >= 4) {
                    progress = ((data[0] & 0xFF) << 24) |
                            ((data[1] & 0xFF) << 16) |
                            ((data[2] & 0xFF) << 8) |
                            (data[3] & 0xFF);
                }

                if (client.callback != null) {
                    client.callback.onProgress(progress);
                }

                Log.d(TAG, "Progreso recibido de ESP32: " + progress + "%");
            } else if (characteristic == client.recvFwChar) {
                byte[] ackData = characteristic.getValue();
                client.parseSectorAck(ackData);
                Log.d(TAG, "ACK recibido para recvFwChar: " + Arrays.toString(ackData));
            } else if (characteristic == client.commandChar) {
                client.parseCommandPacket();
                Log.d(TAG, "Paquete de comando procesado.");
            }
        }



    }
}

