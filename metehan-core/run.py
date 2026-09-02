from __future__ import annotations

import os
import uvicorn
from dotenv import load_dotenv

load_dotenv()

if __name__ == "__main__":
    uvicorn.run(
        "voice_server:app",
        host=os.getenv("METEHAN_HOST", os.getenv("KUTALP_HOST", "127.0.0.1")),
        port=int(os.getenv("METEHAN_PORT", os.getenv("KUTALP_PORT", "8765"))),
        reload=False,
    )
