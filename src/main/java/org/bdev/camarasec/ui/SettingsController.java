package org.bdev.camarasec.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.bdev.camarasec.core.config.AppConfig;
import org.bdev.camarasec.core.config.ConfigManager;
import org.bdev.camarasec.core.notification.TelegramNotifier;
import org.bdev.camarasec.core.ptz.EzvizPtzService;
import org.bdev.camarasec.core.ptz.PtzDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private ToggleGroup modeToggleGroup;

    @FXML
    private RadioButton homeModeRadio;

    @FXML
    private RadioButton awayModeRadio;

    @FXML
    private ComboBox<String> hourStartCombo;

    @FXML
    private ComboBox<String> hourEndCombo;

    @FXML
    private Spinner<Double> minAreaSpinner;

    @FXML
    private Spinner<Integer> cooldownSpinner;

    @FXML
    private Spinner<Integer> sampleRateSpinner;

    @FXML
    private Spinner<Integer> clipDurationSpinner;

    @FXML
    private Spinner<Integer> clipRetentionSpinner;

    @FXML
    private TextField storageDirField;

    @FXML
    private Spinner<Integer> preRollSpinner;

    @FXML
    private CheckBox ptzEnabledCheck;

    @FXML
    private TextField ptzHostField;

    @FXML
    private TextField ptzUsernameField;

    @FXML
    private PasswordField ptzPasswordField;

    @FXML
    private Button testPtzButton;

    @FXML
    private Label ptzStatusLabel;

    @FXML
    private TextField telegramTokenField;

    @FXML
    private TextField telegramChatIdField;

    @FXML
    private Button testTelegramButton;

    @FXML
    private Label telegramStatusLabel;

    @FXML
    private TextField rtspUrlField;

    @FXML
    private Label statusLabel;

    private ConfigManager configManager;
    private Consumer<AppConfig> onSaveCallback;

    @FXML
    public void initialize() {
        // Inicializar listas de horas predefinidas (cada 30 min)
        List<String> times = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            times.add(String.format("%02d:00", h));
            times.add(String.format("%02d:30", h));
        }
        hourStartCombo.setItems(FXCollections.observableArrayList(times));
        hourEndCombo.setItems(FXCollections.observableArrayList(times));

        // Inicializar fábricas de Spinners
        minAreaSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(50.0, 10000.0, 500.0, 50.0));
        cooldownSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 120, 5, 1));
        sampleRateSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3, 1));
        clipDurationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 120, 15, 1));
        clipRetentionSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 15, 1));
        preRollSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10, 3, 1));

        // Deshabilitar combos de hora si se selecciona Modo Ausente (24/7)
        awayModeRadio.selectedProperty().addListener((obs, wasAway, isAway) -> {
            hourStartCombo.setDisable(isAway);
            hourEndCombo.setDisable(isAway);
        });
    }

    public void initData(ConfigManager configManager, Consumer<AppConfig> onSaveCallback) {
        this.configManager = configManager;
        this.onSaveCallback = onSaveCallback;

        AppConfig config = configManager.toAppConfig();

        if (config.isAlwaysNotify()) {
            awayModeRadio.setSelected(true);
            hourStartCombo.setDisable(true);
            hourEndCombo.setDisable(true);
        } else {
            homeModeRadio.setSelected(true);
            hourStartCombo.setDisable(false);
            hourEndCombo.setDisable(false);
        }

        hourStartCombo.setValue(config.getNotificationHourStart());
        hourEndCombo.setValue(config.getNotificationHourEnd());

        minAreaSpinner.getValueFactory().setValue(config.getMotionMinArea());
        cooldownSpinner.getValueFactory().setValue((int) config.getMotionCooldownSeconds());
        sampleRateSpinner.getValueFactory().setValue(config.getMotionSampleRate());
        clipDurationSpinner.getValueFactory().setValue(config.getClipDurationSeconds());
        clipRetentionSpinner.getValueFactory().setValue(config.getClipRetentionDays());

        storageDirField.setText(config.getClipStorageDir() != null ? config.getClipStorageDir() : "clips");
        preRollSpinner.getValueFactory().setValue(config.getPreRollSeconds());

        ptzEnabledCheck.setSelected(config.isPtzEnabled());
        ptzHostField.setText(config.getPtzCameraHost() != null ? config.getPtzCameraHost() : "");
        ptzUsernameField.setText(config.getPtzUsername() != null ? config.getPtzUsername() : "admin");
        ptzPasswordField.setText(config.getPtzPassword() != null ? config.getPtzPassword() : "");

        telegramTokenField.setText(config.getTelegramBotToken() != null ? config.getTelegramBotToken() : "");
        telegramChatIdField.setText(config.getTelegramChatId() != null ? config.getTelegramChatId() : "");
        rtspUrlField.setText(config.getRtspUrl() != null ? config.getRtspUrl() : "");
    }

    @FXML
    private void handleBrowseStorage() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Seleccionar carpeta para guardar clips");
        String currentPath = storageDirField.getText() != null ? storageDirField.getText().trim() : "";
        File current = new File(currentPath.isEmpty() ? "clips" : currentPath);
        if (current.exists() && current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        File selected = chooser.showDialog(statusLabel.getScene().getWindow());
        if (selected != null) {
            storageDirField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void handleTestPtz() {
        String host = ptzHostField.getText().trim();
        if (host.isBlank()) {
            host = configManager.getPtzCameraHost();
        }
        String user = ptzUsernameField.getText().trim();
        if (user.isBlank()) {
            user = configManager.getPtzUsername();
        }
        String pass = ptzPasswordField.getText().trim();
        if (pass.isBlank()) {
            pass = configManager.getPtzPassword();
        }

        if (host.isBlank()) {
            ptzStatusLabel.setText("Host no especificado ni en RTSP.");
            ptzStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        testPtzButton.setDisable(true);
        ptzStatusLabel.setText("Probando conexión PTZ...");
        ptzStatusLabel.setStyle("-fx-text-fill: #f39c12;");

        EzvizPtzService service = new EzvizPtzService(host, user, pass);
        service.stopAsync()
                .thenAccept(success -> Platform.runLater(() -> {
                    testPtzButton.setDisable(false);
                    if (success) {
                        ptzStatusLabel.setText("✓ Conexión PTZ exitosa con cámara C6N.");
                        ptzStatusLabel.setStyle("-fx-text-fill: #2ecc71;");
                    } else {
                        ptzStatusLabel.setText("✗ Falló respuesta PTZ. Revisa IP/clave o LAN Live View.");
                        ptzStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        testPtzButton.setDisable(false);
                        ptzStatusLabel.setText("✗ Error de red: " + ex.getMessage());
                        ptzStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    });
                    return null;
                });
    }

    @FXML
    private void handleTestTelegram() {
        String token = telegramTokenField.getText().trim();
        String chatId = telegramChatIdField.getText().trim();

        if (token.isBlank() || chatId.isBlank() || token.contains("TU_BOT_TOKEN") || chatId.contains("TU_CHAT_ID")) {
            telegramStatusLabel.setText("Token o Chat ID inválidos");
            telegramStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        testTelegramButton.setDisable(true);
        telegramStatusLabel.setText("Enviando mensaje...");
        telegramStatusLabel.setStyle("-fx-text-fill: #f39c12;");

        TelegramNotifier notifier = new TelegramNotifier(token, chatId);
        notifier.sendMessageAsync("🔔 <b>CamaraSec</b>: Conexión de prueba exitosa desde la configuración.")
                .thenAccept(success -> Platform.runLater(() -> {
                    testTelegramButton.setDisable(false);
                    if (success) {
                        telegramStatusLabel.setText("✓ Conexión exitosa. Mensaje enviado.");
                        telegramStatusLabel.setStyle("-fx-text-fill: #2ecc71;");
                    } else {
                        telegramStatusLabel.setText("✗ Falló el envío. Revisa token/chat ID.");
                        telegramStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        testTelegramButton.setDisable(false);
                        telegramStatusLabel.setText("✗ Error de red: " + ex.getMessage());
                        telegramStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    });
                    return null;
                });
    }

    @FXML
    private void handleSave() {
        // Validar formato de horas
        String startStr = hourStartCombo.getValue();
        String endStr = hourEndCombo.getValue();

        try {
            LocalTime.parse(startStr, TIME_FORMAT);
            LocalTime.parse(endStr, TIME_FORMAT);
        } catch (Exception e) {
            statusLabel.setText("Formato de hora inválido (use HH:mm).");
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        AppConfig config = new AppConfig();
        config.setAlwaysNotify(awayModeRadio.isSelected());
        config.setNotificationHourStart(startStr);
        config.setNotificationHourEnd(endStr);
        config.setMotionMinArea(minAreaSpinner.getValue());
        config.setMotionCooldownSeconds(cooldownSpinner.getValue());
        config.setMotionSampleRate(sampleRateSpinner.getValue());
        config.setClipDurationSeconds(clipDurationSpinner.getValue());
        config.setClipRetentionDays(clipRetentionSpinner.getValue());
        config.setClipStorageDir(storageDirField.getText().trim().isEmpty() ? "clips" : storageDirField.getText().trim());
        config.setPreRollSeconds(preRollSpinner.getValue());
        config.setPtzEnabled(ptzEnabledCheck.isSelected());
        config.setPtzCameraHost(ptzHostField.getText().trim());
        config.setPtzUsername(ptzUsernameField.getText().trim());
        config.setPtzPassword(ptzPasswordField.getText().trim());
        config.setTelegramBotToken(telegramTokenField.getText().trim());
        config.setTelegramChatId(telegramChatIdField.getText().trim());
        config.setRtspUrl(rtspUrlField.getText().trim());

        try {
            configManager.saveConfig(config);
            log.info("Configuración guardada satisfactoriamente.");

            if (onSaveCallback != null) {
                onSaveCallback.accept(config);
            }

            closeWindow();
        } catch (Exception e) {
            log.error("Error al persistir configuración: {}", e.getMessage(), e);
            statusLabel.setText("Error al guardar: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }
}
