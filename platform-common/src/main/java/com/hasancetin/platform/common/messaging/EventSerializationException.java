package com.hasancetin.platform.common.messaging;

/** Poison-pill style failure: retrying will never help, so the error handler sends it straight to the DLT. */
public class EventSerializationException extends RuntimeException {

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
