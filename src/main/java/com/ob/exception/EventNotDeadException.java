package com.ob.exception;

import java.util.UUID;

public class EventNotDeadException extends RuntimeException {

    public EventNotDeadException(UUID eventId, String currentStatus) {
        super("event " + eventId + " is " + currentStatus + ", only DEAD events can be requeued");
    }
}
