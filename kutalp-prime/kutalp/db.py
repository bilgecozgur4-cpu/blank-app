from __future__ import annotations

import os
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


@dataclass
class Memory:
    id: int
    text: str
    kind: str
    created_at: str


def _db_path() -> Path:
    return Path(os.getenv("KUTALP_DB", "kutalp_prime.db"))


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(_db_path(), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with connect() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                kind TEXT NOT NULL DEFAULT 'general',
                created_at TEXT NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS decisions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                red_team TEXT,
                created_at TEXT NOT NULL
            )
            """
        )
        conn.commit()


def add_memory(text: str, kind: str = "general") -> int:
    text = text.strip()
    if not text:
        raise ValueError("Memory cannot be empty")
    now = datetime.now(timezone.utc).isoformat()
    with connect() as conn:
        cur = conn.execute(
            "INSERT INTO memories(text, kind, created_at) VALUES (?, ?, ?)",
            (text, kind, now),
        )
        conn.commit()
        return int(cur.lastrowid)


def list_memories(limit: int = 100) -> list[Memory]:
    with connect() as conn:
        rows = conn.execute(
            "SELECT id, text, kind, created_at FROM memories ORDER BY id DESC LIMIT ?",
            (limit,),
        ).fetchall()
    return [Memory(**dict(r)) for r in rows]


def delete_memory(memory_id: int) -> None:
    with connect() as conn:
        conn.execute("DELETE FROM memories WHERE id = ?", (memory_id,))
        conn.commit()


def relevant_memories(query: str, limit: int = 8) -> list[Memory]:
    """Tiny local retriever for V0.1. Later replaced by embeddings/vector search."""
    tokens = {t.strip(".,!?;:()[]{}\"'").lower() for t in query.split() if len(t) >= 3}
    memories = list_memories(limit=300)
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
    now = datetime.now(timezone.utc).isoformat()
    with connect() as conn:
        cur = conn.execute(
            "INSERT INTO decisions(question, answer, red_team, created_at) VALUES (?, ?, ?, ?)",
            (question, answer, red_team, now),
        )
        conn.commit()
        return int(cur.lastrowid)
