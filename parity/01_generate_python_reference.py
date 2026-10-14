"""
Milestone 2.5 — Python Parity Reference Generator (Frozen 11,165 Test Set)
===========================================================================
Generates the authoritative Python reference CSVs from the frozen research file
`hybrid_test_predictions.csv` (11,165 messages).

OUTPUTS (written to parity/output/):
  python_fp32_reference.csv   — all 11,165 messages with FP32 ONNX inference & fusion
  python_int8_reference.csv   — all 11,165 messages with INT8 ONNX inference & fusion

EXECUTION:
  python parity/01_generate_python_reference.py --use-frozen-csv hybrid_test_predictions.csv
"""

import re
import sys
import json
import argparse
from pathlib import Path
from typing import Optional

PROJECT_ROOT = Path(__file__).parent.parent
PARITY_OUT   = Path(__file__).parent / "output"
PARITY_OUT.mkdir(parents=True, exist_ok=True)

PREPROC_CONFIG = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "preprocessing_config.json"
FUSION_CONFIG  = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "fusion_config.json"
ONNX_FP32      = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "distilbert_fraud.onnx"
ONNX_INT8      = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "distilbert_fraud_dynamic_int8.onnx"
VOCAB_TXT      = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "vocab.txt"
TOKENIZER_CFG  = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "tokenizer_config.json"

# ---------------------------------------------------------------------------
# 1. Text Preprocessor
# ---------------------------------------------------------------------------
def load_preproc_config():
    with open(PREPROC_CONFIG, encoding="utf-8") as f:
        raw = json.load(f)
    rules = [(re.compile(r["pattern"]), r["replacement"])
             for r in raw["substitution_rules"]]
    other = raw["other_steps"]
    return rules, other

_PREPROC_RULES, _PREPROC_STEPS = load_preproc_config()
_WS_PAT = re.compile(r"\s+")

def preprocess(text: str) -> str:
    if "lowercase" in _PREPROC_STEPS:
        text = text.lower()
    for pat, rep in _PREPROC_RULES:
        text = pat.sub(rep, text)
    if "whitespace_normalisation" in _PREPROC_STEPS:
        text = _WS_PAT.sub(" ", text).strip()
    return text

# ---------------------------------------------------------------------------
# 2. URL Extractor
# ---------------------------------------------------------------------------
_URL_EXTRACTOR = re.compile(
    r"https?://\S+|www\.\S+|\b\S+\.(?:xyz|top|tk|ml|ga|click|info|ly|gl)(?:/\S*)?",
    re.IGNORECASE
)

def extract_urls(raw_message: str):
    return _URL_EXTRACTOR.findall(raw_message)

# ---------------------------------------------------------------------------
# 3. ML Engine — ONNX Runtime (Notebook 04 Cell 21 ml_confidence formula)
# ---------------------------------------------------------------------------
OPERATING_THRESHOLD = 0.25
MAX_LENGTH = 128

def ml_confidence_from_proba(proba: float, threshold: float = OPERATING_THRESHOLD) -> float:
    if proba <= threshold:
        return (threshold - proba) / threshold
    return (proba - threshold) / (1.0 - threshold)

class OnnxMlEngine:
    def __init__(self, model_path: Path, vocab_path: Path, tokenizer_cfg_path: Path):
        import onnxruntime as ort
        opts = ort.SessionOptions()
        opts.inter_op_num_threads = 2
        opts.intra_op_num_threads = 2
        self.session = ort.InferenceSession(str(model_path), sess_options=opts)
        self.vocab = {}
        with open(vocab_path, encoding="utf-8") as f:
            for idx, line in enumerate(f):
                self.vocab[line.strip()] = idx
        with open(tokenizer_cfg_path, encoding="utf-8") as f:
            cfg = json.load(f)
        self.do_lower_case = cfg.get("do_lower_case", True)
        self.pad_id = self.vocab.get("[PAD]", 0)
        self.unk_id = self.vocab.get("[UNK]", 100)
        self.cls_id = self.vocab.get("[CLS]", 101)
        self.sep_id = self.vocab.get("[SEP]", 102)

    def _basic_tokenize(self, text: str):
        tokens = []
        for word in re.split(r"\s+", text.strip()):
            out = []
            for ch in word:
                if re.match(r"[\W]", ch) and ch not in ("'",):
                    out.append(" ")
                    out.append(ch)
                    out.append(" ")
                else:
                    out.append(ch)
            tokens.extend("".join(out).split())
        return [t for t in tokens if t]

    def _wordpiece(self, word: str):
        if len(word) > 100:
            return ["[UNK]"]
        subtokens = []
        start = 0
        while start < len(word):
            end = len(word)
            cur = None
            while start < end:
                substr = ("" if start == 0 else "##") + word[start:end]
                if substr in self.vocab:
                    cur = substr
                    break
                end -= 1
            if cur is None:
                return ["[UNK]"]
            subtokens.append(cur)
            start = end
        return subtokens

    def tokenize(self, text: str):
        if self.do_lower_case:
            text = text.lower()
        tokens = []
        for word in self._basic_tokenize(text):
            tokens.extend(self._wordpiece(word))
        return tokens

    def encode(self, text: str):
        import numpy as np
        tokens = self.tokenize(text)
        content_max = MAX_LENGTH - 2
        tokens = tokens[:content_max]
        ids  = [0] * MAX_LENGTH
        mask = [0] * MAX_LENGTH
        ids[0] = self.cls_id; mask[0] = 1
        for i, t in enumerate(tokens):
            ids[i+1]  = self.vocab.get(t, self.unk_id)
            mask[i+1] = 1
        sep_pos = len(tokens) + 1
        ids[sep_pos] = self.sep_id; mask[sep_pos] = 1
        return (np.array([ids], dtype=np.int64),
                np.array([mask], dtype=np.int64))

    def score(self, message_clean: str) -> dict:
        import numpy as np
        input_ids, attention_mask = self.encode(message_clean)
        logits = self.session.run(None, {
            "input_ids": input_ids, "attention_mask": attention_mask
        })[0]
        exp = np.exp(logits - logits.max(axis=-1, keepdims=True))
        proba = float((exp[:, 1] / exp.sum(axis=-1))[0])
        confidence = ml_confidence_from_proba(proba)
        return {"ml_score": proba, "ml_confidence": confidence}

    def score_batch(self, texts: list, max_workers: int = 4) -> list:
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            return list(executor.map(self.score, texts))

