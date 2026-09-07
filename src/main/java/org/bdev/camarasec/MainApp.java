package org.bdev.camarasec;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.bdev.camarasec.ui.MainController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage stage) throws IOException {
        log.info("Iniciando CamaraSec...");

        FXMLLoader fxmlLoader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource("/fxml/main-view.fxml"))
        );
        Parent root = fxmlLoader.load();

        MainController controller = fxmlLoader.getController();

        Scene scene = new Scene(root, 960, 580);

        stage.setTitle("CamaraSec - Monitor");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            log.info("Cerrando aplicación...");
            if (controller != null) {
                controller.shutdown();
            }
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
