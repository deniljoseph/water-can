# Firebase Setup Guide

The app uses Firebase Realtime Database for free cross-device sync.
Firebase's **Spark (free) plan** includes:
- 1 GB storage
- 10 GB/month transfer  
- Simultaneous connections: 100

This is more than enough for a water can tracker group.

## Steps (5 minutes)

### 1. Create a Firebase project
1. Go to https://console.firebase.google.com
2. Click **"Add project"**
3. Name it `water-can-tracker` (or anything you like)
4. Disable Google Analytics (not needed) → **Create project**

### 2. Add your Android app
1. In the project dashboard, click the **Android icon**
2. Package name: `com.watercantracker.app`
3. App nickname: `Water Can Tracker`
4. Click **Register app**
5. **Download `google-services.json`**
6. Replace the placeholder file at `app/google-services.json` with your downloaded file

### 3. Enable Realtime Database
1. In the Firebase console, go to **Build → Realtime Database**
2. Click **"Create Database"**
3. Choose location closest to you (e.g. `asia-southeast1` for UAE)
4. Start in **test mode** (allows read/write for 30 days)
5. After 30 days, update the rules to:

```json
{
  "rules": {
    "rooms": {
      "$roomId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

### 4. Enable Anonymous Authentication
1. Go to **Build → Authentication → Sign-in method**
2. Enable **Anonymous** sign-in

### 5. Build and install
Push your code to GitHub → Actions will build the APK → install on your phone.

## How sync works in the app
1. **Your device (master)**: Settings → Live Sync → **Create Sync Room**
2. A QR code appears — other members scan it with any QR reader
3. The scanned link opens the app (or they paste the Room ID manually)
4. Data syncs in real time — no account needed on other devices

## Cost
Always free on Firebase Spark plan for small groups (under 100 devices).
No credit card required.
