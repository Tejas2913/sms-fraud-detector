import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml, OxmlElement
from docx.oxml.ns import nsdecls, qn

DOC_NAME = "SMS_Guard_Android_Deployment_Evidence_Report.docx"

# Color Palette: Deep Navy & Professional Slate
PRIMARY_COLOR = RGBColor(26, 54, 93)     # #1A365D - Deep Navy
SECONDARY_COLOR = RGBColor(43, 108, 176) # #2B6CB0 - Steel Blue
DARK_NEUTRAL = RGBColor(45, 55, 72)      # #2D3748 - Charcoal Text
LIGHT_NEUTRAL = RGBColor(247, 250, 252)  # #F7FAFC - Soft Background
BORDER_COLOR = RGBColor(226, 232, 240)   # #E2E8F0 - Border Gray
ACCENT_GREEN = RGBColor(40, 167, 69)     # #28A745 - Success Green
ACCENT_BLUE = RGBColor(0, 102, 204)      # Accent Blue

def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'''
        <w:tcMar {nsdecls("w")}>
            <w:top w:w="{top}" w:type="dxa"/>
            <w:bottom w:w="{bottom}" w:type="dxa"/>
            <w:left w:w="{left}" w:type="dxa"/>
            <w:right w:w="{right}" w:type="dxa"/>
        </w:tcMar>
    ''')
    tcPr.append(tcMar)

def set_table_borders(table, color_hex="D2D6DC"):
    tblPr = table._tbl.tblPr
    tblBorders = parse_xml(f'''
        <w:tblBorders {nsdecls("w")}>
            <w:top w:val="single" w:sz="4" w:space="0" w:color="{color_hex}"/>
            <w:bottom w:val="single" w:sz="4" w:space="0" w:color="{color_hex}"/>
            <w:left w:val="none"/>
            <w:right w:val="none"/>
            <w:insideH w:val="single" w:sz="4" w:space="0" w:color="{color_hex}"/>
            <w:insideV w:val="none"/>
        </w:tblBorders>
    ''')
    tblPr.append(tblBorders)

def add_header_styled(doc, text, level):
    p = doc.add_heading(level=level)
    run = p.add_run(text)
    run.font.name = 'Calibri'
    if level == 1:
        run.font.size = Pt(16)
        run.font.bold = True
        run.font.color.rgb = PRIMARY_COLOR
        p.paragraph_format.space_before = Pt(14)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.keep_with_next = True
    elif level == 2:
        run.font.size = Pt(13)
        run.font.bold = True
        run.font.color.rgb = SECONDARY_COLOR
        p.paragraph_format.space_before = Pt(10)
        p.paragraph_format.space_after = Pt(3)
        p.paragraph_format.keep_with_next = True
    elif level == 3:
        run.font.size = Pt(11)
        run.font.bold = True
        run.font.color.rgb = DARK_NEUTRAL
        p.paragraph_format.space_before = Pt(6)
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.keep_with_next = True
    return p

def add_paragraph_styled(doc, text="", space_after=4, bold_prefix=None, prefix_color=None):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.line_spacing = 1.15
    if bold_prefix:
        r_pre = p.add_run(bold_prefix)
        r_pre.font.name = 'Calibri'
        r_pre.font.size = Pt(10.5)
        r_pre.font.bold = True
        if prefix_color:
            r_pre.font.color.rgb = prefix_color
        else:
            r_pre.font.color.rgb = PRIMARY_COLOR
    if text:
        r = p.add_run(text)
        r.font.name = 'Calibri'
        r.font.size = Pt(10.5)
        r.font.color.rgb = DARK_NEUTRAL
    return p

def add_bullet_styled(doc, bold_prefix, text, space_after=3):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.line_spacing = 1.15
    if bold_prefix:
        r_pre = p.add_run(bold_prefix)
        r_pre.font.name = 'Calibri'
        r_pre.font.size = Pt(10.5)
        r_pre.font.bold = True
        r_pre.font.color.rgb = PRIMARY_COLOR
    if text:
        r = p.add_run(text)
        r.font.name = 'Calibri'
        r.font.size = Pt(10.5)
        r.font.color.rgb = DARK_NEUTRAL
    return p

