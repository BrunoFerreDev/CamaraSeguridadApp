package org.bdev.camarasec.core.ptz;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EzvizPtzServiceTest {

    private HttpServer mockServer;
    private int serverPort;
    private final AtomicReference<String> lastReceivedBody = new AtomicReference<>();
    private final AtomicReference<String> lastReceivedMethod = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        serverPort = mockServer.getAddress().getPort();

        mockServer.createContext("/ISAPI/PTZCtrl/channels/1/continuous", exchange -> {
            lastReceivedMethod.set(exchange.getRequestMethod());
            try (InputStream is = exchange.getRequestBody()) {
                lastReceivedBody.set(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }

            byte[] response = "<ResponseStatus>OK</ResponseStatus>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        mockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void testIsConfigured() {
        EzvizPtzService notConfigured = new EzvizPtzService("", "admin", "12345");
        assertFalse(notConfigured.isConfigured());

        EzvizPtzService configured = new EzvizPtzService("192.168.1.100", "admin", "12345");
        assertTrue(configured.isConfigured());
    }

    @Test
    void testMoveAndStop() {
        String mockBaseUri = "http://localhost:" + serverPort;
        EzvizPtzService ptzService = new EzvizPtzService("localhost", serverPort, "admin", "testpass", mockBaseUri);

        boolean moveResult = ptzService.move(PtzDirection.UP);
        assertTrue(moveResult);
        assertEquals("PUT", lastReceivedMethod.get());
        assertTrue(lastReceivedBody.get().contains("<pan>0</pan>"));
        assertTrue(lastReceivedBody.get().contains("<tilt>60</tilt>"));

        boolean stopResult = ptzService.stop();
        assertTrue(stopResult);
        assertEquals("PUT", lastReceivedMethod.get());
        assertTrue(lastReceivedBody.get().contains("<pan>0</pan>"));
        assertTrue(lastReceivedBody.get().contains("<tilt>0</tilt>"));
    }

    @Test
    void testMoveDirections() {
        String mockBaseUri = "http://localhost:" + serverPort;
        EzvizPtzService ptzService = new EzvizPtzService("localhost", serverPort, "admin", "testpass", mockBaseUri);

        ptzService.move(PtzDirection.LEFT);
        assertTrue(lastReceivedBody.get().contains("<pan>-60</pan>"));
        assertTrue(lastReceivedBody.get().contains("<tilt>0</tilt>"));

        ptzService.move(PtzDirection.RIGHT);
        assertTrue(lastReceivedBody.get().contains("<pan>60</pan>"));
        assertTrue(lastReceivedBody.get().contains("<tilt>0</tilt>"));

        ptzService.move(PtzDirection.DOWN);
        assertTrue(lastReceivedBody.get().contains("<pan>0</pan>"));
        assertTrue(lastReceivedBody.get().contains("<tilt>-60</tilt>"));
    }
}
