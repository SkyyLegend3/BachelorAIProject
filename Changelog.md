# Changelog

## Stand: 2026-03-23

## Bugfix: AudioFilePicker final stabil auf Android + iOS
- iOS On-Device UI-Log erweitert: Im Prozess-Log wird jetzt explizit ausgewiesen, ob On-Device-LMMapping die LLM-Antwort genutzt hat oder auf Fallback/Heuristik lief.
  - Neue Logzeilen in `FormViewModel`: `On-Device: LLM genutzt`, `On-Device: LLM + Fallback/Heuristik genutzt`, `On-Device: Fallback/Heuristik genutzt`.
- iOS On-Device Anti-Halluzinations-Fix im Formular-Mapping:
  - `OnDeviceLlmFormMappingRepository.ios.kt` filtert LLM-Antworten jetzt strikt gegen Transkript-Evidenz.
  - Antworten werden nur uebernommen, wenn sie als Phrase im Transkript vorkommen oder alle inhaltstragenden Tokens im Transkript belegbar sind.
  - Nicht belegbare LLM-Inhalte werden verworfen und fallen auf Heuristik/Fallback zurueck.
  - Ziel: Keine erfundenen Learnings/Antworten mehr bei iOS On-Device-LMMapping.
- iOS Crashfix (llama): `llama_batch_add` in `iosApp/iosApp/LibLlama.swift` gehaertet.
  - Entferntes Force-Unwrap auf `batch.seq_id[...]!` durch Guard-Checks.
  - Defensiver Schutz gegen nil-Pointer in `token/pos/n_seq_id/seq_id/logits`.
  - Batch-Overflow-Schutz eingebaut und Batch-Kapazitaet auf `2048` angehoben (statt `512`).
  - Ziel: `Fatal error: Unexpectedly found nil while unwrapping an Optional value` bei iOS-Inferenz vermeiden.
- iOS-Dateipicker wurde aus fehleranfälliger Swift/Kotlin-Bridge entfernt und direkt in `composeApp/src/iosMain/kotlin/com/example/bachelor_ai_project/features/recording/ui/RequestAudioFilePickerImpl.ios.kt` implementiert.
- iOS nutzt jetzt nativen `UIDocumentPickerViewController` über Kotlin/Native (`forOpeningContentTypes = listOf(UTTypeAudio)`), inklusive Cancel-/Fehlerbehandlung und Rückgabe der gewählten Datei-URL.
- Defekte iOS-Bridge-Experimente wurden bereinigt; `iosApp/iosApp/IOSAudioFilePickerBridge.swift` ist nun ein harmloser no-op Stub.
- `iosApp/iosApp/iOSApp.swift` registriert keinen separaten Audio-Picker-Bridge-Call mehr.
- Alte Registry-Reste für den verworfenen Bridge-Ansatz wurden entfernt:
  - `composeApp/src/commonMain/kotlin/com/example/bachelor_ai_project/core/util/IosAudioFilePickerRegistry.kt`
  - `composeApp/src/iosMain/kotlin/com/example/bachelor_ai_project/core/util/IosAudioFilePickerRegistry.ios.kt`
  - `composeApp/src/androidMain/kotlin/com/example/bachelor_ai_project/core/util/IosAudioFilePickerRegistry.android.kt`
- `composeApp/src/iosMain/kotlin/com/example/bachelor_ai_project/core/util/AudioFilePicker.ios.kt` wurde als klarer Fallback ohne Registry-Abhängigkeit bereinigt.
- Android-Picker bleibt Compose-basiert über `ActivityResultContracts.OpenDocument()` in `composeApp/src/androidMain/kotlin/com/example/bachelor_ai_project/features/recording/ui/RequestAudioFilePickerImpl.android.kt`.
- Android Cloud-Transkription nach Dateiupload gefixt: `readFileBytes(...)` unter Android unterstützt jetzt `content://`-URIs aus dem Picker via `ContentResolver` (statt nur `File(path)`).
  - Datei: `composeApp/src/androidMain/kotlin/com/example/bachelor_ai_project/features/transcription/domain/FileReader.android.kt`
  - Behebt Fehler wie `open failed: ENOENT` bei Pfaden im Format `content://...`.
- Android Cloud `invalid file format` nach Dateiupload gefixt:
  - Dateipicker kopiert die gewählte Datei jetzt in eine lokale Cache-Datei und gibt einen echten Dateipfad zurück (`RequestAudioFilePickerImpl.android.kt`).
  - OpenAI-Multipart nutzt nun dynamischen Dateinamen + abgeleiteten MIME-Type statt statischem `recording.m4a`/`audio/mp4` (`OpenAiTranscriptionRepository.kt`).
  - `Content-Disposition` des Datei-Parts wurde auf valides `form-data; name="file"; filename="..."` korrigiert (vorher fehleranfällig nur `filename="..."`).
  - Upload-Dateiendung wird jetzt zusätzlich aus Magic-Bytes erkannt (z. B. WAV/OGG/MP4), damit Name/MIME konsistent zum echten Dateicontent sind.
  - Multipart-Header wurden vereinfacht (Ktor setzt `name` selbst), um doppelte/inkonsistente `Content-Disposition`-Parameter zu vermeiden.
  - Diagnose erweitert: Upload-Log enthält jetzt Dateiname, Content-Type, Byte-Länge, Magic-Bytes (hex/ascii) und MP4-`ftyp`-Brand zur schnellen Analyse von Formatfehlern.
  - Zusätzlicher Android-Fix: Wenn der Upload `ftyp=3gp4` hat, wird die Audiospur beim Dateiauswählen in einen MP4/M4A-Container remuxt, bevor sie an OpenAI gesendet wird (`RequestAudioFilePickerImpl.android.kt`).
- Upload-Confirm-Flow korrigiert: Der Haken-Button stößt jetzt tatsächlich die Transkription an (`confirmUploadedFileSelection()`), statt nur erneut den Dateipfad zu setzen.
- Recording-UI angepasst (Android + iOS / commonMain):
  - `Datei hochladen`, `X` und `✓` sind bei gewählter Datei jetzt in **einer gemeinsamen Zeile**.
  - `X` und `✓` sind als normale Buttons im selben Stil dargestellt, mit **weißem Text auf lila Hintergrund**.
  - Der ausgewählte Dateiname wird unterhalb der Button-Reihe angezeigt.
