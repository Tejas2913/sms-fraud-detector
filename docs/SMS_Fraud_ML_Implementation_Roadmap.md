# Hybrid Neuro-Symbolic On-Device SMS Fraud Detection
## ML Implementation Roadmap (Phases 1–9)

Scope note: this roadmap implements the approved architecture exactly as specified in the diagram — Rule Intelligence Engine (always runs first) → Content ML engine (DistilBERT INT8/TFLite), URL intelligence engine (Bloom filter + TLD risk), Sender context engine → Score fusion → Decision controller → Lazy explainability → Local feedback log. No new components, no federated learning, TEE, LoRA, DP-SGD, WHOIS infrastructure, multilingual transformers, or continual learning are introduced anywhere below.

---

## Phase 1 — Dataset Strategy

**Selection.** Combine three public SMS corpora to get coverage of spam, phishing, and benign traffic: UCI SMS Spam Collection (5,574 msgs, EN, baseline spam/ham), SMSPhishCorpus / Naga's phishing SMS dataset (focused on URL-bearing smishing), and a synthetic-augmented OTP/bank-template set built from the regex/keyword families already enumerated in the Rule Engine (so the ML model sees the same fraud patterns the symbolic layer targets, preventing systematic blind spots in the "ML Only" ablation arm). Do not pull in multilingual corpora — out of scope per the architecture.

**Acquisition & merging.** Pull each source into raw/ as-is with a provenance column (`source_dataset`). Merge on a common schema: `{text, label, url_present, source_dataset, timestamp_or_synthetic_flag}`. Labels harmonized to a single binary scheme (`fraud` / `legit`) plus an optional fine-grained `fraud_type` tag (otp_phish, bank_phish, delivery_scam, govt_impersonation, generic_spam) used only for error analysis, not as a training target — the approved model is binary.

**Duplicate removal.** Exact-match dedup on normalized text (lowercased, whitespace-collapsed) first; then near-duplicate removal via MinHash/SimHash (Jaccard > 0.9) to catch template variants ("Your OTP is 1234" vs "Your OTP is 5678") that would otherwise leak across splits.

**Label harmonization.** Map each source's native label set to the binary scheme with an explicit mapping table reviewed once and version-pinned, so relabeling is reproducible rather than ad hoc.

**Dataset statistics.** Generate a stats notebook covering class balance, message length distribution, URL-presence rate per class, language distribution (should be ~100% EN given scope), and per-source contribution — this becomes a dataset-card table in the dissertation.

**Split strategy.** Stratified 70/15/15 train/val/test on the binary label, stratified additionally by `source_dataset` so no split is dominated by one corpus. Synthetic OTP/bank templates are split at the *template* level (not the instantiated-message level) before instantiation, so the same template family never appears in both train and test.

**Temporal split.** Where source timestamps exist (phishing corpora often have collection dates), report a secondary temporal holdout (train on older, test on newer) as a robustness check — fraud language drifts, and this gives you a defensible robustness claim for the write-up even though the primary split is stratified random.

**Leakage prevention.** Dedup must run *before* splitting, not after. Template-level splitting for synthetic data. No feature derived from test-set statistics (e.g., global TF-IDF vocab) may be fit before the split boundary — fit all vectorizers/tokenizer-adjacent statistics on train only.

---

## Phase 2 — Data Preparation

