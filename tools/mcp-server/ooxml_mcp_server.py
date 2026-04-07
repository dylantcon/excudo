#!/usr/bin/env python3
"""
OOXML MCP Server for Excudo.

Exposes twelve tools via MCP stdio transport:
  1.  spec_search              - Search MS-OE376, MS-OI29500, MS-PPTX, ECMA-376 by keyword/section
  2.  validate_pptx            - Validate a PPTX file via the pc.py 3-layer validation system
  3.  oracle_pattern           - Extract reference animation XML from the PowerPoint oracle file
  4.  extract_timing           - Extract timing XML from any PPTX file
  5.  inject_test_animations   - Run console commands to create a test PPTX and return timing XML
  6.  extract_theme            - Extract theme/master/layout XML from any PPTX
  7.  theme_summary            - Compact RLE-style theme representation
  8.  inject_test_theme        - Run console commands for theme ops, then extract + validate
  9.  console_exec             - Run console REPL commands, return structured per-command output
  10. diff_pptx                - Extract two PPTX files and diff all XML parts
  11. inspect_rels             - Dump full relationship graph, content types, and slide chains
  12. generate_crud_sample     - Scaffold a test PPTX for a specific CRUD domain
"""

import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from xml.dom import minidom

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPECS_DIR = os.path.join(PROJECT_ROOT, "docs", "ms-specs")
ORACLE_SLIDES_DIR = os.path.join(PROJECT_ROOT, "test-pptx-samples", "all_anim_extracted", "ppt", "slides")

# Spec files available for search, keyed by short name
SPEC_FILES = {
    "MS-OE376":      os.path.join(SPECS_DIR, "MS-OE376.txt"),
    "MS-OE376-pml":  os.path.join(SPECS_DIR, "MS-OE376-pml.txt"),
    "MS-OI29500":    os.path.join(SPECS_DIR, "MS-OI29500.txt"),
    "MS-OI29500-pml":os.path.join(SPECS_DIR, "MS-OI29500-pml.txt"),
    "MS-PPTX":       os.path.join(SPECS_DIR, "MS-PPTX.txt"),
}

# Auto-discover OpenXML SDK spec pages (topic-specific ISO 29500-1 extracts)
_SDK_DIR = os.path.join(SPECS_DIR, "openxml-sdk")
if os.path.isdir(_SDK_DIR):
    for _fname in sorted(os.listdir(_SDK_DIR)):
        if _fname.endswith(".txt"):
            _key = "sdk:" + _fname.replace(".txt", "")
            SPEC_FILES[_key] = os.path.join(_SDK_DIR, _fname)

# All spec short names, for reference in tool descriptions
ALL_SPEC_NAMES = sorted(SPEC_FILES.keys())

# Build oracle index on startup: slide_num -> {title, presetID, presetClass, filepath}
ORACLE_INDEX = {}

def _build_oracle_index():
    """Parse all oracle slides to build a searchable index."""
    ns = {
        "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
        "p": "http://schemas.openxmlformats.org/presentationml/2006/main",
    }
    for fname in os.listdir(ORACLE_SLIDES_DIR):
        if not fname.endswith(".xml") or fname.startswith("_"):
            continue
        match = re.search(r"(\d+)", fname)
        if not match:
            continue
        num = int(match.group(1))
        fpath = os.path.join(ORACLE_SLIDES_DIR, fname)
        try:
            tree = ET.parse(fpath)
            root = tree.getroot()
            # Extract title
            title = ""
            for sp in root.findall(".//p:sp", ns):
                ph = sp.find(".//p:nvSpPr/p:nvPr/p:ph", ns)
                if ph is not None and ph.get("type") == "title":
                    texts = sp.findall(".//a:t", ns)
                    title = "".join(t.text or "" for t in texts).strip()
                    break
            # Extract first presetID/presetClass
            preset_id = None
            preset_class = None
            for ctn in root.findall(".//{http://schemas.openxmlformats.org/presentationml/2006/main}cTn"):
                pid = ctn.get("presetID")
                if pid:
                    preset_id = pid
                    preset_class = ctn.get("presetClass")
                    break
            ORACLE_INDEX[num] = {
                "title": title,
                "presetID": preset_id,
                "presetClass": preset_class,
                "filepath": fpath,
            }
        except Exception:
            pass


PPTX_NS_WRAPPER = (
    '<wrapper xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
    ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
    ' xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">'
    '{}</wrapper>'
)


def _pretty_xml(xml_str):
    """Pretty-print XML, handling single-line PowerPoint format with namespace prefixes."""
    try:
        dom = minidom.parseString(xml_str)
    except Exception:
        # Likely unbound namespace prefix -- wrap with declarations and unwrap after
        try:
            wrapped = PPTX_NS_WRAPPER.format(xml_str)
            dom = minidom.parseString(wrapped)
            # Remove the <wrapper> layer
            wrapper_el = dom.documentElement
            lines = []
            for child in wrapper_el.childNodes:
                lines.append(child.toprettyxml(indent="  "))
            combined = "\n".join(lines)
            out = [l for l in combined.split("\n") if l.strip()]
            if out and out[0].startswith("<?xml"):
                out = out[1:]
            return "\n".join(out)
        except Exception:
            return xml_str

    pretty = dom.toprettyxml(indent="  ")
    lines = [l for l in pretty.split("\n") if l.strip()]
    if lines and lines[0].startswith("<?xml"):
        lines = lines[1:]
    return "\n".join(lines)


def _extract_timing_section(xml_content):
    """Extract the <p:timing>...</p:timing> section from slide XML."""
    # Work with raw string since the XML is single-line
    match = re.search(r"(<p:timing>.*?</p:timing>)", xml_content, re.DOTALL)
    if match:
        return match.group(1)
    return None


def _extract_animation_elements(xml_content):
    """Extract timing + bldLst sections, pretty-printed."""
    timing = _extract_timing_section(xml_content)
    if not timing:
        return "No animation timing found in this slide."
    return _pretty_xml(timing)


# ---------------------------------------------------------------------------
# Tool implementations
# ---------------------------------------------------------------------------

def spec_search(query, specs=None, context_lines=3, max_results=20, section_number=None):
    """
    Search MS Office spec documents by keyword or section number.

    Args:
        query: Search keyword or phrase (regex supported)
        specs: List of spec names to search. Use "all" to search everything,
               "sdk" to search all OpenXML SDK topic pages, or specific names.
               Default: PML extracts + all SDK topic pages.
               Options: MS-OE376, MS-OE376-pml, MS-OI29500, MS-OI29500-pml, MS-PPTX,
                        sdk:BodyProperties, sdk:Placeholder, etc., or "all", "sdk"
        context_lines: Number of context lines before/after match (default 3)
        max_results: Maximum number of results to return (default 20)
        section_number: If provided, search for this specific section (e.g., "4.6.33")
    """
    if specs is None:
        # Default: PML extracts + all SDK topic pages (fast, focused)
        specs = ["MS-OE376-pml", "MS-OI29500-pml"] + [k for k in ALL_SPEC_NAMES if k.startswith("sdk:")]
    else:
        # Expand shorthand aliases
        expanded = []
        for s in specs:
            if s == "all":
                expanded = list(ALL_SPEC_NAMES)
                break
            elif s == "sdk":
                expanded.extend(k for k in ALL_SPEC_NAMES if k.startswith("sdk:"))
            else:
                expanded.append(s)
        specs = expanded

    if section_number:
        query = re.escape(section_number)

    results = []
    for spec_name in specs:
        fpath = SPEC_FILES.get(spec_name)
        if not fpath or not os.path.exists(fpath):
            results.append({"spec": spec_name, "error": f"Spec file not found: {spec_name}"})
            continue

        with open(fpath, "r", encoding="utf-8", errors="replace") as f:
            lines = f.readlines()

        matches = []
        try:
            pattern = re.compile(query, re.IGNORECASE)
        except re.error as e:
            results.append({"spec": spec_name, "error": f"Invalid regex: {e}"})
            continue

        for i, line in enumerate(lines):
            if pattern.search(line):
                start = max(0, i - context_lines)
                end = min(len(lines), i + context_lines + 1)
                context = "".join(lines[start:end]).rstrip()
                matches.append({
                    "line_number": i + 1,
                    "context": context,
                })
                if len(matches) >= max_results:
                    break

        results.append({
            "spec": spec_name,
            "query": query,
            "match_count": len(matches),
            "matches": matches,
        })

    return results