- Neues Hilfsskript für Android-Modell-Setup nach App-Neuinstallation:
  - `scripts/push_android_models.sh`
  - Pusht lokales Whisper + Llama per `adb` in die App-Sandbox (`run-as`) und nutzt bei Bedarf Werte aus `local.properties` (`llama.model.path`, `whisper.model.path`).
  - Ergänzt: Quellpfade können jetzt ebenfalls aus `local.properties` kommen (`llama.model.push.src`, `whisper.model.push.src`), mit Priorität `CLI > local.properties > Fallback`.
  - Fix: robuster `run-as`-Kopierschritt mit expliziten Zielverzeichnissen (`mkdir -p` je Zielpfad) und Debug-Ausgabe der aufgelösten Zielpfade.
  - Fix: temporäre Push-Dateien liegen nun unter `/data/local/tmp/.bachelor_models` statt `/sdcard`, damit Zugriffsprobleme beim `run-as`-Copy reduziert werden.
- Neues Hilfsskript für iOS-Simulator-Modell-Setup nach App-Neuinstallation:
  - `scripts/push_ios_models.sh`
  - Kopiert lokales Whisper + Llama via `xcrun simctl get_app_container ... data` nach `Documents/models` im Simulator-Container.
  - Unterstützt `local.properties`-Quellpfade (`llama.model.push.src`, `whisper.model.push.src`) sowie CLI-Overrides.
- `AppViewModel` verdrahtet Datei-Upload jetzt explizit auf `transcriptionViewModel.transcribe(filePath)`.
- iOS-On-Device-LLM-Statusprüfung erweitert: Neben `LLAMA_MODEL_PATH` werden jetzt auch `Documents/models/model.gguf`, ein konfigurierter Dateiname in `Documents/models/` sowie die erste gefundene `*.gguf` in `Documents/models` erkannt.
- iOS-Bridge (`IOSLlamaBridge.swift`) sucht ebenfalls in `Documents/models` nach beliebiger `*.gguf`, falls `model.gguf` nicht vorhanden ist.
- iOS-Dateiupload/Transkription gefixt: Upload-Pfade werden nun robust als lokale Pfade verarbeitet (statt `file://...` URI-String mit `%20`), damit lokale Whisper-Transkription die Datei findet.
  - `RequestAudioFilePickerImpl.ios.kt` gibt bevorzugt `NSURL.path` weiter.
  - `IOSWhisperBridge.swift` normalisiert eingehende Pfade (`file://`, percent-encoding) vor `fileExists` und Transkription.
- iOS-Simulator-Crash beim Transkribieren behoben: Metal Residency Sets werden im Simulator deaktiviert, um `MTLDebugDevice newResidencySetWithDescriptor`-Assertion (`device does not support residency sets`) zu vermeiden.
  - `iosApp/llama.cpp/ggml/src/ggml-metal/ggml-metal-device.m`
  - `whisper.cpp/ggml/src/ggml-metal/ggml-metal-device.m`
- Zusätzliche Härtung: Residency Sets werden im Simulator nun schon bei der Device-Initialisierung deaktiviert (`use_residency_sets = false`), damit auch die Residency-Collection gar nicht erst erstellt wird.
- Verifiziert mit Builds:
  - `:composeApp:compileDebugKotlinAndroid` ✅
  - `:composeApp:compileKotlinIosSimulatorArm64` ✅

## Stand: 2026-03-20

## Android: Direct-Llama Prefill-Timeout gefixt
- `ai_chat.cpp` (`DirectLlamaBridge.nativeInfer`) nutzt fuer Prefill jetzt kleine Decode-Chunks statt eines grossen Blocks, damit Timeout/Cancel waehrend Prefill regelmaessig greifen.
- Timeout-Pruefung erfolgt nun vor und nach jedem Prefill-Decode-Chunk; dadurch endet ein haengender Prefill kontrolliert mit `__DIRECT_LLM_ERROR__: timeout`.
- Direct-Load (`nativeLoadModel`) setzt `n_batch/n_ubatch` fuer Mobile konservativer (max. 128 statt 512), um Compute-/Reserve-Last fuer On-Device-Mapping zu senken.

## Android: Direct-Prefill weiter auf Single-Token-Schritte getunt
- `DIRECT_PREFILL_CHUNK` auf `1` gesenkt, damit ein einzelner Prefill-Decode auf langsamen Geraeten nicht mehr mehrere Sekunden am Stueck blockiert.
- `DIRECT_MOBILE_BATCH_MAX` auf `32` reduziert und `llama_batch_init(...)` im Direct-Path auf die mobile Batch-Groesse ausgerichtet (statt fester 512), um Graph-/Compute-Overhead weiter zu druecken.

## Android: Direct-JNI Decode-Pfad beschleunigt
- `ai_chat.cpp` (Direct-Llama-Pfad) wurde im Hotpath optimiert: keine `llama_batch_init/free`-Allokation mehr pro Prefill-Chunk oder pro generiertem Token.
- Prefill und Generation nutzen jetzt den wiederverwendbaren globalen Batch (`g_batch`), wodurch JNI-/Allocator-Overhead in der Inferenz deutlich reduziert wird.
- Runtime-Threadzahl wird beim Direct-Load wieder aus `threads.min/max` + CPU-Anzahl abgeleitet (statt fest auf `2`), inklusive konsistenter `n_batch/n_ubatch`-Konfiguration.
- Sehr chatty Schritt-fuer-Schritt-Logs im Inferenzloop reduziert, um Logcat-I/O als Laufzeitbremse zu vermeiden.

## Android: Typbereinigung im On-Device-LMMapping
- `OnDeviceLlmFormMappingRepository.android.kt` nutzt in den drei relevanten Listen/Signaturen jetzt den korrekten Domain-Typ `FormQuestion` statt `Any`.
- Entfernt wurden die unsicheren Casts (`UNCHECKED_CAST`) in `buildQuestionGroups`, `buildCompactPrompt` und `buildFocusedTranscript` sowie die provisorische `typealias dynamicQuestion = Any`.
- Ergebnis: klarere Typen im Mapping-Flow und weniger Cast-Risiko bei Refactorings.

## Android: Neue On-Device-Strategie mit Direct llama.cpp JNI
- Neuer direkter JNI-Wrapper `com.arm.aichat.direct.DirectLlamaBridge` eingefuehrt (ohne Flow-basiertes `sendUserPrompt`), inkl. nativer Methoden fuer Init, Load, Infer, Cancel, Unload und Shutdown.
- Native `ai_chat.cpp` um Direct-Entry-Points erweitert: Inferenz laeuft als zusammenhaengender nativer Call mit internem Timeout-/Cancel-Check und klaren Fehlerpraefixen.
- Neuer Engine-Adapter `DirectLlamaOnDeviceLlmEngine` in `composeApp` hinzugefuegt: einmaliges Modellladen, app-interner Modellpfad, Recovery nach Fehlern und Diagnose-Logs.
- `OnDeviceLlmEngineProvider.android.kt` priorisiert jetzt den Direct-JNI-Pfad und faellt nur bei Initialisierungsfehlern auf den bisherigen AiChat-Adapter zurueck.

