from __future__ import annotations

import os
import platform
from dataclasses import asdict, dataclass
from typing import Any, Callable

from . import db


@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    parameters: dict[str, Any]
    requires_approval: bool
    handler: Callable[[dict[str, Any]], Any]

    def realtime_schema(self) -> dict[str, Any]:
        return {
            "type": "function",
            "name": self.name,
            "description": self.description,
            "parameters": self.parameters,
        }


def _status(_: dict[str, Any]) -> dict[str, Any]:
    return {
        "service": "METEHAN",
        "version": "0.4",
        "python": platform.python_version(),
        "platform": platform.platform(),
        "api_configured": bool(os.getenv("OPENAI_API_KEY", "").strip()),
        "memory_count": len(db.list_memories(limit=1000)),
        "open_tasks": len(db.list_tasks("open", limit=1000)),
        "prediction_metrics": db.prediction_metrics(),
    }


def _search_memory(args: dict[str, Any]) -> list[dict[str, Any]]:
    return [asdict(m) for m in db.relevant_memories(str(args["query"]), limit=8)]


def _save_memory(args: dict[str, Any]) -> dict[str, Any]:
    mid = db.add_memory(str(args["text"]), str(args["kind"]))
    return {"saved": True, "memory_id": mid}


def _delete_memory(args: dict[str, Any]) -> dict[str, Any]:
    db.delete_memory(int(args["memory_id"]))
    return {"deleted": True, "memory_id": int(args["memory_id"])}


def _create_task(args: dict[str, Any]) -> dict[str, Any]:
    tid = db.add_task(str(args["title"]), args.get("notes"), args.get("due_at"))
    return {"created": True, "task_id": tid}


def _list_tasks(args: dict[str, Any]) -> list[dict[str, Any]]:
    status = args.get("status")
    return [asdict(t) for t in db.list_tasks(status=status, limit=50)]


def _complete_task(args: dict[str, Any]) -> dict[str, Any]:
    ok = db.complete_task(int(args["task_id"]))
    return {"completed": ok, "task_id": int(args["task_id"])}


def _record_prediction(args: dict[str, Any]) -> dict[str, Any]:
    pid = db.add_prediction(
        str(args["statement"]),
        float(args["probability"]),
        args.get("due_at"),
    )
    return {"recorded": True, "prediction_id": pid}


def _list_predictions(args: dict[str, Any]) -> dict[str, Any]:
    status = args.get("status")
    return {
        "items": [asdict(p) for p in db.list_predictions(status=status, limit=50)],
        "metrics": db.prediction_metrics(),
    }


def _resolve_prediction(args: dict[str, Any]) -> dict[str, Any]:
    return db.resolve_prediction(int(args["prediction_id"]), bool(args["outcome"]))


