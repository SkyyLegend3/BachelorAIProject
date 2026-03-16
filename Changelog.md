# Changelog

## Stand: 2026-03-16

## Android: On-Device vs. Cloud Pipeline
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