## Android: On-Device-Llama gegen Timeout gehaertet
- `LlamaCppOnDeviceLlmEngine.completeJson()` auf den vereinbarten Ablauf umgestellt (Model-Check, System-Prompt, Token-Streaming mit Timing-Logs), damit der Inferenzpfad klarer diagnostizierbar bleibt.
- Modellpfad fuer Native-Zugriff gehaertet: liegt der konfigurierte Pfad nicht im app-internen Speicher, wird das GGUF vor dem Laden in `files/models/` kopiert und von dort geladen.
- Timeout-Recovery erweitert: nach `Future`-Timeout wird die Engine explizit bereinigt (`cleanUp`/`destroy`), intern verworfen und beim naechsten Lauf neu erstellt.
- `InferenceEngineImpl.destroy()` setzt jetzt die Singleton-Instanz zurueck, damit nach einem harten Timeout wirklich eine frische Engine instanziiert werden kann.

## Android: Timeout-Wrapper auf Coroutines vereinfacht
- `LlamaCppOnDeviceLlmEngine.runEngineCallWithTimeout()` nutzt jetzt `withContext(Dispatchers.Default)` + `withTimeout(...)` statt Executor/Future-Wrapper.
- `modelLoaded` wird erst gesetzt, wenn der Engine-Status nach dem Ladevorgang tatsaechlich `isModelLoaded == true` ist; bei nicht-bereitem Zustand wird mit klarer Fehlermeldung abgebrochen.

## Android: On-Device LLM-Testbutton im Formular
- Unter den Formularfeldern wird im Modus `On Device` (nur wenn On-Device-Mapping verfuegbar ist) jetzt ein Button `LLM Test` angezeigt.
- Der Test fuehrt eine lokale LLM-Testanfrage aus und zeigt danach direkt unter dem Button das Ergebnis `LLM funktioniert` oder `LLM fail` an.
- Der Ablauf ist an das On-Device-Status-Interface angebunden (`runOnDeviceLlmSelfTest`) und wird im Prozess-Log dokumentiert (`LLM-Test gestartet/erfolgreich/fehlgeschlagen`).

## Stand: 2026-03-19

## Android: Fix fuer `UnsatisfiedLinkError` bei On-Device-LLM
- `llamaAndroidLib` nutzt nicht mehr die alten Backup-Artefakte (`classes.jar` + `jniLibs`) aus `_restore_backup/...`, sondern wieder konsistent die aktuellen Quellen aus dem Modul selbst.
- Dadurch kommen Java-Methoden und JNI-Symbole aus demselben Buildstand; der Fehler `No implementation found ... configureGeneration(int,float,int,int)` wird damit behoben.
- Packaging-Bereinigung: `libai-chat.so`-`pickFirst` entfernt, damit keine veraltete Native-Binary die aktuelle Implementierung ueberschreibt.

## Android + iOS: Prozess-Log zeigt Mapping-Quelle
- `TranscriptMappingResult` um `mappingStrategy` erweitert, damit die Pipeline die tatsaechlich genutzte Quelle transportiert (`Cloud LLM`, `On-Device LLM`, `Mixed`, `Heuristik/Fallback`).
- Repositories markieren ihre Ergebnisse nun explizit mit der jeweiligen Strategie (Cloud-LLM, On-Device-LLM, Keyword/Heuristik-Fallback).
- `FormViewModel` schreibt die Mapping-Quelle in den vorhandenen UI-Prozess-Log, sodass direkt sichtbar ist, ob LLM-Mapping oder Heuristik/Fallback verwendet wurde.

## Android + iOS: LLM-Prioritaet und roter Fallback-Hinweis
- On-Device-Merge leitet die Mapping-Quelle jetzt aus den tatsaechlich verwendeten Feldkandidaten ab (LLM bleibt erste Prioritaet pro Feld).
- Wenn On-Device nicht mit LLM mappen kann, wird der konkrete Grund (`Engine fehlt`, `LLM-Aufruf fehlgeschlagen`, `ungueltige Antwort`) als Diagnose mitgegeben.
- Die Formular-UI zeigt diesen Grund im Prozessbereich als rote Meldung an, auch wenn Heuristik/Fallback erfolgreich Felder befuellt.

## Android + iOS: Sichtbarer LLM-Modellstatus im Formular
- Unter der Modusauswahl wird bei aktivem `On Device` ein sauberer Status-Indikator angezeigt: gruener Kreis mit `LLM-Model bereit` oder roter Kreis mit `LLM-Model nicht gefunden`.
- Der Status basiert auf dem konfigurierten `LLAMA_MODEL_PATH` und einer plattformspezifischen Dateiexistenzpruefung (Android + iOS), der Text wird wie gewuenscht schwarz dargestellt.

## Android + iOS: KMP-Fix fuer FormViewModel-Zeitmessung
- `FormViewModel` nutzt fuer Mapping-Timing jetzt `TimeSource.Monotonic` statt JVM-spezifischer Aufrufe (`System.currentTimeMillis`, `Thread.currentThread`).
- Prozess-Log `Mapping-Start` ist dadurch plattformneutral und kompiliert wieder auf iOS im `commonMain`-Pfad.
- Verifiziert mit `:composeApp:compileKotlinIosSimulatorArm64` und `:composeApp:compileDebugKotlinAndroid` (beide erfolgreich).

## Android + iOS: Einmaliges LLM-Modellladen + Ladezustand in UI
- Android-LLM-Engine laedt das GGUF-Modell jetzt einmalig und nutzt es danach wieder, statt bei jedem Mappinglauf neu zu laden.
- On-Device-Status erweitert: konfiguriert/ready/loading inkl. Warmup-Call beim Umschalten auf On-Device.
- UI unter der On-Device-Auswahl zeigt jetzt zusaetzlich den Zustand `Model wird geladen` waehrend des initialen Ladens.

## Android: UI-Freeze beim LLM-Warmup behoben
- Schweres On-Device-Warmup (Modell laden) laeuft jetzt explizit auf `Dispatchers.Default` statt im Main-Thread-Kontext des `viewModelScope`.
- `LlamaCppOnDeviceLlmEngine.warmupModel()` sichert den Dispatcher-Wechsel zusaetzlich direkt in der Engine ab.
- Ziel: kein Einfrieren der UI waehrend `Model wird geladen` bzw. `Transkript wird verarbeitet...`.

