# KUTALP PRIME Hardware Roadmap

Goal: a personal AI system that remains useful on a phone but can grow into a powerful local workstation and wearable interface.

## Phase A — now

**Existing Android phone + KUTALP PWA**

- microphone
- speaker / Bluetooth headset
- mobile browser
- local or LAN server

This proves the cognitive workflow before buying hardware.

## Phase B — personal AI workstation

Recommended design targets rather than a fixed shopping list:

- modern multi-core CPU
- **64 GB RAM minimum; 128 GB preferred** for local models and multiple services
- **GPU with 24 GB+ VRAM** as the practical serious starting point for local inference
- 2 TB+ high-endurance NVMe for models/data
- second storage device for encrypted backup
- UPS so memory/databases are not corrupted by power loss
- wired Ethernet for stable low-latency service

For much larger local models, multiple GPUs or a professional accelerator class may eventually be justified, but software usefulness should be proven before spending heavily.

## Phase C — wearable interface

- open-ear or bone-conduction audio
- high-quality microphone array
- smartwatch for silent approvals
- camera glasses only with visible capture controls and privacy rules
- optional small edge computer for sensor preprocessing

## Phase D — native always-available assistant

A native Android service is required for a reliable background wake word. Browser PWAs are intentionally not treated as an always-listening daemon.

Target pipeline:

`wake-word engine -> local voice gate -> Realtime session -> tool router -> approval on watch/phone`

Wake-word detection should run locally so raw ambient audio does not need to be continuously uploaded.

## Phase E — local-first intelligence

- local speech recognition fallback
- local TTS fallback
- local LLM for private/offline tasks
- cloud frontier model only when higher reasoning is needed
- automatic routing by privacy, cost and complexity

## Design principle

Buy compute to remove a measured bottleneck, not because large hardware sounds impressive. KUTALP's scorecard is accuracy, latency, privacy, uptime, decision quality and time saved.
