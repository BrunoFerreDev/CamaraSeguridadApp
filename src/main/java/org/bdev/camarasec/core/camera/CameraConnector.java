package org.bdev.camarasec.core.camera;

import javafx.scene.image.Image;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.JavaFXFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CameraConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CameraConnector.class);

    private final String rtspUrl;
    private FFmpegFrameGrabber grabber;
    private final JavaFXFrameConverter converter = new JavaFXFrameConverter();
    private volatile boolean running = false;

    public CameraConnector(String rtspUrl) {
        this.rtspUrl = rtspUrl;
    }

    public synchronized void start() throws Exception {
        if (running) {
            return;
        }

        log.info("Iniciando conexión RTSP hacia la cámara...");
        grabber = new FFmpegFrameGrabber(rtspUrl);
        // Configuración para stream RTSP sobre TCP (más estable frente a pérdidas de paquetes)
        grabber.setOption("rtsp_transport", "tcp");
        grabber.setOption("stimeout", "5000000"); // 5 segundos de timeout
        grabber.setOption("max_delay", "500000"); // Reducir latencia a 500ms
        grabber.setOption("buffer_size", "1024000");

        grabber.start();
        running = true;
        log.info("Conexión RTSP establecida. Resolución: {}x{}, Formato: {}",
                grabber.getImageWidth(), grabber.getImageHeight(), grabber.getFormat());
    }

    /**
     * Captura el siguiente frame crudo de video (útil para detección de movimiento / grabación).
     */
    public synchronized Frame grabRawFrame() throws Exception {
        if (!running || grabber == null) {
            return null;
        }
        return grabber.grabImage();
    }

    /**
     * Captura el siguiente frame de video y lo convierte a Image de JavaFX para UI.
     */
    public synchronized Image grabImage() throws Exception {
        Frame frame = grabRawFrame();
        if (frame != null) {
            return converter.convert(frame);
        }
        return null;
    }

    /**
     * Convierte un Frame existente a Image de JavaFX.
     */
    public synchronized Image convertToFxImage(Frame frame) {
        if (frame != null) {
            return converter.convert(frame);
        }
        return null;
    }

    public synchronized void stop() {
        if (!running && grabber == null) {
            return;
        }
        running = false;
        log.info("Deteniendo grabber de cámara RTSP...");
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                log.warn("Error al detener grabber: {}", e.getMessage());
            } finally {
                grabber = null;
            }
        }
        log.info("Grabber detenido.");
    }

    public boolean isRunning() {
        return running;
    }

    public String getRtspUrl() {
        return rtspUrl;
    }

    public int getImageWidth() {
        return grabber != null ? grabber.getImageWidth() : 0;
    }

    public int getImageHeight() {
        return grabber != null ? grabber.getImageHeight() : 0;
    }

    @Override
    public void close() {
        stop();
    }
}
