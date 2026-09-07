# Plan de Implementación — CamaraSec

## Contexto del proyecto

Aplicación Java para monitorear una cámara de seguridad doméstica (marca **EZVIZ**, acceso vía **RTSP local**), con dos modos de operación:

- **App desktop (JavaFX)**: ver el stream en vivo en cualquier momento, configurar horarios de notificación, revisar historial de clips.
- **Servicio headless**: proceso sin UI que corre 24/7, detecta movimiento, graba clips y envía notificaciones a Telegram. Pensado para eventualmente correr en una Raspberry Pi, separado de la PC de escritorio.

**Stack definido:**
- Java 21 (LTS — se evaluó Java 25 pero JavaFX/JavaCV son más estables en 21 por ahora)
- Maven (sin módulos separados todavía — se usan packages, se migra a multi-módulo Maven más adelante si el proyecto crece)
- JavaFX 21.0.2 — UI desktop
- JavaCV 1.5.10 (wrapper de FFmpeg/OpenCV) — conexión RTSP, detección de movimiento, grabación de clips
- Jackson — manejo de configuración en JSON/properties
- SLF4J + Logback — logging (servicio 24/7 sin supervisión necesita logs persistentes)
- Telegram Bot API — notificaciones (vía `java.net.http.HttpClient` nativo, sin librería extra)

**GroupId/ArtifactId:** `org.bdev:camarasec`

---

## Estructura de paquetes objetivo

```
src/main/java/org/bdev/camarasec/
├── MainApp.java                        → entry point JavaFX
├── core/
│   ├── camera/
│   │   ├── CameraConnector.java        → conexión RTSP con JavaCV (FFmpegFrameGrabber)
│   │   └── MotionDetector.java         → detección de movimiento por diff de frames
│   ├── recorder/
│   │   └── ClipRecorder.java           → grabación de clips (FFmpegFrameRecorder)
│   ├── notification/
│   │   └── TelegramNotifier.java       → cliente HTTP a la Bot API de Telegram
│   └── config/
│       ├── AppConfig.java              → modelo de configuración
│       └── ConfigManager.java          → leer/escribir config (JSON o .properties)
├── ui/
│   ├── MainController.java             → controller de la ventana principal (stream en vivo)
│   └── SettingsController.java         → controller del panel de horarios/config
└── headless/
    └── WatcherService.java             → loop 24/7 sin UI (main() alternativo)

src/main/resources/
├── fxml/
│   ├── main-view.fxml
│   └── settings-view.fxml
├── application.properties.example      → plantilla sin credenciales (va al repo)
├── application.properties              → credenciales reales (NO va al repo, en .gitignore)
└── logback.xml
```

---

## Estado actual / bug resuelto

- `MainApp.java` tiraba `InvocationTargetException` al levantar JavaFX.
  - **Causa raíz identificada**: en `main-view.fxml`, el atributo `fx:controller` tenía el paquete duplicado (`org.bdev.camarasec.org.bdev.camarasec.ui.MainController`), causando `ClassNotFoundException`.
  - **Solución aplicada**: corregido a `org.bdev.camarasec.ui.MainController`. Se alinearon también nombres de clases (`CameraConnector` y `WatcherService`).

---

## Fase 0 — Fundación del proyecto (completada)

- [x] Crear proyecto Maven en IntelliJ (`org.bdev:camarasec`)
- [x] Definir `pom.xml` con JavaFX, JavaCV, Jackson, SLF4J/Logback
- [x] Crear estructura de packages (`core`, `ui`, `headless`)
- [x] **Resolver bug de arranque de `MainApp.java`** (ver sección anterior)
- [x] Confirmar que `mvn clean compile javafx:run` levanta una ventana vacía sin errores
- [x] Crear `.gitignore` con `target/`, `logs/`, `.idea/`, `*.iml`, `application.properties`, `config.json`
- [x] Primer commit del esqueleto funcionando

