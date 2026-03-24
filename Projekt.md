<div align="center">
  <h1><strong>Projektvorstellung: BachelorAIProject</strong></h1>
</div>

<h2><strong>1. Ziel des Projekts</strong></h2>
Im Rahmen dieses Projekts habe ich eine mobile Anwendung entwickelt, die ein gesprochenes Feedback- oder Reflexionsgespräch verarbeitet und daraus automatisch ein strukturiertes Formular befüllt. Die App kann Audio entweder direkt aufnehmen oder als vorhandene Datei laden, daraus ein Transkript erzeugen und die Inhalte anschließend auf feste Formularfelder abbilden.
Der Gesamtprozess besteht aus drei fachlichen Hauptschritten:
1. Audio erfassen
2. Sprache in Text umwandeln
3. Text in strukturierte Formularwerte überführen
Das Projekt ist technisch besonders interessant, weil es nicht nur eine Cloud-Verarbeitung verwendet, sondern zusätzlich lokale On-Device-Verfahren integriert. Dadurch verbindet es klassische App-Entwicklung mit nativer KI-Ausführung auf dem Gerät.

<h2><strong>2. Verwendete Technologien</strong></h2>
Für die Umsetzung habe ich mehrere Technologien kombiniert:
- **Kotlin Multiplatform (KMP)** für gemeinsame Business-Logik auf Android und iOS
- **Compose Multiplatform** für die UI und Screen-Struktur
- **OpenAI API** für Cloud-Transkription und Cloud-basiertes Formular-Mapping
- **whisper.cpp** für lokale Speech-to-Text-Verarbeitung
- **llama.cpp** für lokale LLM-Inferenz zur Formularautomatisierung
- **JNI** auf Android als Brücke zwischen Kotlin und nativer C/C++-Logik
- **Swift-Bridges** auf iOS als Anbindung nativer Bibliotheken an den KMP-Code
- **Ktor HTTP Client** für API-Kommunikation
- **MediaCodec / MediaExtractor** auf Android zur Audio-Dekodierung

<h2><strong>3. Architektur des Projekts</strong></h2>
Die Anwendung ist als Kotlin-Multiplatform-Projekt aufgebaut. Dadurch konnte ich die fachliche Logik plattformübergreifend teilen und nur die systemnahen Teile getrennt implementieren.
Wichtige Bestandteile des Projekts sind:
- `composeApp/`: gemeinsamer App-Code sowie `commonMain`, `androidMain` und `iosMain`
- `iosApp/`: iOS-App-Entry-Point und Swift-Bridges
- `llama.cpp/`: eingebundene native LLM-Bibliothek
- `whisper.cpp/`: eingebundene native Speech-to-Text-Bibliothek
Zusätzlich sind auf Android zwei native Bibliotheken direkt als Gradle-Module eingebunden:
- `:llamaAndroidLib`
- `:whisperAndroidLib`
Die App folgt im Kern einer klaren Schichtenstruktur:
- **UI-Schicht**: Compose Screens
- **Presentation-Schicht**: ViewModels mit `StateFlow`
- **Domain-Schicht**: Modelle, Use-Cases und Interfaces
- **Data-Schicht**: Repositories für Cloud- und On-Device-Verarbeitung
Diese Trennung war für mich wichtig, damit die fachliche Logik nicht direkt an Plattformdetails oder native Bibliotheken gekoppelt ist.

<h2><strong>4. Gesamtworkflow der App</strong></h2>
Der Ablauf der Anwendung ist technisch wie fachlich durchgängig aufgebaut:
1. Die Nutzerin oder der Nutzer nimmt Audio auf oder wählt eine Datei aus.
2. Das `RecordingViewModel` übergibt den Dateipfad an die Transkriptionslogik.
3. Je nach Modus startet entweder die Cloud-Transkription oder die lokale Whisper-Transkription.
4. Das Ergebnis wird als `TranscriptionResponse` in die App zurückgegeben.
5. Dieses Transkript wird an das Formular-Mapping weitergereicht.
6. Das Mapping befüllt Felder wie `name`, `problem` und `learning`.
7. Die UI zeigt das vorausgefüllte Formular an.
Das zentrale Objekt für die Orchestrierung ist das `AppViewModel`. Es verbindet Aufnahme, Transkription und Formularlogik zu einer zusammenhängenden Pipeline.