## Android: On-Device-Inferenz gegen Hänger gehaertet
- `LlamaCppOnDeviceLlmEngine` nutzt jetzt ein explizites Inferenz-Timeout (`60s`), damit ein blockierender Token-Stream nicht endlos laeuft.
- Token-Limit fuer Formular-Mapping auf `256` reduziert, um Laufzeit und Last auf mobilen Geraeten zu senken.
- Zusaetzliches Diagnose-Logging fuer `first token`, `tokenCount` und Fehlzeitpunkt eingebaut, damit Freeze/Ursachen in Logcat klarer sichtbar sind.
- Warmup und Inferenz werden zusaetzlich in einem dedizierten Worker-Thread mit `Future.get(timeout)` ausgefuehrt, damit auch nicht-kooperativ blockierende Native-Calls die Mapping-Pipeline nicht unbegrenzt festhalten.

## Android: Konfigurierbarer LLM-Performance-Mode
- `composeApp/build.gradle.kts` liest jetzt zusaetzliche lokale LLM-Performance-Parameter aus `local.properties`: `llama.performance.mode`, `llama.predict.length`, `llama.inference.timeout.ms`.
- Die Werte werden als `BuildConfig`-Felder in Android bereitgestellt und beim Erzeugen der On-Device-LLM-Engine angewendet.
- Ziel: schnellere/stabilere Formular-Mappings auf mobilen Geraeten durch kleineres Token-Budget und anpassbares Timeout, ohne Codeaenderung pro Testlauf.

## Android: Runtime-Tuning fuer n_ctx, temperature und Threads
- On-Device-Llama uebergibt jetzt zusaetzlich `n_ctx`, `temperature` und Thread-Range aus `BuildConfig` an die JNI-Schicht vor dem Modellladen.
- Native `ai_chat.cpp` nutzt diese Laufzeitwerte fuer Context-Initialisierung (`ctx_params.n_ctx`), Sampler-Temperatur und die Thread-Berechnung (konfigurierter Min/Max-Bereich).
- Neue lokale Parameter in `local.properties`: `llama.n.ctx`, `llama.temperature`, `llama.threads.min`, `llama.threads.max`.

## Android: Buildfix nach korrupten Kotlin-Datei-Headern
- `FormViewModel.kt` bereinigt: versehentlich vorangestellter Codeblock vor der `package`-Deklaration entfernt, sodass Imports/Klasse wieder korrekt geparst werden.
- `OnDeviceLlmFormMappingRepository.android.kt` bereinigt: fehlerhaft eingefuegter Coroutines-Code vor der `package`-Deklaration entfernt.
- Ergebnis: `./gradlew :composeApp:compileDebugKotlinAndroid` laeuft wieder erfolgreich durch.

## Stand: 2026-03-17

## Repo/Tooling (Android + iOS)
- `.gitignore` um `ios-llama-build/` erweitert, damit lokal generierte iOS-Llama-Buildartefakte (inkl. massiver vendor-Kopie unter `LlamaIOSFramework.docc/llama.cpp`) nicht versehentlich versioniert werden.
- Bereits versehentlich gestagte Dateien aus `ios-llama-build/` aus dem Index entfernt; AI-/Modell-Dateien bleiben weiterhin ueber bestehende Regeln ausgeschlossen.

## Android: Whisper-Modellpfad mit Fallback abgesichert
- On-Device-Whisper prueft jetzt zuerst den konfigurierten Modellpfad (typisch `whisper-base.bin`) und faellt bei nicht lesbarer Datei automatisch auf `ggml-base.bin` im gleichen Verzeichnis zurueck.
- Initialisierung nutzt den tatsaechlich gefundenen lesbaren Modellpfad; bei Fehlschlag werden beide geprueften Kandidaten im Fehlertext ausgegeben.

## Android: ANR-Fix bei "Transkript wird verarbeitet"
- Schwere On-Device-Operationen (Whisper-Transkription, lokales Mapping und Llama-Inferenz) laufen jetzt explizit auf `Dispatchers.Default` statt im UI-Kontext.
- Aufrufe in `TranscriptionViewModel` und `FormViewModel` wurden ebenfalls auf Hintergrundausfuehrung umgestellt, damit Compose-Rendering und Touch-Input responsiv bleiben.
- Ziel: Freeze/ANR nach lokaler Transkription reduzieren, insbesondere waehrend des Zustands "Transkript wird verarbeitet".

## Android: Diagnose, Timeout und Build-Stabilisierung
- Zusaetzliches Timing-/Thread-Logging fuer On-Device-Transkription und On-Device-Mapping eingebaut (Start/Ende inkl. Dauer), um ANR-Ursachen auf Geraet besser nachvollziehen zu koennen.
- Timeout-Schutz fuer On-Device-Mapping ergaenzt (`90s`), damit die UI nicht unbegrenzt im Zustand "Transkript wird verarbeitet" verbleibt; bei Timeout wird eine klare Fehlermeldung gesetzt.
- Fehlende Android-Library-Module fuer `:llamaAndroidLib` und `:whisperAndroidLib` mit minimalen Stub-Implementierungen wiederhergestellt, damit Variant-Aufloesung und lokaler Compile-Pfad wieder funktionieren.

## Android: Fix fuer "Transkript ist leer (kein text/segments/words)"
- On-Device-Whisper setzt jetzt den rohen Modelltext als Fallback in `TranscriptionResponse.text`, falls keine Zeitstempel-Segmente geparst werden konnten.
- On-Device-LLM-Mapping behandelt komplett leere Transkripte nicht mehr als harten Fehler, sondern gibt ein gueltiges leeres Mapping-Ergebnis zurueck.
- UI-Fehlermeldung im Formular praezisiert: Bei leerem Transkript wird ein klarer Hinweis auf Aufnahme/Transkriptionspruefung angezeigt.

## Android: UX-Feinschliff bei 0 Mapping-Treffern
- Leere automatische Feldzuordnung (0 Treffer) wird nicht mehr als UI-Fehler angezeigt.
- Stattdessen bleibt der Ablauf erfolgreich, Prozessdetails erscheinen nur im Mapping-Log.

## Android: Root-Cause fuer leere On-Device-Ergebnisse abgesichert
- Laufzeitschutz eingebaut: Wenn nur Stub-Implementierungen der nativen On-Device-Bibliotheken aktiv sind, werden On-Device-Transkription und On-Device-LLM nicht initialisiert.
- Hintergrund: Stub-Libs liefern sofort leere/synthetische Antworten und fuehren sonst zu "0 Felder erkannt" trotz korrektem Gespraech.

## Android: Schritt 1 umgesetzt - echte On-Device-Libs wieder angebunden
- `llamaAndroidLib` und `whisperAndroidLib` nutzen jetzt die gesicherten realen Klassen (`classes.jar`) und JNI-Libs (`.so`) aus `_restore_backup/...` statt Stub-Quellcode.
- Dadurch laufen API und Native-Layer wieder gegen die zuvor gebauten On-Device-Artefakte.

