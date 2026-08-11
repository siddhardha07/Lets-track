# Let's Track - Android Money Tracking App

A modern Android personal finance app built with Kotlin, Jetpack Compose, and Material 3. Automatically tracks your expenses through SMS parsing with smart AI-powered categorization.

## Features

### 🎯 Core Features

- ✅ **Automatic SMS Import** - Auto-capture transactions from bank SMS messages
- ✅ **Smart Categorization** - AI-powered merchant recognition and category learning
- ✅ **Merchant Learning** - Automatically categorize future transactions from known merchants
- ✅ **Bulk Update with Confirmation** - Update all transactions from a merchant with user confirmation
- ✅ **Pull-to-Refresh** - Smart SMS scanning (only new messages since last import)
- ✅ **Transaction Review Overlay** - Review and categorize new transactions in a beautiful bottom sheet

### 📊 Analytics & Insights

- ✅ **AI-Powered Insights** - Get smart spending insights and recommendations
- ✅ **Visual Charts** - Spending trends with beautiful gradient bar charts
- ✅ **Category Breakdown** - Interactive donut chart with category filtering
- ✅ **Time Filters** - View by today, week, month, custom date ranges
- ✅ **Key Metrics Dashboard** - Track income, expenses, and net balance with trend indicators

### 💰 Transaction Management

- ✅ **Manual Entry** - Add expenses with amount, title, description, and notes
- ✅ **Rich Categories** - 10+ default categories with colorful icons and emojis
- ✅ **Search & Filter** - Find transactions by merchant, category, or type
- ✅ **Transaction Types** - Separate tracking for expenses and income
- ✅ **Duplicate Detection** - Prevents duplicate imports from SMS

### 🎨 Design & UX

- ✅ **Material 3 Design** - Modern glass morphism UI with gradient cards
- ✅ **Dynamic Colors** - 6 accent themes (Green, Blue, Teal, Violet, Navy, Navy & Brown)
- ✅ **Dark/Light Theme** - Adaptive theming with dynamic content colors
- ✅ **Smooth Animations** - Polished transitions and interactions
- ✅ **Responsive Layout** - Optimized for all screen sizes

### 🔧 Technical Features

- ✅ **Local-first Architecture** - Room database with reactive Flow streams
- ✅ **Permission Handling** - Runtime SMS and notification permissions
- ✅ **Background Processing** - Service-based SMS monitoring
- ✅ **Thread Safety** - Mutex-based concurrency control
- ✅ **Clean Architecture** - Separated Data, Domain, and UI layers
- ✅ **Dependency Injection** - Hilt/Dagger for maintainable code

## Tech Stack

- **Language**: Kotlin 1.9.23
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Clean Architecture + MVVM
- **Database**: Room SQLite with reactive Flow
- **DI**: Hilt (Dagger)
- **Async**: Coroutines + Flow
- **Design**: Material 3 with glass morphism and gradient cards
- **SMS Processing**: BroadcastReceiver + Background Service
- **AI/ML**: Custom merchant categorization engine
- **Permissions**: Runtime permission handling (SMS, Notifications)
- **Concurrency**: Mutex for thread-safe operations

## Project Structure

```
app/
├── data/
│   ├── local/
│   │   ├── entity/              # Room entities (Expense, Category, SMS, Merchant, etc.)
│   │   ├── dao/                 # Data Access Objects
│   │   └── LetsTrackDatabase.kt # Room database configuration
│   └── repository/              # Repository implementations
├── domain/
│   ├── model/                   # Domain models (Expense, Category, Prediction, etc.)
│   └── repository/              # Repository interfaces
├── ui/
│   ├── home/                    # Home screen with analytics dashboard
│   │   ├── HomeScreen.kt        # Hero cards, charts, insights
│   │   └── InsightsEngine.kt    # AI-powered spending insights
│   ├── expenses/                # Expense list with search and filters
│   ├── addexpense/              # Add/Edit expense screen
│   ├── overlay/                 # Transaction review bottom sheet
│   ├── notifications/           # Pending transaction notifications
│   ├── settings/                # App settings and customization
│   ├── components/              # Reusable UI components (cards, charts, buttons)
│   └── theme/                   # Material 3 theme with accent colors
├── sms/
│   ├── SmsParser.kt             # Extracts transaction data from SMS
│   ├── SmsProcessor.kt          # Processes parsed SMS into transactions
│   ├── SmsBroadcastReceiver.kt  # Listens for incoming SMS
│   ├── SmsImportService.kt      # Background SMS import service
│   └── SmsIngestPipeline.kt     # Manages SMS processing flow
├── ml/
│   ├── SmartCategorizer.kt      # Merchant-based categorization engine
│   └── CommonMerchantsLoader.kt # Loads pre-trained merchant data
├── service/                     # Background services
└── di/                          # Dependency Injection modules
```

## Building the App

1. Clone the repository
2. Open in Android Studio (Hedgehog or later)
3. Sync Gradle
4. Run on an emulator or device (Android 8.0+ / API 26+)

**Required Permissions:**

- ✅ SMS (READ_SMS, RECEIVE_SMS) - For automatic transaction capture
- ✅ POST_NOTIFICATIONS - For transaction review alerts
- ✅ RECEIVE_BOOT_COMPLETED - For SMS monitoring after device restart

The app will request these permissions at runtime when needed.

## Screenshots

_Coming soon - upload screenshots of home screen, charts, transaction review, and settings_

## Key Features in Detail

### 🤖 Smart SMS Parsing

The app automatically detects bank transaction SMS messages and extracts:

- Transaction amount and type (debit/credit)
- Merchant/payee name
- Account number
- Transaction date and time
- Balance after transaction

Supports multiple bank formats and includes filters for mandate creation, scheduled payments, and other non-transaction messages.

### 🧠 Merchant Learning System

When you categorize a transaction from a merchant:

- The app learns and remembers that merchant-category mapping
- Future transactions from the same merchant are auto-categorized
- You can bulk-update all existing transactions from that merchant
- Confirmation dialog prevents accidental bulk changes

### 📈 Analytics Dashboard

- **Key Metrics Card**: Income, expenses, net balance with trend indicators
- **Spending Trend Chart**: Beautiful gradient bar chart with smart time bucketing
- **Category Breakdown**: Interactive donut chart with tap-to-filter
- **AI Insights**: Smart recommendations based on spending patterns

## Future Enhancements

- [ ] Budget tracking and alerts
- [ ] Recurring transaction detection
- [ ] Multi-account support
- [ ] CSV/PDF export
- [ ] Backup and sync
- [ ] Split transactions
- [ ] Receipt photo attachment
- [ ] Natural language transaction entry
- [ ] Savings goals tracking
- [ ] Bill reminders

## Getting Started

### First Launch

1. Grant SMS and Notification permissions when prompted
2. The app will automatically scan your existing SMS for bank transactions
3. Review any pending transactions that need categorization
4. Start tracking! New transactions will be auto-imported and categorized

### Tips

- **Pull to refresh** on the expenses screen to catch up on new SMS messages
- **Tap a transaction** to edit and change its category
- **Bulk update**: When you change a merchant's category, you'll be asked if you want to update all transactions from that merchant
- **Time filters**: Use the filter chips on the home screen to view different time periods
- **Category filtering**: Tap categories on the donut chart to filter the view

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Copyright 2026 Let's Track
