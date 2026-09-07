package org.bdev.camarasec.core.recorder;

import java.io.File;

/**
 * Callback invocado al finalizar la grabación y guardado de un clip de video.
 */
@FunctionalInterface
public interface ClipSavedListener {

    /**
     * Invocado cuando un nuevo clip .mp4 ha sido cerrado y guardado en disco.
     *
     * @param clipFile Archivo de video resultante.
     */
    void onClipSaved(File clipFile);
}