## Android: On-Device-Mapping auf Relevanz verschaerft
- Prompt-Regeln erweitert: Pro Feld nur inhaltlich passende Teilsaetze uebernehmen; Off-Topic-Zusatzinhalte sollen verworfen werden.
- Lokale Nachfilterung fuer Lernfelder hinzugefuegt: Bei gemischten Antworten wird ein irrelevanter Nachsatz (z. B. Hobby-/Vorlieben-Teil) nicht mehr bevorzugt uebernommen.

## Android + iOS: TranscriptionViewModel commonMain-Fix
- JVM-spezifische Aufrufe (`System.currentTimeMillis`, `Thread.currentThread`) im `commonMain` durch `TimeSource.Monotonic` ersetzt.
- Konstruktor-Parameter im `TranscriptionViewModel` bereinigt und KDoc-Referenz auf nicht aufloesbares Symbol entschärft.
- Ergebnis: `:composeApp:compileDebugKotlinAndroid` laeuft wieder erfolgreich.

## Stand: 2026-03-16

## Repo/Tooling (Android + iOS)
- `.gitignore` erweitert um große AI-Modellartefakte (`*.gguf`, `*.ggml`, `*.safetensors`, `*.ckpt`, `*.onnx`, `*.pt`, `*.pth`, `*.tflite`) sowie typische Modellpfade (`**/models/**`, `**/Resources/models/**`, `**/files/models/**`, `iosApp/whisper-base.bin`), damit große lokale Modellfiles nicht versehentlich versioniert werden.