def validate_pptx(filepath, layers=None, office_version="Microsoft365", output_format="json"):
    """
    Validate a PPTX file using the pc.py multi-layer validation system.

    Args:
        filepath: Path to the PPTX file (absolute or relative to project root)
        layers: Validation layers to run. Default: ["ooxml"].
                Options: "ooxml" (schema), "libreoffice" (structural), "powerpoint" (native), "all"
        office_version: Target Office version (default: Microsoft365)
        output_format: Output format - "json" or "xml" (default: json)
    """
    if layers is None:
        layers = ["ooxml"]

    if not os.path.isabs(filepath):
        filepath = os.path.join(PROJECT_ROOT, filepath)

    if not os.path.exists(filepath):
        return {"error": f"File not found: {filepath}"}

    cmd = [
        sys.executable, os.path.join(PROJECT_ROOT, "pc.py"),
        "validate", filepath,
        "--office-version", office_version,
        "--format", output_format,
    ]

    if "all" in layers:
        cmd.append("--all-layers")
    else:
        if "libreoffice" in layers:
            cmd.append("--libreoffice")
        if "powerpoint" in layers:
            cmd.append("--powerpoint")

    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=60,
            cwd=PROJECT_ROOT,
        )
        output = result.stdout.strip()
        stderr = result.stderr.strip()

        if output_format == "json":
            try:
                parsed = json.loads(output)
                return {
                    "validation_result": parsed,
                    "return_code": result.returncode,
                    "stderr": stderr if stderr else None,
                }
            except json.JSONDecodeError:
                pass

        return {
            "output": output,
            "stderr": stderr if stderr else None,
            "return_code": result.returncode,
        }
    except subprocess.TimeoutExpired:
        return {"error": "Validation timed out after 60 seconds"}
    except Exception as e:
        return {"error": str(e)}


def oracle_pattern(query, mode="search", pretty=True):
    """
    Extract animation patterns from the PowerPoint oracle reference file.
    155 slides covering all animation types, created natively in PowerPoint.

    Args:
        query: Search term. Interpreted based on mode:
               - mode="search": keyword to match against slide titles (e.g., "Wipe", "Spin", "Fade")
               - mode="preset": presetID to match (e.g., "22" for Wipe, "45" for Swivel)
               - mode="slide": specific slide number (e.g., "43")
               - mode="class": presetClass to match ("entr", "exit", "emph", "path")
               - mode="list": ignored, lists all slides with metadata
        mode: Search mode - "search", "preset", "slide", "class", or "list"
        pretty: Pretty-print the XML output (default True)
    """
    if mode == "list":
        listing = []
        for num in sorted(ORACLE_INDEX.keys()):
            entry = ORACLE_INDEX[num]
            listing.append({
                "slide": num,
                "title": entry["title"],
                "presetID": entry["presetID"],
                "presetClass": entry["presetClass"],
            })
        return {"total_slides": len(listing), "slides": listing}

    matched_slides = []

    if mode == "slide":
        try:
            slide_num = int(query)
        except ValueError:
            return {"error": f"Invalid slide number: {query}"}
        if slide_num in ORACLE_INDEX:
            matched_slides.append(slide_num)
        else:
            return {"error": f"Slide {slide_num} not found. Valid range: 1-{max(ORACLE_INDEX.keys())}"}

    elif mode == "preset":
        for num, entry in ORACLE_INDEX.items():
            if entry["presetID"] == str(query):
                matched_slides.append(num)

    elif mode == "class":
        for num, entry in ORACLE_INDEX.items():
            if entry["presetClass"] == query:
                matched_slides.append(num)

    else:  # mode == "search"
        pattern = re.compile(re.escape(query), re.IGNORECASE)
        for num, entry in ORACLE_INDEX.items():
            if pattern.search(entry["title"]):
                matched_slides.append(num)

    matched_slides.sort()

    if not matched_slides:
        return {
            "error": f"No oracle slides matched query '{query}' (mode={mode})",
            "hint": "Use mode='list' to see all available slides, or mode='search' with a keyword like 'Fade', 'Wipe', 'Spin'",
        }

    results = []
    for slide_num in matched_slides:
        entry = ORACLE_INDEX[slide_num]
        with open(entry["filepath"], "r", encoding="utf-8") as f:
            xml_content = f.read()

        animation_xml = _extract_animation_elements(xml_content)
        if not pretty:
            timing = _extract_timing_section(xml_content)
            animation_xml = timing if timing else "No animation timing found."

        results.append({
            "slide": slide_num,
            "title": entry["title"],
            "presetID": entry["presetID"],
            "presetClass": entry["presetClass"],
            "animation_xml": animation_xml,
        })

    return {"match_count": len(results), "results": results}


def extract_timing(filepath, slide=None, pretty=True):
    """
    Extract and pretty-print <p:timing> XML from any PPTX file.

    Args:
        filepath: Path to the PPTX file (absolute or relative to project root)
        slide: Slide number(s) to extract. None or "all" = all slides.
               Single int or comma-separated "1,3" for specific slides.
        pretty: Pretty-print the XML output (default True)
    """
    if not os.path.isabs(filepath):
        filepath = os.path.join(PROJECT_ROOT, filepath)

    if not os.path.exists(filepath):
        return {"error": f"File not found: {filepath}"}

    try:
        zf = zipfile.ZipFile(filepath, "r")
    except Exception as e:
        return {"error": f"Cannot open PPTX: {e}"}

    # Discover slide XML entries
    slide_entries = sorted(
        [n for n in zf.namelist() if re.match(r"ppt/slides/slide\d+\.xml$", n)],
        key=lambda n: int(re.search(r"(\d+)", n).group(1)),
    )

    # Determine which slides to extract
    requested = set()
    if slide is None or str(slide).strip().lower() == "all":
        requested = {int(re.search(r"(\d+)", n).group(1)) for n in slide_entries}
    else:
        for part in str(slide).split(","):
            part = part.strip()
            try:
                requested.add(int(part))
            except ValueError:
                pass

    results = []
    for entry in slide_entries:
        num = int(re.search(r"(\d+)", entry).group(1))
        if num not in requested:
            continue

        content = zf.read(entry).decode("utf-8")
        timing_raw = _extract_timing_section(content)

        if timing_raw is None:
            results.append({"slide": num, "has_timing": False, "timing_xml": None})
            continue

        timing_xml = _pretty_xml(timing_raw) if pretty else timing_raw

        # Extract bldLst separately if present (it's inside <p:timing>)
        bld_match = re.search(r"(<p:bldLst>.*?</p:bldLst>)", content, re.DOTALL)
        bld_xml = None
        if bld_match:
            bld_xml = _pretty_xml(bld_match.group(1)) if pretty else bld_match.group(1)

        # Extract animation summary metadata
        animations = []
        for ctn_match in re.finditer(r'<p:cTn[^>]*presetID="(\d+)"[^>]*/?>',  content):
            attrs = ctn_match.group(0)
            pid = re.search(r'presetID="(\d+)"', attrs)
            pcls = re.search(r'presetClass="(\w+)"', attrs)
            psub = re.search(r'presetSubtype="(\d+)"', attrs)
            grp = re.search(r'grpId="(\d+)"', attrs)
            ntype = re.search(r'nodeType="(\w+)"', attrs)
            animations.append({
                "presetID": pid.group(1) if pid else None,
                "presetClass": pcls.group(1) if pcls else None,
                "presetSubtype": psub.group(1) if psub else None,
                "grpId": grp.group(1) if grp else None,
                "nodeType": ntype.group(1) if ntype else None,
            })

        results.append({
            "slide": num,
            "has_timing": True,
            "animation_count": len(animations),
            "animations": animations,
            "timing_xml": timing_xml,
            "bldLst_xml": bld_xml,
        })

    zf.close()
    return {"filepath": filepath, "slide_count": len(results), "slides": results}


