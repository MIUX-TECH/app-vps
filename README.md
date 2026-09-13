# Bot Dashboard — Android App

App WebView + SSH tunnel terintegrasi untuk akses dashboard analisa bot dari HP,
tanpa perlu Termux terpisah.

## PENTING — batasan jujur dari pembuatan project ini

Project ini ditulis **tanpa lingkungan Android SDK/Gradle** untuk dites
compile langsung. Kemungkinan ada penyesuaian kecil (versi dependency,
API yang berubah) yang perlu diperbaiki saat kamu build pertama kali —
ini wajar untuk kode yang ditulis tanpa build environment asli. Struktur
dan API yang dipakai semuanya stabil/well-known (JSch untuk SSH,
Jetpack Security untuk enkripsi, WebView standar), jadi kemungkinan
errornya kecil dan biasanya gampang diperbaiki.

File `gradle-wrapper.jar` **tidak disertakan** (perlu Gradle asli untuk
generate, tidak tersedia saat project ini dibuat) — lihat opsi build
di bawah, keduanya tidak butuh file itu.

## Opsi 1: Build via GitHub Actions (tidak perlu install apa pun di HP/PC)

1. Push folder ini ke repo GitHub baru (public atau private, bebas)
2. Buka tab **Actions** di repo, jalankan workflow **"Build APK"** manual
   (tombol "Run workflow"), atau otomatis jalan tiap push ke branch `main`
3. Setelah selesai (~5-10 menit), download APK dari bagian **Artifacts**
   di halaman run tersebut
4. Transfer APK itu ke HP (Google Drive, kabel USB, dll), install manual
   (aktifkan "Install dari sumber tidak dikenal" di Android)

## Opsi 2: Build lewat Android Studio (di Linux/Windows/Mac)

1. Install [Android Studio](https://developer.android.com/studio)
2. `File -> Open`, pilih folder project ini
3. Android Studio otomatis generate Gradle wrapper yang hilang & sync
   dependency (butuh koneksi internet sekali di tahap ini)
4. `Build -> Build Bundle(s)/APK(s) -> Build APK(s)`
5. APK ada di `app/build/outputs/apk/debug/app-debug.apk`

## Opsi 3: Command line (kalau kamu sudah punya Gradle terinstall)

```bash
gradle wrapper --gradle-version 8.4   # sekali saja, generate wrapper
./gradlew assembleDebug
```

## Cara pakai app setelah terinstall

1. Buka app, tekan tombol **⚙ (Pengaturan)**
2. Isi:
   - Host VPS (contoh: `3.27.32.99`)
   - Username SSH (contoh: `admin`)
   - Port SSH (default `22`)
   - Port dashboard di VPS (default `8080`, sesuai `dashboard_server.py`)
   - Token dashboard (sama dengan `DASHBOARD_TOKEN` yang kamu set di VPS)
   - Pilih file `.pem` kamu lewat tombol **"Pilih File Key .pem"**
     (file ini otomatis dienkripsi & disimpan di storage internal app,
     tidak pernah dikirim ke mana pun, termasuk tidak ke pembuat app ini)
3. Simpan, kembali ke halaman utama, tekan **"Konek"**
4. Setelah status berubah jadi "Terhubung", dashboard otomatis muncul

## Keamanan yang perlu kamu tahu

- Key `.pem` disimpan **terenkripsi** (AES-256-GCM) di storage internal
  app, pakai Android Keystore (hardware-backed di kebanyakan HP modern)
  — app lain di HP-mu tidak bisa membacanya tanpa root.
- **Tapi:** kalau HP-mu sendiri hilang/dicuri dalam keadaan tidak
  terkunci (tanpa PIN/pattern/biometrik), siapa pun yang buka app ini
  bisa pakai key itu. **Pastikan HP-mu selalu terkunci dengan PIN/sidik
  jari** — itu lapisan proteksi utama untuk skenario itu.
- Tidak ada server pihak ketiga yang terlibat sama sekali — tunnel
  langsung dari app ke VPS-mu, seperti kamu jalankan `ssh -L` manual.
