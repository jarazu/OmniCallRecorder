# C:\Users\jahangir\.gemini\antigravity-ide\scratch\OmniCallRecorder
# OmniCall Recorder: Universal Android Call & VoIP Recorder

**OmniCall Recorder** is an advanced, production-ready native Android application (written in Kotlin) designed to record **both regular cellular phone calls and third-party VoIP calls (such as WhatsApp & WhatsApp Business)** on modern unrooted Android devices (Android 9.0 through Android 14/15).

---

## 🔍 How VoIP & Call Recording Works on Unrooted Android

### 1. The Technical Challenge on Modern Android
Since Android 9 (Pie) and Android 10 (Q):
* **Internal Audio Stream Tapping is Restricted**: Google locked down `AudioSource.VOICE_CALL` and `AudioSource.VOICE_DOWNLINK` exclusively to system-signed apps or rooted devices.
* **AudioPlaybackCapture Policy**: Android's `AudioPlaybackCaptureAPI` explicitly bars capturing audio streams tagged with `USAGE_VOICE_COMMUNICATION` (which WhatsApp, Telegram, and Phone dialers use) unless the app is a system app.
* **Play Store Policy**: Google banned third-party call recorders from using Accessibility APIs on the Google Play Store in May 2022.

### 2. How OmniCall Solves This for Personal & Sideloaded Use
OmniCall implements the battle-tested architecture used by professional sideloaded recorders (e.g., Cube ACR, ACR Phone):

1. **`CallDetectionAccessibilityService`**:
   * Inspects active window transitions and view nodes for `com.whatsapp` and `com.whatsapp.w4b`.
   * Automatically detects when WhatsApp launches a calling activity (`VoipActivityV2`, `CallingActivity`) or displays in-call controls ("End call", "Mute", "Speaker").
   * Automatically triggers recording on connect and stops recording immediately when the call ends.
2. **`CallNotificationListener`**:
   * Intercepts WhatsApp's incoming and ongoing call notifications (`Notification.CATEGORY_CALL`).
   * Extracts the contact name or group name to tag the recording with the caller's actual identity.
3. **`PhoneStateReceiver`**:
   * Monitors `TelephonyManager` broadcasts (`CALL_STATE_RINGING`, `CALL_STATE_OFFHOOK`, `CALL_STATE_IDLE`) for standard SIM-based cellular calls.
