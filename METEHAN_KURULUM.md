# METEHAN V0.5 — Android Kurulum ve Kullanım

METEHAN iki parçadan oluşur: Android uygulaması (wake-word, kamera, native komuta, güvenli telefon eylemleri) ve METEHAN Core (zeka, hafıza, Realtime, görsel analiz ve eylem planlayıcı). OpenAI API anahtarı APK içine gömülmez.

## Aynı telefonda Core + APK

1. `Metehan-v0.5-debug.apk` dosyasını Android'e kur.
2. Termux'ta kaynak paketteki `metehan-core/` klasörüne gir ve `bash termux_install.sh` çalıştır.
3. `.env` dosyasında en az şunları ayarla:
   - `OPENAI_API_KEY=...`
   - `METEHAN_HOST=127.0.0.1`
   - `METEHAN_PORT=8765`
   - `METEHAN_DB=metehan.db`
   - İstersen `METEHAN_ACCESS_TOKEN=uzun-guclu-bir-anahtar`
4. `python run.py` ile Core'u başlat.
5. METEHAN uygulamasında mikrofon/kamera izinlerini ver.
6. “Metehan'ı varsayılan asistan yap” düğmesine bas ve Android rol penceresini onayla.
7. Core adresini `http://127.0.0.1:8765` olarak kaydet ve bağlantıyı test et.
8. Wake-word testinde `Metehan` de.

## V0.5'te yeni: Native Komuta Merkezi

Ana ekrandaki **Native Komuta Merkezi** üzerinden yazabilir veya Android konuşma tanımasıyla komut verebilirsin. METEHAN pil, şarj durumu, güç tasarrufu, ağ tipi, saat dilimi ve cihaz modelini bağlam olarak kullanabilir. Bu bağlam kesin konum içermez ve ekran içeriğini gizlice okumaz.

METEHAN gerektiğinde yalnızca şu telefon eylemlerinden birini önerebilir: web adresi açma, Wi-Fi/Bluetooth/konum/genel ayar ekranı açma, numarayı arama ekranına hazırlama, harita araması, Android paylaşım menüsünü açma veya METEHAN kamerasını açma.

**Hiçbir önerilen telefon eylemi otomatik çalışmaz.** Android tarafında native onay penceresi çıkar. Reddedersen eylem yapılmaz; onaylarsan standart Android Intent'i çalışır ve sonuç METEHAN audit kaydına yazılır. Arama için `ACTION_DIAL` kullanılır; METEHAN çağrıyı kendi başına başlatmaz.

## Sesli kullanım

Varsayılan asistan + wake-word aktifken `Metehan` diyerek canlı ses oturumunu açabilirsin. Realtime oturumu desteklenen bir telefon eylemi gerektiğinde native onay penceresine düşer; onay/ret sonucu tekrar sesli modele bildirilir.

Wake-word sesi cihazda yerel işlenir; sürekli buluta gönderilmez. Kamera yalnızca sen kamera özelliğini başlattığında kare yakalar. API anahtarı Core'da kalır.

## Uzak Core

Core başka bir cihaz/sunucudaysa HTTPS kullan ve güçlü bir `METEHAN_ACCESS_TOKEN` tanımla. Android WebView aynı-origin dışındaki bağlantıları kendi içinde açmaz; dış bağlantılar sistem tarayıcısına yönlendirilir.

## Not

Bu APK debug imzalı geliştirme sürümüdür. OpenAI API kullanımı ChatGPT aboneliğinden ayrıdır. Wake-word eşiği gerçek telefon, mikrofon ve ortam gürültüsüne göre sonraki cihaz testlerinde kalibre edilebilir.
