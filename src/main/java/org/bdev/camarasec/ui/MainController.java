package org.bdev.camarasec.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.bdev.camarasec.core.camera.CameraConnector;
import org.bdev.camarasec.core.camera.MotionDetectionEvent;
import org.bdev.camarasec.core.camera.MotionDetector;
import org.bdev.camarasec.core.config.AppConfig;
import org.bdev.camarasec.core.config.ConfigManager;
import org.bdev.camarasec.core.notification.TelegramNotifier;
import org.bdev.camarasec.core.ptz.EzvizPtzService;
import org.bdev.camarasec.core.ptz.PtzDirection;
import org.bdev.camarasec.core.recorder.ClipRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML
    private StackPane videoContainer;

    @FXML
    private ImageView cameraView;

    @FXML
    private VBox placeholderBox;

    @FXML
    private Label statusLabel;

    @FXML
    private Label badgeLabel;

    @FXML
    private Label motionBadge;

    @FXML
    private Label recordingBadge;

    @FXML
    private Label resolutionLabel;

    @FXML
    private Label infoLabel;

    @FXML
    private Label fpsLabel;

    @FXML
    private Button streamToggleButton;

    @FXML
    private CheckBox scheduleFilterCheck;

    @FXML
    private Button manualRecordButton;

    @FXML
    private Button settingsButton;

    @FXML
    private Button ptzToggleButton;

    @FXML
    private VBox ptzPanel;

    @FXML
    private Button ptzUpButton;

    @FXML
    private Button ptzDownButton;

    @FXML
    private Button ptzLeftButton;

    @FXML
    private Button ptzRightButton;

    @FXML
    private Button ptzStopButton;

    @FXML
    private Label ptzFeedbackLabel;

    private CameraConnector cameraConnector;
    private MotionDetector motionDetector;
    private ClipRecorder clipRecorder;
    private TelegramNotifier telegramNotifier;
    private EzvizPtzService ptzService;
    private ConfigManager configManager;
    private Thread captureThread;
    private PauseTransition motionAlertTimer;

    private final Object streamLock = new Object();
    private volatile boolean streamActive = true;
    private volatile boolean running = true;
    private volatile MotionDetectionEvent lastMotionEvent = null;
    private volatile String lastTriggerType = "Detección Automática de Movimiento";

    @FXML
    public void initialize() {
        log.info("MainController inicializado");

        // Ajuste reactivo del ImageView al tamaño del contenedor
        cameraView.fitWidthProperty().bind(videoContainer.widthProperty());
        cameraView.fitHeightProperty().bind(videoContainer.heightProperty());

        // Temporizador para apagar la alerta visual de movimiento tras 2 segundos
        motionAlertTimer = new PauseTransition(Duration.seconds(2));
        motionAlertTimer.setOnFinished(e -> {
            motionBadge.setVisible(false);
            motionBadge.setManaged(false);
        });

        configManager = new ConfigManager();
        String rtspUrl = configManager.getRtspUrl();

        if (rtspUrl == null || rtspUrl.isBlank()) {
            log.error("No se encontró 'camera.rtsp.url' en la configuración.");
            statusLabel.setText("Error: camera.rtsp.url no configurada en application.properties");
            badgeLabel.setText("ERROR");
            badgeLabel.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
            return;
        }

        // Configurar texto y estado del filtro de horario según la configuración
        syncScheduleCheckUI();

        // Configurar cliente de notificaciones de Telegram (Fase 4)
        String botToken = configManager.getTelegramBotToken();
        String chatId = configManager.getTelegramChatId();
        telegramNotifier = new TelegramNotifier(botToken, chatId);
        if (telegramNotifier.isConfigured()) {
            log.info("TelegramNotifier configurado y listo.");
        } else {
            log.info("TelegramNotifier no configurado todavía (token o chat_id pendiente en application.properties).");
        }

        // Configurar grabador de clips con pre-roll buffer y destino configurable
        File storageDir = new File(configManager.getClipStorageDir());
        int clipDuration = configManager.getClipDurationSeconds();
        int retentionDays = configManager.getClipRetentionDays();
        int preRollSeconds = configManager.getClipPreRollSeconds();
        clipRecorder = new ClipRecorder(storageDir, clipDuration, retentionDays, preRollSeconds);
        clipRecorder.addListener(clipFile -> {
            log.info("Nuevo clip guardado: {}", clipFile.getAbsolutePath());
            Platform.runLater(() -> {
                recordingBadge.setVisible(false);
                recordingBadge.setManaged(false);
                updateRecordButtonState(false);
            });

            // Enviar clip a Telegram de forma asíncrona si está configurado y corresponde por horario/modo
            if (telegramNotifier != null && telegramNotifier.isConfigured()) {
                if (configManager.shouldNotify(LocalTime.now()) || !scheduleFilterCheck.isSelected()) {
                    String caption = buildTelegramCaption(clipFile, lastTriggerType, lastMotionEvent);
                    telegramNotifier.sendVideoAsync(clipFile, caption);
                } else {
                    log.info("Clip no enviado a Telegram por encontrarse fuera del horario configurado.");
                }
            }
        });

        // Configurar detector de movimiento con parámetros de ConfigManager
        double minArea = configManager.getMotionMinArea();
        long cooldownMillis = configManager.getMotionCooldownSeconds() * 1000L;
        int sampleRate = configManager.getMotionSampleRate();

        motionDetector = new MotionDetector(minArea, cooldownMillis, sampleRate);
        motionDetector.addListener(event -> {
            lastMotionEvent = event;
            lastTriggerType = "Detección Automática de Movimiento";
            log.info("¡Movimiento detectado! Área: {} px, Regiones: {}", event.motionArea(), event.contourCount());
            Platform.runLater(this::triggerMotionVisualAlert);

            // Verificar si aplica restricción por horario
            if (scheduleFilterCheck.isSelected() && !configManager.shouldNotify(LocalTime.now())) {
                log.info("Movimiento detectado fuera de horario permitido ({} a {}). Omitiendo grabación automática.",
                        configManager.getNotificationHourStart(), configManager.getNotificationHourEnd());
                return;
            }

            // Iniciar o extender grabación de clip
            clipRecorder.triggerRecording();
            Platform.runLater(() -> {
                recordingBadge.setVisible(true);
                recordingBadge.setManaged(true);
                updateRecordButtonState(true);
            });
        });

        // Configurar servicio PTZ para cámara EZVIZ C6N
        ptzService = new EzvizPtzService(
                configManager.getPtzCameraHost(),
                configManager.getPtzUsername(),
                configManager.getPtzPassword()
        );
        ptzToggleButton.setVisible(configManager.isPtzEnabled());
        ptzToggleButton.setManaged(configManager.isPtzEnabled());
        setupPtzControls();

        // Mostrar URL enmascarando contraseña
        String maskedUrl = rtspUrl.replaceAll(":[^:@]+@", ":****@");
        infoLabel.setText("Stream: " + maskedUrl);

        // Iniciar hilo de captura en background
        cameraConnector = new CameraConnector(rtspUrl);
        captureThread = new Thread(this::captureLoop, "RTSP-Capture-Thread");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void triggerMotionVisualAlert() {
        motionBadge.setVisible(true);
        motionBadge.setManaged(true);
        motionAlertTimer.playFromStart();
    }

    private void captureLoop() {
        long frameCount = 0;
        long lastFpsUpdate = System.currentTimeMillis();

        while (running) {
            synchronized (streamLock) {
                while (running && !streamActive) {
                    try {
                        streamLock.wait();
                    } catch (InterruptedException ie) {
                        if (!running) return;
                    }
                }
            }
            if (!running) break;

            try {
                Platform.runLater(() -> {
                    placeholderBox.setVisible(true);
                    statusLabel.setText("Conectando con la cámara EZVIZ...");
                    badgeLabel.setText("CONECTANDO");
                    badgeLabel.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                });

                cameraConnector.start();

                final int width = cameraConnector.getImageWidth();
                final int height = cameraConnector.getImageHeight();

                Platform.runLater(() -> {
                    placeholderBox.setVisible(false);
                    badgeLabel.setText("EN VIVO");
                    badgeLabel.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                    resolutionLabel.setText(width + "x" + height);
                    updateStreamButtonState(true);
                });

                while (running && streamActive && cameraConnector.isRunning()) {
                    Frame rawFrame = cameraConnector.grabRawFrame();
                    if (rawFrame != null) {
                        motionDetector.processFrame(rawFrame);
                        clipRecorder.recordFrame(rawFrame);

                        Image image = cameraConnector.convertToFxImage(rawFrame);
                        if (image != null) {
                            frameCount++;
                            long now = System.currentTimeMillis();
                            if (now - lastFpsUpdate >= 1000) {
                                long finalFps = (frameCount * 1000) / (now - lastFpsUpdate);
                                Platform.runLater(() -> fpsLabel.setText(finalFps + " fps"));
                                frameCount = 0;
                                lastFpsUpdate = now;
                            }

                            Platform.runLater(() -> cameraView.setImage(image));
                        }
                    }
                }

                cameraConnector.stop();

            } catch (Exception e) {
                if (!running) {
                    break;
                }
                if (!streamActive) {
                    continue;
                }

                log.warn("Problema en conexión RTSP: {}. Reintentando en 3 segundos...", e.getMessage());
                Platform.runLater(() -> {
                    placeholderBox.setVisible(true);
                    statusLabel.setText("Conexión perdida. Reconectando en 3 segundos...");
                    badgeLabel.setText("RECONECTANDO");
                    badgeLabel.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                });

                cameraConnector.stop();

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (clipRecorder != null) {
            clipRecorder.close();
        }
        if (cameraConnector != null) {
            cameraConnector.stop();
        }
        log.info("Hilo de captura finalizado.");
    }

    @FXML
    private void handleToggleStream() {
        if (streamActive) {
            log.info("Usuario solicitó pausar/detener la transmisión.");
            streamActive = false;
            if (clipRecorder != null && clipRecorder.isRecording()) {
                clipRecorder.stopRecording();
            }
            if (cameraConnector != null) {
                cameraConnector.stop();
            }
            if (motionDetector != null) {
                motionDetector.resetReference();
            }

            Platform.runLater(() -> {
                placeholderBox.setVisible(true);
                statusLabel.setText("Transmisión en vivo detenida.");
                badgeLabel.setText("DETENIDO");
                badgeLabel.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                fpsLabel.setText("0 fps");
                updateStreamButtonState(false);
            });
        } else {
            log.info("Usuario solicitó reanudar/iniciar la transmisión.");
            streamActive = true;
            Platform.runLater(() -> {
                placeholderBox.setVisible(true);
                statusLabel.setText("Conectando con la cámara EZVIZ...");
                badgeLabel.setText("CONECTANDO");
                badgeLabel.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                updateStreamButtonState(true);
            });
            synchronized (streamLock) {
                streamLock.notifyAll();
            }
        }
    }

    private void updateStreamButtonState(boolean isStreaming) {
        Platform.runLater(() -> {
            if (streamToggleButton == null) return;
            if (isStreaming) {
                streamToggleButton.setText("⏸ Detener Stream");
                streamToggleButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: #ffffff; -fx-background-radius: 6; -fx-padding: 4 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
            } else {
                streamToggleButton.setText("▶ Iniciar Stream");
                streamToggleButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: #ffffff; -fx-background-radius: 6; -fx-padding: 4 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
            }
        });
    }

    @FXML
    private void handleManualRecordToggle() {
        if (clipRecorder == null) {
            return;
        }

        if (clipRecorder.isRecording()) {
            log.info("Detención manual de grabación solicitada por usuario.");
            clipRecorder.stopRecording();
            recordingBadge.setVisible(false);
            recordingBadge.setManaged(false);
            updateRecordButtonState(false);
        } else {
            log.info("Inicio manual de grabación solicitado por usuario.");
            lastTriggerType = "Grabación Manual";
            lastMotionEvent = null;
            clipRecorder.triggerRecording();
            recordingBadge.setVisible(true);
            recordingBadge.setManaged(true);
            updateRecordButtonState(true);
        }
    }

    private void updateRecordButtonState(boolean isRecording) {
        Platform.runLater(() -> {
            if (isRecording) {
                manualRecordButton.setText("■ Detener");
                manualRecordButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: #ffffff; -fx-background-radius: 6; -fx-padding: 4 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
            } else {
                manualRecordButton.setText("● Grabar Clip");
                manualRecordButton.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ffffff; -fx-background-radius: 6; -fx-padding: 4 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
            }
        });
    }

    private String buildTelegramCaption(File clipFile, String triggerType, MotionDetectionEvent motionEvent) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        long bytes = clipFile != null ? clipFile.length() : 0;
        String sizeStr = bytes > 1024 * 1024
                ? String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0))
                : String.format(Locale.ROOT, "%d KB", bytes / 1024);

        int width = cameraConnector != null ? cameraConnector.getImageWidth() : 1920;
        int height = cameraConnector != null ? cameraConnector.getImageHeight() : 1080;
        String resStr = (width > 0 && height > 0) ? width + "x" + height : "1080p";

        String modeStr = configManager.isAlwaysNotify()
                ? "Modo Ausente (24/7)"
                : "Modo En Casa (" + configManager.getNotificationHourStart() + " - " + configManager.getNotificationHourEnd() + ")";

        StringBuilder sb = new StringBuilder();
        if ("Grabación Manual".equals(triggerType)) {
            sb.append("⏺️ <b>CLIP GUARDADO — CamaraSec</b>\n");
        } else {
            sb.append("🚨 <b>ALERTA DE SEGURIDAD — CamaraSec</b>\n");
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📹 <b>Cámara:</b> EZVIZ C6N (").append(resStr).append(")\n");
        sb.append("🔔 <b>Evento:</b> ").append(triggerType).append("\n");
        sb.append("📅 <b>Fecha y Hora:</b> ").append(timestamp).append("\n");
        sb.append("🛡️ <b>Modo:</b> ").append(modeStr).append("\n");

        if (motionEvent != null) {
            sb.append("\n📊 <b>Detalles de Movimiento:</b>\n");
            sb.append("  • Área estimada: <b>").append(String.format(Locale.ROOT, "%,.0f px", motionEvent.motionArea())).append("</b>\n");
            sb.append("  • Regiones activas: <b>").append(motionEvent.contourCount()).append("</b>\n");
        }

        sb.append("\n💾 <b>Archivo adjunto:</b>\n");
        sb.append("  • Clip: <code>").append(clipFile != null ? clipFile.getName() : "desconocido").append("</code>\n");
        sb.append("  • Tamaño: <b>").append(sizeStr).append("</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("<i>Vigilancia activa con CamaraSec</i>");

        return sb.toString();
    }

    private void syncScheduleCheckUI() {
        if (configManager.isAlwaysNotify()) {
            scheduleFilterCheck.setSelected(false);
            scheduleFilterCheck.setText("Modo Ausente (24/7)");
        } else {
            scheduleFilterCheck.setSelected(true);
            scheduleFilterCheck.setText("Grabar solo en horario (" +
                    configManager.getNotificationHourStart() + " - " +
                    configManager.getNotificationHourEnd() + ")");
        }
    }

    @FXML
    private void handleOpenSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-view.fxml"));
            Parent root = loader.load();

            SettingsController controller = loader.getController();
            controller.initData(configManager, this::applyConfiguration);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Configuración - CamaraSec");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            if (settingsButton != null && settingsButton.getScene() != null) {
                dialogStage.initOwner(settingsButton.getScene().getWindow());
            }
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);
            dialogStage.showAndWait();
        } catch (Exception e) {
            log.error("Error abriendo ventana de ajustes: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleTogglePtzPanel() {
        if (ptzPanel == null) return;
        boolean isVisible = ptzPanel.isVisible();
        ptzPanel.setVisible(!isVisible);
        ptzPanel.setManaged(!isVisible);
        ptzToggleButton.setStyle(!isVisible
                ? "-fx-background-color: #2980b9; -fx-text-fill: #ffffff; -fx-background-radius: 6; -fx-padding: 4 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;"
                : "-fx-background-color: #2c3e50; -fx-text-fill: #ffffff; -fx-background-radius: 6; -fx-padding: 4 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
    }

    private void setupPtzControls() {
        if (ptzUpButton == null) return;
        setupPtzButton(ptzUpButton, PtzDirection.UP);
        setupPtzButton(ptzDownButton, PtzDirection.DOWN);
        setupPtzButton(ptzLeftButton, PtzDirection.LEFT);
        setupPtzButton(ptzRightButton, PtzDirection.RIGHT);
        ptzStopButton.setOnAction(e -> handlePtzStop());
    }

    private void setupPtzButton(Button btn, PtzDirection direction) {
        btn.setOnMousePressed(e -> {
            if (ptzService != null && ptzService.isConfigured()) {
                ptzFeedbackLabel.setText("Moviendo " + direction.name());
                ptzFeedbackLabel.setStyle("-fx-text-fill: #3498db; -fx-font-size: 10px;");
                ptzService.moveAsync(direction);
            } else {
                ptzFeedbackLabel.setText("PTZ no configurado");
                ptzFeedbackLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 10px;");
            }
        });
        btn.setOnMouseReleased(e -> handlePtzStop());
    }

    private void handlePtzStop() {
        if (ptzService != null && ptzService.isConfigured()) {
            ptzFeedbackLabel.setText("C6N Lista");
            ptzFeedbackLabel.setStyle("-fx-text-fill: #777777; -fx-font-size: 10px;");
            ptzService.stopAsync();
        }
    }

    private void applyConfiguration(AppConfig config) {
        log.info("Aplicando configuración en caliente...");

        // Actualizar detector de movimiento
        if (motionDetector != null) {
            motionDetector.setMinMotionArea(config.getMotionMinArea());
            motionDetector.setCooldownMillis(config.getMotionCooldownSeconds() * 1000L);
            motionDetector.setSampleRate(config.getMotionSampleRate());
            log.info("Parámetros de MotionDetector actualizados: minArea={}, cooldown={}s, sampleRate={}",
                    config.getMotionMinArea(), config.getMotionCooldownSeconds(), config.getMotionSampleRate());
        }

        // Actualizar grabador de clips (duración, retención, carpeta y pre-roll)
        if (clipRecorder != null) {
            clipRecorder.setDurationSeconds(config.getClipDurationSeconds());
            clipRecorder.setRetentionDays(config.getClipRetentionDays());
            clipRecorder.setStorageDir(new File(config.getClipStorageDir()));
            clipRecorder.setPreRollSeconds(config.getPreRollSeconds());
            log.info("Parámetros de ClipRecorder actualizados: duration={}s, retention={}d, dir={}, preRoll={}s",
                    config.getClipDurationSeconds(), config.getClipRetentionDays(), config.getClipStorageDir(), config.getPreRollSeconds());
        }

        // Actualizar TelegramNotifier
        telegramNotifier = new TelegramNotifier(config.getTelegramBotToken(), config.getTelegramChatId());
        log.info("TelegramNotifier actualizado. Configurado: {}", telegramNotifier.isConfigured());

        // Actualizar servicio PTZ
        ptzService = new EzvizPtzService(config.getPtzCameraHost(), config.getPtzUsername(), config.getPtzPassword());
        ptzToggleButton.setVisible(config.isPtzEnabled());
        ptzToggleButton.setManaged(config.isPtzEnabled());
        if (!config.isPtzEnabled()) {
            ptzPanel.setVisible(false);
            ptzPanel.setManaged(false);
        }
        log.info("Servicio PTZ actualizado. Habilitado: {}, Host: {}", config.isPtzEnabled(), config.getPtzCameraHost());

        // Actualizar UI del filtro de horario
        Platform.runLater(this::syncScheduleCheckUI);
    }

    public void shutdown() {
        log.info("Apagando MainController...");
        running = false;
        streamActive = false;
        synchronized (streamLock) {
            streamLock.notifyAll();
        }
        if (captureThread != null) {
            captureThread.interrupt();
        }
        if (clipRecorder != null) {
            clipRecorder.close();
        }
        if (cameraConnector != null) {
            cameraConnector.stop();
        }
    }
}