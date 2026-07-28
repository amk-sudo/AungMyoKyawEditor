# Aung Myo Kyaw Editor

<div align="center">
  <img src="app/src/main/res/drawable/ic_launcher.png" alt="Aung Myo Kyaw Editor Icon" width="150"/>
  <br/>
  <br/>

  **Aung Myo Kyaw Editor** - Professional Video Editor for Android
  <br/>
  <br/>

  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge" height="35" alt="License" />
  </a>
  <a href="https://github.com/amk-sudo/AungMyoKyawEditor/releases">
    <img src="https://img.shields.io/badge/Download-APK-green.svg?style=for-the-badge" height="35" alt="Download APK" />
  </a>
</div>

<br/>

**Aung Myo Kyaw Editor** is a free, open-source video editor for Android that prioritizes simplicity, efficiency, and privacy. Built for seamless performance, it empowers creators to easily select, edit, and export watermark-free videos locally on their device.

---

## 🚀 Features

### Video Editing Tools
| Feature | Description |
|---------|-------------|
| **Trim** | Cut and trim video clips with precision timeline editor |
| **Speed** | Adjust video speed (0.25x to 4x) for slow-mo or fast-forward effects |
| **Text Overlay** | Add custom text with position control and adjustable font size |
| **Merge** | Combine multiple video clips into one seamless video |
| **Audio** | Mute, extract, or replace audio tracks |
| **Filters** | Apply visual effects: Sepia, Grayscale, Blur, Brightness, Contrast, Vintage, Warm |

### Advanced Features
| Feature | Description |
|---------|-------------|
| **SRT Subtitles** | Load and display subtitle files (.srt) |
| **MP3 Audio** | Add background music from MP3 files |
| **Export Progress** | Real-time export progress with share functionality |
| **Hardware Acceleration** | Fast video exports using device hardware |
| **No Watermark** | Export videos without any watermark |

---

## 📱 Screenshots

> _Screenshots coming soon_

---

## 🛠️ Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34 (Android 14)
- JDK 17

### Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/amk-sudo/AungMyoKyawEditor.git
   ```

2. **Open in Android Studio**:
   - Launch Android Studio → Open an existing project
   - Select the cloned directory

3. **Build and Run**:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install APK**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Download Release APK

Get the latest release from [GitHub Releases](https://github.com/amk-sudo/AungMyoKyawEditor/releases)

---

## 📂 Project Structure

```
AungMyoKyawEditor/
├── app/src/main/
│   ├── java/com/aungmyokyaw/librecuts/
│   │   ├── models/          # Data models
│   │   ├── services/        # FFmpeg rendering engine
│   │   ├── viewmodels/      # ViewModels for MVVM
│   │   └── VideoEditingActivity.kt
│   └── res/
│       ├── layout/          # XML layouts
│       └── values/          # Colors, strings, themes
└── build.gradle.kts
```

---

## 🤝 Contributing

Contributions are welcome!

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -m 'Add new feature'`
4. Push to branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Made with ❤️ for video creators**
