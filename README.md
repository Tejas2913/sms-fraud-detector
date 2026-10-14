# SMS Guard: On-Device SMS Fraud & Phishing Detection for Android

[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue.svg)](https://developer.android.com/about/dashboards)
[![Android Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green.svg)](https://developer.android.com/about/versions/14)
[![Language](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Runtime](https://img.shields.io/badge/ONNX%20Runtime-Android%201.17-blueviolet.svg)](https://onnxruntime.ai/)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVI%2FMVVM-orange.svg)]()

**SMS Guard** is a native Android application providing real-time, privacy-preserving SMS fraud and phishing (smishing) defense. Designed to execute entirely on-device with **zero external telemetry**, SMS Guard employs a hybrid neuro-symbolic pipeline that combines deterministic pattern-matching rules, a fine-tuned DistilBERT transformer running via Microsoft ONNX Runtime, local URL risk heuristics, and Android Contacts context.

---

## 1. System Architecture

The detection engine operates as a multi-stage cascading pipeline designed to balance fast execution with deep linguistic understanding.

```
Incoming SMS Broadcast
        │
        ▼
┌────────────────────────────────────────────────────────┐
│                  Text Preprocessor                     │
│  - Unicode NFKC normalization                          │
│  - Whitespace collapsing                               │
│  - Canonical token masking: <URL>, <PHONE>, <OTP_CODE> │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             Rule Intelligence Engine                   │
│  - 8 Deterministic Regex Pattern Families              │
│  - Urgency, Credential Harvesting, Financial Scams     │
│  - Impersonation, Delivery Holds, Account Action       │
└───────────────────────────┬────────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
┌──────────────────┐┌──────────────┐┌──────────────┐
│    Content ML    ││     URL      ││Sender Context│
│      Engine      ││ Intelligence ││    Engine    │
│  DistilBERT ONNX ││- Bloom filter││- Contacts DB │
│   Dynamic INT8   ││- TLD risk    ││- Reg. Sender │
│  (or FP32 flavor)││- Shorteners  ││- History risk│
└──────────┬───────┘└──────┬───────┘└──────┬───────┘
            └───────────────┼───────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                    Score Fusion                        │
│   Empirically locked weighted linear combination       │
│   + Bounded Rule Intelligence Delta                    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                 Decision Controller                    │
│   - Confidence Gate verification (threshold: 0.50)     │
│   - 4-Tier State Cascade:                              │
│     PASS / PASS-WATCH / ESCALATE / BLOCK               │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│            Notification & Storage Routing              │
│   - System Notification & Warning Badges               │
│   - Local Room DB persistence (URI pointer model)      │
│   - Granular Explainability breakdown for user audit   │
└────────────────────────────────────────────────────────┘
```

---

## 2. Detection Engines

### 2.1. Text Preprocessor
Prepares raw SMS messages for downstream analysis:
- Normalizes text via Unicode NFKC and strips non-printable control characters.
- Replaces phone numbers, URLs, and numeric OTP sequences with canonical placeholder tokens (`<URL>`, `<PHONE>`, `<OTP_CODE>`). This forces the content ML model to evaluate linguistic structures and urgency phrases rather than overfitting to specific phone numbers or domains.

### 2.2. Rule Intelligence Engine (Symbolic Layer)
Applies deterministic pattern recognition against 8 distinct fraud patterns:
- **Urgency Phrase**: Explicit time-pressure cues (e.g., immediate action required, deadlines).
- **Financial Keywords**: Lottery wins, unexpected windfalls, and fraudulent monetary transactions.
- **Credential Harvesting**: Requests to verify or update accounts, KYC, passwords, or PINs.
- **Authority / Bank Impersonation**: Spoofed government, tax authority, or financial institution alerts.
- **Delivery Scams**: Package hold and missed shipment notices with embedded links.
- **Account Action**: Requests to unfreeze or reactivate suspended user accounts.

### 2.3. Content ML Engine (Neural Layer)
- **Model**: DistilBERT (`distilbert-base-uncased`) fine-tuned for binary SMS fraud detection.
- **Inference Runtime**: Microsoft ONNX Runtime Android (`onnxruntime-android`).
- **Quantization**: Dynamic INT8 quantized ONNX model for mobile edge inference, reducing memory usage and inference latency while maintaining FP32 parity.
- **Standalone Model Threshold**: `0.25`.

### 2.4. URL Intelligence Engine
Analyzes hyperlinked content embedded within messages:
- Extracts HTTP/HTTPS domains and IP hostnames.
- Evaluates high-risk Top-Level Domains (TLDs).
- Identifies known URL shortener services (`bit.ly`, `tinyurl.com`, `is.gd`, etc.).
- Performs domain checks using an on-device Bloom filter.

### 2.5. Sender Context Engine
Integrates local Android device signals:
- Resolves sender against the Android Contacts Provider. Messages from existing contacts receive a lower sender risk.
- Identifies registered alphanumeric business sender headers.
- Tracks local historical interaction counts and previous user feedback.

---

## 3. Score Fusion & Decision Cascade

### Score Fusion Formula
The overall fraud risk score is computed through an empirically locked weighted linear equation:

$$\text{risk\_final} = \text{clip}\left(0.30 \times s_{\text{ml}} + 0.30 \times r_{\text{url}} + 0.40 \times r_{\text{sender}} + \Delta_{\text{rule}}, \; 0.0, \; 1.0\right)$$

Where the symbolic rule delta is calculated as:

$$\Delta_{\text{rule}} = \text{clip}\left((s_{\text{rule}} - 0.50) \times 0.20, \; -0.15, \; +0.15\right)$$

### Decision States & Thresholds
The final operational verdict is resolved through a confidence-gated 4-tier decision cascade:
- **Confidence Gate**: Minimum confidence rating of `0.50`. If uncertainty exceeds this limit, the system flags the message for audit.
- **Decision States**:
  - `PASS`: $\text{risk\_final} \le 0.25$ — Benign message; standard delivery.
  - `PASS-WATCH`: $0.25 < \text{risk\_final} \le 0.45$ — Low-risk message with minor indicators; monitored without intrusive warnings.
  - `ESCALATE`: $0.45 < \text{risk\_final} \le 0.65$ — Moderate risk or unverified sender; warning badge and explanation provided.
  - `BLOCK`: $\text{risk\_final} > 0.65$ — High-risk fraud / smishing attempt; quarantined to Fraud Inbox with notification alert.

---

## 4. Privacy & Zero-Telemetry Architecture

- **No Remote Telemetry**: SMS text, sender numbers, contact names, and detection scores are never transmitted off the device.
- **URI Pointer Model**: To avoid insecure duplication of personal SMS content in local application storage, the database entity `MessageRefEntity` stores only message metadata and system Content Provider URI references (`content://sms/...`). The application resolves the message text on-demand solely for user display.
- **Local Audit Log**: An encrypted/sandboxed SQLite Room table (`AuditLogEntity`) tracks detection actions, timestamps, latencies, and component scores for user review and transparency.

---

## 5. User Interface & Architecture

The application is built using modern **Jetpack Compose** with Material 3, following Android Clean Architecture principles with MVVM/MVI patterns and Dagger Hilt dependency injection.

### Application Screens
1. **Onboarding**: Explains privacy architecture and requests necessary Android runtime permissions.
2. **Dashboard**: Displays protection status, protection toggle, aggregate scan statistics, and recent activity.
3. **Fraud Inbox**: Filtered list of quarantined and escalated fraudulent messages with management options.
4. **Safe Inbox**: Clean repository of verified benign SMS traffic.
5. **Message Detail & Explainability**: Granular visual breakdown of detection results, displaying individual engine scores (ML score, URL risk, sender reliability, rule triggers) and actionable safety advice.
6. **Settings**: Background protection configuration, sensitivity controls, notification preferences, and audit export.
7. **Audit Log**: Chronological inspection of pipeline actions, timestamps, and confidence scores.
8. **Developer Mode**: In-app pipeline diagnostics, model variant verification, and test simulation tools.

---

## 6. Required Android Permissions

| Permission | Purpose |
|---|---|
| `android.permission.RECEIVE_SMS` | Intercepts incoming SMS broadcasts via `SmsReceiver` for real-time analysis. |
| `android.permission.READ_SMS` | Reads message threads to populate inboxes and display message detail views. |
| `android.permission.READ_CONTACTS` | Resolves sender phone numbers against user contacts to reduce false positives for trusted contacts. |
| `android.permission.POST_NOTIFICATIONS` | Delivers alerts for detected fraud and quarantined messages on Android 13+ (API 33+). |

---

## 7. Technology Stack

| Component | Library / Framework | Version |
|---|---|---|
| **Language** | Kotlin | 2.0.0 |
| **Java Compatibility** | JDK 17 | 17 |
| **UI Toolkit** | Jetpack Compose (BOM) | 2024.04.01 |
| **Material Design** | Material 3 | 1.2.1 |
| **ML Inference** | Microsoft ONNX Runtime Android | 1.17.0 |
| **Dependency Injection** | Dagger Hilt | 2.51.1 |
| **Database** | Room Database | 2.6.1 |
| **Serialization** | Kotlinx Serialization JSON | 1.6.3 |
| **Concurrency** | Kotlinx Coroutines | 1.8.0 |
| **Testing** | JUnit 4, MockK, AndroidX Test | 4.13.2 / 1.13.11 |
| **Build Tooling** | Gradle & Android Gradle Plugin | 8.7 / 8.4.1 |

---

## 8. Build Variants & Model Flavors

The project uses Gradle product flavors under the `modelVariant` dimension:

| Flavor | Target ABI | Model Asset | Use Case |
|---|---|---|---|
| **`device`** (Default) | `arm64-v8a` | Dynamic INT8 Quantized (`distilbert_fraud_dynamic_int8.onnx`, 64.2 MB) | Production release for physical Android smartphones. Minimal APK footprint and fast inference. |
| **`research`** | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` | Full FP32 Baseline (`distilbert_fraud.onnx`, 255.5 MB) | Parity benchmarking against unquantized transformer baselines across emulators and architectures. |

---

## 9. Setup & Build Instructions

### Prerequisites
- **Git** with **Git LFS** installed:
  ```bash
  git lfs install
  ```
- **Android Studio** (Koala / Jellyfish / Iguana or newer).
- **JDK 17**: Ensure `JAVA_HOME` points to a valid JDK 17 installation (or Android Studio's bundled JBR at `Android Studio/jbr`).
- **Android SDK**: API Level 34 with Build Tools.

### Cloning the Repository
Because the repository uses Git LFS to track ONNX model binaries, clone with Git LFS enabled:
```bash
git clone https://github.com/Tejas2913/sms-fraud-detector.git
cd sms-fraud-detector
git lfs pull
```

### Build Commands

#### Assemble Device Debug APK
```bash
# Windows
.\gradlew.bat assembleDeviceDebug

# Linux / macOS
./gradlew assembleDeviceDebug
```
Output: `app/build/outputs/apk/device/debug/app-device-debug.apk`

#### Assemble Device Release APK (Minified / ProGuard)
```bash
# Windows
.\gradlew.bat assembleDeviceRelease

# Linux / macOS
./gradlew assembleDeviceRelease
```
Output: `app/build/outputs/apk/device/release/app-device-release.apk`

#### Assemble Research Flavor APK
```bash
.\gradlew.bat assembleResearchDebug
```

#### Run Unit Test Suite
Executes the comprehensive suite of 235 unit tests covering tokenization, regex rules, URL parsing, score fusion, and decision gating:
```bash
# Windows
.\gradlew.bat testDeviceDebugUnitTest

# Linux / macOS
./gradlew testDeviceDebugUnitTest
```

---

## 10. Limitations

- **Language Scope**: The current text preprocessor and WordPiece tokenizer vocabulary are optimized for English-language SMS communications.
- **SMS Length Boundedness**: Standard SMS messages are bounded at 160 characters (concatenated messages up to typical limits). The tokenizer sequence length is fixed at 128 tokens.
- **Messaging Scope**: Analyzes SMS messages received via the Android telephony provider (`SMS_RECEIVED`). Over-The-Top (OTT) messaging platforms (e.g., WhatsApp, Telegram, Signal) operate on proprietary encrypted protocols and are out of scope.

---

## 11. Project Directory Structure

```
sms-fraud-detector/
├── app/
│   ├── build.gradle.kts                   # Module build configuration & flavors
│   ├── proguard-rules.pro                 # R8 / ProGuard optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Permissions & Service/Receiver declarations
│       │   ├── assets/                    # Production assets (INT8 ONNX model, vocab, configs)
│       │   ├── java/com/smsfraud/detector/
│       │   │   ├── benchmark/             # On-device latency benchmarking harness
│       │   │   ├── data/                  # Room DB, DAOs, entities, SMS ContentResolver
│       │   │   ├── di/                    # Dagger Hilt dependency injection modules
│       │   │   ├── domain/                # Engine contracts & domain models
│       │   │   ├── engine/                # Core engines (Rules, ML, URL, Sender, Fusion, Decision)
│       │   │   ├── ml/                    # ONNX Runtime runner & WordPiece BertTokenizer
│       │   │   ├── preprocessing/         # Text preprocessor & Unicode normalization
│       │   │   ├── service/               # Background SMS receiver & Notification manager
│       │   │   ├── ui/                    # Jetpack Compose UI (8 screens, ViewModels, Themes)
│       │   │   └── util/                  # Background protection, audit exporters, utilities
│       │   └── res/                       # App resources (values, colors, icons, XML)
│       ├── research/assets/               # Research assets (FP32 ONNX model)
│       └── test/                          # Unit test suite (235 tests)
├── deployment_package/tokenizer/          # Tokenizer vocabulary & configs for unit tests
├── docs/                                  # Architecture and user journey diagrams (.png)
├── gradle/                                # Gradle wrapper and libs.versions.toml version catalog
├── .gitattributes                         # Git LFS tracking rules for ONNX models
├── .gitignore                             # Android Studio, Gradle, and local file exclusions
├── build.gradle.kts                       # Root project build configuration
├── gradle.properties                      # JVM args and AndroidX configuration
├── gradlew.bat                            # Windows Gradle wrapper script
├── README.md                              # Application documentation
└── settings.gradle.kts                    # Project repositories & module settings
```

---

## 12. License

This project is licensed under the Apache License 2.0. See the LICENSE file for details.
