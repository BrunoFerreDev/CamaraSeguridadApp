package org.bdev.camarasec.core.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente HTTP nativo para la Telegram Bot API.
 * Permite enviar mensajes de texto y clips de video (multipart/form-data) sin dependencias externas.
 */
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final String DEFAULT_API_BASE = "https://api.telegram.org/bot";

    private final String botToken;
    private final String chatId;
    private final String apiBase;
    private final HttpClient httpClient;

    public TelegramNotifier(String botToken, String chatId) {
        this(botToken, chatId, DEFAULT_API_BASE);
    }

    public TelegramNotifier(String botToken, String chatId, String apiBase) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.apiBase = apiBase != null ? apiBase : DEFAULT_API_BASE;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Verifica si las credenciales de Telegram están configuradas con datos reales.
     */
    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && !botToken.contains("TU_BOT_TOKEN")
                && chatId != null && !chatId.isBlank() && !chatId.contains("TU_CHAT_ID");
    }

    /**
     * Envía un mensaje de texto de manera asíncrona.
     */
    public CompletableFuture<Boolean> sendMessageAsync(String text) {
        return CompletableFuture.supplyAsync(() -> sendMessage(text));
    }

    /**
     * Envía un mensaje de texto a Telegram.
     */
    public boolean sendMessage(String text) {
        if (!isConfigured()) {
            log.warn("Telegram no configurado (token o chat_id faltante). Mensaje no enviado.");
            return false;
        }

        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String encodedChatId = URLEncoder.encode(chatId, StandardCharsets.UTF_8);
            String url = apiBase + botToken + "/sendMessage";
            String formBody = "chat_id=" + encodedChatId + "&text=" + encodedText + "&parse_mode=HTML";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Mensaje enviado a Telegram correctamente.");
                return true;
            } else {
                log.error("Error al enviar mensaje a Telegram (HTTP {}): {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Excepción al enviar mensaje a Telegram: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Envía un clip de video de manera asíncrona (multipart/form-data).
     */
    public CompletableFuture<Boolean> sendVideoAsync(File clipFile, String caption) {
        return CompletableFuture.supplyAsync(() -> sendVideo(clipFile, caption));
    }

    /**
     * Envía un clip de video a Telegram con soporte de streaming inline.
     */
    public boolean sendVideo(File clipFile, String caption) {
        if (!isConfigured()) {
            log.warn("Telegram no configurado (token o chat_id faltante). Clip no enviado.");
            return false;
        }

        if (clipFile == null || !clipFile.exists() || clipFile.length() == 0) {
            log.warn("Archivo de clip inválido o vacío para enviar a Telegram.");
            return false;
        }

        try {
            String boundary = "---CamaraSecBoundary" + System.currentTimeMillis();
            String url = apiBase + botToken + "/sendVideo";

            byte[] multipartData = buildMultipartVideoData(boundary, clipFile, caption != null ? caption : "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartData))
                    .build();

            log.info("Subiendo clip a Telegram: {} ({} KB)...", clipFile.getName(), clipFile.length() / 1024);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Clip enviado exitosamente a Telegram: {}", clipFile.getName());
                return true;
            } else {
                log.error("Error enviando video a Telegram (HTTP {}): {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Excepción al enviar video a Telegram: {}", e.getMessage(), e);
            return false;
        }
    }

    private byte[] buildMultipartVideoData(String boundary, File file, String caption) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String lineBreak = "\r\n";
        String twoHyphens = "--";

        // Campo chat_id
        baos.write((twoHyphens + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"chat_id\"" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write((chatId + lineBreak).getBytes(StandardCharsets.UTF_8));

        // Campo caption
        if (!caption.isBlank()) {
            baos.write((twoHyphens + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"caption\"" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
            baos.write((caption + lineBreak).getBytes(StandardCharsets.UTF_8));
        }

        // Campo supports_streaming
        baos.write((twoHyphens + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"supports_streaming\"" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("true" + lineBreak).getBytes(StandardCharsets.UTF_8));

        // Campo video (archivo)
        baos.write((twoHyphens + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"video\"; filename=\"" + file.getName() + "\"" + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: video/mp4" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));

        Files.copy(file.toPath(), baos);

        baos.write(lineBreak.getBytes(StandardCharsets.UTF_8));
        baos.write((twoHyphens + boundary + twoHyphens + lineBreak).getBytes(StandardCharsets.UTF_8));

        return baos.toByteArray();
    }
}
