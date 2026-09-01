from __future__ import annotations

import json
import os
from typing import Iterable

import httpx

from .db import Memory

API_URL = "https://api.openai.com/v1/responses"

CORE_IDENTITY = """You are KUTALP PRIME, a personal AI chief adviser.
Your job is not to flatter the user. Your job is to increase decision quality, learning speed, execution quality and long-term outcomes.

Operating principles:
- Be direct, evidence-oriented and explicit about uncertainty.
- Separate facts, inference and speculation.
- If evidence is insufficient, say what is missing and how to test it.
- Challenge weak assumptions instead of agreeing automatically.
- Prefer measurable experiments over vague advice.
- Protect user agency: recommendations are recommendations; the user decides.
- Never claim you performed an external action unless a tool actually did it.
- For high-stakes domains, flag uncertainty and encourage appropriate professional verification.

When the user asks for analysis or a decision, naturally include when useful:
1) conclusion,
2) evidence/reasoning basis,
3) strongest counterargument,
4) missing data,
5) confidence estimate,
6) next measurable action.
"""

SCIENCE_ADDON = """Scientific mode is ON. Use hypothesis-driven reasoning. State observations, hypotheses, evidence quality, alternative explanations, uncertainty and a falsifiable next test when applicable."""

RED_TEAM_PROMPT = """Act as KUTALP PRIME's independent Red Team. Critique the proposed answer aggressively but fairly. Look for unsupported assumptions, confirmation bias, hidden costs, missing evidence, false precision and safer/better alternatives. Return only the critique and what would change the conclusion."""


def api_configured() -> bool:
    return bool(os.getenv("OPENAI_API_KEY", "").strip())


def _extract_text(data: dict) -> str:
    texts: list[str] = []
    for item in data.get("output", []) or []:
        for content in item.get("content", []) or []:
            if content.get("type") in {"output_text", "text"} and content.get("text"):
                texts.append(content["text"])
    if texts:
        return "\n".join(texts).strip()
    if isinstance(data.get("output_text"), str):
        return data["output_text"].strip()
    return json.dumps(data, ensure_ascii=False, indent=2)


def _memory_context(memories: Iterable[Memory]) -> str:
    items = list(memories)
    if not items:
        return "No relevant long-term memory is available."
    return "Relevant long-term memory (user-controlled):\n" + "\n".join(
        f"- [{m.kind}] {m.text}" for m in items
    )


def _call_model(instructions: str, user_input: str, timeout: float = 90.0) -> str:
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if not key:
        raise RuntimeError("OPENAI_API_KEY is not configured")
    model = os.getenv("KUTALP_MODEL", "gpt-5.6-terra")
    payload = {
        "model": model,
        "instructions": instructions,
        "input": user_input,
    }
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    with httpx.Client(timeout=timeout) as client:
        resp = client.post(API_URL, headers=headers, json=payload)
        resp.raise_for_status()
        return _extract_text(resp.json())


def offline_response(user_input: str, memories: Iterable[Memory]) -> str:
    memory_count = len(list(memories))
    return (
        "KUTALP PRIME çekirdeği çalışıyor fakat henüz bir model API anahtarı bağlı değil. "
        f"Yerel hafızadan {memory_count} ilgili kayıt buldum.\n\n"
        "Şu an hafıza ekleme/silme, karar kaydı ve sistem testleri çalışır. "
        "Tam akıl yürütme için OPENAI_API_KEY ekle; ileriki sürümde yerel GPU modeli de bağlanacak."
    )


def answer(user_input: str, memories: Iterable[Memory], scientific: bool = True) -> str:
    memories = list(memories)
    if not api_configured():
        return offline_response(user_input, memories)
    instructions = CORE_IDENTITY
    if scientific:
        instructions += "\n\n" + SCIENCE_ADDON
    instructions += "\n\n" + _memory_context(memories)
    return _call_model(instructions, user_input)


def red_team(user_input: str, proposed_answer: str, memories: Iterable[Memory]) -> str:
    if not api_configured():
        return "Red Team modeli için API anahtarı gerekli."
    context = _memory_context(memories)
    prompt = f"USER REQUEST:\n{user_input}\n\nPROPOSED ANSWER:\n{proposed_answer}"
    return _call_model(RED_TEAM_PROMPT + "\n\n" + context, prompt)
