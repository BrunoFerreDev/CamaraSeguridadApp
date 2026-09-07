package org.bdev.camarasec.core.camera;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.bytedeco.opencv.global.opencv_core.absdiff;
import static org.bytedeco.opencv.global.opencv_core.addWeighted;
import static org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur;
import static org.bytedeco.opencv.global.opencv_imgproc.RETR_EXTERNAL;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.opencv.global.opencv_imgproc.contourArea;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.dilate;
import static org.bytedeco.opencv.global.opencv_imgproc.findContours;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;
import static org.bytedeco.opencv.global.opencv_imgproc.threshold;

/**
 * Detector de movimiento basado en visión por computadora (OpenCV).
 * Compara fotogramas sucesivos contra un fondo de referencia adaptativo.
 */
public class MotionDetector {

    private static final Logger log = LoggerFactory.getLogger(MotionDetector.class);

    // Parámetros de procesamiento
    private static final int PROCESS_WIDTH = 640;
    private static final int PROCESS_HEIGHT = 360;

    private final OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
    private final List<MotionListener> listeners = new CopyOnWriteArrayList<>();

    // Configuración
    private double minMotionArea;
    private long cooldownMillis;
    private int sampleRate; // Analizar 1 de cada N frames

    // Estado interno
    private Mat referenceFrame;
    private long lastTriggerTime = 0;
    private long frameCount = 0;

    // Buffers reutilizados para evitar alocaciones nativas continuas
    private final Mat resizedMat = new Mat();
    private final Mat grayMat = new Mat();
    private final Mat blurredMat = new Mat();
    private final Mat diffMat = new Mat();
    private final Mat threshMat = new Mat();
    private final Mat dilatedMat = new Mat();
    private final Mat kernelMat = new Mat();
    private final MatVector contours = new MatVector();
    private final Size targetSize = new Size(PROCESS_WIDTH, PROCESS_HEIGHT);
    private final Size blurKernel = new Size(21, 21);

    public MotionDetector() {
        this(500.0, 5000, 3);
    }

    public MotionDetector(double minMotionArea, long cooldownMillis, int sampleRate) {
        this.minMotionArea = minMotionArea;
        this.cooldownMillis = cooldownMillis;
        this.sampleRate = Math.max(1, sampleRate);
    }

    /**
     * Procesa un Frame crudo de JavaCV.
     *
     * @param frame Fotograma capturado por el grabber.
     * @return true si se detectó movimiento en este frame.
     */
    public synchronized boolean processFrame(Frame frame) {
        if (frame == null || frame.image == null) {
            return false;
        }

        frameCount++;
        if (frameCount % sampleRate != 0) {
            return false;
        }

        Mat mat = converter.convert(frame);
        if (mat == null || mat.empty()) {
            return false;
        }

        return processMatInternal(mat);
    }

    /**
     * Procesa directamente un Mat de OpenCV (ideal para pruebas unitarias).
     */
    public synchronized boolean processMat(Mat mat) {
        if (mat == null || mat.empty()) {
            return false;
        }
        return processMatInternal(mat);
    }

    private boolean processMatInternal(Mat mat) {
        // 1. Redimensionar para reducir consumo de CPU
        resize(mat, resizedMat, targetSize);

        // 2. Convertir a escala de grises (si tiene más de 1 canal)
        if (resizedMat.channels() > 1) {
            cvtColor(resizedMat, grayMat, COLOR_BGR2GRAY);
        } else {
            resizedMat.copyTo(grayMat);
        }

        // 3. Filtro Gaussiano para eliminar ruido de alta frecuencia
        GaussianBlur(grayMat, blurredMat, blurKernel, 0);

        // 4. Inicializar frame de referencia si es el primero
        if (referenceFrame == null) {
            referenceFrame = blurredMat.clone();
            log.info("Frame de referencia inicial establecido para detección de movimiento.");
            return false;
        }

        // 5. Diferencia absoluta con el frame de referencia
        absdiff(referenceFrame, blurredMat, diffMat);

        // 6. Umbralización binaria (valores > 25 pasan a blanco 255)
        threshold(diffMat, threshMat, 25, 255, THRESH_BINARY);

        // 7. Dilatación morfológica para consolidar regiones cercanas
        dilate(threshMat, dilatedMat, kernelMat);

        // 8. Búsqueda y análisis de contornos
        findContours(dilatedMat, contours, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        double totalArea = 0;
        int detectedRegions = 0;
        long n = contours.size();

        for (long i = 0; i < n; i++) {
            double area = contourArea(contours.get(i));
            if (area >= minMotionArea) {
                totalArea += area;
                detectedRegions++;
            }
        }

        boolean motionDetected = detectedRegions > 0;

        if (motionDetected) {
            long now = System.currentTimeMillis();
            if (now - lastTriggerTime >= cooldownMillis) {
                lastTriggerTime = now;
                log.info("Movimiento detectado: área={} px, regiones={}", totalArea, detectedRegions);

                MotionDetectionEvent event = new MotionDetectionEvent(totalArea, detectedRegions);
                notifyListeners(event);
            }
        } else {
            // Actualización gradual del fondo (running average: 95% fondo viejo + 5% nuevo)
            // Absorbe variaciones lentas de luz solar sin disparar falsas alarmas
            addWeighted(referenceFrame, 0.95, blurredMat, 0.05, 0.0, referenceFrame);
        }

        return motionDetected;
    }

    public void addListener(MotionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(MotionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(MotionDetectionEvent event) {
        for (MotionListener listener : listeners) {
            try {
                listener.onMotionDetected(event);
            } catch (Exception e) {
                log.error("Error en listener de movimiento: {}", e.getMessage(), e);
            }
        }
    }

    public synchronized void resetReference() {
        if (referenceFrame != null) {
            referenceFrame.release();
            referenceFrame = null;
        }
        log.info("Frame de referencia reiniciado.");
    }

    public double getMinMotionArea() {
        return minMotionArea;
    }

    public void setMinMotionArea(double minMotionArea) {
        this.minMotionArea = minMotionArea;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public void setCooldownMillis(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = Math.max(1, sampleRate);
    }
}
