package org.bdev.camarasec.core.camera;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.bytedeco.opencv.global.opencv_imgproc.rectangle;
import static org.junit.jupiter.api.Assertions.*;

class MotionDetectorTest {

    private MotionDetector detector;

    @BeforeEach
    void setUp() {
        // minArea = 100 px, cooldown = 1000ms, sampleRate = 1 (procesar cada frame en tests)
        detector = new MotionDetector(100.0, 1000, 1);
    }

    @Test
    void testInitialFrameEstablishesReference() {
        Mat frame1 = new Mat(360, 640, CV_8UC3, new Scalar(50, 50, 50, 0));
        AtomicBoolean triggered = new AtomicBoolean(false);
        detector.addListener(event -> triggered.set(true));

        boolean detected = detector.processMat(frame1);

        assertFalse(detected, "El primer fotograma debe servir de referencia inicial sin disparar detección");
        assertFalse(triggered.get());
    }

    @Test
    void testIdenticalFramesDoNotTriggerMotion() {
        Mat frame1 = new Mat(360, 640, CV_8UC3, new Scalar(50, 50, 50, 0));
        Mat frame2 = new Mat(360, 640, CV_8UC3, new Scalar(50, 50, 50, 0));

        AtomicBoolean triggered = new AtomicBoolean(false);
        detector.addListener(event -> triggered.set(true));

        detector.processMat(frame1);
        boolean detected = detector.processMat(frame2);

        assertFalse(detected, "Fotogramas idénticos no deben disparar movimiento");
        assertFalse(triggered.get());
    }

    @Test
    void testSignificantChangeTriggersMotion() {
        Mat frame1 = new Mat(360, 640, CV_8UC3, new Scalar(50, 50, 50, 0));
        Mat frame2 = new Mat(360, 640, CV_8UC3, new Scalar(50, 50, 50, 0));

        // Dibujar un rectángulo blanco grande en frame2
        rectangle(frame2, new Point(100, 100), new Point(250, 250), new Scalar(255, 255, 255, 0), -1, 8, 0);

        AtomicBoolean triggered = new AtomicBoolean(false);
        detector.addListener(event -> {
            triggered.set(true);
            assertTrue(event.motionArea() > 100, "El área detectada debe superar el umbral mínimo");
        });

        detector.processMat(frame1);
        boolean detected = detector.processMat(frame2);

        assertTrue(detected, "Un cambio significativo debe ser detectado como movimiento");
        assertTrue(triggered.get(), "El listener debe ser notificado");
    }

    @Test
    void testCooldownPreventsImmediateRetrigger() {
        Mat frame1 = new Mat(360, 640, CV_8UC3, new Scalar(50, 50, 50, 0));
        Mat frame2 = new Mat(360, 640, CV_8UC3, new Scalar(50, 50, 50, 0));
        rectangle(frame2, new Point(100, 100), new Point(250, 250), new Scalar(255, 255, 255, 0), -1, 8, 0);

        Mat frame3 = new Mat(360, 640, CV_8UC3, new Scalar(50, 50, 50, 0));
        rectangle(frame3, new Point(120, 120), new Point(270, 270), new Scalar(255, 255, 255, 0), -1, 8, 0);

        AtomicInteger triggerCount = new AtomicInteger(0);
        detector.addListener(event -> triggerCount.incrementAndGet());

        detector.processMat(frame1); // Referencia inicial
        detector.processMat(frame2); // Dispara evento 1
        detector.processMat(frame3); // Movimiento presente pero bloqueado por cooldown

        assertEquals(1, triggerCount.get(), "El cooldown debe evitar múltiples emisiones consecutivas");
    }
}