**Cleaning pipeline.** Strip control characters, normalize unicode (NFKC), collapse repeated whitespace, lowercase only for the symbolic-feature extraction path (DistilBERT's own tokenizer handles casing — do not lowercase before the transformer tokenizer, since cased subword units carry signal for entity-like tokens, e.g., bank names).

**URL handling.** This is the one place ML and the URL engine must stay decoupled per the architecture: for the **content model**, URLs in the SMS body are replaced with a single canonical placeholder token (e.g., `<URL>`) so DistilBERT learns from surrounding fraud language, not from memorizing specific domains — domain risk is exclusively the URL engine's job downstream. Extracted URLs are logged separately only for dataset statistics (URL-presence rate), never fed back into the content model as features.

**SMS normalization.** Normalize phone numbers and OTP-like digit sequences to placeholder tokens (`<PHONE>`, `<OTP_CODE>`) to prevent the model overfitting to specific digit strings rather than the surrounding urgency/phishing phrasing — mirrors what the Rule Engine already treats as a pattern family.

**Tokenization strategy.** Use the DistilBERT WordPiece tokenizer (`distilbert-base-uncased`) unchanged — no custom vocabulary, no domain-adapted retraining of the tokenizer (would be scope creep beyond the approved model).

**Max sequence length.** SMS is bounded at 160 chars/message (concatenated MMS-OCR text can run longer but rarely). Empirically set max_length via a token-length histogram on train; 128 tokens covers >99% of messages without truncation — justify this number from the histogram rather than assuming it, and document the truncation rate at whatever length is chosen.

**Class imbalance.** Report the natural class ratio after merge; if skewed, use class-weighted cross-entropy during fine-tuning (weight inversely proportional to class frequency) rather than oversampling/undersampling, which would distort the duplicate-controlled split. Class weights are computed from the train split only.

**Feature preparation.** Content model: tokenized IDs + attention mask only — no engineered features mixed into the transformer input (keeps the "ML Only" ablation arm a clean signal). Baselines (Phase 4): TF-IDF features computed independently with their own fit-on-train vectorizer.

---

## Phase 3 — DistilBERT Training Pipeline

**Model loading.** `distilbert-base-uncased` from Hugging Face, classification head with 2 output logits (binary). This is the fixed approved content model — no swap to a different backbone.

**Dataset conversion.** Convert the cleaned/normalized train/val/test splits into a Hugging Face `Dataset` object; tokenize with `truncation=True, padding="max_length", max_length=<chosen value>` using `.map()` for batch efficiency.

**Fine-tuning setup.** Full fine-tuning of all DistilBERT layers (66M params is small enough for full fine-tuning on a single Colab GPU; freezing layers would only be justified if compute were constrained, which it isn't at this scale) plus the classification head, trained end-to-end.

**Hyperparameters (with justification).**
- Batch size 16–32 (Colab T4 VRAM-bound at 128 seq length).
- Learning rate 2e-5 to 5e-5 — standard BERT-family fine-tuning range; do a small LR sweep (3 values) on val F1, not test.
- Epochs: 3–5 with early stopping (small fine-tuning datasets overfit fast beyond this).

**Loss function.** Weighted cross-entropy (weights from Phase 2 imbalance handling).

**Optimizer & schedule.** AdamW (standard for transformer fine-tuning, decoupled weight decay) with a linear warmup (~6–10% of steps) then linear decay — reduces early-training instability typical of transformer fine-tuning.

**Early stopping.** Monitor val macro-F1 (not accuracy — class imbalance makes accuracy misleading), patience of 1–2 epochs.

**Checkpointing.** Save best-val-F1 checkpoint only (plus final epoch for comparison), tagged with a run ID that ties back to the experiment tracker (Phase 9).

**Evaluation.** Held-out test set evaluated once, after all hyperparameter decisions are frozen from val — standard train/val/test discipline so the reported test number is not optimistically biased.

---

## Phase 4 — Experimental Framework (Baselines)

- **TF-IDF + Logistic Regression** — cheapest possible baseline; establishes the floor a "no semantic understanding" linear model achieves on lexical fraud cues (e.g., "urgent", "verify now"). Necessary to show the transformer's gain isn't just from bag-of-words signal.
- **TF-IDF + Linear SVM** — stronger linear baseline, often competitive with shallow methods on short-text classification; rules out "the gain is just because LR is weak."
- **DistilBERT-only** — this is also the "ML Only" arm of the ablation study (Phase 7), so it does double duty: baseline-vs-symbolic comparison *and* one ablation cell.

**Reporting.** A single comparison table (Precision/Recall/F1/ROC-AUC) across TF-IDF+LR, TF-IDF+SVM, DistilBERT-only, and (later) the fused system — this table is the headline result of the dissertation's evaluation chapter.

---

## Phase 5 — Quantization Pipeline

**Export.** Save the fine-tuned PyTorch DistilBERT checkpoint in a clean exportable state (eval mode, fixed input shapes).

**ONNX conversion.** Export via `torch.onnx.export` with fixed/dynamic axes for batch and sequence length as appropriate; validate the ONNX graph immediately afterward by comparing logits on a fixed validation batch (PyTorch vs ONNX Runtime output) — flag any divergence above a small numerical tolerance before proceeding.

**TensorFlow conversion.** Convert ONNX → TensorFlow (`onnx-tf` or equivalent) as the required intermediate step toward TFLite; re-validate logits against the ONNX output on the same fixed batch.

**TFLite conversion + INT8 quantization.** Convert the TF SavedModel to TFLite with post-training INT8 quantization, using a representative dataset (a few hundred train-split examples) for calibration. This matches the architecture's "DistilBERT INT8 · TFLite" requirement exactly.

**Validation after every conversion step.** At each hop (PyTorch → ONNX → TF → TFLite-FP32 → TFLite-INT8), run the same fixed validation batch and log: max logit deviation, accuracy/F1 delta vs the original PyTorch model, and inference latency. This produces a conversion-fidelity table for the dissertation showing exactly where, if anywhere, accuracy degrades.

**Accuracy preservation checks.** Full test-set F1 recomputed on the final INT8 TFLite model, not just the calibration batch — report the FP32-vs-INT8 F1 delta as a named result (this is standard practice for on-device ML papers and directly supports the "mobile deployment constraints" framing of the dissertation).

**Deployment readiness checks.** Confirm the final `.tflite` model hits the architecture's stated latency budget (<50ms for the content engine) on a representative low/mid-range device profile (or a CPU-throttled emulation if physical device testing isn't available yet), and confirm model file size is acceptable for an on-device app bundle.

---

## Phase 6 — Research Evaluation

**Protocol.** Standard binary classification metrics — Precision, Recall, F1 (macro and per-class, since imbalance makes per-class numbers more informative than a single aggregate), ROC-AUC, and a confusion matrix — computed identically across baselines, DistilBERT-only, and the fused system for apples-to-apples comparison.

**Statistical validation.** Since the test set is fixed, report **bootstrap confidence intervals** (e.g., 1,000 resamples of the test set) around F1 and ROC-AUC rather than a single point estimate — this is the standard way to express uncertainty without needing a second labeled dataset.

**Cross-validation.** For the baselines and the val-set hyperparameter selection, use stratified k-fold (k=5) on the train+val pool to make the hyperparameter choice less sensitive to a single split; the transformer's final number still comes from the single held-out test set per Phase 3 (k-fold fine-tuning of a transformer 5x is expensive and not necessary to support the dissertation's claims).