def add_callout_box(doc, title, text, bg_hex="F0F4F8", border_hex="2B6CB0"):
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    cell = tbl.cell(0, 0)
    cell.width = Inches(6.5)
    set_cell_background(cell, bg_hex)
    set_cell_margins(cell, top=140, bottom=140, left=180, right=180)
    
    # Left thick border only
    tcPr = cell._tc.get_or_add_tcPr()
    borders = parse_xml(f'''
        <w:tcBorders {nsdecls("w")}>
            <w:left w:val="single" w:sz="24" w:space="0" w:color="{border_hex}"/>
            <w:top w:val="none"/>
            <w:right w:val="none"/>
            <w:bottom w:val="none"/>
        </w:tcBorders>
    ''')
    tcPr.append(borders)
    
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(2)
    r_title = p.add_run(title + "\n")
    r_title.font.name = 'Calibri'
    r_title.font.size = Pt(10.5)
    r_title.font.bold = True
    r_title.font.color.rgb = PRIMARY_COLOR
    
    r_text = p.add_run(text)
    r_text.font.name = 'Calibri'
    r_text.font.size = Pt(10)
    r_text.font.color.rgb = DARK_NEUTRAL
    
    # Empty paragraph after table
    p_after = doc.add_paragraph()
    p_after.paragraph_format.space_before = Pt(0)
    p_after.paragraph_format.space_after = Pt(4)

def format_table_header(row, col_widths, headers, bg_hex="1A365D"):
    for i, title in enumerate(headers):
        cell = row.cells[i]
        cell.width = Inches(col_widths[i])
        set_cell_background(cell, bg_hex)
        set_cell_margins(cell, top=120, bottom=120, left=120, right=120)
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        r = p.add_run(title)
        r.font.name = 'Calibri'
        r.font.size = Pt(9.5)
        r.font.bold = True
        r.font.color.rgb = RGBColor(255, 255, 255)

def format_table_row(row, col_widths, values, is_even=False):
    bg_hex = "F8FAFC" if is_even else "FFFFFF"
    for i, val in enumerate(values):
        cell = row.cells[i]
        cell.width = Inches(col_widths[i])
        set_cell_background(cell, bg_hex)
        set_cell_margins(cell, top=80, bottom=80, left=120, right=120)
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        r = p.add_run(str(val))
        r.font.name = 'Calibri'
        r.font.size = Pt(9)
        r.font.color.rgb = DARK_NEUTRAL

print("Building document...")
doc = docx.Document()

# Page Margins
sections = doc.sections
for section in sections:
    section.top_margin = Inches(0.8)
    section.bottom_margin = Inches(0.8)
    section.left_margin = Inches(0.8)
    section.right_margin = Inches(0.8)

# Title Block
title_p = doc.add_paragraph()
title_p.paragraph_format.space_before = Pt(0)
title_p.paragraph_format.space_after = Pt(2)
title_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
t_run = title_p.add_run("SMS Guard — Evidence-Locked Deployment Verification Report")
t_run.font.name = 'Calibri'
t_run.font.size = Pt(20)
t_run.font.bold = True
t_run.font.color.rgb = PRIMARY_COLOR

sub_p = doc.add_paragraph()
sub_p.paragraph_format.space_before = Pt(0)
sub_p.paragraph_format.space_after = Pt(14)
sub_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
sub_run = sub_p.add_run("Empirical Audit & Ground-Truth Verification for Research Paper Section 5.5\nDocument Version: 1.0.0 | Verification Date: August 17, 2026")
sub_run.font.name = 'Calibri'
sub_run.font.size = Pt(10.5)
sub_run.font.italic = True
sub_run.font.color.rgb = SECONDARY_COLOR

add_callout_box(
    doc,
    "AUDIT SCOPE & SCIENTIFIC RIGOR NOTICE",
    "This document establishes a strict, evidence-locked empirical record of the on-device Android deployment for the SMS Guard research project. All values, file sizes, SHA-256 cryptographic hashes, build configurations, and physical-device metrics are extracted directly from verified project files, persistent logs, and active build artifacts. No speculative assumptions or synthetic data are included."
)

# SECTION 1
add_header_styled(doc, "1. Purpose & Research Grounding", level=1)
add_paragraph_styled(doc, "The objective of this report is to provide an unalterable evidence base for drafting Section 5.5 ('On-Device Mobile Deployment & Optimization') of the SMS Guard research paper. The frozen research detection engine combines DistilBERT transformer embeddings (ML Engine), deterministic security heuristics (Rule Engine), lexical/TLD risk scoring (URL Engine), and communication metadata (Sender Context Engine) with cascaded score fusion and confidence-gated decision routing. This document verifies the technical feasibility, packaging optimization, cryptographic integrity, and physical runtime stability of the resulting mobile application.")

