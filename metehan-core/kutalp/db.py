from __future__ import annotations

import json
import os
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


@dataclass
class Memory:
    id: int
    text: str
    kind: str
    created_at: str


@dataclass
class Task:
    id: int
    title: str
    notes: str | None
    due_at: str | None
    status: str
    created_at: str
    completed_at: str | None


@dataclass
class Prediction:
    id: int
    statement: str
    probability: float
    due_at: str | None
    outcome: int | None
    status: str
    created_at: str
    resolved_at: str | None
    brier_score: float | None


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _db_path() -> Path:
    return Path(os.getenv("KUTALP_DB", "kutalp_prime.db"))


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(_db_path(), check_same_thread=False, timeout=10.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA busy_timeout=5000")
    return conn


def init_db() -> None:
    with connect() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                kind TEXT NOT NULL DEFAULT 'general',
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS decisions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                red_team TEXT,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                notes TEXT,
                due_at TEXT,
                status TEXT NOT NULL DEFAULT 'open',
                created_at TEXT NOT NULL,
                completed_at TEXT
            );

            CREATE TABLE IF NOT EXISTS predictions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                statement TEXT NOT NULL,
                probability REAL NOT NULL CHECK(probability >= 0 AND probability <= 1),
                due_at TEXT,
                outcome INTEGER CHECK(outcome IN (0,1)),
                status TEXT NOT NULL DEFAULT 'open',
                created_at TEXT NOT NULL,
                resolved_at TEXT,
                brier_score REAL
            );

            CREATE TABLE IF NOT EXISTS audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tool_name TEXT NOT NULL,
                arguments_json TEXT NOT NULL,
                result_json TEXT NOT NULL,
                approved INTEGER NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_memories_kind ON memories(kind);
            CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
            CREATE INDEX IF NOT EXISTS idx_predictions_status ON predictions(status);
            """
        )
        conn.commit()


def add_memory(text: str, kind: str = "general") -> int:
    text = text.strip()
    kind = (kind or "general").strip() or "general"
    if not text:
        raise ValueError("Memory cannot be empty")
    with connect() as conn:
        cur = conn.execute(
            "INSERT INTO memories(text, kind, created_at) VALUES (?, ?, ?)",
            (text, kind, _now()),
        )
        conn.commit()
        return int(cur.lastrowid)


def list_memories(limit: int = 100) -> list[Memory]:
    limit = max(1, min(int(limit), 1000))
    with connect() as conn:
        rows = conn.execute(
            "SELECT id, text, kind, created_at FROM memories ORDER BY id DESC LIMIT ?",
            (limit,),
        ).fetchall()
    return [Memory(**dict(r)) for r in rows]


def delete_memory(memory_id: int) -> None:
    with connect() as conn:
        conn.execute("DELETE FROM memories WHERE id = ?", (int(memory_id),))
        conn.commit()


def relevant_memories(query: str, limit: int = 8) -> list[Memory]:
    """Small local lexical retriever. Vector memory is a later upgrade."""
    tokens = {
        t.strip(".,!?;:()[]{}\"'").lower()
        for t in query.split()
        if len(t.strip(".,!?;:()[]{}\"'")) >= 3
    }
    memories = list_memories(limit=500)
    if not tokens:
        return memories[:limit]

    scored: list[tuple[int, Memory]] = []
    for memory in memories:
        lower = memory.text.lower()
        score = sum(1 for token in tokens if token in lower)
        if score:
            scored.append((score, memory))
    scored.sort(key=lambda x: (x[0], x[1].id), reverse=True)
    return [m for _, m in scored[:limit]] or memories[: min(3, limit)]


def add_decision(question: str, answer: str, red_team: str | None = None) -> int:
    with connect() as conn:
        cur = conn.execute(
            "INSERT INTO decisions(question, answer, red_team, created_at) VALUES (?, ?, ?, ?)",
            (question, answer, red_team, _now()),
        )
        conn.commit()
        return int(cur.lastrowid)


def add_task(title: str, notes: str | None = None, due_at: str | None = None) -> int:
    title = title.strip()
    if not title:
        raise ValueError("Task title cannot be empty")
    notes = notes.strip() if isinstance(notes, str) and notes.strip() else None
    due_at = due_at.strip() if isinstance(due_at, str) and due_at.strip() else None
    with connect() as conn:
        cur = conn.execute(
            "INSERT INTO tasks(title, notes, due_at, status, created_at) VALUES (?, ?, ?, 'open', ?)",
            (title, notes, due_at, _now()),
        )
        conn.commit()
        return int(cur.lastrowid)


def list_tasks(status: str | None = "open", limit: int = 100) -> list[Task]:
    limit = max(1, min(int(limit), 1000))
    with connect() as conn:
        if status in {"open", "done"}:
            rows = conn.execute(
                "SELECT * FROM tasks WHERE status = ? ORDER BY id DESC LIMIT ?",
                (status, limit),
            ).fetchall()
        else:
            rows = conn.execute("SELECT * FROM tasks ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    return [Task(**dict(r)) for r in rows]


def complete_task(task_id: int) -> bool:
    with connect() as conn:
        cur = conn.execute(
            "UPDATE tasks SET status='done', completed_at=? WHERE id=? AND status!='done'",
            (_now(), int(task_id)),
        )
        conn.commit()
        return cur.rowcount > 0


def add_prediction(statement: str, probability: float, due_at: str | None = None) -> int:
    statement = statement.strip()
    probability = float(probability)
    if not statement:
        raise ValueError("Prediction statement cannot be empty")
    if not 0.0 <= probability <= 1.0:
        raise ValueError("Probability must be between 0 and 1")
    due_at = due_at.strip() if isinstance(due_at, str) and due_at.strip() else None
    with connect() as conn:
        cur = conn.execute(
            "INSERT INTO predictions(statement, probability, due_at, status, created_at) VALUES (?, ?, ?, 'open', ?)",
            (statement, probability, due_at, _now()),
        )
        conn.commit()
        return int(cur.lastrowid)


def list_predictions(status: str | None = "open", limit: int = 100) -> list[Prediction]:
    limit = max(1, min(int(limit), 1000))
    with connect() as conn:
        if status in {"open", "resolved"}:
            rows = conn.execute(
                "SELECT * FROM predictions WHERE status=? ORDER BY id DESC LIMIT ?",
                (status, limit),
            ).fetchall()
        else:
            rows = conn.execute("SELECT * FROM predictions ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    return [Prediction(**dict(r)) for r in rows]


def resolve_prediction(prediction_id: int, outcome: bool) -> dict[str, Any]:
    y = 1 if bool(outcome) else 0
    with connect() as conn:
        row = conn.execute(
            "SELECT probability, status FROM predictions WHERE id=?",
            (int(prediction_id),),
        ).fetchone()
        if not row:
            raise ValueError("Prediction not found")
        p = float(row["probability"])
        brier = (p - y) ** 2
        conn.execute(
            """
            UPDATE predictions
            SET outcome=?, status='resolved', resolved_at=?, brier_score=?
            WHERE id=?
            """,
            (y, _now(), brier, int(prediction_id)),
        )
        conn.commit()
    return {"prediction_id": int(prediction_id), "outcome": bool(outcome), "brier_score": brier}


def prediction_metrics() -> dict[str, Any]:
    with connect() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS n, AVG(brier_score) AS avg_brier FROM predictions WHERE status='resolved'"
        ).fetchone()
    n = int(row["n"] or 0)
    avg = float(row["avg_brier"]) if row["avg_brier"] is not None else None
    return {"resolved_predictions": n, "average_brier_score": avg}


def add_audit(tool_name: str, arguments: dict[str, Any], result: Any, approved: bool) -> int:
    with connect() as conn:
        cur = conn.execute(
            """
            INSERT INTO audit_log(tool_name, arguments_json, result_json, approved, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            (
                tool_name,
                json.dumps(arguments, ensure_ascii=False, sort_keys=True),
                json.dumps(result, ensure_ascii=False, sort_keys=True, default=str),
                1 if approved else 0,
                _now(),
            ),
        )
        conn.commit()
        return int(cur.lastrowid)


def list_audit(limit: int = 100) -> list[dict[str, Any]]:
    with connect() as conn:
        rows = conn.execute(
            "SELECT * FROM audit_log ORDER BY id DESC LIMIT ?",
            (max(1, min(int(limit), 1000)),),
        ).fetchall()
    return [dict(r) for r in rows]