<h2><strong>5. Audioaufnahme und Dateiverarbeitung</strong></h2>
Die Audioaufnahme ist über ein plattformspezifisches `AudioRecorder`-Interface abstrahiert. Dadurch bleibt die gemeinsame Logik unabhängig davon, wie Android oder iOS konkret mit Mikrofon und Dateisystem umgehen.
Der Ablauf ist dabei bewusst einfach gehalten:
- `RecordingViewModel` startet oder stoppt die Aufnahme
- `RecordingRepository` kapselt den Umgang mit Dateien
- nach dem Stoppen wird der Dateipfad an die Transkriptionsschicht weitergegeben
Zusätzlich ist ein Datei-Upload vorgesehen. Dadurch kann nicht nur Live-Audio, sondern auch eine bereits vorhandene Gesprächsdatei verarbeitet werden.

<h2><strong>6. Transkription: Cloud und lokal</strong></h2>
Ein Kernbestandteil des Projekts ist die Umwandlung von Sprache in Text.
Die Cloud-Variante wird über `OpenAiTranscriptionRepository` umgesetzt. Dabei passiert technisch Folgendes:
1. Die Audiodatei wird als Byte-Array gelesen.
2. Das Dateiformat wird heuristisch erkannt.
3. Es wird ein `multipart/form-data`-Request erzeugt.
4. Dieser Request wird an die OpenAI-Transkriptionsschnittstelle gesendet.
5. Als Modell wird `whisper-1` verwendet.
6. Die Antwort wird im Format `verbose_json` zurückgegeben.
7. Daraus wird ein `TranscriptionResponse` mit Text, Segmenten und Metadaten erzeugt.
Der Vorteil dieses Wegs ist die robuste Transkriptionsqualität. Der Nachteil ist die Abhängigkeit von Netzverbindung und API-Key.
Ich habe zusätzlich eine lokale Transkriptionsvariante integriert, weil gerade im mobilen Kontext folgende Aspekte relevant sind:
- Datenschutz
- Offline-Fähigkeit
- geringere Abhängigkeit von externen Diensten
- Demonstration nativer KI-Ausführung direkt auf dem Gerät
Dafür wird **whisper.cpp** genutzt.

<h2><strong>7. Android: lokale Transkription mit Whisper</strong></h2>
Auf Android ist die lokale Transkription in `OnDeviceWhisperTranscriptionRepository` umgesetzt.
Zuerst wird geprüft, ob ein gültiges Whisper-Modell vorhanden ist. Der Pfad wird über die App-Konfiguration eingelesen. Falls der primäre Pfad nicht funktioniert, wird zusätzlich ein Fallback gesucht.
Dadurch wird die Integration robuster, weil nicht nur ein einziger fester Pfad akzeptiert wird.
Whisper erwartet keine beliebige Audiodatei, sondern numerische Samples in einem geeigneten Format. Deshalb muss die Eingabedatei zunächst dekodiert werden.
Unter Android läuft das in mehreren Teilschritten ab:
1. Prüfen, ob die Datei bereits WAV/RIFF ist
2. Falls ja: PCM16 direkt lesen
3. Falls nein: komprimierte Formate mit `MediaExtractor` und `MediaCodec` dekodieren
4. Mehrkanal-Audio in Mono umwandeln
5. Auf 16 kHz resamplen
6. In ein `FloatArray` umwandeln
Dieser Schritt ist essenziell, weil das Sprachmodell intern auf Audiosamples arbeitet und nicht direkt auf Formaten wie `.m4a` oder `.mp3`.
Sobald die Audiodaten in geeigneter Form vorliegen, wird über `WhisperContext.createContextFromFile(...)` ein nativer Whisper-Kontext geladen. Ab diesem Moment arbeitet die App mit der nativen Modellbibliothek.
Danach wird `transcribeData(samples, printTimestamp = true)` aufgerufen. Whisper liefert nicht nur Fließtext, sondern segmentierte Textausgabe mit Zeitstempeln.
Diese rohe Modellantwort wird anschließend geparst. Daraus entstehen `TranscriptSegment`-Objekte mit:
- ID
- Text
- Startzeit
- Endzeit
- Sprecherkennung als Default-Wert
Am Ende werden die Segmenttexte zu einem Gesamttranskript zusammengeführt, das später für das Formular-Mapping verwendet wird.

