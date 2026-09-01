# KUTALP PRIME V0.1

KUTALP PRIME, kişisel yapay zekâ başdanışman çekirdeğinin ilk çalışan sürümüdür. Amaç yalnızca sohbet etmek değil; kullanıcı kontrollü uzun dönem hafıza, bilimsel düşünme, karşı-argüman (Red Team) ve ileride gerçek araç/donanım entegrasyonuna açık bir temel kurmaktır.

## V0.1 özellikleri

- Streamlit tabanlı mobil uyumlu arayüz
- SQLite uzun dönem hafıza
- OpenAI Responses API entegrasyonu
- Bilimsel mod: hipotez / kanıt / belirsizlik / test odaklı yaklaşım
- Red Team: cevabı ikinci bağımsız çağrıyla eleştirir
- Karar geçmişini yerel veritabanına kaydeder
- API anahtarı yokken güvenli offline çekirdek açılır

## Kurulum

```bash
cd kutalp-prime
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

`.env` dosyasına kendi anahtarını ekle:

```env
OPENAI_API_KEY=...
KUTALP_MODEL=gpt-5.6-terra
```

Çalıştır:

```bash
streamlit run app.py
```

Telefonda aynı Wi-Fi ağından erişmek için bilgisayarın yerel IP adresini kullanabilir veya güvenli bir deployment servisine kurabilirsin. API anahtarını hiçbir zaman frontend koduna veya GitHub'a commit etme.

## Mimari yön

V0.2 ses, V0.3 gerçek araçlar, V0.4 görüş/kamera, V0.5 yerel GPU ve vektör hafıza, V1.0 ise çoklu uzman ajan orkestrasyonudur.

**Prensip:** Güzel konuşmak başarı ölçütü değildir. KUTALP; doğruluk, hata önleme, zaman kazancı, karar kalitesi ve ölçülebilir sonuçlarla değerlendirilecektir.