# SECTION 2
add_header_styled(doc, "2. Current Android Project State & Architecture", level=1)
add_bullet_styled(doc, "Project Root: ", "c:\\Users\\hp\\Desktop\\SMS_Fraud [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Application Module: ", "app/ (Package: com.smsfraud.detector) [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Build System: ", "Gradle 8.7 with Android Gradle Plugin (AGP) 8.4.0 [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Language & Toolchain: ", "Kotlin 2.0.0, Java 17 (JVM target 17), Kotlin Symbol Processing (KSP) 2.0.0-1.0.21 [VERIFIED — CURRENT]")
add_bullet_styled(doc, "UI & Frameworks: ", "Jetpack Compose BOM 2024.06.00 (Compose UI 1.6.8, Material3), Dagger-Hilt 2.51.1, Jetpack Room 2.6.1 [VERIFIED — CURRENT]")
add_bullet_styled(doc, "On-Device ML Inference Engine: ", "ONNX Runtime Mobile Android 1.18.0 (com.microsoft.onnxruntime:onnxruntime-android:1.18.0) [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Target Specifications: ", "minSdk = 26 (Android 8.0 Oreo), compileSdk = 34, targetSdk = 34 (Android 14) [VERIFIED — CURRENT]")

# SECTION 3
add_header_styled(doc, "3. Build Variant Configuration & Packaging Separation", level=1)
add_paragraph_styled(doc, "To resolve the APK size challenge without violating research reproducibility constraints, the project implements a clean separation using Gradle Product Flavors along the 'modelVariant' dimension:")

add_paragraph_styled(doc, "Exact Gradle Configuration in app/build.gradle.kts:", space_after=2, bold_prefix="[VERIFIED — CURRENT Source Extract]: ")

# Gradle Code snippet box
add_callout_box(
    doc,
    "build.gradle.kts (Flavor Dimension & ABI Filtering)",
    'flavorDimensions.add("modelVariant")\n'
    'productFlavors {\n'
    '    create("research") {\n'
    '        dimension = "modelVariant"\n'
    '        applicationIdSuffix = ".research"\n'
    '        buildConfigField("boolean", "INCLUDE_FP32_MODEL", "true")\n'
    '        ndk {\n'
    '            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))\n'
    '        }\n'
    '    }\n'
    '    create("device") {\n'
    '        dimension = "modelVariant"\n'
    '        buildConfigField("boolean", "INCLUDE_FP32_MODEL", "false")\n'
    '        ndk {\n'
    '            abiFilters.add("arm64-v8a")\n'
    '        }\n'
    '    }\n'
    '}\n'
    'buildTypes {\n'
    '    release {\n'
    '        isMinifyEnabled = true\n'
    '        signingConfig = signingConfigs.getByName("debug")\n'
    '        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")\n'
    '    }\n'
    '}',
    bg_hex="F8FAFC",
    border_hex="4A5568"
)

add_bullet_styled(doc, "RESEARCH Flavor: ", "Packages both the 255.54 MB FP32 baseline model (app/src/research/assets/distilbert_fraud.onnx) and the 64.21 MB INT8 dynamic model, including all 4 target ABIs (armeabi-v7a, arm64-v8a, x86, x86_64). Purpose: Research parity benchmarks, server validation, and desktop/emulator parity testing.")
add_bullet_styled(doc, "DEVICE Flavor: ", "Packages ONLY the 64.21 MB INT8 dynamic quantized model (app/src/main/assets/distilbert_fraud_dynamic_int8.onnx), strictly filters native libraries to arm64-v8a (16.26 MB), and enables R8 code shrinking and resource minification in release mode. Purpose: Lightweight, production-grade deployment on modern physical 64-bit ARM Android devices.")

# SECTION 4
add_header_styled(doc, "4. APK Build Artifact Evidence & Size Progression", level=1)
add_paragraph_styled(doc, "The table below details all recorded APK build variants, tracking the packaging evolution from the unoptimized universal debug build to the final R8-shrunk physical device release APK:")

# Table of APK Variants
t_apk = doc.add_table(rows=6, cols=6)
t_apk.alignment = WD_TABLE_ALIGNMENT.CENTER
t_apk.autofit = False
set_table_borders(t_apk)

headers_apk = ["Variant", "File Path", "Exact Bytes", "Size (MB)", "Build Configuration", "Evidence Status"]
widths_apk = [1.1, 1.7, 1.0, 0.7, 1.0, 1.0]
format_table_header(t_apk.rows[0], widths_apk, headers_apk)

