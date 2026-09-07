package org.bdev.camarasec.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @Test
    void testDaytimeSchedule() {
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(18, 0);

        assertTrue(ConfigManager.isTimeInInterval(LocalTime.of(8, 0), start, end), "Límite inferior debe estar dentro");
        assertTrue(ConfigManager.isTimeInInterval(LocalTime.of(12, 30), start, end), "Mediodía debe estar dentro");
        assertTrue(ConfigManager.isTimeInInterval(LocalTime.of(18, 0), start, end), "Límite superior debe estar dentro");

        assertFalse(ConfigManager.isTimeInInterval(LocalTime.of(7, 59), start, end), "Antes de las 8 debe estar fuera");
        assertFalse(ConfigManager.isTimeInInterval(LocalTime.of(18, 1), start, end), "Después de las 18 debe estar fuera");
        assertFalse(ConfigManager.isTimeInInterval(LocalTime.of(23, 0), start, end), "Medianoche debe estar fuera");
    }

    @Test
    void testOvernightSchedule() {
        // Horario nocturno habitual de seguridad: 22:00 a 07:00
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(7, 0);

        assertTrue(ConfigManager.isTimeInInterval(LocalTime.of(22, 0), start, end), "22:00 debe estar dentro");
        assertTrue(ConfigManager.isTimeInInterval(LocalTime.of(23, 45), start, end), "23:45 debe estar dentro");
        assertTrue(ConfigManager.isTimeInInterval(LocalTime.of(0, 0), start, end), "00:00 debe estar dentro");
        assertTrue(ConfigManager.isTimeInInterval(LocalTime.of(4, 15), start, end), "04:15 debe estar dentro");
        assertTrue(ConfigManager.isTimeInInterval(LocalTime.of(7, 0), start, end), "07:00 debe estar dentro");

        assertFalse(ConfigManager.isTimeInInterval(LocalTime.of(7, 1), start, end), "07:01 debe estar fuera");
        assertFalse(ConfigManager.isTimeInInterval(LocalTime.of(12, 0), start, end), "12:00 debe estar fuera");
        assertFalse(ConfigManager.isTimeInInterval(LocalTime.of(21, 59), start, end), "21:59 debe estar fuera");
    }

    @Test
    void testSaveAndLoadAppConfig(@TempDir Path tempDir) throws IOException {
        File configFile = tempDir.resolve("test-application.properties").toFile();
        ConfigManager manager = new ConfigManager(configFile);
        manager.setActiveConfigFile(configFile);

        AppConfig config = new AppConfig();
        config.setRtspUrl("rtsp://user:pass@192.168.1.50:554/live");
        config.setTelegramBotToken("12345:TOKEN");
        config.setTelegramChatId("987654");
        config.setNotificationHourStart("23:00");
        config.setNotificationHourEnd("06:30");
        config.setAlwaysNotify(true);
        config.setMotionMinArea(750.0);
        config.setMotionCooldownSeconds(10);
        config.setMotionSampleRate(4);
        config.setClipDurationSeconds(20);
        config.setClipRetentionDays(30);
        config.setClipStorageDir("/tmp/security_clips");
        config.setPreRollSeconds(4);
        config.setPtzEnabled(true);
        config.setPtzCameraHost("192.168.1.100");
        config.setPtzUsername("admin");
        config.setPtzPassword("secretpass");

        manager.saveConfig(config);

        // Recargar con una nueva instancia apuntando al mismo archivo
        ConfigManager reloaded = new ConfigManager(configFile);
        assertEquals("rtsp://user:pass@192.168.1.50:554/live", reloaded.getRtspUrl());
        assertEquals("12345:TOKEN", reloaded.getTelegramBotToken());
        assertEquals("987654", reloaded.getTelegramChatId());
        assertEquals("23:00", reloaded.getNotificationHourStart());
        assertEquals("06:30", reloaded.getNotificationHourEnd());
        assertTrue(reloaded.isAlwaysNotify());
        assertEquals(750.0, reloaded.getMotionMinArea());
        assertEquals(10, reloaded.getMotionCooldownSeconds());
        assertEquals(4, reloaded.getMotionSampleRate());
        assertEquals(20, reloaded.getClipDurationSeconds());
        assertEquals(30, reloaded.getClipRetentionDays());
        assertEquals("/tmp/security_clips", reloaded.getClipStorageDir());
        assertEquals(4, reloaded.getClipPreRollSeconds());
        assertTrue(reloaded.isPtzEnabled());
        assertEquals("192.168.1.100", reloaded.getPtzCameraHost());
        assertEquals("admin", reloaded.getPtzUsername());
        assertEquals("secretpass", reloaded.getPtzPassword());

        // Probar toAppConfig
        AppConfig exported = reloaded.toAppConfig();
        assertEquals(config.getRtspUrl(), exported.getRtspUrl());
        assertEquals(config.getTelegramBotToken(), exported.getTelegramBotToken());
        assertEquals(config.getTelegramChatId(), exported.getTelegramChatId());
        assertEquals(config.getNotificationHourStart(), exported.getNotificationHourStart());
        assertEquals(config.getNotificationHourEnd(), exported.getNotificationHourEnd());
        assertTrue(exported.isAlwaysNotify());
        assertEquals(config.getMotionMinArea(), exported.getMotionMinArea());
        assertEquals(config.getMotionCooldownSeconds(), exported.getMotionCooldownSeconds());
        assertEquals(config.getClipDurationSeconds(), exported.getClipDurationSeconds());
        assertEquals("/tmp/security_clips", exported.getClipStorageDir());
        assertEquals(4, exported.getPreRollSeconds());
        assertTrue(exported.isPtzEnabled());
        assertEquals("192.168.1.100", exported.getPtzCameraHost());
    }

    @Test
    void testShouldNotifyWithAlwaysNotify(@TempDir Path tempDir) throws IOException {
        File configFile = tempDir.resolve("test-schedule.properties").toFile();
        ConfigManager manager = new ConfigManager(configFile);
        manager.setActiveConfigFile(configFile);

        AppConfig config = new AppConfig();
        config.setNotificationHourStart("22:00");
        config.setNotificationHourEnd("07:00");
        config.setAlwaysNotify(true); // Modo Ausente
        manager.saveConfig(config);

        ConfigManager reloaded = new ConfigManager(configFile);
        // Al mediodía normalmente estaría fuera de horario, pero en Modo Ausente debe notificar
        assertTrue(reloaded.shouldNotify(LocalTime.of(12, 0)));
        assertTrue(reloaded.shouldNotify(LocalTime.of(23, 0)));

        // Cambiar a Modo En Casa (false)
        config.setAlwaysNotify(false);
        reloaded.saveConfig(config);

        ConfigManager homeMode = new ConfigManager(configFile);
        assertFalse(homeMode.shouldNotify(LocalTime.of(12, 0)));
        assertTrue(homeMode.shouldNotify(LocalTime.of(23, 0)));
    }
}
