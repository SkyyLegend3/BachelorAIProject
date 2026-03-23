# Doku — Projektübersicht, Architektur und Entwicklerhinweise

Letzte Aktualisierung: 2026-03-23

Zweck
-------
Diese Datei dokumentiert die verwendeten Technologien, die Architekturprinzipien und die Funktionsweise der App sowie Hinweise zum Entwickeln, Testen und Debuggen (Android + iOS).

Kurzüberblick
--------------
Die App ist eine Kotlin Multiplatform (KMP) Anwendung mit Compose Multiplatform UI, die folgenden Haupt-Workflow implementiert:
- Audioaufnahme eines Feedback-Gesprächs (als .m4a)
- Upload / lokale Verarbeitung der Audiodatei
- Transkription (Cloud-OpenAI oder On-Device Whisper)
- Sprecherzuweisung / Diarization (sofern verfügbar via Transkriptions-API)
- Mapping des Transkripts auf ein projekt-spezifisches Formular (heuristisch + LLM)
- Optionales On-Device Mapping mittels llama.cpp (GGUF-Modelle)

Wichtigste Ordner & Konzepte
-----------------------------
Die Code-Basis ist feature-orientiert organisiert, zentrale Pfade:

- `composeApp/src/commonMain/kotlin/...` — gemeinsame Business-Logik (domain, use-cases, common ViewModels, UI)
- `composeApp/src/androidMain/...` — Android-spezifische Implementierungen (Permissions, Recorder, Platform-Utilities, On-Device-Bridges für JNI)
- `composeApp/src/iosMain/...` — iOS-spezifische Implementierungen (Swift-Bridges, Platform-Utilities)
- `iosApp/` — iOS App, Swift/SwiftUI-Bridges, xcconfigs
- `llama.cpp/` und `whisper.cpp/` — Native Beispiele/Bridges für On-Device-Inferenz (als Submodule/Source im Repo)

Architekturprinzipien
----------------------
- MVVM (ViewModel in `presentation`/`presentation`-Pfade, UI in `ui`)
- Feature-first Struktur — z. B. `features/recording`, `features/transcription`, `features/form`
- Saubere Trennung: `ui`, `presentation`, `domain`, `data`
- Plattformcode nur wo nötig (Audio, Dateizugriff, Permissions, native LLM/Whisper-Bridges)

Wichtige Technologien
---------------------
- Kotlin Multiplatform (KMP) für gemeinsame Business-Logik
- Compose Multiplatform für UI
- Coroutines für asynchrone Abläufe und Background-Worker
- Ktor / HttpClient (oder plattformspezifischer HttpClient) für POST-Uploads an Cloud-APIs
- OpenAI-API (Cloud) für Transkription (Whisper) und ggf. LLM Mapping
- llama.cpp (via JNI auf Android, via Swift-Bridge auf iOS) für optionales On-Device-LLM (GGUF)
- whisper.cpp (lokale Transkription) als On-Device-Option
- Native JNI/Swift Bridges unter `llama.cpp/examples/...` und `iosApp/...` für iOS

Konfigurationsdateien
---------------------
- `local.properties` (Android / lokale Dev-Einstellungen)
  - enthält z. B. `openai.api.key`, `llama.model.path`, `whisper.model.path` sowie On-Device-Tuning-Parameter (n_ctx, threads, timeout, performance.mode)
  - Beispiel: `llama.model.path=/data/user/.../files/models/model.gguf` (Android app-intern)

- `iosApp/Configuration/Config.xcconfig` und `Secrets.xcconfig` (iOS) — enthält `OPENAI_API_KEY`, `LLAMA_MODEL_PATH`, `WHISPER_MODEL_PATH`

Feature-Flows (detailliert)
---------------------------
1) Aufnahme
  - UI bietet Recording-Start/Stop (Compose).
  - Android: `composeApp/src/androidMain/.../AudioRecorder.android.kt` und `AudioRecorderFactory` implementieren plattformspezifische Aufnahme.
  - iOS: `AudioRecorder.ios.kt` (oder Swift-Seite in `iosApp`) sorgt für Aufnahme und Speicherung als `.m4a`.
  - Ausgabe: eine Audio-Datei im Container `.m4a` (AAC/PCM) im App-internen Speicher.

