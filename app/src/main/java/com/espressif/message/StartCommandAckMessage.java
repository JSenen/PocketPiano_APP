package com.espressif.message;

public class StartCommandAckMessage extends CommandAckMessage {

    public StartCommandAckMessage(int status) {
        super(status);
    }
}

