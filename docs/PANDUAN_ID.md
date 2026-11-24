# Panduan Lengkap Aplikasi Android Molly Agent (Bahasa Indonesia)

Dokumentasi ini menjelaskan konfigurasi, fitur, dan penggunaan aplikasi mobile Molly Agent Companion.

---

## 1. Persiapan & Kompilasi Aplikasi

### Kebutuhan Sistem:
- JDK versi 17 atau lebih baru.
- Android SDK dengan platform target API 34+.
- Gradle 8.13+.

### Perintah Build APK Release:
Jalankan perintah berikut di direktori `Molly-Android`:
```powershell
# Windows
.\gradlew.bat assembleRelease

# Linux / Mac
./gradlew assembleRelease
```
Berkas APK hasil kompilasi tersimpan di:
`app/build/outputs/apk/release/app-release.apk`

---

## 2. Cara Menghubungkan Aplikasi ke Server

1. **Menggunakan Scan QR Code**:
   - Buka Molly Web pada browser.
   - Buka menu modal **Mobile Companion / Pairing**.
   - Di aplikasi Android, klik **Scan QR Code Molly Web**.
   - Sorot kamera ke QR Code di layar monitor.
2. **Menggunakan Kode Pairing Manual**:
   - Klik tombol **Salin Kode Pairing** pada Molly Web.
   - Pilih menu input manual di Android, lalu tempel (*paste*) teks JSON tersebut.
   - Klik **Hubungkan**.

---

## 3. Fitur Utama di Dalam Obrolan

- **Manajemen Sesi Obrolan**:
  - Buka Drawer di sisi kiri untuk melihat riwayat percakapan.
  - Tab **Aktif** menampilkan percakapan yang sedang berjalan.
  - Tab **Arsip** menampilkan percakapan yang diarsipkan.
  - Gunakan menu titik tiga (⋮) pada tiap sesi untuk mengarsipkan atau menghapus sesi.
- **Visualisasi Eksekusi Tool (Real Engine)**:
  - Eksekusi alat oleh Agent ditampilkan dalam kartu interaktif (*Status Badge*, *Input Perintah*, dan *Output Hasil*).
  - Jika alat membutuhkan persetujuan manusia, tombol **Setujui** dan **Tolak** akan muncul otomatis.
- **Peralihan Model & Mode**:
  - Tombol pill di bawah kolom chat memungkinkan Anda mengganti Agen, Mode Persetujuan (*Full Trust* / *Manual*), dan Model AI secara langsung.
- **Fitur Auto-Healing**:
  - Aplikasi secara otomatis membuat thread baru di database jika ID sesi tidak ditemukan, sehingga tidak akan memunculkan error 404.
