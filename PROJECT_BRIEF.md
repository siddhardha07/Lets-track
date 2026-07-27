# Lets-Track - AI-Powered Personal Finance Assistant

## Project Vision

Build a modern Android personal finance app that **minimizes manual data entry** by automatically capturing and understanding transactions through AI. The app should intelligently categorize expenses, parse bank statements, read SMS notifications, and provide smart insights—all while keeping user effort to an absolute minimum.

## Core Philosophy

**"Just credit and debit - don't force users to fill everything"**

Users shouldn't need to manually categorize, tag, or enter details for every transaction. The app should:

- Accept uncategorized transactions
- Mark items for optional review (not mandatory)
- Learn from user corrections over time
- Auto-extract merchant names and details
- Never block the user flow with required fields

## Key Features

### 1. Automatic Transaction Capture

**SMS/Notification Parsing (Priority #1)**

- Listen to bank SMS and payment app notifications (UPI, NEFT, IMPS)
- Automatically extract: amount, date, merchant, transaction type
- Create transactions in real-time without user intervention
- Support: PhonePe, GPay, PayTM, bank SMS formats

**PDF Bank Statement Import**

- Upload password-protected bank statements
- Parse multiple formats: SBI, HDFC, ICICI, Axis, IDFC First Bank
- Extract date, description, debit/credit amounts
- Bulk import with progress tracking

**CSV Import (Flexible)**

- No forced categorization
- Accept any CSV structure
- Smart column mapping
- Mark all as "needs review" for later

### 2. AI-Powered Intelligence

**Smart Categorization**

- Hybrid approach: on-device + cloud ML
- Auto-categorize based on merchant name and description
- Learn from user corrections
- Confidence scores (suggest vs auto-apply)

**Merchant Recognition**

- Extract merchant names from UPI strings
- Normalize variants (e.g., "SWIGGY BANGALORE" → "Swiggy")
- Detect recurring merchants (Uber, Zomato, electricity bills)
- Build personal merchant database

**Pattern Detection**

- Identify recurring expenses (subscriptions, bills)
- Detect unusual spending patterns
- Track category trends over time
- Predict monthly spending

### 3. Core Transaction Management

**Multiple Accounts**

- Bank accounts, credit cards, cash, digital wallets
- Track balance per account
- Transfer between accounts

**Categories & Subcategories**

- Hierarchical structure (Food → Restaurants, Groceries)
- Custom categories
- AI-suggested categories
- Optional - never mandatory

**Transaction Details**

- Title, description, amount, date
- Attachments (receipts, bills)
- Tags for flexible filtering
- Notes for context

### 4. Smart Insights

**Spending Analysis**

- Daily/weekly/monthly summaries
- Category-wise breakdown
- Compare periods
- Budget tracking

**AI Reports**

- "You spent 30% more on food this month"
- "Subscription to Netflix due in 3 days"
- "Unusual spending detected in Transport"

**Natural Language Queries** (Future)

- "How much did I spend on food last month?"
- "Show all Swiggy orders"
- "Total spent at Amazon this year"

### 5. Data Import & Export

**Import Options**

- PDF bank statements (with password support)
- CSV files (flexible mapping)
- SMS/notifications (automatic)
- Backup files

**Export Options**

- CSV export (all transactions)
- PDF reports
- Cloud backup
- Category-wise exports

## Technical Requirements

### Android Platform

- **Minimum SDK**: 21 (Android 5.0)
- **Target SDK**: Latest stable (34+)
- **Language**: 100% Kotlin
- **Architecture**: Clean Architecture, MVVM/MVI

### UI/UX

- **Design**: Material 3 (Material You)
- **Theme**: Dark mode support
- **Compose**: Jetpack Compose for modern UI
- **Responsive**: Handle different screen sizes

### Data Management

- **Database**: Room SQLite
- **Reactive**: Coroutines + Flow
- **Local-first**: Works offline, syncs when online

### AI/ML Stack

- **On-Device**: TensorFlow Lite for basic categorization
- **Cloud ML**: API integration for advanced features
- **Training**: Learn from user corrections
- **Privacy**: Option to keep data fully local

### Key Android Features

- **NotificationListenerService**: Capture payment notifications
- **SMS Reader**: Parse bank transaction SMS
- **File Picker**: PDF/CSV uploads
- **Background Processing**: WorkManager for sync
- **Permissions**: SMS, Notifications, Storage

## User Workflows

### First Time Setup

1. Create account (or skip)
2. Add bank accounts/cards
3. Grant SMS & notification permissions
4. Import existing data (optional):
   - Upload bank statement PDF
   - Import CSV from other apps
5. Start tracking automatically!

### Daily Use

1. Make a payment (UPI/card/cash)
2. App auto-captures from notification/SMS
3. Transaction appears instantly
4. AI suggests category (user can accept/change)
5. Review weekly summary
6. Adjust budgets if needed

### Month-End Workflow

1. Upload bank statement PDF (if needed)
2. Match with auto-captured transactions
3. Review "needs review" items
4. Categorize uncategorized (optional)
5. Export report for records
6. Analyze spending patterns

## User Personas

### Primary: "Busy Professional"

- Age: 25-35
- Uses UPI extensively
- Wants automatic tracking
- Checks monthly summaries
- Doesn't want manual entry

### Secondary: "Detail-Oriented Planner"

- Age: 30-45
- Tracks every expense
- Wants detailed categories
- Exports for tax planning
- Reviews weekly

### Tertiary: "Budget-Conscious Student"

- Age: 18-25
- Limited income
- Needs spending alerts
- Wants to control subscriptions
- Prefers free features

## Success Metrics

### User Engagement

- 80% of transactions auto-captured (no manual entry)
- 70% of categories auto-assigned correctly
- <5% uncategorized transactions older than 7 days
- Daily active usage: 60%+ of users open app weekly

### Technical Performance

- SMS/notification capture: <2 seconds delay
- PDF import: <10 seconds for 100 transactions
- App launch: <1 second cold start
- Categorization accuracy: >85%

### User Satisfaction

- Reduce manual entry time by 90%
- User rates accuracy >4/5 stars
- <3 taps to review weekly summary
- Zero forced fields or mandatory categories

## Monetization (Future)

**Free Tier**

- All core features
- Up to 3 accounts
- Basic categorization
- Manual PDF/CSV import
- Local data only

**Premium Tier** ($2-5/month)

- Unlimited accounts
- Advanced AI categorization
- Cloud backup & sync
- Custom reports & exports
- Priority support
- Scheduled PDF import

## Development Phases

### Phase 1: Foundation (Current)

- Basic transaction management
- Manual entry with flexible fields
- Database schema with AI extensions
- Account management

### Phase 2: Automatic Capture

- SMS parser for top banks
- Notification listener for UPIs
- PDF import with password support
- Transaction matching/deduplication

### Phase 3: AI Intelligence

- ML model for categorization
- Merchant recognition system
- Pattern detection
- Learning from corrections

### Phase 4: Insights & Reports

- Spending analysis
- Budget tracking
- Trend detection
- Export features

### Phase 5: Polish & Launch

- UI/UX refinements
- Performance optimization
- Beta testing
- Play Store release

## Competitive Advantages

1. **Least Manual Effort**: 90% auto-capture vs 10% competition
2. **No Forced Fields**: Optional categorization vs mandatory
3. **Password-Protected PDFs**: Others require unprotected files
4. **Smart SMS Parsing**: Handles all Indian bank formats
5. **Privacy-First**: Local-first, cloud optional
6. **Free Core Features**: Not freemium trap
7. **AI Learning**: Gets better with use

## Technology Stack Summary

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: Clean Architecture, MVI
- **Database**: Room SQLite
- **Async**: Coroutines + Flow
- **DI**: Hilt (Dagger)
- **Navigation**: Compose Navigation
- **PDF**: PdfBox-Android
- **ML**: TensorFlow Lite + Cloud APIs
- **Testing**: JUnit, Espresso, Compose Test

---

**Version**: 1.0.0
**Target Launch**: Q4 2026
**Platform**: Android (iOS future)
