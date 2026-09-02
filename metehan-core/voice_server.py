from __future__ import annotations

import asyncio
import base64
import json
import os
from dataclasses import asdict
from pathlib import Path
from typing import Any

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from kutalp.brain import answer, api_configured, red_team
from kutalp.db import add_decision, add_memory, delete_memory, init_db, list_memories, list_predictions, list_tasks, prediction_metrics, relevant_memories
from kutalp.realtime import REALTIME_API, safety_identifier, session_config_json
from kutalp.tools import execute_tool, tool_metadata

load_dotenv()
init_db()
BASE_DIR = Path(__file__).resolve().parent
WEB_DIR = BASE_DIR / "web"
app = FastAPI(title="METEHAN", version="0.4")
app.mount("/static", StaticFiles(directory=WEB_DIR), name="static")


def _env(primary: str, legacy: str, default: str = "") -> str:
    return os.getenv(primary, os.getenv(legacy, default))


def _check_access(request: Request) -> None:
    expected = _env("METEHAN_ACCESS_TOKEN", "KUTALP_ACCESS_TOKEN").strip()
    if not expected:
        return
    supplied = request.headers.get("X-Metehan-Token", "").strip() or request.headers.get("X-Kutalp-Token", "").strip()
    if supplied != expected:
        raise HTTPException(status_code=401, detail="METEHAN access token required")


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=20000)
    scientific: bool = True
    use_red_team: bool = True


class ToolRequest(BaseModel):
    name: str
    arguments: dict[str, Any] | str = Field(default_factory=dict)
    approved: bool = False


class MemoryRequest(BaseModel):
    text: str = Field(min_length=1, max_length=20000)
    kind: str = "general"


class VisionRequest(BaseModel):
    image_base64: str = Field(min_length=16, max_length=12_000_000)
    mime_type: str = "image/jpeg"
    prompt: str = Field(default="Görüntüyü dikkatle incele. Gözlem, çıkarım ve belirsizliği birbirinden ayır; önemli ayrıntıları ve gerekiyorsa sonraki doğrulama adımını Türkçe açıkla.", min_length=1, max_length=4000)


@app.get("/")
async def index() -> FileResponse:
    return FileResponse(WEB_DIR / "index.html")


@app.get("/manifest.webmanifest")
async def manifest() -> FileResponse:
    return FileResponse(WEB_DIR / "manifest.webmanifest", media_type="application/manifest+json")


@app.get("/sw.js")
async def service_worker() -> FileResponse:
    return FileResponse(WEB_DIR / "sw.js", media_type="application/javascript")


@app.get("/health")
async def health() -> dict[str, Any]:
    return {"ok": True, "service": "METEHAN", "version": "0.4"}


@app.get("/api/config")
async def config(request: Request) -> dict[str, Any]:
    _check_access(request)
    return {
        "version": "0.4", "service": "METEHAN", "api_configured": api_configured(),
        "text_model": _env("METEHAN_MODEL", "KUTALP_MODEL", "gpt-5.6-terra"),
        "realtime_model": _env("METEHAN_REALTIME_MODEL", "KUTALP_REALTIME_MODEL", "gpt-realtime-2.1"),
        "vision_model": _env("METEHAN_VISION_MODEL", "KUTALP_MODEL", "gpt-5.6-terra"),
        "voice": _env("METEHAN_VOICE", "KUTALP_VOICE", "marin"),
        "access_token_required": bool(_env("METEHAN_ACCESS_TOKEN", "KUTALP_ACCESS_TOKEN").strip()),
        "tools": tool_metadata(), "prediction_metrics": prediction_metrics(),
    }


@app.post("/session")
async def create_realtime_session(request: Request) -> Response:
    _check_access(request)
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if not key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not configured")
    sdp = (await request.body()).decode("utf-8", errors="strict")
    if not sdp.strip():
        raise HTTPException(status_code=400, detail="Empty SDP offer")
    files = {"sdp": (None, sdp), "session": (None, session_config_json())}
    headers = {"Authorization": f"Bearer {key}", "OpenAI-Safety-Identifier": safety_identifier()}
    async with httpx.AsyncClient(timeout=45.0) as client:
        upstream = await client.post(REALTIME_API, headers=headers, files=files)
    if upstream.status_code >= 400:
        raise HTTPException(status_code=upstream.status_code, detail=upstream.text[:2000])
    return Response(content=upstream.text, media_type="application/sdp")