def inject_test_animations(commands, output_path=None, extract_slides=None, validate=True):
    """
    Run a sequence of console REPL commands to create a test PPTX, then extract
    timing XML and optionally validate.

    Args:
        commands: List of console command strings to execute in order.
                  Must include load, add-animation, and save commands.
                  A 'quit' is appended automatically.
        output_path: Path where the PPTX will be saved. If not provided and no
                     'save' command is found in commands, a temp file is used.
        extract_slides: Slides to extract timing from after creation. Default: "all".
        validate: Run OOXML + LibreOffice validation on the result (default True).
    """
    if not commands:
        return {"error": "No commands provided"}

    # Ensure quit is appended
    cmd_list = list(commands)
    if not any(c.strip().lower() == "quit" for c in cmd_list):
        cmd_list.append("quit")

    # Determine output path from save command or generate temp
    save_path = output_path
    if save_path and not os.path.isabs(save_path):
        save_path = os.path.join(PROJECT_ROOT, save_path)

    if not save_path:
        # Look for a save command to infer path
        for c in cmd_list:
            parts = c.strip().split()
            if parts and parts[0].lower() == "save" and len(parts) > 1:
                candidate = parts[1].strip('"').strip("'")
                if not os.path.isabs(candidate):
                    candidate = os.path.join(PROJECT_ROOT, candidate)
                save_path = candidate
                break

    if not save_path:
        fd, save_path = tempfile.mkstemp(suffix=".pptx", prefix="mcp_inject_")
        os.close(fd)

    # Build input for the REPL
    repl_input = "\n".join(cmd_list) + "\n"

    cmd = [
        sys.executable, os.path.join(PROJECT_ROOT, "pc.py"),
        "run", "console", "--headless",
    ]

    try:
        result = subprocess.run(
            cmd,
            input=repl_input,
            capture_output=True,
            text=True,
            timeout=120,
            cwd=PROJECT_ROOT,
        )
        console_stdout = result.stdout
        console_stderr = result.stderr
        console_rc = result.returncode
    except subprocess.TimeoutExpired:
        return {"error": "Console REPL timed out after 120 seconds"}
    except Exception as e:
        return {"error": f"Failed to run console REPL: {e}"}

    response = {
        "console_output": console_stdout,
        "console_stderr": console_stderr if console_stderr else None,
        "console_return_code": console_rc,
    }

    # Extract timing if the output file exists
    if os.path.exists(save_path):
        response["output_path"] = save_path
        extract_target = extract_slides if extract_slides else "all"
        timing_result = extract_timing(save_path, slide=extract_target, pretty=True)
        response["timing"] = timing_result

        # Validate if requested
        if validate:
            val_result = validate_pptx(save_path, layers=["ooxml", "libreoffice"])
            response["validation"] = val_result
    else:
        response["error"] = f"Output file not found at {save_path}. Check console output for errors."

    return response


# ---------------------------------------------------------------------------
# Theme extraction and summary tools
# ---------------------------------------------------------------------------


def extract_theme(filepath, pretty=True):
    """
    Extract and pretty-print theme/master/layout XML from any PPTX file.

    Returns:
        - clrScheme (12 colors)
        - fontScheme (major/minor fonts)
        - txStyles summary from slide master
        - Per-layout: matchingName, placeholder list
        - Relationship chain verification
    """
    if not os.path.isabs(filepath):
        filepath = os.path.join(PROJECT_ROOT, filepath)

    if not os.path.exists(filepath):
        return {"error": f"File not found: {filepath}"}

    try:
        zf = zipfile.ZipFile(filepath, "r")
    except Exception as e:
        return {"error": f"Cannot open PPTX: {e}"}

    result = {"filepath": filepath}

    # Extract theme XML
    theme_entries = sorted([n for n in zf.namelist() if re.match(r"ppt/theme/theme\d+\.xml$", n)])
    for tentry in theme_entries:
        content = zf.read(tentry).decode("utf-8")
        theme_data = {"path": tentry}

        # Parse colors
        colors = {}
        for color_name in ["dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3",
                           "accent4", "accent5", "accent6", "hlink", "folHlink"]:
            m = re.search(rf"<a:{color_name}><a:srgbClr val=\"([^\"]+)\"/>", content)
            if m:
                colors[color_name] = m.group(1)
            else:
                # Try sysClr
                m2 = re.search(rf"<a:{color_name}><a:sysClr[^>]*lastClr=\"([^\"]+)\"", content)
                if m2:
                    colors[color_name] = m2.group(1)
        theme_data["colors"] = colors

        # Parse fonts
        major_m = re.search(r"<a:majorFont><a:latin typeface=\"([^\"]+)\"", content)
        minor_m = re.search(r"<a:minorFont><a:latin typeface=\"([^\"]+)\"", content)
        theme_data["majorFont"] = major_m.group(1) if major_m else None
        theme_data["minorFont"] = minor_m.group(1) if minor_m else None

        # Theme name
        name_m = re.search(r'<a:theme[^>]*name="([^"]*)"', content)
        theme_data["name"] = name_m.group(1) if name_m else None

        if pretty:
            theme_data["xml"] = _pretty_xml(content)

        result.setdefault("themes", []).append(theme_data)

    # Extract slide master txStyles summary
    master_entries = sorted([n for n in zf.namelist() if re.match(r"ppt/slideMasters/slideMaster\d+\.xml$", n)])
    for mentry in master_entries:
        content = zf.read(mentry).decode("utf-8")
        master_data = {"path": mentry}

        # Extract clrMap
        clr_map_m = re.search(r"<p:clrMap([^/]*?)/>", content)
        if clr_map_m:
            attrs = clr_map_m.group(1)
            mappings = dict(re.findall(r'(\w+)="(\w+)"', attrs))
            master_data["clrMap"] = mappings

        # Summarize txStyles
        for style_name in ["titleStyle", "bodyStyle", "otherStyle"]:
            levels = []
            for lvl in range(1, 10):
                lvl_m = re.search(rf"<a:lvl{lvl}pPr[^>]*>.*?</a:lvl{lvl}pPr>", content, re.DOTALL)
                if lvl_m:
                    lvl_content = lvl_m.group(0)
                    sz_m = re.search(r'sz="(\d+)"', lvl_content)
                    b_m = re.search(r' b="1"', lvl_content)
                    clr_m = re.search(r'<a:srgbClr val="([^"]+)"', lvl_content)
                    scheme_m = re.search(r'<a:schemeClr val="([^"]+)"', lvl_content)
                    bullet_m = re.search(r'<a:buChar char="([^"]+)"', lvl_content)
                    levels.append({
                        "level": lvl,
                        "fontSize": int(sz_m.group(1)) // 100 if sz_m else None,
                        "bold": bool(b_m),
                        "color": clr_m.group(1) if clr_m else (f"scheme:{scheme_m.group(1)}" if scheme_m else None),
                        "bullet": bullet_m.group(1) if bullet_m else None,
                    })
            if levels:
                master_data[style_name] = levels

        result.setdefault("masters", []).append(master_data)

    # Extract layout summaries
    layout_entries = sorted(
        [n for n in zf.namelist() if re.match(r"ppt/slideLayouts/slideLayout\d+\.xml$", n)],
        key=lambda n: int(re.search(r"(\d+)", n.split("/")[-1]).group(1)),
    )
    layouts = []
    for lentry in layout_entries:
        content = zf.read(lentry).decode("utf-8")
        layout_data = {"path": lentry}

        # matchingName
        mn_m = re.search(r'matchingName="([^"]*)"', content)
        layout_data["matchingName"] = mn_m.group(1) if mn_m else None

        # type attribute
        type_m = re.search(r'<p:sldLayout[^>]*type="([^"]*)"', content)
        layout_data["type"] = type_m.group(1) if type_m else None

        # Placeholder list
        placeholders = []
        for ph_m in re.finditer(r'<p:ph([^/]*?)/?>', content):
            ph_attrs = ph_m.group(1)
            ph_type = re.search(r'type="([^"]+)"', ph_attrs)
            ph_idx = re.search(r'idx="(\d+)"', ph_attrs)
            placeholders.append({
                "type": ph_type.group(1) if ph_type else "body",
                "idx": int(ph_idx.group(1)) if ph_idx else None,
            })
        layout_data["placeholders"] = placeholders

        layouts.append(layout_data)

    result["layouts"] = layouts

    # Verify relationship chain
    rels_issues = []
    for mentry in master_entries:
        rels_path = mentry.replace("slideMasters/", "slideMasters/_rels/") + ".rels"
        if rels_path in zf.namelist():
            rels_content = zf.read(rels_path).decode("utf-8")
            theme_rel = re.search(r'Type="[^"]*relationships/theme"[^>]*Target="([^"]+)"', rels_content)
            if not theme_rel:
                rels_issues.append(f"{mentry}: no theme relationship found")
        else:
            rels_issues.append(f"{mentry}: .rels file missing")

    result["relationship_issues"] = rels_issues if rels_issues else None

    zf.close()
    return result


