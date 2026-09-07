package org.bdev.camarasec.core.recorder;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;

/**
 * Grabador de clips de video .mp4 en respuesta a eventos de movimiento.
 */
public class ClipRecorder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClipRecorder.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private File storageDir;
    private int durationSeconds;
    private int retentionDays;
    private int preRollSeconds;
    private final PreRollBuffer preRollBuffer;
    private final List<ClipSavedListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean recording = false;
    private FFmpegFrameRecorder currentRecorder;
    private File currentFile;
    private long recordingStartTime = 0;

    public ClipRecorder() {
        this(new File("clips"), 15, 15, 3);
    }

    public ClipRecorder(File storageDir, int durationSeconds, int retentionDays) {
        this(storageDir, durationSeconds, retentionDays, 3);
    }

    public ClipRecorder(File storageDir, int durationSeconds, int retentionDays, int preRollSeconds) {
        this.storageDir = storageDir;
        this.durationSeconds = Math.max(3, durationSeconds);
        this.retentionDays = Math.max(1, retentionDays);
        this.preRollSeconds = Math.max(0, preRollSeconds);
        this.preRollBuffer = new PreRollBuffer(this.preRollSeconds * 25);
        ensureStorageDir();
    }

    private void ensureStorageDir() {
        if (!storageDir.exists() && storageDir.mkdirs()) {
            log.info("Carpeta de clips creada: {}", storageDir.getAbsolutePath());
        }
    }

    /**
     * Inicia una sesión de grabación de clip. Si ya está grabando, extiende el tiempo.
     */
    public synchronized void triggerRecording() {
        if (recording) {
            log.debug("Grabación ya en curso; extendiendo tiempo de grabación.");
            recordingStartTime = System.currentTimeMillis();
            return;
        }

        ensureStorageDir();
        String filename = "clip_" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".mp4";
        currentFile = new File(storageDir, filename);
        recordingStartTime = System.currentTimeMillis();
        recording = true;
        log.info("Iniciando grabación de clip: {}", currentFile.getAbsolutePath());
    }

    /**
     * Graba un frame si la sesión de grabación está activa, o lo acumula en el pre-roll buffer si está en espera.
     *
     * @param frame Fotograma capturado de la cámara.
     */
    public synchronized void recordFrame(Frame frame) {
        if (frame == null || frame.image == null) {
            return;
        }

        if (!recording) {
            preRollBuffer.add(frame);
            return;
        }

        try {
            // Inicializar grabador en el primer frame si aún no se inicializó
            if (currentRecorder == null) {
                int width = frame.imageWidth;
                int height = frame.imageHeight;

                currentRecorder = new FFmpegFrameRecorder(currentFile, width, height);
                currentRecorder.setFormat("mp4");
                currentRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                currentRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                currentRecorder.setFrameRate(25);
                currentRecorder.setVideoOption("preset", "ultrafast");
                currentRecorder.setVideoOption("tune", "zerolatency");
                currentRecorder.setVideoQuality(23);
                currentRecorder.start();

                // Volcar fotogramas previos retenidos en el buffer circular (pre-roll)
                List<Frame> preRollFrames = preRollBuffer.drainAll();
                if (!preRollFrames.isEmpty()) {
                    log.info("Volcando {} fotogramas previos (pre-roll) al clip...", preRollFrames.size());
                    for (Frame pf : preRollFrames) {
                        try {
                            currentRecorder.record(pf);
                        } finally {
                            pf.close();
                        }
                    }
                }
            }

            currentRecorder.record(frame);

            // Chequear si se alcanzó la duración configurada
            long elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000L;
            if (elapsed >= durationSeconds) {
                stopRecording();
            }

        } catch (Exception e) {
            log.error("Error al grabar frame en {}: {}", currentFile != null ? currentFile.getName() : "null", e.getMessage());
            stopRecording();
        }
    }

    /**
     * Detiene y finaliza la grabación del clip actual.
     */
    public synchronized void stopRecording() {
        if (!recording && currentRecorder == null) {
            return;
        }

        recording = false;
        File completedFile = currentFile;

        if (currentRecorder != null) {
            try {
                currentRecorder.stop();
                currentRecorder.release();
                log.info("Clip grabado exitosamente: {} ({} bytes)",
                        completedFile.getName(), completedFile.exists() ? completedFile.length() : 0);

                notifyClipSaved(completedFile);
            } catch (Exception e) {
                log.error("Error cerrando grabador de clip: {}", e.getMessage(), e);
            } finally {
                currentRecorder = null;
                currentFile = null;
            }
        }

        // Ejecutar política de retención en background
        ForkJoinPool.commonPool().execute(this::cleanOldClips);
    }

    public void addListener(ClipSavedListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(ClipSavedListener listener) {
        listeners.remove(listener);
    }

    private void notifyClipSaved(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        for (ClipSavedListener listener : listeners) {
            try {
                listener.onClipSaved(file);
            } catch (Exception e) {
                log.error("Error en ClipSavedListener: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Elimina clips que superen la cantidad de días de retención configurada.
     */
    public void cleanOldClips() {
        if (!storageDir.exists() || !storageDir.isDirectory()) {
            return;
        }

        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".mp4"));
        if (files == null || files.length == 0) {
            return;
        }

        long maxAgeMillis = (long) retentionDays * 24 * 3600 * 1000L;
        long now = System.currentTimeMillis();

        int deletedCount = 0;
        for (File file : files) {
            long age = now - file.lastModified();
            if (age > maxAgeMillis) {
                if (file.delete()) {
                    deletedCount++;
                    log.info("Clip antiguo eliminado por política de retención: {}", file.getName());
                }
            }
        }

        if (deletedCount > 0) {
            log.info("Limpieza de retención completada: {} clips eliminados.", deletedCount);
        }
    }

    public boolean isRecording() {
        return recording;
    }

    public File getStorageDir() {
        return storageDir;
    }

    public synchronized void setStorageDir(File storageDir) {
        if (storageDir != null) {
            this.storageDir = storageDir;
            ensureStorageDir();
            log.info("Directorio de almacenamiento de clips actualizado a: {}", storageDir.getAbsolutePath());
        }
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = Math.max(3, durationSeconds);
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = Math.max(1, retentionDays);
    }

    public int getPreRollSeconds() {
        return preRollSeconds;
    }

    public synchronized void setPreRollSeconds(int preRollSeconds) {
        this.preRollSeconds = Math.max(0, preRollSeconds);
        this.preRollBuffer.setMaxCapacity(this.preRollSeconds * 25);
        log.info("Segundos de pre-roll actualizados a: {}s (capacidad: {} frames)",
                this.preRollSeconds, this.preRollSeconds * 25);
    }

    @Override
    public void close() {
        stopRecording();
        preRollBuffer.close();
    }
}
