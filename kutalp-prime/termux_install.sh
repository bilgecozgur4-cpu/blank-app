#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

pkg update -y
pkg install -y python git
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env. Add OPENAI_API_KEY before starting KUTALP."
fi

echo "Run: python run.py"
echo "Open: http://127.0.0.1:8765"