def theme_summary(filepath):
    """
    Compact RLE-style theme summary for context-window efficiency.
    Compresses ~60KB of structural XML into ~500 bytes.
    """
    raw = extract_theme(filepath, pretty=False)
    if "error" in raw:
        return raw

    lines = []
    for theme in raw.get("themes", []):
        colors = theme.get("colors", {})
        color_str = " ".join(f"{k}=#{v}" for k, v in colors.items())
        lines.append(f"colors: {color_str}")
        lines.append(f"fonts: major={theme.get('majorFont', '?')} minor={theme.get('minorFont', '?')}")

    for master in raw.get("masters", []):
        for style_name in ["titleStyle", "bodyStyle", "otherStyle"]:
            levels = master.get(style_name, [])
            if not levels:
                continue
            parts = []
            for lvl in levels:
                desc = f"{lvl['fontSize']}pt" if lvl.get("fontSize") else "?"
                if lvl.get("bold"):
                    desc += " bold"
                if lvl.get("color"):
                    desc += f" {lvl['color']}"
                if lvl.get("bullet"):
                    desc += f" bullet:{lvl['bullet']}"
                parts.append(desc)
            lines.append(f"{style_name}: [{', '.join(parts)}]")

    layout_parts = []
    for layout in raw.get("layouts", []):
        name = layout.get("matchingName", "?")
        phs = "+".join(p["type"] for p in layout.get("placeholders", []))
        layout_parts.append(f"{name}({phs})" if phs else name)
    lines.append(f"layouts: {' | '.join(layout_parts)}")

    return {"summary": "\n".join(lines), "detail": raw}


def inject_test_theme(commands, output_path=None, validate=True):
    """
    Like inject_test_animations but for theme operations. Run console commands,
    then auto-extract theme XML and validate.
    """
    if not commands:
        return {"error": "No commands provided"}

    # Ensure quit is appended
    cmd_list = list(commands)
    if not any(c.strip().lower() == "quit" for c in cmd_list):
        cmd_list.append("quit")

    # Determine output path from save command or generate temp
    save_path = output_path
    if save_path and not os.path.isabs(save_path):
        save_path = os.path.join(PROJECT_ROOT, save_path)

    if not save_path:
        for c in cmd_list:
            parts = c.strip().split()
            if parts and parts[0].lower() == "save" and len(parts) > 1:
                candidate = parts[1].strip('"').strip("'")
                if not os.path.isabs(candidate):
                    candidate = os.path.join(PROJECT_ROOT, candidate)
                save_path = candidate
                break

    if not save_path:
        fd, save_path = tempfile.mkstemp(suffix=".pptx", prefix="mcp_theme_")
        os.close(fd)

    repl_input = "\n".join(cmd_list) + "\n"

    cmd = [
        sys.executable, os.path.join(PROJECT_ROOT, "pc.py"),
        "run", "console", "--headless",
    ]

    try:
        proc_result = subprocess.run(
            cmd,
            input=repl_input,
            capture_output=True,
            text=True,
            timeout=120,
            cwd=PROJECT_ROOT,
        )
        console_stdout = proc_result.stdout
        console_stderr = proc_result.stderr
        console_rc = proc_result.returncode
    except subprocess.TimeoutExpired:
        return {"error": "Console REPL timed out after 120 seconds"}
    except Exception as e:
        return {"error": f"Failed to run console REPL: {e}"}

    response = {
        "console_output": console_stdout,
        "console_stderr": console_stderr if console_stderr else None,
        "console_return_code": console_rc,
    }

    if os.path.exists(save_path):
        response["output_path"] = save_path
        theme_result = extract_theme(save_path, pretty=True)
        response["theme"] = theme_result

        summary_result = theme_summary(save_path)
        response["summary"] = summary_result.get("summary", "")

        if validate:
            val_result = validate_pptx(save_path, layers=["ooxml", "libreoffice"])
            response["validation"] = val_result
    else:
        response["error"] = f"Output file not found at {save_path}. Check console output for errors."

    return response


# ---------------------------------------------------------------------------
# Tool 9: console_exec -- Run console REPL commands, return structured output
# ---------------------------------------------------------------------------

def console_exec(commands, timeout_seconds=120):
    """Run a list of console REPL commands and return structured per-command results."""
    if not commands:
        return {"error": "No commands provided"}

    cmd_list = list(commands)
    if not cmd_list[-1].strip().lower() == "quit":
        cmd_list.append("quit")

    repl_input = "\n".join(cmd_list) + "\n"

    cmd = [
        sys.executable, os.path.join(PROJECT_ROOT, "pc.py"),
        "run", "console", "--headless",
    ]

    try:
        proc_result = subprocess.run(
            cmd,
            input=repl_input,
            capture_output=True,
            text=True,
            timeout=timeout_seconds,
            cwd=PROJECT_ROOT,
        )
    except subprocess.TimeoutExpired:
        return {"error": f"Console REPL timed out after {timeout_seconds} seconds"}
    except Exception as e:
        return {"error": f"Failed to run console REPL: {e}"}

    # Parse output into per-prompt blocks
    stdout = proc_result.stdout
    blocks = []
    current_block = {"command": None, "output": []}

    for line in stdout.splitlines():
        if line.startswith("pptx>"):
            if current_block["command"] is not None:
                current_block["output"] = "\n".join(current_block["output"]).strip()
                blocks.append(current_block)
            remainder = line[5:].strip()
            current_block = {"command": remainder if remainder else "(prompt)", "output": []}
        else:
            current_block["output"].append(line)

    if current_block["command"] is not None:
        current_block["output"] = "\n".join(current_block["output"]).strip()
        blocks.append(current_block)

    # Capture any pre-prompt output (session init, etc.)
    pre_prompt = []
    for line in stdout.splitlines():
        if line.startswith("pptx>"):
            break
        pre_prompt.append(line)

    response = {
        "return_code": proc_result.returncode,
        "init_output": "\n".join(pre_prompt).strip() if pre_prompt else None,
        "command_results": blocks,
    }

    if proc_result.stderr:
        response["stderr"] = proc_result.stderr.strip()

    return response


# ---------------------------------------------------------------------------
# Tool 10: diff_pptx -- Extract two PPTX files and diff all XML parts
# ---------------------------------------------------------------------------

