package com.example.messages;

import java.io.Serializable;

public class EventIndexComplete implements Serializable {
    private final Long eventId;
    private final int status;
    private final String errorMessage;

    public EventIndexComplete(Long eventId, int status, String errorMessage) {
        this.eventId = eventId;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public EventIndexComplete(Long eventId, int status) {
        this(eventId, status, null);
    }

    public Long getEventId() {
        return eventId;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
