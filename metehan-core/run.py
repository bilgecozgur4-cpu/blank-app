from __future__ import annotations

import os
import uvicorn
from dotenv import load_dotenv

load_dotenv()

# V0.3 compatibility: the database module still understands the legacy env key.
# METEHAN_DB is authoritative for normal V0.4 startup.
if os.getenv("METEHAN_DB"):
    os.environ.setdefault("KUTALP_DB", os.environ["METEHAN_DB"])

if __name__ == "__main__":
    uvicorn.run(
        "voice_server:app",
        host=os.getenv("METEHAN_HOST", os.getenv("KUTALP_HOST", "127.0.0.1")),
        port=int(os.getenv("METEHAN_PORT", os.getenv("KUTALP_PORT", "8765"))),
        reload=False,
    )