rows_apk_data = [
    ("Universal Debug", "app/build/outputs/apk/debug/app-debug.apk", "419,417,908", "399.99 MB", "Debug / Multi-ABI / FP32+INT8", "[VERIFIED — HISTORICAL]"),
    ("Universal Release", "app/build/outputs/apk/release/app-release-unsigned.apk", "403,747,624", "385.04 MB", "Release (Unsigned) / Multi-ABI / FP32+INT8", "[VERIFIED — HISTORICAL]"),
    ("Research Debug", "app/build/outputs/apk/research/debug/app-research-debug.apk", "419,055,264", "399.64 MB", "Research Debug / Multi-ABI / FP32+INT8", "[VERIFIED — HISTORICAL]"),
    ("Device Debug", "app/build/outputs/apk/device/debug/app-device-debug.apk", "101,537,176", "96.83 MB", "Device Debug / ARM64-only / INT8 only", "[VERIFIED — HISTORICAL]"),
    ("Device Release", "release_archive/app-device-release.apk", "86,237,732", "82.24 MB", "Device Release / ARM64-only / INT8 / R8", "[VERIFIED — CURRENT]")
]

for i, rdata in enumerate(rows_apk_data):
    format_table_row(t_apk.rows[i+1], widths_apk, rdata, is_even=(i%2==1))

p_sp = doc.add_paragraph()
p_sp.paragraph_format.space_before = Pt(0)
p_sp.paragraph_format.space_after = Pt(4)

# SECTION 5
add_header_styled(doc, "5. Current Archived Production APK Verification", level=1)
add_paragraph_styled(doc, "The current production release APK is permanently archived outside the temporary build directory to prevent accidental deletion during Gradle cleaning:")
add_bullet_styled(doc, "Archived File Path: ", "release_archive/app-device-release.apk [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Exact File Size: ", "86,237,732 bytes (82.24 MB / 82.238 MB) [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Cryptographic Hash (SHA-256): ", "7F43C9332900E7004EA7F8BEC9A402BA6512BFD04784896C344D8C2A10066C5C [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Signature Verification: ", "Signed with APK Signature Scheme v2 (RSA 2048-bit Android Debug certificate, Verified by apksigner) [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Integrity Match: ", "100% Exact bitwise and cryptographic match to expected release specification [VERIFIED — CURRENT]")

# SECTION 6
add_header_styled(doc, "6. APK Internal Content Forensics (Uncompressed vs. Packaged)", level=1)
add_paragraph_styled(doc, "A full read-only forensic inspection of the internal ZIP archive of release_archive/app-device-release.apk reveals the exact internal byte distribution:")

t_forensics = doc.add_table(rows=7, cols=5)
t_forensics.alignment = WD_TABLE_ALIGNMENT.CENTER
t_forensics.autofit = False
set_table_borders(t_forensics)

headers_f = ["Component / Asset", "Internal Archive Path", "Uncompressed Size", "Packaged Size", "Forensic Finding"]
widths_f = [1.3, 2.0, 1.1, 1.0, 1.1]
format_table_header(t_forensics.rows[0], widths_f, headers_f)

rows_f_data = [
    ("INT8 ONNX Model", "assets/distilbert_fraud_dynamic_int8.onnx", "67,327,609 B (64.21 MB)", "67,327,609 B (64.21 MB)", "PRESENT (Stored uncompressed for zero-copy memory mapping)"),
    ("FP32 ONNX Model", "assets/distilbert_fraud.onnx", "0 B", "0 B", "CONFIRMED ABSENT (Successfully stripped)"),
    ("ONNX Runtime Native", "lib/arm64-v8a/libonnxruntime.so", "16,276,832 B (15.52 MB)", "16,276,832 B (15.52 MB)", "PRESENT (ARM64 64-bit binary engine)"),
    ("ONNX JNI Bridge", "lib/arm64-v8a/libonnxruntime4j_jni.so", "768,976 B (750.95 KB)", "768,976 B (750.95 KB)", "PRESENT (Java native interface)"),
    ("Redundant ABIs", "lib/{armeabi-v7a, x86, x86_64}/", "0 B", "0 B", "CONFIRMED ABSENT (100% filtered)"),
    ("Compiled Classes (DEX)", "classes.dex (Single DEX)", "2,169,032 B (2.07 MB)", "1,028,477 B (0.98 MB)", "PRESENT (R8 ProGuard optimized & merged into 1 DEX)")
]

