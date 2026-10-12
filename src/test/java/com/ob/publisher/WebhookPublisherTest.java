package com.ob.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ob.model.OutboxEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPublisherTest {

    private HttpServer server;
    private ServerSocket silentServer;

    @AfterEach
    void stopServers() throws IOException {
        if (server != null) {
            server.stop(0);
        }
        if (silentServer != null) {
            silentServer.close();
        }
    }

    private OutboxEvent sampleEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEntityId(UUID.randomUUID());
        event.setEventType("TRANSFER_INITIATED");
        event.setPayload("{\"amount\": 10}");
        return event;
    }

    @Test
    void sendsEventWithIdHeader() throws Exception {
        AtomicReference<String> header = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/webhook", exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("X-Event-Id"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:" + server.getAddress().getPort() + "/webhook";
        WebhookPublisher publisher = new WebhookPublisher(new ObjectMapper(), url, 2000, 2000);
        OutboxEvent event = sampleEvent();

        boolean result = publisher.publish(event);

        assertThat(result).isTrue();
        assertThat(header.get()).isEqualTo(event.getId().toString());
        assertThat(body.get()).contains("\"type\":\"TRANSFER_INITIATED\"").contains("\"amount\":10");
    }

    @Test
    void returnsFalseWhenServerNeverAnswers() throws Exception {
        // accepts, never replies
        silentServer = new ServerSocket(0);
        String url = "http://localhost:" + silentServer.getLocalPort() + "/webhook";
        WebhookPublisher publisher = new WebhookPublisher(new ObjectMapper(), url, 1000, 1000);

        Instant start = Instant.now();
        boolean result = publisher.publish(sampleEvent());

        assertThat(result).isFalse();
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(5));
    }
}