4. **`AudioRecorderEngine` with Software Gain Boost**:
   * Records 16-bit PCM at 44.1 kHz via `AudioSource.VOICE_COMMUNICATION` (with fallback to `MIC`).
   * Applies an integrated real-time software gain multiplier (1.8x – 2.0x) with soft-clipping protection. This amplifies the acoustic downlink (the other person's voice picked up by the noise-canceling mic).
   * Automatically compiles raw PCM audio into standard 44-byte RIFF WAV audio files.
5. **`RecordingService` (Foreground Service)**:
   * Holds a high-priority foreground notification and CPU wake-lock so Android cannot kill the recorder during a call.

> 💡 **Pro-Tip for Crystal-Clear Two-Way WhatsApp Audio**:
> On unrooted Android devices, turning on the **Speakerphone** during a WhatsApp or Phone call ensures that both your voice and the caller's voice are captured with 100% clarity!

---

## 📁 Project Architecture & Directory Structure

```
OmniCallRecorder/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Declares foreground mic service, accessibility, & receivers
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml         # Modern dark dashboard with status, search & player
│   │   │   │   ├── activity_permissions.xml  # Interactive 5-step permissions wizard
│   │   │   │   └── item_recording.xml        # Recording card with WhatsApp/Phone badges
│   │   │   ├── xml/
│   │   │   │   ├── accessibility_service_config.xml # Accessibility hooks for WhatsApp
│   │   │   │   └── file_paths.xml                  # FileProvider paths for sharing audio
│   │   │   └── values/
│   │   │       ├── colors.xml                # Premium dark obsidian & emerald palette
│   │   │       ├── strings.xml
│   │   │       └── themes.xml
│   │   └── java/com/omnicall/recorder/
│   │       ├── OmniCallApplication.kt       # Application class & notification channels
│   │       ├── data/
│   │       │   ├── model/CallRecord.kt      # Room entity for recordings metadata
│   │       │   └── db/
│   │       │       ├── CallRecordDao.kt     # DAO with search, filter, and delete queries
│   │       │       └── AppDatabase.kt       # Room Database singleton
│   │       ├── recorder/
│   │       │   ├── AudioRecorderEngine.kt   # PCM WAV recorder with gain boost
│   │       │   └── RecordingService.kt      # Foreground recording service
│   │       ├── services/
│   │       │   ├── CallDetectionAccessibilityService.kt # WhatsApp VoIP detection
│   │       │   └── CallNotificationListener.kt         # Caller ID extraction from alerts
│   │       ├── receivers/
│   │       │   └── PhoneStateReceiver.kt    # Standard cellular call state receiver
│   │       ├── ui/
│   │       │   ├── MainActivity.kt          # Dashboard, filters, in-app audio player
│   │       │   ├── PermissionsActivity.kt   # Permission onboarding manager
│   │       │   └── RecordingsAdapter.kt     # Smooth list adapter with play/pause
│   │       └── util/
│   │           ├── AudioPlayerManager.kt    # MediaPlayer wrapper with seek & controls
│   │           ├── DateTimeUtils.kt         # Formats timestamps, duration & file sizes
│   │           └── FileUtils.kt             # Audio export & file sharing
│   └── build.gradle.kts                     # App dependencies (Room, Coroutines, Material3)
├── build.gradle.kts                         # Project level gradle
├── settings.gradle.kts                      # Module settings
└── README.md                                # Full documentation
```

---

## 🚀 How to Build and Run in Android Studio

### Step 1: Open Project in Android Studio
1. Launch **Android Studio** (Hedgehog, Iguana, Koala, or Ladybug).
2. Click **File -> Open...** and select the folder:
   `C:\Users\jahangir\.gemini\antigravity-ide\scratch\OmniCallRecorder`
3. Allow Gradle to synchronize dependencies.

### Step 2: Connect Your Android Device
1. On your Android phone, enable **Developer Options**:
   * Go to **Settings -> About Phone -> Tap "Build Number" 7 times**.
2. Go to **Settings -> Developer Options -> Turn on "USB Debugging"**.
3. Connect your phone via USB cable and allow USB debugging when prompted.

### Step 3: Run the Application
1. In Android Studio, select your physical phone in the device dropdown.
2. Click the green **Run (▶)** button or press `Shift + F10`.

---

## ⚙️ Initial Device Configuration (Permissions Setup)

When you first launch the app:
1. Tap the **Setup** button on the top right.
2. Complete the 5 steps:
   * **Microphone Access**: Tap "Grant" -> Allow while using app.
   * **Phone State**: Tap "Grant" -> Allow.
   * **WhatsApp Call Hook (Accessibility)**: Tap "Enable" -> It opens your phone's Accessibility settings. Scroll to **Downloaded Apps / Installed Services** -> Select **OmniCall WhatsApp & VoIP Call Detector** -> Turn it **ON**.
   * **Notification Listener**: Tap "Enable" -> It opens Notification Access -> Toggle **OmniCall Notification Call Listener** to **Allowed**.
   * **Disable Battery Saver**: Tap "Allow" -> Exclude OmniCall from battery restrictions so Android's background manager doesn't kill it during long calls.

---

## 🎧 Features Included

* **Automatic WhatsApp VoIP Recording**: Starts when a WhatsApp voice call connects, stops automatically when you hang up.
* **Automatic Cellular Phone Recording**: Detects regular phone calls (incoming and outgoing).
* **Smart Caller ID**: Captures caller names and phone numbers directly into the call log.
* **Built-in Audio Player**: Play, pause, seek, and scrub through call recordings with live duration counters.
* **Instant Audio Sharing**: Share recordings via WhatsApp, Email, Drive, or Bluetooth with one tap.
* **Search & Filter**: Filter by "All", "WhatsApp", or "Cellular", or search by caller name or phone number.
* **Storage Location**: Recordings are saved locally in standard `.wav` format in:
  `Android/data/com.omnicall.recorder/files/Music/CallRecordings/`
