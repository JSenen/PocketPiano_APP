package com.espressif.message;

public class CommandAckMessage extends BleOTAMessage {
    private int status;

    public CommandAckMessage(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