**Error analysis.** Manually inspect a stratified sample of false positives and false negatives from the fused system test predictions, categorize by `fraud_type` (from Phase 1's auxiliary tag) and by which engine(s) disagreed — this becomes the qualitative discussion section explaining *why* the hybrid system wins or loses specific cases, which reviewers expect.

---

## Phase 7 — Ablation Study Design (core contribution)

All eight cells below are evaluated on the **same fixed test set** so results are directly comparable. "Score fusion" for partial-signal cells uses the same fusion formula with the missing engine's weight redistributed proportionally (documented explicitly so it's reproducible) — this is an experimental-design choice for the ablation only, and is separate from the approved production fusion formula, which always uses all three engines.

| Cell | Inputs | Output | Metrics | Expected finding |
|---|---|---|---|---|
| Rules Only | Rule Engine verdict (FORCE_PASS/FORCE_BLOCK/soft score) | Binary decision | P/R/F1 | High precision, lower recall — symbolic rules catch known templates but miss novel phrasing |
| ML Only | `ml_score` (DistilBERT) | Thresholded score | P/R/F1/AUC | Strong recall on semantic/novel fraud, weaker on URL-driven smishing with benign-sounding text |
| URL Only | `url_risk` | Thresholded score | P/R/F1/AUC | High precision on URL-bearing fraud, blind on URL-free SMS (e.g., bank-template phishing without links) |
| Sender Only | `sender_risk` | Thresholded score | P/R/F1/AUC | Weak standalone (sender history is contextual, not discriminative alone) — included to quantify its marginal value, not to claim it works alone |
| ML + URL | fused (2-way) | Thresholded score | P/R/F1/AUC | Recall gain over ML Only on link-bearing fraud |
| ML + Sender | fused (2-way) | Thresholded score | P/R/F1/AUC | Modest gain, mainly on repeat-offender senders |
| ML + URL + Sender | fused (3-way, no rules) | Thresholded score | P/R/F1/AUC | Approaches full system; gap to Full System isolates the Rule Engine's contribution |
| Full System | Rules + ML + URL + Sender + Decision controller thresholds | PASS/PASS-WATCH/ESCALATE/BLOCK | P/R/F1/AUC + ESCALATE rate | Best overall; the dissertation's core claim — quantify rules' precision contribution and confidence-gate's effect on the ESCALATE rate |

