#!/usr/bin/env python3
"""Turn Gradle and JUnit failures into GitHub annotations, so they show on the run page
and through the public checks API without downloading logs."""
import glob
import os
import re
import xml.etree.ElementTree as ET


def esc(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


count = 0

# 1. Failed unit tests (JUnit XML written by Gradle).
for path in glob.glob("app/build/test-results/**/*.xml", recursive=True):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        for fail in list(case.findall("failure")) + list(case.findall("error")):
            name = f"{case.get('classname')}.{case.get('name')}"
            text = (fail.get("message") or "") + "\n" + (fail.text or "")
            print(f"::error title=Test failed: {esc(name)}::{esc(text[:3000])}")
            count += 1

# 2. Gradle's "What went wrong" block (dependency, plugin or build-script problems).
for log in glob.glob("gradle-*.log"):
    with open(log, encoding="utf-8", errors="replace") as f:
        content = f.read()
    for m in re.finditer(r"\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)", content, re.S):
        print(f"::error title=Gradle ({os.path.basename(log)})::{esc(m.group(1).strip()[:3000])}")
        count += 1
    # Kotlin errors, in case the problem matcher missed them.
    for m in re.finditer(r"^e: (.+)$", content, re.M):
        if count > 40:
            break
        print(f"::error title=Kotlin::{esc(m.group(1)[:1000])}")
        count += 1

print(f"Reported {count} problem(s).")