2) Transkription
  - Zwei Pfade vorhanden:
    - Cloud (OpenAI Whisper / OpenAI-Transcription via POST) — Standard-Fallback.
    - On-Device (whisper.cpp) — wenn `WHISPER_MODEL_PATH` (iOS) oder `whisper.model.path` (Android) konfiguriert ist und native Bridge verfügbar.
  - Implementierungen:
    - Cloud: `OpenAiTranscriptionRepository` in `commonMain` sendet die `.m4a` per POST an OpenAI
    - On-Device Android: `OnDeviceWhisperTranscriptionRepository.android.kt` nutzt native JNI-Bindings
    - On-Device iOS: `IOSWhisperBridge.swift` + `OnDeviceWhisperTranscriptionRepository.ios.kt`
  - Ergebnis: `TranscriptionResponse` mit `text`, `segments` und Timestamps; Speaker-Labels falls verfügbar.

3) Mapping Transkript -> Formular
  - Ziel: Aus Transkript sprachliche Antworten in spezifische Formularfelder überführen
  - Mögliche Mapping-Strategien (Priorität):
    1. On-Device LLM (llama.cpp) — wenn Modell vorhanden und Engine bereit
    2. Cloud-LLM (OpenAI) — wenn API-Key vorhanden und On-Device nicht verfügbar oder fehlschlägt
    3. Heuristik / Keyword-Fallback — robuster Fallback ohne LLM
  - Implementierung:
    - `MapTranscriptToFormUseCase` und `TranscriptToFormMapper` (commonMain) orchestrieren den Ablauf
    - `OnDeviceLlmFormMappingRepository` (Android/iOS platform-specific) ruft LLM-Bridge auf
    - `OnDeviceKeywordFormMappingRepository` implementiert heuristische Extraktion
  - Besonderheiten:
    - Duplikat-Schutz (vermeidet identische Werte in mehreren Feldern)
    - Feld-spezifische Validierung (z. B. Name-Längenprüfung)
    - Optionaler Rechtschreibkorrektur-Toggle für On-Device-LLM

4) UI und Editierbarkeit
  - Ergebnis wird in `FormViewModel` gehalten und in `FormScreen` angezeigt.
  - Nutzer kann erkannte Werte editieren und Änderungen persistieren.

Plattform-spezifische Brücken
------------------------------
Android
  - JNI-Wrapper `DirectLlamaBridge` unter `llama.cpp/examples/llama.android/.../DirectLlamaBridge.kt` stellt native Methoden bereit: Init, LoadModel, Infer, Cancel, Unload, Shutdown.
  - On-Device Whisper JNI lives unter `whisper.cpp/...` und wird über `OnDeviceWhisperTranscriptionRepository.android.kt` aufgerufen.
  - Modellpfade typischerweise in App-internem Speicher (`files/models/`) erwartet; falls konfigurierter Pfad außerhalb liegt, wird das Modell in `files/models/` kopiert bevor geladen.

iOS
  - Swift-Bridges: `IOSLlamaBridge.swift` und `IOSWhisperBridge.swift` werden beim App-Start registriert, wenn entsprechende Module (`llama`, `whisper`) und Modellpfade vorhanden sind.
  - `LLAMA_MODEL_PATH` / `WHISPER_MODEL_PATH` können über `Info.plist` oder `Config.xcconfig` konfiguriert werden.

Netzwerk & Sicherheit
----------------------
- API-Keys: `openai.api.key` in `local.properties` (Android dev) oder `Secrets.xcconfig` (iOS). Niemals in Git einchecken.
- Upload: Audio wird als `.m4a` per POST an die Transkriptions-API gesendet (`OpenAiTranscriptionRepository`).
- Beim Entwickeln lokal: setze `OPENAI_API_KEY` in `local.properties` / `Secrets.xcconfig` oder nutze Umgebungsvariablen.

Modelle & Performance-Tuning
-----------------------------
- Für On-Device LLM empfiehlt sich ein kleines quantisiertes Instruct-GGUF-Modell (z. B. Llama-3-xx in kleiner Größe), da mobile Geräte begrenzte RAM/CPU haben.
- `local.properties` enthält Parameter zum Run-Time-Tuning von Llama (z. B. `llama.n.ctx`, `llama.predict.length`, `llama.temperature`, `llama.threads.min/max`, `llama.inference.timeout.ms`).
- Android setzt konservative Defaults (z. B. `n_batch` kleiner) um OOM/ANR zu vermeiden.

Debugging & Diagnose
---------------------
- Startdiagnosen:
  - Android-Logs (Logcat): Transkript-, Mapping-, LLM-Statusmeldungen werden ausgegeben
  - iOS: Konsolenlogs aus Swift/Bridge
- Häufige Fehlerquellen:
  - Fehlender Modellpfad → On-Device fällt kontrolliert auf Heuristik/Cloud zurück
  - JNI/Symbol-Mismatch → `UnsatisfiedLinkError` wenn native libs nicht aus dem selben Build stammen
  - Lange On-Device-Operationen → Inferenz/Transkription immer auf Background-Dispatcher laufen

