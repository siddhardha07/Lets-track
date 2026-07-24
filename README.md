# Let's Track - Android Money Tracking App

A modern Android personal finance app built with Kotlin, Jetpack Compose, and Material 3.

## Features

- ✅ Add expenses with amount, title, description, and notes
- ✅ 10 default categories with colorful icons
- ✅ View all expenses in a beautiful list
- ✅ Track total expenses
- ✅ Material 3 design with dynamic colors
- ✅ Local-first with Room database
- ✅ Clean Architecture (Data, Domain, UI layers)
- ✅ Dependency Injection with Hilt
- ✅ Reactive UI with Kotlin Flow

## Tech Stack

- **Language**: Kotlin 1.9.23
- **UI Framework**: Jetpack Compose
- **Architecture**: Clean Architecture + MVVM
- **Database**: Room SQLite
- **DI**: Hilt (Dagger)
- **Async**: Coroutines + Flow
- **Design**: Material 3

## Project Structure

```
app/
├── data/
│   ├── local/
│   │   ├── entity/         # Room entities
│   │   ├── dao/            # Data Access Objects
│   │   └── LetsTrackDatabase.kt
│   └── repository/         # Repository implementations
├── domain/
│   ├── model/              # Domain models
│   └── repository/         # Repository interfaces
├── ui/
│   ├── home/               # Home screen with expense list
│   ├── addexpense/         # Add expense screen
│   └── theme/              # Material 3 theme
└── di/                     # Dependency Injection modules
```

## Building the App

1. Clone the repository
2. Open in Android Studio (Hedgehog or later)
3. Sync Gradle
4. Run on an emulator or device (Android 8.0+ / API 26+)

## Future Enhancements

Phase 2:
- SMS/Notification parsing for auto-capture
- CSV/PDF import
- Merchant recognition

Phase 3:
- AI-powered categorization
- Smart insights and analytics
- Natural language queries
- Budget tracking

## License

Copyright 2026 Let's Track