<h2><strong>8. Whisper-Bridge und JNI auf Android</strong></h2>
Die Bibliotheken `whisper.cpp` und `llama.cpp` sind in C/C++ implementiert. Android-App-Code läuft dagegen in Kotlin auf der Android Runtime. Zwischen beiden Welten ist daher eine technische Brücke notwendig.
Diese Brücke ist **JNI (Java Native Interface)**.
JNI verbindet:
- Kotlin/Java-Code
- native C/C++-Funktionen
In `whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/jni.c` liegt die native Anbindung für Whisper. Diese JNI-Schicht stellt Funktionen bereit für:
- Modellinitialisierung aus Datei oder Assets
- Starten der vollständigen Transkription
- Zugriff auf erkannte Segmente
- Freigabe nativer Ressourcen
Fachlich bedeutet das:
- Kotlin ruft eine API auf,
- der Aufruf springt in nativen C-Code,
- dort wird `whisper_full(...)` ausgeführt,
- die Ergebnisse werden wieder in JVM-kompatible Typen zurückgegeben.
Die eigentliche "Whisper-Bridge" im Projekt ist also nicht nur ein einzelnes Objekt, sondern eine **mehrstufige Brücke**:
1. KMP-Repository auf Kotlin-Seite
2. Android-Bibliothek mit JVM-API
3. JNI-Schicht in C
4. whisper.cpp als native Modellruntime
Diese Kette ist notwendig, damit ein Audiofile aus der App bis in die native Inferenz gelangt und das Ergebnis wieder sauber zurückkommt.

<h2><strong>9. Formularautomatisierung: Vom Transkript zum Formular</strong></h2>
Nach der Transkription liegt zunächst nur unstrukturierter Text vor. Dieser Text muss in konkrete Formularfelder überführt werden.
Im Projekt ist das Formular bewusst kompakt gehalten und enthält drei Felder:
- `name`
- `problem`
- `learning`
Die Felddefinitionen werden zentral über `DefaultFormDefinitionProvider` bereitgestellt. Das ist wichtig, weil dadurch UI und Mapping dieselbe fachliche Struktur verwenden.

<h2><strong>10. Zwischenschritt: Segment- zu Sprecherblöcken</strong></h2>
Bevor die eigentliche Feldzuordnung erfolgt, werden Transkriptsegmente zu **SpeakerBlocks** zusammengefasst. Das geschieht in `TranscriptToFormMapper`.
Die Idee dahinter ist:
- aufeinanderfolgende Segmente desselben Sprechers zusammenführen,
- dadurch eine besser lesbare Struktur für die UI erzeugen,
- eine saubere Grundlage für das spätere Mapping schaffen.
Auch wenn die Sprechertrennung im aktuellen Stand noch vereinfacht ist, bildet diese Zwischenstruktur eine wichtige Grundlage für weitere Erweiterungen.

<h2><strong>11. Heuristische Formularautomatisierung</strong></h2>
Ein wichtiger Teil des Projekts ist ein lokaler Fallback ohne Cloud und ohne großes Modell. Dieser ist in `OnDeviceKeywordFormMappingRepository` umgesetzt.
Ich habe diesen Schritt bewusst eingebaut, weil eine App nicht ausschließlich von einem LLM abhängig sein sollte. Heuristiken helfen, wenn:
- kein lokales Modell vorhanden ist,
- eine native Inferenz fehlschlägt,
- keine Internetverbindung verfügbar ist,
- ein Modell keine brauchbare Antwort liefert.
Für jedes Feld werden einfache Regeln verwendet:
- Für `name` werden Muster wie „Ich heiße ...“ oder „Mein Name ist ...“ gesucht.
- Für `problem` werden Sätze mit Begriffen wie „Problem“, „Fehler“, „Konflikt“ oder „Herausforderung“ bevorzugt.
- Für `learning` werden Aussagen mit Begriffen wie „gelernt“, „mitgenommen“, „ich werde“ oder „verbessern“ priorisiert.
Diese Logik ist bewusst konservativ. Ziel ist kein vollständiges Sprachverständnis, sondern ein robuster und nachvollziehbarer Fallback.

<h2><strong>12. LLM-gestütztes Formular-Mapping</strong></h2>
Neben den Heuristiken gibt es auch LLM-basiertes Mapping.
In der Cloud-Variante wird ein OpenAI-Modell genutzt, um Inhalte aus dem Transkript strukturiert den Formularfeldern zuzuordnen. Dabei ist das Ziel nicht freier Fließtext, sondern eine möglichst klare JSON-ähnliche Feldantwort.
Spannender ist die lokale Variante. Dafür gibt es ein `OnDeviceLlmFormMappingRepository`.
Die Strategie ist mehrstufig:
1. Felddefinitionen vorbereiten
2. heuristische Kandidaten erzeugen
3. kompakten Prompt aus Transkript und Feldern bauen
4. lokales LLM aufrufen
5. JSON-Antwort parsen
6. LLM-Ergebnis mit Heuristik und Fallback mergen
Diese Pipeline ist bewusst defensiv konstruiert, weil LLMs nicht immer exakt das gewünschte Format liefern.

