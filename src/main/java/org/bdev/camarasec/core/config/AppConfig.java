package org.bdev.camarasec.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Modelo de configuración de la aplicación CamaraSec.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {

    private String rtspUrl = "";
    private String telegramBotToken = "";
    private String telegramChatId = "";
    private String notificationHourStart = "22:00";
    private String notificationHourEnd = "07:00";
    private boolean alwaysNotify = false; // true = Modo Ausente (24/7), false = Modo En Casa (solo en horario)
    private double motionMinArea = 500.0;
    private long motionCooldownSeconds = 5;
    private int motionSampleRate = 3;
    private int clipDurationSeconds = 15;
    private int clipRetentionDays = 15;
    private String clipStorageDir = "clips";
    private int preRollSeconds = 3;

    // Control PTZ
    private boolean ptzEnabled = true;
    private String ptzCameraHost = "";
    private String ptzUsername = "admin";
    private String ptzPassword = "";

    public AppConfig() {
    }

    public String getRtspUrl() {
        return rtspUrl;
    }

    public void setRtspUrl(String rtspUrl) {
        this.rtspUrl = rtspUrl;
    }

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public void setTelegramBotToken(String telegramBotToken) {
        this.telegramBotToken = telegramBotToken;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public String getNotificationHourStart() {
        return notificationHourStart;
    }

    public void setNotificationHourStart(String notificationHourStart) {
        this.notificationHourStart = notificationHourStart;
    }

    public String getNotificationHourEnd() {
        return notificationHourEnd;
    }

    public void setNotificationHourEnd(String notificationHourEnd) {
        this.notificationHourEnd = notificationHourEnd;
    }

    public boolean isAlwaysNotify() {
        return alwaysNotify;
    }

    public void setAlwaysNotify(boolean alwaysNotify) {
        this.alwaysNotify = alwaysNotify;
    }

    public double getMotionMinArea() {
        return motionMinArea;
    }

    public void setMotionMinArea(double motionMinArea) {
        this.motionMinArea = motionMinArea;
    }

    public long getMotionCooldownSeconds() {
        return motionCooldownSeconds;
    }

    public void setMotionCooldownSeconds(long motionCooldownSeconds) {
        this.motionCooldownSeconds = motionCooldownSeconds;
    }

    public int getMotionSampleRate() {
        return motionSampleRate;
    }

    public void setMotionSampleRate(int motionSampleRate) {
        this.motionSampleRate = motionSampleRate;
    }

    public int getClipDurationSeconds() {
        return clipDurationSeconds;
    }

    public void setClipDurationSeconds(int clipDurationSeconds) {
        this.clipDurationSeconds = clipDurationSeconds;
    }

    public int getClipRetentionDays() {
        return clipRetentionDays;
    }

    public void setClipRetentionDays(int clipRetentionDays) {
        this.clipRetentionDays = clipRetentionDays;
    }

    public String getClipStorageDir() {
        return clipStorageDir;
    }

    public void setClipStorageDir(String clipStorageDir) {
        this.clipStorageDir = clipStorageDir;
    }

    public int getPreRollSeconds() {
        return preRollSeconds;
    }

    public void setPreRollSeconds(int preRollSeconds) {
        this.preRollSeconds = preRollSeconds;
    }

    public boolean isPtzEnabled() {
        return ptzEnabled;
    }

    public void setPtzEnabled(boolean ptzEnabled) {
        this.ptzEnabled = ptzEnabled;
    }

    public String getPtzCameraHost() {
        return ptzCameraHost;
    }

    public void setPtzCameraHost(String ptzCameraHost) {
        this.ptzCameraHost = ptzCameraHost;
    }

    public String getPtzUsername() {
        return ptzUsername;
    }

    public void setPtzUsername(String ptzUsername) {
        this.ptzUsername = ptzUsername;
    }

    public String getPtzPassword() {
        return ptzPassword;
    }

    public void setPtzPassword(String ptzPassword) {
        this.ptzPassword = ptzPassword;
    }
}
