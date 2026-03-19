# Orch AI
## Private, offline AI for Android

Powered by Qwen3-1.7B running fully on-device via llama.cpp.

---

### Features
- 🧠 Reasoning model — thinks before answering
- 💻 Syntax-highlighted code blocks with copy buttons
- 📡 In-app OTA updates — no Play Store needed  
- 🔒 100% offline — your data never leaves your device

### Self-Updating
The app checks this repo's GitHub Releases automatically.
To push an update to all users:
1. Bump `versionCode` in `app/build.gradle.kts`
2. Push a tag: `git tag v2.1 && git push origin v2.1`
3. GitHub Actions builds the APK and creates the release automatically
4. Users see the update badge in the app header ✓

### Model
- **Qwen3-1.7B Q4_K_M** (~1.28 GB, downloaded once on first launch)
- Stored privately in app's internal storage
