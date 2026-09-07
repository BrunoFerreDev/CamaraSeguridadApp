package org.bdev.camarasec.core.recorder;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.junit.jupiter.api.Assertions.*;

class ClipRecorderTest {

    @TempDir
    Path tempDir;

    private File testStorageDir;
    private ClipRecorder recorder;
    private final OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();

    @BeforeEach
    void setUp() {
        testStorageDir = tempDir.resolve("test_clips").toFile();
        recorder = new ClipRecorder(testStorageDir, 3, 15);
    }

    @AfterEach
    void tearDown() {
        if (recorder != null) {
            recorder.close();
        }
    }

    @Test
    void testRecordingLifecycleGeneratesMp4() throws Exception {
        AtomicReference<File> savedFile = new AtomicReference<>();
        recorder.addListener(savedFile::set);

        recorder.triggerRecording();
        assertTrue(recorder.isRecording(), "El grabador debe encontrarse en estado activo tras el trigger");

        // Generar 10 frames sintéticos de 320x240
        Mat syntheticMat = new Mat(240, 320, CV_8UC3, new Scalar(100, 150, 200, 0));
        Frame syntheticFrame = converter.convert(syntheticMat);

        for (int i = 0; i < 10; i++) {
            recorder.recordFrame(syntheticFrame);
        }

        recorder.stopRecording();
        assertFalse(recorder.isRecording(), "El grabador debe indicar que finalizó la grabación");

        File file = savedFile.get();
        assertNotNull(file, "El listener de clip guardado debió ser notificado");
        assertTrue(file.exists(), "El archivo .mp4 generado debe existir en disco");
        assertTrue(file.getName().endsWith(".mp4"), "El archivo debe tener extensión .mp4");
        assertTrue(file.length() > 0, "El archivo .mp4 no debe estar vacío");
    }

    @Test
    void testRetentionPolicyCleansOldFiles() throws IOException {
        assertTrue(testStorageDir.mkdirs() || testStorageDir.exists());

        File oldFile = new File(testStorageDir, "clip_20200101_000000.mp4");
        assertTrue(oldFile.createNewFile());
        // Simular antigüedad de 30 días atrás
        assertTrue(oldFile.setLastModified(System.currentTimeMillis() - (30L * 24 * 3600 * 1000L)));

        File newFile = new File(testStorageDir, "clip_20260904_000000.mp4");
        assertTrue(newFile.createNewFile());
        assertTrue(newFile.setLastModified(System.currentTimeMillis()));

        recorder.cleanOldClips();

        assertFalse(oldFile.exists(), "El clip antiguo (> 15 días) debe ser eliminado");
        assertTrue(newFile.exists(), "El clip reciente debe ser preservado");
    }
}
