# Changelog

## Stand: 2026-03-17

## Repo/Tooling (Android + iOS)
- `.gitignore` um `ios-llama-build/` erweitert, damit lokal generierte iOS-Llama-Buildartefakte (inkl. massiver vendor-Kopie unter `LlamaIOSFramework.docc/llama.cpp`) nicht versehentlich versioniert werden.
- Bereits versehentlich gestagte Dateien aus `ios-llama-build/` aus dem Index entfernt; AI-/Modell-Dateien bleiben weiterhin ueber bestehende Regeln ausgeschlossen.

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