**Criterio de aceptación:** ventana JavaFX abre mostrando "Esperando conexión a la cámara..." sin excepciones en consola. (Verificado)

---

## Fase 1 — Conexión RTSP y visualización en vivo (completada)

**Objetivo:** ver el stream de la cámara EZVIZ dentro de la ventana JavaFX.

- [x] Implementar `CameraConnector` (`core/camera/`):
  - Conectar a la URL RTSP con `FFmpegFrameGrabber`
  - Método `start()` / `stop()` / `grabRawFrame()` / `grabImage()`
  - Manejo de reconexión automática con retry + backoff (la cámara/red puede caerse)
- [x] Agregar `ImageView` al `main-view.fxml`
- [x] En `MainController`, correr la captura en un hilo aparte (`Thread` daemon / background, NUNCA en el JavaFX Application Thread)
- [x] Convertir cada `Frame` de JavaCV a `javafx.scene.image.Image` y actualizar el `ImageView` vía `Platform.runLater(...)`
- [x] Manejar el cierre prolijo del grabber en `stage.setOnCloseRequest(...)`
- [x] Mover la URL RTSP (con usuario/código de verificación EZVIZ) a `application.properties`, leída vía `ConfigManager`

**Criterio de aceptación:** al abrir la app, se ve el video en vivo de la cámara EZVIZ dentro de la ventana, a un frame rate razonable (15-25 fps), sin congelar la UI. (Verificado: stream H.264 1920x1080 activo)

---

## Fase 2 — Detección de movimiento (completada)

**Objetivo:** detectar cambios significativos entre frames.

- [x] Implementar `MotionDetector` (`core/camera/`):
  - Comparar frame actual contra frame de referencia (`absdiff` + threshold + contornos, vía OpenCV)
  - Parámetro configurable de sensibilidad (`motion.min.area` en `application.properties`)
  - Actualizar el frame de referencia periódicamente (running average 95/5 para absorber variaciones de luz sin falsos positivos)
  - Analizar 1 de cada N frames (`motion.sample.rate=3`) para bajar consumo de CPU
- [x] Emitir un evento/callback cuando se detecta movimiento (`MotionListener` y `MotionDetectionEvent`)
- [x] Tests unitarios con imágenes de prueba sintéticas (`MotionDetectorTest` con JUnit 5 — 4/4 passing)
- [x] Mecanismo de cooldown (`motion.cooldown.seconds=5` para no re-disparar detección continua)

**Criterio de aceptación:** mover algo frente a la cámara loguea "Movimiento detectado" en consola/logback, sin dispararse por ruido de video normal. (Verificado con tests y preparado para prueba manual)

---

## Fase 3 — Grabación de clips (completada)

**Objetivo:** al detectar movimiento, grabar un clip de N segundos a disco.

- [x] Implementar `ClipRecorder` (`core/recorder/`):
  - `FFmpegFrameRecorder` apuntando a un `.mp4` con timestamp en el nombre
  - Duración configurable (`clip.duration.seconds=15`)
  - Guardar en carpeta `clips/` (incluida en `.gitignore`)
- [x] Política de retención: borrar clips con más de X días (`clip.retention.days=15`)
- [ ] (Opcional, fase posterior) Subir clips a storage externo (S3/Cloudinary) en vez de solo local

**Criterio de aceptación:** al detectarse movimiento, se genera un `.mp4` reproducible en `clips/` con la duración configurada. (Verificado con ClipRecorderTest)

---

## Fase 4 — Notificaciones a Telegram (completada)

**Objetivo:** avisar por Telegram cuando se detecta movimiento y graba un clip.

- [x] Crear bot con `@BotFather`, guardar token (configurable en `application.properties`)
- [x] Obtener `chat_id` propio vía `getUpdates` (configurable en `application.properties`)
- [x] Implementar `TelegramNotifier` (`core/notification/`):
  - Método `sendMessage(String texto)`
  - Método `sendVideo(File clip, String caption)` (multipart/form-data)
  - Manejo de errores de red (no tumba la app si Telegram falla)
