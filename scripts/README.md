# Scripts

## Android llama.cpp Source Update (gemma4)

Der Android-Native-Build nutzt bevorzugt `third_party/llama.cpp`.
Mit diesem Skript wird ein aktueller upstream-Stand (inkl. gemma4-Support) geklont/aktualisiert:

```bash
./scripts/update_llama_cpp_android.sh
```

## Android On-Device LLM Timing

Dieses Projekt loggt Inferenzzeiten bereits zur Laufzeit ueber:
- `DEBUG DirectLlamaOnDeviceLlmEngine: start inference`
- `DEBUG DirectLlamaOnDeviceLlmEngine: done in <ms>ms`

Mit `parse_llm_timings.py` kannst du diese Zeiten als Kurzreport auswerten.

### 1) Logcat mitschreiben

```bash
adb logcat -c
adb logcat | tee /tmp/llm_measurement.log
```

Dann in der App den On-Device-Mapping-Flow mehrfach ausfuehren und danach `Ctrl+C`.

### 2) Report erzeugen

```bash
python3 scripts/parse_llm_timings.py /tmp/llm_measurement.log
```

### Empfehlung

- Mindestens 5 Runs erfassen (1x Cold-Start, danach Warm-Runs).
- Fuer Vergleiche immer gleiches Geraet, gleiches Modell und aehnlich lange Transkripte nutzen.