def _xml_semantic_diff(content_a, content_b, filename):
    """Produce a compact semantic diff of two XML strings.

    Instead of unified text diff (huge for single-line XML), this parses both
    documents and reports structural changes: added/removed/changed elements
    and attributes. Identical sibling changes are RLE-compressed.
    """
    from xml.etree import ElementTree as ET

    def strip_ns(tag):
        """Remove namespace URI, keep prefix-style short tag."""
        if tag.startswith("{"):
            uri, local = tag[1:].split("}", 1)
            # Map common OOXML namespaces to short prefixes
            ns_map = {
                "http://schemas.openxmlformats.org/presentationml/2006/main": "p",
                "http://schemas.openxmlformats.org/drawingml/2006/main": "a",
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships": "r",
                "http://schemas.openxmlformats.org/package/2006/relationships": "rel",
                "http://schemas.openxmlformats.org/package/2006/content-types": "ct",
                "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties": "ep",
                "http://schemas.openxmlformats.org/package/2006/metadata/core-properties": "cp",
                "http://purl.org/dc/elements/1.1/": "dc",
                "http://purl.org/dc/terms/": "dcterms",
            }
            prefix = ns_map.get(uri, uri.split("/")[-1])
            return f"{prefix}:{local}"
        return tag

    def elem_signature(elem):
        """Short signature: tag + key identifying attributes."""
        tag = strip_ns(elem.tag)
        # For Relationship elements, include Id
        id_attr = elem.get("Id") or elem.get("id") or elem.get("PartName")
        if id_attr:
            return f"{tag}[{id_attr}]"
        # For placeholder elements, include type/idx
        ph_type = elem.get("type")
        ph_idx = elem.get("idx")
        if ph_type:
            return f"{tag}[@type={ph_type}]"
        if ph_idx:
            return f"{tag}[@idx={ph_idx}]"
        return tag

    def elem_to_compact(elem, depth=0):
        """Convert element tree to compact string for comparison."""
        tag = strip_ns(elem.tag)
        attrs = " ".join(f'{k}="{v}"' for k, v in sorted(elem.attrib.items()))
        text = (elem.text or "").strip()
        children = list(elem)
        if not children and not text:
            return f"<{tag} {attrs}/>" if attrs else f"<{tag}/>"
        parts = [f"<{tag} {attrs}>" if attrs else f"<{tag}>"]
        if text:
            parts.append(text[:80] + ("..." if len(text) > 80 else ""))
        for child in children:
            parts.append(elem_to_compact(child, depth + 1))
        return " ".join(parts)

    def diff_elements(elem_a, elem_b, path=""):
        """Recursively diff two elements, return list of change descriptions."""
        changes = []
        tag = strip_ns(elem_a.tag)
        current_path = f"{path}/{tag}" if path else tag

        # Compare attributes
        attrs_a = dict(elem_a.attrib)
        attrs_b = dict(elem_b.attrib)
        all_keys = sorted(set(attrs_a.keys()) | set(attrs_b.keys()))
        attr_changes = []
        for k in all_keys:
            va = attrs_a.get(k)
            vb = attrs_b.get(k)
            if va != vb:
                if va is None:
                    attr_changes.append(f"+{k}=\"{vb}\"")
                elif vb is None:
                    attr_changes.append(f"-{k}=\"{va}\"")
                else:
                    attr_changes.append(f"{k}: \"{va}\" -> \"{vb}\"")
        if attr_changes:
            changes.append(f"  ATTR {current_path}: {', '.join(attr_changes)}")

        # Compare text
        text_a = (elem_a.text or "").strip()
        text_b = (elem_b.text or "").strip()
        if text_a != text_b:
            ta = text_a[:60] + ("..." if len(text_a) > 60 else "") if text_a else "(empty)"
            tb = text_b[:60] + ("..." if len(text_b) > 60 else "") if text_b else "(empty)"
            changes.append(f"  TEXT {current_path}: {ta} -> {tb}")

        # Compare children
        children_a = list(elem_a)
        children_b = list(elem_b)

        # Build index by signature for matching
        def index_children(children):
            idx = {}
            for i, c in enumerate(children):
                sig = elem_signature(c)
                idx.setdefault(sig, []).append((i, c))
            return idx

        idx_a = index_children(children_a)
        idx_b = index_children(children_b)
        sigs_a = set(idx_a.keys())
        sigs_b = set(idx_b.keys())

        # Removed children (in A not in B)
        removed = sorted(sigs_a - sigs_b)
        if removed:
            # RLE: group similar removals
            changes.append(f"  DEL  {current_path}: {', '.join(removed)} (x{len(removed)})")

        # Added children (in B not in A)
        added = sorted(sigs_b - sigs_a)
        if added:
            # Show compact representation of added elements
            added_details = []
            for sig in added:
                for _, elem in idx_b[sig]:
                    compact = elem_to_compact(elem)
                    if len(compact) > 120:
                        compact = compact[:120] + "..."
                    added_details.append(f"{sig}")
            if len(added_details) > 5:
                changes.append(f"  ADD  {current_path}: {', '.join(added_details[:5])} ... +{len(added_details)-5} more")
            else:
                changes.append(f"  ADD  {current_path}: {', '.join(added_details)}")

        # Recurse into matched children
        for sig in sorted(sigs_a & sigs_b):
            pairs_a = idx_a[sig]
            pairs_b = idx_b[sig]
            for j in range(min(len(pairs_a), len(pairs_b))):
                sub = diff_elements(pairs_a[j][1], pairs_b[j][1], current_path)
                changes.extend(sub)

        return changes

    try:
        root_a = ET.fromstring(content_a)
        root_b = ET.fromstring(content_b)
        changes = diff_elements(root_a, root_b)
        if not changes:
            return None  # Semantically identical
        return "\n".join(changes)
    except ET.ParseError:
        # Fallback: character-level summary for non-XML
        if content_a == content_b:
            return None
        return f"  (non-XML) {len(content_a)} chars -> {len(content_b)} chars"


def diff_pptx(filepath_a, filepath_b, ignore_whitespace=True):
    """Extract two PPTX files and return compact semantic diffs of all XML parts.

    Uses XML-aware structural diffing with RLE compression instead of raw
    unified diffs. Output is typically 50-100x smaller than unified diffs.
    """
    for label, fp in [("a", filepath_a), ("b", filepath_b)]:
        if not os.path.isabs(fp):
            fp = os.path.join(PROJECT_ROOT, fp)
            if label == "a":
                filepath_a = fp
            else:
                filepath_b = fp
        if not os.path.exists(fp):
            return {"error": f"File not found: {fp}"}

    with tempfile.TemporaryDirectory(prefix="mcp_diff_") as tmpdir:
        dir_a = os.path.join(tmpdir, "a")
        dir_b = os.path.join(tmpdir, "b")

        with zipfile.ZipFile(filepath_a) as zf:
            zf.extractall(dir_a)
        with zipfile.ZipFile(filepath_b) as zf:
            zf.extractall(dir_b)

        # Collect all files from both
        files_a = set()
        for root, _, fnames in os.walk(dir_a):
            for f in fnames:
                rel = os.path.relpath(os.path.join(root, f), dir_a)
                files_a.add(rel)

        files_b = set()
        for root, _, fnames in os.walk(dir_b):
            for f in fnames:
                rel = os.path.relpath(os.path.join(root, f), dir_b)
                files_b.add(rel)

        only_in_a = sorted(files_a - files_b)
        only_in_b = sorted(files_b - files_a)
        common = sorted(files_a & files_b)

        diffs = []
        unchanged = []

        # RLE grouping: track files with identical diff patterns
        diff_groups = {}

        for rel in common:
            fa = os.path.join(dir_a, rel)
            fb = os.path.join(dir_b, rel)

            try:
                content_a = open(fa, "r", encoding="utf-8").read()
                content_b = open(fb, "r", encoding="utf-8").read()
            except UnicodeDecodeError:
                continue

            if ignore_whitespace:
                norm_a = re.sub(r"\s+", " ", content_a).strip()
                norm_b = re.sub(r"\s+", " ", content_b).strip()
                if norm_a == norm_b:
                    unchanged.append(rel)
                    continue

            if content_a == content_b:
                unchanged.append(rel)
                continue

            semantic = _xml_semantic_diff(content_a, content_b, rel)
            if semantic is None:
                unchanged.append(rel)
                continue

            # Group identical diffs (RLE for layout files etc.)
            if semantic in diff_groups:
                diff_groups[semantic].append(rel)
            else:
                diff_groups[semantic] = [rel]

        # Build output: unique diffs with file lists
        for semantic, files in diff_groups.items():
            if len(files) == 1:
                diffs.append({"file": files[0], "changes": semantic})
            else:
                diffs.append({
                    "files": files,
                    "count": len(files),
                    "changes": semantic,
                })

    # Build compact output
    lines = []
    lines.append("=== PPTX DIFF SUMMARY ===")
    if only_in_a:
        lines.append(f"Only in A: {', '.join(only_in_a)}")
    if only_in_b:
        lines.append(f"Only in B: {', '.join(only_in_b)}")
    lines.append(f"Changed: {sum(len(d.get('files', [d.get('file')])) for d in diffs)} files")
    lines.append(f"Unchanged: {len(unchanged)} files")
    lines.append("")

    for d in diffs:
        if "files" in d:
            header = f"--- {d['files'][0]} (and {d['count']-1} similar: {', '.join(d['files'][1:])})"
        else:
            header = f"--- {d['file']}"

        lines.append(header)
        change_lines = d["changes"].split("\n")
        if len(change_lines) > 40:
            # Truncate very long diffs, show first 30 + last 5
            lines.extend(change_lines[:30])
            lines.append(f"  ... ({len(change_lines) - 35} more changes) ...")
            lines.extend(change_lines[-5:])
        else:
            lines.append(d["changes"])
        lines.append("")

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Tool 11: inspect_rels -- Dump the full relationship graph of a PPTX
# ---------------------------------------------------------------------------