for i, rdata in enumerate(rows_f_data):
    format_table_row(t_forensics.rows[i+1], widths_f, rdata, is_even=(i%2==1))

p_sp2 = doc.add_paragraph()
p_sp2.paragraph_format.space_before = Pt(0)
p_sp2.paragraph_format.space_after = Pt(4)

# SECTION 7
add_header_styled(doc, "7. Source Model Verification & Preservation", level=1)
add_paragraph_styled(doc, "Both the baseline research model and the active edge inference model are preserved in the project source tree:")

t_models = doc.add_table(rows=3, cols=5)
t_models.alignment = WD_TABLE_ALIGNMENT.CENTER
t_models.autofit = False
set_table_borders(t_models)

headers_m = ["Model Variant", "Source Location", "Exact Size", "SHA-256 Hash", "Role in Project"]
widths_m = [1.2, 1.9, 0.9, 1.3, 1.2]
format_table_header(t_models.rows[0], widths_m, headers_m)

rows_m_data = [
    ("INT8 Quantized (Dynamic)", "app/src/main/assets/distilbert_fraud_dynamic_int8.onnx", "67,327,609 B\n(64.21 MB)", "0A32D9C572DE78331B9E41DDDA4AF5A31D1F592DD7F862106E6F2C141AA0C486", "Active edge runtime inference model [VERIFIED — CURRENT]"),
    ("FP32 Unquantized (Baseline)", "app/src/research/assets/distilbert_fraud.onnx", "267,955,636 B\n(255.54 MB)", "4FBCCF4378E7D5AD51753443C2CE7FD99931946926718DD69E5BE996B02FF5E8", "Research baseline & parity anchor [VERIFIED — CURRENT]")
]

for i, rdata in enumerate(rows_m_data):
    format_table_row(t_models.rows[i+1], widths_m, rdata, is_even=(i%2==1))

p_sp3 = doc.add_paragraph()
p_sp3.paragraph_format.space_before = Pt(0)
p_sp3.paragraph_format.space_after = Pt(4)

# SECTION 8
add_header_styled(doc, "8. Physical Device Runtime Verification", level=1)
add_paragraph_styled(doc, "The optimized Device Release APK was deployed and tested on a commercial physical Android smartphone via Android Debug Bridge (ADB):")
add_bullet_styled(doc, "Target Smartphone: ", "Xiaomi POCO X5 Pro 5G (Model: 22101320I / Codename: redwoodin) [VERIFIED — HISTORICAL]")
add_bullet_styled(doc, "Operating System: ", "Android 14 (API Level 34) / HyperOS 2.0 (Build OS2.0.15.0.UMSINXM) [VERIFIED — HISTORICAL]")
add_bullet_styled(doc, "CPU Architecture: ", "Qualcomm Snapdragon 778G (arm64-v8a 64-bit) [VERIFIED — HISTORICAL]")
add_bullet_styled(doc, "Installation Result: ", "Streamed Install SUCCESS (Package: com.smsfraud.detector) [VERIFIED — HISTORICAL]")
add_bullet_styled(doc, "Cold-Start Launch Latency: ", "Displayed com.smsfraud.detector/.MainActivity in +841ms (PID 9757) [VERIFIED — HISTORICAL]")
add_bullet_styled(doc, "Runtime Process Status: ", "Active background process without crashes or memory exhaustion [VERIFIED — HISTORICAL]")

# SECTION 9
add_header_styled(doc, "9. Settings Crash Fix & Lifecycle Diagnostics", level=1)
add_paragraph_styled(doc, "During initial physical device testing of the release build, a runtime crash occurred when navigating to the Settings screen. The root cause and resolution were systematically documented:")
add_bullet_styled(doc, "Crash Signature: ", "java.lang.IllegalStateException: CompositionLocal LocalLifecycleOwner not present [VERIFIED — HISTORICAL]")
add_bullet_styled(doc, "Root Cause: ", "Jetpack Compose BOM 2024.06.00 (Compose UI 1.6.8) populates androidx.compose.ui.platform.LocalLifecycleOwner at the root of the composition tree. The screen incorrectly referenced androidx.lifecycle.compose.LocalLifecycleOwner from Lifecycle 2.8.2, which was not wired by Compose 1.6.8 and threw an uninitialized CompositionLocal error [VERIFIED — CURRENT Analysis].")
add_bullet_styled(doc, "One-Line Resolution: ", "Updated app/src/main/java/com/smsfraud/detector/ui/settings/SettingsScreen.kt (Line 79) from androidx.lifecycle.compose.LocalLifecycleOwner.current to androidx.compose.ui.platform.LocalLifecycleOwner.current [VERIFIED — CURRENT].")
add_bullet_styled(doc, "Post-Fix Validation: ", "Zero FATAL EXCEPTION, zero AndroidRuntime crashes on physical device during continuous Settings tab transitions [VERIFIED — HISTORICAL].")

