# 💧 Water Can Tracker — Android App

A **production-ready** Android application built with Kotlin + Jetpack Compose to manage shared
water can expenses in offices, apartments, hostels, or any shared workplace.

---

## Screenshots / Screens

| Screen | What it does |
|--------|-------------|
| **Dashboard** | Hero "Next to Pay" card, last payment, monthly summary, member count |
| **Payments** | Searchable/filterable history list with add / edit / delete |
| **Add Payment** | Full form: member selector, quantity, amount, date picker, vendor, notes |
| **Members** | Rotation-ordered list with stats, queue re-ordering, skip/next-payer override |
| **Reports** | Balance tracker, monthly table, contributions, CSV/Excel/PDF export |
| **Settings** | Light/Dark/System theme, notification toggles, monthly reset |
| **Widget** | "Who Pays Next" home-screen widget (Glance) |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository + StateFlow |
| Database | Room 2.6 |
| DI | Hilt 2.51 |
| Background | WorkManager (Hilt-Worker) |
| Widget | Glance AppWidget (Compose-based) |
| Image loading | Coil 2 |
| CSV export | OpenCSV |
| Excel export | Apache POI |
| PDF export | Android PdfDocument (built-in) |
| Min SDK | 29 (Android 10) |
| Target SDK | 34 (Android 14) |

---

## Project Structure

```
app/src/main/java/com/watercantracker/app/
├── WaterCanTrackerApp.kt          ← Hilt + notification channels
├── MainActivity.kt                ← Edge-to-edge, Compose host, bottom nav
│
├── data/
│   ├── local/
│   │   ├── WaterCanDatabase.kt    ← Room DB (+ widget getInstance)
│   │   ├── dao/                   ← MemberDao, PaymentDao, SettingsDao, NotificationDao
│   │   └── entity/                ← Room @Entity data classes
│   ├── repository/                ← MemberRepository, PaymentRepository, SettingsRepository
│   └── export/                    ← ExportManager (CSV / Excel / PDF)
│
├── di/                            ← Hilt modules (DB, Worker, Export)
│
├── domain/model/                  ← MemberStats, NextPayerResult, MonthlySpendingSummary …
│
├── notification/                  ← ReminderWorker (WorkManager), BootCompletedReceiver
│
├── widget/                        ← WhoPaysNextWidget (Glance), WhoPaysNextWidgetReceiver
│
└── ui/
    ├── theme/                     ← Color, Type, Shape, Theme (light + dark)
    ├── navigation/                ← Screen sealed class, NavGraph, BottomNavItems
    ├── components/                ← MemberAvatar, StatChip, ConfirmDialog, EmptyState …
    └── screens/
        ├── dashboard/             ← DashboardScreen + DashboardViewModel
        ├── payments/              ← PaymentsScreen, AddEditPaymentScreen, PaymentsViewModel
        ├── members/               ← MembersScreen, AddEditMemberScreen, MembersViewModel
        ├── reports/               ← ReportsScreen + ReportsViewModel
        └── settings/              ← SettingsScreen + SettingsViewModel
```

---

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API 34

### Steps

1. **Clone / unzip** this project into your Android Studio workspace.

2. **Open** the `WaterCanTracker` folder in Android Studio.

3. **Sync Gradle** — Android Studio will download all dependencies automatically.
   If you see a proxy/SSL error, try:
   ```
   File → Settings → Build → Gradle → Use Gradle from 'wrapper'
   ```

4. **Run** on an emulator (API 29+) or physical device:
   ```
   Run → Run 'app'   (Shift+F10)
   ```

5. **First launch**:
   - The app requests notification permission (Android 13+).
   - WorkManager schedules daily reminder checks automatically.

### Build for release
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

Sign with your keystore before distributing.

---

## Key Design Decisions

### Rotation logic
The "next payer" is resolved in `MemberRepository.resolveNextPayer()`:
1. **Manual override** wins — if any member has `isManualNextPayer = true` they go first.
2. **Normal rotation** — members are sorted by `rotationOrder` (integer index). The member
   who comes after the last payment's payer is next. Skipped members are excluded from the
   cycle until un-skipped.

### History preservation
`PaymentEntity.paidByNameSnapshot` stores the payer's name at the time of payment so that
deleting or renaming a member never corrupts history.

### Widget
`WhoPaysNextWidget` (Glance) queries Room directly via a companion `getInstance()` extension
that shares the same database file as Hilt's injection, avoiding duplication.

### Export
- **CSV** — OpenCSV, writes to `Documents/exports/` via `getExternalFilesDir()` (no permission needed on API 29+).
- **Excel** — Apache POI XSSF, same location.
- **PDF** — Android's built-in `android.graphics.pdf.PdfDocument` (no third-party dependency needed for basic output). For richer PDF layouts, swap with iText7 (dependency already in build.gradle).

---

## Customisation

| Want to change | Where |
|----------------|-------|
| Brand colours | `ui/theme/Color.kt` |
| Currency symbol | `SettingsEntity.currencySymbol` + `formatAmount()` in `CommonComponents.kt` |
| Default can price | Settings screen → "Default price per can" (stored in `SettingsEntity`) |
| Reminder schedule | `ReminderWorker.schedule()` — change the 24-hour period |
| Overdue threshold | Settings → stored in `SettingsEntity.overdueThresholdDays` |

---

## Permissions

| Permission | Why |
|-----------|-----|
| `POST_NOTIFICATIONS` | Show payment reminder notifications (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule WorkManager after device restart |
| `SCHEDULE_EXACT_ALARM` | Precise notification delivery |
| `CAMERA` | Attach receipt photos to payments |
| `INTERNET` | Google Drive backup (future) |
| `READ_EXTERNAL_STORAGE` | Import backup file (Android 12 and below only) |

---

## License
MIT — free to use, modify and distribute.
