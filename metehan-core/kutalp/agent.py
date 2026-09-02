from __future__ import annotations

import json
import os
from typing import Any, Iterable

import httpx

from .db import Memory
from .realtime import safety_identifier

API_URL = "https://api.openai.com/v1/responses"
ACTION_TYPES = (
    "none",
    "open_url",
    "open_settings",
    "dial",
    "map_search",
    "share_text",
    "open_camera",
)

ACTION_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "reply": {"type": "string"},
        "confidence": {"type": "number", "minimum": 0, "maximum": 1},
        "needs_confirmation": {"type": "boolean"},
        "action": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "type": {"type": "string", "enum": list(ACTION_TYPES)},
                "label": {"type": "string"},
                "target": {"type": "string"},
            },
            "required": ["type", "label", "target"],
        },
    },
    "required": ["reply", "confidence", "needs_confirmation", "action"],
}

AGENT_INSTRUCTIONS = """You are METEHAN's Android action planner.
Answer the user's request helpfully, but never pretend an Android action has already happened.
You may propose at most one device action and only from this allow-list:
- none: no device action
- open_url: target must be an http/https URL
- open_settings: target must be one of wifi, bluetooth, location, general
- dial: target is a phone number; Android will only open the dialer, never place the call automatically
- map_search: target is a place or search phrase
- share_text: target is text to put into Android's share chooser
- open_camera: target must be empty

Rules:
- Use none unless a device action materially helps satisfy the user's request.
- Every non-none action is only a proposal. The Android client will ask the user for confirmation before execution.
- Never propose hidden automation, accessibility control, background surveillance, sending messages, purchases, financial transactions, deleting data, changing security settings, or bypassing permissions.
- Device context is informational only. Do not infer precise location from network/device data.
- Keep reply concise and in Turkish unless the user clearly asks for another language.
"""


def _memory_context(memories: Iterable[Memory]) -> str:
    items = list(memories)
    if not items:
        return "No relevant long-term memory."
    return "Relevant user-controlled memory:\n" + "\n".join(
        f"- [{m.kind}] {m.text}" for m in items
    )


def _extract_text(data: dict[str, Any]) -> str:
    texts: list[str] = []
    for item in data.get("output", []) or []:
        for content in item.get("content", []) or []:
            if content.get("type") in {"output_text", "text"} and content.get("text"):
                texts.append(str(content["text"]))
    if texts:
        return "\n".join(texts).strip()
    value = data.get("output_text")
    return value.strip() if isinstance(value, str) else ""


def _normalize_plan(plan: dict[str, Any]) -> dict[str, Any]:
    action = plan.get("action") if isinstance(plan.get("action"), dict) else {}
    action_type = str(action.get("type", "none"))
    if action_type not in ACTION_TYPES:
        action_type = "none"
    target = str(action.get("target", ""))[:4000]
    label = str(action.get("label", ""))[:240]
    if action_type == "none":
        target = ""
        label = ""
    return {
        "reply": str(plan.get("reply", ""))[:12000],
        "confidence": max(0.0, min(float(plan.get("confidence", 0.5)), 1.0)),
        "needs_confirmation": action_type != "none",
        "action": {"type": action_type, "label": label, "target": target},
    }


def offline_plan(message: str) -> dict[str, Any]:
    return {
        "reply": "METEHAN çekirdeği açık, fakat akıllı eylem planlamak için OPENAI_API_KEY bağlı değil.",
        "confidence": 0.2,
        "needs_confirmation": False,
        "action": {"type": "none", "label": "", "target": ""},
    }


def plan_command(
    message: str,
    device_context: dict[str, Any] | None,
    memories: Iterable[Memory],
) -> dict[str, Any]:
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if not key:
        return offline_plan(message)

    model = os.getenv("METEHAN_MODEL", os.getenv("KUTALP_MODEL", "gpt-5.6-terra"))
    context = json.dumps(device_context or {}, ensure_ascii=False, sort_keys=True)[:10000]
    instructions = AGENT_INSTRUCTIONS + "\n\n" + _memory_context(memories) + "\n\nDEVICE CONTEXT:\n" + context
    payload = {
        "model": model,
        "instructions": instructions,
        "input": message,
        "store": False,
        "safety_identifier": safety_identifier(),
        "text": {
            "format": {
                "type": "json_schema",
                "name": "metehan_android_plan",
                "description": "One safe Android action proposal plus a user-facing reply.",
                "strict": True,
                "schema": ACTION_SCHEMA,
            }
        },
    }
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    with httpx.Client(timeout=90.0) as client:
        response = client.post(API_URL, headers=headers, json=payload)
        response.raise_for_status()
        raw = _extract_text(response.json())
    if not raw:
        raise RuntimeError("Model returned no structured action plan")
    return _normalize_plan(json.loads(raw))
