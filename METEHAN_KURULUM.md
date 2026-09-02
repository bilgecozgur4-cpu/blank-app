# METEHAN V0.4 — Android Kurulum

METEHAN iki parçadan oluşur: Android uygulaması (duyular/uyandırma/arayüz) ve METEHAN Core (zeka, hafıza, araçlar, OpenAI bağlantısı). API anahtarı APK içine gömülmez.

## En kolay kurulum: Core ve APK aynı telefonda

1. `Metehan-v0.4-debug.apk` dosyasını Android'e kur.
2. Termux'ta kaynak paketteki `metehan-core/` klasörüne gir ve `bash termux_install.sh` çalıştır.
3. `.env` dosyasında `OPENAI_API_KEY`, `METEHAN_HOST=127.0.0.1`, `METEHAN_PORT=8765`, `METEHAN_DB=metehan.db` ayarla.
4. `python run.py` ile Core'u başlat.
5. METEHAN uygulamasında mikrofon/kamera izinlerini ver.
6. “Metehan'ı varsayılan asistan yap” düğmesine bas ve Android'in rol penceresini onayla.
7. Bağlantıyı test et ve ardından wake-word testinde `Metehan` de.

Wake-word sesi cihazda yerel işlenir; sürekli buluta gönderilmez. Kamera yalnızca kullanıcı “Metehan Gör” dediği/ekranındaki düğmeye bastığı zaman kare yakalar. Uzak Core kullanacaksan HTTPS ve güçlü `METEHAN_ACCESS_TOKEN` kullan.

Bu APK debug imzalı geliştirme sürümüdür. OpenAI API kullanımı ChatGPT aboneliğinden ayrıdır. Wake-word eşikleri ilk gerçek cihaz testlerinde ses/ortama göre kalibre edilmelidir.