## iOS: Lokale Formularbefuellung (On-Device)
- iOS-Whisper-Dekodierung gehaertet: `IOSWhisperBridge.swift` nutzt jetzt neben `AVAudioFile` einen robusten `AVAssetReader`-Fallback (PCM float32, 16 kHz, mono), falls `AVAudioFile` bei `.m4a` 0 Samples liefert oder mit ObjC/Foundation-Fehlern abbricht.
- iOS-Diagnose erweitert: Audio-Dateigroesse und Decoder-Fallback-Pfad werden geloggt, damit Fehler wie `Keine Samples nach Dekodierung` besser analysierbar sind.
- iOS-Whisper-Diagnose fuer Foundation/ObjC-Fehler erweitert: `IOSWhisperBridge.swift` loggt bei Exceptions und Audio-Read/Convert-Fehlern jetzt `NSError`-Details (`domain`, `code`, `description`), damit Fehler wie `Foundation._GenericObjCError` konkret nachvollziehbar sind.
- iOS-Whisper-Import robuster gemacht: `IOSWhisperBridge.swift` akzeptiert jetzt sowohl `canImport(whisper)` als auch `canImport(libwhisper_all)`, damit statische XCFramework-Builds mit abweichendem Modulnamen nicht faelschlich als "nicht verlinkt" behandelt werden.
- iOS-Startdiagnose erweitert: App-Start loggt jetzt explizit, welches Whisper-Swift-Modul erkannt wurde (`whisper`, `libwhisper_all` oder keines), um verbleibende Linking-Probleme eindeutig sichtbar zu machen.
- iOS-Linking-Fix: `iosApp/iosApp.xcodeproj/project.pbxproj` aktualisiert, damit `whisper.xcframework` wieder in der `Frameworks`-Build-Phase des Targets `iosApp` verlinkt ist; dadurch wird `canImport(whisper)` im Swift-Bridge-Code wieder aktiv.
- iOS-Whisper-Registrierung robust gemacht: `registerIosWhisperBridgeIfAvailable()` registriert die Bridge nun immer beim App-Start; fehlendes Modell fuehrt nur noch zu einem klaren Laufzeitfehler (statt `Bridge nicht registriert`).
- iOS-On-Device-Fehlerdiagnose erweitert: `IOSWhisperBridge.swift` speichert jetzt den konkreten letzten Whisper-Fehlergrund (z. B. Modellpfad, Audio-Datei, Init/Konvertierung/Inference) und gibt ihn ueber `lastErrorMessage()` an KMP weiter; `TranscriptionViewModel` loggt diese Ursache explizit als `On-Device-Diagnose`.
- iOS-On-Device-Transkriptionsmodus stabilisiert: `createDefaultOnDeviceTranscriptionRepository()` nutzt jetzt eine lazy Bridge-Aufloesung (`DeferredIosWhisperBridge`), damit On-Device nicht mehr wegen Init-Reihenfolge (Swift-Bridge noch nicht registriert) auf Cloud zurueckfaellt.
- iOS-Whisper finalisiert auf statische Variante: `whisper.xcframework` wird jetzt aus `libwhisper_all.a` (inkl. statisch zusammengefuehrter `ggml`-Archive) erzeugt, sodass keine Runtime-Abhaengigkeit auf `libggml*.dylib` mehr besteht.
- `iosApp/iosApp.xcodeproj/project.pbxproj`: `whisper.xcframework` wieder in `Frameworks` aktiviert (ohne Embed), damit `canImport(whisper)` und On-Device-Whisper auf iOS wieder verfuegbar sind.
- iOS-Stabilitaetsfix: `whisper.xcframework` voruebergehend aus `Frameworks`/`CopyFiles` des iOS-Targets entfernt, um dyld-Crash durch transitive `libggml.*.dylib`-Abhaengigkeiten beim App-Start zu verhindern.
- iOS-Dyld-Fix fuer Whisper: `libwhisper.1.8.3.dylib`-Install-Name auf `@rpath/libwhisper.1.8.3.dylib` gesetzt, damit Runtime-Lookup nicht mehr auf `@rpath/libwhisper.1.dylib` ins Leere laeuft.
- iOS-Compile-Fix: `IOSWhisperBridge.swift` angepasst, damit `whisper_full_get_segment_t0/t1` (Int64) korrekt in der Zeitstempel-Formatierung verarbeitet werden (kein Int64->Int32-Fehler mehr).
- iOS-Whisper-Build-Fix: `whisper.xcframework`-Header erweitert (`ggml.h`, `ggml-cpu.h`, `ggml-backend.h`, `ggml-alloc.h`) und `module.modulemap` aktualisiert, damit `import whisper` und der Clang dependency scanner auf iOS korrekt aufloesen.
- `iosApp/iosApp.xcodeproj/project.pbxproj` erweitert: `whisper.xcframework` wird im Target `iosApp` jetzt in `Frameworks` verlinkt und ueber `CopyFiles` (CodeSignOnCopy) eingebettet.
- iOS-Whisper-Fix: sichere C-String-Uebergabe fuer `params.language` in `IOSWhisperBridge.swift` (kein dangling Pointer mehr waehrend `whisper_full`).
- iOS: On-Device-Whisper-Transkription strukturell aktiviert: `createOnDeviceTranscriptionRepository()` nutzt jetzt den iOS-Whisper-Provider statt `null`.
- Neue iOS-Whisper-Bridge-Registry in Shared (`IosWhisperBridge` / `IosWhisperBridgeRegistry`) fuer Swift->KMP-Anbindung analog zur Llama-Bridge.
- Neues iOS-Repository `OnDeviceWhisperTranscriptionRepository.ios.kt` fuer lokales Parsing von Whisper-Text/Segmenten in `TranscriptionResponse`.
- Neue Swift-Datei `iosApp/iosApp/IOSWhisperBridge.swift` inkl. Modellpfad-Fallbacks (`WHISPER_MODEL_PATH` -> Bundle -> Documents) und App-Start-Registrierung.
- `iosApp/iosApp/iOSApp.swift` erweitert: registriert jetzt zusaetzlich `registerIosWhisperBridgeIfAvailable()` beim Start.
- On-Device-Formular-Mapping auf iOS aktiviert (bisher `null`-Factory ersetzt).
- iOS nutzt jetzt denselben lokalen Mapping-Kern wie Android: LLM-JSON-Extraktion, Heuristik und Feld-Merge mit Qualitaetspruefungen.
- iOS-Keyword-Fallback fuer Formularbefuellung hinzugefuegt, damit auch ohne verfuegbare LLM-Engine lokale Zuordnung moeglich bleibt.
- Swift-Bridge (`iosApp/iosApp/IOSLlamaBridge.swift`) zur echten lokalen llama.cpp-Inferenz angebunden und beim App-Start in `iosApp/iosApp/iOSApp.swift` registriert.
- Shared-iOS-Provider nutzt jetzt eine registrierbare Bridge (`IosLlmBridgeRegistry`) statt eines festen Platzhalters, damit lokale LLM-Antworten direkt in den bestehenden Mapping-Flow einfliessen.
- `iosApp/iosApp/LibLlama.swift` aus dem llama.swiftui-Beispiel als Runtime-Basis integriert (guarded via `#if canImport(llama)`).
- `iosApp/iosApp.xcodeproj/project.pbxproj` erweitert, sodass `llama.xcframework` im Target `iosApp` in der Frameworks-Phase verlinkt ist.
- iOS-Dyld-Fix: `llama.xcframework` wird im Target `iosApp` zusaetzlich ueber eine `Embed Frameworks`-Phase in die App eingebettet (`CodeSignOnCopy`), damit `@rpath/llama.framework/llama` zur Laufzeit gefunden wird.
- `iosApp/Configuration/Config.xcconfig` nutzt nun optional `#include? "Secrets.xcconfig"` fuer lokale Overrides.
- Lokale Datei `iosApp/Configuration/Secrets.xcconfig` angelegt und `Secrets.xcconfig.template` um `LLAMA_MODEL_PATH`/`WHISPER_MODEL_PATH` erweitert.
- Startstabilitaet verbessert: Bridge-Registrierung beim App-Start erfolgt nur noch, wenn `LLAMA_MODEL_PATH` gesetzt ist, die Datei existiert und `llama` importierbar ist.
- UI-Fix: On-Device-Formular-Mapping ist jetzt auch auf iOS auswählbar, selbst wenn On-Device-Transkription nicht verfuegbar ist (Entkopplung von Mapping- und Transkriptions-Repository-Wiring).
- iOS-Modellpfad-Handling erweitert: lokale Llama-Bridge sucht Modell nun in dieser Reihenfolge: `LLAMA_MODEL_PATH` (uebergeben/Info.plist) -> `model.gguf` im App-Bundle -> `Documents/models/model.gguf`.
- iOS-Automationsmodus-Fix: Bei gewaehltem On-Device-Modus bleibt der Modus stabil; falls lokale Whisper-Transkription fehlt, wird nur die Transkription auf Cloud gefallbackt (statt den Modus auf Cloud zu erzwingen).
- iOS-Config-Fix: `Config.xcconfig`-Include-Reihenfolge korrigiert, damit Werte aus `Secrets.xcconfig` (`OPENAI_API_KEY`, Modellpfade) die Defaults wirklich ueberschreiben.
- iOS-UX-Fix: Die Form-Automatisierung kann auf On-Device stehen, ohne dass die Transkriptions-Logs einen nicht konfigurierten On-Device-Whisper-Pfad als aktiven Fehlerpfad anzeigen; Transkription bleibt in diesem Fall kontrolliert auf Cloud.
- iOS-Konfiguration erweitert: `LLAMA_MODEL_PATH` und `WHISPER_MODEL_PATH` werden aus `Info.plist` gelesen (analog zu bestehendem `OPENAI_API_KEY`).
- `iosApp/iosApp/Info.plist` um Modellpfad-Keys ergaenzt und strukturell bereinigt (duplizierte XML/Plist-Deklaration entfernt).
- `iosApp/Configuration/Config.xcconfig` um `LLAMA_MODEL_PATH` und `WHISPER_MODEL_PATH` als Build-Variablen ergaenzt.