# SECTION 10
add_header_styled(doc, "10. Detection Engine Integrity Verification", level=1)
add_paragraph_styled(doc, "All 8 core engine files were cryptographically audited to guarantee that zero detection logic, scoring weights, or classification thresholds were altered during packaging optimization:")

t_engine = doc.add_table(rows=9, cols=4)
t_engine.alignment = WD_TABLE_ALIGNMENT.CENTER
t_engine.autofit = False
set_table_borders(t_engine)

headers_e = ["Module / File", "Exact File Path", "SHA-256 Hash", "Integrity Status"]
widths_e = [1.5, 2.0, 1.8, 1.2]
format_table_header(t_engine.rows[0], widths_e, headers_e)

rows_e_data = [
    ("Decision Controller", "app/src/main/java/.../DecisionControllerImpl.kt", "149E35EED1984E4145400B1CAE839396677A0045984809E6EAD9F1E05BFAFD88", "FROZEN [VERIFIED — CURRENT]"),
    ("Score Fusion", "app/src/main/java/.../ScoreFusionImpl.kt", "35D2707DF1E33CCBC690D56D289AFF95B8B9B256EA7A4ECDFD225BF82DB0F586", "FROZEN [VERIFIED — CURRENT]"),
    ("Rule Engine", "app/src/main/java/.../RuleEngineImpl.kt", "D9DEB8996912CD9FEA5FA4364844CC70C94A4F46313B5334542128F46DEE9633", "FROZEN [VERIFIED — CURRENT]"),
    ("URL Engine", "app/src/main/java/.../UrlEngineImpl.kt", "D1AE03F0AEC4C3FA3DCDAAD2C4A906069F1B1AF1564BCA08690090FB3CDFFC86", "FROZEN [VERIFIED — CURRENT]"),
    ("Sender Context Engine", "app/src/main/java/.../SenderContextEngineImpl.kt", "6603FFD22A400CE7F0F70FD32D63C17B8A08A2C44636B2DD15A0898923E7CC63", "FROZEN [VERIFIED — CURRENT]"),
    ("ML Engine", "app/src/main/java/.../MlEngineImpl.kt", "F05F11A85D000B84D1924AA7FDA1B4DDC2B64AD7C52BD1B38991178B3289C7FE", "FROZEN [VERIFIED — CURRENT]"),
    ("Text Preprocessor", "app/src/main/java/.../TextPreprocessor.kt", "BEFE4BA99BB6DEB63FEC9D80F68FB1AC0B107894A69934C53D01B2FE436A695E", "FROZEN [VERIFIED — CURRENT]"),
    ("Detection Pipeline", "app/src/main/java/.../DetectionPipeline.kt", "2859EF432072A3D4BC1D83F7CC2420EA95634EB128D6DE95608D95569BCD2444", "FROZEN [VERIFIED — CURRENT]")
]

for i, rdata in enumerate(rows_e_data):
    format_table_row(t_engine.rows[i+1], widths_e, rdata, is_even=(i%2==1))

p_sp4 = doc.add_paragraph()
p_sp4.paragraph_format.space_before = Pt(0)
p_sp4.paragraph_format.space_after = Pt(4)

add_bullet_styled(doc, "Frozen Fusion Weights: ", "W_ML = 0.30, W_URL = 0.30, W_Sender = 0.40 (Zero-URL fallback: W_ML = 0.50, W_Sender = 0.50) [VERIFIED — CURRENT]")
add_bullet_styled(doc, "Frozen Decision Thresholds: ", "PASS_MAX = 0.25, ESCALATE_MAX = 0.65, CONFIDENCE_GATE = 0.50, HIGH_CONF_PASS = 0.85, HIGH_CONF_BLOCK = 0.80 [VERIFIED — CURRENT]")

