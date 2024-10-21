package com.espressif.message;

public class EndCommandAckMessage extends CommandAckMessage {

    public EndCommandAckMessage(int status) {
        super(status);
    }
}