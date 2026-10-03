# AutoSim Bot Android

AutoSim Bot Android is an expanded Android automation project built around accessibility-driven tap execution, overlays, OCR, image matching, macros, swipes, profiles, and global runner controls.

The repository contains an Android Gradle project under `AutoSimFullProject`. The app is labeled `BOT` and extends the earlier AutoSim automation concept with richer workflow screens and reusable automation building blocks.

## Features

- Saved click spots and swipe gestures
- Floating overlay controls
- Sequence and macro builders
- OCR region scanning and phrase-based actions
- Image template matching utilities
- Visual macro screens
- Profile and global runner screens
- Local Room database for automation data
- Import/export support
- Accessibility service gesture execution
- Screen capture support for OCR and image workflows

## Tech Stack

- Kotlin
- Android Gradle Plugin
- Jetpack Compose
- Material 3
- Room
- Kotlin Coroutines and Flow
- Google ML Kit Text Recognition
- Android Accessibility APIs
- Android MediaProjection APIs

## Project Layout

```text
AutoSimFullProject/
├── app/src/main/java/com/example/autosim
│   ├── accessibility/
│   ├── db/
│   ├── engine/
│   ├── ui/
│   └── utils/
├── app/src/main/res/
├── build.gradle
└── settings.gradle
```

## Requirements

- Android Studio
- JDK 17 or Android Studio bundled JDK
- Android SDK installed
- Android device or emulator

## Build

Open `AutoSimFullProject` in Android Studio and sync Gradle.

From a terminal with Gradle available:

```powershell
cd AutoSimFullProject
gradle assembleDebug
```

## Permissions

The app uses sensitive Android capabilities for user-controlled automation:

- Accessibility Service for gestures
- Display over other apps for overlays
- MediaProjection for screen capture
- Foreground services for active automation sessions

Review every automation sequence before running it, especially on screens involving payments, account settings, or irreversible actions.

## Status

This is an active development project. Core automation concepts are present, while some advanced macro, image matching, and runner workflows may still need production hardening and end-to-end testing.

