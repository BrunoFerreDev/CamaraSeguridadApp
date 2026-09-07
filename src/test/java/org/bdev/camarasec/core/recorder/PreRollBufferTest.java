package org.bdev.camarasec.core.recorder;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreRollBufferTest {

    private Java2DFrameConverter converter;
    private PreRollBuffer buffer;

    @BeforeEach
    void setUp() {
        converter = new Java2DFrameConverter();
        buffer = new PreRollBuffer(3);
    }

    @AfterEach
    void tearDown() {
        buffer.close();
        converter.close();
    }

    private Frame createTestFrame(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        return converter.convert(img);
    }

    @Test
    void testCapacityAndDrain() {
        Frame f1 = createTestFrame(10, 10);
        Frame f2 = createTestFrame(20, 20);
        Frame f3 = createTestFrame(30, 30);

        buffer.add(f1);
        buffer.add(f2);
        buffer.add(f3);

        assertEquals(3, buffer.size());

        List<Frame> drained = buffer.drainAll();
        assertEquals(3, drained.size());
        assertEquals(0, buffer.size());

        // Verificar dimensiones en orden FIFO
        assertEquals(10, drained.get(0).imageWidth);
        assertEquals(20, drained.get(1).imageWidth);
        assertEquals(30, drained.get(2).imageWidth);

        // Liberar frames extraídos
        for (Frame f : drained) {
            f.close();
        }
    }

    @Test
    void testEvictionOnOverflow() {
        Frame f1 = createTestFrame(10, 10);
        Frame f2 = createTestFrame(20, 20);
        Frame f3 = createTestFrame(30, 30);
        Frame f4 = createTestFrame(40, 40);

        buffer.add(f1);
        buffer.add(f2);
        buffer.add(f3);
        buffer.add(f4); // Debe expulsar f1

        assertEquals(3, buffer.size());

        List<Frame> drained = buffer.drainAll();
        assertEquals(3, drained.size());
        assertEquals(20, drained.get(0).imageWidth);
        assertEquals(30, drained.get(1).imageWidth);
        assertEquals(40, drained.get(2).imageWidth);

        for (Frame f : drained) {
            f.close();
        }
    }

    @Test
    void testZeroCapacity() {
        buffer.setMaxCapacity(0);
        Frame f = createTestFrame(10, 10);
        buffer.add(f);

        assertEquals(0, buffer.size());
        assertTrue(buffer.drainAll().isEmpty());
    }
}
