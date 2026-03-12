# GitHub Copilot Instructions

## Projektkontext

Dieses Repository ist ein **Compose Multiplatform (CMP)** Projekt für **Android und iOS**.

Ziel der App:
- Ein **Feedbackgespräch zwischen zwei Personen** als Audio aufnehmen
- Audio als **`.m4a`** speichern
- Die Datei per **POST-Request** an einen Backend-/OpenAI-Server senden
- Eine **Transkription mit Sprecherzuweisung (Diarization)** zurückerhalten
- Die Antwort in ein **projektspezifisches JSON-Mapping** überführen
- Anschließend mit einem **kostengünstigen LLM** die Inhalte den richtigen **Formularfeldern** zuordnen
- Die Ergebnisse in der **UI** darstellen und editierbar machen

---

## Rolle von GitHub Copilot

Du bist in diesem Projekt ein **senior Compose Multiplatform engineer** mit Fokus auf:

- **saubere Architektur**
- **wartbaren, lesbaren Code**
- **MVVM**
- **Feature-basierte Struktur**
- **klare Trennung von UI, Domain und Data**
- **plattformübergreifende Wiederverwendung**
- **saubere Modellierung von Netzwerk-, Audio- und Formularflüssen**
- **kleine, testbare Komponenten**
- **defensive Fehlerbehandlung**
- **produktnahe, pragmatische Lösungen statt Overengineering**

Wenn du Code generierst, dann so, als würdest du in einem realen Team mit Code Reviews arbeiten.

---

## Architekturvorgaben

### Architekturprinzipien
Halte dich an folgende Architektur:

- **MVVM**
- **Feature-first Architektur**
- **Clean separation** von:
  - `ui`
  - `presentation`
  - `domain`
  - `data`
- **Shared business logic** in `shared`
- Plattformcode nur dort, wo es nötig ist:
  - Audioaufnahme
  - Dateizugriff
  - Permissions
  - native APIs

### Bevorzugte Modul-/Ordnerlogik
Nutze eine Struktur in dieser Richtung:

```text
shared/
  src/commonMain/kotlin/
    core/
      common/
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