- [x] Guardar token y chat_id en `application.properties` (ignorado en `.gitignore`)
- [x] Conectar: `MotionDetector` → `ClipRecorder` → al terminar el clip → `TelegramNotifier`

**Criterio de aceptación:** mover algo frente a la cámara genera una notificación en Telegram con el video adjunto. (Verificado con mock server y pruebas unitarias TelegramNotifierTest)

---

## Fase 5 — Configuración de horarios desde la UI

**Objetivo:** poder definir desde la app en qué horario se envían notificaciones.

- [x] Modelo `AppConfig` (`core/config/`): rango(s) horario(s) de notificación, sensibilidad de detección, duración de clip, modo "siempre notificar" (ausente/en casa)
- [x] `ConfigManager`: leer/escribir esta config a `application.properties` o `config.json`
- [x] `settings-view.fxml` + `SettingsController`: UI simple para editar horarios (ComboBox/Spinner de hora inicio/fin, toggle de modo)
- [x] Antes de notificar, `WatcherService`/`TelegramNotifier` chequea `LocalTime.now()` contra el rango configurado
- [x] Si está fuera de horario: igual grabar el clip, pero no notificar (o notificar sin prioridad)

**Criterio de aceptación:** cambiar el horario en la UI y guardar, hace que las notificaciones respeten ese rango sin reiniciar la app (o con reinicio simple del watcher). (Verificado con pruebas unitarias ConfigManagerTest y reload en caliente en MainController)

---

## Fase 6 — Separar modo headless

**Objetivo:** poder correr el watcher sin UI, de forma independiente a la app JavaFX.

- [ ] Implementar `WatcherService` (`headless/`) con su propio `main()`:
  - Levanta `CameraConnector` + `MotionDetector` + `ClipRecorder` + `TelegramNotifier`
  - Sin dependencias de JavaFX
  - Loop infinito con manejo de shutdown prolijo (`Runtime.getRuntime().addShutdownHook(...)`)
- [ ] Generar un JAR ejecutable separado para este modo (`maven-shade-plugin` o `maven-assembly-plugin`, con `mainClass` apuntando a `WatcherService`)
- [ ] Documentar cómo correrlo como servicio:
  - Linux/Raspberry Pi: unidad `systemd`
  - Windows: NSSM o Tarea Programada
- [ ] La app JavaFX pasa a ser solo un "visor" opcional — puede leer la misma config y conectarse al RTSP en paralelo para mostrar video en vivo, sin duplicar la lógica de detección/grabación

**Criterio de aceptación:** el JAR headless corre en background sin ventana, detecta movimiento, graba y notifica igual que antes. La app JavaFX se puede abrir/cerrar sin afectar el watcher.

---

## Fase 7 — Robustez y pulido (para portfolio)

- [ ] Reconexión automática robusta si se cae el RTSP o la red
- [ ] Manejo de errores sin crashear el proceso completo (try/catch en cada capa, logs claros)
- [ ] README completo: arquitectura, diagrama simple, cómo correr cada modo, cómo configurar EZVIZ RTSP y el bot de Telegram
- [ ] (Opcional) Historial de eventos en SQLite (fecha, hora, duración, path del clip) — habilita después mostrar esto en el dashboard React existente
- [ ] (Opcional) Endpoint liviano (Javalin, no Spring Boot completo) para exponer el historial de eventos al dashboard
- [ ] (Opcional) Empaquetado nativo con `jpackage`/`jlink` para la app desktop

---

## Notas de seguridad (no negociable, aplica desde la Fase 1)

- Credenciales RTSP y token de Telegram: **nunca** hardcodeados ni en el repo. Siempre vía `application.properties` (gitignored) o variables de entorno.
- `application.properties.example` sin datos reales sí va al repo, como plantilla.
- Si en el futuro se expone algo a internet (endpoint del historial, acceso remoto), autenticación obligatoria.