<h2><strong>13. Android: llama.cpp, JNI und lokale LLM-Inferenz</strong></h2>
Auf Android soll das Transkript lokal analysiert werden können, ohne auf eine Cloud angewiesen zu sein. Dafür wird `llama.cpp` eingebunden.
Im Projekt gibt es dafür zwei relevante Pfade:
- `LlamaCppOnDeviceLlmEngine`
- `DirectLlamaOnDeviceLlmEngine`
Die direkte Variante verwendet `DirectLlamaBridge.kt`. Dort sind `external`-Methoden definiert wie:
- `nativeInit`
- `nativeLoadModel`
- `nativeInfer`
- `nativeCancel`
- `nativeUnload`
- `nativeShutdown`
Diese Methoden werden auf der nativen Seite in `ai_chat.cpp` umgesetzt. Dort passieren die eigentlichen Low-Level-Schritte:
- Laden der nativen CPU-Backends
- Erzeugen von `llama_model` und `llama_context`
- Setzen von Inferenzparametern
- Prompt-Verarbeitung
- Token-Generierung
- Abbruch- und Cleanup-Logik
Dadurch entsteht wieder eine mehrstufige technische Brücke:
1. Kotlin-Repository
2. Kotlin-Bridge mit `external`-Methoden
3. JNI
4. native C++-Runtime von llama.cpp
Das Modell liegt nicht im Repository, sondern wird über `local.properties` konfiguriert. Dadurch lassen sich verschiedene GGUF-Modelle lokal testen, ohne große Binärdateien einzuchecken.
Zusätzlich sind Parameter wie diese steuerbar:
- `n_ctx`
- `temperature`
- `threadsMin`
- `threadsMax`
- Predict-Länge
- Timeout
Gerade auf mobilen Geräten ist diese Konfigurierbarkeit wichtig, weil Laufzeit und Speicherverbrauch stark davon abhängen.

<h2><strong>14. iOS: Swift-Bridge statt JNI</strong></h2>
Auf iOS kann JNI nicht verwendet werden. Deshalb ist dort ein anderer Integrationsweg notwendig.
Die gemeinsame KMP-Logik definiert Interfaces, die im iOS-App-Target in Swift implementiert werden. Die Swift-Klassen werden beim App-Start registriert und stehen danach der Kotlin-Seite zur Verfügung.
`IOSWhisperBridge.swift` implementiert die lokale Whisper-Anbindung. Diese Bridge übernimmt:
- Auflösen des Modellpfads
- Auflösen des Audiopfads
- Dekodierung des Audios in ein geeignetes Sample-Format
- Initialisierung von Whisper
- Starten der Transkription
- Rückmeldung von Fehlern an Kotlin
Besonders wichtig ist, dass die Bridge den Modellpfad über mehrere Stufen sucht:
1. direkter Parameter
2. Konfigurationseintrag
3. Modell im App-Bundle
4. passende Datei im Bundle
5. Fallback im Dokumentenverzeichnis
Auf der Kotlin-Seite wird eine Registry mit Lazy-Proxy verwendet. Das ist nötig, weil Swift-Bridges erst beim iOS-App-Start registriert werden, die KMP-Logik aber schon vorher initialisiert sein kann.
Dadurch wird die Abhängigkeit von der Initialisierungsreihenfolge sauber gelöst.
Auch für das On-Device-Mapping existiert eine Swift-Anbindung über `IOSLlamaBridge.swift`. Die Kotlin-Seite arbeitet hier wieder nur gegen ein Interface, wodurch die gemeinsame Logik nicht direkt von Swift-Code abhängt.

<h2><strong>15. Konfiguration des Projekts</strong></h2>
Ein wichtiger technischer Teil ist die Einbindung von API-Keys und Modellpfaden.
Auf Android werden Werte aus `local.properties` gelesen und per `buildConfigField` in die App eingebracht. Dazu gehören insbesondere:
- `OPENAI_API_KEY`
- `LLAMA_MODEL_PATH`
- `WHISPER_MODEL_PATH`
- Laufzeitparameter für llama.cpp
Unter iOS werden vergleichbare Werte über `xcconfig` und `Info.plist` verfügbar gemacht. So können beide Plattformen dieselbe fachliche Logik nutzen, obwohl die technische Einbindung unterschiedlich erfolgt.