**Statistical comparison methodology.** Pairwise comparison between adjacent ablation cells (e.g., ML Only vs ML+URL) using McNemar's test on the paired test-set predictions (same examples, two systems) — appropriate for comparing two classifiers on one shared test set, and standard in NLP ablation papers. Report bootstrap CIs (Phase 6) alongside McNemar's p-values so both effect size and significance are visible.

**ESCALATE handling.** Because the Decision controller's confidence gate can route to ESCALATE regardless of risk score, report ESCALATE rate as its own metric per cell where applicable (only meaningful for cells that include a confidence signal) — a system that escalates too often is operationally costly even if its binary P/R looks good, and the dissertation should make this trade-off explicit.

---

## Phase 8 — Notebook Structure (Colab)

```
sms-fraud-msc/
├── data/
│   ├── raw/                  # untouched source downloads
│   ├── interim/              # merged, deduped, harmonized
│   └── processed/            # final train/val/test splits (versioned)
├── notebooks/
│   ├── 01_dataset_acquisition_and_merge.ipynb
│   ├── 02_cleaning_eda_and_splits.ipynb
│   ├── 03_baselines_tfidf_lr_svm.ipynb
│   ├── 04_distilbert_finetuning.ipynb
│   ├── 05_quantization_onnx_tf_tflite.ipynb
│   ├── 06_evaluation_and_statistics.ipynb
│   ├── 07_ablation_study.ipynb
│   └── 08_results_aggregation_and_plots.ipynb
├── models/
│   ├── checkpoints/          # PyTorch fine-tuned checkpoints
│   └── tflite/                # quantized deployment artifacts
├── results/
│   ├── metrics/               # per-notebook JSON/CSV metric dumps
│   └── figures/                # plots for the dissertation
└── configs/                   # seeds, hyperparams, split manifests
```

| Notebook | Purpose | Inputs | Outputs | Depends on | Artifacts |
|---|---|---|---|---|---|
| 01 | Acquire + merge sources | raw downloads | `interim/merged.csv` | — | merge log, provenance table |
| 02 | Clean, dedup, EDA, split | `interim/merged.csv` | `processed/{train,val,test}.csv` | 01 | dataset stats, split manifest |
| 03 | TF-IDF baselines | processed splits | baseline metrics | 02 | `results/metrics/baselines.json` |
| 04 | Fine-tune DistilBERT | processed splits | PyTorch checkpoint | 02 | best checkpoint, training curves |
| 05 | Quantize to TFLite | checkpoint | `.tflite` model | 04 | conversion-fidelity table, `.tflite` |
| 06 | Full evaluation + CIs | checkpoint/TFLite + test set | metric tables, bootstrap CIs | 04, 05 | `results/metrics/eval.json` |
| 07 | Ablation cells + McNemar | all engine scores + test set | per-cell metrics, p-values | 02–06 | `results/metrics/ablation.json` |
| 08 | Aggregate plots/tables | all `results/metrics/*` | final figures/tables | 03–07 | dissertation-ready figures |

