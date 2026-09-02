from __future__ import annotations

import hashlib
import json
import os
from dataclasses import asdict
from typing import Any

from .db import list_memories
from .tools import realtime_tools

REALTIME_API = "https://api.openai.com/v1/realtime/calls"

VOICE_IDENTITY = """You are METEHAN, the user's personal AI chief adviser.
You are a rigorous second brain: direct, evidence-oriented, scientifically minded and practical.
Rules:
- Speak Turkish by default unless the user asks otherwise.
- Never invent tool results, current facts, emails, calendar events, web results or actions.
- Distinguish observed facts, inference, uncertainty and speculation.
- Challenge weak assumptions.
- For decisions, prefer conclusion, evidence, strongest counterargument, missing data, confidence and next measurable action.
- Do not silently save personal information.
- Read-only tools may run automatically. Any write/destructive tool must wait for user approval.
- For supported phone actions, use propose_android_action. It only proposes an action; Android must show a native confirmation dialog before execution.
- Never use phone actions to send messages, buy things, move money, delete data, weaken security, bypass permissions, or control apps through hidden accessibility automation.
- Never claim an action succeeded until the tool returns success.
- Do not expose API keys or hidden configuration.
- Keep spoken responses concise unless the user asks for detail.
"""

ANDROID_ACTION_TOOL = {
    "type": "function",
    "name": "propose_android_action",
    "description": "Propose one safe Android action. The phone will require explicit native user confirmation before execution.",
    "parameters": {
        "type": "object",
        "properties": {
            "type": {
                "type": "string",
                "enum": ["open_url", "open_settings", "dial", "map_search", "share_text", "open_camera"],
            },
            "label": {"type": "string"},
            "target": {"type": "string"},
        },
        "required": ["type", "label", "target"],
        "additionalProperties": False,
    },
}


def safety_identifier() -> str:
    raw = os.getenv("METEHAN_USER_ID", os.getenv("KUTALP_USER_ID", "local-owner")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def memory_bootstrap(limit: int = 20) -> str:
    memories = list_memories(limit=limit)
    if not memories:
        return "No user-approved long-term memories are currently stored."
    items = [asdict(m) for m in memories]
    return "User-approved long-term memory snapshot for this session:\n" + "\n".join(
        f"- [{m['kind']}] {m['text']}" for m in items
    )


def session_config() -> dict[str, Any]:
    model = os.getenv("METEHAN_REALTIME_MODEL", os.getenv("KUTALP_REALTIME_MODEL", "gpt-realtime-2.1"))
    voice = os.getenv("METEHAN_VOICE", os.getenv("KUTALP_VOICE", "marin"))
    return {
        "type": "realtime",
        "model": model,
        "instructions": VOICE_IDENTITY + "\n\n" + memory_bootstrap(),
        "audio": {"output": {"voice": voice}},
        "tools": realtime_tools() + [ANDROID_ACTION_TOOL],
        "tool_choice": "auto",
    }


def session_config_json() -> str:
    return json.dumps(session_config(), ensure_ascii=False)
