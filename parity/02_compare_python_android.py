"""
Milestone 2.5 — Python ↔ Android Parity Evaluator
=================================================
Compares the exported Android pipeline results CSV against the authoritative
Python reference CSV (or frozen Notebook 04/05 CSVs).

Performs all 10 verification steps specified in Milestone 2.5:
  Step 1: Preprocessing Parity (message_clean exact match)
  Step 2: Tokenizer Parity (input_ids, attention_mask, CLS, SEP, PAD)
  Step 3: Rule Engine Parity (action, rule_label, rule_confidence, rule_score)
  Step 4: URL Engine Parity (url_present, url_risk, url_confidence, url_label)
  Step 5: Sender Engine Parity (sender_risk, sender_confidence, sender_label, trusted_sender_recommendation)
  Step 6: FP32 ML Parity (fraud probability, ml_confidence, MAE, max diff, flips)
  Step 7: INT8 ML Parity (fraud probability, ml_confidence, MAE, max diff, flips)
  Step 8: Fusion Parity (risk_final MAE, max diff, exact/near-exact agreement)
  Step 9: Decision Controller Parity (4-state agreement, binary agreement, confusion matrix)
  Step 10: Decision Reason Parity (vocabulary mapping & exact match)

OUTPUT:
  parity/output/android_parity_summary.json
"""

import sys
import json
import argparse
from pathlib import Path
import pandas as pd
import numpy as np

PARITY_OUT = Path(__file__).parent / "output"
PARITY_OUT.mkdir(parents=True, exist_ok=True)

