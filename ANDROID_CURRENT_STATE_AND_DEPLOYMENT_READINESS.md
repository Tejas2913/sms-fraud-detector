# Android Current State & Deployment Readiness

## 1. Executive Summary

This document provides a comprehensive technical audit of the **SMS Guard** Android application (`c:\Users\hp\Desktop\SMS_Fraud`) to evaluate its deployment readiness for the MSc research project: *"Hybrid Neuro-Symbolic On-Device SMS Fraud Detection"*.

### Current System Status
The codebase currently represents **Milestone 1 (Android Foundation)**. It contains a fully configured Gradle project, Jetpack Compose UI skeleton (8 screens), Hilt Dependency Injection setup, and a Room Database (5 tables). The project compiles cleanly and assembles a debug APK (`app/build/outputs/apk/debug/app-debug.apk`).

### What Already Works
- **Android UI & Navigation**: Jetpack Compose single-activity architecture (`MainActivity.kt`, `SmsGuardNavHost.kt`) supporting 8 screens (Onboarding, Dashboard, Fraud Inbox, Safe Inbox, Settings, Audit Log, Developer Mode, Message Detail).
- **Database Layer**: Room database (`AppDatabase.kt`) implementing 5 entities (`MessageRefEntity`, `AuditLogEntity`, `SenderHistoryEntity`, `UserFeedbackEntity`, `SettingsEntity`) and corresponding DAOs and Repositories.
- **Privacy Model Structure**: `MessageRefEntity` correctly avoids storing raw SMS text, storing only lightweight metadata and Content Provider URI pointers (`system_sms_uri`). `AuditLogEntity` implements a union schema.
- **Domain Contracts**: Kotlin interfaces for `RuleEngine`, `MlEngine`, `UrlEngine`, `SenderContextEngine`, `ScoreFusion`, and `DecisionController` exist in `com.smsfraud.detector.domain.engine`.

### What Is Missing or Unimplemented
1. **Engine Implementations**: All 4 engine implementation classes (`RuleEngineImpl`, `MlEngineImpl` / ONNX runner, `UrlEngineImpl`, `SenderContextEngineImpl`), `ScoreFusionImpl`, and `DecisionControllerImpl` are **completely missing**. The `com.smsfraud.detector.engine` package contains only a placeholder marker (`EnginePackageMarker.kt`).
2. **Text Preprocessor**: `TextPreprocessor` is missing. The `com.smsfraud.detector.preprocessing` package contains only a marker (`PreprocessingPackageMarker.kt`).
3. **ML Runtime & Model Assets**: ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`) is **not included** in `app/build.gradle.kts` or `gradle/libs.versions.toml`. No model artifacts (`distilbert_fraud.onnx`, `distilbert_fraud_dynamic_int8.onnx`, `vocab.txt`, `bloom_filter.bin`) exist in `app/src/main/assets/` (the `assets/` folder does not exist).
4. **Tokenizer**: No WordPiece / HuggingFace tokenizer exists in the codebase.
5. **Experimental Benchmarking Harness**: No UI or background task exists to load test datasets (e.g. 11,165 messages), run CSV batch evaluation, or compare Android predictions against Python reference outputs.

### Physical-Device Validation Status
**Physical-device validation has NOT occurred.** Zero ONNX or DistilBERT models have been executed on-device, and zero hybrid decisions have been computed.

### Overall Readiness
**CURRENT STATUS: NOT READY.** The codebase is an Android structural skeleton ready for Milestone 2 engine development.

---

## 2. Research Architecture Reference

The authoritative research architecture is defined as follows:

```
Incoming SMS
    ↓
Text Preprocessor
    ↓
Rule Intelligence Engine (8 Regex Patterns)
    ↓
Content ML Engine (DistilBERT ONNX FP32 / INT8)
    ↓
URL Intelligence Engine (Bloom Filter & TLD Risk)
    ↓
Sender Context Engine (History & Contact Signals)
    ↓
Score Fusion (Locked Weights + Rule Delta)
    ↓
Decision Controller (Confidence Gate = 0.50)
    ↓
PASS / PASS-WATCH / ESCALATE / BLOCK
```

### Frozen Score Fusion Configuration
```
risk_final = 0.30 × ml_score + 0.30 × url_risk + 0.40 × sender_risk + rule_delta