## Android: On-Device vs. Cloud Pipeline
- Android-Transkriptionssprache korrigiert: `whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/jni.c` setzt `params.language` wieder auf `"de"` statt `"en"`, damit On-Device-Whisper auf Deutsch transkribiert.
- Android-Build-Alignment: `whisper.cpp/examples/whisper.android/lib/build.gradle` auf `ndkVersion 29.0.14206865` und ABI-Set `arm64-v8a/x86_64` vereinheitlicht, passend zu `llamaAndroidLib` und zur aktuellen lokalen SDK-Konfiguration.
- Android-Packaging-Fix: `composeApp/build.gradle.kts` um `jniLibs.pickFirsts` fuer `**/libggml*.so` erweitert, damit Merge-Konflikte zwischen `llamaAndroidLib` und `whisperAndroidLib` bei nativen ggml-Binaries (z. B. `libggml.so`, `libggml-base.so`) aufgeloest werden.
- Android-Whisper-Restore: fehlende Kernquellen in `whisper.cpp` (u. a. `CMakeLists.txt`, `src/`, `include/`, `cmake/`, `ggml/`) wiederhergestellt, damit `:whisperAndroidLib:configureCMake...` nicht mehr am fehlenden `ggml`-Source-Verzeichnis scheitert.
- Android-Whisper-CMake-Warnung bereinigt: `whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt` nutzt fuer `WHISPER_VERSION` kein `PARENT_SCOPE` mehr im Top-Level-Scope.
- Android-Native-Compat-Fix: `llama.cpp/examples/llama.android/lib/src/main/cpp/logging.h` nutzt `__android_log_is_loggable` nur noch ab API 30; fuer niedrigere APIs (z. B. minSdk 24) wird auf einen Log-Level-Fallback gewechselt.
- Android-Native-CMake-Fix: KleidiAI fuer `arm64-v8a` im wiederhergestellten `llamaAndroidLib` deaktiviert (`GGML_CPU_KLEIDIAI=OFF`), um den `FetchContent/ExternalProject`-Configure-Fehler unter CMake 3.22.x zu vermeiden.
- Android-Native-Restore erweitert: `llama.cpp/examples/llama.android/lib/src/main/cpp/CMakeLists.txt` nutzt jetzt einen Fallback auf `iosApp/llama.cpp`, falls im Root-`llama.cpp` keine `CMakeLists.txt` mehr vorhanden ist.
- Android-CMake-Kompatibilitaet hergestellt: `llama.cpp/examples/llama.android/lib/src/main/cpp/CMakeLists.txt` auf `cmake_minimum_required(VERSION 3.22.1)` angepasst, damit die lokale SDK-CMake-Version den Native-Configure-Schritt wieder ausfuehren kann.
- Android-CMake-Fix: `llama.cpp/examples/llama.android/lib/build.gradle.kts` auf `cmake.version = "3.22.1"` umgestellt, da `3.31.6` lokal nicht installiert war und `:llamaAndroidLib:configureCMakeRelease[arm64-v8a]` dadurch fehlschlug.
- Android-Build-Restore vervollstaendigt: `llama.cpp/examples/llama.android/lib/build.gradle.kts` auf `minSdk = 24` und lokal vorhandenes `ndkVersion = 29.0.14206865` angepasst, damit `:llamaAndroidLib` wieder mit `composeApp` zusammengebaut werden kann.
- Android-Build-Restore vervollstaendigt: `whisper.cpp/examples/whisper.android/lib/build.gradle` auf `minSdk 24` angepasst, damit `:whisperAndroidLib` wieder zur `composeApp`-Manifest-Konfiguration passt (kein Merge-Fehler 24 vs 26 mehr).
- Android-Build-Restore: `llama.cpp/examples/llama.android/lib/build.gradle.kts` von Version-Catalog-Aliases (`libs.*`) auf direkte Plugin-/Dependency-Notation umgestellt, damit das Modul als eingebundenes Subprojekt in `BachelorAIProject` wieder korrekt konfiguriert und als Variant-Provider aufgeloest wird.
- Umschaltbare Automatisierung zwischen Cloud und On-Device fuer die Formularbefuellung eingefuehrt.
- On-Device-Flow als eigener Pfad auf Android integriert (ohne Cloud-Abhaengigkeit fuer Mapping, wenn On-Device aktiv ist).

## On-Device Transkription (Whisper)
- Lokale Whisper-Transkription fuer Android angebunden.
- Modellladepfad auf App-internen Speicher (`files/models/whisper-base.bin`) ausgerichtet.
- Sprachfokus fuer On-Device-Transkription auf Deutsch gesetzt.
- Debug-Ausgaben fuer Transkript-Metriken (Textlaenge, Segmente, Woerter) ergaenzt, um Fehleranalyse zu beschleunigen.

## On-Device Formular-Mapping (LLM)
- Llama.cpp als lokale LLM-Engine ueber C++/JNI-Bridge auf Android eingebunden.
- GGUF-Modell-Ladepfad im App-internen Speicher (`files/models/model.gguf`) vorgesehen.
- On-Device-Prompting und JSON-Extraktion fuer Formularfelder integriert.
- Fallback-Strategien (Heuristik + bestehendes Mapping) ergaenzt, falls LLM-Antwort unvollstaendig ist.

## UI/UX und Diagnose
- UI-Feedback fuer laufende On-Device-Automatisierung verbessert (sichtbarer Verarbeitungsstatus/Loading).
- Prozess-Logs in der UI fuer den On-Device-Ablauf erweitert.
- Fehlermeldungen fuer typische Ursachen (leeres Transkript, fehlendes/ungueltiges Modell, Mapping ohne Treffer) praezisiert.

## Aktueller Fix (dieser Schritt)
- On-Device-Mapping so verbessert, dass bei Frage-Antwort-Verlaeufen bevorzugt die **Antwort** statt der wiederholten Frage in Felder geschrieben wird.
- Prompting auf generische Feldzuordnung erweitert: Das Modell orientiert sich an den aktuellen Formularfragen (IDs/Labels), statt nur an fest verdrahteten Frageformulierungen.
- Heuristische Antwortfindung pro Formularfeld verallgemeinert und robustere Erkennung von Frage-Echos eingebaut.
- Fallback-Key-Parsing fuer On-Device-LLM-Antworten auf dynamische Formularfelder erweitert (nicht mehr nur fest auf `name/problem/learning`).

## Nachtraeglicher Fix: Duplizierte Antworten in allen Feldern
- On-Device-Merging ueberarbeitet: Antworten werden jetzt pro Feld bewertet (LLM -> Heuristik -> Fallback) statt nur blind per Map-Prioritaet zu ueberschreiben.
- Schutz gegen degenerierte LLM-Ausgaben eingebaut: Wenn das LLM denselben Wert fuer mehrere Felder liefert, werden diese Antworten verworfen und durch Heuristik/Fallback ersetzt.
- Duplikat-Schutz zwischen Nicht-Name-Feldern eingefuehrt, damit nicht dieselbe Antwort in `problem` und `learning` landet.
- Zusaetzliches Debug-Logging fuer Quellfelder (`llm`, `heuristic`, `fallback`, `final`) hinzugefuegt, damit Fehlzuordnungen schneller analysiert werden koennen.

## Nachtraeglicher Fix: Persistente Fehlzuordnung auf erste Antwort
- Frageerkennung in der Heuristik entkoppelt: Feld-Keywords markieren nicht mehr automatisch jede passende Aussage als "Frage".
- Satzbewertung verschaerft: Es werden nur noch inhaltlich passende Saetze pro Feld beruecksichtigt (kein globaler Basispunkt mehr, der sonst oft den ersten Satz bevorzugte).
- Name-Feld-Validierung verschaerft, damit keine langen Antwortsaetze als Name uebernommen werden.
- Frage-Echo-Erkennung erweitert (inkl. exakter Label-Wiederholung), um wiederholte Frageformulierungen besser auszufiltern.

