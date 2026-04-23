#!/usr/bin/env python3
"""Parst Android-Logcat-Ausgaben fuer On-Device-LLM-Laufzeiten.

Erwartete Logzeilen (werden bereits im Projekt ausgegeben):
- DEBUG DirectLlamaOnDeviceLlmEngine: done in 1234ms
- DEBUG DirectLlamaOnDeviceLlmEngine: model ready at ...

Beispiel:
    python3 scripts/parse_llm_timings.py /tmp/llm.log
"""

from __future__ import annotations

import argparse
import re
import statistics
from pathlib import Path

INFERENCE_RE = re.compile(r"DirectLlamaOnDeviceLlmEngine: done in (\d+)ms")
MODEL_READY_RE = re.compile(r"DirectLlamaOnDeviceLlmEngine: model ready at ")
TIMEOUT_RE = re.compile(r"DirectLlamaOnDeviceLlmEngine: recovery reason=timeout")


def percentile(values: list[int], p: float) -> float:
    if not values:
        raise ValueError("values darf nicht leer sein")
    if len(values) == 1:
        return float(values[0])
    rank = (len(values) - 1) * p
    low = int(rank)
    high = min(low + 1, len(values) - 1)
    weight = rank - low
    return values[low] * (1.0 - weight) + values[high] * weight


def parse_log(text: str) -> tuple[list[int], int, int]:
    durations = [int(match.group(1)) for match in INFERENCE_RE.finditer(text)]
    durations.sort()
    model_ready_count = len(MODEL_READY_RE.findall(text))
    timeout_recoveries = len(TIMEOUT_RE.findall(text))
    return durations, model_ready_count, timeout_recoveries


def format_report(durations: list[int], model_ready_count: int, timeout_recoveries: int) -> str:
    lines = []
    lines.append("LLM Timing Report")
    lines.append(f"- inference_count: {len(durations)}")
    lines.append(f"- model_ready_count: {model_ready_count}")
    lines.append(f"- timeout_recoveries: {timeout_recoveries}")

    if durations:
        lines.append(f"- min_ms: {durations[0]}")
        lines.append(f"- p50_ms: {percentile(durations, 0.50):.1f}")
        lines.append(f"- p95_ms: {percentile(durations, 0.95):.1f}")
        lines.append(f"- max_ms: {durations[-1]}")
        lines.append(f"- avg_ms: {statistics.fmean(durations):.1f}")
    else:
        lines.append("- warning: keine Inferenz-Zeilen gefunden")

    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Parst On-Device-LLM Timings aus Logcat-Dateien")
    parser.add_argument("logfile", type=Path, help="Pfad zur Logdatei")
    args = parser.parse_args()

    if not args.logfile.exists():
        raise SystemExit(f"Datei nicht gefunden: {args.logfile}")

    text = args.logfile.read_text(encoding="utf-8", errors="replace")
    durations, model_ready_count, timeout_recoveries = parse_log(text)
    print(format_report(durations, model_ready_count, timeout_recoveries))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

