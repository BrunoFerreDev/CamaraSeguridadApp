package org.bdev.camarasec.core.camera;

/**
 * Interfaz de callback para reaccionar a eventos de detección de movimiento.
 */
@FunctionalInterface
public interface MotionListener {

    /**
     * Invocado cuando se detecta movimiento que supera los umbrales configurados.
     *
     * @param event Información del evento de movimiento.
     */
    void onMotionDetected(MotionDetectionEvent event);
}
