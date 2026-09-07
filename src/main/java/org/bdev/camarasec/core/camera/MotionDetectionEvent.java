package org.bdev.camarasec.core.camera;

import java.time.Instant;

/**
 * Evento emitido cuando se detecta movimiento en el flujo de video.
 *
 * @param timestamp Momento exacto en que ocurrió la detección.
 * @param motionArea Suma del área de los contornos detectados (en píxeles de la imagen procesada).
 * @param contourCount Cantidad de zonas o contornos individuales con movimiento.
 */
public record MotionDetectionEvent(
        Instant timestamp,
        double motionArea,
        int contourCount
) {
    public MotionDetectionEvent(double motionArea, int contourCount) {
        this(Instant.now(), motionArea, contourCount);
    }
}