def inspect_rels(filepath):
    """Dump the full relationship graph, content types, and structure of a PPTX."""
    if not os.path.isabs(filepath):
        filepath = os.path.join(PROJECT_ROOT, filepath)
    if not os.path.exists(filepath):
        return {"error": f"File not found: {filepath}"}

    result = {}

    with zipfile.ZipFile(filepath) as zf:
        # Content types
        if "[Content_Types].xml" in zf.namelist():
            ct_xml = zf.read("[Content_Types].xml").decode("utf-8")
            defaults = dict(re.findall(r'<Default Extension="([^"]+)" ContentType="([^"]+)"', ct_xml))
            overrides = {}
            for m in re.finditer(r'<Override PartName="([^"]+)" ContentType="([^"]+)"', ct_xml):
                overrides[m.group(1)] = m.group(2)
            result["content_types"] = {"defaults": defaults, "overrides": overrides}

        # All relationship files
        rels_graph = {}
        rels_files = sorted([n for n in zf.namelist() if n.endswith(".rels")])
        for rf in rels_files:
            rels_xml = zf.read(rf).decode("utf-8")
            parent = rf.replace("_rels/", "").replace(".rels", "")
            if parent == "_rels/." or parent == ".":
                parent = "(root)"

            relationships = []
            for m in re.finditer(r'<Relationship\s+([^>]+?)/?>', rels_xml):
                attrs_str = m.group(1)
                rid = re.search(r'Id="([^"]+)"', attrs_str)
                rtype = re.search(r'Type="([^"]+)"', attrs_str)
                rtarget = re.search(r'Target="([^"]+)"', attrs_str)
                if rid and rtype and rtarget:
                    relationships.append({
                        "rId": rid.group(1),
                        "type": rtype.group(1).split("/")[-1],
                        "target": rtarget.group(1),
                    })

            # Check for xmlns
            has_xmlns = 'xmlns="' in rels_xml or "xmlns='" in rels_xml
            entry = {"relationships": relationships}
            if not has_xmlns:
                entry["WARNING"] = "Missing xmlns namespace declaration on <Relationships>"
            rels_graph[rf] = entry

        result["relationships"] = rels_graph

        # Slide -> layout -> master -> theme chain
        chains = []
        slide_entries = sorted(
            [n for n in zf.namelist() if re.match(r"ppt/slides/slide\d+\.xml$", n)],
            key=lambda n: int(re.search(r"(\d+)", n.split("/")[-1]).group(1)),
        )
        for slide_path in slide_entries:
            chain = {"slide": slide_path}

            # Find slide rels
            slide_num = re.search(r"(\d+)", slide_path.split("/")[-1]).group(1)
            slide_rels_path = f"ppt/slides/_rels/slide{slide_num}.xml.rels"
            if slide_rels_path in zf.namelist():
                slide_rels = zf.read(slide_rels_path).decode("utf-8")
                layout_m = re.search(r'Target="([^"]*slideLayout[^"]*)"', slide_rels)
                if layout_m:
                    layout_target = layout_m.group(1)
                    # Resolve relative path
                    if layout_target.startswith(".."):
                        layout_path = "ppt/" + layout_target.replace("../", "")
                    else:
                        layout_path = layout_target
                    chain["layout"] = layout_path

                    # Find layout rels -> master
                    layout_num = re.search(r"(\d+)", layout_path.split("/")[-1]).group(1)
                    layout_rels_path = f"ppt/slideLayouts/_rels/slideLayout{layout_num}.xml.rels"
                    if layout_rels_path in zf.namelist():
                        layout_rels = zf.read(layout_rels_path).decode("utf-8")
                        master_m = re.search(r'Target="([^"]*slideMaster[^"]*)"', layout_rels)
                        if master_m:
                            master_target = master_m.group(1)
                            if master_target.startswith(".."):
                                master_path = "ppt/" + master_target.replace("../", "")
                            else:
                                master_path = master_target
                            chain["master"] = master_path

                            # Find master rels -> theme
                            master_num = re.search(r"(\d+)", master_path.split("/")[-1]).group(1)
                            master_rels_path = f"ppt/slideMasters/_rels/slideMaster{master_num}.xml.rels"
                            if master_rels_path in zf.namelist():
                                master_rels = zf.read(master_rels_path).decode("utf-8")
                                theme_m = re.search(r'Target="([^"]*theme[^"]*)"', master_rels)
                                if theme_m:
                                    theme_target = theme_m.group(1)
                                    if theme_target.startswith(".."):
                                        theme_path = "ppt/" + theme_target.replace("../", "")
                                    else:
                                        theme_path = theme_target
                                    chain["theme"] = theme_path

            chains.append(chain)
        result["slide_chains"] = chains

        # File listing
        result["all_parts"] = sorted(zf.namelist())

    return result


# ---------------------------------------------------------------------------
# Tool 12: generate_crud_sample -- Scaffold a test PPTX for a specific domain
# ---------------------------------------------------------------------------

