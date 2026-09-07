package org.bdev.camarasec.core.ptz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente local para control de movimiento PTZ (Pan / Tilt) en cámaras EZVIZ / Hikvision (ISAPI).
 */
public class EzvizPtzService {

    private static final Logger log = LoggerFactory.getLogger(EzvizPtzService.class);

    private final String cameraHost;
    private final int port;
    private final String username;
    private final String password;
    private final String baseUri;
    private final HttpClient httpClient;

    public EzvizPtzService(String cameraHost, String username, String password) {
        this(cameraHost, 80, username, password, null);
    }

    public EzvizPtzService(String cameraHost, int port, String username, String password, String customBaseUri) {
        this.cameraHost = cameraHost != null ? cameraHost.trim() : "";
        this.port = port > 0 ? port : 80;
        this.username = username != null ? username.trim() : "admin";
        this.password = password != null ? password.trim() : "";

        if (customBaseUri != null && !customBaseUri.isBlank()) {
            this.baseUri = customBaseUri.endsWith("/") ? customBaseUri.substring(0, customBaseUri.length() - 1) : customBaseUri;
        } else {
            this.baseUri = "http://" + this.cameraHost + ":" + this.port;
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EzvizPtzService.this.username, EzvizPtzService.this.password.toCharArray());
                    }
                })
                .build();
    }

    public boolean isConfigured() {
        return !cameraHost.isBlank();
    }

    /**
     * Inicia o actualiza el movimiento de la cámara de forma asíncrona.
     */
    public CompletableFuture<Boolean> moveAsync(PtzDirection direction) {
        return CompletableFuture.supplyAsync(() -> move(direction));
    }

    /**
     * Detiene cualquier movimiento en curso de forma asíncrona.
     */
    public CompletableFuture<Boolean> stopAsync() {
        return CompletableFuture.supplyAsync(this::stop);
    }

    /**
     * Envía comando de movimiento continuo según la dirección indicada.
     */
    public boolean move(PtzDirection direction) {
        if (!isConfigured()) {
            log.warn("PTZ no configurado: falta dirección IP de la cámara.");
            return false;
        }

        if (direction == null) {
            direction = PtzDirection.STOP;
        }

        String xmlPayload = String.format(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<PTZData>\r\n" +
                "    <pan>%d</pan>\r\n" +
                "    <tilt>%d</tilt>\r\n" +
                "</PTZData>",
                direction.getPan(), direction.getTilt()
        );

        String url = baseUri + "/ISAPI/PTZCtrl/channels/1/continuous";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/xml; charset=UTF-8")
                    .PUT(HttpRequest.BodyPublishers.ofString(xmlPayload, StandardCharsets.UTF_8))
                    .build();

            log.debug("Enviando comando PTZ {} a {}", direction, url);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Comando PTZ {} ejecutado con éxito (HTTP {})", direction, response.statusCode());
                return true;
            } else {
                log.warn("Respuesta de cámara al comando PTZ {}: HTTP {}", direction, response.statusCode());
                return false;
            }
        } catch (Exception e) {
            log.warn("Excepción al enviar comando PTZ {}: {}", direction, e.getMessage());
            return false;
        }
    }

    /**
     * Envía comando de detención (pan=0, tilt=0).
     */
    public boolean stop() {
        return move(PtzDirection.STOP);
    }

    public String getCameraHost() {
        return cameraHost;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }
}
