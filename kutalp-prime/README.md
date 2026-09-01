# KUTALP PRIME V0.3

KUTALP PRIME is a personal AI chief-adviser architecture: realtime voice, scientific decision support, explicit long-term memory, permissioned tools and a measurable prediction ledger.

## What works now

- **Realtime speech-to-speech** in a browser/mobile PWA over WebRTC
- **Text chief adviser** using the Responses API
- **Independent Red Team** critique for text decisions
- **Local SQLite memory** controlled by the user
- **Tool permission system** with approval cards for writes/destructive actions
- **Task ledger**
- **Prediction calibration ledger** with Brier score
- **Tool audit log**
- **Installable PWA** for Android/home-screen use
- **API key kept server-side**

## Install

```bash
cd kutalp-prime
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# Linux/macOS/Termux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Edit `.env`:

```env
OPENAI_API_KEY=YOUR_KEY
KUTALP_MODEL=gpt-5.6-terra
KUTALP_REALTIME_MODEL=gpt-realtime-2.1
KUTALP_VOICE=marin
```

## Run the new voice/PWA interface

```bash
python run.py
```

Open:

```text
http://127.0.0.1:8765
```

If the server runs on a PC and you want to open KUTALP from a phone on the same private Wi-Fi:

```env
KUTALP_HOST=0.0.0.0
KUTALP_ACCESS_TOKEN=choose-a-long-random-secret
```

Then open the PC's LAN IP on port 8765 and enter the same local access token in KUTALP's System tab. **That plain-HTTP LAN URL is suitable for the dashboard, but phone microphone/PWA features generally require a secure context (HTTPS) when the origin is not localhost.** For immediate Android voice use, the easiest path is to run KUTALP directly in Termux and open `http://127.0.0.1:8765` on the same phone. If KUTALP runs on another machine, put trusted HTTPS in front of it before expecting microphone/PWA behavior to work reliably.

## Legacy text interface

The V0.1 Streamlit UI remains available:

```bash
streamlit run app.py
```

## Important limitation

A PWA can do excellent live voice while it is open, but it is **not a trustworthy always-on background wake-word service on Android**. A native Android foreground service with a local wake-word model is planned for the native phase. KUTALP does not pretend otherwise.

## Architecture

Read `ARCHITECTURE.md`, `SECURITY.md`, `HARDWARE_ROADMAP.md`, `CONNECTORS.md` and `ROADMAP.md`.

**Core principle:** KUTALP is scored by accuracy, calibration, prevented mistakes, completed work and time saved—not by how impressive its conversation sounds.
