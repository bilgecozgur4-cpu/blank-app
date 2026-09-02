# METEHAN Core V0.4

METEHAN kişisel yapay zekâ başdanışman çekirdeğidir. V0.4; Realtime ses, bilimsel karar desteği, Red Team, kullanıcı kontrollü SQLite hafıza, izinli araç sistemi, tahmin kalibrasyonu ve Android kamera istemcisi için `/api/vision` endpoint'ini birleştirir.

## Çalıştırma

```bash
cd metehan-core
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python run.py
```

Android aynı telefonda ise varsayılan adres `http://127.0.0.1:8765` olur. Uzak makine kullanılıyorsa HTTPS ve `METEHAN_ACCESS_TOKEN` zorunlu tutulmalıdır. API anahtarı Android APK'ya gömülmez.

Not: Python paket klasörünün adı V0.3 uyumluluğu için şimdilik `kutalp/` olarak korunmuştur; bu yalnızca iç modül adıdır, ürün adı METEHAN'dır.
