# SMS Guard: Hybrid Neuro-Symbolic On-Device SMS Fraud Detection

[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue.svg)](https://developer.android.com/about/dashboards)
[![Android Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green.svg)](https://developer.android.com/about/versions/14)
[![Language](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Runtime](https://img.shields.io/badge/ONNX%20Runtime-Android-blueviolet.svg)](https://onnxruntime.ai/)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVI%2FMVVM-orange.svg)]()

**SMS Guard** is an advanced, privacy-first, on-device SMS fraud and smishing detection system designed for Android. Combining deterministic symbolic pattern matching with deep transformer-based language representations (DistilBERT via Microsoft ONNX Runtime), URL threat intelligence, and local sender context, SMS Guard detects complex financial phishing, OTP theft, and social engineering attacks in real time without sending message content or telemetry off the device.

---

## 1. System Architecture

The core of SMS Guard is a **Hybrid Neuro-Symbolic** cascading pipeline designed to balance sub-millisecond execution for clear-cut cases with transformer-level deep linguistic analysis for sophisticated scams.

```
Incoming SMS
    │
    ▼
┌──────────────────────────────────────────────┐
│             Text Preprocessor                │
│  - Unicode NFKC normalization                │
│  - Whitespace collapsing                     │
│  - Canonical masking: <URL>, <PHONE>, <OTP>  │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│        Rule Intelligence Engine              │
│  - 8 Deterministic Regex Pattern Families    │
│  - Urgency, Credential Harvesting, Financial │
│  - Impersonation, Delivery Scams, etc.       │
└──────────────────────┬───────────────────────┘
                       │
       ┌───────────────┼───────────────┐
       ▼               ▼               ▼
┌──────────────┐┌──────────────┐┌──────────────┐
│  Content ML  ││     URL      ││Sender Context│
│    Engine    ││ Intelligence ││    Engine    │
│ (DistilBERT  ││- Bloom filter││- Contacts DB │
│  ONNX INT8)  ││- TLD risk    ││- Reg. Sender │
│              ││- Shorteners  ││- History risk│
└──────┬───────┘└──────┬───────┘└──────┬───────┘
       └───────────────┼───────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│               Score Fusion                   │
│   Empirically locked weighted linear sum     │
│   + Bounded Rule Intelligence Delta          │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│             Decision Controller              │
│   - Confidence Gate verification (0.50)      │
│   - 4-Tier State Cascade                     │
│   (PASS / PASS-WATCH / ESCALATE / BLOCK)     │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│         Notification & Inbox Routing         │
│   - System Notification & Warning Badge      │
│   - Explainable breakdown for user audit     │
└──────────────────────────────────────────────┘
```

---

## 2. Core Detection Components

### 2.1. Text Preprocessor
Normalizes raw SMS payloads before feeding downstream analyzers:
- Unicode NFKC normalization and control character stripping.
- Masking of sensitive variable-length entities with canonical tokens (`<URL>`, `<PHONE>`, `<OTP_CODE>`) to ensure the downstream content transformer learns syntactic and urgency patterns rather than overfitting to specific phone numbers or domains.

### 2.2. Rule Intelligence Engine (Symbolic Layer)
Applies deterministic pattern recognition against 8 distinct fraud families:
- **Urgency Phrase**: Explicit time-pressure tactics ("act within 24 hours", "immediate response required").
- **Financial Keywords**: Unauthorized transaction claims, lotteries, and unexpected windfall promises.
- **Credential Harvesting**: Requests to verify or update accounts, KYC, PAN, passwords, or PINs.
- **Authority / Bank Impersonation**: Spoofed bank alerts, government bodies, and tax agencies.
- **Delivery Scams**: False delivery hold / missed package alerts requiring link clicks.
- **Account Action**: Requests to unblock or verify suspended user accounts.

### 2.3. Content ML Engine (Neural Layer)
- **Model**: DistilBERT (`distilbert-base-uncased`) fine-tuned for binary SMS fraud detection.
- **On-Device Inference**: Executed via Microsoft ONNX Runtime Android (`onnxruntime-android`).
- **Dynamic INT8 Quantization**: Quantized for edge execution, drastically reducing memory footprint while maintaining inference parity with the unquantized FP32 baseline.
- **Standalone Model Threshold**: `0.25`.

### 2.4. URL Intelligence Engine
Analyzes hyperlinked content embedded within messages:
- Extracts HTTP/HTTPS domains and IP patterns.
- High-risk Top-Level Domain (TLD) evaluation.
- Known URL shortener identification (`bit.ly`, `tinyurl.com`, `is.gd`, etc.).
- Bloom Filter domain matching for zero-latency local blacklist checks.

### 2.5. Sender Context Engine
Resolves relationship signals from on-device Android Providers:
- Android Contacts Provider query (verifying whether sender is an existing saved contact).
- Standard registered business sender header verification (e.g., alphanumeric headers).
- Local historical interaction frequency and previous user feedback.

---

## 3. Mathematical Score Fusion & Decision Thresholds

### Frozen Score Fusion Formula
The overall fraud risk is computed via an empirically locked fusion equation combining the neural model, URL risk, sender context, and a bounded rule adjustment delta:

$$\text{risk\_final} = \text{clip}\left(0.30 \times s_{\text{ml}} + 0.30 \times r_{\text{url}} + 0.40 \times r_{\text{sender}} + \Delta_{\text{rule}}, \; 0.0, \; 1.0\right)$$

Where the symbolic rule delta is calculated as:

$$\Delta_{\text{rule}} = \text{clip}\left((s_{\text{rule}} - 0.50) \times 0.20, \; -0.15, \; +0.15\right)$$

### Decision Controller Operating Thresholds
The final decision is resolved through a confidence-gated 4-tier decision cascade:
- **Confidence Gate**: Minimum confidence score of `0.50`. If uncertainty exceeds this limit, the system flags high uncertainty for audit.
- **Decision States**:
  - `PASS`: $\text{risk\_final} \le 0.25$ — Message is deemed safe; standard inbox routing.
  - `PASS-WATCH`: $0.25 < \text{risk\_final} \le 0.45$ — Low-risk message with minor indicators; monitored without intrusive warnings.
  - `ESCALATE`: $0.45 < \text{risk\_final} \le 0.65$ — Medium-risk or suspicious sender; warning badge and explanation provided.
  - `BLOCK`: $\text{risk\_final} > 0.65$ — High-risk fraud / smishing attempt; notification alert and moved to Fraud Inbox.

---

## 4. Privacy & Zero-Telemetry Architecture

SMS Guard is engineered from the ground up to respect Android user privacy:
- **Zero Cloud Egress**: Zero SMS data, message content, tokens, sender numbers, or analysis metrics are transmitted to any remote server.
- **Content Provider URI Pointer Strategy**: To avoid creating a duplicate insecure storage of personal SMS messages, `MessageRefEntity` in the Room Database stores only metadata and local system URI pointers (`content://sms/...`). The app resolves message bodies on-demand only for user presentation.
- **Union Schema Local Audit**: The `AuditLogEntity` logs detection decisions, timestamp, latencies, and component scores locally for security audits and user transparency.

---

## 5. Android Application & User Interface

Built with modern **Jetpack Compose** and **Material Design 3**, following Android Clean Architecture principles with MVVM / MVI patterns and Dagger Hilt dependency injection.

### Application Screens
1. **Onboarding**: Runtime permission requests (`RECEIVE_SMS`, `READ_SMS`, `READ_CONTACTS`, `POST_NOTIFICATIONS`) and privacy disclaimers.
2. **Dashboard**: System health status, protection toggle, real-time fraud counters, and recent scan history.
3. **Fraud Inbox**: Filtered list of blocked and escalated fraudulent messages with quick quarantine management.
4. **Safe Inbox**: Clean repository of verified benign SMS traffic.
5. **Message Detail & Explainability**: Granular visual breakdown of detection results, displaying individual engine scores (ML score, URL risk, sender reliability, rule triggers) and actionable user guidance.
6. **Settings**: Background protection configuration, notifications, sensitivity tuning, and data export.
7. **Audit Log**: Chronological inspection of pipeline actions, timestamps, and confidence ratings.
8. **Developer Mode**: In-app pipeline inspection, model variant indicators, and benchmark simulation tools.

---

## 6. Technology Stack

| Domain | Technology / Library |
|---|---|
| **Language** | Kotlin 2.0 (JVM 17 Target) |
| **UI Framework** | Jetpack Compose (BOM 2024.04.01) + Material 3 |
| **Architecture** | Single-Activity, Navigation Compose, Clean Architecture |
| **Dependency Injection** | Dagger Hilt 2.51.1 |
| **Persistence** | Room Database 2.6.1 + KSP |
| **ML Inference** | Microsoft ONNX Runtime Android (`com.microsoft.onnxruntime:onnxruntime-android:1.17.0`) |
| **Async & Concurrency** | Kotlinx Coroutines 1.8.0 + StateFlow |
| **Serialization** | Kotlinx Serialization JSON 1.6.3 |
| **Testing** | JUnit 4, MockK, AndroidX Test Runner, Compose UI Test |
| **Build System** | Gradle 8.7, Android Gradle Plugin 8.4.1 |

---

## 7. Build Variants & Product Flavors

The project defines two product flavors under the `modelVariant` dimension:

| Flavor | Target ABI | Included Model | Typical Use Case |
|---|---|---|---|
| **`device`** | `arm64-v8a` | Dynamic INT8 Quantized (`distilbert_fraud_dynamic_int8.onnx`, ~67 MB) | Production release on real Android smartphones. Minimal APK footprint and fast inference. |
| **`research`** | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` | Full FP32 Baseline (`distilbert_fraud.onnx`, ~268 MB) | Research benchmarking, parity validation against Python baseline, and emulator ablation studies. |

---

## 8. Setup and Build Instructions

### Prerequisites
- **Git** with **Git LFS** installed:
  ```bash
  git lfs install
  ```
- **Android Studio** (Koala / Iguana / Jellyfish or newer).
- **Java Development Kit (JDK)**: JDK 17.
- **Android SDK**: API Level 34 (Android 14) with Build Tools installed.

### Cloning the Repository
Because the repository uses Git LFS to track ONNX models, ensure you clone with Git LFS enabled:
```bash
git clone https://github.com/Tejas2913/sms-fraud-detector.git
cd sms-fraud-detector
git lfs pull
```

### Building via Command Line (Gradle)

#### 1. Assemble Device Debug APK
Builds the lightweight on-device flavor using the INT8 quantized model:
```bash
# Windows
.\gradlew.bat assembleDeviceDebug

# Linux / macOS
./gradlew assembleDeviceDebug
```
Output APK location: `app/build/outputs/apk/device/debug/app-device-debug.apk`

#### 2. Assemble Device Release APK
Builds the optimized, minified release APK with ProGuard/R8:
```bash
# Windows
.\gradlew.bat assembleDeviceRelease

# Linux / macOS
./gradlew assembleDeviceRelease
```
Output APK location: `app/build/outputs/apk/device/release/app-device-release.apk`

#### 3. Assemble Research Flavor
Builds the research variant containing the FP32 unquantized model:
```bash
.\gradlew.bat assembleResearchDebug
```

#### 4. Run Unit Test Suite
Executes the comprehensive suite of 235 unit tests covering tokenization, regex rules, URL extraction, score fusion, and decision gating:
```bash
# Windows
.\gradlew.bat testDeviceDebugUnitTest

# Linux / macOS
./gradlew testDeviceDebugUnitTest
```

---

## 9. Research Parity & Validation

The repository includes a dedicated `parity/` suite to verify algorithmic parity between the Python research pipeline and the on-device Kotlin/Android implementation across 11,165 test messages:
- `parity/01_generate_python_reference.py`: Computes the authoritative Python reference outputs for FP32 and INT8 variants.
- `parity/02_compare_python_android.py`: Performs row-by-row tolerance checking across all 10 cascade steps.
- `hybrid_test_predictions.csv`: Frozen benchmark dataset containing 11,165 evaluated SMS messages from public research corpora (UCI SMS Spam Collection, SMSPhishCorpus, and synthetic templates).

---

## 10. Project Directory Structure

```
sms-fraud-detector/
├── app/
│   ├── build.gradle.kts                   # App module build configuration & flavors
│   ├── proguard-rules.pro                 # R8 / ProGuard optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Permissions & Service/Receiver declarations
│       │   ├── assets/                    # Production assets (INT8 ONNX model, vocab, configs)
│       │   ├── java/com/smsfraud/detector/
│       │   │   ├── benchmark/             # On-device latency benchmarking harness
│       │   │   ├── data/                  # Room database, DAOs, entities, SMS ContentResolver
│       │   │   ├── di/                    # Dagger Hilt dependency injection modules
│       │   │   ├── domain/                # Engine contracts & data models
│       │   │   ├── engine/                # Core engines (Rules, ML, URL, Sender, Fusion, Decision)
│       │   │   ├── ml/                    # ONNX Runtime wrapper & WordPiece BertTokenizer
│       │   │   ├── preprocessing/         # Text preprocessor & Unicode normalization
│       │   │   ├── service/               # Background SMS receiver & Notification manager
│       │   │   ├── ui/                    # Jetpack Compose UI (8 screens, ViewModels, Themes)
│       │   │   └── util/                  # Background protection, audit exporters, date utilities
│       │   └── res/                       # App resources (values, colors, icons, XML)
│       ├── research/assets/               # Research assets (FP32 ONNX model)
│       └── test/                          # Comprehensive JUnit unit test suite
├── deployment_package/                    # Standalone exported models, tokenizers, and configs
├── docs/                                  # Specifications, architecture diagrams, user journey
├── gradle/                                # Gradle wrapper and libs.versions.toml version catalog
├── parity/                                # Python vs Android parity validation harness & outputs
├── 04_Hybrid_Neuro_Symbolic_Architecture.ipynb  # Architecture training & exploration notebook
├── 05_On_Device_Deployment_And_Optimization_v2.ipynb # Quantization & deployment notebook
├── build.gradle.kts                       # Root project build configuration
├── gradle.properties                      # JVM args and AndroidX configuration
├── gradlew.bat                            # Windows Gradle wrapper script
└── settings.gradle.kts                    # Project repositories & module settings
```

---

## 11. License & Academic Attribution

This project was developed as part of an MSc research dissertation on **Hybrid Neuro-Symbolic On-Device SMS Fraud Detection**. All implementations of detection rules, fusion logic, and neural cascades are provided for academic and cybersecurity research purposes.
