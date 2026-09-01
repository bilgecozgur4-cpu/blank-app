# KUTALP PRIME — Donanım Yol Haritası

## Aşama A — Çekirdek geliştirme
- Mevcut telefon + bilgisayar yeterli.
- Telefon sadece arayüzdür; kalıcı veri yerel/veritabanı katmanında tutulur.

## Aşama B — Yerel AI workstation
Hedef, kişisel veriyi yerelde işleyebilen ve internet kesilince temel yeteneklerini koruyan bir sistemdir.
- Yüksek VRAM'li NVIDIA GPU sınıfı
- 64–128 GB sistem RAM'i
- 2 TB+ NVMe
- Ayrı şifreli yedek disk
- UPS
- Gigabit/2.5GbE ağ

Parçalar satın alınmadan önce güncel model boyutları, VRAM ihtiyacı, güç tüketimi ve fiyat/performans yeniden ölçülmelidir; belirli ekran kartı modeli şimdiden kilitlenmemelidir.

## Aşama C — Giyilebilir algı
- Mikrofonlu kulaklık / kemik iletimli ses
- Akıllı saat üzerinden hızlı onay
- Kamera destekli gözlük veya telefon kamerası
- Fiziksel gizlilik anahtarı (kamera/mikrofon kesme)

## Aşama D — Kişisel edge node
- Düşük güç tüketimli mini bilgisayar
- Wake word ve ses ön işleme yerelde
- Şifreli yerel bağlantı

## Tasarım kuralları
1. Kritik işlem kullanıcı onayı olmadan yapılmaz.
2. Mikrofon/kamera için fiziksel kapatma seçeneği bulunur.
3. Bulut yardımcıdır; kişisel hafızanın tek kopyası bulutta tutulmaz.
4. AI önerileri ve gerçekleşen sonuçlar ayrı kaydedilir; doğruluk sonradan ölçülür.
5. Donanım değiştirilebilir modüllerden oluşur; tek markaya kilitlenmez.
