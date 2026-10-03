# AutoSim Bot Android

AutoSim Bot Android is an Android automation app for building and running repeated actions on a phone.

In simple words, this project is like a more advanced version of an auto-clicker. It can save taps, swipes, macros, OCR areas, and other automation settings. The goal is to help a user create repeatable phone workflows from inside an Android app.

The main Android project is inside the `AutoSimFullProject` folder. The app name shown on the phone is `BOT`.

## What This App Can Do

- Save tap positions and swipe actions.
- Build simple automation sequences.
- Create reusable macros.
- Show floating controls over other apps.
- Read text from the screen using OCR.
- Match screen text with saved phrases.
- Store automation data in a local Room database.
- Import and export saved settings.
- Use Android accessibility tools to perform gestures.
- Use screen capture for OCR and image-based features.

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

## Permissions Used

The app uses Android permissions that are common for automation apps:

- Accessibility Service for gestures
- Display over other apps for overlays
- MediaProjection for screen capture
- Foreground services for active automation sessions

Always review your automation before running it. Be extra careful on screens related to money, account settings, messages, or any action that cannot be undone.

## Current Status

This is an active development project. The main automation ideas are already included, but some advanced macro, image matching, and runner features may still need more testing and polishing.
