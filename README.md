<img src="screenshots/app_aini_icon512.png" alt="Alternative text" width="50" align="left"> Kask — Local Multimodal AI Assistant for Android
---
Kask is a high-performance, **completely offline** AI assistant for Android. It leverages the power of **Qwen 3.5** and **ASR** models to provide Text generation, Vision (image analysis), and ASR (voice transcription) directly on your device.

Built with modern Android standards: Kotlin, Jetpack Compose, Material 3, and Hilt.
---
![Alternative text](https://github.com/rhuta/kask/blob/main/screenshots/Gemini_feature.png)
---

## Key Features

-   **Fully Offline**: Your data never leaves your device. All inference is performed locally using `llama.cpp`.
-   **Multimodal Intelligence** powered by Qwen3.5 and Qwen3-ASR:
    -   **Text**: Summarize, rewrite, translate, and chat.
    -   **Vision**: "Ask about this image"—analyze and describe photos or screenshots.
    -   **ASR**: High-accuracy voice-to-text.
-   **Smart Content Detection**: Attach a PDF, Image, or Audio file, and Kask automatically surfaces the most relevant AI actions.
-   **Hardware-Aware Engine Tiers**: Automatically scales from **Efficient** (0.8B) to **Precision** (4B) engine tiers based on your device's RAM and storage.
-   **Local Library & History**: Save your AI results, rename notes, and keep track of your conversation history with local SQLite (Room) storage.
-   **Professional Markdown UI**: High-fidelity Markdown rendering for all AI responses, supporting tables, code blocks, and math equations.

---

<img src="https://github.com/rhuta/kask/blob/main/screenshots/Screenshot_20260716_141645.png" width="24%" alt="HomeScreen"> <img src="https://github.com/rhuta/kask/blob/main/screenshots/Screenshot_20260716_141043.png" width="24%" alt="MathEquation">
<img src="https://github.com/rhuta/kask/blob/main/screenshots/Screenshot_20260716_141406.png" width="24%" alt="Audio"><img src="https://github.com/rhuta/kask/blob/main/screenshots/Screenshot_20260716_141544.png" width="24%" alt="Image">

## Architecture

Kask follows a clean, modular architecture:

```
com.rhuta.kask/
├── KaskApplication.kt          # Hilt entry point
├── MainActivity.kt             # Single-activity host with dynamic Keyboard/Navigation handling
│
├── data/
│   ├── db/                     # Room Database: History and Library persistence
│   ├── network/                # DownloadManager for model bootstrapping
│   └── repository/             # Single source of truth for the UI
│
├── di/                         # Hilt Modules for Database, Engine, and Network
│
├── domain/
│   ├── engine/                 # Core AI logic (AIEngine interface + LlamaCppEngine implementation)
│   ├── model/                  # Domain models: ChatMessage, EngineTier, TaskAction
│   └── util/                   # TextExtractor (PDF/Office), Media Utility
│
├── service/                    # Foreground InferenceService for background processing
│
└── ui/
    ├── home/                   # Main chat interface with smart action tray
    ├── history/                # Searchable interaction logs
    ├── library/                # Document and result management
    ├── markdown/               # Custom WebView-based high-performance Markdown renderer
    ├── settings/               # Preferences and Model Management
    ├── navigation/             # Type-safe Navigation Compose routes
    └── onboarding/             # First-run experience and model downloader
```

---

## Tech Stack

| Layer          | Technology                                   |
|----------------|----------------------------------------------|
| **Core AI**    | `llama.cpp` (b9789) via JNI                  |
| **Models**     | Qwen 3.5 (0.8B, 2B, 4B) + Qwen3-ASR Q4_K_M   |
| **Language**   | Kotlin 2.0                                   |
| **UI Framework**| Jetpack Compose + Material 3                |
| **DI**         | Hilt                                         |
| **Database**   | Room (SQLite)                                |
| **Markdown**   | Custom CSS/JS Bridge (Highlight.js)          |
| **Image Loading**| Coil                                       |
| **PDF Parsing**| PDFBox (Android optimized)                   |
| **docx Parsing**| Zip + XML (Android optimized)                   |

---

## Building & Running

### Requirements
-   **Android Studio**: Ladybug or newer.
-   **JDK**: 17+
-   **Device**: Android 10+ (API 29).
    -   *Efficient Tier*: 6GB+ RAM recommended.
    -   *Precision Tier*: 16GB+ RAM recommended.

---

## Deployment Highlights

-   **Intelligent Keyboard Handling**: Uses dynamic inset consumption to ensure the chat input tray stays flush with the system keyboard, hiding the bottom navigation bar when typing for maximum focus.
-   **Resource Management**: Implements explicit engine releasing (`engine.release()`) to free up RAM when starting a new thread.
-   **Native Prompt Templating**: Utilizes the GGUF's internal chat templates for maximum model alignment and accuracy.

---

## License

Apache License 2.0 © rhuta 2006