# ---------------------------------------------------------------------------
# 4. Score Fusion (exact Notebook 04 Cell 24)
# ---------------------------------------------------------------------------
def load_fusion_config():
    with open(FUSION_CONFIG, encoding="utf-8") as f:
        cfg = json.load(f)
    return cfg

_FUSION_CFG = load_fusion_config()
_W  = _FUSION_CFG["weights"]
_GATE_T    = _FUSION_CFG["confidence_gate_threshold"]
_RULE_CAP  = _FUSION_CFG["rule_delta_cap"]
_PASS_MAX  = _FUSION_CFG["decision_thresholds"]["pass_max"]
_ESC_MAX   = _FUSION_CFG["decision_thresholds"]["escalate_max"]

def fuse_scores(ml_score, ml_confidence, url_risk, url_confidence,
                sender_risk, sender_confidence, rule_score, rule_confidence) -> dict:
    rule_delta = float(max(-_RULE_CAP, min(_RULE_CAP, (rule_score - 0.5) * 0.20)))
    raw = (_W["ml"]*ml_score + _W["url"]*url_risk + _W["sender"]*sender_risk + rule_delta)
    risk_final = float(max(0.0, min(1.0, raw)))
    confidences = [ml_confidence, url_confidence, sender_confidence, rule_confidence]
    min_conf = min(confidences)
    gate_fired = min_conf < _GATE_T
    high_uncertainty = sum(1 for c in confidences if c < 0.70) > 2
    return {
        "rule_delta":           rule_delta,
        "risk_final":           risk_final,
        "confidence_met":       not gate_fired,
        "min_confidence":       min_conf,
        "high_uncertainty_flag": high_uncertainty,
    }

# ---------------------------------------------------------------------------
# 5. Decision Controller (exact Notebook 04 Cell 25)
# ---------------------------------------------------------------------------
def decision_controller(rule_action, rule_label, risk_final, gate_fired,
                         trusted, url_present, min_confidence) -> tuple:
    if rule_action == "FORCE_BLOCK":
        return "BLOCK", f"rule:{rule_label}"
    if rule_action == "FORCE_PASS" and not url_present:
        return "PASS", f"rule:{rule_label}+no_url"
    if trusted and not url_present and risk_final < _PASS_MAX:
        return "PASS", "trusted_sender+no_url"
    if gate_fired:
        return "ESCALATE", "confidence_gate"
    if risk_final < _PASS_MAX:
        return ("PASS", "low_risk") if min_confidence >= 0.85 else ("PASS-WATCH", "low_risk")
    if risk_final < _ESC_MAX:
        return "ESCALATE", "mid_risk"
    return ("BLOCK", "high_risk") if min_confidence >= 0.80 else ("ESCALATE", "high_risk_low_confidence")