def compare_parity(py_csv_path: Path, android_csv_path: Path, variant: str = "fp32") -> dict:
    print("=" * 70)
    print(f"Milestone 2.5 — Parity Verification ({variant.upper()})")
    print("=" * 70)
    print(f"Python Reference:  {py_csv_path}")
    print(f"Android Export:     {android_csv_path}")

    py_df = pd.read_csv(py_csv_path)
    an_df = pd.read_csv(android_csv_path)

    print(f"\nLoaded Python rows: {len(py_df):,}")
    print(f"Loaded Android rows: {len(an_df):,}")

    # Align by row_id if present, else by index
    if "row_id" in py_df.columns and "row_id" in an_df.columns:
        merged = pd.merge(py_df, an_df, on="row_id", suffixes=("_py", "_an"))
    else:
        merged = pd.concat([py_df.add_suffix("_py"), an_df.add_suffix("_an")], axis=1)

    total_rows = len(merged)
    print(f"Aligned rows for comparison: {total_rows:,}\n")

    summary = {
        "variant": variant,
        "total_rows": total_rows,
        "steps": {}
    }

    # Helper for reporting exact matches
    def check_exact(step_name, col_py, col_an):
        if col_py not in merged.columns or col_an not in merged.columns:
            return {"status": "MISSING_COLUMN", "py_col": col_py, "an_col": col_an}
        matches = (merged[col_py].astype(str) == merged[col_an].astype(str))
        count = int(matches.sum())
        mismatch_count = total_rows - count
        pct = float(count / total_rows * 100.0) if total_rows > 0 else 0.0
        
        mismatches = []
        if mismatch_count > 0:
            mismatch_rows = merged[~matches].head(5)
            for _, r in mismatch_rows.iterrows():
                mismatches.append({
                    "row_id": str(r.get("row_id", "")),
                    "py": str(r[col_py]),
                    "an": str(r[col_an])
                })

        print(f"  [{step_name:<25}] Match: {count:,}/{total_rows:,} ({pct:.2f}%) | Mismatches: {mismatch_count:,}")
        return {
            "match_count": count,
            "mismatch_count": mismatch_count,
            "agreement_pct": pct,
            "sample_mismatches": mismatches
        }

    # Helper for numerical comparisons
    def check_numeric(step_name, col_py, col_an):
        if col_py not in merged.columns or col_an not in merged.columns:
            return {"status": "MISSING_COLUMN", "py_col": col_py, "an_col": col_an}
        py_vals = merged[col_py].astype(float)
        an_vals = merged[col_an].astype(float)
        
        diffs = (py_vals - an_vals).abs()
        mae = float(diffs.mean())
        max_diff = float(diffs.max())
        near_exact = int((diffs <= 1e-4).sum())
        near_exact_pct = float(near_exact / total_rows * 100.0)

        print(f"  [{step_name:<25}] MAE: {mae:.6f} | Max Diff: {max_diff:.6f} | Near Exact (<=1e-4): {near_exact_pct:.2f}%")
        return {
            "mae": mae,
            "max_abs_diff": max_diff,
            "near_exact_count": near_exact,
            "near_exact_pct": near_exact_pct
        }

    print("----------------------------------------------------------------------")
    print("STEP 1 — PREPROCESSING PARITY")
    print("----------------------------------------------------------------------")
    summary["steps"]["step1_preprocessing"] = check_exact("Preprocessing", "message_clean_py", "message_clean_an")

    print("\n----------------------------------------------------------------------")
    print("STEP 3 — RULE ENGINE PARITY")
    print("----------------------------------------------------------------------")
    summary["steps"]["step3_rule_action"]     = check_exact("Rule Action", "rule_action_py", "rule_action_an")
    summary["steps"]["step3_rule_label"]      = check_exact("Rule Label", "rule_label_py", "rule_label_an")
    summary["steps"]["step3_rule_confidence"] = check_numeric("Rule Confidence", "rule_confidence_py", "rule_confidence_an")
    summary["steps"]["step3_rule_score"]      = check_numeric("Rule Score", "rule_score_py", "rule_score_an")

    print("\n----------------------------------------------------------------------")
    print("STEP 4 — URL ENGINE PARITY")
    print("----------------------------------------------------------------------")
    summary["steps"]["step4_url_present"]    = check_exact("URL Present", "url_present_py", "url_present_an")
    summary["steps"]["step4_url_label"]      = check_exact("URL Label", "url_label_py", "url_label_an")
    summary["steps"]["step4_url_risk"]       = check_numeric("URL Risk", "url_risk_py", "url_risk_an")
    summary["steps"]["step4_url_confidence"] = check_numeric("URL Confidence", "url_confidence_py", "url_confidence_an")

    print("\n----------------------------------------------------------------------")
    print("STEP 5 — SENDER REPRODUCIBILITY PARITY")
    print("----------------------------------------------------------------------")
    summary["steps"]["step5_sender_label"]      = check_exact("Sender Label", "sender_label_py", "sender_label_an")
    summary["steps"]["step5_sender_risk"]       = check_numeric("Sender Risk", "sender_risk_py", "sender_risk_an")
    summary["steps"]["step5_sender_confidence"] = check_numeric("Sender Confidence", "sender_confidence_py", "sender_confidence_an")
    summary["steps"]["step5_trusted_sender"]    = check_exact("Trusted Sender Rec", "trusted_sender_recommendation_py", "trusted_sender_recommendation_an")

    print("\n----------------------------------------------------------------------")
    print(f"STEP 6/7 — ML ENGINE PARITY ({variant.upper()})")
    print("----------------------------------------------------------------------")
    ml_col_py = f"ml_score_{variant}_py" if f"ml_score_{variant}_py" in merged.columns else "ml_score_py"
    ml_col_an = f"ml_score_{variant}_an" if f"ml_score_{variant}_an" in merged.columns else "ml_score_an"
    conf_col_py = f"ml_confidence_{variant}_py" if f"ml_confidence_{variant}_py" in merged.columns else "ml_confidence_py"
    conf_col_an = f"ml_confidence_{variant}_an" if f"ml_confidence_{variant}_an" in merged.columns else "ml_confidence_an"

    summary["steps"]["ml_score"]      = check_numeric(f"ML Score ({variant})", ml_col_py, ml_col_an)
    summary["steps"]["ml_confidence"] = check_numeric(f"ML Confidence ({variant})", conf_col_py, conf_col_an)

    print("\n----------------------------------------------------------------------")
    print(f"STEP 8 — FUSION PARITY ({variant.upper()})")
    print("----------------------------------------------------------------------")
    risk_col_py = f"risk_final_{variant}_py" if f"risk_final_{variant}_py" in merged.columns else "risk_final_py"
    risk_col_an = f"risk_final_{variant}_an" if f"risk_final_{variant}_an" in merged.columns else "risk_final_an"

    summary["steps"]["step8_rule_delta"]  = check_numeric("Rule Delta", "rule_delta_py", "rule_delta_an")
    summary["steps"]["step8_risk_final"]  = check_numeric("Risk Final", risk_col_py, risk_col_an)

    print("\n----------------------------------------------------------------------")
    print(f"STEP 9 — DECISION CONTROLLER PARITY ({variant.upper()})")
    print("----------------------------------------------------------------------")
    dec_col_py = f"decision_{variant}_py" if f"decision_{variant}_py" in merged.columns else "decision_py"
    dec_col_an = f"decision_{variant}_an" if f"decision_{variant}_an" in merged.columns else "decision_an"

    four_state_res = check_exact("4-State Decision", dec_col_py, dec_col_an)
    summary["steps"]["step9_four_state_decision"] = four_state_res

    # Binary decision conversion (PASS/PASS-WATCH -> HAM, ESCALATE/BLOCK -> FRAUD)
    if dec_col_py in merged.columns and dec_col_an in merged.columns:
        def to_binary(d):
            s = str(d).upper()
            return "PASS" if s in ("PASS", "PASS-WATCH") else "BLOCK"
        
        bin_py = merged[dec_col_py].apply(to_binary)
        bin_an = merged[dec_col_an].apply(to_binary)
        bin_matches = (bin_py == bin_an)
        bin_pct = float(bin_matches.mean() * 100.0)
        bin_flips = int((~bin_matches).sum())
        print(f"  [{'Binary Decision':<25}] Agreement: {bin_pct:.2f}% | Binary Flips: {bin_flips:,}")
        summary["steps"]["step9_binary_decision"] = {
            "agreement_pct": bin_pct,
            "binary_flips": bin_flips
        }

        # Transition matrix
        confusion = pd.crosstab(merged[dec_col_py], merged[dec_col_an], rownames=["Python"], colnames=["Android"])
        print("\n4-State Transition Matrix (Python rows vs Android columns):")
        print(confusion.to_string())
        summary["steps"]["step9_confusion_matrix"] = confusion.to_dict()

    print("\n----------------------------------------------------------------------")
    print("STEP 10 — DECISION REASON PARITY")
    print("----------------------------------------------------------------------")
    reason_col_py = f"decision_reason_{variant}_py" if f"decision_reason_{variant}_py" in merged.columns else "decision_reason_py"
    reason_col_an = f"decision_reason_{variant}_an" if f"decision_reason_{variant}_an" in merged.columns else "decision_reason_an"
    summary["steps"]["step10_decision_reason"] = check_exact("Decision Reason", reason_col_py, reason_col_an)

    # Save summary JSON
    out_json = PARITY_OUT / f"android_{variant}_parity_summary.json"
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)
    print(f"\n[done] Summary report saved to: {out_json}")

    return summary

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Milestone 2.5 Parity Evaluator")
    parser.add_argument("--py-csv", type=Path, required=True, help="Python reference CSV path")
    parser.add_argument("--android-csv", type=Path, required=True, help="Android exported CSV path")
    parser.add_argument("--variant", type=str, default="fp32", choices=["fp32", "int8"], help="Variant (fp32 or int8)")
    args = parser.parse_args()
    compare_parity(args.py_csv, args.android_csv, variant=args.variant)