TOOLS: dict[str, ToolSpec] = {
    "get_system_status": ToolSpec(
        name="get_system_status",
        description="Read METEHAN local system status. This is read-only.",
        parameters={"type": "object", "properties": {}, "required": [], "additionalProperties": False},
        requires_approval=False,
        handler=_status,
    ),
    "search_memory": ToolSpec(
        name="search_memory",
        description="Search the user's explicitly saved local long-term memory. Read-only.",
        parameters={"type": "object", "properties": {"query": {"type": "string"}}, "required": ["query"], "additionalProperties": False},
        requires_approval=False,
        handler=_search_memory,
    ),
    "save_memory": ToolSpec(
        name="save_memory",
        description="Save a user-approved fact, goal, preference, project note or rule to long-term memory. Never call for sensitive data unless the user explicitly asks to save it.",
        parameters={"type": "object", "properties": {"text": {"type": "string"}, "kind": {"type": "string", "enum": ["general", "goal", "preference", "project", "rule", "user_note"]}}, "required": ["text", "kind"], "additionalProperties": False},
        requires_approval=True,
        handler=_save_memory,
    ),
    "delete_memory": ToolSpec(
        name="delete_memory",
        description="Delete one long-term memory by ID. This is destructive and requires approval.",
        parameters={"type": "object", "properties": {"memory_id": {"type": "integer"}}, "required": ["memory_id"], "additionalProperties": False},
        requires_approval=True,
        handler=_delete_memory,
    ),
    "create_task": ToolSpec(
        name="create_task",
        description="Create a local task for the user. Requires user approval before writing.",
        parameters={"type": "object", "properties": {"title": {"type": "string"}, "notes": {"type": ["string", "null"]}, "due_at": {"type": ["string", "null"], "description": "ISO date/time or human-readable date, or null"}}, "required": ["title", "notes", "due_at"], "additionalProperties": False},
        requires_approval=True,
        handler=_create_task,
    ),
    "list_tasks": ToolSpec(
        name="list_tasks",
        description="List local tasks. Read-only.",
        parameters={"type": "object", "properties": {"status": {"type": ["string", "null"], "enum": ["open", "done", None]}}, "required": ["status"], "additionalProperties": False},
        requires_approval=False,
        handler=_list_tasks,
    ),
    "complete_task": ToolSpec(
        name="complete_task",
        description="Mark a local task as completed. Requires user approval.",
        parameters={"type": "object", "properties": {"task_id": {"type": "integer"}}, "required": ["task_id"], "additionalProperties": False},
        requires_approval=True,
        handler=_complete_task,
    ),
    "record_prediction": ToolSpec(
        name="record_prediction",
        description="Record a falsifiable forecast with a calibrated probability so METEHAN can later score its accuracy scientifically. Requires approval.",
        parameters={"type": "object", "properties": {"statement": {"type": "string"}, "probability": {"type": "number", "minimum": 0, "maximum": 1}, "due_at": {"type": ["string", "null"]}}, "required": ["statement", "probability", "due_at"], "additionalProperties": False},
        requires_approval=True,
        handler=_record_prediction,
    ),
    "list_predictions": ToolSpec(
        name="list_predictions",
        description="List METEHAN's recorded predictions and calibration metric. Read-only.",
        parameters={"type": "object", "properties": {"status": {"type": ["string", "null"], "enum": ["open", "resolved", None]}}, "required": ["status"], "additionalProperties": False},
        requires_approval=False,
        handler=_list_predictions,
    ),
    "resolve_prediction": ToolSpec(
        name="resolve_prediction",
        description="Resolve a recorded prediction as true or false and calculate its Brier score. Requires approval.",
        parameters={"type": "object", "properties": {"prediction_id": {"type": "integer"}, "outcome": {"type": "boolean"}}, "required": ["prediction_id", "outcome"], "additionalProperties": False},
        requires_approval=True,
        handler=_resolve_prediction,
    ),
}


def realtime_tools() -> list[dict[str, Any]]:
    return [spec.realtime_schema() for spec in TOOLS.values()]


def tool_metadata() -> list[dict[str, Any]]:
    return [{"name": spec.name, "description": spec.description, "requires_approval": spec.requires_approval} for spec in TOOLS.values()]


def execute_tool(name: str, arguments: dict[str, Any], approved: bool = False) -> dict[str, Any]:
    spec = TOOLS.get(name)
    if spec is None:
        result = {"ok": False, "error": f"Unknown tool: {name}"}
        db.add_audit(name, arguments, result, approved=False)
        return result
    if spec.requires_approval and not approved:
        result = {"ok": False, "approval_required": True, "tool": name, "message": "User approval is required before this action can run."}
        db.add_audit(name, arguments, result, approved=False)
        return result
    try:
        data = spec.handler(arguments)
        result = {"ok": True, "data": data}
    except Exception as exc:
        result = {"ok": False, "error": f"{type(exc).__name__}: {exc}"}
    db.add_audit(name, arguments, result, approved=approved or not spec.requires_approval)
    return result
