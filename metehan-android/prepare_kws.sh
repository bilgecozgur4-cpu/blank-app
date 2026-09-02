#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
ASSETS="$ROOT/app/src/main/assets"
MODEL="sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/${MODEL}.tar.bz2"
mkdir -p "$ASSETS"
if [[ ! -f "$ASSETS/$MODEL/tokens.txt" ]]; then
  tmp="$(mktemp -d)"
  curl -L --fail --retry 3 "$URL" -o "$tmp/model.tar.bz2"
  tar -xjf "$tmp/model.tar.bz2" -C "$ASSETS"
  rm -rf "$tmp"
fi
cat > "$ASSETS/$MODEL/keywords_raw.txt" <<'KW'
METEHAN :2.2 #0.35 @METEHAN
HEY METEHAN :2.1 #0.35 @HEY_METEHAN
ME TE HAN :2.0 #0.35 @ME_TE_HAN
MEH TEH HAN :2.0 #0.35 @MEH_TEH_HAN
KW
python -m pip install --quiet "sherpa-onnx==1.13.4"
sherpa-onnx-cli text2token --tokens "$ASSETS/$MODEL/tokens.txt" --tokens-type bpe --bpe-model "$ASSETS/$MODEL/bpe.model" "$ASSETS/$MODEL/keywords_raw.txt" "$ASSETS/$MODEL/keywords.txt"
echo "Metehan KWS assets ready."