Build & Run (Kurzbefehle)
-------------------------
- Android debug build (macOS / Linux):
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- iOS: Öffne `iosApp/iosApp.xcodeproj` in Xcode und starte das Target `iosApp` (oder nutze die im Projekt konfigurierten Schemes).

Testing
--------
- Unit-Tests: Schreibe Tests für `domain`/`usecase`-Logik in `commonTest`.
- Integration: Manuelles Testen für On-Device Pfade (benötigen lokal verfügbare Modelle und ggf. Geräte/Sim). Verwende kleine Modelle für schnelle Iteration.

Hinweise für Entwickler
-----------------------
- Behandle `local.properties` und `Secrets.xcconfig` als vertraulich.
- Platform-only code belongs into `androidMain` / `iosMain` only.
- Keep prompts and mapping logic in `commonMain` where possible to reuse between platforms.
- Wenn du native libs updatest, baue alle nativen Module sauber (ndk/CMake für Android, XCFrameworks für iOS) und prüfe ABI/Version-Konsistenz.

Zusätzlicher Hinweis für On-Device Betrieb (Modelle auf dem Gerät)
---------------------------------------------------------------
- Für On-Device-Transkription (`whisper`) und On-Device-LLM (`llama` / GGUF) muss das jeweilige Modell auf dem Gerät vorhanden und lesbar sein. Die App sucht typischerweise in ihrem App-internen Speicher `files/models/` (z. B. `/data/data/com.example.bachelor_ai_project/files/models/`).
- Beim Entwickeln ist es praktisch, Modelle per `adb` auf das Gerät zu kopieren. Zwei sichere Varianten:

  1) Empfohlen: `run-as` (nur möglich für debuggable Builds)

  ```bash
  # 1) Pushen in ein temporäres Verzeichnis
  adb push /Pfad/zum/whisper-base.bin /data/local/tmp/whisper-base.bin

  # 2) Mit run-as in das App-interne files-Verzeichnis kopieren (ersetze package.name)
  adb shell run-as com.example.bachelor_ai_project sh -c 'mkdir -p files/models && cat /data/local/tmp/whisper-base.bin > files/models/whisper-base.bin && chmod 0644 files/models/whisper-base.bin'

  # Analog für das GGUF-Model (.gguf)
  adb push /Pfad/zum/model.gguf /data/local/tmp/model.gguf
  adb shell run-as com.example.bachelor_ai_project sh -c 'mkdir -p files/models && cat /data/local/tmp/model.gguf > files/models/model.gguf && chmod 0644 files/models/model.gguf'
  ```

  Hinweise:
  - `run-as` funktioniert nur, wenn das App-Build debuggable ist (Standard bei `assembleDebug`).
  - Verwende korrekte Dateirechte (`chmod 0644`) damit die App die Dateien lesen kann.

  2) Fallback: Push auf externen Speicher (sdcard)

  ```bash
  # Direktes Pushen in das extern zugängliche app-Verzeichnis
  adb push /Pfad/zum/whisper-base.bin /sdcard/Android/data/com.example.bachelor_ai_project/files/models/whisper-base.bin
  adb push /Pfad/zum/model.gguf /sdcard/Android/data/com.example.bachelor_ai_project/files/models/model.gguf
  ```

  Hinweise:
  - Manche Geräte/OS-Versionen blockieren Zugriff auf `/sdcard/Android/data/...` für Apps; dieser Pfad ist jedoch eine einfache Option ohne root/run-as.
  - Nach dem Pushen empfiehlt es sich, die App neu zu starten oder einen kleinen File-Existenz-Check in der App auszuführen.

Wenn du Probleme beim Zugriff auf die Pfade hast, prüfe mittels `adb shell ls -l /data/data/com.example.bachelor_ai_project/files/models` und Logs (Logcat) auf Berechtigungs- oder Pfadfehler.

Changelog-Entry
----------------
- Diese `Doku.md` wurde am 2026-03-23 hinzugefügt (Android + iOS).

Weiterführende Dateien (schnell finden)
-------------------------------------
- `Changelog.md` — Entwicklungsschritte & Fix-Historie
- `README.md` — Kurzinfo & wichtige Hinweise zum On-Device Betrieb
- `composeApp/src/commonMain/kotlin/com/example/bachelor_ai_project/features/` — Feature-Code
- `llama.cpp/examples/...` und `whisper.cpp/examples/...` — native Brückenquellen
---
Ende der Doku