---

## Phase 9 — Reproducibility (solo MSc researcher)

- **Seed management.** One global seed constant (e.g., `SEED=42`) set for Python, NumPy, PyTorch, and the HF `Trainer`, recorded in every notebook's first cell and in `configs/seed.yaml`.
- **Version control.** Git repo for code/notebooks/configs (Colab notebooks committed as `.ipynb` after clearing heavy outputs); `.gitignore` for raw data and large checkpoints.
- **Dataset versioning.** Each `processed/` split tagged with a short hash of its generating config (source list + dedup threshold + split seed) so any result can be traced back to an exact dataset version — lightweight DVC-style manifest is enough; no extra infrastructure needed.
- **Experiment tracking.** A single flat CSV/JSON log (or a free-tier Weights & Biases project, if preferred) recording: run ID, notebook, hyperparameters, seed, dataset version hash, resulting metrics. No bespoke tracking server — overkill for a solo MSc project.
- **Model registry.** Simple convention: `models/checkpoints/<run_id>/` and `models/tflite/<run_id>.tflite`, with `run_id` matching the experiment log so any reported number maps to an exact artifact on disk.
- **Result storage.** All metric dumps as JSON in `results/metrics/`, never overwritten — append run_id to filenames so the ablation table in the dissertation can be regenerated from raw JSON at any time without rerunning training.

---

## Dependency Graph

```
01 (acquire/merge)
   └─> 02 (clean/dedup/split)
          ├─> 03 (TF-IDF baselines) ───────────────┐
          └─> 04 (DistilBERT fine-tune)             │
                 └─> 05 (quantize: ONNX→TF→TFLite)  │
                        └─> 06 (evaluation + CIs) ◄──┘
                               └─> 07 (ablation study, uses Rule/URL/Sender
                                       engine outputs alongside ML score)
                                      └─> 08 (aggregate figures/tables)
```

## Recommended Execution Order

1. Notebook 01 → 02 (dataset is the long pole — get it locked first; every downstream number depends on the split being final).
2. Notebook 03 (baselines) in parallel with starting 04 (fine-tuning) — baselines are cheap and don't block the transformer work.
3. Notebook 04 → 05 (fine-tune, then quantize) — quantization fidelity checks should happen immediately after fine-tuning while the PyTorch checkpoint is fresh.
4. Notebook 06 (full evaluation with CIs) once both FP32 and INT8 models exist.
5. Notebook 07 (ablation study) last among the ML notebooks — it needs the Rule/URL/Sender engine score outputs as well as the ML score, so it should start as soon as those non-ML engines can produce scores on the same test set, even if that means a placeholder/stub for those engines initially.
6. Notebook 08 (aggregation) continuously updated as each prior notebook produces results, finalized last.

## Implementation Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Template-level leakage from synthetic OTP/bank data inflating accuracy | Split synthetic templates before instantiation (Phase 1); spot-check test set for near-duplicate templates post-split |
| Class imbalance making accuracy look better than it is | Report macro-F1 and per-class metrics everywhere, not just accuracy |
| ONNX/TF conversion silently degrading accuracy | Mandatory logit-comparison check at every conversion hop (Phase 5) before moving to the next |
| Colab session timeouts losing fine-tuning progress | Checkpoint every epoch to Drive-backed storage, not just at the end |
| Ablation fusion-weight redistribution being seen as inconsistent with the production formula | Document explicitly in the dissertation that ablation fusion is an experimental-design choice, distinct from the fixed production formula |
| Small test set giving noisy ablation comparisons | Bootstrap CIs + McNemar's test (Phase 6/7) rather than relying on point-estimate differences |
| Engine score availability mismatch (Rule/URL/Sender engines may be implemented in app code, not notebooks) | Stub deterministic score functions in Python mirroring the approved engine logic exactly, so ablation notebook 07 isn't blocked on app-side implementation |

---

This roadmap covers dataset through quantized, evaluated, ablation-tested model — the ML-side foundation the approved architecture's Android deployment and publication write-up will build on next.