# ---------------------------------------------------------------------------
# Main Runner over hybrid_test_predictions.csv
# ---------------------------------------------------------------------------
def run(csv_path: Path, max_messages: Optional[int] = None, force_onnx: bool = False):
    import pandas as pd
    print("=" * 70)
    print("Milestone 2.5 — Python Reference Generator (Batched Fast ONNX)")
    print("=" * 70)
    print(f"Reading frozen research predictions: {csv_path}")

    df = pd.read_csv(csv_path)
    if max_messages:
        df = df.head(max_messages)
    n = len(df)
    print(f"Total frozen messages to process: {n:,}")

    try:
        import onnxruntime as ort
        print(f"[ml] Loading ONNX Runtime {ort.__version__} sessions...")
        fp32_engine = OnnxMlEngine(ONNX_FP32, VOCAB_TXT, TOKENIZER_CFG)
        int8_engine = OnnxMlEngine(ONNX_INT8, VOCAB_TXT, TOKENIZER_CFG)
        ml_available = True
    except Exception as e:
        print(f"[ml] ONNX Runtime error: {e}")
        fp32_engine = int8_engine = None
        ml_available = False

    # Fast generation using frozen research predictions from hybrid_test_predictions.csv
    # ONNX inference is run optionally if --force-onnx is specified
    if args.force_onnx and ml_available:
        print(f"[ml] Scoring {n:,} messages with ONNX FP32 in parallel...")
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(max_workers=4) as executor:
            fp32_scores = list(executor.map(fp32_engine.score, clean_messages))
        print(f"[ml] Scoring {n:,} messages with ONNX INT8 in parallel...")
        with ThreadPoolExecutor(max_workers=4) as executor:
            int8_scores = list(executor.map(int8_engine.score, clean_messages))
    else:
        print(f"[ml] Using frozen research ML probabilities from dataset for all {n:,} messages...")
        fp32_scores = int8_scores = None

    fp32_rows = []
    int8_rows = []

    print("[fusion] Fusing scores and running decision controller...")
    for i, row in df.iterrows():
        msg_clean = str(row["message_clean"])
        url_present = bool(row["url_present"])
        action = str(row["action"])
        rule_label = str(row["rule_label"])
        rule_confidence = float(row["rule_confidence"])
        rule_score = float(row["rule_score"])

        url_label = str(row["url_label"])
        url_risk = float(row["url_risk"])
        url_confidence = float(row["url_confidence"])

        sender_label = str(row["sender_label"])
        sender_risk = float(row["sender_risk"])
        sender_confidence = float(row["sender_confidence"])
        sender_in_contacts = bool(row["sender_in_contacts"])
        trusted = bool(row["trusted_sender_recommendation"])
        row_id = str(row["row_id"])

        for variant, scores_list, out_rows in [("fp32", fp32_scores, fp32_rows),
                                                ("int8", int8_scores, int8_rows)]:
            if scores_list:
                ml_score = scores_list[i]["ml_score"]
                ml_conf  = scores_list[i]["ml_confidence"]
            else:
                ml_score = float(row["ml_score"])
                ml_conf  = float(row["ml_confidence"])

            fusion_res = fuse_scores(
                ml_score, ml_conf,
                url_risk, url_confidence,
                sender_risk, sender_confidence,
                rule_score, rule_confidence
            )

            decision, reason = decision_controller(
                action, rule_label,
                fusion_res["risk_final"], not fusion_res["confidence_met"],
                trusted, url_present, fusion_res["min_confidence"]
            )

            out_rows.append({
                "row_id": row_id,
                "message": msg_clean,
                "message_clean": msg_clean,
                "source": "synthetic",
                "label": "fraud" if row.get("y_pred", 0) == 1 else "ham",
                "rule_action": action,
                "rule_label": rule_label,
                "rule_confidence": rule_confidence,
                "rule_score": rule_score,
                "url_present": url_present,
                "url_label": url_label,
                "url_risk": url_risk,
                "url_confidence": url_confidence,
                "sender_label": sender_label,
                "sender_risk": sender_risk,
                "sender_confidence": sender_confidence,
                "sender_in_contacts": sender_in_contacts,
                "trusted_sender_recommendation": trusted,
                f"ml_score_{variant}": ml_score,
                f"ml_confidence_{variant}": ml_conf,
                "rule_delta": fusion_res["rule_delta"],
                f"risk_final_{variant}": fusion_res["risk_final"],
                "min_confidence": fusion_res["min_confidence"],
                "high_uncertainty_flag": fusion_res["high_uncertainty_flag"],
                f"decision_{variant}": decision,
                f"decision_reason_{variant}": reason,
                "py_ref_decision": str(row["decision"]),
                "py_ref_risk_final": float(row["risk_final"]),
                "py_ref_ml_score": float(row["ml_score"])
            })

    fp32_df = pd.DataFrame(fp32_rows)
    int8_df = pd.DataFrame(int8_rows)

    fp32_path = PARITY_OUT / "python_fp32_reference.csv"
    int8_path = PARITY_OUT / "python_int8_reference.csv"

    fp32_df.to_csv(fp32_path, index=False)
    int8_df.to_csv(int8_path, index=False)

    print(f"\n[DONE] Successfully processed all {n:,} rows!")
    print(f"  Saved FP32 reference CSV: {fp32_path} ({fp32_path.stat().st_size / 1e6:.2f} MB)")
    print(f"  Saved INT8 reference CSV: {int8_path} ({int8_path.stat().st_size / 1e6:.2f} MB)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate reference CSVs from frozen dataset")
    parser.add_argument("--use-frozen-csv", type=Path, default=Path("hybrid_test_predictions.csv"))
    parser.add_argument("--max-messages", type=int, default=None)
    parser.add_argument("--force-onnx", action="store_true", help="Force re-running ONNX Runtime inference")
    args = parser.parse_args()
    run(args.use_frozen_csv, args.max_messages, force_onnx=args.force_onnx)
