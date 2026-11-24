# Molly Agent - Android Companion Application

> **English & Bahasa Indonesia Documentation**

The official mobile companion client for the Molly AI Agent ecosystem. Connects your mobile device to your Molly workspace via local network or Cloudflare Tunnel with real-time bidirectional synchronization.

Aplikasi pendamping resmi untuk ekosistem Molly AI Agent. Menghubungkan perangkat ponsel cerdas Anda ke workspace Molly melalui jaringan lokal maupun Cloudflare Tunnel dengan sinkronisasi dua arah secara realtime.

---

## 🌟 Key Features / Fitur Utama

- **Real-Time Web Synchronization**: Seamlessly mirrored chat state between Molly Web and Android device. *(Sinkronisasi percakapan langsung dan instan antara Molly Web dan Android)*.
- **Visual Tool Machine Execution**: Real-time rendering of tool actions, commands, status badges, and outputs. *(Visualisasi eksekusi tool, input monospace, dan output alat secara langsung)*.
- **Interactive Tool Approval**: Direct authorization decision buttons (`Approve` / `Reject`) for human-in-the-loop workflows. *(Konfirmasi persetujuan eksekusi tool langsung dari layar smartphone)*.
- **Auto-Healing Session Engine**: Automatically registers active threads on backend databases without 404 session drops. *(Manajemen sesi otomatis untuk mencegah hilangnya ID thread pada backend)*.
- **Conversation Management**: Full drawer management with Active/Archived tabs, clear chat, delete session, and new chat actions. *(Drawer riwayat obrolan lengkap dengan filter arsip, hapus sesi, dan bersihkan obrolan)*.
- **Model & Agent Switcher**: Dynamic selectors for AI providers, models, and execution modes. *(Pilihan pergantian model LLM dan konfigurasi mode persetujuan)*.

---

## 📁 Project Architecture / Struktur Direktori

```text
Molly-Android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/molly/agent/
│   │   │   ├── SplashActivity.java         # Initial branding splash
│   │   │   ├── MainActivity.java           # Connection & QR scanner
│   │   │   ├── ChatActivity.java           # Primary conversation engine
│   │   │   ├── adapter/                    # RecyclerView & Drawer adapters
│   │   │   ├── api/MollyApiClient.java     # Network client & Auto-healing
│   │   │   ├── intro/                      # Onboarding slider components
│   │   │   ├── model/                      # Session & tool data structures
│   │   │   └── ui/MessageAdapter.java      # Realtime item renderer
│   │   └── res/                            # Layouts, vector drawables & themes
│   └── build.gradle
├── docs/                                   # Detailed documentation
├── CONTRIBUTING.md                         # Contribution guide
├── LICENSE.md                              # Open source license
└── README.md
```

---

## 🚀 Getting Started / Cara Memulai

### Prerequisites / Persyaratan:
1. Android Studio Ladybug / Meerkat or Android SDK (API 34+).
2. Java Development Kit (JDK) 17+.
3. Active Molly-LLM backend service.

### Building Release APK / Kompilasi APK:
```bash
# Windows
.\gradlew.bat assembleRelease

# Linux / macOS
./gradlew assembleRelease
```
The compiled release APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📱 Pairing with Molly Web / Cara Pairing

1. Open Molly Web in your browser and open the **Mobile Companion** dialog.
2. In the Android app, tap **Scan QR Code Molly Web**.
3. Point your camera at the screen QR code, or paste the Pairing JSON configuration manually.
4. Your agent workspace will be connected and ready for messaging.

---

## 📚 Further Documentation / Dokumentasi Lengkap
- [Architecture & Lifecycle](docs/ARCHITECTURE.md)
- [API Specification & Protocols](docs/API_GUIDE.md)
- [Dokumentasi Lengkap Bahasa Indonesia](docs/PANDUAN_ID.md)
