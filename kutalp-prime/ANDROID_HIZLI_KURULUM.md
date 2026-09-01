# Android'de KUTALP PRIME V0.3 — Hızlı Kurulum

Bu yol, KUTALP'ı doğrudan telefonda çalıştırır. Böylece tarayıcı `127.0.0.1` üzerinden açılır ve mikrofon/PWA için LAN-HTTP sorunu oluşmaz.

## 1. Termux

Termux'u güvenilir kaynağından kur ve aç.

## 2. Projeyi telefona koy

ZIP'i bir klasöre çıkar. Termux'ta o klasöre geç.

Örnek:

```bash
termux-setup-storage
cd /storage/emulated/0/Download/kutalp-prime-v0.3
```

## 3. Kur

```bash
bash termux_install.sh
```

## 4. API anahtarını ekle

`.env` dosyasını aç:

```bash
nano .env
```

Şunu doldur:

```env
OPENAI_API_KEY=BURAYA_KENDI_API_ANAHTARIN
```

Kaydet.

## 5. Başlat

```bash
python run.py
```

Tarayıcıda aç:

```text
http://127.0.0.1:8765
```

`Canlı oturumu başlat` düğmesine bas ve mikrofon iznini ver.

## Güvenlik

- `.env` dosyasını kimseyle paylaşma.
- API anahtarını GitHub'a yükleme.
- Telefon dışında başka cihazlardan erişim açmadığın sürece `KUTALP_HOST=127.0.0.1` olarak kalsın.
- KUTALP'ın hafıza/görev/tahmin gibi yazma işlemleri kullanıcı onayına bağlıdır.

## Şimdiki sınır

V0.3, uygulama/PWA açıkken gerçek zamanlı sesli çalışır. Android arka planında sürekli dinleyen güvenilir bir “KUTALP” uyandırma kelimesi için daha sonra native Android foreground service gerekir; bu sürüm bunu varmış gibi göstermiyor.