def generate_crud_sample(domain, theme_id="minimal", slide_count=3, save_path=None,
                         animations=None, validate=True):
    """Generate a test PPTX with configurable content for a given CRUD domain."""
    if save_path and not os.path.isabs(save_path):
        save_path = os.path.join(PROJECT_ROOT, save_path)

    if not save_path:
        fd, save_path = tempfile.mkstemp(suffix=".pptx", prefix=f"mcp_crud_{domain}_")
        os.close(fd)

    cmd_list = [f"new {theme_id}"]

    layouts = ["slideLayout1", "slideLayout2", "slideLayout4"]
    for i in range(1, slide_count + 1):
        layout = layouts[(i - 1) % len(layouts)]
        cmd_list.append(f'create {i} "Test Slide {i}" {layout}')

    # Domain-specific content
    if domain == "animations" and animations:
        for anim in animations:
            # Each animation: {slide, shape, type, direction, trigger}
            slide = anim.get("slide", 1)
            shape = anim.get("shape", 2)
            anim_type = anim.get("type", "fade")
            direction = anim.get("direction", "in")
            trigger = anim.get("trigger", "on-click")
            cmd = f"add-animation {slide} {shape} {anim_type} {direction} {trigger}"
            if anim.get("duration"):
                cmd += f" --duration {anim['duration']}"
            cmd_list.append(cmd)
    elif domain == "animations" and not animations:
        # Default: one of each category
        cmd_list.extend([
            "add-animation 1 2 fade in on-click",
            "add-animation 1 2 fade out on-click",
            "add-animation 2 2 wipe in on-click",
            "add-animation 2 3 spin emphasis on-click",
        ])

    cmd_list.append(f'save {save_path}')
    cmd_list.append("quit")

    repl_input = "\n".join(cmd_list) + "\n"
    cmd = [
        sys.executable, os.path.join(PROJECT_ROOT, "pc.py"),
        "run", "console", "--headless",
    ]

    try:
        proc_result = subprocess.run(
            cmd,
            input=repl_input,
            capture_output=True,
            text=True,
            timeout=120,
            cwd=PROJECT_ROOT,
        )
    except subprocess.TimeoutExpired:
        return {"error": "Console REPL timed out after 120 seconds"}
    except Exception as e:
        return {"error": f"Failed to run console REPL: {e}"}

    response = {
        "domain": domain,
        "theme": theme_id,
        "slide_count": slide_count,
        "output_path": save_path,
        "console_output": proc_result.stdout,
        "return_code": proc_result.returncode,
    }

    if proc_result.stderr:
        response["stderr"] = proc_result.stderr.strip()

    if os.path.exists(save_path):
        if domain == "animations":
            timing_result = extract_timing(save_path, pretty=True)
            response["timing"] = timing_result

        if validate:
            val_result = validate_pptx(save_path, layers=["ooxml", "libreoffice"])
            response["validation"] = val_result

        rels_result = inspect_rels(save_path)
        response["relationships"] = rels_result.get("slide_chains", [])
    else:
        response["error"] = f"Output file not found at {save_path}"

    return response


# ---------------------------------------------------------------------------
# MCP protocol handler (JSON-RPC over stdio)
# ---------------------------------------------------------------------------

TOOLS = [
    {
        "name": "spec_search",
        "description": (
            "Search OOXML specification documents by keyword, regex, or section number. "
            "Covers MS-OE376, MS-OI29500, MS-PPTX (full + PML extracts) and 15 OpenXML SDK "
            "topic pages (BodyProperties, Placeholder, SlideLayout, SlideMaster, ColorScheme, "
            "FontScheme, FormatScheme, TextStyles, PresetGeometry, NormalAutoFit, MajorFont, "
            "MinorFont, ColorMap, PlaceholderValues, SlideLayoutValues). Default search scope "
            "includes PML extracts + all SDK pages. Use specs=['all'] to search everything "
            "including the full 2.7MB MS-OE376/MS-OI29500 texts."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Search keyword, regex pattern, or phrase to find in spec documents",
                },
                "specs": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": (
                        "Which spec files to search. Shortcuts: 'all' (everything), 'sdk' (all SDK topic pages). "
                        "Specific names: MS-OE376, MS-OE376-pml, MS-OI29500, MS-OI29500-pml, MS-PPTX, "
                        "sdk:BodyProperties, sdk:Placeholder, sdk:SlideLayout, etc. "
                        "Default: PML extracts + all SDK topic pages."
                    ),
                },
                "context_lines": {
                    "type": "integer",
                    "description": "Lines of context before/after each match (default: 3)",
                },
                "max_results": {
                    "type": "integer",
                    "description": "Maximum matches to return (default: 20)",
                },
                "section_number": {
                    "type": "string",
                    "description": "Search for a specific section number (e.g., '4.6.33' for cTn)",
                },
            },
            "required": ["query"],
        },
    },
    {
        "name": "validate_pptx",
        "description": (
            "Validate a PPTX file against OOXML standards using the project's 3-layer validation system. "
            "Layer 1 (default): ECMA-376 XSD schema validation. "
            "Layer 2 (libreoffice): Structural validation via LibreOffice headless conversion. "
            "Layer 3 (powerpoint): Native PowerPoint validation via Wine. "
            "Returns validation results with any errors or warnings found."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "filepath": {
                    "type": "string",
                    "description": "Path to the PPTX file (absolute or relative to project root)",
                },
                "layers": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": (
                        "Validation layers to run. Options: 'ooxml', 'libreoffice', 'powerpoint', 'all'. "
                        "Default: ['ooxml']"
                    ),
                },
                "office_version": {
                    "type": "string",
                    "description": "Target Office version. Default: Microsoft365",
                },
            },
            "required": ["filepath"],
        },
    },
    {
        "name": "oracle_pattern",
        "description": (
            "Extract reference animation XML patterns from the PowerPoint oracle file "
            "(155 slides created natively in PowerPoint, covering all animation types). "
            "Use this to see exactly how PowerPoint structures animation timing XML for any "
            "animation type, including entrance, exit, emphasis, and motion path animations. "
            "Returns pretty-printed <p:timing> XML for matched slides."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": (
                        "What to search for. For mode='search': keyword like 'Wipe', 'Fade', 'Spin'. "
                        "For mode='preset': presetID like '22'. For mode='slide': slide number like '43'. "
                        "For mode='class': preset class like 'entr', 'exit', 'emph', 'path'. "
                        "For mode='list': ignored."
                    ),
                },
                "mode": {
                    "type": "string",
                    "enum": ["search", "preset", "slide", "class", "list"],
                    "description": (
                        "Search mode. 'search' = keyword in title, 'preset' = by presetID, "
                        "'slide' = by slide number, 'class' = by presetClass, 'list' = show all slides"
                    ),
                },
                "pretty": {
                    "type": "boolean",
                    "description": "Pretty-print the XML output (default: true)",
                },
            },
            "required": ["query"],
        },
    },
    {
        "name": "extract_timing",
        "description": (
            "Extract and pretty-print animation timing XML (<p:timing>) from any PPTX file. "
            "Returns per-slide timing XML, bldLst, and animation metadata summary "
            "(presetID, presetClass, grpId, nodeType). Use this to inspect what our "
            "animation pipeline actually produced in a generated PPTX."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "filepath": {
                    "type": "string",
                    "description": "Path to the PPTX file (absolute or relative to project root)",
                },
                "slide": {
                    "type": "string",
                    "description": (
                        "Which slides to extract. 'all' (default), single number '1', "
                        "or comma-separated '1,3'. Omit for all slides."
                    ),
                },
                "pretty": {
                    "type": "boolean",
                    "description": "Pretty-print the XML output (default: true)",
                },
            },
            "required": ["filepath"],
        },
    },
    {
        "name": "inject_test_animations",
        "description": (
            "Run a sequence of console REPL commands (load, add-animation, save, etc.) to "
            "create a test PPTX with specific animations, then automatically extract the "
            "timing XML and run validation. Returns console output, per-slide timing XML, "
            "animation metadata, and validation results in one call. Use this to test the "
            "full animation injection pipeline end-to-end."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "commands": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": (
                        "List of console REPL commands to execute in order. "
                        "Must include load and save commands. 'quit' is appended automatically. "
                        "Example: [\"load template.pptx\", \"add-animation 1 3 fade in on-click\", "
                        "\"save output.pptx\"]"
                    ),
                },
                "output_path": {
                    "type": "string",
                    "description": (
                        "Where to save the PPTX. If omitted, inferred from save command or uses temp file."
                    ),
                },
                "extract_slides": {
                    "type": "string",
                    "description": "Slides to extract timing from. Default: 'all'.",
                },
                "validate": {
                    "type": "boolean",
                    "description": "Run OOXML + LibreOffice validation on result (default: true)",
                },
            },
            "required": ["commands"],
        },
    },
    {
        "name": "extract_theme",
        "description": (
            "Extract and pretty-print theme/master/layout XML from any PPTX file. "
            "Returns color scheme (12 colors), font scheme (major/minor fonts), "
            "txStyles summary (per-level font size, color, bullet), per-layout matchingName "
            "and placeholder list, and relationship chain verification."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "filepath": {
                    "type": "string",
                    "description": "Path to the PPTX file (absolute or relative to project root)",
                },
                "pretty": {
                    "type": "boolean",
                    "description": "Pretty-print the XML output (default: true)",
                },
            },
            "required": ["filepath"],
        },
    },
    {
        "name": "theme_summary",
        "description": (
            "Compact RLE-style representation of a PPTX theme for context-window efficiency. "
            "Compresses ~60KB of structural XML into ~500 bytes. Shows colors, fonts, "
            "text style levels, and layout list in a compact format."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "filepath": {
                    "type": "string",
                    "description": "Path to the PPTX file (absolute or relative to project root)",
                },
            },
            "required": ["filepath"],
        },
    },
    {
        "name": "inject_test_theme",
        "description": (
            "Like inject_test_animations but for theme operations. Run console REPL commands "
            "(load, apply-theme, save, etc.), then automatically extract theme XML and validate. "
            "Returns console output, theme extraction results, compact summary, and validation."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "commands": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": (
                        "List of console REPL commands to execute in order. "
                        "Must include load and save commands. 'quit' is appended automatically. "
                        "Example: [\"load template.pptx\", \"apply-theme minimal\", \"save output.pptx\"]"
                    ),
                },
                "output_path": {
                    "type": "string",
                    "description": "Where to save the PPTX. If omitted, inferred from save command.",
                },
                "validate": {
                    "type": "boolean",
                    "description": "Run OOXML + LibreOffice validation on result (default: true)",
                },
            },
            "required": ["commands"],
        },
    },
    {
        "name": "console_exec",
        "description": (
            "Run a list of console REPL commands and return structured per-command output. "
            "Replaces manual 'echo -e | python3 pc.py run console --headless' invocations. "
            "Each command's output is parsed into a separate result block. 'quit' is appended "
            "automatically if not present. Use this for any console REPL interaction."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "commands": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": (
                        "List of console REPL commands to execute in order. "
                        "Examples: ['new corporate', 'create 1 \"Title Slide\" slideLayout1', "
                        "'save output.pptx']. 'quit' is appended automatically."
                    ),
                },
                "timeout_seconds": {
                    "type": "integer",
                    "description": "Timeout in seconds (default: 120)",
                },
            },
            "required": ["commands"],
        },
    },
    {
        "name": "diff_pptx",
        "description": (
            "Extract two PPTX files (ZIP archives) and diff all XML parts. Uses XML-aware "
            "structural diffing with RLE compression -- output is 50-100x smaller than unified "
            "diffs. Reports added/removed/changed elements and attributes semantically. "
            "Identical changes across files (e.g. all layouts) are grouped. "
            "Use this to compare raw pipeline output against PowerPoint-repaired versions."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "filepath_a": {
                    "type": "string",
                    "description": "Path to the first PPTX file (raw/original)",
                },
                "filepath_b": {
                    "type": "string",
                    "description": "Path to the second PPTX file (repaired/reference)",
                },
                "ignore_whitespace": {
                    "type": "boolean",
                    "description": "Ignore whitespace-only differences (default: true)",
                },
            },
            "required": ["filepath_a", "filepath_b"],
        },
    },
    {
        "name": "inspect_rels",
        "description": (
            "Dump the full relationship graph of a PPTX file. Returns: content types "
            "(defaults + overrides), all .rels files with their relationships, "
            "slide->layout->master->theme inheritance chains, and a complete file listing. "
            "Also warns about missing xmlns namespace declarations on Relationships elements."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "filepath": {
                    "type": "string",
                    "description": "Path to the PPTX file (absolute or relative to project root)",
                },
            },
            "required": ["filepath"],
        },
    },
    {
        "name": "generate_crud_sample",
        "description": (
            "Generate a test PPTX with configurable content for a given CRUD domain. "
            "Creates a from-scratch presentation with the specified theme, slides, and "
            "optional animations, then extracts timing/relationships and validates. "
            "Domains: 'animations', 'slide', 'shapes', 'theme', 'textel', 'slidenotes'."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {
                "domain": {
                    "type": "string",
                    "description": "CRUD domain: animations, slide, shapes, theme, textel, slidenotes",
                },
                "theme_id": {
                    "type": "string",
                    "description": "Theme to use (default: minimal). Options: minimal, corporate, academic",
                },
                "slide_count": {
                    "type": "integer",
                    "description": "Number of slides to create (default: 3)",
                },
                "save_path": {
                    "type": "string",
                    "description": "Where to save the PPTX (default: temp file)",
                },
                "animations": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "slide": {"type": "integer"},
                            "shape": {"type": "integer"},
                            "type": {"type": "string"},
                            "direction": {"type": "string"},
                            "trigger": {"type": "string"},
                            "duration": {"type": "integer"},
                        },
                    },
                    "description": (
                        "Animation specs for 'animations' domain. "
                        "Each: {slide, shape, type, direction, trigger, duration}. "
                        "If omitted, default animations are added."
                    ),
                },
                "validate": {
                    "type": "boolean",
                    "description": "Run validation on result (default: true)",
                },
            },
            "required": ["domain"],
        },
    },
]


