# AutoSim Bot Android

![AutoSim Bot Android preview](docs/screenshots/preview.svg)

## Short Description

A bigger Android automation bot with macros, OCR areas, swipes, and profiles.

## About This Project

AutoSim Bot Android is an advanced Android automation project. It is like a stronger auto-clicker, but with saved macros, swipe actions, OCR checks, profile data, and runner controls.

The goal is to keep the project easy to understand, easy to run, and useful for learning or further development.

## Purpose And Idea

**Purpose:** The purpose of this project is to build a stronger Android automation bot with macros, profiles, tap steps, swipe steps, OCR areas, and runner controls.

**Idea:** The idea is to go beyond a basic auto-clicker. The app should let a user build reusable automation flows and control them from an Android app.

**Why I made it:** I made this to test a bigger automation system and learn how macros, overlays, screen capture, and saved profiles can work together.

## Screenshots

### Real Android emulator screenshot

![Real Android emulator screenshot](docs/screenshots/real-app.png)

### Project preview

![Project preview](docs/screenshots/preview.svg)

### Real source structure

![Real source structure](docs/screenshots/source-structure.svg)

## Main Features

- Save taps, swipes, and macros
- Build repeatable phone workflows
- Use OCR areas for text-based triggers
- Control automation from overlay tools
- Store profiles and settings locally
- Import/export automation settings

## Tech Stack

- Kotlin
- Jetpack Compose
- Room
- ML Kit OCR
- Android Accessibility

## Project Location

Main local folder:

```text
D:\PROJECTS\AutoSimBotAndroid\AutoSimFullProject
```

GitHub repository:

https://github.com/saifalian/AutoSimBotAndroid

## Project Structure

```text
AutoSimFullProject/
AutoSimFullProject/app/   Main Android app
AutoSimFullProject/gradle/ Gradle files
README.md                 Project documentation
```

## How To Run

1. Open AutoSimFullProject in Android Studio.
2. Sync Gradle.
3. Build with gradle assembleDebug.
4. Install on a test phone or emulator.
5. Grant automation permissions only after reviewing the workflow.

## Build Check

Build check: Gradle wrapper files were restored in AutoSimFullProject, assembleDebug completed successfully, and the app was installed and opened on an Android emulator for the real screenshot.

## Current Status

This project is uploaded to GitHub and prepared as a portfolio-style repository. More improvements can be added later, such as real app screenshots, demo videos, releases, and issue templates.

## Safety Note

Automation can affect other apps. Test on safe screens first.

## License

No license file is included yet. Add a license before using this project as an open-source project.