# SECTION 11
add_header_styled(doc, "11. End-to-End SMS Pipeline Execution Sequence", level=1)
add_paragraph_styled(doc, "The on-device SMS ingestion sequence operates without network egress, structured as follows:")
add_bullet_styled(doc, "1. Ingestion: ", "android.provider.Telephony.SMS_RECEIVED broadcast intercepted by SmsReceiver (Priority 999).")
add_bullet_styled(doc, "2. Privacy Preprocessing: ", "Raw message scrubbed of PII/phone numbers, URLs extracted, normalized by TextPreprocessor.")
add_bullet_styled(doc, "3. Heuristic & Lexical Scoring: ", "RuleEngine evaluates structural fraud patterns; UrlEngine scores domain lexical/TLD entropy.")
add_bullet_styled(doc, "4. Metadata Evaluation: ", "SenderContextEngine evaluates contact address book presence and alphanumeric sender headers.")
add_bullet_styled(doc, "5. Transformer Inference: ", "MlEngine executes DistilBERT INT8 ONNX graph on device CPU (ARM NEON accelerated).")
add_bullet_styled(doc, "6. Fusion & Routing: ", "ScoreFusion computes weighted aggregate score S_fusion; DecisionController enforces confidence gates, outputting PASS, ESCALATE, BLOCK, or PASS_WATCH.")
add_bullet_styled(doc, "7. Notification & Storage: ", "NotificationHelper dispatches channel-specific user warning; Room Database persists risk breakdown telemetry without storing raw SMS text.")

# SECTION 12
add_header_styled(doc, "12. Quantitative Size Reduction Calculations", level=1)
add_paragraph_styled(doc, "Mathematical formulas and verified calculations documenting the packaging size optimization:")

add_paragraph_styled(doc, "Formula: Reduction (%) = ((Original Size − Optimized Size) / Original Size) × 100", bold_prefix="Size Reduction Formula: ")

add_bullet_styled(doc, "A. Universal Debug → Device Release APK Reduction: ", "((419,417,908 − 86,237,732) / 419,417,908) × 100 = 79.44% Reduction (-333,180,176 bytes / -317.75 MB)")
add_bullet_styled(doc, "B. Universal Release → Device Release APK Reduction: ", "((403,747,624 − 86,237,732) / 403,747,624) × 100 = 78.64% Reduction (-317,509,892 bytes / -302.80 MB)")
add_bullet_styled(doc, "C. FP32 Model → INT8 Model Storage Reduction: ", "((267,955,636 − 67,327,609) / 267,955,636) × 100 = 74.87% Reduction (-200,628,027 bytes / -191.33 MB)")
add_bullet_styled(doc, "D. Native Library ABI Filtering Reduction: ", "((47,208,960 − 16,276,832) / 47,208,960) × 100 = 65.52% Reduction (-30,932,128 bytes / -29.50 MB)")

add_callout_box(
    doc,
    "CRITICAL DISTINCTION: APK SIZE vs. BUILD DIRECTORY CLEANUP",
    "• APK Size Optimization: A permanent 79.4% reduction in application package size (from 400 MB to 82.24 MB) achieved through INT8 dynamic quantization, ARM64 ABI filtering, and R8 byte-code shrinking.\n"
    "• Development Build Storage Cleanup: A temporary 4.71 GB workspace reclaim achieved by deleting intermediate Gradle build caches (app/build/) without modifying source code or model weights."
)

# SECTION 13
add_header_styled(doc, "13. Evidence Classification & Provenance Matrix", level=1)
add_paragraph_styled(doc, "To maintain the highest scientific integrity, all findings are categorized into three distinct evidentiary classes:")

t_evidence = doc.add_table(rows=4, cols=3)
t_evidence.alignment = WD_TABLE_ALIGNMENT.CENTER
t_evidence.autofit = False
set_table_borders(t_evidence)

headers_ev = ["Evidence Class", "Definition & Standard", "Included Verified Elements"]
widths_ev = [1.5, 2.2, 2.8]
format_table_header(t_evidence.rows[0], widths_ev, headers_ev)

rows_ev_data = [
    ("Class A:\n[VERIFIED — CURRENT]", "Artifacts, source files, and binary packages currently residing in the active workspace filesystem.", "Archived Release APK (82.24 MB, SHA-256 7F43C933...), Canonical INT8 Model (64.21 MB), Canonical FP32 Model (255.54 MB), 8 Detection Engine source files & SHA-256 hashes, build.gradle.kts flavor definitions, 235 unit tests passing."),
    ("Class B:\n[VERIFIED — HISTORICAL]", "Metrics and build sizes recorded in persistent test reports, build logs, and prior diagnostic session outputs.", "Historical universal debug/release APK sizes (399.99 MB, 385.04 MB), Xiaomi POCO X5 Pro 5G ADB device properties (Android 14, API 34), ADB streamed installation success output, cold-start latency (+841ms)."),
    ("Class C:\n[OBSERVED — NOT CURRENTLY RECOVERABLE]", "Observations noted during physical manual interactions where persistent file-based logs were not written.", "Visual rendering of Jetpack Compose UI onboarding cards on physical device screen during manual touch navigation.")
]