def handle_request(request):
    """Process a single JSON-RPC request and return the response."""
    method = request.get("method")
    req_id = request.get("id")
    params = request.get("params", {})

    if method == "initialize":
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {}},
                "serverInfo": {
                    "name": "ooxml-spec-server",
                    "version": "1.0.0",
                },
            },
        }

    if method == "notifications/initialized":
        return None  # No response for notifications

    if method == "tools/list":
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {"tools": TOOLS},
        }

    if method == "tools/call":
        tool_name = params.get("name")
        tool_args = params.get("arguments", {})

        try:
            if tool_name == "spec_search":
                result = spec_search(**tool_args)
            elif tool_name == "validate_pptx":
                result = validate_pptx(**tool_args)
            elif tool_name == "oracle_pattern":
                result = oracle_pattern(**tool_args)
            elif tool_name == "extract_timing":
                result = extract_timing(**tool_args)
            elif tool_name == "inject_test_animations":
                result = inject_test_animations(**tool_args)
            elif tool_name == "extract_theme":
                result = extract_theme(**tool_args)
            elif tool_name == "theme_summary":
                result = theme_summary(**tool_args)
            elif tool_name == "inject_test_theme":
                result = inject_test_theme(**tool_args)
            elif tool_name == "console_exec":
                result = console_exec(**tool_args)
            elif tool_name == "diff_pptx":
                result = diff_pptx(**tool_args)
            elif tool_name == "inspect_rels":
                result = inspect_rels(**tool_args)
            elif tool_name == "generate_crud_sample":
                result = generate_crud_sample(**tool_args)
            else:
                return {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "error": {"code": -32601, "message": f"Unknown tool: {tool_name}"},
                }

            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(result, indent=2, ensure_ascii=False),
                        }
                    ],
                },
            }
        except Exception as e:
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "content": [{"type": "text", "text": json.dumps({"error": str(e)})}],
                    "isError": True,
                },
            }

    if method == "ping":
        return {"jsonrpc": "2.0", "id": req_id, "result": {}}

    # Unknown method
    return {
        "jsonrpc": "2.0",
        "id": req_id,
        "error": {"code": -32601, "message": f"Method not found: {method}"},
    }


def main():
    """Run the MCP server over stdio."""
    _build_oracle_index()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
            response = handle_request(request)
            if response is not None:
                sys.stdout.write(json.dumps(response) + "\n")
                sys.stdout.flush()
        except json.JSONDecodeError:
            error_response = {
                "jsonrpc": "2.0",
                "id": None,
                "error": {"code": -32700, "message": "Parse error"},
            }
            sys.stdout.write(json.dumps(error_response) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
