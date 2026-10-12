package com.ob.dto;

import java.util.UUID;

public record RequeueResponse(UUID eventId, String status) {
}