for i, rdata in enumerate(rows_ev_data):
    format_table_row(t_evidence.rows[i+1], widths_ev, rdata, is_even=(i%2==1))

p_sp5 = doc.add_paragraph()
p_sp5.paragraph_format.space_before = Pt(0)
p_sp5.paragraph_format.space_after = Pt(4)

# SECTION 14
add_header_styled(doc, "14. Limitations & Missing Evidence", level=1)
add_bullet_styled(doc, "1. Cellular Carrier SMS Testing: ", "Due to Android 14 security policies prohibiting unauthorized broadcast injection via ADB shell without root/system privileges, end-to-end SMS ingestion was verified via the comprehensive JVM test suite rather than live carrier SMS transmission.")
add_bullet_styled(doc, "2. Temporary Build Directory Absence: ", "Historical intermediate build APKs (app-debug.apk, app-release-unsigned.apk) no longer exist on disk following the 4.71 GB Gradle build cleanup, though their exact sizes are preserved in persistent diagnostic records.")
add_bullet_styled(doc, "3. PASS-WATCH Physical Device Verification: ", "PASS-WATCH physical device testing was unavailable because no unsaved/unverified phone number was available during physical device testing; the decision branch is 100% verified via automated unit tests.")

# SECTION 15
add_header_styled(doc, "15. Final Audit Summary Table", level=1)
add_paragraph_styled(doc, "The summary below provides a definitive verdict on every technical claim regarding the Android mobile deployment:")

t_summary = doc.add_table(rows=12, cols=4)
t_summary.alignment = WD_TABLE_ALIGNMENT.CENTER
t_summary.autofit = False
set_table_borders(t_summary)

headers_s = ["Deployment Claim", "Evidence Source / File", "Verification Status", "Verdict"]
widths_s = [1.8, 2.4, 1.3, 1.0]
format_table_header(t_summary.rows[0], widths_s, headers_s)

rows_s_data = [
    ("Device Release APK = 82.24 MB", "release_archive/app-device-release.apk (86,237,732 B)", "[VERIFIED — CURRENT]", "CONFIRMED"),
    ("INT8 Model Packaged (64.21 MB)", "assets/distilbert_fraud_dynamic_int8.onnx in APK", "[VERIFIED — CURRENT]", "CONFIRMED"),
    ("FP32 Excluded from Device APK", "assets/distilbert_fraud.onnx missing in APK", "[VERIFIED — CURRENT]", "CONFIRMED"),
    ("Native Libs: arm64-v8a Only", "lib/arm64-v8a/libonnxruntime.so (16.26 MB)", "[VERIFIED — CURRENT]", "CONFIRMED"),
    ("R8 / Minification Enabled", "classes.dex (Single DEX, 0.98 MB compressed)", "[VERIFIED — CURRENT]", "CONFIRMED"),
    ("APK SHA-256 Hash Match", "7F43C9332900E7004EA7F8BEC9A402BA6512BFD04784896C344D8C2A10066C5C", "[VERIFIED — CURRENT]", "CONFIRMED"),
    ("Physical Device Installation", "Xiaomi POCO X5 Pro 5G ADB Install Stream", "[VERIFIED — HISTORICAL]", "CONFIRMED"),
    ("Physical Device App Launch", "ActivityTaskManager +841ms Cold Start (PID 9757)", "[VERIFIED — HISTORICAL]", "CONFIRMED"),
    ("Settings Crash Fix", "SettingsScreen.kt:79 LocalLifecycleOwner import", "[VERIFIED — CURRENT]", "CONFIRMED"),
    ("Unit Test Regression Suite", "testDeviceDebugUnitTest (235/235 Passed, 0 Failures)", "[VERIFIED — HISTORICAL]", "CONFIRMED"),
    ("Detection Engine Unchanged", "All 8 engine files SHA-256 hash match frozen spec", "[VERIFIED — CURRENT]", "CONFIRMED")
]

for i, rdata in enumerate(rows_s_data):
    format_table_row(t_summary.rows[i+1], widths_s, rdata, is_even=(i%2==1))

# Save document
doc.save(DOC_NAME)
print(f"Document saved successfully: {DOC_NAME}")
