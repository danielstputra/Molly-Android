# Contributing Guidelines / Panduan Kontribusi

We welcome community contributions to the Molly Android Companion project.
Kami menyambut kontribusi dari komunitas untuk pengembangan aplikasi pendamping Android Molly.

---

## 🛠️ Development Setup / Penyiapan Lingkungan

1. **Fork & Clone**:
   ```bash
   git clone https://github.com/danielstputra/Molly-Android.git
   cd Molly-Android
   ```
2. **Open in Android Studio**:
   - Open the root folder as an existing project.
   - Sync Gradle dependencies (ensure JDK 17 is selected in Project Structure).
3. **Local Testing**:
   - Run on an Android emulator or physical device via USB Debugging.
   - Connect to local backend via `10.0.2.2:5050` (emulator) or LAN IP / Cloudflare Tunnel (device).

---

## 📋 Git Workflow / Alur Kerja Git

- **Branch Naming**: `feat/feature-name`, `fix/bug-description`, `docs/update-topic`.
- **Commit Messages**: Follow Conventional Commits format:
  - `feat(ui): add dark mode color palette`
  - `fix(api): handle connection timeout on tunnel loss`
  - `docs(readme): clarify pairing setup`

---

## 🧪 Testing Checklist / Daftar Pengujian

Before submitting a Pull Request, ensure:
1. `assembleRelease` compiles successfully without lint errors.
2. QR Code scanning parses valid payloads and rejects malformed inputs gracefully.
3. Message polling updates smoothly without UI freezes.
4. Tool approval decisions communicate accurately with the server.
