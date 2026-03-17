This is a Kotlin Multiplatform project targeting Android, iOS.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

### Android On-Device LLM (llama.cpp)

Fuer die lokale Formular-Automatisierung auf Android ist eine llama.cpp-Anbindung vorhanden.
Damit sie aktiv wird, muss ein lokales GGUF-Modell bereitliegen und in `local.properties`
konfiguriert werden:

```properties
openai.api.key=YOUR_OPENAI_KEY
llama.model.path=/absolute/path/to/your/model.gguf
```

Hinweise:
- Das Modell muss unter diesem Pfad existieren und lesbar sein.
- Der On-Device-Modus faellt automatisch auf Keyword-Mapping zurueck, falls kein Modellpfad gesetzt ist oder lokale Inferenz fehlschlaegt.
- Fuer mobile Geraete empfiehlt sich ein kleines quantisiertes Instruct-Modell im GGUF-Format.

### iOS On-Device LLM (llama.cpp)

Die iOS-Variante nutzt denselben On-Device-Mapping-Flow wie Android und bindet die lokale
Inferenz ueber eine Swift-Bridge (`iosApp/iosApp/IOSLlamaBridge.swift`) an.

Konfiguration in `iosApp/Configuration/Config.xcconfig` (oder lokal in `Secrets.xcconfig`):

```xcconfig
OPENAI_API_KEY=
LLAMA_MODEL_PATH=/absolute/path/to/your/model.gguf
WHISPER_MODEL_PATH=
```

Wichtig:
- Das iOS-Target benoetigt ein verlinktes `llama`-Modul (z. B. aus einer `llama.xcframework`),
  damit echte On-Device-Inferenz laeuft.
- Ist das Modul nicht verlinkt, bleibt die App lauffaehig und nutzt weiterhin den lokalen
  Keyword-/Heuristik-Fallback fuer die Formularbefuellung.

### iOS On-Device Transkription (Whisper)

Die iOS-Transkription ist nun ebenfalls auf eine lokale Bridge vorbereitet
(`iosApp/iosApp/IOSWhisperBridge.swift`) und wird beim App-Start registriert,
wenn ein Whisper-Modell gefunden wird.

Konfiguration:
- `WHISPER_MODEL_PATH` in `iosApp/Configuration/Config.xcconfig` oder `Secrets.xcconfig`
- Alternativ Fallback-Suche auf iOS in dieser Reihenfolge:
  1. `WHISPER_MODEL_PATH` (Info.plist)
  2. `whisper-base.bin` im App-Bundle
  3. erste Bundle-`.bin`, die "whisper" im Namen traegt
  4. `Documents/models/whisper-base.bin`

Wichtig:
- Fuer echte lokale Inferenz muss ein `whisper`-Modul im iOS-Target verlinkt sein.
- Ohne verlinktes Modul registriert sich die Bridge nicht; Transkription bleibt dann kontrolliert im Cloud-Pfad.