## Nachtraeglicher Fix: Name + Lernfeld in linearen Gespraechstexten
- Namensextraktion angepasst: Im Namensfeld wird jetzt der volle erkannte Name (z. B. Vor- und Nachname) statt nur des Vornamens eingetragen.
- Neue On-Device-Heuristik fuer lineare Frage-Antwort-Transkripte ohne stabile Sprecherwechsel: Antwort wird direkt aus dem Textabschnitt nach der passenden Frage extrahiert.
- Bereinigung der extrahierten Antwort ergaenzt, damit eingeschobene Folgefragen am Segmentende abgeschnitten und nur der relevante Antwortteil uebernommen wird.

## Nachtraeglicher Fix: Namensvarianten wie "Ich heiss ..."
- On-Device-Namensextraktion erweitert, damit auch umgangssprachliche Varianten wie "Ich heiss ..."/"Ich heiß ..." erkannt werden.
- Name wird im Mapping vor der Feldvalidierung aus Antwortsaetzen normalisiert (z. B. "Ich heiss Markus Andreas" -> "Markus Andreas").
- Name-Feldgrenzen leicht erweitert, damit gueltige mehrteilige Namen nicht verworfen werden.
- Keyword-Fallback fuer das Namensfeld auf dieselben sprachlichen Varianten angehoben.

## Neu: On-Device Toggle fuer Rechtschreibkorrektur
- Im Formular gibt es einen neuen Toggle "On-Device Rechtschreibkorrektur" (nur fuer On-Device relevant).
- Bei aktiviertem Toggle erhaelt das lokale LLM eine zusaetzliche Prompt-Regel, offensichtliche Whisper-/ASR-Schreibfehler kontextbasiert zu korrigieren (inkl. lautnaher Namen/Fachbegriffe), bevor Felder befuellt werden.
- Bei deaktiviertem Toggle arbeitet On-Device moeglichst woertlich ohne aktive Rechtschreibnormalisierung.
- Toggle-Aenderungen werden im Prozess-Log sichtbar gemacht und im On-Device-Modus direkt auf das letzte Transkript neu angewendet.

## Stand: 2026-03-23

- `Doku.md` hinzugefuegt: umfassende Projektdokumentation (Technologien, Architektur, Feature-Flows, Plattform-Hinweise). (Android + iOS)

## Stand: 2026-03-24

- Android (On-Device-Form-Mapping): Abschneiden von Antwortsaetzen (z. B. `problem` nur `Das Problem`) behoben.
  - `OnDeviceLlmFormMappingRepository.android.kt`: Prompt-Regel von `maximal 6 Woerter` auf zusammenhaengende Antwortsaetze (bis 220 Zeichen) angepasst.
  - `OnDeviceLlmEngineProvider.android.kt`: `predictLength` im Performance-Mode nicht mehr auf 16 reduziert (jetzt bis 32), damit JSON-Antworten nicht vorzeitig enden.
  - `OnDeviceLlmFormMappingRepository.android.kt`: neue Bereinigungsregel fuer LLM-Endartefakte; abschliessende Sequenzen wie `",`/`,"` werden vor dem Befuellen entfernt, damit Felder nicht mit Restzeichen enden.
  - `OnDeviceLlmFormMappingRepository.android.kt`: Speaker-Platzhalter (`SPEAKER_00`, `Sprecher 1`) werden im Namensfeld explizit verworfen, damit echte Namensaussagen bevorzugt uebernommen werden.
  - `OnDeviceLlmFormMappingRepository.android.kt`: Fallback-KeyValue-Parsing unterstuetzt quoted Werte mit Kommas robuster; zusaetzliche Kantenbereinigung entfernt Artefakte wie `["...`, `"]`, `",`.

- Android: `whisperAndroidLib` (`whisper.cpp/examples/whisper.android/lib/build.gradle`) auf echten Source-Build mit statischem ggml-Linking festgezogen (`-DBUILD_SHARED_LIBS=OFF`, `-DGGML_BACKEND_DL=OFF`), damit keine Restore-/Prebuilt-JNI-Artefakte fuer ggml mehr noetig sind.
- Android: ggml-Kollisions-Workaround per `pickFirst` entfernt: `composeApp/build.gradle.kts` und `llama.cpp/examples/llama.android/lib/build.gradle.kts` nutzen kein `**/libggml*.so`-`pickFirst` mehr.
- Android: Packaging bleibt fuer `libomp.so` weiterhin defensiv auf `pickFirst`, da hier weiterhin ein legitimer transitive Native-Konflikt zwischen den Android-Libs auftreten kann.
- Android: neues Auswertungsskript `scripts/parse_llm_timings.py` hinzugefuegt, um On-Device-LLM-Laufzeiten aus `DirectLlamaOnDeviceLlmEngine`-Logcat-Zeilen (inkl. p50/p95/avg) reproduzierbar zu messen.
- Android: `scripts/README.md` ergaenzt mit einem kompakten Messablauf (`adb logcat` aufnehmen + Parser ausfuehren) fuer den erneuten LLM-Pfad-Check.
- Android: Modellpfad-Aufloesung fuer On-Device-LLM robuster gemacht (`composeApp/src/androidMain/kotlin/com/example/bachelor_ai_project/features/form/data/AndroidLlamaModelPathResolver.kt`), damit bei Dateinamen-/Pfadabweichungen nach Push/Reinstall vorhandene `.gguf`-Modelle in `files/models` trotzdem gefunden werden.
- Android: `OnDeviceLlmEngineProvider.android.kt` und `OnDeviceLlmFormMappingRepository.android.kt` auf den gemeinsamen Resolver umgestellt; dadurch nutzt Engine-Initialisierung und UI-Status dieselbe reale Modellpfad-Pruefung statt eines starren Einzelpfads.
- Android: Persistenter roter LLM-Status nach Modellinstallation behoben: `OnDeviceLlmFormMappingRepository.android.kt` kann eine initial fehlende Engine jetzt lazy nacherzeugen und blockiert Warmup/Selftest nicht mehr auf einem fruehen `null`-Snapshot.
- Android: On-Device-Statusanzeige praezisiert (`FormUiState.kt`, `FormViewModel.kt`, `FormScreen.kt`) mit separatem Zustand `isOnDeviceLlmModelConfigured`; UI zeigt bei gefundenem, aber noch nicht geladenem Modell jetzt einen Zwischenstatus statt faelschlich "LLM-Model nicht gefunden".
- Android: Proaktiver Warmup im `FormViewModel`-Init aktiviert, damit Modellladen frueher startet und der Status schneller auf "bereit" wechselt.
- Android: Llama-Warmup gegen fehlerhafte Engine-Zustaende gehaertet (`LlamaCppOnDeviceLlmEngine.android.kt`): vor `configureRuntime` wird ein vorhandener `Error`-State per `cleanUp()` zurueckgesetzt und auf `Initialized` gewartet, damit der Fehler "Cannot configure runtime in Error!" nicht mehr den Modell-Load blockiert.

