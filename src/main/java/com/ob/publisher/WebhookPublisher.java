package com.ob.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ob.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WebhookPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebhookPublisher.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;

    public WebhookPublisher(ObjectMapper objectMapper,
                            @Value("${outbox.publisher.url}") String webhookUrl,
                            @Value("${outbox.publisher.connect-timeout-ms:2000}") int connectTimeoutMs,
                            @Value("${outbox.publisher.read-timeout-ms:5000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;

        // never hang forever
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public boolean publish(OutboxEvent event) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventId", event.getId().toString());
            body.put("entityId", event.getEntityId().toString());
            body.put("type", event.getEventType());
            body.put("payload", objectMapper.readTree(event.getPayload()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // consumer dedup key
            headers.set("X-Event-Id", event.getId().toString());

            restTemplate.postForEntity(webhookUrl, new HttpEntity<>(body, headers), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Could not deliver event {}: {}", event.getId(), e.getMessage());
            return false;
        }
    }
}