where:
rule_delta = clip((rule_score - 0.50) × 0.20, -0.15, +0.15)
risk_final = clip(risk_final, 0.0, 1.0)
```
*Note: Obsolete default weights (ML=0.40, URL=0.35, Sender=0.25) are strictly superseded by the empirically locked weights (0.30, 0.30, 0.40).*

### Frozen Decision Controller Thresholds
- **Confidence Gate**: `0.50`
- **Operating Thresholds**:
  - `PASS`: `risk_final ≤ 0.25`
  - `PASS-WATCH`: `0.25 < risk_final ≤ 0.45` *(Research Spec)*
  - `ESCALATE`: `0.25 < risk_final ≤ 0.65` *(or between pass_max and escalate_max)*
  - `BLOCK`: `risk_final > 0.65`
- **DistilBERT Standalone Threshold**: `0.25` *(Distinct from Decision Controller `pass_max`)*

---

## 3. Project Structure

```
c:\Users\hp\Desktop\SMS_Fraud\
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/ (values/strings.xml, colors.xml, themes.xml, mipmap/...)
│       └── java/com/smsfraud/detector/
│           ├── MainActivity.kt
│           ├── SmsGuardApp.kt
│           ├── data/
│           │   ├── db/
│           │   │   ├── AppDatabase.kt
│           │   │   ├── dao/ (AuditLogDao, MessageRefDao, SenderHistoryDao, SettingsDao, UserFeedbackDao)
│           │   │   └── entity/ (AuditLogEntity, MessageRefEntity, SenderHistoryEntity, SettingsEntity, UserFeedbackEntity)
│           │   ├── repository/ (AuditLogRepository, FeedbackRepository, MessageRepository, SenderHistoryRepository, SettingsRepository)
│           │   └── sms/ (SmsContentResolver.kt)
│           ├── di/ (AppModule.kt, DatabaseModule.kt, RepositoryModule.kt)
│           ├── domain/
│           │   ├── engine/ (DecisionController.kt, MlEngine.kt, RuleEngine.kt, ScoreFusion.kt, SenderContextEngine.kt, UrlEngine.kt)
│           │   └── model/ (DecisionEvent.kt, DecisionState.kt, FeedbackAction.kt, FusionResult.kt, MlResult.kt, RuleResult.kt, SenderResult.kt, UrlEntry.kt, UrlResult.kt)
│           ├── engine/ (EnginePackageMarker.kt)
│           ├── preprocessing/ (PreprocessingPackageMarker.kt)
│           ├── service/ (ServicePackageMarker.kt)
│           ├── ui/ (auditlog, dashboard, developermode, fraudinbox, messagedetail, navigation, onboarding, safeinbox, settings, theme)
│           └── util/ (DateUtil.kt, HashUtil.kt)
├── docs/
├── gradle/ (libs.versions.toml, wrapper/...)
├── build.gradle.kts
├── gradle.properties
├── local.properties
└── settings.gradle.kts
```

---

## 4. Complete Component Inventory

| Component | Expected Research Role | Current Implementation | Files / Location | Status | Notes |
|---|---|---|---|---|---|
| **Text Preprocessor** | URL extraction, PII scrubbing, text normalisation | Package marker only | `com.smsfraud.detector.preprocessing.PreprocessingPackageMarker` | **MISSING** | Needs `TextPreprocessor` implementation |
| **Rule Intelligence Engine** | 8 regex pattern evaluation, rule score & delta calculation | Interface only | `com.smsfraud.detector.domain.engine.RuleEngine` | **PARTIAL** | Interface defined; `RuleEngineImpl` missing |
| **Content ML Engine** | DistilBERT ONNX FP32/INT8 inference, probability & attention output | Interface only | `com.smsfraud.detector.domain.engine.MlEngine` | **PARTIAL** | Interface defined; ONNX runner missing |
| **URL Intelligence Engine** | Bloom filter check & TLD risk scoring | Interface & UrlEntry model | `com.smsfraud.detector.domain.engine.UrlEngine` | **PARTIAL** | Interface & `UrlEntry` defined; logic missing |
| **Sender Context Engine** | Sender reputation, contact lookup, trusted sender recommendation | Interface only | `com.smsfraud.detector.domain.engine.SenderContextEngine` | **PARTIAL** | Interface defined; scoring logic missing |
| **Score Fusion** | Weighted fusion (0.30, 0.30, 0.40) + rule_delta clipping | Interface only | `com.smsfraud.detector.domain.engine.ScoreFusion` | **PARTIAL** | Interface defined; `ScoreFusionImpl` missing |
| **Decision Controller** | Cross-engine decision, confidence gate, priority resolution | Interface only | `com.smsfraud.detector.domain.engine.DecisionController` | **PARTIAL** | Interface defined; `DecisionControllerImpl` missing |
| **Room Database** | 5-table local storage (MessageRef, AuditLog, SenderHistory, UserFeedback, Settings) | Implemented | `com.smsfraud.detector.data.db.AppDatabase` | **IMPLEMENTED** | Schema v1, 18-field union `AuditLogEntity` |
| **UI Layer** | 8 Compose screens (Dashboard, FraudInbox, Settings, DevMode, etc.) | Implemented | `com.smsfraud.detector.ui.*` | **IMPLEMENTED** | Compose UI skeleton functional |
| **Service Layer** | SMS Receiver, Foreground Service, Decision Event Bus | Package marker only | `com.smsfraud.detector.service.ServicePackageMarker` | **MISSING** | Deferred to Milestone 3 |

---

## 5. ML / ONNX Inference Audit

- **ONNX Runtime Library**: `com.microsoft.onnxruntime:onnxruntime-android` is **NOT declared** in `app/build.gradle.kts` (lines 57-97) or `gradle/libs.versions.toml` (lines 18-60).
- **Model Assets**: `app/src/main/assets/` does not exist. Neither `distilbert_fraud.onnx` nor `distilbert_fraud_dynamic_int8.onnx` is present.
- **Inference Code**: No ONNX `OrtEnvironment`, `OrtSession`, Tensor allocation, shape handling `[batch, seq_len]`, or softmax computation exists in the project.
- **Interface Contract**: `MlEngine.kt` defines `fun predict(normalisedText: String): MlResult`. `MlResult.kt` defines `mlScore: Float`, `mlConfidence: Float`, `attentionWeights: List<Float>`.

---

## 6. Tokenizer Audit

- **HuggingFace / WordPiece Tokenizer**: **MISSING**. No Java/Kotlin tokenizer implementation exists.
- **Vocabulary File**: `vocab.txt` is missing. No asset directory exists.
- **Tokenization Pipeline**: No logic exists for `CLS`/`SEP` token insertion, WordPiece subword splitting, token ID mapping, sequence padding to `max_length=128`, or attention mask array generation (`1` for real tokens, `0` for padding).
- **Parity Status**: **UNTESTED / NON-EXISTENT**.

---

## 7. Rule Intelligence Engine Audit

- **Spec Requirement**: 8 named regex patterns (Notebook 04 authoritative).
- **Interface**: `RuleEngine.kt` defines `fun evaluate(normalisedText: String): RuleResult`.
- **Model**: `RuleResult.kt` defines `RuleAction` (`FORCE_PASS`, `FORCE_BLOCK`, `SCORE`), `ruleLabel: String`, `ruleConfidence: Float`, `ruleScore: Float`.
- **Actual Code Status**: **MISSING**. No pattern definitions, rule matching, or `rule_delta` calculation code exists in `com.smsfraud.detector.engine`.

---

## 8. URL Intelligence Engine Audit

- **Spec Requirement**: URL extraction, parsing, Bloom filter lookup, TLD risk scoring. No live online API calls.
- **Interface**: `UrlEngine.kt` defines `fun evaluate(urlList: List<UrlEntry>): UrlResult`.
- **Models**: `UrlEntry.kt` (`rawUrl`, `domain`, `tld`, `path`, `isShortener`) and `UrlResult.kt` (`urlRisk`, `urlConfidence`, `urlLabel`, `urlPresent`).
- **Actual Code Status**: **MISSING**. Bloom filter binary data, TLD lists, and evaluation algorithms are not implemented.
- **External APIs**: None present in code (matches frozen offline research requirement).

---

## 9. Sender Context Engine Audit

- **Spec Requirement**: Local sender reputation accumulator, contact lookup check, trusted sender recommendation.
- **Interface**: `SenderContextEngine.kt` defines `suspend fun evaluate(senderString: String): SenderResult`.
- **Database Support**: `SenderHistoryEntity.kt` (lines 12-28) and `SenderHistoryDao.kt` provide storage for `messageCount`, `firstSeenAt`, `fraudReportsByUser`, `isRegisteredBusiness`. `SettingsRepository.kt` manages `KEY_CONTACT_LOOKUP_ENABLED`.
- **Actual Code Status**: **MISSING**. Scoring logic combining DB history, contact lookup, and calculating `senderRisk` / `trustedSenderRecommendation` is not implemented.

---

## 10. Score Fusion Audit

- **Frozen Fusion Formula**:
  `risk_final = 0.30 × ml_score + 0.30 × url_risk + 0.40 × sender_risk + rule_delta`
- **Interface**: `ScoreFusion.kt` (lines 14-20) defines:
  ```kotlin
  fun fuse(
      mlResult: MlResult,
      urlResult: UrlResult,
      senderResult: SenderResult,
      ruleResult: RuleResult
  ): FusionResult
  ```
- **Actual Code Status**: **MISSING**. No implementation class exists.

---

## 11. Decision Controller Audit

- **Interface**: `DecisionController.kt` (lines 16-24) defines `suspend fun decide(...)`.
- **Decision States in Code**: `DecisionState.kt` (lines 7-11):
  ```kotlin
  enum class DecisionState {
      PASS,
      ESCALATE,
      BLOCK
  }
  ```
- **Research Specification Deviation**: `DecisionState.kt` excludes `PASS_WATCH` ("Three MVP decision states per Product Spec v4 §11. PASS_WATCH is excluded from MVP — future work only.").
- **Actual Code Status**: **MISSING**. Cross-engine decision logic is not implemented.

---

## 12. UI and User Flow

- **Architecture**: Single Activity (`MainActivity.kt`), Navigation Compose (`SmsGuardNavHost.kt`), Material 3 design.
- **Screens**:
  1. `OnboardingScreen.kt`: 4-page Pager requesting SMS and notification permissions.
  2. `DashboardScreen.kt`: Protection toggle, stat cards, navigation links.
  3. `FraudInboxScreen.kt`: Filter chips, message list powered by Room DB.
  4. `SafeInboxScreen.kt`: Read-only PASS message list.
  5. `MessageDetailScreen.kt`: Shows decision badge, risk score progress bar, signal breakdown rows.
  6. `SettingsScreen.kt`: Toggles, Scan Scope chips, Retention picker dialog, Export button placeholder, 7-tap Developer Mode unlock.
  7. `AuditLogScreen.kt`: Displays historical `AuditLogEntity` records.
  8. `DeveloperModeScreen.kt`: Renders raw per-engine fields from `AuditLogEntity`.
- **Experimental Flow Gap**: The UI currently connects only to system SMS / Room DB queries. It lacks a research test harness screen for typing manual test messages or importing CSV files for evaluation.

---

## 13. Model and Deployment Artifacts

| Artifact | Expected Location | Present? | Purpose | Status |
|---|---|---|---|---|
| `distilbert_fraud.onnx` | `app/src/main/assets/` | **NO** | ONNX FP32 model | **MISSING** |
| `distilbert_fraud_dynamic_int8.onnx` | `app/src/main/assets/` | **NO** | ONNX dynamic INT8 model | **MISSING** |
| `vocab.txt` | `app/src/main/assets/` | **NO** | WordPiece vocabulary | **MISSING** |
| `tokenizer.json` / `tokenizer_config.json` | `app/src/main/assets/` | **NO** | HuggingFace tokenizer config | **MISSING** |
| `bloom_filter.bin` | `app/src/main/assets/` | **NO** | Malicious URL Bloom filter | **MISSING** |
| `tld_risk.json` | `app/src/main/assets/` | **NO** | High-risk TLD list | **MISSING** |

---

## 14. Current End-to-End Data Flow

Tracing a message through the current codebase:
1. **Input**: UI screens (`FraudInboxScreen.kt`, `DashboardScreen.kt`) invoke ViewModels.
2. **Repository**: ViewModels call `MessageRepository.getByState()`.
3. **Database**: `MessageRefDao.getByState()` queries Room table `message_ref`.
4. **Processing Pipeline**: **STOPPED**. Preprocessing, Rule Engine, ML Engine, URL Engine, Sender Engine, Fusion, and Decision Controller do not execute because no engine implementations exist.
5. **Output**: UI displays static/empty database records.

---

## 15. Experimental Capability Audit

| Experiment / Feature | Current Capability | Status | Missing Work |
|---|---|---|---|
| **Single-message inference** | None | **MISSING** | Requires Engine implementations + Test UI |
| **FP32 inference** | None | **MISSING** | Requires ONNX Runtime + FP32 model asset |
| **INT8 inference** | None | **MISSING** | Requires ONNX Runtime + INT8 model asset |
| **Complete hybrid inference** | None | **MISSING** | Requires all 4 engines + Fusion + Controller |
| **Batch inference** | None | **MISSING** | Requires CSV batch processing runner |
| **CSV input** | None | **MISSING** | Requires CSV parser & File Picker |
| **CSV output** | UI placeholder only | **PARTIAL** | Export button in `SettingsScreen` lacks CSV writer |
| **Prediction logging** | DB schema ready | **PARTIAL** | `AuditLogEntity` exists; engine populator missing |
| **Latency measurement** | DB field ready | **PARTIAL** | `inferenceLatencyMs` in `AuditLogEntity`; timing code missing |
| **Memory measurement** | None | **MISSING** | Requires Memory Profiler utility (PSS tracking) |
| **Reference-prediction comparison** | None | **MISSING** | Requires ground-truth validation harness |
| **Decision-parity validation** | None | **MISSING** | Requires parity test suite |

---

## 16. Physical Device Readiness

- **Model loading**: **NOT READY** (No ONNX Runtime dependency, no model assets).
- **Tokenizer**: **NOT READY** (No WordPiece tokenizer code).
- **Assets**: **NOT READY** (`app/src/main/assets` missing).
- **Permissions**: `RECEIVE_SMS`, `READ_SMS`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `READ_CONTACTS` declared in `AndroidManifest.xml` (lines 6-13).
- **Threading**: Kotlin Coroutines configured (`kotlinx-coroutines-android`).
- **Overall Assessment**: **NOT READY for physical-device deployment validation.**

---

## 17. Python ↔ Android Parity Readiness

- **Model Output Parity**: **UNTESTED** (ONNX model cannot run on Android yet).
- **Complete Hybrid Decision Parity**: **UNTESTED** (Engine and Fusion logic missing).
- **Parity Testing Harness**: **MISSING**.

---

## 18. Legacy / Incorrect / Suspicious Components

1. **Decision States**: `DecisionState.kt` defines 3 states (`PASS`, `ESCALATE`, `BLOCK`), omitting `PASS_WATCH`.
2. **Manifest SMS Permissions**: `AndroidManifest.xml` declares `RECEIVE_SMS` and `READ_SMS`. Controlled research evaluation typically uses manual input / CSV batch runner to avoid system SMS permission prompts during lab testing.
3. **Empty Package Markers**: `EnginePackageMarker.kt`, `PreprocessingPackageMarker.kt`, and `ServicePackageMarker.kt` are temporary placeholders.
4. **Database Migration**: `DatabaseModule.kt` uses `.fallbackToDestructiveMigration()`.

---

## 19. Research-System Deviations

| # | Item | Frozen Research System | Current Android Implementation | Impact |
|---|---|---|---|---|
| 1 | **Decision States** | 4 states (`PASS`, `PASS-WATCH`, `ESCALATE`, `BLOCK`) | 3 states (`PASS`, `ESCALATE`, `BLOCK`) in `DecisionState.kt` | Prevents exact 4-state research decision parity |
| 2 | **Engine Layer** | 4 active engines + Preprocessor + Fusion + Controller | Zero implementation classes (Interfaces only) | System cannot perform inference |
| 3 | **Model & Assets** | ONNX FP32, ONNX INT8, `vocab.txt` | Zero assets in `app/src/main/assets/` | Cannot load models |
| 4 | **Fusion Weights** | Locked: `0.30 ML + 0.30 URL + 0.40 Sender` | Unimplemented (Previous docs referenced old 0.40/0.35/0.25) | Must implement exact locked weights in M2 |
| 5 | **Test Input** | CSV batch runner & manual text input | System Content Provider reading only | Cannot execute batch parity tests (11,165 messages) |

---

## 20. Required Changes

### CRITICAL (Blockers for Milestone 2)
1. Add ONNX Runtime dependency (`com.microsoft.onnxruntime:onnxruntime-android:1.18.0`) to `gradle/libs.versions.toml` and `app/build.gradle.kts`.
2. Add assets to `app/src/main/assets/`: `distilbert_fraud.onnx`, `distilbert_fraud_dynamic_int8.onnx`, `vocab.txt`.
3. Implement `BertTokenizer.kt` (WordPiece tokenization, padding to 128, attention mask generation).
4. Implement `RuleEngineImpl.kt` (8 regex patterns from Notebook 04).
5. Implement `MlEngineImpl.kt` (ONNX Runtime session wrapper for FP32 and INT8).
6. Implement `UrlEngineImpl.kt` (Bloom filter & TLD risk calculation).
7. Implement `SenderContextEngineImpl.kt` (Sender history & contact lookup scoring).
8. Implement `ScoreFusionImpl.kt` (Locked formula: `0.30*ML + 0.30*URL + 0.40*Sender + rule_delta`).
9. Implement `DecisionControllerImpl.kt` (Confidence gate 0.50, thresholds 0.25 & 0.65).

### HIGH (Required for Research Validation)
1. Implement `TextPreprocessor.kt` (URL extraction, PII scrubbing, normalisation).
2. Update `DecisionState.kt` to include `PASS_WATCH` for full 4-state research parity.
3. Add a Research Evaluation / Single-Message & CSV Batch Test Screen to the Compose UI.
4. Implement latency measurement (`System.nanoTime()`) and prediction logging into `AuditLogEntity`.

### MEDIUM
1. Implement CSV Export for `AuditLog` in `SettingsViewModel`.
2. Implement basic Memory Profiler (PSS / JVM heap monitoring).

### LOW
1. Clean up placeholder package markers.

---

## 21. Recommended Implementation Order

1. **Phase 2.1**: Dependencies & Model Assets Setup (ONNX Runtime Gradle dependency, `assets/` creation, model & vocab import).
2. **Phase 2.2**: Text Preprocessor & WordPiece Tokenizer (`TextPreprocessor.kt`, `BertTokenizer.kt`).
3. **Phase 2.3**: Rule Intelligence Engine (`RuleEngineImpl.kt` with 8 regex patterns).
4. **Phase 2.4**: Content ML Engine (`MlEngineImpl.kt` with ONNX Runtime FP32/INT8 execution).
5. **Phase 2.5**: URL & Sender Context Engines (`UrlEngineImpl.kt`, `SenderContextEngineImpl.kt`).
6. **Phase 2.6**: Score Fusion & Decision Controller (`ScoreFusionImpl.kt`, `DecisionControllerImpl.kt`).
7. **Phase 2.7**: Research Benchmarking & Parity Test Harness (Single-message & CSV batch test UI).
8. **Phase 2.8**: Unit Testing, End-to-End Integration, Latency & Memory Measurement.

---

## 22. Required Physical-Device Experiments

To fulfill research deployment validation, the application must support measuring:
1. **Model Size**: FP32 ONNX vs INT8 ONNX file sizes.
2. **Inference Latency**: Single-message inference latency (ms) for FP32 vs INT8 on CPU.
3. **Pipeline Latency**: Total pipeline latency (Preprocessing → Rule → ML → URL → Sender → Fusion → Controller).
4. **Memory Footprint**: Peak PSS / RAM usage during model loading and inference.
5. **Model Output Parity**: Standalone ONNX prediction parity against Python outputs across test set.
6. **Full Hybrid Decision Parity**: Complete decision state parity across test set (11,165 messages or stratified subset).

---

## 23. Final Readiness Assessment

- **CURRENT STATUS**: **NOT READY**
- **FIRST NEXT ACTION**: Add ONNX Runtime Android dependency (`com.microsoft.onnxruntime:onnxruntime-android`) to `gradle/libs.versions.toml` and `app/build.gradle.kts`, and place `distilbert_fraud.onnx`, `distilbert_fraud_dynamic_int8.onnx`, and `vocab.txt` into `app/src/main/assets/`.
- **BIGGEST BLOCKER**: Total absence of ONNX Runtime library, model assets, tokenizer, and engine implementation classes.
- **MINIMUM WORK REQUIRED**: Implement ONNX inference session, WordPiece tokenizer, 4 detection engines, fusion math, decision controller, and a research evaluation test runner.

---

## 24. Reviewer Handoff Summary

> **For Independent Reviewer:**
> The `SMS_Fraud` repository represents a clean, fully building **Milestone 1 Android Foundation** (Compose UI + Room DB + Hilt DI + Navigation). The database schema adheres strictly to the frozen specs (5 tables, 18-field union `AuditLogEntity`). However, **no machine learning models, ONNX runtime dependencies, or detection engine implementations currently exist**. All engine packages contain only interface contracts (`domain/engine/`) or placeholder markers (`engine/`). To transition to Milestone 2, the team must add ONNX Runtime, import model/vocab assets into `app/src/main/assets/`, implement the WordPiece tokenizer and 4 detection engines, apply the locked score fusion formula (`0.30*ML + 0.30*URL + 0.40*Sender + rule_delta`), and build a research evaluation test harness for physical-device benchmarking.
