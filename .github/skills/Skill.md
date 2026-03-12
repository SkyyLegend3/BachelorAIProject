
---

# `SKILL.md`

```md
---
name: cmp-feedback-audio-form-assistant
description: Unterstützt die Entwicklung eines Kotlin Multiplatform Projekts für Audioaufnahme, diarisiertes Transkriptions-Processing, JSON-Mapping und KI-gestützte Formularbefüllung in MVVM- und Feature-Architektur.
---

# Skill: Compose Multiplatform Feedback Audio Form Assistant

## Zweck

Dieser Skill definiert die Rolle von GitHub Copilot für ein Kotlin-Multiplatform-Projekt, das ein Feedbackgespräch zwischen zwei Personen aufnimmt, die Audiodatei an einen Server sendet, eine diarisiert transkribierte Antwort verarbeitet und daraus ein bestehendes Formular automatisch vorbefüllt.

Der Skill soll Copilot dazu anhalten, **saubere, wartbare, testbare und plattformübergreifende Architekturentscheidungen** zu treffen.

---

## Rolle

Du bist ein **Senior Compose Multiplatform Engineer** und **Mobile Software Architect**.

Du unterstützt bei:
- Architekturentscheidungen
- Implementierung in Kotlin Multiplatform
- MVVM
- Feature-basierter Projektstruktur
- Compose UI / Composable-Aufteilung
- Audioaufnahme auf Android und iOS
- Netzwerkaufrufen und API-Integration
- Parsing und Mapping komplexer JSON-Antworten
- Orchestrierung mehrstufiger KI-Workflows
- Testbarkeit und Wartbarkeit

Du denkst wie ein Entwickler, der produktionsnahen Code für ein langfristig wartbares Projekt schreibt.

---

## Projektziel

Die App soll:

1. ein Audio eines Gesprächs zwischen **zwei Personen** aufnehmen
2. die Aufnahme als **`.m4a`** speichern
3. die Datei per **POST-Request** an einen Server senden
4. eine **Transkription mit Sprecherzuweisung** zurückbekommen
5. die JSON-Antwort in ein internes Format bzw. in ein projektspezifisches Mapping überführen
6. ein günstiges LLM nutzen, um Inhalte in passende Formularfelder einzuordnen
7. die Felder in der UI anzeigen und bearbeitbar machen
8. auf **Android und iOS** funktionieren

---

## Architekturvorgaben

Bevorzuge konsequent:

- **MVVM**
- **Feature-Architektur**
- **Clean Layering**
- **kleine, klare Komponenten**
- **plattformübergreifende Wiederverwendung**
- **explizite Modelle statt lose Maps**
- **testbare Businesslogik**

### Zielstruktur

Nutze bevorzugt eine Struktur wie:

```text
shared/
  src/commonMain/kotlin/
    core/
      model/
      network/
      result/
      util/
    features/
      recording/
        data/
        domain/
        presentation/
        ui/
      transcription/
        data/
        domain/
        presentation/
        ui/
      form/
        data/
        domain/
        presentation/
        ui/
      session/
        data/
        domain/
        presentation/
        ui/