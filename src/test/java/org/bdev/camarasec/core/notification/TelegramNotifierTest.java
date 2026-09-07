package org.bdev.camarasec.core.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TelegramNotifierTest {

    @TempDir
    Path tempDir;

    private HttpServer mockServer;
    private int mockPort;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockPort = mockServer.getAddress().getPort();
        mockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void testIsConfiguredValidation() {
        TelegramNotifier unconfigured1 = new TelegramNotifier(null, null);
        assertFalse(unconfigured1.isConfigured());

        TelegramNotifier unconfigured2 = new TelegramNotifier("TU_BOT_TOKEN_AQUI", "TU_CHAT_ID_AQUI");
        assertFalse(unconfigured2.isConfigured());

        TelegramNotifier unconfigured3 = new TelegramNotifier("", "");
        assertFalse(unconfigured3.isConfigured());

        TelegramNotifier configured = new TelegramNotifier("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11", "987654321");
        assertTrue(configured.isConfigured());
    }

    @Test
    void testUnconfiguredDoesNotThrow() {
        TelegramNotifier unconfigured = new TelegramNotifier(null, null);
        assertFalse(unconfigured.sendMessage("Texto"));
        assertFalse(unconfigured.sendVideo(new File("nonexistent.mp4"), "Caption"));
    }

    @Test
    void testSendMessageAgainstMockServer() {
        AtomicBoolean requestReceived = new AtomicBoolean(false);

        mockServer.createContext("/botTESTTOKEN/sendMessage", exchange -> {
            requestReceived.set(true);
            String response = "{\"ok\":true,\"result\":{\"message_id\":1}}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        String mockApiBase = "http://localhost:" + mockPort + "/bot";
        TelegramNotifier notifier = new TelegramNotifier("TESTTOKEN", "12345", mockApiBase);

        boolean success = notifier.sendMessage("Alerta de prueba");

        assertTrue(success, "El envío de mensaje debe reportar éxito con respuesta 200");
        assertTrue(requestReceived.get(), "El servidor mock debió recibir la petición HTTP");
    }

    @Test
    void testSendVideoAgainstMockServer() throws Exception {
        AtomicBoolean videoReceived = new AtomicBoolean(false);

        mockServer.createContext("/botTESTTOKEN/sendVideo", exchange -> {
            videoReceived.set(true);
            String response = "{\"ok\":true,\"result\":{\"message_id\":2}}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        File dummyClip = tempDir.resolve("test_clip.mp4").toFile();
        Files.write(dummyClip.toPath(), "FAKE_MP4_CONTENT".getBytes());

        String mockApiBase = "http://localhost:" + mockPort + "/bot";
        TelegramNotifier notifier = new TelegramNotifier("TESTTOKEN", "12345", mockApiBase);

        boolean success = notifier.sendVideo(dummyClip, "Clip de prueba");

        assertTrue(success, "El envío de video multipart debe reportar éxito con respuesta 200");
        assertTrue(videoReceived.get(), "El servidor mock debió recibir el multipart con el video");
    }
}
