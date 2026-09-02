# METEHAN Android V0.4

Native Android kabuğu, sistemin varsayılan asistan rolünü alabilen `VoiceInteractionService`, cihaz üzerinde çalışan sherpa-onnx wake-word motoru, Metehan Core canlı WebRTC paneli ve CameraX görsel analiz istemcisini birleştirir.

Wake-word sesi buluta gönderilmez. OpenAI API anahtarı APK'ya gömülmez; yalnızca Metehan Core tarafındadır. `http://` sadece `127.0.0.1` / `localhost` için kabul edilir; uzak çekirdek HTTPS olmalıdır. Kamera yalnızca kullanıcı Camera Vision ekranını açıp düğmeye bastığında kare yakalar.
