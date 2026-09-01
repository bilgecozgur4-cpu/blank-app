# KUTALP PRIME Architecture — V0.3

KUTALP PRIME is designed as a personal cognitive system, not a single chatbot.

## 1. Brain layer

- **Text reasoning:** OpenAI Responses API (`KUTALP_MODEL`, default `gpt-5.6-terra`)
- **Realtime speech:** OpenAI Realtime API over WebRTC (`gpt-realtime-2.1` by default)
- **Red Team:** separate critique pass for text decisions
- **Scientific ledger:** explicit predictions with probabilities and later Brier-score evaluation

## 2. Memory layer

SQLite stores only local data:

- user-approved long-term memory
- decisions and Red Team critiques
- tasks
- falsifiable predictions and outcomes
- tool audit log

The system does not silently promote conversation text into long-term memory.

## 3. Tool layer

Tools are divided by permission:

### Read-only / auto-run
- `get_system_status`
- `search_memory`
- `list_tasks`
- `list_predictions`

### Write / destructive / approval required
- `save_memory`
- `delete_memory`
- `create_task`
- `complete_task`
- `record_prediction`
- `resolve_prediction`

The model can request an action, but the browser UI is the approval gate.

## 4. Client layer

The V0.3 PWA provides:

- live speech-to-speech session
- microphone mute control
- typed chat fallback
- approval cards for tool actions
- memory/task/science dashboards
- installable mobile PWA shell

The API key never appears in browser JavaScript.

## 5. Expansion interfaces

V0.3 intentionally separates the following future adapters:

- Gmail / Calendar OAuth connector
- GitHub tool adapter
- web research service
- computer-use / local shell sandbox
- camera and wearable vision
- local GPU inference
- native Android wake-word service
- multi-agent orchestration

The core rule is stable: read-only tools may be automatic; state-changing actions require an explicit permission policy.
