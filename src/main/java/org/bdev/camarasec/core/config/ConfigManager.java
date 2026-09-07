package org.bdev.camarasec.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalTime;
import java.util.Properties;

public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final String DEFAULT_FILE = "application.properties";

    private final Properties properties = new Properties();
    private File activeConfigFile;

    public ConfigManager() {
        loadProperties();
    }

    public ConfigManager(File configFile) {
        this.activeConfigFile = configFile;
        if (configFile != null && configFile.exists() && configFile.isFile()) {
            try (InputStream is = new FileInputStream(configFile)) {
                properties.load(is);
                log.info("Configuración cargada desde archivo personalizado: {}", configFile.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Error leyendo archivo personalizado {}: {}", configFile.getAbsolutePath(), e.getMessage());
            }
        }
    }

    private void loadProperties() {
        // 1. Intentar cargar desde el archivo en el directorio de trabajo (útil para producción/headless)
        File external = new File(DEFAULT_FILE);
        if (external.exists() && external.isFile()) {
            try (InputStream is = new FileInputStream(external)) {
                properties.load(is);
                activeConfigFile = external;
                log.info("Configuración cargada desde archivo local: {}", external.getAbsolutePath());
                return;
            } catch (IOException e) {
                log.warn("No se pudo leer el archivo local {}: {}", external.getAbsolutePath(), e.getMessage());
            }
        }

        // 2. Intentar cargar desde classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULT_FILE)) {
            if (is != null) {
                properties.load(is);
                log.info("Configuración cargada desde classpath: {}", DEFAULT_FILE);
            }
        } catch (IOException e) {
            log.warn("Error leyendo {} desde classpath: {}", DEFAULT_FILE, e.getMessage());
        }

        // 3. Fallback a ruta de desarrollo src/main/resources/application.properties
        File devFile = new File("src/main/resources/" + DEFAULT_FILE);
        if (devFile.exists() && devFile.isFile()) {
            try (InputStream is = new FileInputStream(devFile)) {
                properties.load(is);
                activeConfigFile = devFile;
                log.info("Configuración cargada desde entorno dev: {}", devFile.getAbsolutePath());
                return;
            } catch (IOException e) {
                log.warn("Error leyendo {}", devFile.getAbsolutePath(), e);
            }
        }

        if (activeConfigFile == null) {
            activeConfigFile = external;
        }
        log.warn("No se encontró ningún archivo {} con configuración previa.", DEFAULT_FILE);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getRtspUrl() {
        return properties.getProperty("camera.rtsp.url");
    }

    public String getTelegramBotToken() {
        return properties.getProperty("telegram.bot.token");
    }

    public String getTelegramChatId() {
        return properties.getProperty("telegram.chat.id");
    }

    public String getNotificationHourStart() {
        return properties.getProperty("notification.hour.start", "22:00");
    }

    public String getNotificationHourEnd() {
        return properties.getProperty("notification.hour.end", "07:00");
    }

    public double getMotionMinArea() {
        try {
            return Double.parseDouble(properties.getProperty("motion.min.area", "500.0"));
        } catch (NumberFormatException e) {
            return 500.0;
        }
    }

    public long getMotionCooldownSeconds() {
        try {
            return Long.parseLong(properties.getProperty("motion.cooldown.seconds", "5"));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    public int getMotionSampleRate() {
        try {
            return Integer.parseInt(properties.getProperty("motion.sample.rate", "3"));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    public int getClipDurationSeconds() {
        try {
            return Integer.parseInt(properties.getProperty("clip.duration.seconds", "15"));
        } catch (NumberFormatException e) {
            return 15;
        }
    }

    public int getClipRetentionDays() {
        try {
            return Integer.parseInt(properties.getProperty("clip.retention.days", "15"));
        } catch (NumberFormatException e) {
            return 15;
        }
    }

    public String getClipStorageDir() {
        return properties.getProperty("clip.storage.dir", "clips");
    }

    public int getClipPreRollSeconds() {
        try {
            return Integer.parseInt(properties.getProperty("clip.preroll.seconds", "3"));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    public boolean isPtzEnabled() {
        return Boolean.parseBoolean(properties.getProperty("ptz.enabled", "true"));
    }

    public String getPtzCameraHost() {
        String host = properties.getProperty("ptz.camera.host");
        if (host != null && !host.isBlank()) {
            return host;
        }
        return extractHostFromRtsp(getRtspUrl());
    }

    public String getPtzUsername() {
        String user = properties.getProperty("ptz.username");
        if (user != null && !user.isBlank()) {
            return user;
        }
        String extracted = extractUserFromRtsp(getRtspUrl());
        return extracted != null && !extracted.isBlank() ? extracted : "admin";
    }

    public String getPtzPassword() {
        String pass = properties.getProperty("ptz.password");
        if (pass != null && !pass.isBlank()) {
            return pass;
        }
        return extractPasswordFromRtsp(getRtspUrl());
    }

    private String extractHostFromRtsp(String rtspUrl) {
        if (rtspUrl == null) return "";
        try {
            int atIndex = rtspUrl.indexOf('@');
            if (atIndex != -1) {
                String remainder = rtspUrl.substring(atIndex + 1);
                int colonOrSlash = remainder.indexOf(':');
                if (colonOrSlash == -1) colonOrSlash = remainder.indexOf('/');
                if (colonOrSlash != -1) return remainder.substring(0, colonOrSlash);
                return remainder;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractUserFromRtsp(String rtspUrl) {
        if (rtspUrl == null) return "";
        try {
            int schemaIndex = rtspUrl.indexOf("://");
            int atIndex = rtspUrl.indexOf('@');
            if (schemaIndex != -1 && atIndex != -1 && atIndex > schemaIndex) {
                String userPass = rtspUrl.substring(schemaIndex + 3, atIndex);
                int colonIndex = userPass.indexOf(':');
                if (colonIndex != -1) return userPass.substring(0, colonIndex);
                return userPass;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractPasswordFromRtsp(String rtspUrl) {
        if (rtspUrl == null) return "";
        try {
            int schemaIndex = rtspUrl.indexOf("://");
            int atIndex = rtspUrl.indexOf('@');
            if (schemaIndex != -1 && atIndex != -1 && atIndex > schemaIndex) {
                String userPass = rtspUrl.substring(schemaIndex + 3, atIndex);
                int colonIndex = userPass.indexOf(':');
                if (colonIndex != -1) return userPass.substring(colonIndex + 1);
            }
        } catch (Exception ignored) {}
        return "";
    }

    public boolean isAlwaysNotify() {
        return Boolean.parseBoolean(properties.getProperty("notification.always", "false"));
    }

    /**
     * Determina si corresponde notificar según el modo y horario configurado.
     * Si notification.always es true (Modo Ausente), siempre notifica (24/7).
     * De lo contrario (Modo En Casa), notifica solo dentro de la ventana horaria.
     */
    public boolean shouldNotify(LocalTime now) {
        if (isAlwaysNotify()) {
            return true;
        }
        return isWithinSchedule(now);
    }

    public File getActiveConfigFile() {
        return activeConfigFile;
    }

    public void setActiveConfigFile(File activeConfigFile) {
        this.activeConfigFile = activeConfigFile;
    }

    /**
     * Mapea los valores actuales a una instancia AppConfig.
     */
    public AppConfig toAppConfig() {
        AppConfig config = new AppConfig();
        config.setRtspUrl(getRtspUrl() != null ? getRtspUrl() : "");
        config.setTelegramBotToken(getTelegramBotToken() != null ? getTelegramBotToken() : "");
        config.setTelegramChatId(getTelegramChatId() != null ? getTelegramChatId() : "");
        config.setNotificationHourStart(getNotificationHourStart());
        config.setNotificationHourEnd(getNotificationHourEnd());
        config.setAlwaysNotify(isAlwaysNotify());
        config.setMotionMinArea(getMotionMinArea());
        config.setMotionCooldownSeconds(getMotionCooldownSeconds());
        config.setMotionSampleRate(getMotionSampleRate());
        config.setClipDurationSeconds(getClipDurationSeconds());
        config.setClipRetentionDays(getClipRetentionDays());
        config.setClipStorageDir(getClipStorageDir());
        config.setPreRollSeconds(getClipPreRollSeconds());
        config.setPtzEnabled(isPtzEnabled());
        config.setPtzCameraHost(getPtzCameraHost());
        config.setPtzUsername(getPtzUsername());
        config.setPtzPassword(getPtzPassword());
        return config;
    }

    /**
     * Guarda la configuración dada en memoria y persiste en el archivo de propiedades.
     */
    public synchronized void saveConfig(AppConfig config) throws IOException {
        if (config == null) {
            return;
        }

        if (config.getRtspUrl() != null) properties.setProperty("camera.rtsp.url", config.getRtspUrl());
        if (config.getTelegramBotToken() != null) properties.setProperty("telegram.bot.token", config.getTelegramBotToken());
        if (config.getTelegramChatId() != null) properties.setProperty("telegram.chat.id", config.getTelegramChatId());
        if (config.getNotificationHourStart() != null) properties.setProperty("notification.hour.start", config.getNotificationHourStart());
        if (config.getNotificationHourEnd() != null) properties.setProperty("notification.hour.end", config.getNotificationHourEnd());
        properties.setProperty("notification.always", String.valueOf(config.isAlwaysNotify()));
        properties.setProperty("motion.min.area", String.valueOf(config.getMotionMinArea()));
        properties.setProperty("motion.cooldown.seconds", String.valueOf(config.getMotionCooldownSeconds()));
        properties.setProperty("motion.sample.rate", String.valueOf(config.getMotionSampleRate()));
        properties.setProperty("clip.duration.seconds", String.valueOf(config.getClipDurationSeconds()));
        properties.setProperty("clip.retention.days", String.valueOf(config.getClipRetentionDays()));
        if (config.getClipStorageDir() != null) properties.setProperty("clip.storage.dir", config.getClipStorageDir());
        properties.setProperty("clip.preroll.seconds", String.valueOf(config.getPreRollSeconds()));
        properties.setProperty("ptz.enabled", String.valueOf(config.isPtzEnabled()));
        if (config.getPtzCameraHost() != null) properties.setProperty("ptz.camera.host", config.getPtzCameraHost());
        if (config.getPtzUsername() != null) properties.setProperty("ptz.username", config.getPtzUsername());
        if (config.getPtzPassword() != null) properties.setProperty("ptz.password", config.getPtzPassword());

        File targetFile = activeConfigFile;
        if (targetFile == null) {
            targetFile = new File(DEFAULT_FILE);
            activeConfigFile = targetFile;
        }

        try (OutputStream os = new FileOutputStream(targetFile)) {
            properties.store(os, "CamaraSec Configuration");
            log.info("Configuración guardada exitosamente en {}", targetFile.getAbsolutePath());
        }
    }

    /**
     * Verifica si la hora dada se encuentra dentro del rango de notificación/grabación configurado.
     */
    public boolean isWithinSchedule(LocalTime now) {
        try {
            LocalTime start = LocalTime.parse(getNotificationHourStart());
            LocalTime end = LocalTime.parse(getNotificationHourEnd());
            return isTimeInInterval(now, start, end);
        } catch (Exception e) {
            log.warn("Error al evaluar horario configurado: {}", e.getMessage());
            return true;
        }
    }

    public static boolean isTimeInInterval(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            // Ejemplo: 08:00 a 20:00
            return !now.isBefore(start) && !now.isAfter(end);
        } else {
            // Ejemplo: 22:00 a 07:00 (cruza medianoche)
            return !now.isBefore(start) || !now.isAfter(end);
        }
    }
}
