package org.bdev.camarasec.core.recorder;

import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Buffer circular en memoria para retener los fotogramas previos al inicio de la grabación.
 * Asegura la liberación de memoria nativa al descartar fotogramas antiguos.
 */
public class PreRollBuffer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PreRollBuffer.class);

    private final Deque<Frame> buffer = new ArrayDeque<>();
    private int maxCapacity;

    public PreRollBuffer(int maxCapacity) {
        this.maxCapacity = Math.max(0, maxCapacity);
    }

    /**
     * Agrega un fotograma al buffer circular.
     * Si el buffer supera la capacidad máxima, el fotograma más antiguo es descartado y liberado.
     */
    public synchronized void add(Frame frame) {
        if (maxCapacity <= 0 || frame == null || frame.image == null) {
            return;
        }

        try {
            Frame cloned = frame.clone();

            while (buffer.size() >= maxCapacity) {
                Frame oldest = buffer.pollFirst();
                if (oldest != null) {
                    oldest.close();
                }
            }

            buffer.addLast(cloned);
        } catch (Exception e) {
            log.warn("Error al clonar fotograma para PreRollBuffer: {}", e.getMessage());
        }
    }

    /**
     * Extrae y devuelve todos los fotogramas acumulados en orden cronológico,
     * vaciando el buffer y transfiriendo la propiedad de los fotogramas al invocador.
     */
    public synchronized List<Frame> drainAll() {
        List<Frame> result = new ArrayList<>(buffer.size());
        while (!buffer.isEmpty()) {
            Frame f = buffer.pollFirst();
            if (f != null) {
                result.add(f);
            }
        }
        return result;
    }

    /**
     * Cantidad actual de fotogramas en el buffer.
     */
    public synchronized int size() {
        return buffer.size();
    }

    /**
     * Capacidad máxima configurada.
     */
    public synchronized int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * Actualiza la capacidad máxima del buffer circular.
     */
    public synchronized void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = Math.max(0, maxCapacity);
        while (buffer.size() > this.maxCapacity) {
            Frame oldest = buffer.pollFirst();
            if (oldest != null) {
                oldest.close();
            }
        }
    }

    /**
     * Limpia y libera todos los fotogramas almacenados.
     */
    public synchronized void clear() {
        while (!buffer.isEmpty()) {
            Frame f = buffer.pollFirst();
            if (f != null) {
                try {
                    f.close();
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void close() {
        clear();
    }
}