@app.post("/api/chat")
async def chat(req: ChatRequest, request: Request) -> dict[str, Any]:
    _check_access(request)
    memories = relevant_memories(req.message)
    try:
        reply = await asyncio.to_thread(answer, req.message, memories, req.scientific)
        critique = await asyncio.to_thread(red_team, req.message, reply, memories) if req.use_red_team and api_configured() else None
        await asyncio.to_thread(add_decision, req.message, reply, critique)
        return {"ok": True, "reply": reply, "red_team": critique}
    except Exception as exc:
        return JSONResponse(status_code=500, content={"ok": False, "error": f"{type(exc).__name__}: {exc}"})


def _extract_response_text(data: dict[str, Any]) -> str:
    out: list[str] = []
    for item in data.get("output", []) or []:
        for content in item.get("content", []) or []:
            if content.get("type") in {"output_text", "text"} and content.get("text"):
                out.append(content["text"])
    if out:
        return "\n".join(out).strip()
    value = data.get("output_text")
    return value.strip() if isinstance(value, str) else json.dumps(data, ensure_ascii=False)


@app.post("/api/vision")
async def vision(req: VisionRequest, request: Request) -> dict[str, Any]:
    _check_access(request)
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if not key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not configured")
    if req.mime_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise HTTPException(status_code=400, detail="Unsupported image type")
    try:
        raw = base64.b64decode(req.image_base64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Invalid base64 image: {exc}") from exc
    if len(raw) > 8 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Image exceeds METEHAN 8 MiB capture limit")
    payload = {
        "model": _env("METEHAN_VISION_MODEL", "KUTALP_MODEL", "gpt-5.6-terra"),
        "instructions": "You are METEHAN's visual analyst. Be evidence-oriented. Separate direct visual observations from inference. Never claim certainty about details that the image cannot establish.",
        "input": [{"role": "user", "content": [{"type": "input_text", "text": req.prompt}, {"type": "input_image", "image_url": f"data:{req.mime_type};base64,{req.image_base64}"}]}],
        "safety_identifier": safety_identifier(),
    }
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    async with httpx.AsyncClient(timeout=90.0) as client:
        upstream = await client.post("https://api.openai.com/v1/responses", headers=headers, json=payload)
    if upstream.status_code >= 400:
        raise HTTPException(status_code=upstream.status_code, detail=upstream.text[:2000])
    return {"ok": True, "analysis": _extract_response_text(upstream.json()), "bytes": len(raw)}


@app.post("/api/tools/execute")
async def run_tool(req: ToolRequest, request: Request) -> dict[str, Any]:
    _check_access(request)
    args = req.arguments
    if isinstance(args, str):
        try: args = json.loads(args) if args.strip() else {}
        except json.JSONDecodeError as exc: return {"ok": False, "error": f"Invalid tool JSON: {exc}"}
    return await asyncio.to_thread(execute_tool, req.name, args, req.approved)


@app.get("/api/memories")
async def memories(request: Request) -> list[dict[str, Any]]:
    _check_access(request); return [asdict(m) for m in list_memories(limit=200)]

@app.post("/api/memories")
async def create_memory(req: MemoryRequest, request: Request) -> dict[str, Any]:
    _check_access(request); mid = await asyncio.to_thread(add_memory, req.text, req.kind); return {"ok": True, "memory_id": mid}

@app.delete("/api/memories/{memory_id}")
async def remove_memory(memory_id: int, request: Request) -> dict[str, Any]:
    _check_access(request); await asyncio.to_thread(delete_memory, memory_id); return {"ok": True, "memory_id": memory_id}

@app.get("/api/tasks")
async def tasks(request: Request, status: str | None = "open") -> list[dict[str, Any]]:
    _check_access(request); return [asdict(t) for t in list_tasks(status=status, limit=200)]

@app.get("/api/predictions")
async def predictions(request: Request, status: str | None = "open") -> dict[str, Any]:
    _check_access(request); return {"items": [asdict(p) for p in list_predictions(status=status, limit=200)], "metrics": prediction_metrics()}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("voice_server:app", host=_env("METEHAN_HOST", "KUTALP_HOST", "127.0.0.1"), port=int(_env("METEHAN_PORT", "KUTALP_PORT", "8765")), reload=False)