<h2><strong>16. Warum Kotlin Multiplatform hier sinnvoll ist</strong></h2>
Für dieses Projekt war Kotlin Multiplatform besonders geeignet, weil sich die fachliche Logik sehr gut teilen lässt.
Geteilt werden unter anderem:
- Datenmodelle
- Use-Cases
- Formularlogik
- ViewModels
- ein großer Teil der Repository-Logik
- UI mit Compose Multiplatform
Nicht geteilt werden die wirklich systemnahen Teile:
- Audioaufnahme
- Dateizugriff
- JNI auf Android
- Swift-Bridges auf iOS
- native Modellinitialisierung
Gerade diese Aufteilung war für mich architektonisch sinnvoll, weil ich so eine gemeinsame App-Struktur behalten konnte, ohne die nativen Besonderheiten zu ignorieren.

<h2><strong>17. Zentrale technische Herausforderungen</strong></h2>
Während der Umsetzung gab es mehrere anspruchsvolle Punkte:
Audiodateien liegen in der Praxis nicht automatisch in einem Format vor, das ein Speech-to-Text-Modell direkt verarbeiten kann. Deshalb musste die Dekodierung und Sample-Konvertierung explizit umgesetzt werden.
Die Integration von Kotlin beziehungsweise Swift mit C/C++-Bibliotheken ist deutlich komplexer als klassische App-Entwicklung. Wichtig waren hier vor allem:
- saubere Initialisierung
- Ressourcenverwaltung
- Fehlerbehandlung
- kontrolliertes Freigeben nativer Kontexte
Das Mapping von Freitext auf strukturierte Felder ist fehleranfällig. Deshalb habe ich mehrere Sicherheitsebenen eingebaut:
- Heuristik
- LLM-Ausgabe im JSON-Stil
- Validierung der Antworten
- Merge- und Fallback-Strategien
Lokale Modellinferenz ist auf Smartphones ressourcenintensiv. Deshalb musste ich Parameter wie Kontextgröße, Thread-Anzahl, Vorhersagelänge und Timeouts gezielt steuerbar machen.

<h2><strong>18. Grenzen des aktuellen Prototyps</strong></h2>
Trotz funktionierender Kernlogik gibt es Grenzen:
- Das Formular ist fachlich noch klein gehalten.
- Die heuristische Extraktion ist nicht allgemein für jede Domäne geeignet.
- Die lokale Inferenz hängt stark von Modellgröße und Gerätehardware ab.
- Die Sprechertrennung ist bisher noch vereinfacht.
- Die Qualität des Mappings hängt stark von Modellwahl und Prompting ab.

<h2><strong>19. Mögliche Weiterentwicklungen</strong></h2>
Sinnvolle nächste Schritte wären aus meiner Sicht:
- größeres und realistischeres Formularschema
- bessere Sprechertrennung und Rollenmodellierung
- Evaluation mit Testdatensätzen
- systematischer Vergleich von Cloud- und On-Device-Qualität
- Export- oder Speicherfunktion für Ergebnisse
- verbessertes Prompting für lokale Modelle

<h2><strong>20. Zusammenfassung</strong></h2>
Zusammenfassend ist dieses Projekt eine **Kotlin-Multiplatform-Anwendung zur automatisierten Verarbeitung gesprochener Gespräche in strukturierte Formulardaten**.
Der technische Kern ist eine mehrstufige Pipeline:
1. Audioaufnahme oder Dateiauswahl
2. Transkription über Cloud oder whisper.cpp
3. Aufbereitung des Transkripts
4. Formular-Mapping per Heuristik, OpenAI oder llama.cpp
5. Darstellung als vorausgefülltes Formular
Besonders hervorzuheben sind aus meiner Sicht:
- die plattformübergreifende Architektur mit Kotlin Multiplatform,
- die Android-Integration nativer Bibliotheken über JNI,
- die iOS-Integration über Swift-Bridges,
- und die Kombination aus heuristischer und LLM-basierter Formularautomatisierung.
Damit zeigt das Projekt sehr gut, wie moderne KI-Funktionalität praktisch in eine mobile Anwendung integriert werden kann.
