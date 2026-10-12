package com.ob.publisher;

import com.ob.model.OutboxEvent;

public interface EventPublisher {

    // true means delivered
    boolean publish(OutboxEvent event);
}
